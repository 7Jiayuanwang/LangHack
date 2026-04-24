#!/usr/bin/env bash
# ==============================================================================
# LangHack 公共 Docker 镜像同步到本地 registry
#
# 用法:
#   bash scripts/sync-docker-images.sh [选项]
#
# 选项:
#   --registry <host:port>  本地 registry 地址 (默认: localhost:5000)
#   --dry-run               只打印命令，不实际执行
#   --help, -h              显示帮助
#
# 环境变量:
#   LOCAL_REGISTRY          Registry 地址
#   REGISTRY_DATA_PATH      数据存储路径 (默认: /data/registry)
#
# 示例:
#   bash scripts/sync-docker-images.sh
#   bash scripts/sync-docker-images.sh --registry localhost:5000
#   REGISTRY_DATA_PATH=/data/registry bash scripts/sync-docker-images.sh
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

环境变量:
  LOCAL_REGISTRY          Registry 地址 (默认: localhost:5000)
  REGISTRY_DATA_PATH      数据存储路径 (默认: /data/registry)

示例:
  bash scripts/sync-docker-images.sh --registry localhost:5000
  LOCAL_REGISTRY=192.168.1.10:5000 bash scripts/sync-docker-images.sh
  REGISTRY_DATA_PATH=/data/registry bash scripts/sync-docker-images.sh
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

# 检查并启动本地 Registry（如果目标是 localhost 且 Registry 未运行）
# 环境变量:
#   REGISTRY_DATA_PATH - 数据存储路径，默认: /data/registry
ensure_local_registry() {
    local registry="$1"
    # 只处理 localhost/127.0.0.1 的 registry
    if [[ "$registry" == localhost:* ]] || [[ "$registry" == 127.0.0.1:* ]]; then
        local port="${registry##*:}"
        local port="${port:-5000}"
        local data_path="${REGISTRY_DATA_PATH:-/data/registry}"

        # 检查 Registry 是否可访问
        if ! curl -sf "http://${registry}/v2/_catalog" &>/dev/null; then
            echo "[WARN] Registry $registry 未启动，正在启动..."
            echo "[INFO] 数据存储路径: $data_path"

            # 检查是否存在已停止的 local-registry 容器
            if docker ps -a --format '{{.Names}}' | grep -q "^local-registry$"; then
                run_cmd docker start local-registry
            else
                # 确保数据目录存在
                if [ "$DRY_RUN" = false ]; then
                    mkdir -p "$data_path"
                fi
                run_cmd docker run -d \
                    -p "${port}:5000" \
                    --name local-registry \
                    --restart always \
                    -v "${data_path}:/var/lib/registry" \
                    registry:2
            fi

            # 等待 Registry 启动
            local retries=10
            while [ $retries -gt 0 ]; do
                if curl -sf "http://${registry}/v2/_catalog" &>/dev/null; then
                    echo "[OK] Registry $registry 已就绪"
                    return 0
                fi
                sleep 1
                retries=$((retries - 1))
            done

            echo "[ERROR] Registry 启动失败或无法连接: $registry"
            exit 1
        fi
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

# 确保本地 Registry 已启动
ensure_local_registry "$REGISTRY"

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
