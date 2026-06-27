#!/bin/bash
# ─────────────────────────────────────────────
# 授予 root 用户 REPLICATION CLIENT 权限
# 修复：DynamicDataSource 健康检查执行 SHOW REPLICA STATUS 需要此权限
# 日志错误：Access denied; you need (at least one of) the SUPER, REPLICATION CLIENT privilege(s)
# ─────────────────────────────────────────────

set -e

echo "[grant-repl-client] 授予 root@'%' REPLICATION CLIENT 权限"

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    -- root 用户应默认有此权限，但某些初始化流程可能遗漏
    -- 显式授予以确保 DynamicDataSource 健康检查正常
    GRANT REPLICATION CLIENT ON *.* TO 'root'@'%';
    GRANT REPLICATION CLIENT ON *.* TO 'root'@'localhost';

    FLUSH PRIVILEGES;
EOSQL

echo "[grant-repl-client] ✅ root 用户 REPLICATION CLIENT 权限已授予"
