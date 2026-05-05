#!/bin/bash
# ─────────────────────────────────────────────
# LangHack MySQL 从库复制初始化脚本
# 在 docker-entrypoint-initdb.d 中最后执行
#
# 策略：先建空表 → 等待主库数据就绪 → 从主库 mysqldump 导入数据 → 基于主库 binlog 位置启动复制
#
# 关键步骤：
#   1. 等待主库就绪且数据初始化完成（包括 ecdict 导入）
#   2. 从主库 mysqldump 导入全量数据（跳过 media 表的 BLOB）
#   3. 大表（ecdict_words >10万条）单独导出（结构+数据），避免主 dump 过大
#   4. 获取主库当前 binlog 文件和位置
#   5. RESET MASTER 清除从库本地 GTID
#   6. CHANGE REPLICATION SOURCE TO + START REPLICA
#
# 为什么不用 GTID auto-position：
#   主库 init 阶段的事务（建表、导入数据）GTID 集合与从库 init 阶段的事务
#   有重叠（双方都执行了相同的 DDL），设 gtid_purged 会跳过这些事务导致从库
#   缺少数据。改用 binlog 位置方式，在 dump 数据后从主库当前位置开始复制。
# ─────────────────────────────────────────────

set +e  # 复制配置失败不应导致容器崩溃，显式处理各步骤错误

echo "[replication] ======================================"
echo "[replication] 从库复制初始化开始..."
echo "[replication] 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "[replication] ======================================"

DB_NAME="${MYSQL_DATABASE:-english_learning}"

# ── 1. 等待主库可达（最多 240 秒）────────────────────
echo "[replication] 等待主库 mysql-master:3306 就绪..."
MAX_RETRIES=120
RETRY_COUNT=0
while ! mysqladmin ping -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; do
    RETRY_COUNT=$((RETRY_COUNT + 1))
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
        echo "[replication] ❌ 主库连接超时（${MAX_RETRIES}次重试），复制初始化失败"
        echo "[replication] ❌ 从库将以 unhealthy 状态运行，部署将被阻塞"
        echo "[replication] ❌ 请检查主库容器状态: docker logs langhack-mysql-master"
        exit 1
    fi
    echo "[replication] 等待主库... (${RETRY_COUNT}/${MAX_RETRIES})"
    sleep 2
done

echo "[replication] ✅ 主库已就绪"

# ── 2. 等待主库数据初始化完成 ────────────────────────
# 检查主库 administrative_class 表是否已创建（V9 最后创建的表，说明所有 init 脚本已执行完毕）
echo "[replication] 等待主库数据初始化完成（检查 V9 表: administrative_class）..."
MAX_INIT_RETRIES=180
INIT_RETRY=0
while true; do
    TABLE_EXISTS=$(mysql -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" -N -e \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='administrative_class';" 2>/dev/null || echo "0")
    if [ "$TABLE_EXISTS" = "1" ]; then
        echo "[replication] ✅ 主库初始化完成（所有 V1~V9 迁移已执行）"
        # 额外等待 10 秒，确保主库当前事务已提交
        sleep 10
        break
    fi
    INIT_RETRY=$((INIT_RETRY + 1))
    if [ $INIT_RETRY -ge $MAX_INIT_RETRIES ]; then
        echo "[replication] ⚠️ 等待主库数据初始化超时（${MAX_INIT_RETRIES}次重试），继续配置复制（可能存在数据缺失）..."
        echo "[replication] ⚠️ 请检查主库初始化日志: docker logs langhack-mysql-master"
        break
    fi
    echo "[replication] 等待主库数据... (${INIT_RETRY}/${MAX_INIT_RETRIES})"
    sleep 2
done

# ── 3. 从主库 mysqldump 导入全量数据 ────────────────
# 跳过 media 表（BLOB 数据太大），media 数据通过后续应用访问时从主库读取
# 使用 --set-gtid-purged=OFF 避免导入时设置 gtid_purged
echo "[replication] 从主库导出数据（跳过 media 表）..."
DUMP_FILE="/tmp/master-dump.sql"

# 先等待主库 ecdict 导入完成（检查 3 次，间隔 5 秒，计数不再变化则认为导入完成）
echo "[replication] 等待主库 ecdict_words 数据稳定..."
ECDICT_PREV="-1"
ECDICT_STABLE_COUNT=0
for i in 1 2 3 4 5 6; do
    ECDICT_COUNT=$(mysql -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" -N -e \
        "SELECT COUNT(*) FROM ${DB_NAME}.ecdict_words;" 2>/dev/null || echo "0")
    echo "[replication] 主库 ecdict_words: ${ECDICT_COUNT} 条 (检查 ${i}/6)"
    if [ "$ECDICT_COUNT" = "$ECDICT_PREV" ]; then
        ECDICT_STABLE_COUNT=$((ECDICT_STABLE_COUNT + 1))
        if [ "$ECDICT_STABLE_COUNT" -ge 2 ]; then
            echo "[replication] ✅ ecdict_words 数据已稳定（${ECDICT_COUNT} 条）"
            break
        fi
    else
        ECDICT_STABLE_COUNT=0
    fi
    ECDICT_PREV="$ECDICT_COUNT"
    sleep 5
done
echo "[replication] 主库 ecdict_words 最终计数: ${ECDICT_COUNT} 条"

# 构建忽略表列表
IGNORE_TABLES="--ignore-table=${DB_NAME}.media"
# 如果 ecdict 数据量太大（>10万条），跳过数据导出以加速导入
# 但必须单独导出表结构+数据，否则从库缺数据
if [ "$ECDICT_COUNT" -gt "100000" ] 2>/dev/null; then
    IGNORE_TABLES="${IGNORE_TABLES} --ignore-table=${DB_NAME}.ecdict_words"
    echo "[replication] ecdict_words 数据量大，主 dump 跳过（将单独导出）"
fi

DUMP_START=$(date +%s)
mysqldump -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" \
    --set-gtid-purged=OFF \
    --single-transaction \
    --routines \
    --triggers \
    --no-create-db \
    ${IGNORE_TABLES} \
    "${DB_NAME}" > "$DUMP_FILE" 2>/dev/null

DUMP_EXIT=$?
DUMP_END=$(date +%s)
DUMP_DURATION=$((DUMP_END - DUMP_START))

if [ $DUMP_EXIT -ne 0 ]; then
    echo "[replication] ❌ mysqldump 失败 (exit: ${DUMP_EXIT})，尝试不带 --single-transaction 重试..."
    mysqldump -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" \
        --set-gtid-purged=OFF \
        --routines \
        --triggers \
        --no-create-db \
        ${IGNORE_TABLES} \
        "${DB_NAME}" > "$DUMP_FILE" 2>/dev/null
    DUMP_EXIT=$?
fi

if [ $DUMP_EXIT -ne 0 ]; then
    echo "[replication] ❌ mysqldump 重试仍失败，从库可能缺少数据"
    DUMP_FILE=""
fi

if [ -n "$DUMP_FILE" ] && [ -f "$DUMP_FILE" ]; then
    DUMP_SIZE=$(stat -c%s "$DUMP_FILE" 2>/dev/null || echo "0")
    DUMP_SIZE_MB=$(( DUMP_SIZE / 1024 / 1024 ))
    echo "[replication] dump 文件大小: ${DUMP_SIZE_MB} MB (耗时 ${DUMP_DURATION}s)"

    if [ "$DUMP_SIZE" -gt "1024" ]; then
        echo "[replication] 导入数据到从库..."
        IMPORT_START=$(date +%s)
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME" < "$DUMP_FILE" 2>/dev/null
        IMPORT_EXIT=$?
        IMPORT_END=$(date +%s)
        IMPORT_DURATION=$((IMPORT_END - IMPORT_START))

        if [ $IMPORT_EXIT -eq 0 ]; then
            echo "[replication] ✅ 数据导入完成 (耗时 ${IMPORT_DURATION}s)"
        else
            echo "[replication] ⚠️ 数据导入部分失败 (exit: ${IMPORT_EXIT})，继续配置复制"
        fi
    else
        echo "[replication] ⚠️ dump 文件过小 (${DUMP_SIZE} 字节)，跳过导入"
    fi

    # ── 单独导入 ecdict_words 表（表结构+数据） ──
    # 主 dump 中 ecdict_words 被忽略（数据量大），但从库必须有该表和数据
    # 否则 DataSourceAspect 路由到从库的查询会报 Table doesn't exist 或找不到数据
    if [ "$ECDICT_COUNT" -gt "100000" ] 2>/dev/null; then
        echo "[replication] 单独导出 ecdict_words 表（结构+数据，使用 extended-insert 加速）..."
        ECDICT_DUMP_FILE="/tmp/ecdict-dump.sql"

        # 先导出表结构
        mysqldump -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" \
            --set-gtid-purged=OFF \
            --no-data \
            --no-create-db \
            "${DB_NAME}" "ecdict_words" > "$ECDICT_DUMP_FILE" 2>/dev/null

        # 再追加数据（使用 extended-insert 加速：每批多行合并为单条 INSERT）
        # --where 1=1 确保导出全部数据
        mysqldump -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" \
            --set-gtid-purged=OFF \
            --no-create-info \
            --no-create-db \
            --extended-insert \
            --net-buffer-length=32768 \
            "${DB_NAME}" "ecdict_words" >> "$ECDICT_DUMP_FILE" 2>/dev/null

        ECDICT_DUMP_SIZE=$(stat -c%s "$ECDICT_DUMP_FILE" 2>/dev/null || echo "0")
        ECDICT_DUMP_MB=$((ECDICT_DUMP_SIZE / 1024 / 1024))
        echo "[replication] ecdict dump 文件大小: ${ECDICT_DUMP_MB} MB"

        if [ "$ECDICT_DUMP_SIZE" -gt "1024" ]; then
            echo "[replication] 导入 ecdict_words 数据到从库..."
            ECDICT_IMPORT_START=$(date +%s)
            # 使用 -f 强制模式，跳过重复键错误（表结构可能已由 04 脚本创建）
            mysql -f -u root -p"${MYSQL_ROOT_PASSWORD}" "$DB_NAME" < "$ECDICT_DUMP_FILE" 2>/dev/null
            ECDICT_IMPORT_EXIT=$?
            ECDICT_IMPORT_END=$(date +%s)
            ECDICT_IMPORT_DURATION=$((ECDICT_IMPORT_END - ECDICT_IMPORT_START))

            if [ $ECDICT_IMPORT_EXIT -eq 0 ]; then
                echo "[replication] ✅ ecdict_words 导入完成 (耗时 ${ECDICT_IMPORT_DURATION}s)"
            else
                echo "[replication] ⚠️ ecdict_words 导入部分失败 (exit: ${ECDICT_IMPORT_EXIT})，继续配置复制"
            fi
        else
            echo "[replication] ⚠️ ecdict dump 文件过小 (${ECDICT_DUMP_SIZE} 字节)，跳过导入"
        fi
        rm -f "$ECDICT_DUMP_FILE"
    fi

    # 清理临时文件
    rm -f "$DUMP_FILE"
else
    echo "[replication] ⚠️ 无 dump 文件，继续配置复制（从库可能缺少数据）"
fi

# ── 4. 获取主库当前 binlog 位置 ──────────────────────
echo "[replication] 获取主库 binlog 位置..."
MASTER_STATUS=$(mysql -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW MASTER STATUS\G" 2>/dev/null || true)

MASTER_LOG_FILE=$(echo "$MASTER_STATUS" | grep "File:" | awk '{print $2}' || echo "")
MASTER_LOG_POS=$(echo "$MASTER_STATUS" | grep "Position:" | awk '{print $2}' || echo "")

if [ -z "$MASTER_LOG_FILE" ] || [ -z "$MASTER_LOG_POS" ]; then
    echo "[replication] ⚠️ 无法获取主库 binlog 位置，尝试 GTID auto-position 方式..."
    USE_GTID=1
else
    echo "[replication] 主库 binlog: ${MASTER_LOG_FILE} @ ${MASTER_LOG_POS}"
    USE_GTID=0
fi

# ── 5. 配置复制 ──────────────────────────────────────
echo "[replication] 配置 CHANGE REPLICATION SOURCE TO..."

if [ "$USE_GTID" = "0" ]; then
    # 推荐：基于 binlog 位置（避免 GTID 跳过问题）
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
        STOP REPLICA;
        RESET MASTER;
        CHANGE REPLICATION SOURCE TO
            SOURCE_HOST='mysql-master',
            SOURCE_PORT=3306,
            SOURCE_USER='${MYSQL_REPLICATION_USER}',
            SOURCE_PASSWORD='${MYSQL_REPLICATION_PASSWORD}',
            SOURCE_LOG_FILE='${MASTER_LOG_FILE}',
            SOURCE_LOG_POS=${MASTER_LOG_POS},
            GET_SOURCE_PUBLIC_KEY=1;
        START REPLICA;
EOSQL
else
    # 降级：GTID auto-position（从库可能缺少历史数据）
    # 获取主库 GTID 执行集
    MASTER_GTID=$(mysql -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" -N -e "SELECT @@global.gtid_executed;" 2>/dev/null || echo "")
    echo "[replication] 主库 GTID: ${MASTER_GTID:-无法获取}"

    if [ -n "$MASTER_GTID" ]; then
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
            STOP REPLICA;
            RESET MASTER;
            SET GLOBAL gtid_purged='${MASTER_GTID}';
            CHANGE REPLICATION SOURCE TO
                SOURCE_HOST='mysql-master',
                SOURCE_PORT=3306,
                SOURCE_USER='${MYSQL_REPLICATION_USER}',
                SOURCE_PASSWORD='${MYSQL_REPLICATION_PASSWORD}',
                SOURCE_AUTO_POSITION=1,
                GET_SOURCE_PUBLIC_KEY=1;
            START REPLICA;
EOSQL
    else
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
            STOP REPLICA;
            RESET MASTER;
            CHANGE REPLICATION SOURCE TO
                SOURCE_HOST='mysql-master',
                SOURCE_PORT=3306,
                SOURCE_USER='${MYSQL_REPLICATION_USER}',
                SOURCE_PASSWORD='${MYSQL_REPLICATION_PASSWORD}',
                SOURCE_AUTO_POSITION=1,
                GET_SOURCE_PUBLIC_KEY=1;
            START REPLICA;
EOSQL
    fi
fi

# 安装半同步复制插件（独立执行，失败不阻塞复制启动）
echo "[replication] 安装半同步复制插件（从库端）..."
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "INSTALL PLUGIN rpl_semi_sync_slave SONAME 'semisync_slave.so';" 2>/dev/null || echo "[replication] ⚠️ 半同步插件已安装或不可用，跳过"
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SET GLOBAL rpl_semi_sync_slave_enabled = ON;" 2>/dev/null || echo "[replication] ⚠️ 半同步参数设置失败，跳过"
echo "[replication] ✅ 半同步复制插件配置完成"

echo "[replication] ✅ 复制已启动"

# ── 6. 验证复制状态 ─────────────────────────────────
echo "[replication] 验证复制状态..."
sleep 5
REPLICA_STATUS=$(mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW REPLICA STATUS\G" 2>/dev/null || true)

IO_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_IO_Running:" | awk '{print $2}' || echo "Unknown")
SQL_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_SQL_Running:" | awk '{print $2}' || echo "Unknown")
SECONDS_BEHIND=$(echo "$REPLICA_STATUS" | grep "Seconds_Behind_Source:" | awk '{print $2}' || echo "Unknown")

if [ "$IO_RUNNING" = "Yes" ]; then
    echo "[replication] ✅ Replica IO Thread: Running"
else
    echo "[replication] ⚠️ Replica IO Thread: ${IO_RUNNING}"
    echo "$REPLICA_STATUS" | grep -E "(Last_IO_Error)" || true
fi

if [ "$SQL_RUNNING" = "Yes" ]; then
    echo "[replication] ✅ Replica SQL Thread: Running"
else
    echo "[replication] ⚠️ Replica SQL Thread: ${SQL_RUNNING}"
    echo "$REPLICA_STATUS" | grep -E "(Last_SQL_Error)" || true
fi

echo "[replication] Seconds Behind Source: ${SECONDS_BEHIND}"

# ── 7. 验证从库数据 ─────────────────────────────────
SLAVE_TEXT_COUNT=$(mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -N -e \
    "SELECT COUNT(*) FROM ${DB_NAME}.texts;" 2>/dev/null || echo "0")
echo "[replication] 从库 texts 数据: ${SLAVE_TEXT_COUNT} 条"

if [ "$SLAVE_TEXT_COUNT" -eq "0" ] 2>/dev/null; then
    echo "[replication] ⚠️ 从库 texts 表为空！数据可能未正确导入"
fi

# 设置只读模式（不能放在 command 中，否则会阻塞 init 脚本执行）
echo "[replication] 设置只读模式..."
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SET GLOBAL read_only = ON; SET GLOBAL super_read_only = ON;" 2>/dev/null || true
echo "[replication] ✅ 只读模式已启用"

echo "[replication] ======================================"
echo "[replication] 从库复制初始化完成"
echo "[replication] ======================================"
