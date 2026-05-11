#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────
# LangHack 增量备份延迟导入脚本
# 在 docker-entrypoint-initdb.d 中第 5 步执行（在 04-apply-all-migrations.sql 之后）
#
# 功能：
#   - 检查是否有待导入的增量备份（由 03-import-backup.sh 标记）
#   - 如果有，在表结构创建完成后导入增量数据
#   - 确保增量备份中的新表数据能正确导入
# ─────────────────────────────────────────────────────────────────────────

set -e

MARKER_FILE="/tmp/pending-incremental-backup.txt"
DB_NAME="${MYSQL_DATABASE:-english_learning}"

# 从库跳过增量备份导入：数据通过主从复制同步，无需重复导入
if [ "${MYSQL_ROLE:-}" = "slave" ]; then
    echo "[incr-seed] ℹ️  从库环境，跳过增量备份导入（数据将通过复制同步）"
    return 0
fi

echo "[incr-seed] ======================================"
echo "[incr-seed] 增量备份延迟导入检查..."
echo "[incr-seed] 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "[incr-seed] ======================================"

# 检查是否有待导入的增量备份
if [ ! -f "$MARKER_FILE" ]; then
    echo "[incr-seed] ℹ️  无待导入的增量备份"
    echo "[incr-seed] ======================================"
    exit 0
fi

SEED_FILE=$(cat "$MARKER_FILE")

if [ ! -f "$SEED_FILE" ]; then
    echo "[incr-seed] ⚠️  标记的备份文件不存在: $SEED_FILE"
    rm -f "$MARKER_FILE"
    echo "[incr-seed] ======================================"
    exit 0
fi

# 执行导入
FILE_SIZE=$(stat -c%s "$SEED_FILE" 2>/dev/null || echo "0")
FILE_SIZE_MB=$(( FILE_SIZE / 1024 / 1024 ))
echo "[incr-seed] 发现增量备份: $(basename "$SEED_FILE")"
echo "[incr-seed] 文件大小: ${FILE_SIZE_MB} MB"
echo "[incr-seed] 表结构已创建，开始导入增量数据..."
START_TIME=$(date +%s)

case "$SEED_FILE" in
    *.sql.gz)
        echo "[incr-seed] 检测到 gzip 压缩，解压后导入..."
        if ! command -v gunzip &>/dev/null && ! command -v zcat &>/dev/null; then
            echo "[incr-seed] [ERROR] 未找到 gunzip 或 zcat，无法解压 .sql.gz"
            exit 1
        fi
        zcat "$SEED_FILE" | mysql --default-character-set=utf8mb4 -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME"
        ;;
    *.sql)
        mysql --default-character-set=utf8mb4 -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME" < "$SEED_FILE"
        ;;
    *)
        echo "[incr-seed] [WARN] 未知文件扩展名: $SEED_FILE，尝试直接导入"
        mysql --default-character-set=utf8mb4 -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME" < "$SEED_FILE"
        ;;
esac

END_TIME=$(date +%s)
DURATION=$(( END_TIME - START_TIME ))

# 清理标记文件
rm -f "$MARKER_FILE"

echo "[incr-seed] ✅ 增量数据导入完成（耗时 ${DURATION}s）"
echo "[incr-seed] ======================================"
