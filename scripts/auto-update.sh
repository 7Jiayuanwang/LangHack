#!/usr/bin/env bash
# ==============================================================================
# LangHack 自动更新脚本
# 定时从 GitHub 拉取最新代码，检测变更后自动重新构建并部署
#
# 用法:
#   chmod +x scripts/auto-update.sh
#   # 手动执行（默认 master 分支）
#   ./scripts/auto-update.sh
#   # 指定分支
#   ./scripts/auto-update.sh -b main
#   ./scripts/auto-update.sh --branch feature/monitoring
#   # 交互式选择分支
#   ./scripts/auto-update.sh -i
#   # 或加入 crontab（每 5 分钟检查一次）
#   crontab -e
#   */5 * * * * /path/to/LangHack/scripts/auto-update.sh -b master >> /var/log/langhack-update.log 2>&1
# ==============================================================================
set -euo pipefail

# ── 配置 ──────────────────────────────────────────────
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRANCH="${DEPLOY_BRANCH:-master}"                    # 要跟踪的分支（默认）
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}" # 使用根目录的 docker-compose
LOG_FILE="${LOG_FILE:-/var/log/langhack-update.log}"
LOCK_FILE="/tmp/langhack-update.lock"
INTERACTIVE=false
# 企业微信/钉钉/飞书 Webhook（可选，留空则不通知）
WEBHOOK_URL="${WEBHOOK_URL:-}"
# ─────────────────────────────────────────────────────

# ── 参数解析 ──────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        -b|--branch)
            BRANCH="$2"
            shift 2
            ;;
        -i|--interactive)
            INTERACTIVE=true
            shift
            ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "  -b, --branch <分支名>   指定要跟踪的分支 (默认: master)"
            echo "  -i, --interactive       交互式选择分支"
            echo "  -h, --help              显示帮助信息"
            echo ""
            echo "环境变量:"
            echo "  DEPLOY_BRANCH           默认跟踪分支 (优先级低于 -b)"
            echo "  COMPOSE_FILE            docker-compose 文件路径"
            echo "  WEBHOOK_URL             通知 Webhook URL"
            echo ""
            echo "示例:"
            echo "  $0                      跟踪 master 分支"
            echo "  $0 -b main              跟踪 main 分支"
            echo "  $0 -i                   交互式选择分支"
            echo "  DEPLOY_BRANCH=dev $0    跟踪 dev 分支"
            exit 0
            ;;
        *)
            echo "[ERROR] 未知参数: $1 (运行 $0 --help 查看帮助)"
            exit 1
            ;;
    esac
done

# ── 交互式分支选择 ────────────────────────────────────
if [ "$INTERACTIVE" = true ]; then
    cd "$PROJECT_ROOT"
    echo "[INFO] 正在获取远程分支列表..."
    git fetch --all --quiet 2>/dev/null || echo "[WARN] 无法连接远程仓库"

    mapfile -t BRANCHES_LIST < <(git branch -r --no-color 2>/dev/null \
        | grep -v 'HEAD' \
        | sed 's|origin/||;s|^[[:space:]]*||' \
        | sort -u)

    CURRENT=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

    if [ ${#BRANCHES_LIST[@]} -eq 0 ]; then
        echo "[WARN] 未获取到分支列表，使用默认: $BRANCH"
    else
        echo ""
        echo "可用分支:"
        for i in "${!BRANCHES_LIST[@]}"; do
            branch="${BRANCHES_LIST[$i]}"
            marker=""
            [ "$branch" = "$CURRENT" ] && marker=" ← 当前"
            printf "  %2d) %s%s\n" $((i + 1)) "$branch" "$marker"
        done
        echo ""
        while true; do
            read -rp "请选择分支编号 [1-${#BRANCHES_LIST[@]}]: " choice
            if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le ${#BRANCHES_LIST[@]} ]; then
                BRANCH="${BRANCHES_LIST[$((choice - 1))]}"
                break
            else
                echo "[ERROR] 无效选择"
            fi
        done
    fi
    echo "[INFO] 已选择分支: $BRANCH"
fi

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

notify() {
    local message="$1"
    if [ -n "$WEBHOOK_URL" ]; then
        curl -s -X POST "$WEBHOOK_URL" \
            -H 'Content-Type: application/json' \
            -d "{\"msgtype\":\"text\",\"text\":{\"content\":\"[LangHack] $message\"}}" \
            > /dev/null 2>&1 || true
    fi
}

cleanup() {
    rm -f "$LOCK_FILE"
}

# ── 防止并发执行 ──────────────────────────────────────
if [ -f "$LOCK_FILE" ]; then
    pid=$(cat "$LOCK_FILE" 2>/dev/null || echo "")
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        log "SKIP: 另一个更新进程正在运行 (PID: $pid)"
        exit 0
    fi
    log "WARN: 发现残留锁文件，清理中..."
    rm -f "$LOCK_FILE"
fi
echo $$ > "$LOCK_FILE"
trap cleanup EXIT

# ── 进入项目目录 ──────────────────────────────────────
cd "$PROJECT_ROOT"

# ── 确保在目标分支上 ──────────────────────────────────
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
if [ "$CURRENT_BRANCH" != "$BRANCH" ]; then
    log "INFO: 切换分支 $CURRENT_BRANCH → $BRANCH"
    git checkout "$BRANCH" --quiet 2>/dev/null || {
        log "ERROR: 切换到分支 $BRANCH 失败"
        notify "自动更新失败: 无法切换到分支 $BRANCH"
        exit 1
    }
fi

# ── 检查 Git 远程更新 ────────────────────────────────
log "INFO: 检查远程仓库更新 (branch: $BRANCH)..."
git fetch origin "$BRANCH" --quiet

LOCAL_HASH=$(git rev-parse HEAD)
REMOTE_HASH=$(git rev-parse "origin/$BRANCH")

if [ "$LOCAL_HASH" = "$REMOTE_HASH" ]; then
    log "INFO: 已是最新，无需更新 (commit: ${LOCAL_HASH:0:8})"
    exit 0
fi

# ── 有新提交，开始更新 ────────────────────────────────
log "UPDATE: 检测到新提交！"
log "  本地: ${LOCAL_HASH:0:8}"
log "  远程: ${REMOTE_HASH:0:8}"

# 显示新增的提交记录
log "──── 新提交列表 ────"
git log --oneline "$LOCAL_HASH".."$REMOTE_HASH" | head -20
log "────────────────────"

# 拉取最新代码
log "INFO: 拉取最新代码..."
git pull origin "$BRANCH" --ff-only

if [ $? -ne 0 ]; then
    log "ERROR: git pull 失败（可能有本地冲突），跳过本次更新"
    notify "自动更新失败: git pull 出现冲突，需要手动处理"
    exit 1
fi

# ── 判断需要重建哪些服务 ──────────────────────────────
CHANGED_FILES=$(git diff --name-only "$LOCAL_HASH" "$REMOTE_HASH")

REBUILD_BACKEND=false
REBUILD_FRONTEND=false

if echo "$CHANGED_FILES" | grep -qE '^backend/'; then
    REBUILD_BACKEND=true
    log "INFO: 检测到后端变更"
fi

if echo "$CHANGED_FILES" | grep -qE '^frontend/'; then
    REBUILD_FRONTEND=true
    log "INFO: 检测到前端变更"
fi

# docker-compose 或 Dockerfile 变更则全部重建
if echo "$CHANGED_FILES" | grep -qE '(docker-compose|Dockerfile|\.env\.example)'; then
    REBUILD_BACKEND=true
    REBUILD_FRONTEND=true
    log "INFO: 检测到部署配置变更，全量重建"
fi

# ── 执行重建 ──────────────────────────────────────────
if [ "$REBUILD_BACKEND" = true ] || [ "$REBUILD_FRONTEND" = true ]; then
    log "INFO: 开始重新构建..."

    SERVICES=""
    if [ "$REBUILD_BACKEND" = true ]; then
        SERVICES="$SERVICES backend"
    fi
    if [ "$REBUILD_FRONTEND" = true ]; then
        SERVICES="$SERVICES frontend"
    fi

    # 重新构建变更的服务
    docker-compose -f "$COMPOSE_FILE" build --no-cache $SERVICES
    # 重启变更的服务（其他服务不受影响）
    docker-compose -f "$COMPOSE_FILE" up -d $SERVICES

    log "SUCCESS: 服务已更新并重启"
    log "  更新服务: $SERVICES"
    log "  当前版本: $(git rev-parse --short HEAD)"
    log "  提交信息: $(git log -1 --format='%s')"

    notify "自动更新成功 ✅\n版本: $(git rev-parse --short HEAD)\n提交: $(git log -1 --format='%s')\n服务: $SERVICES"
else
    log "INFO: 变更不涉及前后端代码（可能是文档/脚本），跳过重建"
fi

# ── 健康检查 ──────────────────────────────────────────
log "INFO: 等待服务启动..."
sleep 10

if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    log "HEALTH: 后端服务正常 ✓"
else
    log "WARN: 后端健康检查未通过，可能仍在启动中"
    notify "警告: 更新后后端健康检查未通过，请检查日志"
fi

log "──── 更新完成 ────"
