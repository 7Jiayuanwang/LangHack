#!/usr/bin/env bash
# LangHack 一键部署脚本
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DOCKER_DIR="$PROJECT_ROOT/docker"

echo "======================================"
echo " LangHack 部署脚本 v1.0"
echo "======================================"

# 检查 Docker
command -v docker &>/dev/null || { echo "[ERROR] Docker 未安装，请先安装 Docker 20+"; exit 1; }
docker compose version &>/dev/null || { echo "[ERROR] Docker Compose V2 未安装"; exit 1; }

# 检查 .env 文件
if [ ! -f "$DOCKER_DIR/.env" ]; then
    echo "[INFO] .env 文件不存在，从模板创建..."
    cp "$DOCKER_DIR/.env.example" "$DOCKER_DIR/.env"
    echo "[WARN] 请编辑 $DOCKER_DIR/.env 填写真实配置后重新运行！"
    exit 1
fi

# 检查必填环境变量
source "$DOCKER_DIR/.env"
required_vars=(DB_PASSWORD REDIS_PASSWORD JWT_SECRET OPENROUTER_API_KEY)
for var in "${required_vars[@]}"; do
    val="${!var:-}"
    if [ -z "$val" ] || [[ "$val" == *"your_"* ]] || [[ "$val" == *"_here"* ]]; then
        echo "[ERROR] 环境变量 $var 未正确设置，请编辑 $DOCKER_DIR/.env"
        exit 1
    fi
done

echo "[INFO] 环境变量检查通过"

# 拉取最新镜像
cd "$DOCKER_DIR"
echo "[INFO] 拉取基础镜像..."
docker compose pull mysql-master mysql-slave redis 2>/dev/null || true

# 构建应用镜像
echo "[INFO] 构建应用镜像..."
docker compose build app

# 启动服务
echo "[INFO] 启动所有服务..."
docker compose up -d

# 等待服务健康
echo "[INFO] 等待服务启动（最多 120s）..."
timeout=120
elapsed=0
while [ $elapsed -lt $timeout ]; do
    if docker compose ps | grep -q "healthy"; then
        healthy_count=$(docker compose ps | grep -c "healthy" || true)
        if [ "$healthy_count" -ge 3 ]; then
            break
        fi
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    echo "  等待中... ${elapsed}s"
done

# 最终状态
echo ""
echo "======================================"
docker compose ps
echo "======================================"
echo ""
echo "[SUCCESS] 部署完成！"
echo "  应用地址: http://$(hostname -I | awk '{print $1}'):8080"
echo "  健康检查: http://$(hostname -I | awk '{print $1}'):8080/actuator/health"
echo ""
echo "[提示] 首次部署需配置 MySQL 主从复制，参考 docs/DEPLOYMENT.md"
