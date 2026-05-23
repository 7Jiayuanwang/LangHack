#!/usr/bin/env bash
# ==============================================================================
# LangHack 零停机热更新脚本 (hot-deploy.sh)
# 基于 Docker blue/green 部署策略
#
# 策略：
#   1. 构建新镜像（green）
#   2. 启动 green 栈（使用备用端口）
#   3. 健康检查通过后，切换 nginx upstream 到 green
#   4. 启动新 blue，健康检查通过后切换 nginx 回 blue
#   5. 停止 green 栈
#   6. 失败时自动回滚到 blue
#
# 用法:
#   ./scripts/hot-deploy.sh [--branch <branch>] [--skip-build] [--dry-run]
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DOCKER_DIR="$PROJECT_ROOT/docker"
LOG_DIR="$PROJECT_ROOT/logs"
ENV_FILE="$PROJECT_ROOT/docker/.env"

# ── 端口配置 ──────────────────────────────────────────────────────────────────
BLUE_BACKEND_PORT=8080
GREEN_BACKEND_PORT=8081
NGINX_PORT=80

# ── 容器/服务名 ───────────────────────────────────────────────────────────────
BLUE_COMPOSE="$DOCKER_DIR/docker-compose.yml"
GREEN_COMPOSE="$DOCKER_DIR/docker-compose.green.yml"
BLUE_BACKEND_CONTAINER="langhack-backend"
GREEN_BACKEND_CONTAINER="langhack-backend-green"
NGINX_CONTAINER="langhack-frontend"

# ── 镜像 & 网络（与 docker-compose.yml name: LangHack 保持一致）──────────────
BACKEND_IMAGE="langhack-backend:latest"
NETWORK_NAME="langhack_langhack-network"

# ── 健康检查 ──────────────────────────────────────────────────────────────────
HEALTH_URL_BLUE="http://localhost:${BLUE_BACKEND_PORT}/api/health"
HEALTH_URL_GREEN="http://localhost:${GREEN_BACKEND_PORT}/api/health"
HEALTH_WAIT_SECS=120
HEALTH_INTERVAL=3

# ── 参数 ──────────────────────────────────────────────────────────────────────
BRANCH="$(git -C "$PROJECT_ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'master')"
SKIP_BUILD=false
DRY_RUN=false

mkdir -p "$LOG_DIR"

# ── 日志工具 ──────────────────────────────────────────────────────────────────
_ts() { date '+%H:%M:%S'; }
log_info()    { printf '\033[36m[%s] ℹ\033[0m  %s\n' "$(_ts)" "$*" >&2; }
log_ok()      { printf '\033[32m[%s] ✓\033[0m  %s\n' "$(_ts)" "$*" >&2; }
log_warn()    { printf '\033[33m[%s] ⚠\033[0m  %s\n' "$(_ts)" "$*" >&2; }
log_error()   { printf '\033[31m[%s] ✗\033[0m  %s\n' "$(_ts)" "$*" >&2; }
log_section() { printf '\n\033[1;34m══ %s ══\033[0m\n' "$*" >&2; }

# ── 参数解析 ──────────────────────────────────────────────────────────────────
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --branch)   BRANCH="$2"; shift 2 ;;
            --skip-build) SKIP_BUILD=true; shift ;;
            --dry-run)  DRY_RUN=true; shift ;;
            --help|-h)  usage; exit 0 ;;
            *) log_error "未知参数: $1"; usage; exit 2 ;;
        esac
    done
}

usage() {
    cat >&2 <<EOF
用法: $(basename "$0") [选项]

选项:
  --branch <branch>   部署分支（默认: 当前所在分支）
  --skip-build        跳过镜像构建（使用现有镜像）
  --dry-run           仅打印操作，不实际执行
  --help, -h          显示帮助

零停机部署流程:
  1. git pull 最新代码
  2. 构建新 Docker 镜像（green）
  3. 启动 green 后端（端口 $GREEN_BACKEND_PORT）
  4. 健康检查通过后切换 nginx upstream → green
  5. 启动新 blue 后端，健康检查通过后切换 nginx → blue
  6. 停止 green 后端
  7. 失败时自动回滚
EOF
}

# ── dry-run 包装 ──────────────────────────────────────────────────────────────
run() {
    if $DRY_RUN; then
        log_info "[DRY-RUN] $*"
    else
        "$@"
    fi
}

# ── 健康检查 ──────────────────────────────────────────────────────────────────
wait_healthy() {
    local url="$1" label="$2"
    log_info "等待 $label 健康检查 ($url)..."
    local waited=0
    while [ $waited -lt $HEALTH_WAIT_SECS ]; do
        if curl -sf -m 3 "$url" >/dev/null 2>&1; then
            log_ok "$label 健康检查通过 (${waited}s)"
            return 0
        fi
        sleep $HEALTH_INTERVAL
        waited=$((waited + HEALTH_INTERVAL))
        printf '.' >&2
    done
    printf '\n' >&2
    log_error "$label 健康检查超时 (${HEALTH_WAIT_SECS}s)"
    return 1
}

# ── nginx upstream 切换 ───────────────────────────────────────────────────────
# nginx conf 使用 Docker Compose 服务名 DNS：
#   blue  → proxy_pass http://backend:8080
#   green → proxy_pass http://backend-green:8080
switch_nginx_to() {
    local target="$1"  # blue | green

    if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${NGINX_CONTAINER}$"; then
        log_warn "nginx 容器 ${NGINX_CONTAINER} 未运行，跳过流量切换"
        return 0
    fi

    log_info "切换 nginx upstream → $target"

    if [ "$target" = "green" ]; then
        # backend → backend-green（替换所有 proxy_pass 中的服务名）
        run docker exec "$NGINX_CONTAINER" sh -c \
            "sed -i 's|http://backend:|http://backend-green:|g' /etc/nginx/conf.d/default.conf && nginx -t && nginx -s reload" \
            || log_warn "nginx reload 失败，流量切换可能未生效"
    else
        # backend-green → backend
        run docker exec "$NGINX_CONTAINER" sh -c \
            "sed -i 's|http://backend-green:|http://backend:|g' /etc/nginx/conf.d/default.conf && nginx -t && nginx -s reload" \
            || log_warn "nginx reload 失败，流量切换可能未生效"
    fi
}

# ── 回滚 ──────────────────────────────────────────────────────────────────────
rollback() {
    log_warn "部署失败，执行回滚..."
    # 停止 green 栈
    if [ -f "$GREEN_COMPOSE" ]; then
        run docker compose -f "$GREEN_COMPOSE" down --remove-orphans 2>/dev/null || true
    fi
    # 切换 nginx 回 blue（无论当前指向哪里）
    switch_nginx_to blue
    # 确保 blue 仍在运行
    if ! $DRY_RUN && ! curl -sf -m 3 "$HEALTH_URL_BLUE" >/dev/null 2>&1; then
        log_warn "blue 后端不健康，尝试重启..."
        run docker compose -f "$BLUE_COMPOSE" --env-file "$ENV_FILE" up -d backend
        wait_healthy "$HEALTH_URL_BLUE" "blue backend (回滚)" || log_error "回滚失败，请手动检查"
    else
        log_ok "blue 后端仍在运行，回滚完成"
    fi
}

# ── 生成 green compose ────────────────────────────────────────────────────────
generate_green_compose() {
    cat > "$GREEN_COMPOSE" <<EOF
# 自动生成 — 零停机热更新 green 栈
# 由 hot-deploy.sh 生成，部署完成后自动删除
services:
  backend-green:
    image: ${BACKEND_IMAGE}
    container_name: ${GREEN_BACKEND_CONTAINER}
    ports:
      - "${GREEN_BACKEND_PORT}:8080"
    env_file:
      - .env
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    networks:
      - langhack-network
    restart: "no"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

networks:
  langhack-network:
    external: true
    name: ${NETWORK_NAME}
EOF
}

# ── 主流程 ────────────────────────────────────────────────────────────────────
main() {
    parse_args "$@"

    log_section "LangHack 零停机热更新"
    log_info "分支: $BRANCH | skip-build: $SKIP_BUILD | dry-run: $DRY_RUN"

    # 1. 拉取最新代码
    log_section "Step 1: 拉取最新代码"
    run git -C "$PROJECT_ROOT" fetch origin "$BRANCH"
    run git -C "$PROJECT_ROOT" checkout "$BRANCH"
    run git -C "$PROJECT_ROOT" pull origin "$BRANCH"
    log_ok "代码已更新到最新"

    # 2. 构建新镜像
    if ! $SKIP_BUILD; then
        log_section "Step 2: 构建 green 镜像"
        run docker compose -f "$BLUE_COMPOSE" --env-file "$ENV_FILE" build backend
        log_ok "镜像构建完成"
    else
        log_info "跳过镜像构建（--skip-build）"
    fi

    # 3. 生成 green compose 文件
    log_section "Step 3: 生成 green 栈配置"
    if $DRY_RUN; then
        log_info "[DRY-RUN] 生成 $GREEN_COMPOSE"
    else
        generate_green_compose
    fi
    log_ok "green compose 文件已生成: $GREEN_COMPOSE"

    # 4. 启动 green 后端
    log_section "Step 4: 启动 green 后端 (port $GREEN_BACKEND_PORT)"
    run docker compose -f "$GREEN_COMPOSE" up -d backend-green

    # 5. 健康检查
    log_section "Step 5: 健康检查 green"
    if ! $DRY_RUN; then
        if ! wait_healthy "$HEALTH_URL_GREEN" "green backend"; then
            rollback
            exit 1
        fi
    fi

    # 6. 切换流量到 green
    log_section "Step 6: 切换流量 → green"
    switch_nginx_to green

    # 7. 停止旧 blue 后端
    log_section "Step 7: 停止旧 blue 后端"
    run docker compose -f "$BLUE_COMPOSE" --env-file "$ENV_FILE" stop backend
    log_ok "旧 blue 后端已停止"

    # 8. 启动新 blue（使用刚构建的镜像），健康检查后切换 nginx 回 blue
    log_section "Step 8: 启动新 blue 后端"
    run docker compose -f "$BLUE_COMPOSE" --env-file "$ENV_FILE" up -d backend
    if ! $DRY_RUN; then
        if ! wait_healthy "$HEALTH_URL_BLUE" "new blue backend"; then
            log_error "新 blue 后端启动失败，保持流量在 green"
            exit 1
        fi
    fi

    # 9. 切换流量回 blue，停止 green
    log_section "Step 9: 切换流量 → blue，停止 green"
    switch_nginx_to blue
    run docker compose -f "$GREEN_COMPOSE" down --remove-orphans
    log_ok "green 后端已停止"

    # 10. 清理 green compose 文件
    if ! $DRY_RUN; then
        rm -f "$GREEN_COMPOSE" 2>/dev/null || true
    fi

    log_section "部署完成"
    log_ok "零停机热更新成功！"
    log_info "当前运行版本: $(git -C "$PROJECT_ROOT" rev-parse --short HEAD 2>/dev/null || echo 'unknown')"
}

# ── 错误陷阱 ──────────────────────────────────────────────────────────────────
trap 'log_error "脚本在第 $LINENO 行异常退出（exit=$?）"; rollback 2>/dev/null || true' ERR
trap 'log_warn "收到中断信号"; exit 130' INT TERM

main "$@"
