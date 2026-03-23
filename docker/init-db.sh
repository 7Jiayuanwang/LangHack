#!/bin/bash
# ─────────────────────────────────────────────
# LangHack MySQL 主库初始化脚本
# 由 docker-entrypoint-initdb.d 自动执行（仅首次启动）
#
# 执行顺序由文件名前缀控制：
#   01-init-db.sh        ← 本文件（设置字符集等基础配置）
#   02-create-replication-user.sql  ← 创建主从复制用户
#   03-import-backup.sql ← 导入完整备份数据
# ─────────────────────────────────────────────

set -e

echo "[init-db] LangHack 数据库初始化开始..."
echo "[init-db] 数据库: ${MYSQL_DATABASE}"

# 确保目标数据库使用 utf8mb4 字符集
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    ALTER DATABASE IF EXISTS \`${MYSQL_DATABASE}\`
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;
EOSQL

echo "[init-db] 字符集配置完成 (utf8mb4_unicode_ci)"
echo "[init-db] 后续 SQL 文件将自动按文件名顺序执行"
