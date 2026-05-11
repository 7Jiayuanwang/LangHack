#!/usr/bin/env bash
# 诊断 Docker Compose 环境变量加载问题

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DOCKER_DIR="$PROJECT_ROOT/docker"

echo "======================================"
echo " 环境变量诊断工具"
echo "======================================"
echo ""

echo "1. 检查 .env 文件是否存在"
if [ -f "$DOCKER_DIR/.env" ]; then
    echo "   ✓ $DOCKER_DIR/.env 存在"
    echo "   文件大小: $(wc -c < "$DOCKER_DIR/.env") 字节"
    echo "   行数: $(wc -l < "$DOCKER_DIR/.env")"
else
    echo "   ✗ $DOCKER_DIR/.env 不存在"
    exit 1
fi

echo ""
echo "2. 检查 .env 文件关键变量"
echo "   MYSQL_ROOT_PASSWORD: $(grep '^MYSQL_ROOT_PASSWORD=' "$DOCKER_DIR/.env" | cut -d= -f2 | head -c 10)..."
echo "   DB_USERNAME: $(grep '^DB_USERNAME=' "$DOCKER_DIR/.env" | cut -d= -f2)"
echo "   DB_PASSWORD: $(grep '^DB_PASSWORD=' "$DOCKER_DIR/.env" | cut -d= -f2 | head -c 10)..."

echo ""
echo "3. 检查 Docker Compose 是否能读取 .env"
cd "$DOCKER_DIR"
echo "   当前目录: $(pwd)"
echo "   测试读取 MYSQL_ROOT_PASSWORD:"
docker compose config | grep -A 2 "MYSQL_ROOT_PASSWORD:" | head -3

echo ""
echo "4. 检查运行中容器的环境变量"
if docker ps --format '{{.Names}}' | grep -q langhack-backend; then
    echo "   backend 容器的 DB_URL:"
    docker exec langhack-backend printenv DB_URL || echo "   (未设置)"
    echo "   backend 容器的 DB_USERNAME:"
    docker exec langhack-backend printenv DB_USERNAME || echo "   (未设置)"
else
    echo "   backend 容器未运行"
fi

echo ""
echo "======================================"
