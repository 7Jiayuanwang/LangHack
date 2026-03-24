#!/bin/bash
# ─────────────────────────────────────────────
# LangHack MySQL 初始化脚本（主库和从库通用）
# 由 docker-entrypoint-initdb.d 自动执行（仅首次启动）
#
# 执行顺序由文件名前缀控制：
#   01-init-db.sh                      ← 本文件（设置字符集 + 应用迁移）
#   02-create-replication-user.sql     ← 创建主从复制用户（仅主库挂载）
#   03-import-backup.sql               ← 导入完整备份数据
#   04-apply-all-migrations.sql        ← 合并迁移脚本（V1.2~V24）
# ─────────────────────────────────────────────

set -e

echo "[init-db] ======================================"
echo "[init-db] LangHack 数据库初始化开始..."
echo "[init-db] 数据库: ${MYSQL_DATABASE}"
echo "[init-db] 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "[init-db] ======================================"

# 确保目标数据库使用 utf8mb4 字符集
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    ALTER DATABASE \`${MYSQL_DATABASE}\`
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;
EOSQL

echo "[init-db] 字符集配置完成 (utf8mb4_unicode_ci)"
echo "[init-db] 后续 SQL 文件将自动按文件名顺序执行："
echo "[init-db]   → 02: 复制用户创建"
echo "[init-db]   → 03: 备份数据导入"
echo "[init-db]   → 04: 合并迁移应用（V1.2~V24）"
echo "[init-db] ======================================"
