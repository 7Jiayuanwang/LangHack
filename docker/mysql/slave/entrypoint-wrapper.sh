#!/bin/bash
# ─────────────────────────────────────────────
# LangHack MySQL 从库 Entrypoint Wrapper
# 每次容器启动时执行：启动 MySQL → 检查/修复复制 → 守护进程
#
# 解决重部署时 docker-entrypoint-initdb.d 脚本被跳过的问题：
#   旧数据卷存在 → MySQL entrypoint 跳过所有 init 脚本
#   → 复制状态可能因主库 binlog 轮转而失效
#   → 本 wrapper 确保复制每次都被检查/修复
#
# 修复策略（两种）：
#   A) 复制曾配置过 → 尝试 START REPLICA 重连（秒级，无数据损失）
#   B) 从未配置/A失败 → 运行完整 init-replication.sh（含 mysqldump + 导入）
# ─────────────────────────────────────────────

set -e

echo "[slave-wrapper] ======================================"
echo "[slave-wrapper] 从库 Entrypoint Wrapper 启动"
echo "[slave-wrapper] 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "[slave-wrapper] ======================================"

# 1. 启动 MySQL（使用原始 entrypoint），放入后台
echo "[slave-wrapper] 启动 MySQL..."
/usr/local/bin/docker-entrypoint.sh mysqld "$@" &
MYSQL_PID=$!

# 陷阱：向 MySQL 进程转发终止信号
trap 'echo "[slave-wrapper] 收到信号，转发给 MySQL (PID: ${MYSQL_PID})..."; kill -TERM ${MYSQL_PID}; wait ${MYSQL_PID}' TERM INT

# 2. 等待 MySQL 就绪
echo "[slave-wrapper] 等待 MySQL 就绪..."
MAX_WAIT=120
WAIT_COUNT=0
while ! mysqladmin ping -h localhost -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; do
    WAIT_COUNT=$((WAIT_COUNT + 1))
    if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
        echo "[slave-wrapper] ⚠️ MySQL 启动超时，跳过复制初始化，进入守护模式"
        MYSQL_TIMEOUT=1
        break
    fi
    sleep 2
done

if [ "${MYSQL_TIMEOUT:-0}" != "1" ]; then

echo "[slave-wrapper] ✅ MySQL 已就绪"

# 3. 检查复制 IO 线程当前状态
IO_RUNNING=$(mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e \
    "SELECT SERVICE_STATE FROM performance_schema.replication_connection_status WHERE CHANNEL_NAME='' LIMIT 1;" 2>/dev/null || echo "")

if [ "$IO_RUNNING" = "ON" ]; then
    echo "[slave-wrapper] ✅ 复制 IO 线程已在运行，无需干预"
else
    echo "[slave-wrapper] 复制未运行（IO线程状态: ${IO_RUNNING:-未配置}）"

    # 4. 判断是"重连"还是"全新配置"
    REPLICA_CONFIGURED=$(mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e \
        "SELECT COUNT(*) FROM mysql.slave_master_info WHERE Channel_name='' AND Host='mysql-master';" 2>/dev/null || echo "0")

    if [ "$REPLICA_CONFIGURED" -gt "0" ]; then
        echo "[slave-wrapper] 检测到已有复制配置，尝试 START REPLICA 重连..."
        mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "START REPLICA;" 2>/dev/null || true
        sleep 3

        IO_RETRY=$(mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e \
            "SELECT SERVICE_STATE FROM performance_schema.replication_connection_status WHERE CHANNEL_NAME='' LIMIT 1;" 2>/dev/null || echo "")

        if [ "$IO_RETRY" = "ON" ]; then
            echo "[slave-wrapper] ✅ 复制已成功重连"
        else
            echo "[slave-wrapper] ⚠️ START REPLICA 重连失败，原因可能是主库 binlog 已被清理"
            echo "[slave-wrapper] 回退到完整初始化（含 mysqldump + 导入，可能需要较长时间）..."
            if [ -f /opt/langhack/init-replication.sh ]; then
                bash /opt/langhack/init-replication.sh || echo "[slave-wrapper] ⚠️ 复制初始化返回非零"
            fi
        fi
    else
        echo "[slave-wrapper] 无历史复制配置，执行完整初始化..."
        if [ -f /opt/langhack/init-replication.sh ]; then
            bash /opt/langhack/init-replication.sh || echo "[slave-wrapper] ⚠️ 复制初始化返回非零"
        else
            echo "[slave-wrapper] ⚠️ 未找到 /opt/langhack/init-replication.sh，跳过复制配置"
        fi
    fi
fi
fi  # MYSQL_TIMEOUT guard

echo "[slave-wrapper] MySQL PID: ${MYSQL_PID}，进入守护模式..."
wait ${MYSQL_PID}
echo "[slave-wrapper] MySQL 已退出"
