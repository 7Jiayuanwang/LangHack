#!/usr/bin/env bash
# 验证本地 registry 镜像完整性并提供修复建议

set -euo pipefail

REGISTRY="${1:-localhost:5000}"

echo "======================================"
echo " Registry 镜像完整性检查"
echo "======================================"
echo "Registry: $REGISTRY"
echo ""

# 期望的镜像列表（与 sync-docker-images.sh 保持一致）
EXPECTED_IMAGES=(
    "mysql:8.0"
    "redis:6-alpine"
    "maven:3.8-openjdk-17"
    "eclipse-temurin:17-jre-alpine"
    "node:20-alpine"
    "nginx:alpine"
    "registry:2"
)

echo "[INFO] 检查 registry 连接..."
if ! curl -sf "http://${REGISTRY}/v2/_catalog" >/dev/null 2>&1; then
    echo "[ERROR] 无法连接到 registry: $REGISTRY"
    echo "[提示] 请确保 registry 正在运行: docker ps | grep registry"
    exit 1
fi
echo "[OK] Registry 连接正常"
echo ""

echo "[INFO] 检查镜像完整性..."
MISSING=0
INCOMPLETE=0

for image in "${EXPECTED_IMAGES[@]}"; do
    repo="${image%%:*}"
    tag="${image##*:}"

    # 检查仓库是否存在
    if ! curl -sf "http://${REGISTRY}/v2/${repo}/tags/list" 2>/dev/null | grep -q "\"${tag}\""; then
        echo "[MISSING] $image"
        MISSING=$((MISSING + 1))
    else
        echo "[OK] $image ✓"
    fi
done

echo ""
echo "======================================"
echo " 检查结果"
echo "======================================"
echo "期望镜像: ${#EXPECTED_IMAGES[@]} 个"
echo "缺失镜像: $MISSING 个"

if [ $MISSING -gt 0 ]; then
    echo ""
    echo "[ERROR] Registry 镜像不完整"
    echo ""
    echo "[修复] 运行以下命令同步镜像:"
    echo "  bash scripts/sync-docker-images.sh --registry $REGISTRY"
    echo ""
    echo "[强制重新同步] 如果镜像已存在但不完整:"
    echo "  bash scripts/sync-docker-images.sh --registry $REGISTRY --force"
    exit 1
else
    echo ""
    echo "[SUCCESS] 所有镜像完整 ✓"
    echo ""
    echo "[提示] 可以开始部署:"
    echo "  IMAGE_SOURCE_MODE=local LOCAL_REGISTRY=$REGISTRY bash scripts/deploy.sh"
fi
