#!/usr/bin/env bash
# ==============================================================================
# LangHack 自动更新脚本 (v2.3)
# 定时从 GitHub 拉取最新代码，检测变更后自动重新构建并部署
#
# v2.0 新增:
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
while [[ $# -gt 0 ]]; do
    case "$1" in
        -b|--branch) BRANCH="$2"; shift 2 ;;
        -i|--interactive) INTERACTIVE=true; shift ;;
        --backup-before-update) PRE_BACKUP=true; shift ;;
        --no-backup) PRE_BACKUP=false; shift ;;
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
    log "HEALTH: 等待所有服务就绪 (超时 ${timeout}s)..."

    while [ $elapsed -lt "$timeout" ]; do
        # 1. 检查是否有容器 unhealthy 或异常退出（快速失败）
        svc_statuses=$(docker compose -f "$DOCKER_DIR/docker-compose.yml" \
            ps --format '{{.Service}}\t{{.Status}}' 2>/dev/null || true)
        if [ -n "$svc_statuses" ]; then
            failed=$(echo "$svc_statuses" | awk -F'\t' '$2 ~ /unhealthy|Exit [^0]|exited/ {print $1}' || true)
            if [ -n "$failed" ]; then
                log "ERROR: HEALTH: 以下服务启动失败，终止健康等待："
                echo "$failed" | while read -r svc; do
                    log "ERROR:   [$svc] $(echo "$svc_statuses" | awk -F'\t' -v s="$svc" '$1==s{print $2}')"
                    log "ERROR:   [$svc] 最近日志:"
                    docker compose -f "$DOCKER_DIR/docker-compose.yml" logs --tail=20 "$svc" 2>/dev/null \
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
    docker compose -f "$DOCKER_DIR/docker-compose.yml" ps 2>/dev/null | while read -r line; do
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

    # 版本同步校验：Flyway 文件数 vs 合并脚本头部列表
    local flyway_dir="$PROJECT_ROOT/backend/src/main/resources/db/migration"
    if [ -d "$flyway_dir" ]; then
        local flyway_count merged_count
        flyway_count=$(ls "$flyway_dir"/V*.sql 2>/dev/null | wc -l || echo 0)
        merged_count=$(grep -cE '^--   V[0-9]+__' "$migration_sql" 2>/dev/null || echo 0)
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
MAX_FETCH_ATTEMPTS=3
for attempt in $(seq 1 $MAX_FETCH_ATTEMPTS); do
    fetch_err=$(git fetch origin "$BRANCH" --quiet 2>&1) && { fetch_ok=true; break; }
    if [ "$attempt" -lt "$MAX_FETCH_ATTEMPTS" ]; then
        log "WARN: git fetch 失败 (尝试 $attempt/$MAX_FETCH_ATTEMPTS)，10s 后重试... 错误: $fetch_err"
        sleep 10
    else
        log "WARN: git fetch 失败 (尝试 $attempt/$MAX_FETCH_ATTEMPTS)，错误: $fetch_err"
    fi
done
if [ "$fetch_ok" = false ]; then
    log "WARN: git fetch 在 $MAX_FETCH_ATTEMPTS 次尝试后仍然失败，跳过本次更新"
    # 检测是否是 SSH 认证问题，给出修复建议
    if echo "$fetch_err" | grep -q "Permission denied (publickey)"; then
        log "HINT: SSH 认证失败。建议切换为 HTTPS 方式:"
        log "      git remote set-url origin https://<token>@github.com/<user>/<repo>.git"
        log "      或生成 SSH key: ssh-keygen -t ed25519 -C deploy && cat ~/.ssh/id_ed25519.pub"
    fi
    # 网络临时断开属于正常情况，exit 0 避免 cron 发送告警邮件
    exit 0
fi

LOCAL_HASH=$(git rev-parse HEAD)
REMOTE_HASH=$(git rev-parse "origin/$BRANCH")

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
        git checkout "$PRE_PULL_COMMIT" --force --quiet 2>/dev/null || true
        # 部署记录文件不更新，保持指向上次成功部署的 commit
        disable_maintenance
        notify "已回退到 ${PRE_PULL_COMMIT:0:8}，旧服务继续运行"
        ops_log "ERROR" "${PRE_PULL_COMMIT:0:8}" "构建失败，已回退代码，部署记录未更新"
        exit 1
    fi

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
    docker compose -f "$COMPOSE_FILE" up -d $SERVICES

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
        # disable_maintenance 在 rollback 内部或 trap cleanup 中处理
        exit 1
    fi
else
    log "INFO: 变更不涉及前后端代码（可能是文档/脚本/迁移），跳过重建"
    # 即使不重建，也要执行迁移（如仅合并 SQL 或 Flyway 文件有更新）
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
    # 无需重建，更新部署记录到最新 commit，直接关闭维护模式
    NEW_COMMIT=$(git rev-parse HEAD)
    echo "$NEW_COMMIT" > "$DEPLOY_RECORD_FILE"
    ops_log "SUCCESS" "$(git rev-parse --short HEAD)" "仅文档/脚本变更，跳过重建"
    disable_maintenance
fi

log "──── 更新完成 ────"
