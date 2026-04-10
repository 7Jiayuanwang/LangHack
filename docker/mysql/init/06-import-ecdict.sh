#!/bin/bash
# ─────────────────────────────────────────────
# ECDICT 词典数据自动导入
# 在 docker-entrypoint-initdb.d 中执行（仅首次启动）
# 从挂载的 CSV 文件批量导入 770K+ 词汇到 ecdict_words 表
# ─────────────────────────────────────────────

set -e

ECDICT_CSV="/docker-entrypoint-initdb.d/ecdict.csv"
DB_NAME="${MYSQL_DATABASE:-english_learning}"

# 从库跳过 ECDICT 导入：数据通过主从复制同步，无需重复导入
# 主库导入耗时 1-3 分钟，从库执行会导致容器超时崩溃
if [ "${MYSQL_ROLE:-}" = "slave" ]; then
    echo "[ecdict] ℹ️  从库环境，跳过 ECDICT 导入（数据将通过复制同步）"
    exit 0
fi

echo "[ecdict] ======================================"
echo "[ecdict] ECDICT 词典数据导入开始..."
echo "[ecdict] 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "[ecdict] ======================================"

# 检查 CSV 文件是否存在
if [ ! -f "$ECDICT_CSV" ]; then
    echo "[ecdict] ⚠️ CSV 文件不存在: $ECDICT_CSV，跳过导入"
    echo "[ecdict] 提示: 请确认 ECDICT-master/ecdict.csv 已正确挂载"
    exit 0
fi

CSV_LINES=$(wc -l < "$ECDICT_CSV")
echo "[ecdict] CSV 文件: $ECDICT_CSV ($CSV_LINES 行)"

# 检查表是否已有数据（幂等性）
EXISTING_COUNT=$(mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME" -N -e "SELECT COUNT(*) FROM ecdict_words;" 2>/dev/null || echo "0")

if [ "$EXISTING_COUNT" -gt "100000" ]; then
    echo "[ecdict] ✅ ecdict_words 表已有 $EXISTING_COUNT 条数据，跳过导入"
    exit 0
fi

echo "[ecdict] 当前数据量: $EXISTING_COUNT 条，开始导入..."

# 清空旧数据（如有少量残留）
if [ "$EXISTING_COUNT" -gt "0" ]; then
    echo "[ecdict] 清空 $EXISTING_COUNT 条残留数据..."
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME" -e "TRUNCATE TABLE ecdict_words;" 2>/dev/null
fi

# 启用 local_infile
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SET GLOBAL local_infile=ON;" 2>/dev/null

# 批量导入 CSV
echo "[ecdict] 正在导入 CSV 数据（约 770K 行，预计 1-3 分钟）..."
START_TIME=$(date +%s)

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" --local-infile=1 "$DB_NAME" <<-EOSQL
    LOAD DATA LOCAL INFILE '$ECDICT_CSV'
    INTO TABLE ecdict_words
    CHARACTER SET utf8mb4
    FIELDS TERMINATED BY ','
    OPTIONALLY ENCLOSED BY '"'
    LINES TERMINATED BY '\n'
    IGNORE 1 LINES
    (word, phonetic, definition, translation, pos, collins, oxford, tag, bnc, frq, exchange, @detail, @audio);
EOSQL

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# 验证导入结果
FINAL_COUNT=$(mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME" -N -e "SELECT COUNT(*) FROM ecdict_words;" 2>/dev/null || echo "0")

echo "[ecdict] ======================================"
echo "[ecdict] ✅ ECDICT 导入完成"
echo "[ecdict] 导入数据: $FINAL_COUNT 条"
echo "[ecdict] 耗时: ${DURATION} 秒"
echo "[ecdict] ======================================"
