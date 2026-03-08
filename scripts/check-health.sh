#!/usr/bin/env bash
# LangHack 健康检查脚本
set -euo pipefail

DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docker"

# 加载 .env 中的 MYSQL_ROOT_PASSWORD（如果存在）
if [ -f "$DOCKER_DIR/.env" ]; then
    # shellcheck disable=SC1091
    set -a
    source "$DOCKER_DIR/.env"
    set +a
fi

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"

echo "======================================"
echo " LangHack 健康状态检查"
echo "======================================"

check_service() {
    local name=$1
    local cmd=$2
    printf "  %-20s" "$name"
    if eval "$cmd" &>/dev/null; then
        echo "OK  正常"
    else
        echo "FAIL  异常"
    fi
}

# Spring Boot
check_service "Spring Boot" \
    "curl -sf http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"'"

# Redis
check_service "Redis" \
    "docker exec langhack-redis redis-cli ping | grep -q PONG"

# MySQL 主库
check_service "MySQL 主库" \
    "docker exec langhack-mysql-master mysqladmin ping -uroot -p${MYSQL_ROOT_PASSWORD} --silent"

# MySQL 从库
check_service "MySQL 从库" \
    "docker exec langhack-mysql-slave mysqladmin ping -uroot -p${MYSQL_ROOT_PASSWORD} --silent"

# 主从同步状态
echo ""
echo "[主从同步状态]"
docker exec langhack-mysql-slave mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    -e "SHOW REPLICA STATUS\G" 2>/dev/null | \
    grep -E "Replica_IO_Running|Replica_SQL_Running|Seconds_Behind" || \
    echo "  (无法获取同步状态，可能未配置主从复制)"

echo ""
echo "[连接池状态] 查看: http://localhost:8080/actuator/metrics/hikaricp.connections.active"
