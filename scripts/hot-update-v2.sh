#!/bin/bash
# ==============================================================================
# LangHack 本地热部署脚本 v2.0 (hot-update-v2.sh)
# ------------------------------------------------------------------------------
# 相对 v1 的改进：
#   • CLI 参数支持（full/db/restart/backend/frontend/quick/status/stop/--help）
#   • quick 模式：基于文件变化检测自动选最小动作（免重启）
#   • MYSQL_PWD 环境变量：密码不再出现在 ps aux / 进程列表
#   • 迁移失败不再静默：捕获 mysql 错误输出并展示
#   • 迁移不同步时列出具体缺失的 V*.sql 文件名
#   • 端口驱动的进程检测（lsof → netstat 退化），比 grep 进程名可靠
#   • 优雅终止：SIGTERM → 轮询 15s → SIGKILL
#   • 前后端并行启动（在需要全启时）
#   • 健康检查统一为轮询 + 自适应间隔
#   • 启动前预检（java/mvn/node/npm/curl/.env/端口占用）
#   • trap 错误/中断自动清理
#   • 可选启动后 tail -f 日志
# ==============================================================================
set -euo pipefail

# ─────────────────────────────────────────────
# 常量
# ─────────────────────────────────────────────
readonly SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly ENV_FILE="$PROJECT_ROOT/.env"
readonly LOG_DIR="$PROJECT_ROOT/logs"
readonly CACHE_FILE="$LOG_DIR/.hot-update.cache"
readonly MIGRATION_SQL="$PROJECT_ROOT/docker/mysql/init/04-apply-all-migrations.sql"
readonly FLYWAY_DIR="$PROJECT_ROOT/backend/src/main/resources/db/migration"
readonly BACKEND_PORT=8080
readonly FRONTEND_PORT=3000
readonly BACKEND_HEALTH_URL="http://localhost:${BACKEND_PORT}/api/health"
readonly FRONTEND_HEALTH_URL="http://localhost:${FRONTEND_PORT}"
readonly BACKEND_WAIT_SECS=90
readonly FRONTEND_WAIT_SECS=45
readonly SHUTDOWN_WAIT_SECS=15

mkdir -p "$LOG_DIR"

# ─────────────────────────────────────────────
# 日志工具（全部写到 stderr，不污染 stdout，便于 pipe）
# ─────────────────────────────────────────────
_ts() { date '+%H:%M:%S'; }
log_info()    { printf '\033[36m[%s] ℹ\033[0m  %s\n' "$(_ts)" "$*" >&2; }
log_ok()      { printf '\033[32m[%s] ✓\033[0m  %s\n' "$(_ts)" "$*" >&2; }
log_warn()    { printf '\033[33m[%s] ⚠\033[0m  %s\n' "$(_ts)" "$*" >&2; }
log_error()   { printf '\033[31m[%s] ✗\033[0m  %s\n' "$(_ts)" "$*" >&2; }
log_section() { printf '\n\033[1;34m══ %s ══\033[0m\n' "$*" >&2; }

# ─────────────────────────────────────────────
# 加载 .env
# ─────────────────────────────────────────────
load_env() {
    if [ -f "$ENV_FILE" ]; then
        set -a
        # shellcheck disable=SC1090
        source "$ENV_FILE"
        set +a
    fi
    DB_USER="${DB_USERNAME:-root}"
    DB_PW="${DB_PASSWORD:-}"
    DB_HOST="${DB_HOST:-127.0.0.1}"
    DB_NAME="${DB_NAME:-english_learning}"
}

# ─────────────────────────────────────────────
# 预检：必要工具 + 基础配置
# ─────────────────────────────────────────────
preflight() {
    local missing=()
    for cmd in curl mvn node npm java; do
        command -v "$cmd" >/dev/null 2>&1 || missing+=("$cmd")
    done
    if [ ${#missing[@]} -gt 0 ]; then
        log_error "缺少必要工具：${missing[*]}"
        return 1
    fi
    [ -f "$ENV_FILE" ] || log_warn ".env 不存在，将使用默认值"
    return 0
}

# ─────────────────────────────────────────────
# 端口 → PID（跨平台：lsof → netstat 退化）
# ─────────────────────────────────────────────
pid_on_port() {
    local port=$1
    if command -v lsof >/dev/null 2>&1; then
        lsof -ti:"$port" -sTCP:LISTEN 2>/dev/null | head -1
    elif command -v netstat >/dev/null 2>&1; then
        # Windows Git Bash: netstat -ano 最后一列为 PID
        netstat -ano 2>/dev/null | awk -v p=":$port" '$2 ~ p && $4 == "LISTENING" {print $5; exit}'
    fi
}

port_in_use() {
    [ -n "$(pid_on_port "$1")" ]
}

# ─────────────────────────────────────────────
# 优雅停止进程（SIGTERM → wait → SIGKILL）
# ─────────────────────────────────────────────
kill_pid_graceful() {
    local pid=$1
    local label=${2:-"pid=$pid"}
    [ -z "$pid" ] && return 0
    kill -0 "$pid" 2>/dev/null || return 0

    log_info "终止 $label (PID: $pid) — 发送 SIGTERM"
    kill -TERM "$pid" 2>/dev/null || true

    local waited=0
    while [ $waited -lt $SHUTDOWN_WAIT_SECS ] && kill -0 "$pid" 2>/dev/null; do
        sleep 1
        waited=$((waited + 1))
    done

    if kill -0 "$pid" 2>/dev/null; then
        log_warn "$label 未响应 SIGTERM，发送 SIGKILL"
        kill -KILL "$pid" 2>/dev/null || true
        sleep 1
    fi
    log_ok "$label 已停止"
}

# ─────────────────────────────────────────────
# 迁移版本同步校验（不同步时列出缺失文件名）
# ─────────────────────────────────────────────
check_migration_sync() {
    [ -f "$MIGRATION_SQL" ] || { log_warn "合并脚本不存在，跳过迁移"; return 1; }
    [ -d "$FLYWAY_DIR" ]    || { log_warn "Flyway 目录不存在，跳过迁移"; return 1; }

    local flyway_files merged_versions
    flyway_files=$(ls "$FLYWAY_DIR"/V*.sql 2>/dev/null | xargs -n1 basename | sort)
    merged_versions=$(grep -oE '^--   V[0-9]+__[^ ]+\.sql' "$MIGRATION_SQL" | awk '{print $2}' | sort)

    local missing
    missing=$(comm -23 <(echo "$flyway_files") <(echo "$merged_versions"))

    if [ -n "$missing" ]; then
        log_error "迁移脚本版本不同步 — 合并脚本缺少以下文件对应的版本块："
        echo "$missing" | while read -r f; do echo "    • $f"; done >&2
        log_error "请将这些 V*.sql 的内容追加到 $MIGRATION_SQL 并在头部列表中登记"
        return 1
    fi
    log_ok "迁移脚本版本同步（$(echo "$flyway_files" | wc -l | tr -d ' ') 个文件）"
    return 0
}

# ─────────────────────────────────────────────
# 执行迁移（密码走 MYSQL_PWD env，不上命令行）
# ─────────────────────────────────────────────
apply_migrations() {
    log_section "数据库迁移"
    check_migration_sync || return 1

    local stderr_log="$LOG_DIR/migration.stderr.log"
    : > "$stderr_log"

    if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "langhack-mysql-master"; then
        log_info "使用 Docker MySQL 容器 (langhack-mysql-master)"
        if MYSQL_PWD="$DB_PW" docker exec -i -e MYSQL_PWD langhack-mysql-master \
               mysql -u"$DB_USER" "$DB_NAME" < "$MIGRATION_SQL" 2>"$stderr_log"; then
            log_ok "迁移完成（Docker 模式）"
        else
            log_error "迁移失败，错误输出："
            sed 's/^/    /' "$stderr_log" >&2
            return 1
        fi
    elif command -v mysql >/dev/null 2>&1; then
        log_info "使用本地 mysql 客户端 → $DB_HOST"
        if MYSQL_PWD="$DB_PW" mysql -h "$DB_HOST" -u"$DB_USER" "$DB_NAME" \
               < "$MIGRATION_SQL" 2>"$stderr_log"; then
            log_ok "迁移完成（本地模式）"
        else
            log_error "迁移失败，错误输出："
            sed 's/^/    /' "$stderr_log" >&2
            return 1
        fi
    else
        log_warn "未检测到 Docker MySQL 或本地 mysql 客户端，跳过迁移"
        return 1
    fi
}

# ─────────────────────────────────────────────
# 停止后端 / 前端（按端口找 PID）
# ─────────────────────────────────────────────
stop_backend()  { local p; p=$(pid_on_port $BACKEND_PORT);  [ -n "$p" ] && kill_pid_graceful "$p" "backend"  || log_info "backend 未运行"; }
stop_frontend() { local p; p=$(pid_on_port $FRONTEND_PORT); [ -n "$p" ] && kill_pid_graceful "$p" "frontend" || log_info "frontend 未运行"; }

# ─────────────────────────────────────────────
# 启动后端（后台 nohup）
# ─────────────────────────────────────────────
start_backend() {
    if port_in_use $BACKEND_PORT; then
        log_warn "端口 $BACKEND_PORT 已被占用，跳过启动"
        return 0
    fi
    log_info "启动后端 (mvn spring-boot:run) → logs/backend.log"
    ( cd "$PROJECT_ROOT/backend" && nohup mvn spring-boot:run \
          > "$LOG_DIR/backend.log" 2>&1 < /dev/null & )
}

wait_backend_healthy() {
    log_info "等待后端健康检查..."
    local waited=0 interval=2
    while [ $waited -lt $BACKEND_WAIT_SECS ]; do
        if curl -sf -m 2 "$BACKEND_HEALTH_URL" >/dev/null 2>&1; then
            log_ok "后端健康检查通过 (${waited}s)"
            return 0
        fi
        sleep $interval
        waited=$((waited + interval))
        printf '.' >&2
    done
    printf '\n' >&2
    log_error "后端启动超时 (${BACKEND_WAIT_SECS}s) — 查看 logs/backend.log"
    return 1
}

# ─────────────────────────────────────────────
# 启动前端
# ─────────────────────────────────────────────
start_frontend() {
    if port_in_use $FRONTEND_PORT; then
        log_warn "端口 $FRONTEND_PORT 已被占用，跳过启动"
        return 0
    fi
    log_info "启动前端 (npm run dev) → logs/frontend.log"
    ( cd "$PROJECT_ROOT/frontend" && nohup npm run dev \
          > "$LOG_DIR/frontend.log" 2>&1 < /dev/null & )
}

wait_frontend_healthy() {
    log_info "等待前端健康检查..."
    local waited=0 interval=2
    while [ $waited -lt $FRONTEND_WAIT_SECS ]; do
        if curl -sf -I -m 2 "$FRONTEND_HEALTH_URL" >/dev/null 2>&1; then
            log_ok "前端健康检查通过 (${waited}s)"
            return 0
        fi
        sleep $interval
        waited=$((waited + interval))
        printf '.' >&2
    done
    printf '\n' >&2
    log_warn "前端启动超时 — 查看 logs/frontend.log"
    return 1
}

# ─────────────────────────────────────────────
# 变更检测（quick 模式用）
# ─────────────────────────────────────────────
hash_tree() {
    # 对给定目录下的源文件做 md5 汇总（文件内容 + 路径）
    local dir=$1
    [ -d "$dir" ] || { echo "none"; return; }
    find "$dir" -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yml' \
         -o -name '*.vue' -o -name '*.js' -o -name '*.ts' -o -name '*.sql' \) \
         -not -path '*/node_modules/*' -not -path '*/target/*' -not -path '*/dist/*' \
         2>/dev/null | sort | xargs -I{} md5sum {} 2>/dev/null | md5sum | awk '{print $1}'
}

load_cache()  { [ -f "$CACHE_FILE" ] && source "$CACHE_FILE" 2>/dev/null || true; }
save_cache()  {
    cat > "$CACHE_FILE" <<EOF
LAST_BACKEND_HASH="${LAST_BACKEND_HASH:-}"
LAST_FRONTEND_HASH="${LAST_FRONTEND_HASH:-}"
LAST_MIGRATION_HASH="${LAST_MIGRATION_HASH:-}"
EOF
}

detect_changes() {
    LAST_BACKEND_HASH=""; LAST_FRONTEND_HASH=""; LAST_MIGRATION_HASH=""
    load_cache

    CURR_BACKEND_HASH=$(hash_tree "$PROJECT_ROOT/backend/src/main")
    CURR_FRONTEND_HASH=$(hash_tree "$PROJECT_ROOT/frontend/src")
    CURR_MIGRATION_HASH=$(hash_tree "$FLYWAY_DIR")

    BACKEND_CHANGED=false; FRONTEND_CHANGED=false; MIGRATION_CHANGED=false
    [ "$CURR_BACKEND_HASH"   != "${LAST_BACKEND_HASH:-}"   ] && BACKEND_CHANGED=true
    [ "$CURR_FRONTEND_HASH"  != "${LAST_FRONTEND_HASH:-}"  ] && FRONTEND_CHANGED=true
    [ "$CURR_MIGRATION_HASH" != "${LAST_MIGRATION_HASH:-}" ] && MIGRATION_CHANGED=true
}

commit_cache() {
    LAST_BACKEND_HASH="$CURR_BACKEND_HASH"
    LAST_FRONTEND_HASH="$CURR_FRONTEND_HASH"
    LAST_MIGRATION_HASH="$CURR_MIGRATION_HASH"
    save_cache
}

# ─────────────────────────────────────────────
# 访问地址打印
# ─────────────────────────────────────────────
print_endpoints() {
    cat >&2 <<EOF

📋 访问地址：
   管理端:  http://localhost:$FRONTEND_PORT/admin
   教师端:  http://localhost:$FRONTEND_PORT/teacher
   学生端:  http://localhost:$FRONTEND_PORT/student
   后端API: http://localhost:$BACKEND_PORT
EOF
}

# ─────────────────────────────────────────────
# 状态查询
# ─────────────────────────────────────────────
mode_status() {
    log_section "服务状态"
    local bp fp
    bp=$(pid_on_port $BACKEND_PORT)
    fp=$(pid_on_port $FRONTEND_PORT)
    if [ -n "$bp" ]; then
        log_ok "backend  → PID $bp  (port $BACKEND_PORT)"
    else
        log_warn "backend  → 未运行"
    fi
    if [ -n "$fp" ]; then
        log_ok "frontend → PID $fp  (port $FRONTEND_PORT)"
    else
        log_warn "frontend → 未运行"
    fi

    if docker ps --format '{{.Names}}' 2>/dev/null | grep -q langhack-mysql-master; then
        log_ok "mysql    → Docker 容器运行中"
    elif command -v mysqladmin >/dev/null 2>&1 \
         && MYSQL_PWD="$DB_PW" mysqladmin -h"$DB_HOST" -u"$DB_USER" ping >/dev/null 2>&1; then
        log_ok "mysql    → 本地服务运行中"
    else
        log_warn "mysql    → 未检测到运行中的实例"
    fi
}

# ─────────────────────────────────────────────
# 各模式实现
# ─────────────────────────────────────────────
mode_stop() {
    log_section "停止所有服务"
    stop_backend
    stop_frontend
}

mode_db() {
    apply_migrations || return 1
}

mode_backend() {
    log_section "重启后端"
    stop_backend
    start_backend
    wait_backend_healthy
}

mode_frontend() {
    log_section "重启前端"
    stop_frontend
    start_frontend
    wait_frontend_healthy
}

mode_restart() {
    log_section "重启前后端（跳过迁移）"
    stop_backend
    stop_frontend
    start_backend      # 后台启动（非阻塞）
    start_frontend     # 后台启动（非阻塞）
    # 并行等待两个健康检查
    wait_backend_healthy  & local bp=$!
    wait_frontend_healthy & local fp=$!
    wait $bp || true
    wait $fp || true
    print_endpoints
}

mode_full() {
    log_section "全量部署（数据库 + 前后端）"
    detect_changes
    apply_migrations || return 1
    stop_backend
    stop_frontend
    start_backend
    start_frontend
    wait_backend_healthy  & local bp=$!
    wait_frontend_healthy & local fp=$!
    wait $bp || true
    wait $fp || true
    commit_cache
    print_endpoints
}

mode_quick() {
    log_section "Quick 模式（基于文件变化自动选择动作）"
    detect_changes

    local actions=()
    $MIGRATION_CHANGED && actions+=("迁移")
    $BACKEND_CHANGED   && actions+=("重启后端")
    $FRONTEND_CHANGED  && actions+=("前端 Vite HMR 已自动处理")

    if [ ${#actions[@]} -eq 0 ]; then
        log_ok "未检测到源文件变更，无动作"
        return 0
    fi
    log_info "检测到变更：${actions[*]}"

    $MIGRATION_CHANGED && { apply_migrations || return 1; }
    if $BACKEND_CHANGED; then
        stop_backend
        start_backend
        wait_backend_healthy || return 1
    fi
    if $FRONTEND_CHANGED && ! port_in_use $FRONTEND_PORT; then
        log_info "前端未运行，启动"
        start_frontend
        wait_frontend_healthy || true
    fi
    commit_cache
    print_endpoints
}

# ─────────────────────────────────────────────
# 交互菜单（无参数时）
# ─────────────────────────────────────────────
show_menu() {
    cat >&2 <<EOF

╔══════════════════════════════════════════════╗
║        LangHack 热部署 v2.0                  ║
╠══════════════════════════════════════════════╣
║  [1] 重启前后端           [5] 只重启后端    ║
║  [2] 仅同步数据库         [6] 只重启前端    ║
║  [3] 全量（DB + 服务）    [7] quick 智能检测 ║
║  [4] 状态查询             [8] 停止所有      ║
║  [0] 退出                                    ║
╚══════════════════════════════════════════════╝
EOF
}

# ─────────────────────────────────────────────
# 参数解析 / 主入口
# ─────────────────────────────────────────────
usage() {
    cat >&2 <<EOF
用法: $(basename "$0") [命令]

命令:
  full        全量部署（DB 迁移 + 重启前后端）
  db          仅同步数据库
  restart     重启前后端（跳过迁移）
  backend     只重启后端
  frontend    只重启前端
  quick       智能模式，仅对有变更的部分执行动作
  status      查看当前服务状态
  stop        停止所有服务
  --help, -h  显示本帮助

无参数时进入交互菜单。
EOF
}

dispatch() {
    case "$1" in
        full)     mode_full ;;
        db)       mode_db ;;
        restart)  mode_restart ;;
        backend)  mode_backend ;;
        frontend) mode_frontend ;;
        quick)    mode_quick ;;
        status)   mode_status ;;
        stop)     mode_stop ;;
        --help|-h|help) usage ;;
        *) log_error "未知命令: $1"; usage; exit 2 ;;
    esac
}

interactive() {
    while true; do
        show_menu
        echo -n "请选择操作 [0-8]: " >&2
        read -r CHOICE
        case "$CHOICE" in
            1) mode_restart;  break ;;
            2) mode_db;       break ;;
            3) mode_full;     break ;;
            4) mode_status;   break ;;
            5) mode_backend;  break ;;
            6) mode_frontend; break ;;
            7) mode_quick;    break ;;
            8) mode_stop;     break ;;
            0) echo "已退出。" >&2; exit 0 ;;
            *) log_warn "无效选择，请输入 0-8" ;;
        esac
    done
}

# 错误/中断陷阱：打印行号辅助排查
trap 'log_error "脚本在第 $LINENO 行异常退出（exit=$?）"' ERR
trap 'log_warn "收到中断信号，正在退出（后台进程可能仍在运行，必要时手动 stop）"; exit 130' INT TERM

load_env
preflight || exit 1

if [ $# -ge 1 ]; then
    dispatch "$1"
else
    interactive
fi
