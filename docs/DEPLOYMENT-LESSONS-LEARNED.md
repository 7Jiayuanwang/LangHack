# 部署问题排查经验教训

> 记录时间：2026-04-08
> 问题场景：MySQL 主从复制健康检查失败导致部署阻塞

---

## 问题总结

### 1. MySQL 从库健康检查命令错误

**症状**：
- 从库容器状态一直显示 `unhealthy`
- 健康检查日志显示 `ExitCode: 1, Output: ""`
- 手动执行健康检查命令失败

**根因分析**：
1. **`-N` 参数与 `\G` 格式不兼容**：`SHOW REPLICA STATUS\G` 使用垂直格式，`-N`（no table headers）只对表格格式有效
2. **`2>/dev/null` 过滤问题**：MySQL 客户端的正常输出受 stderr 重定向影响
3. **`mysqladmin ping --silent` 仍有输出**：警告信息干扰后续管道
4. **双引号转义问题**：`"SHOW REPLICA STATUS\\G"` 在容器内执行时引号处理不正确

**修复方案**：
```yaml
# docker-compose.yml - mysql-slave healthcheck
test: ["CMD-SHELL", "mysqladmin ping -h localhost -uroot -p${MYSQL_ROOT_PASSWORD} --silent >/dev/null 2>&1 && mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e 'SHOW REPLICA STATUS\\G' 2>&1 | grep -q 'Replica_IO_Running: Yes'"]
```

**关键修改**：
- 移除 `-N` 参数
- `mysqladmin` 使用 `>/dev/null 2>&1` 完全静默
- `mysql` 使用 `2>&1` 保留输出
- SQL 命令使用单引号 `'SHOW REPLICA STATUS\\G'`

---

### 2. 复制用户权限范围错误

**症状**：
- 从库日志显示：`Access denied for user 'replicator'@'10.255.1.4' (using password: YES)`
- 手动用 `replicator` 用户可以连接主库
- 但复制线程报权限拒绝

**根因分析**：
- 初始化脚本创建 `replicator@'172.%'`
- Docker Compose 网络实际分配 IP 段可能是 `10.255.x.x`
- 权限范围不匹配导致复制失败

**修复方案**：
```bash
# docker/mysql/init/01-create-replication-user.sh
CREATE USER IF NOT EXISTS '${REPL_USER}'@'%'  # 改为 '%' 允许所有内部网络
    IDENTIFIED WITH mysql_native_password
    BY '${REPL_PASS}';

GRANT REPLICATION SLAVE ON *.* TO '${REPL_USER}'@'%';
```

**安全说明**：
- `'%'` 在 Docker 内部网络中是安全的（容器间隔离）
- 主库端口映射到宿主机时，应使用防火墙限制外部访问
- 生产环境建议使用强密码 + SSL 连接

---

### 3. 数据库初始化脚本未执行

**症状**：
- 后端启动失败：`Table 'english_learning.users' doesn't exist`
- 删除数据卷后重新部署，表未创建

**根因分析**：
- 用户执行 `docker volume rm` 删除了数据卷
- 但容器未完全删除，MySQL 检测到数据目录存在（空目录）
- 跳过了 `docker-entrypoint-initdb.d` 脚本执行

**修复方案**：
```bash
# 完整清理流程
docker compose down                    # 停止容器
docker volume rm langhack_mysql_master_data langhack_mysql_slave_data  # 删除卷
docker compose up -d                   # 重新创建（触发初始化）
```

**预防措施**：
- `deploy.sh` 应添加数据库初始化验证
- 检查关键表是否存在，不存在则提示用户

---

### 4. 旧容器残留问题

**症状**：
- `docker compose down -v` 后仍有旧容器
- 容器显示 `Up 15 hours` 而不是刚创建
- 日志显示旧的时间戳

**根因分析**：
- `docker compose down` 只删除当前 compose 文件管理的容器
- 如果容器是通过其他方式创建的（如手动 `docker run`），不会被删除
- 容器名称冲突导致新容器无法创建

**修复方案**：
```bash
# 强制清理所有相关容器
docker stop $(docker ps -aq --filter "name=langhack")
docker rm -f $(docker ps -aq --filter "name=langhack")
docker volume rm $(docker volume ls -q --filter "name=langhack")
```

---

## 部署脚本改进建议

### deploy.sh 增强

1. **数据库初始化验证**：
```bash
verify_database_init() {
    local mysql_container=$(docker ps --format '{{.Names}}' | grep mysql-master | head -1)
    if [ -z "$mysql_container" ]; then
        return 0
    fi

    local table_count=$(docker exec "$mysql_container" \
        mysql -uroot -p"$MYSQL_ROOT_PASSWORD" english_learning \
        -sN -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='english_learning';" \
        2>/dev/null || echo "0")

    if [ "${table_count:-0}" -lt 10 ]; then
        echo "[ERROR] 数据库初始化不完整，检测到表数量: $table_count"
        echo "[提示] 请删除数据卷后重新部署: docker volume rm langhack_mysql_master_data langhack_mysql_slave_data"
        return 1
    fi

    echo "[OK] 数据库初始化验证通过（表数量: $table_count）"
    return 0
}
```

2. **容器清理检查**：
```bash
check_old_containers() {
    local old_containers=$(docker ps -a --filter "name=langhack" --format "{{.Names}}\t{{.CreatedAt}}" | \
        awk -v cutoff="$(date -d '1 hour ago' '+%Y-%m-%d %H:%M:%S')" '$2 < cutoff {print $1}')

    if [ -n "$old_containers" ]; then
        echo "[WARN] 检测到旧容器，建议清理："
        echo "$old_containers"
        read -p "是否清理旧容器？[y/N] " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo "$old_containers" | xargs docker rm -f
        fi
    fi
}
```

3. **健康检查超时调整**：
```yaml
# docker-compose.yml
healthcheck:
  start_period: 120s  # 给足启动时间
  interval: 10s
  timeout: 5s
  retries: 18         # 总共 120s + 18*10s = 300s
```

---

## 排查流程总结

### 标准排查步骤

1. **查看容器状态**：
```bash
docker ps -a --filter "name=langhack"
docker inspect <container> | grep -A 20 "Health"
```

2. **查看容器日志**：
```bash
docker logs <container> --tail 100
docker logs <container> 2>&1 | grep -i error
```

3. **手动测试健康检查**：
```bash
docker exec <container> <healthcheck-command>
```

4. **检查网络连接**：
```bash
docker exec <slave> ping mysql-master
docker exec <slave> mysql -h mysql-master -u replicator -p<password> -e "SELECT 1"
```

5. **检查复制状态**：
```bash
docker exec <slave> mysql -uroot -p<password> -e "SHOW REPLICA STATUS\G"
```

### 常见错误模式

| 错误信息 | 可能原因 | 排查方向 |
|---------|---------|---------|
| `Access denied for user 'replicator'@'x.x.x.x'` | 用户权限范围不匹配 | 检查 `CREATE USER` 的 host 部分 |
| `Table 'xxx' doesn't exist` | 数据库未初始化 | 检查数据卷是否为空，初始化脚本是否执行 |
| `Replica_IO_Running: Connecting` | 复制配置问题 | 检查密码、网络、主库状态 |
| `healthcheck: unhealthy` | 健康检查命令错误 | 手动执行健康检查命令验证 |
| `Up X hours (healthy)` 但实际是新部署 | 旧容器残留 | 强制删除所有相关容器 |

---

## 经验教训

### 1. 健康检查命令设计原则

- ✅ 在容器内完成所有操作（包括 grep）
- ✅ 使用单引号避免转义问题
- ✅ 完全静默辅助命令（`>/dev/null 2>&1`）
- ✅ 保留关键命令输出（`2>&1`）
- ❌ 不要混用 `-N` 和 `\G`
- ❌ 不要过度使用 `2>/dev/null`

### 2. Docker 网络权限配置

- Docker Compose 网络 IP 段不固定（可能是 172.x 或 10.x）
- 内部服务间通信使用 `'%'` 或服务名
- 外部访问通过端口映射 + 防火墙控制

### 3. 数据卷管理

- `docker compose down -v` 会删除匿名卷，但不删除命名卷
- 删除数据卷前务必备份
- 重新初始化需要完全删除容器和卷

### 4. 部署脚本健壮性

- 添加前置检查（旧容器、数据完整性）
- 提供清晰的错误提示和修复建议
- 记录详细日志便于排查

---

## 提交修复

```bash
git add docker/docker-compose.yml docker/mysql/init/01-create-replication-user.sh
git commit -m "fix(docker): 修复 MySQL 主从复制健康检查和用户权限

- 健康检查：移除 -N 参数，使用单引号，完全静默 mysqladmin
- 复制用户：从 172.% 改为 % 以兼容所有 Docker 网络 IP 段
- 文档：添加部署问题排查经验教训

Fixes: 从库健康检查失败导致部署阻塞
Root cause: 健康检查命令格式错误 + 复制用户权限范围不匹配"
```

---

## 参考资料

- [MySQL 8.0 Replication Documentation](https://dev.mysql.com/doc/refman/8.0/en/replication.html)
- [Docker Compose Healthcheck](https://docs.docker.com/compose/compose-file/compose-file-v3/#healthcheck)
- [Docker Networking](https://docs.docker.com/network/)

### 5. 备份导入脚本文件类型检查问题

**症状**：
- 从库日志显示：`ERROR: Can't initialize batch_readline - may be the input source is a directory or a block device.`
- 尝试导入名为 "tmp" 的文件
- 后续初始化脚本未执行

**根因分析**：
- 脚本使用 `ls *.sql` 会匹配到目录和非常规文件
- `stat` 命令输出格式在某些情况下解析失败
- 导致尝试导入目录或无效文件，初始化中断

**修复方案**：
```bash
# 03-import-backup.sh - 添加文件类型检查
for pattern in "*.sql" "*.sql.gz"; do
    for file in "$dir"/$pattern; do
        # 检查文件是否存在且为常规文件（非目录）
        if [ -f "$file" ]; then
            CANDIDATES="${CANDIDATES}${file}"$'\n'
        fi
    done
done
```

---

### 6. 从库复制初始化执行顺序问题

**症状**：
- 从库容器启动后 `SHOW REPLICA STATUS` 返回空
- 日志中没有 `[replication]` 开头的输出
- `06-init-replication.sh` 脚本未执行

**根因分析**：
- MySQL entrypoint 在执行完 `docker-entrypoint-initdb.d` 中的脚本后会关闭临时服务器
- 在某些情况下，entrypoint 在执行完 `05-import-ecdict.sh` 后就提前终止
- `06-init-replication.sh` 没有被执行

**修复方案**：
采用两阶段初始化策略：

1. **initdb 阶段**（`06-create-replication-marker.sh`）：
   ```bash
   # 创建标记文件
   touch /var/lib/mysql/.replication-init-pending
   # 复制初始化脚本到持久化位置
   cp /docker-entrypoint-initdb.d/init-replication.sh /var/lib/mysql/init-replication.sh
   ```

2. **deploy 阶段**（`deploy.sh` 已有逻辑）：
   - 检查复制状态（第 981-1119 行）
   - 如果复制未配置，自动执行初始化
   - 支持 GTID 自动定位和错误恢复

**优势**：
- ✅ 避免 initdb 阶段的时序问题
- ✅ deploy.sh 自动检测并修复复制配置
- ✅ auto-update.sh 也会自动验证复制状态
- ✅ 支持手动修复：`docker exec <container> bash /var/lib/mysql/init-replication.sh`

---

### 7. MySQL 健康检查启动等待时间不足

**症状**：
- MySQL 容器状态显示 `unhealthy`
- 初始化脚本还在执行，但健康检查已经开始失败

**根因分析**：
- 主库 `start_period` 只有 60 秒
- 备份导入 + ECDICT 导入可能需要更长时间
- 健康检查在初始化完成前就开始计数失败

**修复方案**：
```yaml
# docker-compose.yml
mysql-master:
  healthcheck:
    start_period: 180s  # 增加到 3 分钟

mysql-slave:
  healthcheck:
    start_period: 240s  # 增加到 4 分钟
```

---
