#!/usr/bin/env bash
# ==============================================================================
# LangHack 自动更新脚本 (v2.0)
# 定时从 GitHub 拉取最新代码，检测变更后自动重新构建并部署
#
# v2.0 新增:
#   - 维护模式: 更新前显示维护页面，完成后自动恢复
#   - 数据库备份: 更新前自动 mysqldump
#   - .env 保护: 备份 + 验证关键配置
#   - 自动回滚: 构建失败 → revert git / 健康检查失败 → 旧版本重建
#
# 用法:
#   chmod +x scripts/auto-update.sh
#   ./scripts/auto-update.sh                    # 默认 master 分支
#   ./scripts/auto-update.sh -b main            # 指定分支
#   ./scripts/auto-update.sh -i                 # 交互式选择分支
#   # crontab (每小时检查)
#   0 * * * * /path/to/LangHack/scripts/auto-update.sh -b master >> /var/log/langhack-update.log 2>&1
# ==============================================================================
set -euo pipefail

# ── 配置 ──────────────────────────────────────────────
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRANCH="${DEPLOY_BRANCH:-master}"
# docker-compose.yml 绝对路径（优先使用环境变量覆盖）
COMPOSE_FILE="${COMPOSE_FILE:-$PROJECT_ROOT/docker/docker-compose.yml}"
LOG_FILE="${LOG_FILE:-/var/log/langhack-update.log}"
LOCK_FILE="/tmp/langhack-update.lock"
INTERACTIVE=false
WEBHOOK_URL="${WEBHOOK_URL:-}"

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

# ── 参数解析 ──────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        -b|--branch) BRANCH="$2"; shift 2 ;;
        -i|--interactive) INTERACTIVE=true; shift ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "  -b, --branch <分支名>   指定要跟踪的分支 (默认: master)"
            echo "  -i, --interactive       交互式选择分支"
            echo "  -h, --help              显示帮助信息"
            echo ""
            echo "环境变量 (.env):"
            echo "  DEPLOY_BRANCH              默认跟踪分支 (优先级低于 -b)"
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

enable_maintenance() {
    [ "$MAINTENANCE_MODE" != "true" ] && return 0
    log "MAINTENANCE: 开启维护模式..."
    # 尝试在 frontend 或 app 容器中创建标记文件
    docker compose -f "$COMPOSE_FILE" exec -T frontend \
        touch /usr/share/nginx/html/maintenance.flag 2>/dev/null || \
    docker compose -f "$COMPOSE_FILE" exec -T app \
        touch /usr/share/nginx/html/maintenance.flag 2>/dev/null || \
    log "WARN: 无法开启维护模式（frontend 容器可能未运行）"
}

disable_maintenance() {
    [ "$MAINTENANCE_MODE" != "true" ] && return 0
    log "MAINTENANCE: 关闭维护模式..."
    docker compose -f "$COMPOSE_FILE" exec -T frontend \
        rm -f /usr/share/nginx/html/maintenance.flag 2>/dev/null || \
    docker compose -f "$COMPOSE_FILE" exec -T app \
        rm -f /usr/share/nginx/html/maintenance.flag 2>/dev/null || true
}

# ── .env 备份 & 验证 ─────────────────────────────────

backup_env() {
    if [ -f "$ENV_FILE" ]; then
        mkdir -p "$BACKUP_DIR"
        cp "$ENV_FILE" "$BACKUP_DIR/.env.pre-update.$(date +%Y%m%d_%H%M%S)"
        log "CONFIG: .env 已备份"

        # 清理旧备份，只保留最近 N 份
        local count
        count=$(ls -1 "$BACKUP_DIR"/.env.pre-update.* 2>/dev/null | wc -l || echo "0")
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
    log "HEALTH: 等待服务启动 (超时 ${timeout}s)..."

    while [ $elapsed -lt "$timeout" ]; do
        # 使用与 docker-compose.yml healthcheck 一致的端点
        if curl -sf http://localhost:8080/api/health > /dev/null 2>&1; then
            log "HEALTH: 后端服务正常 ✓ (${elapsed}s)"
            return 0
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done

    log "HEALTH: 健康检查超时 (${timeout}s)"
    return 1
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
        return 0
    else
        log "CRITICAL: 回滚后健康检查仍失败！需要人工介入"
        notify "紧急: 回滚后服务仍不可用，请立即检查！"
        return 1
    fi
}

# ── 清理 ─────────────────────────────────────────────

cleanup() {
    # 保底: 无论脚本如何退出，都尝试关闭维护模式
    disable_maintenance
    rm -f "$LOCK_FILE"
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

# git fetch 带重试（网络断开时 set -e 会直接退出）
fetch_ok=false
for attempt in 1 2 3; do
    if git fetch origin "$BRANCH" --quiet 2>/dev/null; then
        fetch_ok=true
        break
    fi
    log "WARN: git fetch 失败 (尝试 $attempt/3)，10s 后重试..."
    sleep 10
done
if [ "$fetch_ok" = false ]; then
    log "ERROR: git fetch 在 3 次尝试后仍然失败，跳过本次更新"
    notify "自动更新失败: 无法连接远程仓库 (3 次重试均失败)"
    exit 1
fi

LOCAL_HASH=$(git rev-parse HEAD)
REMOTE_HASH=$(git rev-parse "origin/$BRANCH")

if [ "$LOCAL_HASH" = "$REMOTE_HASH" ]; then
    log "INFO: 已是最新，无需更新 (commit: ${LOCAL_HASH:0:8})"
    exit 0
fi

# ══════════════════════════════════════════════════════
# 检测到更新，开始完整更新流程
# ══════════════════════════════════════════════════════
log "UPDATE: 检测到新提交！"
log "  本地: ${LOCAL_HASH:0:8}"
log "  远程: ${REMOTE_HASH:0:8}"

# 显示新增的提交记录
log "──── 新提交列表 ────"
git log --oneline "$LOCAL_HASH".."$REMOTE_HASH" | head -20
log "────────────────────"

# ① 记录当前版本（用于回滚）
OLD_COMMIT="$LOCAL_HASH"

# ② 开启维护模式
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

# ⑥ 拉取新代码
log "INFO: 拉取最新代码..."
if ! git pull origin "$BRANCH" --ff-only; then
    log "ERROR: git pull 失败（可能有本地冲突），跳过本次更新"
    notify "自动更新失败: git pull 出现冲突，需要手动处理"
    disable_maintenance
    exit 1
fi

# ⑦ 验证 .env 仍存在
restore_env_if_missing

# ⑧ 判断需要重建哪些服务
CHANGED_FILES=$(git diff --name-only "$OLD_COMMIT" HEAD)

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
    SERVICES=""
    [ "$REBUILD_BACKEND" = true ] && SERVICES="backend"
    [ "$REBUILD_FRONTEND" = true ] && SERVICES="${SERVICES:+$SERVICES }frontend"

    # ⑨ 构建新镜像（旧容器仍在运行）
    log "INFO: 构建新镜像... (服务: $SERVICES)"
    if ! docker compose -f "$COMPOSE_FILE" build --no-cache $SERVICES; then
        log "ERROR: 构建失败，回退代码"
        notify "自动更新失败: Docker 构建失败，正在回退代码..."
        # 构建失败 → 旧容器未受影响，只需 revert git
        git checkout "$OLD_COMMIT" --force --quiet 2>/dev/null || true
        disable_maintenance
        notify "已回退到 ${OLD_COMMIT:0:8}，旧服务继续运行"
        exit 1
    fi

    # ⑩ 替换为新容器
    log "INFO: 启动新容器..."
    docker compose -f "$COMPOSE_FILE" up -d $SERVICES

    # ⑪ 健康检查
    if wait_for_health $HEALTH_CHECK_TIMEOUT; then
        # ⑫ 成功！关闭维护模式
        disable_maintenance

        log "SUCCESS: 服务已更新并重启"
        log "  更新服务: $SERVICES"
        log "  当前版本: $(git rev-parse --short HEAD)"
        log "  提交信息: $(git log -1 --format='%s')"

        notify "自动更新成功\n版本: $(git rev-parse --short HEAD)\n提交: $(git log -1 --format='%s')\n服务: $SERVICES"
    else
        # 健康检查失败 → 自动回滚
        log "ERROR: 新版本健康检查失败，启动自动回滚..."
        rollback_to_old_version "$OLD_COMMIT" "$SERVICES"
        # disable_maintenance 在 rollback 内部或 trap cleanup 中处理
        exit 1
    fi
else
    log "INFO: 变更不涉及前后端代码（可能是文档/脚本），跳过重建"
    # 无需重建，直接关闭维护模式
    disable_maintenance
fi

log "──── 更新完成 ────"
