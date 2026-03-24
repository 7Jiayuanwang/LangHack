#!/usr/bin/env bash
# ==============================================================================
# LangHack Docker Compose 交互式部署脚本
# 面向 docker/ 目录结构的完整部署向导
#
# 用法:
#   ./scripts/deploy.sh              # 启动交互式向导
#   ./scripts/deploy.sh --quick      # 快速模式（跳过已配置项）
#   ./scripts/deploy.sh --help       # 帮助
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DOCKER_DIR="$PROJECT_ROOT/docker"

QUICK_MODE=false

# ── 参数解析 ──────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --quick|-q) QUICK_MODE=true; shift ;;
        --help|-h)
            echo "LangHack Docker Compose 交互式部署"
            echo ""
            echo "用法: $0 [选项]"
            echo "  --quick, -q   快速模式（跳过已配置项）"
            echo "  --help, -h    显示帮助"
            exit 0
            ;;
        *) echo "[ERROR] 未知参数: $1"; exit 1 ;;
    esac
done

# ── 工具函数 ──────────────────────────────────────────
ask_value() {
    local prompt="$1" default="$2" var_name="$3"
    local input
    if [ -n "$default" ]; then
        read -rp "  ${prompt} [${default}]: " input
        eval "$var_name=\"${input:-$default}\""
    else
        read -rp "  ${prompt}: " input
        eval "$var_name=\"$input\""
    fi
}

ask_secret() {
    local prompt="$1" default="$2" var_name="$3"
    local input masked=""
    if [ -n "$default" ]; then
        [ ${#default} -gt 6 ] && masked="${default:0:3}***${default: -3}" || masked="***"
        read -rp "  ${prompt} [${masked}]: " input
        eval "$var_name=\"${input:-$default}\""
    else
        read -rp "  ${prompt}: " input
        eval "$var_name=\"$input\""
    fi
}

ask_choice() {
    local question="$1" var_name="$3" default_idx="${4:-1}"
    IFS='|' read -ra options <<< "$2"
    echo "  ${question}"
    for i in "${!options[@]}"; do
        local rec=""; [ $((i + 1)) -eq "$default_idx" ] && rec=" <- 推荐"
        echo "    $((i + 1))) ${options[$i]}${rec}"
    done
    while true; do
        read -rp "  选择 [${default_idx}]: " choice
        choice="${choice:-$default_idx}"
        if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le ${#options[@]} ]; then
            eval "$var_name=$choice"; return
        fi
        echo "  [ERROR] 请输入 1-${#options[@]}"
    done
}

ask_yn() {
    local question="$1" default="$2" var_name="$3"
    local hint; [ "$default" = "y" ] && hint="Y/n" || hint="y/N"
    while true; do
        read -rp "  ${question} [${hint}]: " answer
        answer="${answer:-$default}"
        case "${answer,,}" in
            y|yes) eval "$var_name=true"; return ;;
            n|no)  eval "$var_name=false"; return ;;
        esac
    done
}

load_env_value() {
    local key="$1" default="$2" file="${3:-$DOCKER_DIR/.env}"
    if [ -f "$file" ]; then
        local val; val=$(grep -E "^${key}=" "$file" 2>/dev/null | head -1 | cut -d'=' -f2-)
        [ -n "$val" ] && echo "$val" && return
    fi
    echo "$default"
}

is_placeholder() {
    [[ "$1" == *"your"* ]] || [[ "$1" == *"here"* ]] || [[ "$1" == *"change_this"* ]] || [ -z "$1" ]
}

# 检查端口是否可用（未被宿主机或 Docker 容器占用）
check_port() {
    local port="$1"
    # 检查 Docker 容器端口占用
    if docker ps --format '{{.Ports}}' 2>/dev/null | grep -q "0\.0\.0\.0:${port}->"; then
        return 1
    fi
    # 检查宿主机端口占用
    if command -v ss &>/dev/null; then
        ss -tlnp 2>/dev/null | grep -q ":${port}[[:space:]]" && return 1
    elif command -v netstat &>/dev/null; then
        netstat -tln 2>/dev/null | grep -q ":${port}[[:space:]]" && return 1
    fi
    return 0
}

# 端口选择（自动跳过占用端口）
ask_port() {
    local prompt="$1" default="$2" var_name="$3"
    local port="$default"
    ask_value "$prompt" "$port" "$var_name"
    eval "port=\$$var_name"
    if ! check_port "$port"; then
        echo "  [WARN] 端口 ${port} 已被占用，自动寻找可用端口..."
        local suggest=$((port + 1))
        while ! check_port "$suggest"; do suggest=$((suggest + 1)); done
        echo "  [建议] 可用端口: ${suggest}"
        ask_value "$prompt (换一个)" "$suggest" "$var_name"
    fi
}

# ══════════════════════════════════════════════════════
echo "======================================"
echo " LangHack Docker Compose 部署向导 v2.0"
echo "======================================"

# ── 前置检查 ──────────────────────────────────────────
command -v docker &>/dev/null || { echo "[ERROR] Docker 未安装"; exit 1; }
docker compose version &>/dev/null || { echo "[ERROR] Docker Compose V2 未安装"; exit 1; }

# ══════════════════════════════════════════════════════
# 步骤 1: 分支选择
# ══════════════════════════════════════════════════════
echo ""
echo "── 步骤 1/5: 选择部署分支 ──"

cd "$PROJECT_ROOT"
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
echo "  当前分支: $CURRENT_BRANCH"

git fetch --all --quiet 2>/dev/null || true
mapfile -t BRANCH_LIST < <({
    git branch -r --no-color 2>/dev/null | grep -v 'HEAD' | sed 's|origin/||;s|^[[:space:]]*||'
    git branch --no-color 2>/dev/null | sed 's|^\*||;s|^[[:space:]]*||'
} | sort -u)

[ ${#BRANCH_LIST[@]} -eq 0 ] && BRANCH_LIST=("$CURRENT_BRANCH")

default_idx=1
for i in "${!BRANCH_LIST[@]}"; do
    marker=""; [ "${BRANCH_LIST[$i]}" = "$CURRENT_BRANCH" ] && marker=" <- 当前" && default_idx=$((i + 1))
    printf "    %2d) %s%s\n" $((i + 1)) "${BRANCH_LIST[$i]}" "$marker"
done

while true; do
    read -rp "  选择分支 [${default_idx}]: " bc
    bc="${bc:-$default_idx}"
    if [[ "$bc" =~ ^[0-9]+$ ]] && [ "$bc" -ge 1 ] && [ "$bc" -le ${#BRANCH_LIST[@]} ]; then
        TARGET_BRANCH="${BRANCH_LIST[$((bc - 1))]}"; break
    fi
done
echo "  [OK] 分支: $TARGET_BRANCH"

# ══════════════════════════════════════════════════════
# 步骤 2: 环境变量配置
# ══════════════════════════════════════════════════════
echo ""
echo "── 步骤 2/5: 配置环境变量 ──"

ENV_FILE="$DOCKER_DIR/.env"
SKIP_ENV=false

if [ -f "$ENV_FILE" ] && [ "$QUICK_MODE" = true ]; then
    ask_yn "检测到已有配置，是否修改？" "n" EDIT_ENV
    [ "$EDIT_ENV" = false ] && SKIP_ENV=true
fi

if [ "$SKIP_ENV" = false ]; then
    echo ""
    echo "  [ 数据库 ]"
    DEF_DBPW=$(load_env_value "DB_PASSWORD" "")
    DEF_DBUSER=$(load_env_value "DB_USERNAME" "root")
    ask_value "MySQL 用户名" "$DEF_DBUSER" CFG_DB_USERNAME
    if is_placeholder "$DEF_DBPW"; then
        ask_secret "MySQL 密码 (必填)" "" CFG_DB_PASSWORD
        while [ -z "$CFG_DB_PASSWORD" ]; do
            echo "  [ERROR] 密码不能为空"
            ask_secret "MySQL 密码 (必填)" "" CFG_DB_PASSWORD
        done
    else
        ask_secret "MySQL 密码" "$DEF_DBPW" CFG_DB_PASSWORD
    fi

    echo ""
    echo "  [ JWT ]"
    DEF_JWT=$(load_env_value "JWT_SECRET" "")
    if is_placeholder "$DEF_JWT"; then
        CFG_JWT_SECRET=$(openssl rand -base64 48 2>/dev/null || echo "AUTO_$(date +%s)_CHANGE_ME_32CHARS")
        echo "  JWT 密钥已自动生成"
    else
        ask_yn "重新生成 JWT 密钥？" "n" REGEN
        if [ "$REGEN" = true ]; then
            CFG_JWT_SECRET=$(openssl rand -base64 48 2>/dev/null || echo "AUTO_$(date +%s)")
        else
            CFG_JWT_SECRET="$DEF_JWT"
        fi
    fi

    echo ""
    echo "  [ Redis ]"
    ask_value "Redis 密码 (可留空)" "$(load_env_value "REDIS_PASSWORD" "")" CFG_REDIS_PASSWORD

    echo ""
    echo "  [ AI 服务 ]"
    ask_secret "OpenRouter API Key" "$(load_env_value "OPENROUTER_API_KEY" "")" CFG_OPENROUTER_KEY
    ask_secret "HiAgent API Key (可选)" "$(load_env_value "HIAGENT_API_KEY" "")" CFG_HIAGENT_KEY

    echo ""
    echo "  [ 腾讯云 ]"
    ask_secret "SecretId (可选)" "$(load_env_value "TENCENT_SECRET_ID" "")" CFG_TENCENT_ID
    ask_secret "SecretKey (可选)" "$(load_env_value "TENCENT_SECRET_KEY" "")" CFG_TENCENT_KEY

    echo ""
    echo "  [ 端口映射 ]"
    ask_port "前端端口" "$(load_env_value "PORT_FRONTEND" "80")" CFG_PORT_FE
    ask_port "后端端口" "$(load_env_value "PORT_BACKEND" "8080")" CFG_PORT_BE
    ask_port "MySQL 端口" "$(load_env_value "PORT_MYSQL" "3306")" CFG_PORT_DB

    echo ""
    echo "  [ 备份 ]"
    ask_yn "启用自动备份？" "y" CFG_BACKUP_YN
    CFG_BACKUP_ENABLED="false"
    CFG_BACKUP_DIR="./backups"
    CFG_BACKUP_RETENTION="30"
    if [ "$CFG_BACKUP_YN" = true ]; then
        CFG_BACKUP_ENABLED="true"
        ask_value "备份目录" "$(load_env_value "BACKUP_DIR" "./backups")" CFG_BACKUP_DIR
        ask_value "保留天数" "$(load_env_value "BACKUP_RETENTION_DAYS" "30")" CFG_BACKUP_RETENTION
    fi

    echo ""
    echo "  [ 自动更新 ]"
    echo "  (定期从 GitHub 拉取代码并自动重建部署)"
    ask_yn "启用定期自动更新？" "n" CFG_AUTO_UPDATE_YN
    if [ "$CFG_AUTO_UPDATE_YN" = true ]; then
        CFG_AUTO_UPDATE_ENABLED="true"
        ask_choice "更新检查间隔:" "每 5 分钟|每 10 分钟|每 30 分钟|每小时" AU_FREQ 4
        case $AU_FREQ in
            1) CFG_AUTO_UPDATE_CRON="*/5 * * * *" ;;
            2) CFG_AUTO_UPDATE_CRON="*/10 * * * *" ;;
            3) CFG_AUTO_UPDATE_CRON="*/30 * * * *" ;;
            4) CFG_AUTO_UPDATE_CRON="0 * * * *" ;;
        esac
        ask_value "跟踪分支" "$TARGET_BRANCH" CFG_AUTO_UPDATE_BRANCH
        ask_yn "更新前自动备份数据库？" "y" AU_BACKUP_YN
        CFG_AUTO_UPDATE_PRE_BACKUP="true"
        [ "$AU_BACKUP_YN" = false ] && CFG_AUTO_UPDATE_PRE_BACKUP="false"
        ask_yn "更新时启用维护模式？" "y" AU_MAINT_YN
        CFG_AUTO_UPDATE_MAINTENANCE="true"
        [ "$AU_MAINT_YN" = false ] && CFG_AUTO_UPDATE_MAINTENANCE="false"
    else
        CFG_AUTO_UPDATE_ENABLED="false"
        CFG_AUTO_UPDATE_CRON="0 * * * *"
        CFG_AUTO_UPDATE_BRANCH="$TARGET_BRANCH"
        CFG_AUTO_UPDATE_MAINTENANCE="true"
        CFG_AUTO_UPDATE_PRE_BACKUP="true"
    fi

    # 写入 .env
    mkdir -p "$DOCKER_DIR"
    cat > "$ENV_FILE" <<ENVEOF
# LangHack Docker 环境配置 (由部署向导生成 $(date '+%Y-%m-%d %H:%M:%S'))
DB_USERNAME=${CFG_DB_USERNAME}
DB_PASSWORD=${CFG_DB_PASSWORD}
JWT_SECRET=${CFG_JWT_SECRET}
$([ -n "$CFG_REDIS_PASSWORD" ] && echo "REDIS_PASSWORD=${CFG_REDIS_PASSWORD}" || echo "# REDIS_PASSWORD=")
$([ -n "$CFG_OPENROUTER_KEY" ] && echo "OPENROUTER_API_KEY=${CFG_OPENROUTER_KEY}" || echo "# OPENROUTER_API_KEY=")
$([ -n "$CFG_HIAGENT_KEY" ] && echo "HIAGENT_API_KEY=${CFG_HIAGENT_KEY}" || echo "# HIAGENT_API_KEY=")
$([ -n "$CFG_TENCENT_ID" ] && echo "TENCENT_SECRET_ID=${CFG_TENCENT_ID}" || echo "# TENCENT_SECRET_ID=")
$([ -n "$CFG_TENCENT_KEY" ] && echo "TENCENT_SECRET_KEY=${CFG_TENCENT_KEY}" || echo "# TENCENT_SECRET_KEY=")
MYSQL_ROOT_PASSWORD=${CFG_DB_PASSWORD}
MYSQL_DATABASE=english_learning
MYSQL_REPLICATION_USER=replicator
MYSQL_REPLICATION_PASSWORD=${CFG_DB_PASSWORD}_repl
DB_URL=jdbc:mysql://mysql-master:3306/english_learning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true
DB_SLAVE_URL=jdbc:mysql://mysql-slave:3306/english_learning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true
DB_SLAVE_USERNAME=${CFG_DB_USERNAME}
DB_SLAVE_PASSWORD=${CFG_DB_PASSWORD}
REDIS_HOST=redis
APP_CORS_ALLOWED_ORIGINS=http://localhost,http://localhost:${CFG_PORT_FE}
TUTOR_API_BASE_URL=http://localhost:8000/api/tutor
VITE_TUTOR_API_BASE_URL=/tutor
PORT_FRONTEND=${CFG_PORT_FE}
PORT_BACKEND=${CFG_PORT_BE}
PORT_MYSQL=${CFG_PORT_DB}
PORT_MYSQL_SLAVE=$((CFG_PORT_DB + 1))
PORT_REDIS=6379
BACKUP_ENABLED=${CFG_BACKUP_ENABLED}
BACKUP_DIR=${CFG_BACKUP_DIR}
BACKUP_RETENTION_DAYS=${CFG_BACKUP_RETENTION}
AUTO_UPDATE_ENABLED=${CFG_AUTO_UPDATE_ENABLED}
AUTO_UPDATE_CRON=${CFG_AUTO_UPDATE_CRON}
AUTO_UPDATE_BRANCH=${CFG_AUTO_UPDATE_BRANCH}
AUTO_UPDATE_MAINTENANCE=${CFG_AUTO_UPDATE_MAINTENANCE}
AUTO_UPDATE_PRE_BACKUP=${CFG_AUTO_UPDATE_PRE_BACKUP}
ENVEOF
    echo "  [OK] 配置已写入 $ENV_FILE"
fi

# ── 验证必填项（不直接 source，避免 JDBC URL 中的 & 触发后台进程）──────────
for var in DB_PASSWORD JWT_SECRET; do
    val=$(load_env_value "$var" "" "$ENV_FILE")
    if is_placeholder "$val"; then
        echo "[ERROR] $var 未正确配置"
        exit 1
    fi
done
# 为后续步骤读取端口变量（安全读取，不 eval）
PORT_FRONTEND=$(load_env_value "PORT_FRONTEND" "80"  "$ENV_FILE")
PORT_BACKEND=$(load_env_value  "PORT_BACKEND"  "8080" "$ENV_FILE")
echo "  [OK] 环境变量检查通过"

# ══════════════════════════════════════════════════════
# 步骤 3: 构建选项
# ══════════════════════════════════════════════════════
echo ""
echo "── 步骤 3/5: 构建选项 ──"

ask_choice "构建方式:" "全量重建 (--no-cache)|增量构建 (使用缓存)|仅重启 (不构建)" BUILD_MODE 2
ask_choice "部署服务:" "全部|仅后端+前端 (backend+frontend)|仅后端 (backend)|仅前端 (frontend)|仅基础设施 (mysql+redis)" SVC_MODE 1

# ══════════════════════════════════════════════════════
# 步骤 4: 确认
# ══════════════════════════════════════════════════════
echo ""
echo "── 步骤 4/5: 确认部署 ──"
echo "  分支: $TARGET_BRANCH"
case $BUILD_MODE in
    1) echo "  构建: 全量重建" ;;
    2) echo "  构建: 增量构建" ;;
    3) echo "  构建: 仅重启" ;;
esac
case $SVC_MODE in
    1) echo "  服务: 全部" ;;
    2) echo "  服务: 后端+前端" ;;
    3) echo "  服务: 仅后端" ;;
    4) echo "  服务: 仅前端" ;;
    5) echo "  服务: 仅基础设施" ;;
esac
if [ "${CFG_AUTO_UPDATE_ENABLED:-false}" = "true" ]; then
    echo "  自动更新: 已启用 (${CFG_AUTO_UPDATE_CRON}) → ${CFG_AUTO_UPDATE_BRANCH}"
else
    echo "  自动更新: 未启用"
fi

ask_yn "开始部署？" "y" GO
[ "$GO" = false ] && echo "已取消" && exit 0

# ══════════════════════════════════════════════════════
# 步骤 5: 执行
# ══════════════════════════════════════════════════════
echo ""
echo "── 步骤 5/5: 执行部署 ──"

# 切换分支
CURRENT=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
if [ "$TARGET_BRANCH" != "$CURRENT" ]; then
    echo "[INFO] 切换分支..."
    git diff --quiet 2>/dev/null || git stash push -m "deploy-$(date +%s)" --quiet
    git checkout "$TARGET_BRANCH" --quiet
    git pull origin "$TARGET_BRANCH" --ff-only --quiet 2>/dev/null || true
fi

echo "[INFO] 版本: $(git rev-parse --short HEAD)"

cd "$DOCKER_DIR"

# 服务列表
case $SVC_MODE in
    1) SVCS="" ;;
    2) SVCS="backend frontend" ;;
    3) SVCS="backend" ;;
    4) SVCS="frontend" ;;
    5) SVCS="mysql-master mysql-slave redis" ;;
esac

# 构建
case $BUILD_MODE in
    1)
        echo "[INFO] 全量构建..."
        [ -n "$SVCS" ] && docker compose build --no-cache $SVCS || docker compose build --no-cache
        ;;
    2)
        echo "[INFO] 增量构建..."
        [ -n "$SVCS" ] && docker compose build $SVCS || docker compose build
        ;;
    3) echo "[INFO] 跳过构建" ;;
esac

# 启动
echo "[INFO] 启动服务..."
[ -n "$SVCS" ] && docker compose up -d $SVCS || docker compose up -d

# 健康等待（快速失败：发现 unhealthy 立即报告）
echo "[INFO] 等待服务就绪..."
timeout=180; elapsed=0; deploy_ok=false
while [ $elapsed -lt $timeout ]; do
    ps_out=$(docker compose ps 2>/dev/null)
    unhealthy=$(echo "$ps_out" | grep -c "unhealthy" || true)
    healthy=$(echo   "$ps_out" | grep -c "(healthy)"  || true)
    if [ "$unhealthy" -gt 0 ]; then
        echo ""
        echo "[ERROR] 有服务启动失败（unhealthy），终止等待："
        echo "$ps_out"
        echo ""
        echo "─── 失败服务日志 ───"
        docker compose ps --format '{{.Name}} {{.Status}}' 2>/dev/null \
          | grep unhealthy | awk '{print $1}' \
          | while read -r svc; do
              echo ">>> $svc"
              docker compose logs --tail=30 "$svc" 2>/dev/null
            done
        break
    fi
    [ "$healthy" -ge 5 ] && deploy_ok=true && break
    sleep 5; elapsed=$((elapsed + 5))
    printf "  等待中... %ds (healthy: %d/5)\r" "$elapsed" "$healthy"
done
echo ""

echo ""
echo "======================================"
docker compose ps
echo "======================================"
echo ""
if [ "$deploy_ok" = false ]; then
    echo "[ERROR] 部署失败：服务未能在 ${timeout}s 内全部就绪，请检查上方日志"
    exit 1
fi
echo "[SUCCESS] 部署完成！"
echo "  分支: $TARGET_BRANCH"
echo "  版本: $(cd "$PROJECT_ROOT" && git rev-parse --short HEAD)"
SERVER_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "localhost")
echo "  前端: http://${SERVER_IP}:${PORT_FRONTEND}"
echo "  后端: http://${SERVER_IP}:${PORT_BACKEND}"

# ── 设置自动更新 crontab ────────────────────────────
if [ "${CFG_AUTO_UPDATE_ENABLED:-}" = "true" ] || [ "$(load_env_value "AUTO_UPDATE_ENABLED" "false" "$ENV_FILE")" = "true" ]; then
    AU_CRON="${CFG_AUTO_UPDATE_CRON:-$(load_env_value "AUTO_UPDATE_CRON" "0 * * * *" "$ENV_FILE")}"
    AU_BRANCH="${CFG_AUTO_UPDATE_BRANCH:-$(load_env_value "AUTO_UPDATE_BRANCH" "$TARGET_BRANCH" "$ENV_FILE")}"
    SCRIPT_PATH="$PROJECT_ROOT/scripts/auto-update.sh"
    LOG_PATH="/var/log/langhack-update.log"
    CRON_CMD="$AU_CRON $SCRIPT_PATH -b $AU_BRANCH >> $LOG_PATH 2>&1"

    ( crontab -l 2>/dev/null | grep -v "auto-update.sh" || true; echo "$CRON_CMD" ) | crontab - 2>/dev/null && \
        echo "[OK] 自动更新已配置 (crontab: $AU_CRON)" || \
        echo "[WARN] crontab 设置失败，请手动添加: $CRON_CMD"
fi

echo ""
echo "[重要] 请备份 .env 文件到安全位置！如更换服务器需一并迁移。"
