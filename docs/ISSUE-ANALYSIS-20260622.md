# LangHack 2026-06-22 日志问题排查与修复报告

## 问题概览

根据生产日志 `langhack-20260622-142811.log` 分析，发现三个主要问题：

### 问题 1: 学生表字段长度不足（P0 严重）
**症状**：
- `counselor_name` 字段：2808 次 `Data truncation: Data too long for column` 错误
- `phone` 字段：32 次数据截断错误

**根因**：
- V12 迁移文件定义：`counselor_name VARCHAR(50)`，`phone VARCHAR(50)`
- CDSP API 实际返回数据超过 50 字符：
  - `phone`：可能返回多个号码拼接（如 "13800138000,18900189000"）
  - `counselor_name`：可能包含多个辅导员或职务信息（如 "张三,李四" 或 "张三（兼职辅导员）"）

**影响**：
- 学生数据同步失败，导致用户信息不完整
- 前端 `/ops/school-data-browser` 页面加载异常

**修复**：
- 创建 V21 迁移：扩展字段长度到 VARCHAR(100)
- 同步更新 `docker/mysql/init/04-apply-all-migrations.sql`
- 提交：`3106d1ee` - "fix(db): 扩展 school_student 字段长度修复数据截断错误"

### 问题 2: 从库权限错误（P1 高频）
**症状**：
- 每分钟触发一次：`Access denied; you need (at least one of) the SUPER, REPLICATION CLIENT privilege(s) for this operation`
- 从库持续降级到主库，读写分离失效

**根因**：
- `DynamicDataSource.checkSlaveReplication()` 执行 `SHOW REPLICA STATUS` 检查从库健康状态
- 应用使用 `root` 用户连接从库，但该用户缺少 `REPLICATION CLIENT` 权限
- 复制用户 `replicator` 有此权限，但应用未使用

**影响**：
- 从库无法正常提供只读查询服务
- 所有查询被路由到主库，增加主库负载

**修复**：
- 创建 `docker/mysql/init/01-grant-replication-client-to-root.sh`
- 显式授予 `root@'%'` 和 `root@'localhost'` REPLICATION CLIENT 权限
- 提交：`8b82c99a` - "fix(db): 授予 root 用户 REPLICATION CLIENT 权限"

### 问题 3: 前端 uncaught_error（次要，衍生问题）
**症状**：
- `/ops/school-data-browser` 页面报 `uncaught_error`，`message: "null"`

**根因**：
- 后端数据截断错误导致 API 返回 500 错误
- 前端未捕获网络异常

**状态**：
- 问题 1 修复后，此问题应自动解决
- 如持续出现，需检查前端错误处理逻辑

## 部署建议

### 立即部署（生产环境）
```bash
git pull origin develop
docker compose down
docker compose up -d mysql-master mysql-slave
# 等待数据库初始化完成（约 30 秒）
docker compose up -d
```

### 验证步骤
1. 检查从库权限：
```bash
docker exec langhack-mysql-master mysql -u root -p'密码' \
  -e "SELECT user, host, Super_priv, Repl_client_priv FROM mysql.user WHERE user='root';"
```

2. 检查字段长度：
```bash
docker exec langhack-mysql-master mysql -u root -p'密码' english_learning \
  -e "SHOW COLUMNS FROM school_student WHERE Field IN ('phone', 'counselor_name');"
```

3. 监控日志：
```bash
docker logs -f langhack-backend --tail 100 | grep -E "DataIntegrityViolation|Cannot connect to slave"
```

### 预期结果
- ✅ 无 `Data too long for column` 错误
- ✅ 无 `Access denied; you need REPLICATION CLIENT` 错误
- ✅ 前端 `/ops/school-data-browser` 页面正常加载

## 附加改进建议

1. **字段长度监控**：建立数据质量监控，定期检查字段实际使用长度
2. **权限审计**：确保所有数据库用户权限符合最小权限原则
3. **前端错误处理**：增强 API 错误捕获和用户友好提示

---

生成时间：2026-06-25  
修复人：AI Assistant  
涉及提交：3106d1ee, 8b82c99a
