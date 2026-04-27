#!/usr/bin/env bash
# ==============================================================================
# LangHack 自动更新脚本 (v2.5)
# 定时从 GitHub 拉取最新代码，检测变更后自动重新构建并部署
#
# v2.5 新增:
#   - Git fetch/pull 超时保护（60s）
#   - Docker 构建进度监控 + 超时保护（30min）
#   - 离线更新能力（使用本地缓存的远程分支）
#   - 增强错误诊断和恢复提示
#   - 幂等性和原子性改进
#
# v2.4 新增:
#   - 支持 UPDATE_MODE=auto|docker|local
#   - Docker 模式保留原有容器更新/回滚能力
#   - Local 模式支持按需重启 backend/frontend dev 进程
#   - 新增本地 PID 文件与本地健康检查/代码回退逻辑
#
#   - 维护模式: 更新前显示维护页面，完成后自动恢复
#   - 数据库备份: 更新前自动 mysqldump
#   - .env 保护: 备份 + 验证关键配置
#   - 自动回滚: 构建失败 → revert git / 健康检查失败 → 旧版本重建
#
# v2.1 新增:
#   - 部署记录文件: 以实际成功部署的 commit 为基准判断是否需要更新
#     (避免构建失败后误判已更新、回滚后死循环触发等问题)
#   - 回滚暂停机制: 回滚成功后暂停自动更新，防止同一有问题 commit 循环拉取
#   - 运维日志增强: 更新/回滚结果写入结构化日志，方便运维端查阅
#
# v2.2 修复:
#   - 数据库迁移改为直接执行 docker/mysql/init/04-apply-all-migrations.sql
#     (消除 heredoc 与合并 SQL 双重维护问题，deploy.sh / auto-update.sh 单一数据源)
#   - 迁移执行改用 root 账户（DDL 需要 root 权限）
#   - 新增版本同步校验：Flyway 文件数 vs 合并 SQL 头部列表
#   - verify_flyway_migrations 补充 V7 新增表（user_tags / feature_override）+ V8/V9
#   - 迁移文件变更（04-apply-all-migrations.sql / db/migration/）时强制执行迁移
#   - 新增从库复制延迟验证
#
# v2.3 修复:
#   - run_incremental_migrations: 版本不同步从 WARN 改为 return 1（阻塞部署）
#   - run_incremental_migrations: lag=NULL（复制未配置）从 WARN+break 改为 return 1
#   - 不重建路径（仅文档/脚本变更）迁移失败从 WARN 改为触发自动回滚
#
# 用法:
#   chmod +x scripts/auto-update.sh
#   ./scripts/auto-update.sh                    # 默认更新当前分支
#   ./scripts/auto-update.sh -b main            # 指定分支
#   ./scripts/auto-update.sh -i                 # 交互式选择分支
#   # crontab (每小时检查)
#   0 * * * * /path/to/LangHack/scripts/auto-update.sh >> /var/log/langhack-update.log 2>&1
# ==============================================================================
set -euo pipefail

# ── 健壮性增强：错误处理和状态追踪 ──────────────────────
# 全局状态变量
SCRIPT_STATE="INIT"           # INIT -> CHECKING -> UPDATING -> BUILDING -> DEPLOYING -> SUCCESS/FAILED
ROLLBACK_NEEDED=false
MAINTENANCE_ACTIVE=false
BUILD_PID=""
CLEANUP_DONE=false

# ── 配置 ──────────────────────────────────────────────
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRANCH="${DEPLOY_BRANCH:-}"
UPDATE_MODE="${UPDATE_MODE:-auto}"
# docker-compose.yml 绝对路径（优先使用环境变量覆盖）
COMPOSE_FILE="${COMPOSE_FILE:-$PROJECT_ROOT/docker/docker-compose.yml}"
LOG_FILE="${LOG_FILE:-/var/log/langhack-update.log}"
LOCK_FILE="/tmp/langhack-update.lock"
INTERACTIVE=false
WEBHOOK_URL="${WEBHOOK_URL:-}"
BACKEND_PID_FILE="$PROJECT_ROOT/logs/backend.pid"
FRONTEND_PID_FILE="$PROJECT_ROOT/logs/frontend.pid"

# 部署记录文件：记录上次成功部署的 commit hash
# 用于替代 git HEAD 作为「已部署版本」的基准
DEPLOY_RECORD_FILE="${DEPLOY_RECORD_FILE:-$PROJECT_ROOT/.last-deployed-commit}"

# 运维结构化日志：供运维端页面查阅
OPS_LOG_FILE="${OPS_LOG_FILE:-$PROJECT_ROOT/logs/deploy-history.log}"

# .env 文件路径：优先 docker/.env（由 deploy.sh 生成），回退到项目根目录 .env
if [ -f "$PROJECT_ROOT/docker/.env" ]; then
    ENV_FILE="$PROJECT_ROOT/docker/.env"
elif [ -f "$PROJECT_ROOT/.env" ]; then
    ENV_FILE="$PROJECT_ROOT/.env"
else
    ENV_FILE="$PROJECT_ROOT/docker/.env"
fi

# 加载 .env（安全读取，不使用 source 避免 JDBC URL 中 & 被 shell 解释为后台执行）
if [ -f "$ENV_FILE" ]; then
    while IFS='=' read -r key value; do
        # 跳过注释和空行
        [[ "$key" =~ ^[[:space:]]*# ]] && continue
        [[ -z "$key" ]] && continue
        # 去除首尾空格
        key=$(echo "$key" | xargs)
        # 只允许合法的变量名（防止命令注入）
        if [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
            export "$key=$value"
        fi
    done < "$ENV_FILE"
fi

# 维护模式 & 备份配置（可通过 .env 中 AUTO_UPDATE_* 覆盖）
MAINTENANCE_MODE="${AUTO_UPDATE_MAINTENANCE:-true}"
PRE_BACKUP="${AUTO_UPDATE_PRE_BACKUP:-true}"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
HEALTH_CHECK_TIMEOUT=120
ENV_BACKUP_KEEP=10

# ── 运维结构化日志函数 ────────────────────────────────
# 写入 deploy-history.log，格式：时间戳|事件类型|commit|描述
ops_log() {
    local event="$1"   # SUCCESS / ROLLBACK / SKIP / ERROR
    local commit="$2"
    local desc="$3"
    mkdir -p "$(dirname "$OPS_LOG_FILE")"
    echo "$(date '+%Y-%m-%d %H:%M:%S')|${event}|${commit}|${desc}" >> "$OPS_LOG_FILE"
}

# ── 参数解析 ──────────────────────────────────────────
OFFLINE_MODE=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        -b|--branch) BRANCH="$2"; shift 2 ;;
        -i|--interactive) INTERACTIVE=true; shift ;;
        --backup-before-update) PRE_BACKUP=true; shift ;;
        --no-backup) PRE_BACKUP=false; shift ;;
        --offline|--local-only) OFFLINE_MODE=true; shift ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "  -b, --branch <分支名>   指定要跟踪的分支 (默认: 当前分支)"
            echo "  -i, --interactive       交互式选择分支"
            echo "  --offline, --local-only 离线模式，跳过 git fetch，使用本地代码直接部署"
            echo "  -h, --help              显示帮助信息"
            echo ""
            echo "模式说明:"
            echo "  UPDATE_MODE=auto        自动识别 Docker / Local (默认)"
            echo "  UPDATE_MODE=docker      强制使用 Docker 更新路径"
            echo "  UPDATE_MODE=local       强制使用本地开发更新路径"
            echo ""
            echo "环境变量 (.env):"
            echo "  DEPLOY_BRANCH              默认跟踪分支 (优先级低于 -b)"
            echo "  UPDATE_MODE                更新模式: auto|docker|local"
            echo "  COMPOSE_FILE               docker-compose 文件路径"
            echo "  WEBHOOK_URL                通知 Webhook URL"
            echo "  AUTO_UPDATE_MAINTENANCE    更新时启用维护模式 (true/false)"
            echo "  AUTO_UPDATE_PRE_BACKUP     更新前备份数据库 (true/false)"
            echo "  BACKUP_DIR                 备份存储目录"
            exit 0
            ;;
        *) echo "[ERROR] 未知参数: $1 (运行 $0 --help 查看帮助)"; exit 1 ;;
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

# ══════════════════════════════════════════════════════
# 核心函数
# ══════════════════════════════════════════════════════

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

is_pid_running() {
    local pid="$1"
    [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

read_pid_file() {
    local pid_file="$1"
    [ -f "$pid_file" ] || return 1
    tr -d '[:space:]' < "$pid_file"
}

remove_pid_file() {
    local pid_file="$1"
    [ -f "$pid_file" ] && rm -f "$pid_file"
}

resolve_update_mode() {
    case "$UPDATE_MODE" in
        docker|local)
            echo "$UPDATE_MODE"
            return 0
            ;;
        auto)
            ;;
        *)
            log "ERROR: UPDATE_MODE 必须是 auto、docker 或 local，当前为: $UPDATE_MODE"
            exit 1
            ;;
    esac

    if command -v docker >/dev/null 2>&1 && [ -f "$COMPOSE_FILE" ]; then
        local services
        services=$(docker compose -f "$COMPOSE_FILE" ps --services 2>/dev/null || true)
        if echo "$services" | grep -qE '^(backend|frontend|mysql-master|mysql-slave|redis)$'; then
            echo "docker"
            return 0
        fi
    fi

    if pgrep -f "spring-boot:run" >/dev/null 2>&1 || pgrep -f "vite|npm run dev" >/dev/null 2>&1 || [ -f "$PROJECT_ROOT/.env" ]; then
        echo "local"
        return 0
    fi

    if [ -f "$PROJECT_ROOT/docker/.env" ]; then
        echo "docker"
        return 0
    fi

    echo "local"
}

wait_for_port_state() {
    local port="$1"
    local expected="$2"
    local timeout="${3:-30}"
    local elapsed=0

    while [ "$elapsed" -lt "$timeout" ]; do
        local listening=false
        if command -v ss >/dev/null 2>&1; then
            ss -tln 2>/dev/null | grep -q ":${port}[[:space:]]" && listening=true
        elif command -v netstat >/dev/null 2>&1; then
            netstat -tln 2>/dev/null | grep -q ":${port}[[:space:]]" && listening=true
        else
            curl -s "http://localhost:${port}" >/dev/null 2>&1 && listening=true || true
        fi

        if { [ "$expected" = "open" ] && [ "$listening" = true ]; } || { [ "$expected" = "closed" ] && [ "$listening" = false ]; }; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
}

stop_pid_or_pattern() {
    local pid_file="$1"
    local pattern="$2"
    local label="$3"
    local pid=""

    pid=$(read_pid_file "$pid_file" 2>/dev/null || true)
    if is_pid_running "$pid"; then
        log "LOCAL: 停止 ${label} 进程 (PID: $pid)"
        kill "$pid" 2>/dev/null || true
        for _ in $(seq 1 10); do
            is_pid_running "$pid" || break
            sleep 1
        done
        if is_pid_running "$pid"; then
            log "LOCAL: 强制停止 ${label} 进程 (PID: $pid)"
            kill -9 "$pid" 2>/dev/null || true
        fi
        remove_pid_file "$pid_file"
        return 0
    fi

    local matched
    matched=$(pgrep -f "$pattern" 2>/dev/null || true)
    if [ -n "$matched" ]; then
        log "LOCAL: 停止 ${label} 进程模式匹配: $matched"
        echo "$matched" | xargs -r kill 2>/dev/null || true
        sleep 2
        matched=$(pgrep -f "$pattern" 2>/dev/null || true)
        if [ -n "$matched" ]; then
            echo "$matched" | xargs -r kill -9 2>/dev/null || true
        fi
    fi
    remove_pid_file "$pid_file"
}

local_stop_backend() {
    stop_pid_or_pattern "$BACKEND_PID_FILE" "spring-boot:run|backend/start_backend\.sh" "backend"
    wait_for_port_state 8080 closed 30 || log "WARN: backend 8080 端口未在预期时间内释放"
}

local_stop_frontend() {
    stop_pid_or_pattern "$FRONTEND_PID_FILE" "vite|npm run dev" "frontend"
    wait_for_port_state 3000 closed 30 || log "WARN: frontend 3000 端口未在预期时间内释放"
}

local_start_backend() {
    mkdir -p "$PROJECT_ROOT/logs"
    log "LOCAL: 启动 backend..."
    nohup bash "$PROJECT_ROOT/backend/start_backend.sh" > "$PROJECT_ROOT/logs/backend.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$BACKEND_PID_FILE"
}

local_start_frontend() {
    mkdir -p "$PROJECT_ROOT/logs"
    log "LOCAL: 启动 frontend..."
    (cd "$PROJECT_ROOT/frontend" && nohup npm run dev > "$PROJECT_ROOT/logs/frontend.log" 2>&1 & echo $! > "$FRONTEND_PID_FILE")
}

local_wait_for_health() {
    local timeout="${1:-120}"
    local elapsed=0
    log "LOCAL: 等待本地服务健康检查通过 (超时 ${timeout}s)..."

    while [ "$elapsed" -lt "$timeout" ]; do
        local backend_ok=false
        local frontend_ok=false
        curl -sf http://localhost:8080/api/health >/dev/null 2>&1 && backend_ok=true || true
        curl -sf -I http://localhost:3000 >/dev/null 2>&1 && frontend_ok=true || true

        if [ "$backend_ok" = true ] && [ "$frontend_ok" = true ]; then
            log "LOCAL: 本地服务健康检查通过 ✓ (${elapsed}s)"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    log "ERROR: LOCAL: 本地服务健康检查失败，请检查 logs/backend.log 与 logs/frontend.log"
    return 1
}

local_restart_changed_services() {
    local restart_backend="$1"
    local restart_frontend="$2"

    [ "$restart_backend" = true ] && local_stop_backend
    [ "$restart_frontend" = true ] && local_stop_frontend
    [ "$restart_backend" = true ] && local_start_backend
    [ "$restart_frontend" = true ] && local_start_frontend
    local_wait_for_health "$HEALTH_CHECK_TIMEOUT"
}

local_rollback_code_only() {
    local old_commit="$1"
    local restart_backend="$2"
    local restart_frontend="$3"

    log "ROLLBACK: LOCAL 模式回退到 ${old_commit:0:8}..."
    notify "本地模式更新失败，正在回退到 ${old_commit:0:8}..."

    git checkout "$old_commit" --force --quiet 2>/dev/null || {
        log "CRITICAL: LOCAL 回退代码失败，需要人工介入"
        ops_log "ERROR" "${old_commit:0:8}" "LOCAL 回退代码失败"
        return 1
    }

    if local_restart_changed_services "$restart_backend" "$restart_frontend"; then
        log "ROLLBACK: LOCAL 回退成功 ✓"
        ops_log "ROLLBACK" "${old_commit:0:8}" "LOCAL 模式重启失败后已回退代码并恢复进程"
        return 0
    fi

    log "CRITICAL: LOCAL 回退后重启仍失败，需要人工介入"
    ops_log "ERROR" "${old_commit:0:8}" "LOCAL 回退后重启仍失败"
    return 1
}

notify() {
    local message="$1"
    if [ -n "$WEBHOOK_URL" ]; then
        # 转义 JSON 特殊字符（\, ", 换行）
        local escaped
        escaped=$(printf '%s' "$message" | sed 's/\\/\\\\/g; s/"/\\"/g; s/$/\\n/' | tr -d '\n' | sed 's/\\n$//')
        curl -s -X POST "$WEBHOOK_URL" \
            -H 'Content-Type: application/json' \
            -d "{\"msgtype\":\"text\",\"text\":{\"content\":\"[LangHack] $escaped\"}}" \
            > /dev/null 2>&1 || true
    fi
}

# ── 维护模式 ──────────────────────────────────────────

MAINTENANCE_ACTIVE=false

enable_maintenance() {
    [ "$MAINTENANCE_MODE" != "true" ] && return 0
    MAINTENANCE_ACTIVE=true
    log "MAINTENANCE: 开启维护模式..."
    # MSYS_NO_PATHCONV=1 防止 Git Bash 将容器内绝对路径转换为 Windows 路径
    local container
    container=$(docker compose -f "$COMPOSE_FILE" ps -q frontend 2>/dev/null || \
                docker compose -f "$COMPOSE_FILE" ps -q app 2>/dev/null || true)
    if [ -n "$container" ]; then
        MSYS_NO_PATHCONV=1 docker exec "$container" touch /usr/share/nginx/html/maintenance.flag 2>/dev/null || \
            log "WARN: 无法开启维护模式（写入标记文件失败）"
    else
        log "WARN: 无法开启维护模式（frontend 容器可能未运行）"
    fi
}

disable_maintenance() {
    [ "$MAINTENANCE_MODE" != "true" ] && return 0
    [ "$MAINTENANCE_ACTIVE" != "true" ] && return 0
    MAINTENANCE_ACTIVE=false
    log "MAINTENANCE: 关闭维护模式..."
    local container
    container=$(docker compose -f "$COMPOSE_FILE" ps -q frontend 2>/dev/null || \
                docker compose -f "$COMPOSE_FILE" ps -q app 2>/dev/null || true)
    if [ -n "$container" ]; then
        MSYS_NO_PATHCONV=1 docker exec "$container" rm -f /usr/share/nginx/html/maintenance.flag 2>/dev/null || true
    fi
}

# 清理上次部署异常中断残留的 maintenance.flag
# (与 MAINTENANCE_ACTIVE 无关，这是会话启动时的残留清理)
clear_stale_maintenance() {
    [ "$MAINTENANCE_MODE" != "true" ] && return 0
    local container
    container=$(docker compose -f "$COMPOSE_FILE" ps -q frontend 2>/dev/null || true)
    if [ -n "$container" ]; then
        if MSYS_NO_PATHCONV=1 docker exec "$container" test -f /usr/share/nginx/html/maintenance.flag 2>/dev/null; then
            log "MAINTENANCE: 检测到残留 maintenance.flag，清理中..."
            MSYS_NO_PATHCONV=1 docker exec "$container" rm -f /usr/share/nginx/html/maintenance.flag 2>/dev/null || true
        fi
    fi
}

# ── .env 备份 & 验证 ─────────────────────────────────

backup_env() {
    if [ -f "$ENV_FILE" ]; then
        mkdir -p "$BACKUP_DIR"
        cp "$ENV_FILE" "$BACKUP_DIR/.env.pre-update.$(date +%Y%m%d_%H%M%S)"
        log "CONFIG: .env 已备份"

        # 清理旧备份，只保留最近 N 份
        local count
        count=$(ls -1 "$BACKUP_DIR"/.env.pre-update.* 2>/dev/null | wc -l | tr -d '\r' || echo "0")
        if [ "$count" -gt "$ENV_BACKUP_KEEP" ]; then
            ls -1t "$BACKUP_DIR"/.env.pre-update.* | tail -n +"$((ENV_BACKUP_KEEP + 1))" | xargs rm -f 2>/dev/null || true
            log "CONFIG: 已清理旧 .env 备份 (保留 $ENV_BACKUP_KEEP 份)"
        fi
    fi
}

validate_config() {
    local errors=0
    for key in DB_PASSWORD JWT_SECRET; do
        local val
        val=$(grep -E "^${key}=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- || echo "")
        if [ -z "$val" ] || [[ "$val" == *"change"* ]] || [[ "$val" == *"your"* ]]; then
            log "ERROR: 关键配置 $key 缺失或无效"
            errors=$((errors + 1))
        fi
    done
    return $errors
}

restore_env_if_missing() {
    if [ ! -f "$ENV_FILE" ]; then
        log "CRITICAL: .env 在更新后消失！正在从备份恢复..."
        local latest
        latest=$(ls -1t "$BACKUP_DIR"/.env.pre-update.* 2>/dev/null | head -1 || echo "")
        if [ -n "$latest" ]; then
            cp "$latest" "$ENV_FILE"
            log "CONFIG: .env 已从备份恢复 ($latest)"
        else
            log "CRITICAL: 无可用 .env 备份！需要手动运行 deploy.sh 重新配置"
            notify "紧急: .env 配置文件丢失且无备份，需要手动重新配置！"
        fi
    fi
}

# ── 数据库备份 ────────────────────────────────────────

pre_update_backup() {
    [ "$PRE_BACKUP" != "true" ] && return 0
    mkdir -p "$BACKUP_DIR"

    local backup_file="$BACKUP_DIR/pre-update_$(date +%Y%m%d_%H%M%S).sql.gz"

    # 找到 mysql 服务名
    local mysql_svc
    mysql_svc=$(docker compose -f "$COMPOSE_FILE" ps --services 2>/dev/null \
        | grep -E '^mysql' | head -1 || echo "")

    if [ -z "$mysql_svc" ]; then
        log "WARN: 未找到 MySQL 服务容器，跳过数据库备份"
        return 0
    fi

    # 从 .env 获取密码
    local db_password="${DB_PASSWORD:-}"
    if [ -z "$db_password" ] && [ -f "$ENV_FILE" ]; then
        db_password=$(grep -E '^DB_PASSWORD=' "$ENV_FILE" | cut -d'=' -f2- || echo "")
    fi

    if [ -z "$db_password" ]; then
        log "WARN: 未找到数据库密码，跳过数据库备份"
        return 0
    fi

    log "BACKUP: 正在备份数据库..."
    # 使用子 shell 隔离 pipefail，避免 mysqldump 失败导致整个脚本崩溃
    if (docker compose -f "$COMPOSE_FILE" exec -T "$mysql_svc" \
        mysqldump -u root -p"$db_password" --single-transaction \
        --routines --triggers english_learning 2>/dev/null \
        | gzip > "$backup_file"); then
        :  # 管道成功
    else
        log "WARN: mysqldump 管道返回非零退出码，备份可能不完整"
    fi

    if [ -s "$backup_file" ]; then
        log "BACKUP: 数据库已备份 → $backup_file ($(du -h "$backup_file" | cut -f1))"
    else
        log "WARN: 备份文件为空（数据库可能为空或连接失败），继续更新"
        rm -f "$backup_file"
    fi
}

# ── 健康检查 ──────────────────────────────────────────

wait_for_health() {
    local timeout="${1:-$HEALTH_CHECK_TIMEOUT}"
    local elapsed=0
    log "HEALTH: 等待所有服务就绪 (超时 ${timeout}s)..."

    while [ $elapsed -lt "$timeout" ]; do
        # 1. 检查是否有容器 unhealthy 或异常退出（快速失败）
        svc_statuses=$(docker compose -f "$COMPOSE_FILE" \
            ps --format '{{.Service}}\t{{.Status}}' 2>/dev/null || true)
        if [ -n "$svc_statuses" ]; then
            failed=$(echo "$svc_statuses" | awk -F'\t' '$2 ~ /unhealthy|Exit [^0]|exited/ {print $1}' || true)
            # 维护模式期间，frontend 因 nginx 返回 503 必然 unhealthy，这是设计行为，排除
            if [ "$MAINTENANCE_ACTIVE" = "true" ]; then
                failed=$(echo "$failed" | grep -v '^frontend$' || true)
            fi
            if [ -n "$failed" ]; then
                log "ERROR: HEALTH: 以下服务启动失败，终止健康等待："
                echo "$failed" | while read -r svc; do
                    log "ERROR:   [$svc] $(echo "$svc_statuses" | awk -F'\t' -v s="$svc" '$1==s{print $2}')"
                    log "ERROR:   [$svc] 最近日志:"
                    docker compose -f "$COMPOSE_FILE" logs --tail=20 "$svc" 2>/dev/null \
                        | while read -r line; do log "         $line"; done
                done
                return 1
            fi
        fi

        # 2. 检查后端 HTTP 健康端点（确认应用层就绪）
        if curl -sf http://localhost:8080/api/health > /dev/null 2>&1; then
            log "HEALTH: 所有服务正常 ✓ (${elapsed}s)"
            return 0
        fi

        sleep 5
        elapsed=$((elapsed + 5))
        log "HEALTH: 等待中... ${elapsed}s"
    done

    log "ERROR: HEALTH: 健康检查超时 (${timeout}s)"
    # 超时时打印当前容器状态
    docker compose -f "$COMPOSE_FILE" ps 2>/dev/null | while read -r line; do
        log "         $line"
    done
    return 1
}

# ── AI 服务商配置完整性检查 ──────────────────────────────
# 检查 ai_provider 表中是否存在 api_key 被脱敏覆盖或 model 为空的记录
# 这类问题不影响服务启动，但会导致翻译等功能静默失败，故只告警不回滚

verify_ai_providers() {
    local db_password="${DB_PASSWORD:-}"
    if [ -z "$db_password" ] && [ -f "$ENV_FILE" ]; then
        db_password=$(grep -E '^DB_PASSWORD=' "$ENV_FILE" | cut -d'=' -f2- || echo "")
    fi
    if [ -z "$db_password" ]; then
        log "WARN: 未找到数据库密码，跳过 AI 服务商配置检查"
        return 0
    fi

    local mysql_container
    mysql_container=$(docker ps --format '{{.Names}}' | grep -E 'mysql.master|mysql-master' | head -1 || echo "")
    if [ -z "$mysql_container" ]; then
        mysql_container=$(docker ps --format '{{.Names}}' | grep mysql | head -1 || echo "")
    fi
    if [ -z "$mysql_container" ]; then
        log "WARN: 未找到 MySQL 容器，跳过 AI 服务商配置检查"
        return 0
    fi

    log "AI_PROVIDER: 检查服务商配置完整性..."

    # 检查 api_key 含 **** 的记录（脱敏值被写回数据库）
    local masked_count
    masked_count=$(docker exec "$mysql_container" \
        mysql -uroot -p"$db_password" english_learning \
        -sN -e "SELECT COUNT(*) FROM ai_provider WHERE api_key LIKE '%****%' AND enabled=1;" \
        2>/dev/null || echo "0")
    masked_count=$(echo "$masked_count" | tr -d '[:space:]')

    if [ "${masked_count:-0}" -gt 0 ]; then
        log "WARN: 发现 ${masked_count} 条 AI 服务商记录的 api_key 含脱敏占位符 (****)"
        log "WARN: 这些服务商将无法正常调用，请在运维面板重新填写真实 API Key"
        notify "部署警告: ${masked_count} 条 AI 服务商 api_key 疑似被脱敏值覆盖，翻译等功能可能失败"
        ops_log "WARN" "$(git rev-parse --short HEAD)" "AI 服务商 api_key 含脱敏值，需人工修复"
    fi

    # 检查 model 为空的启用记录
    local empty_model_count
    empty_model_count=$(docker exec "$mysql_container" \
        mysql -uroot -p"$db_password" english_learning \
        -sN -e "SELECT COUNT(*) FROM ai_provider WHERE (model IS NULL OR model='') AND enabled=1;" \
        2>/dev/null || echo "0")
    empty_model_count=$(echo "$empty_model_count" | tr -d '[:space:]')

    if [ "${empty_model_count:-0}" -gt 0 ]; then
        log "WARN: 发现 ${empty_model_count} 条 AI 服务商记录的 model 字段为空"
        log "WARN: 调用这些服务商时将触发 API 400 错误，请在运维面板补全 model 名称"
        notify "部署警告: ${empty_model_count} 条 AI 服务商 model 字段为空，翻译等功能可能失败"
        ops_log "WARN" "$(git rev-parse --short HEAD)" "AI 服务商 model 字段为空，需人工修复"
    fi

    if [ "${masked_count:-0}" -eq 0 ] && [ "${empty_model_count:-0}" -eq 0 ]; then
        log "AI_PROVIDER: 配置检查通过 ✓"
    fi
}

# ── 增量 SQL 迁移 ─────────────────────────────────────
# 直接执行 docker/mysql/init/04-apply-all-migrations.sql（与 deploy.sh 同一数据源）
# 使用 root 账户确保 DDL 权限（CREATE TABLE / ALTER TABLE 等）
# 全幂等（IF NOT EXISTS + INSERT IGNORE），新旧数据库均可安全重复执行

run_incremental_migrations() {
    local migration_sql="$PROJECT_ROOT/docker/mysql/init/04-apply-all-migrations.sql"

    if [ ! -f "$migration_sql" ]; then
        log "ERROR: MIGRATE: 迁移脚本不存在: $migration_sql"
        return 1
    fi

    # Git LFS 指针文件检测：LFS 文件未下载时内容是版本指针而非真实 SQL
    if head -1 "$migration_sql" 2>/dev/null | grep -qE '^version https://git-lfs\.github\.com'; then
        log "ERROR: MIGRATE: 迁移脚本为 Git LFS 指针文件，未下载真实内容"
        log "ERROR:   文件: $migration_sql"
        log "ERROR:   请执行 'git lfs pull' 后重新部署"
        notify "部署阻塞: 迁移脚本是 LFS 指针文件，请执行 git lfs pull"
        return 1
    fi

    # 版本同步校验：Flyway 文件数 vs 合并脚本头部列表
    local flyway_dir="$PROJECT_ROOT/backend/src/main/resources/db/migration"
    if [ -d "$flyway_dir" ]; then
        local flyway_count merged_count
        flyway_count=$(ls "$flyway_dir"/V*.sql 2>/dev/null | wc -l | tr -d '\r' || echo 0)
        merged_count=$(grep -cE '^--   V[0-9]+(\.[0-9]+)?__' "$migration_sql" 2>/dev/null | tr -d '\r' || echo 0)
        if [ "$flyway_count" -gt "$merged_count" ]; then
            log "ERROR: MIGRATE: 迁移脚本版本不同步，阻塞部署"
            log "ERROR:   Flyway 文件数: ${flyway_count}"
            log "ERROR:   合并脚本版本: ${merged_count}"
            log "ERROR:   请将新迁移同步到 docker/mysql/init/04-apply-all-migrations.sql 后重新部署"
            notify "部署阻塞: 迁移脚本版本不同步（Flyway: ${flyway_count}, 合并: ${merged_count}），请检查"
            return 1
        fi
    fi

    # 使用 root 账户（DDL 权限）
    local db_root_pw
    db_root_pw=$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- || echo "")
    [ -z "$db_root_pw" ] && db_root_pw=$(grep -E '^DB_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- || echo "")
    if [ -z "$db_root_pw" ]; then
        log "WARN: MIGRATE: 未找到数据库 root 密码，跳过增量迁移"
        return 0
    fi

    # 找到 mysql-master 容器
    local mysql_container
    mysql_container=$(docker ps --format '{{.Names}}' | grep -E 'mysql.master|mysql-master' | head -1 || echo "")
    [ -z "$mysql_container" ] && mysql_container=$(docker ps --format '{{.Names}}' | grep mysql | head -1 || echo "")
    if [ -z "$mysql_container" ]; then
        log "ERROR: MIGRATE: 未找到 MySQL 容器，无法执行增量迁移"
        return 1
    fi

    # 等待 MySQL 就绪（最多 60s）
    log "MIGRATE: 等待 MySQL 就绪 (容器: $mysql_container)..."
    local waited=0
    until docker exec "$mysql_container" mysqladmin ping -uroot -p"$db_root_pw" --silent 2>/dev/null; do
        sleep 2; waited=$((waited + 2))
        if [ $waited -ge 60 ]; then
            log "ERROR: MIGRATE: MySQL 在 60s 内未就绪，跳过迁移"
            return 1
        fi
    done

    # 提取最高版本号用于日志
    local max_ver
    max_ver=$(grep -oE '^--   V[0-9]+__' "$migration_sql" 2>/dev/null | grep -oE '[0-9]+' | sort -n | tail -1 || echo "?")
    log "MIGRATE: 执行 04-apply-all-migrations.sql (V1~V${max_ver})..."

    local migrate_output migrate_exit migrate_errors
    migrate_output=$(docker exec -i "$mysql_container" mysql -uroot -p"$db_root_pw" 2>&1 < "$migration_sql")
    migrate_exit=$?
    migrate_errors=$(echo "$migrate_output" \
        | grep -v "Using a password on the command line interface can be insecure" \
        | grep -v "^$" || true)

    if [ $migrate_exit -ne 0 ]; then
        log "ERROR: MIGRATE: 增量迁移失败，退出码: $migrate_exit"
        [ -n "$migrate_errors" ] && log "$migrate_errors"
        return 1
    elif [ -n "$migrate_errors" ]; then
        log "WARN: MIGRATE: 迁移完成但有警告:"
        log "$migrate_errors"
    fi
    log "MIGRATE: 增量迁移完成 ✓ (V1~V${max_ver})"

    # 种子数据检查与自动补全：docker compose down -v 后可能丢失
    local texts_count ecdict_count article_count
    texts_count=$(docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" \
        -sN -e "SELECT COUNT(*) FROM texts;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
    ecdict_count=$(docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" \
        -sN -e "SELECT COUNT(*) FROM ecdict_words;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
    article_count=$(docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" \
        -sN -e "SELECT COUNT(*) FROM article;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")

    local need_seed=false
    [ "${texts_count:-0}" = "0" ] && need_seed=true
    [ "${ecdict_count:-0}" -lt 100000 ] && need_seed=true

    if [ "$need_seed" = true ]; then
        log "SEED: 检测到空数据库（texts: ${texts_count}, ecdict_words: ${ecdict_count}, article: ${article_count}），开始自动导入..."

        local db_name="${MYSQL_DATABASE:-english_learning}"

        # ① 全量备份
        local seed_file=""
        for dir in "$PROJECT_ROOT/database" "$PROJECT_ROOT/backups"; do
            [ -d "$dir" ] || continue
            for file in "$dir"/*.sql "$dir"/*.sql.gz; do
                [ -f "$file" ] || continue
                basename "$file" | grep -q "_incr_" && continue
                local fsize; fsize=$(stat -c%s "$file" 2>/dev/null || echo "0")
                [ "$fsize" -lt 1024 ] && continue
                basename "$file" | grep -qi "schema_export" && continue
                if [ -z "$seed_file" ]; then
                    seed_file="$file"
                else
                    local cur_mtime best_mtime
                    cur_mtime=$(stat -c%Y "$file" 2>/dev/null || echo "0")
                    best_mtime=$(stat -c%Y "$seed_file" 2>/dev/null || echo "0")
                    [ "$cur_mtime" -gt "$best_mtime" ] && seed_file="$file"
                fi
            done
        done

        if [ -n "$seed_file" ] && [ -f "$seed_file" ]; then
            log "SEED: 导入全量备份: $(basename "$seed_file")"
            case "$seed_file" in
                *.sql.gz) zcat "$seed_file" | docker exec -i "$mysql_container" mysql -uroot -p"$db_root_pw" "$db_name" 2>&1 | grep -v "Using a password" || true ;;
                *.sql)    docker exec -i "$mysql_container" mysql -uroot -p"$db_root_pw" "$db_name" < "$seed_file" 2>&1 | grep -v "Using a password" || true ;;
            esac
            log "SEED: 全量备份导入完成 ✓"
        else
            log "WARN: SEED: 未找到全量备份文件，跳过"
        fi

        # ② 3Blue1Brown 课文
        local article_dir="$PROJECT_ROOT/database/3Blue1Brown"
        if [ -d "$article_dir" ]; then
            local article_after; article_after=$(docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" "$db_name" \
                -sN -e "SELECT COUNT(*) FROM article;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
            if [ "${article_after:-0}" = "0" ]; then
                log "SEED: 导入课文数据 (3Blue1Brown)..."
                for f in "$article_dir"/*.sql; do
                    [ -f "$f" ] || continue
                    docker exec -i "$mysql_container" mysql -uroot -p"$db_root_pw" "$db_name" < "$f" 2>&1 | grep -v "Using a password" || true
                done
                log "SEED: 课文数据导入完成 ✓"
            fi
        fi

        # ③ ECDICT 词典
        local ecdict_after; ecdict_after=$(docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" "$db_name" \
            -sN -e "SELECT COUNT(*) FROM ecdict_words;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
        if [ "${ecdict_after:-0}" -lt 100000 ]; then
            local ecdict_csv="$PROJECT_ROOT/ECDICT-master/ecdict.csv"
            if [ -f "$ecdict_csv" ]; then
                log "SEED: 导入 ECDICT 词典（预计 1-3 分钟）..."
                docker cp "$ecdict_csv" "$mysql_container:/tmp/ecdict.csv"
                docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" -e "SET GLOBAL local_infile=ON;" 2>/dev/null || true
                docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" --local-infile=1 "$db_name" -e \
                    "TRUNCATE TABLE ecdict_words;
                     LOAD DATA LOCAL INFILE '/tmp/ecdict.csv'
                     INTO TABLE ecdict_words
                     CHARACTER SET utf8mb4
                     FIELDS TERMINATED BY ','
                     OPTIONALLY ENCLOSED BY '\"'
                     LINES TERMINATED BY '\n'
                     IGNORE 1 LINES
                     (word, phonetic, definition, translation, pos, collins, oxford, tag, bnc, frq, exchange, @detail, @audio);" 2>&1 \
                    | grep -v "Using a password" || true
                docker exec "$mysql_container" rm -f /tmp/ecdict.csv
                log "SEED: ECDICT 词典导入完成 ✓"
            else
                log "WARN: SEED: ECDICT CSV 不存在: $ecdict_csv"
            fi
        fi

        # 验证
        local texts_final ecdict_final article_final
        texts_final=$(docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" "$db_name" \
            -sN -e "SELECT COUNT(*) FROM texts;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
        ecdict_final=$(docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" "$db_name" \
            -sN -e "SELECT COUNT(*) FROM ecdict_words;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
        article_final=$(docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" "$db_name" \
            -sN -e "SELECT COUNT(*) FROM article;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
        log "SEED: 种子数据导入完成 ✓ (texts: ${texts_final}, ecdict_words: ${ecdict_final}, article: ${article_final})"
    fi

    # 验证从库复制延迟
    local slave_container slave_lag_wait=0
    slave_container=$(docker ps --format '{{.Names}}' | grep -E 'mysql.slave|mysql-slave' | head -1 || echo "")
    if [ -n "$slave_container" ]; then
        log "MIGRATE: 验证从库复制延迟..."
        until [ $slave_lag_wait -ge 30 ]; do
            local lag io
            lag=$(docker exec "$slave_container" mysql -uroot -p"$db_root_pw" \
                  -e "SHOW REPLICA STATUS\G" 2>/dev/null \
                  | grep "Seconds_Behind_Source:" | awk '{print $2}' || echo "NULL")
            io=$(docker exec "$slave_container" mysql -uroot -p"$db_root_pw" \
                 -e "SHOW REPLICA STATUS\G" 2>/dev/null \
                 | grep "Replica_IO_Running:" | awk '{print $2}' || echo "Unknown")
            if [ "$lag" = "0" ] && [ "$io" = "Yes" ]; then
                log "MIGRATE: 从库同步完成 ✓ (延迟: 0s)"
                break
            elif [ "$lag" = "NULL" ]; then
                log "ERROR: MIGRATE: 从库复制未配置（SHOW REPLICA STATUS 为空），迁移后数据无法同步到从库"
                log "ERROR:          请先运行 deploy.sh 完成主从复制初始化，再执行自动更新"
                return 1
            fi
            log "MIGRATE: 等待从库同步... (延迟: ${lag}s, IO: ${io})"
            sleep 3; slave_lag_wait=$((slave_lag_wait + 3))
        done
        [ $slave_lag_wait -ge 30 ] && log "WARN: MIGRATE: 从库同步等待超时，请手动检查"
    fi

    return 0
}

# ── 数据库 Schema 验证 ───────────────────────────────
# 验证本项目关键表是否存在
# 每新增一个 Flyway 迁移版本，在此表列中补充该版本引入的标志性表

verify_flyway_migrations() {
    log "SCHEMA: 验证关键表是否存在..."

    local db_root_pw
    db_root_pw=$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- || echo "")
    [ -z "$db_root_pw" ] && db_root_pw=$(grep -E '^DB_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- || echo "")
    if [ -z "$db_root_pw" ]; then
        log "WARN: 未找到数据库密码，跳过 Schema 验证"
        return 0
    fi

    local mysql_container
    mysql_container=$(docker ps --format '{{.Names}}' | grep -E 'mysql.master|mysql-master' | head -1 || echo "")
    [ -z "$mysql_container" ] && mysql_container=$(docker ps --format '{{.Names}}' | grep mysql | head -1 || echo "")
    if [ -z "$mysql_container" ]; then
        log "WARN: 未找到 MySQL 容器，跳过 Schema 验证"
        return 0
    fi

    # 关键标志表（每个引入新表的 V 版本选一张有代表性的表）：
    #   V1: score_records       V2: text_learning_records   V3: media
    #   V4: backup_records      V5: access_log              V6: system_config
    #   V7: article_favorites   V8: sso_user_bindings       V9: administrative_class
    local missing_tables=""
    for tbl in score_records text_learning_records media backup_records access_log \
               system_config ai_provider article_favorites user_tags feature_override \
               sso_user_bindings administrative_class; do
        local exists
        exists=$(docker exec "$mysql_container" \
            mysql -uroot -p"$db_root_pw" english_learning \
            -sN -e "SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='${tbl}';" \
            2>/dev/null | tr -d '[:space:]' || echo "0")
        if [ "${exists:-0}" = "0" ]; then
            missing_tables="${missing_tables} ${tbl}"
        fi
    done

    if [ -n "$missing_tables" ]; then
        log "ERROR: 数据库缺少关键表:${missing_tables}"
        notify "部署警告: 数据库缺少关键表:${missing_tables}，已触发回滚"
        ops_log "ERROR" "$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')" "Schema 缺少关键表:${missing_tables}"
        return 1
    fi

    log "SCHEMA: 核心表验证通过 ✓ (V1~V9 全部关键表存在)"
    return 0
}

# ── 回滚 ─────────────────────────────────────────────

rollback_to_old_version() {
    local old_commit="$1"
    local services="$2"
    log "ROLLBACK: 健康检查失败，回滚到 ${old_commit:0:8}..."
    notify "自动更新失败，正在回滚到 ${old_commit:0:8}..."

    # 新容器可能已替换旧容器，重新开启维护模式
    enable_maintenance

    # 恢复旧代码
    git checkout "$old_commit" --force --quiet 2>/dev/null || {
        log "CRITICAL: 无法回滚到 ${old_commit:0:8}！需要人工介入"
        notify "紧急: git checkout 回滚失败，请立即检查！"
        return 1
    }

    # 用旧代码重建
    log "ROLLBACK: 重建旧版本..."
    if ! docker compose -f "$COMPOSE_FILE" build --no-cache $services 2>&1; then
        log "CRITICAL: 旧版本构建也失败！需要人工介入"
        notify "紧急: 回滚构建失败，请立即检查！"
        return 1
    fi

    # 重启
    docker compose -f "$COMPOSE_FILE" up -d $services 2>&1 || {
        log "CRITICAL: 旧版本启动失败！需要人工介入"
        notify "紧急: 回滚启动失败，请立即检查！"
        return 1
    }

    # 等待旧版本启动
    if wait_for_health 120; then
        log "ROLLBACK: 旧版本恢复成功 ✓"
        notify "回滚成功: 已恢复到 ${old_commit:0:8}"
        # 写入回滚暂停标记，防止下次 cron 再次拉取同一有问题的 commit
        echo "ROLLBACK:${old_commit}" > "$DEPLOY_RECORD_FILE"
        ops_log "ROLLBACK" "${old_commit:0:8}" "健康检查失败，已回滚并暂停自动更新。恢复请执行: rm '$DEPLOY_RECORD_FILE'"
        disable_maintenance
        return 0
    else
        log "CRITICAL: 回滚后健康检查仍失败！需要人工介入"
        notify "紧急: 回滚后服务仍不可用，请立即检查！"
        ops_log "ERROR" "${old_commit:0:8}" "CRITICAL: 回滚后健康检查仍失败，需要人工介入"
        return 1
    fi
}

# ── 清理 ─────────────────────────────────────────────

cleanup() {
    local exit_code=$?

    # 防止重复清理
    if [ "$CLEANUP_DONE" = true ]; then
        return 0
    fi
    CLEANUP_DONE=true

    log "INFO: 执行清理操作（状态: $SCRIPT_STATE, 退出码: $exit_code）..."

    # 清理构建进程
    if [ -n "$BUILD_PID" ] && kill -0 "$BUILD_PID" 2>/dev/null; then
        log "WARN: 终止残留的构建进程 (PID: $BUILD_PID)"
        kill "$BUILD_PID" 2>/dev/null || true
        wait "$BUILD_PID" 2>/dev/null || true
    fi

    # 清理临时文件
    rm -f /tmp/langhack-build-$$.log 2>/dev/null || true

    # 保底: 无论脚本如何退出，都尝试关闭维护模式
    if [ "$MAINTENANCE_ACTIVE" = true ]; then
        log "WARN: 检测到维护模式未关闭，强制关闭"
        disable_maintenance
    fi

    # 清理锁文件
    rm -f "$LOCK_FILE" 2>/dev/null || true

    # 非零退出时输出提示
    if [ $exit_code -ne 0 ]; then
        log "ERROR: 脚本异常退出（状态: $SCRIPT_STATE, 退出码: $exit_code）"
        log "ERROR: 完整日志: tail -100 $LOG_FILE"

        # 根据状态给出恢复建议
        case "$SCRIPT_STATE" in
            BUILDING)
                log "ERROR: 构建阶段失败，旧服务应仍在运行"
                log "ERROR: 检查构建日志: tail -100 /tmp/langhack-build-*.log"
                ;;
            DEPLOYING)
                log "ERROR: 部署阶段失败，可能需要手动回滚"
                log "ERROR: 检查容器状态: docker compose -f $COMPOSE_FILE ps"
                log "ERROR: 查看容器日志: docker compose -f $COMPOSE_FILE logs --tail=50"
                ;;
            UPDATING)
                log "ERROR: 更新阶段失败，代码可能处于不一致状态"
                log "ERROR: 检查 git 状态: git status"
                ;;
        esac

        # 回滚建议
        if [ "$ROLLBACK_NEEDED" = true ] && [ -n "${PRE_PULL_COMMIT:-}" ]; then
            log "ERROR: 建议手动回滚到上一个版本:"
            log "ERROR:   cd $PROJECT_ROOT"
            log "ERROR:   git checkout ${PRE_PULL_COMMIT:0:8}"
            log "ERROR:   cd docker && docker compose up -d --force-recreate"
        fi
    else
        log "INFO: 清理完成 ✓"
    fi
}

# ══════════════════════════════════════════════════════
# 主流程
# ══════════════════════════════════════════════════════

# ── 防止并发执行 ──────────────────────────────────────
if [ -f "$LOCK_FILE" ]; then
    pid=$(cat "$LOCK_FILE" 2>/dev/null || echo "")
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        log "SKIP: 另一个更新进程正在运行 (PID: $pid)"
        exit 0
    fi
    log "WARN: 发现残留锁文件（进程 $pid 已不存在），清理中..."
    rm -f "$LOCK_FILE"
fi

# 原子性创建锁文件（使用 noclobber 防止竞态条件）
if ( set -o noclobber; echo $$ > "$LOCK_FILE" ) 2>/dev/null; then
    log "INFO: 获取更新锁成功 (PID: $$)"
else
    log "SKIP: 无法获取更新锁，可能有并发进程"
    exit 0
fi

trap cleanup EXIT
trap 'log "WARN: 收到中断信号，正在安全退出..."; SCRIPT_STATE="INTERRUPTED"; exit 130' SIGINT SIGTERM

# ── 进入项目目录 ──────────────────────────────────────
cd "$PROJECT_ROOT"

RUNTIME_MODE=$(resolve_update_mode)
log "INFO: 当前更新模式: $RUNTIME_MODE"

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
if [ -z "$BRANCH" ]; then
    if [ "$CURRENT_BRANCH" = "HEAD" ] || [ "$CURRENT_BRANCH" = "unknown" ]; then
        log "ERROR: 无法识别当前分支，请使用 -b <分支名> 或设置 DEPLOY_BRANCH"
        notify "自动更新失败: 无法识别当前分支"
        exit 1
    fi
    BRANCH="$CURRENT_BRANCH"
fi

# ── 确保在目标分支上 ──────────────────────────────────
if [ "$CURRENT_BRANCH" != "$BRANCH" ]; then
    log "INFO: 切换分支 $CURRENT_BRANCH → $BRANCH"
    git checkout "$BRANCH" --quiet 2>/dev/null || {
        log "ERROR: 切换到分支 $BRANCH 失败"
        notify "自动更新失败: 无法切换到分支 $BRANCH"
        exit 1
    }
fi

# ── 检查 Git 远程更新 ────────────────────────────────
SCRIPT_STATE="CHECKING"
log "INFO: 检查远程仓库更新 (branch: $BRANCH)..."

# 离线模式：跳过 git fetch，直接使用本地代码
if [ "$OFFLINE_MODE" = true ]; then
    log "INFO: 离线模式已启用，跳过 git fetch"
    log "INFO: 将使用本地当前代码进行部署"
    LOCAL_HASH=$(git rev-parse HEAD)
    log "INFO:   本地 HEAD: ${LOCAL_HASH:0:8}"

    # 检查是否有未提交的更改
    if ! git diff-index --quiet HEAD --; then
        log "WARN: 检测到未提交的本地更改，将包含在部署中"
    fi

    # 跳过远程检查，直接进入部署流程
    REMOTE_HASH="$LOCAL_HASH"  # 设置为相同，触发"强制部署"逻辑
else
    # 在线模式：正常 git fetch
    # git fetch 带超时和重试（网络断开时 set -e 会直接退出）
    fetch_ok=false
    MAX_FETCH_ATTEMPTS=3
    FETCH_TIMEOUT=60  # 单次 fetch 超时时间（秒）
    for attempt in $(seq 1 $MAX_FETCH_ATTEMPTS); do
        log "INFO: git fetch 尝试 $attempt/$MAX_FETCH_ATTEMPTS (超时 ${FETCH_TIMEOUT}s)..."

    # 使用 timeout 命令限制 git fetch 执行时间
    if command -v timeout >/dev/null 2>&1; then
        fetch_err=$(timeout "$FETCH_TIMEOUT" git fetch origin "$BRANCH" --quiet 2>&1) && { fetch_ok=true; break; }
        fetch_exit=$?
        # timeout 返回 124 表示超时
        if [ $fetch_exit -eq 124 ]; then
            fetch_err="git fetch 超时 (${FETCH_TIMEOUT}s)"
        fi
    else
        # 无 timeout 命令时回退到原始方式
        fetch_err=$(git fetch origin "$BRANCH" --quiet 2>&1) && { fetch_ok=true; break; }
    fi

    if [ "$attempt" -lt "$MAX_FETCH_ATTEMPTS" ]; then
        log "WARN: git fetch 失败 (尝试 $attempt/$MAX_FETCH_ATTEMPTS)，10s 后重试... 错误: $fetch_err"
        sleep 10
    else
        log "WARN: git fetch 失败 (尝试 $attempt/$MAX_FETCH_ATTEMPTS)，错误: $fetch_err"
    fi
done

# fetch 失败后检查本地是否有已拉取但未应用的更新
if [ "$fetch_ok" = false ]; then
    log "WARN: git fetch 在 $MAX_FETCH_ATTEMPTS 次尝试后仍然失败"
    # 检测是否是 SSH 认证问题，给出修复建议
    if echo "$fetch_err" | grep -q "Permission denied (publickey)"; then
        log "HINT: SSH 认证失败。建议切换为 HTTPS 方式:"
        log "      git remote set-url origin https://<token>@github.com/<user>/<repo>.git"
        log "      或生成 SSH key: ssh-keygen -t ed25519 -C deploy && cat ~/.ssh/id_ed25519.pub"
    elif echo "$fetch_err" | grep -qi "timeout\|timed out"; then
        log "HINT: 网络超时。可能原因："
        log "      1. GitHub 访问受限，考虑配置代理或使用镜像"
        log "      2. DNS 解析慢，检查 /etc/resolv.conf"
        log "      3. 防火墙阻止，检查 iptables/firewalld"
    fi

    # 检查本地是否有 origin/$BRANCH 引用（之前成功 fetch 过）
    if git rev-parse "origin/$BRANCH" >/dev/null 2>&1; then
        log "INFO: 检测到本地已有 origin/$BRANCH 引用，检查是否有未应用的更新..."
        LOCAL_HASH=$(git rev-parse HEAD)
        CACHED_REMOTE_HASH=$(git rev-parse "origin/$BRANCH")

        if [ "$LOCAL_HASH" != "$CACHED_REMOTE_HASH" ]; then
            log "INFO: 发现本地缓存的远程更新（上次 fetch 成功时拉取）"
            log "INFO: 将使用本地缓存的 origin/$BRANCH 进行更新"
            log "INFO:   本地 HEAD: ${LOCAL_HASH:0:8}"
            log "INFO:   缓存远程: ${CACHED_REMOTE_HASH:0:8}"
            notify "网络故障，使用本地缓存的远程分支进行更新 (${CACHED_REMOTE_HASH:0:8})"
            # 继续执行更新流程，使用缓存的 origin/$BRANCH
        else
            log "INFO: 本地 HEAD 与缓存的 origin/$BRANCH 一致，无需更新"
            SCRIPT_STATE="SUCCESS"
            exit 0
        fi
    else
        log "WARN: 本地无 origin/$BRANCH 引用（从未成功 fetch 过），无法使用缓存更新"
        log "WARN: 跳过本次更新，等待网络恢复后重试"
        # 网络临时断开属于正常情况，exit 0 避免 cron 发送告警邮件
        SCRIPT_STATE="SKIPPED"
        exit 0
    fi
else
    log "INFO: git fetch 成功 ✓"
fi  # 结束离线模式 if

LOCAL_HASH=$(git rev-parse HEAD)
# 离线模式下直接使用本地 HEAD，否则使用 origin/$BRANCH
if [ "$OFFLINE_MODE" = true ]; then
    REMOTE_HASH="$LOCAL_HASH"
else
    REMOTE_HASH=$(git rev-parse "origin/$BRANCH")
fi

# 读取上次成功部署的 commit（部署记录文件）
# 若文件不存在，则回退到 git HEAD 作为初始基准
if [ -f "$DEPLOY_RECORD_FILE" ]; then
    DEPLOYED_HASH=$(cat "$DEPLOY_RECORD_FILE" | tr -d '[:space:]')
    # 若记录文件内容是 ROLLBACK: 前缀，说明上次是回滚状态
    if [[ "$DEPLOYED_HASH" == ROLLBACK:* ]]; then
        ROLLED_BACK_TO="${DEPLOYED_HASH#ROLLBACK:}"
        REMOTE_HASH_CHECK=$(git rev-parse "origin/$BRANCH")
        if [ "$REMOTE_HASH_CHECK" = "$ROLLED_BACK_TO" ]; then
            # 上游没有新提交，远程仍是失败的那个 commit，继续暂停
            log "SKIP: 检测到回滚暂停标记 (回滚至 ${ROLLED_BACK_TO:0:8})，远程无新提交，跳过"
            log "      如需强制恢复自动更新，请手动执行: rm '$DEPLOY_RECORD_FILE'"
            ops_log "SKIP" "${ROLLED_BACK_TO:0:8}" "回滚暂停中（远程无新提交），跳过自动更新"
            exit 0
        else
            # 上游已有新提交，自动解除暂停，尝试更新
            log "INFO: 检测到上游有新提交 (${REMOTE_HASH_CHECK:0:8})，自动解除回滚暂停，尝试更新"
            DEPLOYED_HASH="$ROLLED_BACK_TO"
        fi
    fi
else
    # 首次运行：以当前 git HEAD 初始化部署记录
    DEPLOYED_HASH="$LOCAL_HASH"
    echo "$DEPLOYED_HASH" > "$DEPLOY_RECORD_FILE"
    log "INFO: 初始化部署记录文件 (commit: ${DEPLOYED_HASH:0:8})"
fi

if [ "$DEPLOYED_HASH" = "$REMOTE_HASH" ]; then
    log "INFO: 已是最新部署版本，无需更新 (commit: ${DEPLOYED_HASH:0:8})"
    exit 0
fi

# ══════════════════════════════════════════════════════
# 检测到更新，开始完整更新流程
# ══════════════════════════════════════════════════════
log "UPDATE: 检测到新提交！"
log "  已部署: ${DEPLOYED_HASH:0:8}"
log "  远程:   ${REMOTE_HASH:0:8}"

# 显示新增的提交记录
log "──── 新提交列表 ────"
git log --oneline "$DEPLOYED_HASH".."$REMOTE_HASH" | head -20 || true
log "────────────────────"

# ① 记录当前版本（用于回滚）
PRE_PULL_COMMIT="$LOCAL_HASH"   # 保存 pull 前的 HEAD，用于 git checkout 回滚
OLD_COMMIT="$DEPLOYED_HASH"     # 用已部署 commit 作为 diff 基准，检测本次变更范围

# ② 开启维护模式
clear_stale_maintenance
enable_maintenance

# ③ 备份 .env 配置文件
backup_env

# ④ 验证关键配置完整
if ! validate_config; then
    log "ERROR: 关键配置验证失败，中止更新"
    notify "自动更新中止: 关键配置 (DB_PASSWORD/JWT_SECRET) 缺失或无效"
    disable_maintenance
    exit 1
fi

# ⑤ 数据库备份
pre_update_backup

# ⑥ 拉取新代码（强制同步，清理本地修改/未跟踪文件冲突）
log "INFO: 拉取最新代码..."
SCRIPT_STATE="UPDATING"
PRE_PULL_COMMIT=$(git rev-parse HEAD)

# 保存本地修改（如果有）
git stash --include-untracked 2>/dev/null || true
git checkout --force "$BRANCH" --quiet 2>/dev/null || true

# 使用 git reset --hard 而非 git pull，确保原子性和幂等性
if git reset --hard "origin/$BRANCH" --quiet 2>/dev/null; then
    POST_PULL_COMMIT=$(git rev-parse HEAD)
    log "INFO: 代码更新成功 ✓"
    log "INFO:   更新前: ${PRE_PULL_COMMIT:0:8}"
    log "INFO:   更新后: ${POST_PULL_COMMIT:0:8}"
    ROLLBACK_NEEDED=true  # 从此刻起，失败需要回滚
else
    log "ERROR: git reset --hard 失败，可能是 Git 仓库损坏"
    log "ERROR: 建议手动检查: git status && git fsck"
    notify "自动更新失败: git reset 失败，仓库可能损坏"
    ops_log "ERROR" "${PRE_PULL_COMMIT:0:8}" "git reset 失败，仓库可能损坏"
    disable_maintenance
    SCRIPT_STATE="FAILED"
    exit 1
fi

# ⑥.5 确保 Git LFS 文件已下载（git pull 后 LFS 文件可能仍为指针）
if command -v git-lfs &>/dev/null || git lfs version &>/dev/null; then
    git lfs pull 2>/dev/null || log "WARN: git lfs pull 失败，LFS 文件可能未下载（如 .sql 迁移脚本）"
fi

# ⑦ 验证 .env 仍存在
restore_env_if_missing

# ⑧ 判断需要重建哪些服务
CHANGED_FILES=$(git diff --name-only "$OLD_COMMIT" HEAD)

REBUILD_BACKEND=false
REBUILD_FRONTEND=false
RUN_MIGRATIONS=false   # 是否需要执行增量迁移（即使不重建后端）

if echo "$CHANGED_FILES" | grep -qE '^backend/'; then
    REBUILD_BACKEND=true
    RUN_MIGRATIONS=true
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
    RUN_MIGRATIONS=true
    log "INFO: 检测到部署配置变更，全量重建"
fi

# 迁移脚本自身变更时，即使后端代码未动也需要执行迁移
if echo "$CHANGED_FILES" | grep -qE '(docker/mysql/init/04-apply-all-migrations\.sql|backend/src/main/resources/db/migration/)'; then
    RUN_MIGRATIONS=true
    log "INFO: 检测到数据库迁移脚本变更，将执行增量迁移"
fi

if [ "$RUNTIME_MODE" = "local" ] && [ "$RUN_MIGRATIONS" = true ]; then
    log "WARN: LOCAL 模式检测到迁移文件变化；当前不会自动执行容器式迁移，请手动确认本地数据库 schema。"
fi

# ── 执行重建 / 重启 ───────────────────────────────────
if [ "$RUNTIME_MODE" = "docker" ]; then
    if [ "$REBUILD_BACKEND" = true ] || [ "$REBUILD_FRONTEND" = true ]; then
        SERVICES=""
        [ "$REBUILD_BACKEND" = true ] && SERVICES="backend"
        [ "$REBUILD_FRONTEND" = true ] && SERVICES="${SERVICES:+$SERVICES }frontend"

        # ⑨ 构建新镜像（旧容器仍在运行）
        log "INFO: 构建新镜像... (服务: $SERVICES)"
        log "INFO: 构建可能需要数分钟，请耐心等待..."
        SCRIPT_STATE="BUILDING"

        # 使用后台进程 + 进度监控，避免长时间无输出导致用户误以为卡死
        BUILD_LOG="/tmp/langhack-build-$$.log"
        docker compose -f "$COMPOSE_FILE" build --no-cache $SERVICES > "$BUILD_LOG" 2>&1 &
        BUILD_PID=$!

        # 监控构建进程，每 30 秒输出一次心跳
        BUILD_TIMEOUT=1800  # 30 分钟超时
        BUILD_ELAPSED=0
        while kill -0 "$BUILD_PID" 2>/dev/null; do
            sleep 30
            BUILD_ELAPSED=$((BUILD_ELAPSED + 30))
            log "INFO: 构建进行中... (已耗时 ${BUILD_ELAPSED}s)"

            # 超时检测
            if [ $BUILD_ELAPSED -ge $BUILD_TIMEOUT ]; then
                log "ERROR: 构建超时 (${BUILD_TIMEOUT}s)，终止构建"
                kill "$BUILD_PID" 2>/dev/null || true
                wait "$BUILD_PID" 2>/dev/null || true
                rm -f "$BUILD_LOG"
                git checkout "$PRE_PULL_COMMIT" --force --quiet 2>/dev/null || true
                disable_maintenance
                notify "自动更新失败: Docker 构建超时，已回退代码"
                ops_log "ERROR" "${PRE_PULL_COMMIT:0:8}" "构建超时，已回退代码"
                SCRIPT_STATE="FAILED"
                exit 1
            fi
        done

        # 等待构建进程结束并获取退出码
        wait "$BUILD_PID"
        BUILD_EXIT=$?
        BUILD_PID=""  # 清空 PID，防止 cleanup 重复 kill

        if [ $BUILD_EXIT -ne 0 ]; then
            log "ERROR: 构建失败（退出码: $BUILD_EXIT），回退代码"
            log "ERROR: 构建日志（最后 50 行）："
            tail -50 "$BUILD_LOG" | while read -r line; do log "       $line"; done
            rm -f "$BUILD_LOG"
            notify "自动更新失败: Docker 构建失败，正在回退代码..."
            # 构建失败 → 旧容器未受影响，只需 revert git
            git checkout "$PRE_PULL_COMMIT" --force --quiet 2>/dev/null || true
            # 部署记录文件不更新，保持指向上次成功部署的 commit
            disable_maintenance
            notify "已回退到 ${PRE_PULL_COMMIT:0:8}，旧服务继续运行"
            ops_log "ERROR" "${PRE_PULL_COMMIT:0:8}" "构建失败，已回退代码，部署记录未更新"
            SCRIPT_STATE="FAILED"
            exit 1
        fi

        rm -f "$BUILD_LOG"
        log "INFO: 构建完成 ✓ (耗时 ${BUILD_ELAPSED}s)"

        # ⑩ 先执行数据库迁移，再启动新容器（避免新后端启动时 Schema 尚未更新）
        if [ "$RUN_MIGRATIONS" = true ]; then
            run_incremental_migrations || {
                log "ERROR: 增量迁移失败，回退代码（旧容器未受影响）"
                git checkout "$PRE_PULL_COMMIT" --force --quiet 2>/dev/null || true
                disable_maintenance
                notify "自动更新失败: 数据库迁移失败，已回退代码，旧服务继续运行"
                ops_log "ERROR" "${PRE_PULL_COMMIT:0:8}" "迁移失败，已回退代码，部署记录未更新"
                exit 1
            }
            verify_flyway_migrations || {
                log "ERROR: Schema 验证失败，回退代码（旧容器未受影响）"
                git checkout "$PRE_PULL_COMMIT" --force --quiet 2>/dev/null || true
                disable_maintenance
                notify "自动更新失败: Schema 验证失败，已回退代码，旧服务继续运行"
                ops_log "ERROR" "${PRE_PULL_COMMIT:0:8}" "Schema 验证失败，已回退代码，部署记录未更新"
                exit 1
            }
        fi

        # ⑩.5 替换为新容器（Schema 已就绪，后端可安全启动）
        log "INFO: 启动新容器..."
        SCRIPT_STATE="DEPLOYING"

        if docker compose -f "$COMPOSE_FILE" up -d $SERVICES; then
            log "INFO: 容器启动成功 ✓"
        else
            log "ERROR: 容器启动失败，尝试回滚..."
            notify "自动更新失败: 容器启动失败，正在回滚..."

            # 回滚代码
            git checkout "$PRE_PULL_COMMIT" --force --quiet 2>/dev/null || true

            # 重建旧版本镜像并启动
            log "INFO: 重建旧版本镜像..."
            if docker compose -f "$COMPOSE_FILE" build --no-cache $SERVICES 2>&1 | tee -a "$LOG_FILE"; then
                docker compose -f "$COMPOSE_FILE" up -d $SERVICES
                notify "已回滚到 ${PRE_PULL_COMMIT:0:8}"
                ops_log "ERROR" "${PRE_PULL_COMMIT:0:8}" "容器启动失败，已回滚"
            else
                log "ERROR: 回滚构建失败，服务可能不可用"
                notify "严重错误: 回滚失败，服务可能不可用，需要人工介入"
                ops_log "CRITICAL" "${PRE_PULL_COMMIT:0:8}" "回滚失败，服务可能不可用"
            fi

            disable_maintenance
            SCRIPT_STATE="FAILED"
            exit 1
        fi

        # ⑪ 健康检查
        if wait_for_health $HEALTH_CHECK_TIMEOUT; then
            # ⑫ 成功！关闭维护模式并更新部署记录
            disable_maintenance

            NEW_COMMIT=$(git rev-parse HEAD)
            echo "$NEW_COMMIT" > "$DEPLOY_RECORD_FILE"

            # ⑫.5 数据完整性告警检查（不触发回滚，仅告警）
            verify_ai_providers

            log "SUCCESS: 服务已更新并重启"
            log "  更新服务: $SERVICES"
            log "  当前版本: $(git rev-parse --short HEAD)"
            log "  提交信息: $(git log -1 --format='%s')"
            ops_log "SUCCESS" "$(git rev-parse --short HEAD)" "$(git log -1 --format='%s') [服务: $SERVICES]"

            notify "自动更新成功\n版本: $(git rev-parse --short HEAD)\n提交: $(git log -1 --format='%s')\n服务: $SERVICES"
        else
            # 健康检查失败 → 自动回滚
            log "ERROR: 新版本健康检查失败，启动自动回滚..."
            rollback_to_old_version "$PRE_PULL_COMMIT" "$SERVICES"
            exit 1
        fi
    else
        log "INFO: 变更不涉及前后端代码（可能是文档/脚本/迁移），跳过重建"
        if [ "$RUN_MIGRATIONS" = true ]; then
            run_incremental_migrations || {
                log "ERROR: 迁移执行失败，启动自动回滚..."
                rollback_to_old_version "$PRE_PULL_COMMIT" "$SERVICES"
                exit 1
            }
            verify_flyway_migrations || {
                log "ERROR: Schema 验证失败，启动自动回滚..."
                rollback_to_old_version "$PRE_PULL_COMMIT" "$SERVICES"
                exit 1
            }
        fi
        NEW_COMMIT=$(git rev-parse HEAD)
        echo "$NEW_COMMIT" > "$DEPLOY_RECORD_FILE"
        ops_log "SUCCESS" "$(git rev-parse --short HEAD)" "仅文档/脚本变更，跳过重建"
        disable_maintenance
    fi
else
    if [ "$REBUILD_BACKEND" = true ] || [ "$REBUILD_FRONTEND" = true ]; then
        if local_restart_changed_services "$REBUILD_BACKEND" "$REBUILD_FRONTEND"; then
            NEW_COMMIT=$(git rev-parse HEAD)
            echo "$NEW_COMMIT" > "$DEPLOY_RECORD_FILE"
            log "SUCCESS: LOCAL 模式服务已更新并重启"
            ops_log "SUCCESS" "$(git rev-parse --short HEAD)" "LOCAL 模式更新成功 [backend=$REBUILD_BACKEND frontend=$REBUILD_FRONTEND]"
            notify "本地模式自动更新成功\n版本: $(git rev-parse --short HEAD)\n提交: $(git log -1 --format='%s')"
        else
            log "ERROR: LOCAL 模式健康检查失败，启动代码回退..."
            local_rollback_code_only "$PRE_PULL_COMMIT" "$REBUILD_BACKEND" "$REBUILD_FRONTEND"
            exit 1
        fi
    else
        NEW_COMMIT=$(git rev-parse HEAD)
        echo "$NEW_COMMIT" > "$DEPLOY_RECORD_FILE"
        log "INFO: LOCAL 模式检测到无需重启的变更，更新部署记录"
        ops_log "SUCCESS" "$(git rev-parse --short HEAD)" "LOCAL 模式仅文档/脚本变更，跳过重启"
    fi
fi

log "──── 更新完成 ────"
