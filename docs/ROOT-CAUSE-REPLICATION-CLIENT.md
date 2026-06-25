# 从库权限问题根因分析

## 问题回顾
**症状**：应用启动后，`DynamicDataSource.checkSlaveReplication()` 执行 `SHOW REPLICA STATUS` 时报权限错误：
```
Access denied; you need (at least one of) the SUPER, REPLICATION CLIENT privilege(s) for this operation
```

## 深层根因分析

### 1. MySQL 官方镜像的 root 用户创建机制
**问题源头**：MySQL 8.0 官方 Docker 镜像通过环境变量 `MYSQL_ROOT_PASSWORD` 创建 root 用户时，**只授予基本权限**，不包含 `REPLICATION CLIENT`。

**官方镜像默认授权（伪代码）**：
```sql
-- MySQL 官方镜像 entrypoint.sh 创建 root 用户
CREATE USER 'root'@'localhost' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}';
CREATE USER 'root'@'%' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}';

-- 默认授予的权限（ALL PRIVILEGES 在 MySQL 8.0 中不包含某些特殊权限）
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
```

**关键点**：MySQL 8.0 的 `ALL PRIVILEGES` **不包含**以下动态权限：
- `REPLICATION CLIENT` (查看复制状态)
- `REPLICATION SLAVE` (设置复制)
- `GROUP_REPLICATION_ADMIN`
- `BINLOG_ADMIN`
- 等其他动态权限

参考：https://dev.mysql.com/doc/refman/8.0/en/privileges-provided.html#priv_replication-client

### 2. 我们的初始化脚本流程
```
01-create-replication-user.sh  →  创建 replicator 用户 + 授予 REPLICATION CLIENT ✅
02-...                          →  其他初始化
03-import-backup.sh             →  导入备份
04-apply-all-migrations.sh      →  执行迁移
```

**遗漏点**：我们只给复制用户 `replicator` 授予了 `REPLICATION CLIENT` 权限，但**应用连接使用的是 root 用户**。

### 3. 为什么之前没发现？
可能的原因：
1. **测试环境是单机部署**：`checkSlaveReplication()` 检测到 slave URL == master URL 时会跳过复制检查（代码第 157-160 行）
2. **全量 MySQL 服务器安装**：非 Docker 部署时，root 用户默认有所有权限
3. **从未启用读写分离**：之前可能没有配置独立的从库环境

### 4. 触发时机
- **何时检查**：应用启动时 + 每 60 秒定时探测
- **执行命令**：`SHOW REPLICA STATUS` / `SHOW SLAVE STATUS`
- **调用路径**：
  ```
  DynamicDataSource.afterPropertiesSet() (启动时)
  → checkSlaveReplication()
  → conn.createStatement().executeQuery("SHOW REPLICA STATUS")
  → SQLException: Access denied
  ```

## 根本原因总结

| 层级 | 问题 | 原因 |
|------|------|------|
| **直接原因** | root 用户无 REPLICATION CLIENT 权限 | MySQL 8.0 ALL PRIVILEGES 不含动态权限 |
| **架构原因** | 应用用户 = root，健康检查需要特殊权限 | 未分离应用用户与管理用户 |
| **流程原因** | 初始化脚本只配置 replicator 用户 | 假设 root 自带所有权限 |

## 修复验证与防护措施

### 修复验证清单
```bash
# 1. 确认新脚本已复制到镜像
docker exec langhack-mysql-master ls -l /docker-entrypoint-initdb.d/ | grep grant-replication

# 2. 验证权限授予成功
docker exec langhack-mysql-master mysql -u root -p'密码' \
  -e "SELECT user, host, Repl_client_priv FROM mysql.user WHERE user='root';"
# 预期输出: Repl_client_priv = Y

# 3. 验证健康检查不再报错
docker logs -f langhack-backend --tail 100 | grep "Cannot connect to slave"
# 预期: 无此日志

# 4. 验证从库路由正常
docker logs -f langhack-backend --tail 100 | grep "Slave recovered"
# 预期: 看到 "Slave recovered, re-enabling slave routing"
```

### 防护措施（防止再次发生）

#### 1. 更新 Dockerfile（确保新脚本打包）
```dockerfile
# docker/mysql/master/Dockerfile
COPY mysql/init/01-grant-replication-client-to-root.sh \
     /docker-entrypoint-initdb.d/01-grant-replication-client-to-root.sh
```

**⚠️ 当前问题**：新脚本 `01-grant-replication-client-to-root.sh` 没有在 Dockerfile 中声明！

#### 2. 添加启动健康检查
在应用日志中增加启动时权限检查：
```java
// DynamicDataSource.java afterPropertiesSet()
private void logPermissionStatus() {
    try (Connection conn = resolveMasterDataSource().getConnection()) {
        ResultSet rs = conn.createStatement().executeQuery(
            "SHOW GRANTS FOR CURRENT_USER()"
        );
        while (rs.next()) {
            String grant = rs.getString(1);
            if (grant.contains("REPLICATION CLIENT")) {
                log.info("[DataSource] ✅ REPLICATION CLIENT permission verified");
                return;
            }
        }
        log.warn("[DataSource] ⚠️ REPLICATION CLIENT permission missing, slave health check will fail");
    } catch (SQLException e) {
        log.error("[DataSource] Failed to check permissions", e);
    }
}
```

#### 3. 文档化部署检查清单
在 `docs/DEPLOYMENT.md` 中添加：
```markdown
## 数据库权限验证

部署后必须验证：
- [ ] root 用户有 REPLICATION CLIENT 权限
- [ ] 应用启动日志无 "Access denied" 错误
- [ ] 从库健康检查正常（日志显示 "Slave replication OK"）
```

## 立即行动项

### 🚨 紧急修复：更新 Dockerfile
当前 `01-grant-replication-client-to-root.sh` **未被打包到镜像中**，需要立即修复！
