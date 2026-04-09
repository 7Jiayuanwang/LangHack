#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────
# LangHack 数据库种子导入脚本
# 在 docker-entrypoint-initdb.d 中第 3 步执行（仅首次启动）
#
# 导入策略：
#   从 seed/（database/）和 seed-backups/（backups/）两个目录中，
#   按文件修改时间选择最新的一个 .sql 或 .sql.gz 文件导入。
#
#   - 全量备份（FULL）：包含完整表结构和数据，可直接导入
#   - 增量备份（INCREMENTAL）：仅包含数据变更，需要先有表结构
#
# 查找优先级：
#   1. DB_SEED_SQL 环境变量指定的文件
#   2. seed/ + seed-backups/ 中修改时间最新的 .sql 或 .sql.gz
#   3. 传统固定路径：/docker-entrypoint-initdb.d/03-import-backup.sql
#
# 增量备份处理：
#   - 检测到增量备份时，延迟到 04-apply-all-migrations.sql 之后导入
#   - 创建标记文件供后续步骤使用
#
# 如果找不到有效的种子文件，打印提示后继续（不报错），
# 后续 04-apply-all-migrations.sql 会创建所有必要的表结构。
# ─────────────────────────────────────────────────────────────────────────

set -e

echo "[seed] ======================================"
echo "[seed] 数据库种子导入..."
echo "[seed] 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "[seed] ======================================"

DB_NAME="${MYSQL_DATABASE:-english_learning}"
SEED_FILE=""

SEED_DIR="/docker-entrypoint-initdb.d/seed"
BACKUP_DIR="/docker-entrypoint-initdb.d/seed-backups"

# ── 查找种子文件 ──────────────────────────────────────

# 优先级 1：环境变量
if [ -n "${DB_SEED_SQL:-}" ] && [ -f "$DB_SEED_SQL" ]; then
    SEED_FILE="$DB_SEED_SQL"
    echo "[seed] 使用环境变量指定文件: $SEED_FILE"
fi

# 优先级 2：跨 seed/ 和 seed-backups/ 找修改时间最新的 .sql / .sql.gz
if [ -z "$SEED_FILE" ]; then
    CANDIDATES=""
    for dir in "$SEED_DIR" "$BACKUP_DIR"; do
        if [ -d "$dir" ]; then
            # 收集 .sql 和 .sql.gz（只收集常规文件，排除目录）
            for pattern in "*.sql" "*.sql.gz"; do
                for file in "$dir"/$pattern; do
                    # 检查文件是否存在且为常规文件（非目录）
                    if [ -f "$file" ]; then
                        CANDIDATES="${CANDIDATES}${file}"$'\n'
                    fi
                done
            done
        fi
    done

    if [ -n "$CANDIDATES" ]; then
        # 按修改时间降序排列，取最新的
        SEED_FILE=$(echo "$CANDIDATES" | tr -s '\n' | grep -v '^$' \
            | xargs -I{} stat -c '%Y {}' {} 2>/dev/null \
            | sort -rn | head -1 | awk '{print $2}' || true)

        # 如果 stat 输出格式不对，回退到简单的 ls -t 排序
        if [ -z "$SEED_FILE" ] || [ ! -f "$SEED_FILE" ]; then
            SEED_FILE=$(echo "$CANDIDATES" | tr -s '\n' | grep -v '^$' \
                | xargs ls -t 2>/dev/null | head -1 || true)
        fi

        [ -n "$SEED_FILE" ] && [ -f "$SEED_FILE" ] && echo "[seed] 发现最新备份文件: $(basename "$SEED_FILE")"
    fi
fi

# 优先级 3：传统固定路径
if [ -z "$SEED_FILE" ]; then
    LEGACY="/docker-entrypoint-initdb.d/03-import-backup.sql"
    if [ -f "$LEGACY" ]; then
        SEED_FILE="$LEGACY"
        echo "[seed] 使用传统固定路径: $SEED_FILE"
    fi
fi

# ── 无种子文件 ────────────────────────────────────────
if [ -z "$SEED_FILE" ]; then
    echo "[seed] ℹ️  未找到种子 SQL 文件，跳过导入"
    echo "[seed] 后续 04-apply-all-migrations.sql 将创建所有必要的表结构"
    echo "[seed] ======================================"
    exit 0
fi

# ── 检测 LFS 指针 ────────────────────────────────────
FILE_SIZE=$(stat -c%s "$SEED_FILE" 2>/dev/null || echo "0")
FIRST_BYTES=$(head -c 40 "$SEED_FILE" 2>/dev/null || true)

if echo "$FIRST_BYTES" | grep -q "^version https://git-lfs"; then
    echo "[seed] ⚠️  检测到 Git LFS 指针文件（未拉取 LFS 内容），跳过导入"
    echo "[seed] 请在服务器上执行: git lfs pull"
    echo "[seed] 后续 04-apply-all-migrations.sql 将创建所有必要的表结构"
    echo "[seed] ======================================"
    exit 0
fi

if [ "$FILE_SIZE" -lt 1024 ]; then
    echo "[seed] ⚠️  种子文件过小 (${FILE_SIZE} 字节)，可能不完整，跳过导入"
    echo "[seed] ======================================"
    exit 0
fi

# ── 检测备份类型 ─────────────────────────────────────
# 检查是否为增量备份（文件头包含 "Incremental backup"）
BACKUP_TYPE="FULL"
if head -5 "$SEED_FILE" 2>/dev/null | grep -qi "Incremental backup"; then
    BACKUP_TYPE="INCREMENTAL"
    echo "[seed] 检测到增量备份，需要先创建表结构"
fi

# 如果是增量备份，延迟到 04-apply-all-migrations.sql 之后导入
if [ "$BACKUP_TYPE" = "INCREMENTAL" ]; then
    echo "[seed] ⏸️  增量备份将在表结构创建后导入"
    echo "[seed] 创建延迟导入标记: /tmp/pending-incremental-backup.txt"
    echo "$SEED_FILE" > /tmp/pending-incremental-backup.txt
    echo "[seed] ======================================"
    exit 0
fi

# ── 执行导入（仅全量备份）─────────────────────────────
FILE_SIZE_MB=$(( FILE_SIZE / 1024 / 1024 ))
echo "[seed] 文件大小: ${FILE_SIZE_MB} MB，开始导入..."
echo "[seed] 备份类型: ${BACKUP_TYPE}"
START_TIME=$(date +%s)

case "$SEED_FILE" in
    *.sql.gz)
        echo "[seed] 检测到 gzip 压缩，解压后导入..."
        if ! command -v gunzip &>/dev/null && ! command -v zcat &>/dev/null; then
            echo "[seed] [ERROR] 未找到 gunzip 或 zcat，无法解压 .sql.gz"
            exit 1
        fi
        zcat "$SEED_FILE" | mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME"
        ;;
    *.sql)
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME" < "$SEED_FILE"
        ;;
    *)
        echo "[seed] [WARN] 未知文件扩展名: $SEED_FILE，尝试直接导入"
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME" < "$SEED_FILE"
        ;;
esac

END_TIME=$(date +%s)
DURATION=$(( END_TIME - START_TIME ))

echo "[seed] ✅ 种子数据导入完成（耗时 ${DURATION}s）"
echo "[seed] ======================================"
