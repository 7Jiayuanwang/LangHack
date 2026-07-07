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
FORCE_SYNC=false

# 镜像清单（按用途分类）
# 1. 基础设施服务（docker-compose.yml）
# 2. 构建阶段镜像（Dockerfile FROM）
# 3. 探测镜像（deploy.sh 网络探测）
# 4. Registry 自身（ensure_local_registry）
IMAGES=(
    # ── 基础设施服务 ──
    "mysql:8.0"                      # MySQL 主从库
    "redis:7-alpine"                 # Redis 缓存（升级自 6-alpine：修复 CVE-2026-25243 / CVE-2026-23631）

    # ── 后端构建 & 运行 ──
    "maven:3.8-openjdk-17"           # 后端构建阶段（backend/Dockerfile stage 1）
    "eclipse-temurin:17-jre-alpine"  # 后端运行阶段（backend/Dockerfile stage 2）

    # ── 前端构建 & 运行 ──
    "node:20-alpine"                 # 前端构建阶段（frontend/Dockerfile stage 1）+ 探测镜像
    "nginx:alpine"                   # 前端运行阶段（frontend/Dockerfile stage 2）

    # ── Registry 服务 ──
    "registry:2"                     # 本地 Docker Registry（ensure_local_registry 自动启动）
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
  --force                 强制重新同步所有镜像（即使已存在于 registry）
  --help, -h              显示帮助

环境变量:
  LOCAL_REGISTRY          Registry 地址 (默认: localhost:5000)
  REGISTRY_DATA_PATH      数据存储路径 (默认: /data/registry)

示例:
  bash scripts/sync-docker-images.sh --registry localhost:5000
  bash scripts/sync-docker-images.sh --force  # 强制重新同步所有镜像
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
        --force)
            FORCE_SYNC=true
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
                echo "[INFO] 发现已停止的 local-registry 容器，正在启动..."
                run_cmd docker start local-registry
            else
                # 确保 registry:2 镜像存在
                if ! docker image inspect registry:2 &>/dev/null; then
                    echo "[INFO] registry:2 镜像不存在，正在拉取..."
                    if [ "$DRY_RUN" = false ]; then
                        if ! docker pull registry:2; then
                            echo "[ERROR] 无法拉取 registry:2 镜像"
                            echo "[提示] 请检查网络连接或手动拉取: docker pull registry:2"
                            exit 1
                        fi
                    else
                        echo "  docker pull registry:2"
                    fi
                fi

                # 确保数据目录存在
                if [ "$DRY_RUN" = false ]; then
                    mkdir -p "$data_path"
                fi

                echo "[INFO] 创建并启动 local-registry 容器..."
                run_cmd docker run -d \
                    -p "${port}:5000" \
                    --name local-registry \
                    --restart always \
                    -v "${data_path}:/var/lib/registry" \
                    registry:2
            fi

            # 等待 Registry 启动
            local retries=10
            echo "[INFO] 等待 Registry 启动（最多 10 秒）..."
            while [ $retries -gt 0 ]; do
                if curl -sf "http://${registry}/v2/_catalog" &>/dev/null; then
                    echo "[OK] Registry $registry 已就绪 ✓"
                    return 0
                fi
                sleep 1
                retries=$((retries - 1))
            done

            echo "[ERROR] Registry 启动失败或无法连接: $registry"
            echo "[排查] 请检查:"
            echo "  1. 容器状态: docker ps -a | grep local-registry"
            echo "  2. 容器日志: docker logs local-registry"
            echo "  3. 端口占用: ss -tlnp | grep :${port} 或 netstat -tlnp | grep :${port}"
            echo "  4. 防火墙: sudo firewall-cmd --list-ports 或 sudo iptables -L"
            echo "[修复] 手动启动:"
            echo "  docker run -d -p ${port}:5000 --name local-registry --restart always \\"
            echo "    -v ${data_path}:/var/lib/registry registry:2"
            exit 1
        else
            echo "[OK] Registry $registry 已在运行 ✓"
        fi
    else
        echo "[INFO] 使用远程 registry: $registry"
        echo "[提示] 确保远程 registry 可访问，否则同步将失败"
    fi
}

echo "======================================"
echo " LangHack Docker 镜像同步"
echo "======================================"
echo "[INFO] 目标 registry: $REGISTRY"
[ "$DRY_RUN" = true ] && echo "[INFO] dry-run 模式，不会修改镜像"
[ "$FORCE_SYNC" = true ] && echo "[INFO] 强制同步模式，将重新同步所有镜像"
echo ""

echo "[INFO] 同步镜像清单 (共 ${#IMAGES[@]} 个):"
printf '  - %s\n' "${IMAGES[@]}"
echo ""

# 确保本地 Registry 已启动
ensure_local_registry "$REGISTRY"

# 检查 registry 中已有的镜像（用于跳过已同步的镜像）
echo "[INFO] 检查 registry 中已有的镜像..."
EXISTING_IMAGES=()
if [ "$DRY_RUN" = false ]; then
    # 获取 registry 中所有仓库
    repos=$(curl -sf "http://${REGISTRY}/v2/_catalog" 2>/dev/null | grep -o '"repositories":\[[^]]*\]' | grep -o '"[^"]*"' | grep -v repositories | tr -d '"' || true)
    if [ -n "$repos" ]; then
        for repo in $repos; do
            # 获取每个仓库的标签
            tags=$(curl -sf "http://${REGISTRY}/v2/${repo}/tags/list" 2>/dev/null | grep -o '"tags":\[[^]]*\]' | grep -o '"[^"]*"' | grep -v tags | tr -d '"' || true)
            if [ -n "$tags" ]; then
                for tag in $tags; do
                    EXISTING_IMAGES+=("${repo}:${tag}")
                done
            fi
        done
        echo "[INFO] Registry 中已有 ${#EXISTING_IMAGES[@]} 个镜像"
    else
        echo "[INFO] Registry 为空，将同步所有镜像"
    fi
fi
echo ""

# 统计变量
TOTAL=${#IMAGES[@]}
SUCCESS=0
FAILED=0
SKIPPED=0
FAILED_IMAGES=()

for image in "${IMAGES[@]}"; do
    target="$REGISTRY/$image"

    # 检查镜像是否已存在于 registry
    if [ "$DRY_RUN" = false ] && [ "$FORCE_SYNC" = false ]; then
        image_exists=false
        for existing in "${EXISTING_IMAGES[@]}"; do
            if [ "$existing" = "$image" ]; then
                image_exists=true
                break
            fi
        done

        if [ "$image_exists" = true ]; then
            echo "[SKIP] $image 已存在于 registry，跳过（使用 --force 强制重新同步）"
            SKIPPED=$((SKIPPED + 1))
            SUCCESS=$((SUCCESS + 1))
            echo ""
            continue
        fi
    fi

    echo "[INFO] 同步 $image → $target"

    # Pull 阶段
    if ! run_cmd docker pull "$image"; then
        echo "[ERROR] 拉取失败: $image"
        FAILED=$((FAILED + 1))
        FAILED_IMAGES+=("$image (pull failed)")
        echo ""
        continue
    fi

    # Tag 阶段
    if ! run_cmd docker tag "$image" "$target"; then
        echo "[ERROR] 标记失败: $image → $target"
        FAILED=$((FAILED + 1))
        FAILED_IMAGES+=("$image (tag failed)")
        echo ""
        continue
    fi

    # Push 阶段
    if ! run_cmd docker push "$target"; then
        echo "[ERROR] 推送失败: $target"
        FAILED=$((FAILED + 1))
        FAILED_IMAGES+=("$image (push failed)")
        echo ""
        continue
    fi

    # 验证阶段
    if [ "$DRY_RUN" = false ]; then
        # 验证推送成功：检查 registry API 是否能列出该镜像
        repo_name="${image%%:*}"
        tag_name="${image##*:}"
        if curl -sf "http://${REGISTRY}/v2/${repo_name}/tags/list" 2>/dev/null | grep -q "\"${tag_name}\""; then
            echo "[OK] $target ✓"
            SUCCESS=$((SUCCESS + 1))
        else
            echo "[ERROR] 推送后无法从 registry 读取镜像: $target"
            FAILED=$((FAILED + 1))
            FAILED_IMAGES+=("$image (verification failed)")
        fi
    else
        echo "[OK] $target (dry-run)"
        SUCCESS=$((SUCCESS + 1))
    fi
    echo ""
done

# 同步结果报告
echo "======================================"
echo " 同步结果"
echo "======================================"
echo "总计: $TOTAL 个镜像"
echo "成功: $SUCCESS 个"
if [ $SKIPPED -gt 0 ]; then
    echo "跳过: $SKIPPED 个（已存在）"
fi
echo "失败: $FAILED 个"

if [ $FAILED -gt 0 ]; then
    echo ""
    echo "[ERROR] 以下镜像同步失败:"
    printf '  - %s\n' "${FAILED_IMAGES[@]}"
    echo ""
    echo "[提示] 失败原因可能是:"
    echo "  1. 网络连接问题（无法访问上游公网镜像源）"
    echo "  2. 本地 registry 存储空间不足"
    echo "  3. 镜像名称或标签错误"
    echo ""
    echo "[修复] 建议:"
    echo "  - 检查网络连接: curl -I https://registry-1.docker.io"
    echo "  - 检查磁盘空间: df -h \${REGISTRY_DATA_PATH:-/data/registry}"
    echo "  - 重试失败的镜像: docker pull <image>"
    exit 1
fi

echo ""
echo "[SUCCESS] 所有镜像同步完成 ✓"
echo ""
echo "[提示] 部署时可使用:"
echo "  IMAGE_SOURCE_MODE=local LOCAL_REGISTRY=$REGISTRY bash scripts/deploy.sh"
echo ""
echo "[验证] 查看 registry 中的镜像:"
echo "  curl http://${REGISTRY}/v2/_catalog"
