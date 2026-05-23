#!/usr/bin/env bash
# ==============================================================================
# LangHack 零停机热更新脚本 (hot-deploy.sh)
# 基于 Docker blue/green 部署策略
#
# 策略：
#   1. 构建新镜像（green）
#   2. 启动 green 栈（使用备用端口）
#   3. 健康检查通过后，切换 nginx upstream 到 green
#   4. 停止 blue 栈
#   5. 失败时自动回滚到 blue
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

# ── 健康检查 ──────────────────────────────────────────────────────────────────
HEALTH_URL_BLUE="http://localhost:${BLUE_BACKEND_PORT}/api/health"
HEALTH_URL_GREEN="http://localhost:${GREEN_BACKEND_PORT}/api/health"
HEALTH_WAIT_SECS=120
HEALTH_INTERVAL=3

# ── 参数 ──────────────────────────────────────────────────────────────────────
BRANCH="master"
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
  --branch <branch>   部署分支（默认: master）
  --skip-build        跳过镜像构建（使用现有镜像）
  --dry-run           仅打印操作，不实际执行
  --help, -h          显示帮助

零停机部署流程:
  1. git pull 最新代码
  2. 构建新 Docker 镜像（green）
  3. 启动 green 后端（端口 $GREEN_BACKEND_PORT）
  4. 健康检查通过后切换 nginx upstream
  5. 停止 blue 后端（端口 $BLUE_BACKEND_PORT）
  6. 失败时自动回滚
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

# ── 回滚 ──────────────────────────────────────────────────────────────────────
rollback() {
    log_warn "部署失败，执行回滚..."
    # 停止 green 栈
    if [ -f "$GREEN_COMPOSE" ]; then
        run docker compose -f "$GREEN_COMPOSE" --env-file "$ENV_FILE" down --remove-orphans 2>/dev/null || true
    fi
    # 确保 blue 仍在运行
    if ! curl -sf -m 3 "$HEALTH_URL_BLUE" >/dev/null 2>&1; then
        log_warn "blue 后端不健康，尝试重启..."
        run docker compose -f "$BLUE_COMPOSE" --env-file "$ENV_FILE" up -d backend
        wait_healthy "$HEALTH_URL_BLUE" "blue backend (回滚)" || log_error "回滚失败，请手动检查"
    else
        log_ok "blue 后端仍在运行，回滚完成"
    fi
    # 切换 nginx 回 blue
    switch_nginx_to blue
}

# ── nginx upstream 切换 ───────────────────────────────────────────────────────
switch_nginx_to() {
    local target="$1"  # blue | green
    local port
    if [ "$target" = "green" ]; then
        port=$GREEN_BACKEND_PORT
    else
        port=$BLUE_BACKEND_PORT
    fi

    log_info "切换 nginx upstream → $target (port $port)"

    # 通过 nginx reload 实现无缝切换
    # nginx.conf 中 upstream backend 使用环境变量或动态配置
    if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "$NGINX_CONTAINER"; then
        run docker exec "$NGINX_CONTAINER" sh -c \
            "sed -i 's|server langhack-backend[^;]*;|server host.docker.internal:${port};|g' /etc/nginx/conf.d/default.conf && nginx -s reload" 2>/dev/null || \
        run docker exec "$NGINX_CONTAINER" nginx -s reload 2>/dev/null || \
        log_warn "nginx reload 失败，流量切换可能未生效"
    else
        log_warn "nginx 容器未运行，跳过流量切换"
    fi
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
    generate_green_compose
    log_ok "green compose 文件已生成: $GREEN_COMPOSE"

    # 4. 启动 green 后端
    log_section "Step 4: 启动 green 后端 (port $GREEN_BACKEND_PORT)"
    run docker compose -f "$GREEN_COMPOSE" --env-file "$ENV_FILE" up -d backend-green

    # 5. 健康检查
    log_section "Step 5: 健康检查"
    if ! $DRY_RUN; then
        if ! wait_healthy "$HEALTH_URL_GREEN" "green backend"; then
            rollback
            exit 1
        fi
    fi

    # 6. 切换流量到 green
    log_section "Step 6: 切换流量 → green"
    switch_nginx_to green

    # 7. 停止 blue 后端
    log_section "Step 7: 停止 blue 后端"
    run docker compose -f "$BLUE_COMPOSE" --env-file "$ENV_FILE" stop backend
    log_ok "blue 后端已停止"

    # 8. 将 green 提升为新的 blue（重命名容器）
    log_section "Step 8: 提升 green → blue"
    run docker compose -f "$GREEN_COMPOSE" --env-file "$ENV_FILE" down --remove-orphans
    run docker compose -f "$BLUE_COMPOSE" --env-file "$ENV_FILE" up -d backend
    if ! $DRY_RUN; then
        wait_healthy "$HEALTH_URL_BLUE" "new blue backend" || {
            log_error "新 blue 后端启动失败"
            exit 1
        }
    fi
    switch_nginx_to blue

    # 9. 清理 green compose 文件
    rm -f "$GREEN_COMPOSE" 2>/dev/null || true

    log_section "部署完成"
    log_ok "零停机热更新成功！"
    log_info "当前运行版本: $(git -C "$PROJECT_ROOT" rev-parse --short HEAD 2>/dev/null || echo 'unknown')"
}

# ── 生成 green compose ────────────────────────────────────────────────────────
generate_green_compose() {
    cat > "$GREEN_COMPOSE" <<EOF
# 自动生成 — 零停机热更新 green 栈
# 由 hot-deploy.sh 生成，部署完成后自动删除
version: '3.8'
services:
  backend-green:
    image: \${COMPOSE_PROJECT_NAME:-langhack}-backend:latest
    container_name: ${GREEN_BACKEND_CONTAINER}
    ports:
      - "${GREEN_BACKEND_PORT}:8080"
    env_file:
      - .env
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - mysql-master
    networks:
      - langhack-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

networks:
  langhack-network:
    external: true
    name: \${COMPOSE_PROJECT_NAME:-langhack}_langhack-network
EOF
}

# ── 错误陷阱 ──────────────────────────────────────────────────────────────────
trap 'log_error "脚本在第 $LINENO 行异常退出（exit=$?）"; rollback 2>/dev/null || true' ERR
trap 'log_warn "收到中断信号"; exit 130' INT TERM

main "$@"
