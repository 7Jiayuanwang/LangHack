#!/bin/bash
# ─────────────────────────────────────────────
# LangHack MySQL 从库复制初始化脚本
# 在 docker-entrypoint-initdb.d 中最后执行（05-）
# 使用 GTID 自动定位，无需手动指定 binlog 文件和位置
#
# 关键步骤：
#   1. 等待主库就绪
#   2. 获取主库的 GTID 执行集
#   3. RESET MASTER 清除从库本地 GTID（因为主从各自执行了相同的 init 脚本）
#   4. SET gtid_purged = 主库 GTID 集（告诉从库"这些事务我已经有了"）
#   5. CHANGE REPLICATION SOURCE TO + START REPLICA
# ─────────────────────────────────────────────

set +e  # 复制配置失败不应导致容器崩溃，显式处理各步骤错误

echo "[replication] ======================================"
echo "[replication] 从库复制初始化开始..."
echo "[replication] 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "[replication] ======================================"

# 等待主库可达（最多 60 秒）
echo "[replication] 等待主库 mysql-master:3306 就绪..."
MAX_RETRIES=30
RETRY_COUNT=0
while ! mysqladmin ping -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; do
    RETRY_COUNT=$((RETRY_COUNT + 1))
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
        echo "[replication] ❌ 主库连接超时（${MAX_RETRIES}次重试），复制初始化失败"
        echo "[replication] ❌ 从库将以 unhealthy 状态运行，部署将被阻塞"
        exit 1  # 必须失败，让 healthcheck 检测到并阻塞部署
    fi
    echo "[replication] 等待主库... (${RETRY_COUNT}/${MAX_RETRIES})"
    sleep 2
done

echo "[replication] ✅ 主库已就绪"

# 等待主库数据库初始化完成（检查 V16 最后创建的表，确保所有迁移脚本已执行完毕）
# 主库健康检查只验证 MySQL 进程存活，init 脚本可能仍在执行
# 若此时捕获 GTID 集合不完整，从库会尝试复制主库后续的 init 事务，导致"表已存在"等复制错误
echo "[replication] 等待主库数据库初始化完成（V16 表: administrative_class）..."
MAX_INIT_RETRIES=90
INIT_RETRY=0
while true; do
    TABLE_EXISTS=$(mysql -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" -N -e \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='english_learning' AND table_name='administrative_class';" 2>/dev/null || echo "0")
    if [ "$TABLE_EXISTS" = "1" ]; then
        echo "[replication] ✅ 主库初始化完成"
        break
    fi
    INIT_RETRY=$((INIT_RETRY + 1))
    if [ $INIT_RETRY -ge $MAX_INIT_RETRIES ]; then
        echo "[replication] ⚠️ 等待主库初始化超时，继续配置复制（可能存在复制错误）..."
        break
    fi
    echo "[replication] 等待主库初始化... (${INIT_RETRY}/${MAX_INIT_RETRIES})"
    sleep 2
done

# 获取主库的 GTID 执行集
echo "[replication] 获取主库 GTID 执行集..."
MASTER_GTID=$(mysql -h mysql-master -u root -p"${MYSQL_ROOT_PASSWORD}" -N -e "SELECT @@global.gtid_executed;" 2>/dev/null || echo "")

if [ -z "$MASTER_GTID" ]; then
    echo "[replication] ⚠️ 无法获取主库 GTID 集，尝试不设置 gtid_purged 直接配置..."
else
    echo "[replication] 主库 GTID: ${MASTER_GTID}"
fi

# 配置复制
echo "[replication] 配置 CHANGE REPLICATION SOURCE TO..."

if [ -n "$MASTER_GTID" ]; then
    # 标准流程：清除从库本地 GTID + 设置 gtid_purged + 启动复制
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
    # 降级流程：无法获取 GTID，直接用 auto-position 尝试
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

# 安装半同步复制插件（独立执行，失败不阻塞复制启动）
echo "[replication] 安装半同步复制插件（从库端）..."
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "INSTALL PLUGIN rpl_semi_sync_slave SONAME 'semisync_slave.so';" 2>/dev/null || echo "[replication] ⚠️ 半同步插件已安装或不可用，跳过"
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SET GLOBAL rpl_semi_sync_slave_enabled = ON;" 2>/dev/null || echo "[replication] ⚠️ 半同步参数设置失败，跳过"
echo "[replication] ✅ 半同步复制插件配置完成"

echo "[replication] ✅ 复制已启动"

# 验证复制状态
echo "[replication] 验证复制状态..."
sleep 3
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

# 设置只读模式（不能放在 command 中，否则会阻塞 init 脚本执行）
echo "[replication] 设置只读模式..."
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" -e "SET GLOBAL read_only = ON; SET GLOBAL super_read_only = ON;" 2>/dev/null || true
echo "[replication] ✅ 只读模式已启用"

echo "[replication] ======================================"
echo "[replication] 从库复制初始化完成"
echo "[replication] ======================================"
