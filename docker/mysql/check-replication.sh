#!/bin/bash
# ─────────────────────────────────────────────
# LangHack MySQL 主从复制状态检查脚本
# 用法: bash docker/mysql/check-replication.sh
# 需要在 Docker Compose 项目目录下运行
# ─────────────────────────────────────────────

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo " LangHack MySQL 主从复制状态检查"
echo " $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================"
echo ""

# 检查从库容器是否运行
if ! docker ps --format '{{.Names}}' | grep -q 'langhack-mysql-slave'; then
    echo -e "${RED}[ERROR] langhack-mysql-slave 容器未运行${NC}"
    exit 1
fi

# 获取复制状态
REPLICA_STATUS=$(docker exec langhack-mysql-slave mysql -u root -p"${MYSQL_ROOT_PASSWORD:-langhack123}" -e "SHOW REPLICA STATUS\G" 2>/dev/null || true)

if [ -z "$REPLICA_STATUS" ]; then
    echo -e "${RED}[ERROR] 无法获取复制状态${NC}"
    exit 1
fi

# 解析关键指标
IO_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_IO_Running:" | awk '{print $2}')
SQL_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_SQL_Running:" | awk '{print $2}')
SECONDS_BEHIND=$(echo "$REPLICA_STATUS" | grep "Seconds_Behind_Source:" | awk '{print $2}')
LAST_IO_ERROR=$(echo "$REPLICA_STATUS" | grep "Last_IO_Error:" | sed 's/.*Last_IO_Error: //')
LAST_SQL_ERROR=$(echo "$REPLICA_STATUS" | grep "Last_SQL_Error:" | sed 's/.*Last_SQL_Error: //')
GTID_EXECUTED=$(echo "$REPLICA_STATUS" | grep "Executed_Gtid_Set:" | sed 's/.*Executed_Gtid_Set: //')

# 输出结果
echo "── 复制线程状态 ──"
if [ "$IO_RUNNING" = "Yes" ]; then
    echo -e "  IO Thread:  ${GREEN}Running${NC}"
else
    echo -e "  IO Thread:  ${RED}${IO_RUNNING}${NC}"
    [ -n "$LAST_IO_ERROR" ] && echo -e "  IO Error:   ${RED}${LAST_IO_ERROR}${NC}"
fi

if [ "$SQL_RUNNING" = "Yes" ]; then
    echo -e "  SQL Thread: ${GREEN}Running${NC}"
else
    echo -e "  SQL Thread: ${RED}${SQL_RUNNING}${NC}"
    [ -n "$LAST_SQL_ERROR" ] && echo -e "  SQL Error:  ${RED}${LAST_SQL_ERROR}${NC}"
fi

echo ""
echo "── 复制延迟 ──"
if [ "$SECONDS_BEHIND" = "0" ]; then
    echo -e "  Seconds Behind Source: ${GREEN}0 (同步)${NC}"
elif [ "$SECONDS_BEHIND" = "NULL" ] || [ -z "$SECONDS_BEHIND" ]; then
    echo -e "  Seconds Behind Source: ${RED}NULL (复制中断)${NC}"
elif [ "$SECONDS_BEHIND" -gt 60 ]; then
    echo -e "  Seconds Behind Source: ${RED}${SECONDS_BEHIND}s (严重延迟!)${NC}"
elif [ "$SECONDS_BEHIND" -gt 10 ]; then
    echo -e "  Seconds Behind Source: ${YELLOW}${SECONDS_BEHIND}s (轻微延迟)${NC}"
else
    echo -e "  Seconds Behind Source: ${GREEN}${SECONDS_BEHIND}s${NC}"
fi

# 检查半同步复制状态
echo ""
echo "── 半同步复制 ──"
SEMI_SYNC=$(docker exec langhack-mysql-master mysql -u root -p"${MYSQL_ROOT_PASSWORD:-langhack123}" -N -e "SELECT VARIABLE_VALUE FROM performance_schema.global_variables WHERE VARIABLE_NAME='rpl_semi_sync_master_enabled';" 2>/dev/null || echo "N/A")
if [ "$SEMI_SYNC" = "ON" ]; then
    echo -e "  Master Semi-Sync: ${GREEN}ON${NC}"
else
    echo -e "  Master Semi-Sync: ${YELLOW}${SEMI_SYNC}${NC}"
fi

SEMI_SYNC_SLAVE=$(docker exec langhack-mysql-slave mysql -u root -p"${MYSQL_ROOT_PASSWORD:-langhack123}" -N -e "SELECT VARIABLE_VALUE FROM performance_schema.global_variables WHERE VARIABLE_NAME='rpl_semi_sync_slave_enabled';" 2>/dev/null || echo "N/A")
if [ "$SEMI_SYNC_SLAVE" = "ON" ]; then
    echo -e "  Slave Semi-Sync:  ${GREEN}ON${NC}"
else
    echo -e "  Slave Semi-Sync:  ${YELLOW}${SEMI_SYNC_SLAVE}${NC}"
fi

# 主从数据一致性快速检查
echo ""
echo "── 数据一致性 ──"
MASTER_GTID=$(docker exec langhack-mysql-master mysql -u root -p"${MYSQL_ROOT_PASSWORD:-langhack123}" -N -e "SELECT @@global.gtid_executed;" 2>/dev/null || echo "N/A")
SLAVE_GTID=$(docker exec langhack-mysql-slave mysql -u root -p"${MYSQL_ROOT_PASSWORD:-langhack123}" -N -e "SELECT @@global.gtid_executed;" 2>/dev/null || echo "N/A")

if [ "$MASTER_GTID" = "$SLAVE_GTID" ] && [ "$MASTER_GTID" != "N/A" ]; then
    echo -e "  GTID 一致性: ${GREEN}一致${NC}"
else
    echo -e "  Master GTID: $MASTER_GTID"
    echo -e "  Slave  GTID: $SLAVE_GTID"
    if [ "$MASTER_GTID" != "$SLAVE_GTID" ]; then
        echo -e "  GTID 一致性: ${YELLOW}存在差异（正在追赶中）${NC}"
    fi
fi

echo ""
echo "========================================"
# 总结
if [ "$IO_RUNNING" = "Yes" ] && [ "$SQL_RUNNING" = "Yes" ] && [ "$SECONDS_BEHIND" = "0" ]; then
    echo -e "${GREEN}✅ 主从复制状态正常${NC}"
elif [ "$IO_RUNNING" = "Yes" ] && [ "$SQL_RUNNING" = "Yes" ]; then
    echo -e "${YELLOW}⚠️ 主从复制运行中，存在延迟${NC}"
else
    echo -e "${RED}❌ 主从复制异常，需要排查${NC}"
fi
echo "========================================"
