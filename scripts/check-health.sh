#!/usr/bin/env bash
# LangHack 健康检查脚本
set -euo pipefail

DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docker"

# 加载 .env 中的 MYSQL_ROOT_PASSWORD（安全读取，避免 JDBC URL 中 & 被 shell 解释）
if [ -f "$DOCKER_DIR/.env" ]; then
    while IFS='=' read -r key value; do
        [[ "$key" =~ ^[[:space:]]*# ]] && continue
        [[ -z "$key" ]] && continue
        key=$(echo "$key" | xargs)
        if [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
            export "$key=$value"
        fi
    done < "$DOCKER_DIR/.env"
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

# Spring Boot（与 docker-compose.yml healthcheck 保持一致）
check_service "Spring Boot" \
    "curl -sf http://localhost:8080/api/health"

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
