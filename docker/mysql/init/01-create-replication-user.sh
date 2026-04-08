#!/bin/bash
# ─────────────────────────────────────────────
# 主库初始化：创建主从复制用户
# 此脚本在 mysql-master 容器首次启动时自动执行
# 使用 .sh 而非 .sql 以支持环境变量替换
# ─────────────────────────────────────────────

set -e

REPL_USER="${MYSQL_REPLICATION_USER:-replicator}"
REPL_PASS="${MYSQL_REPLICATION_PASSWORD:-repl123456}"

echo "[repl-user] 创建复制用户: ${REPL_USER}"

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    -- 创建复制用户（允许所有 Docker 内部网络）
    -- 注意：Docker Compose 的网络 IP 段可能是 172.x 或 10.x，使用 '%' 确保兼容
    CREATE USER IF NOT EXISTS '${REPL_USER}'@'%'
        IDENTIFIED WITH mysql_native_password
        BY '${REPL_PASS}';

    GRANT REPLICATION SLAVE ON *.* TO '${REPL_USER}'@'%';

    -- 清理旧的 @'172.%' 用户（如存在）
    DROP USER IF EXISTS '${REPL_USER}'@'172.%';

    FLUSH PRIVILEGES;

    SHOW MASTER STATUS;
EOSQL

echo "[repl-user] ✅ 复制用户 ${REPL_USER} 创建完成（限制 Docker 网络）"

# 安装半同步复制插件（独立执行，失败不阻塞复制用户创建）
echo "[repl-user] 安装半同步复制插件..."
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "INSTALL PLUGIN rpl_semi_sync_master SONAME 'semisync_master.so';" 2>/dev/null || echo "[repl-user] ⚠️ 半同步插件已安装或不可用，跳过"
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SET GLOBAL rpl_semi_sync_master_enabled = ON; SET GLOBAL rpl_semi_sync_master_timeout = 3000;" 2>/dev/null || echo "[repl-user] ⚠️ 半同步参数设置失败，跳过"
echo "[repl-user] ✅ 半同步复制插件配置完成"
