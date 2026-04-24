#!/usr/bin/env bash
# ==============================================================================
# LangHack 公共 Docker 镜像同步到本地 registry
#
# 用法:
#   bash scripts/sync-docker-images.sh
#   bash scripts/sync-docker-images.sh --registry localhost:5000
#   bash scripts/sync-docker-images.sh --dry-run
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_ROOT/docker/.env"

REGISTRY="${LOCAL_REGISTRY:-}"
DRY_RUN=false
IMAGES=(
    "mysql:8.0"
    "redis:6-alpine"
    "maven:3.8-openjdk-17"
    "eclipse-temurin:17-jre-alpine"
    "node:20-alpine"
    "nginx:alpine"
)

load_env_value() {
    local key="$1" default="$2" file="${3:-$ENV_FILE}"
    if [ -f "$file" ]; then
        local val
        val=$(grep -E "^${key}=" "$file" 2>/dev/null | grep -v '^\s*#' | head -1 | cut -d'=' -f2- | LC_ALL=C sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[[:cntrl:]]//g')
        [ -n "$val" ] && echo "$val" && return
    fi
    echo "$default"
}

usage() {
    cat <<'USAGE'
LangHack Docker 镜像同步工具

用法:
  bash scripts/sync-docker-images.sh [选项]

选项:
  --registry <host:port>  本地 registry 地址，默认读取 LOCAL_REGISTRY 或 docker/.env
  --dry-run               只打印将执行的命令，不实际 pull/tag/push
  --help, -h              显示帮助

示例:
  bash scripts/sync-docker-images.sh --registry localhost:5000
  LOCAL_REGISTRY=192.168.1.10:5000 bash scripts/sync-docker-images.sh
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --registry)
            REGISTRY="${2:-}"
            [ -n "$REGISTRY" ] || { echo "[ERROR] --registry 需要参数"; exit 1; }
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "[ERROR] 未知参数: $1"
            usage
            exit 1
            ;;
    esac
done

if [ -z "$REGISTRY" ]; then
    REGISTRY=$(load_env_value "LOCAL_REGISTRY" "localhost:5000")
fi
REGISTRY="${REGISTRY#http://}"
REGISTRY="${REGISTRY#https://}"
REGISTRY="${REGISTRY%/}"

command -v docker &>/dev/null || { echo "[ERROR] Docker 未安装"; exit 1; }
docker info &>/dev/null || { echo "[ERROR] Docker daemon 未运行，请执行: sudo systemctl start docker"; exit 1; }

run_cmd() {
    if [ "$DRY_RUN" = true ]; then
        printf '  %q' "$@"
        printf '\n'
    else
        "$@"
    fi
}

echo "======================================"
echo " LangHack Docker 镜像同步"
echo "======================================"
echo "[INFO] 目标 registry: $REGISTRY"
[ "$DRY_RUN" = true ] && echo "[INFO] dry-run 模式，不会修改镜像"
echo ""

echo "[INFO] 同步镜像清单:"
printf '  - %s\n' "${IMAGES[@]}"
echo ""

for image in "${IMAGES[@]}"; do
    target="$REGISTRY/$image"
    echo "[INFO] 同步 $image → $target"
    run_cmd docker pull "$image"
    run_cmd docker tag "$image" "$target"
    run_cmd docker push "$target"
    if [ "$DRY_RUN" = false ]; then
        docker manifest inspect "$target" >/dev/null 2>&1 || {
            echo "[ERROR] 推送后无法从 registry 读取镜像清单: $target"
            exit 1
        }
    fi
    echo "[OK] $target"
    echo ""
done

echo "[SUCCESS] 镜像同步完成"
echo "[提示] 部署时可使用: IMAGE_SOURCE_MODE=local LOCAL_REGISTRY=$REGISTRY bash scripts/deploy.sh"
