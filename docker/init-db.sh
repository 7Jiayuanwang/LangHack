#!/bin/bash
# ─────────────────────────────────────────────
# LangHack MySQL 初始化脚本（主库和从库通用）
# 由 docker-entrypoint-initdb.d 自动执行（仅首次启动）
#
# 执行顺序由文件名前缀控制：
#   01-init-db.sh                      ← 本文件（设置字符集）
#   02-create-replication-user.sh      ← 创建主从复制用户（仅主库挂载）
#   03-import-backup.sql               ← 导入完整备份数据
#   04-apply-all-migrations.sql        ← 合并迁移脚本（V1~V9，全幂等）
#   05-import-ecdict.sh                ← 导入 ECDICT 词典（可选）
#   06-init-replication.sh             ← 从库复制初始化（仅从库挂载）
# ─────────────────────────────────────────────

set -e

echo "[init-db] ======================================"
echo "[init-db] LangHack 数据库初始化开始..."
echo "[init-db]   数据库: ${MYSQL_DATABASE}"
echo "[init-db]   时间:   $(date '+%Y-%m-%d %H:%M:%S')"
echo "[init-db] ======================================"

# 确保目标数据库使用 utf8mb4 字符集（避免中文乱码）
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    ALTER DATABASE \`${MYSQL_DATABASE}\`
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;
EOSQL

echo "[init-db] ✅ 字符集配置完成 (utf8mb4_unicode_ci)"

# 如果配置了非 root 的应用用户，自动创建并授权
# 这样 deploy.sh 中填写的 DB_USERNAME 无论是 root 还是自定义用户都能正常连接
if [ -n "${MYSQL_APP_USER}" ] && [ "${MYSQL_APP_USER}" != "root" ] && [ -n "${MYSQL_APP_PASSWORD}" ]; then
    echo "[init-db] 创建应用用户: ${MYSQL_APP_USER}"
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
        CREATE USER IF NOT EXISTS '${MYSQL_APP_USER}'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
        GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_APP_USER}'@'%';
        FLUSH PRIVILEGES;
EOSQL
    echo "[init-db] ✅ 应用用户创建完成: ${MYSQL_APP_USER}"
fi
echo "[init-db] 后续 SQL 文件将自动按文件名顺序执行："
echo "[init-db]   → 02: 主从复制用户创建"
echo "[init-db]   → 03: 历史备份数据导入"
echo "[init-db]   → 04: 合并迁移脚本（V1~V9）"
echo "[init-db]   → 05: ECDICT 词典导入（可选）"
echo "[init-db]   → 06: 从库复制初始化（仅从库）"
echo "[init-db] ======================================"
