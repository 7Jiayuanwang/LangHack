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
STASHED=false
OLD_BRANCH=""
OLD_COMMIT=""
CRON_TMP=""

# ── 信号处理 & 清理 ─────────────────────────────────
deploy_cleanup() {
    local exit_code=$?
    if [ "$STASHED" = true ]; then
        echo ""
        echo "[提示] 部署前保存了本地修改，恢复命令: git stash pop"
        echo "  查看: git stash list | head -1"
    fi
    if [ $exit_code -ne 0 ] && [ -n "$OLD_BRANCH" ]; then
        echo "[提示] 原始分支: $OLD_BRANCH"
    fi
    # 清理可能残留的 crontab 临时文件
    [ -n "$CRON_TMP" ] && rm -f "$CRON_TMP" 2>/dev/null || true
}
trap deploy_cleanup EXIT

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
        input=$(echo "${input:-$default}" | sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[\x00-\x08\x0b-\x1f\x7f]//g; s/\[\+[0-9;~]*//g')
        printf -v "$var_name" '%s' "$input"
    else
        read -rp "  ${prompt}: " input
        input=$(echo "$input" | sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[\x00-\x08\x0b-\x1f\x7f]//g; s/\[\+[0-9;~]*//g')
        printf -v "$var_name" '%s' "$input"
    fi
}

ask_secret() {
    local prompt="$1" default="$2" var_name="$3"
    local input masked=""
    if [ -n "$default" ]; then
        [ ${#default} -gt 6 ] && masked="${default:0:3}***${default: -3}" || masked="***"
        read -rp "  ${prompt} [${masked}]: " input
        input=$(echo "${input:-$default}" | sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[\x00-\x08\x0b-\x1f\x7f]//g; s/\[\+[0-9;~]*//g')
        printf -v "$var_name" '%s' "$input"
    else
        read -rp "  ${prompt}: " input
        input=$(echo "$input" | sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[\x00-\x08\x0b-\x1f\x7f]//g; s/\[\+[0-9;~]*//g')
        printf -v "$var_name" '%s' "$input"
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
            printf -v "$var_name" '%d' "$choice"; return
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
            y|yes) printf -v "$var_name" '%s' "true";  return ;;
            n|no)  printf -v "$var_name" '%s' "false"; return ;;
        esac
    done
}

load_env_value() {
    local key="$1" default="$2" file="${3:-$DOCKER_DIR/.env}"
    if [ -f "$file" ]; then
        local val; val=$(grep -E "^${key}=" "$file" 2>/dev/null | grep -v '^\s*#' | head -1 | cut -d'=' -f2- | sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[\x00-\x08\x0b-\x1f\x7f]//g; s/[\[\]]\+[0-9~]*//g')
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
    # 验证端口号范围
    if ! [[ "$port" =~ ^[0-9]+$ ]] || [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
        echo "  [ERROR] 无效端口号: $port (有效范围 1-65535)"
        return 1
    fi
    # 检查 Docker 容器端口占用（兼容 0.0.0.0 和 127.0.0.1 绑定）
    if docker ps --format '{{.Ports}}' 2>/dev/null | grep -qE ":${port}(->|/)"; then
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
    port="${!var_name}"
    if ! check_port "$port"; then
        echo "  [WARN] 端口 ${port} 已被占用，自动寻找可用端口..."
        local suggest=$((port + 1))
        local max_suggest=$((port + 100))
        [ "$max_suggest" -gt 65535 ] && max_suggest=65535
        while ! check_port "$suggest" && [ "$suggest" -le "$max_suggest" ]; do suggest=$((suggest + 1)); done
        if [ "$suggest" -gt "$max_suggest" ]; then
            echo "  [ERROR] 在 ${port}-${max_suggest} 范围内未找到可用端口"
            suggest="$port"
        else
            echo "  [建议] 可用端口: ${suggest}"
        fi
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
# 检查 Docker daemon 是否在运行
if ! docker info &>/dev/null; then
    echo "[ERROR] Docker daemon 未运行，请执行: sudo systemctl start docker"
    exit 1
fi

# ══════════════════════════════════════════════════════
# 步骤 1: 分支选择
# ══════════════════════════════════════════════════════
echo ""
echo "── 步骤 1/5: 选择部署分支 ──"

cd "$PROJECT_ROOT"
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "[ERROR] 当前目录不在 git 仓库中，无法继续"
    exit 1
fi
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
    echo "  [ AI 服务商 (可选，也可部署后在运维面板 → AI服务管理中配置多个服务商并设置负载均衡) ]"
    echo "  提示: 支持 OpenAI/OpenRouter/HiAgent 等兼容 OpenAI 格式的服务商"
    ask_secret "初始翻译服务 API Key (留空=稍后在面板配置)" "$(load_env_value "AI_TRANSLATE_API_KEY" "")" CFG_AI_TRANSLATE_KEY
    CFG_AI_TRANSLATE_URL=""
    CFG_AI_TRANSLATE_MODEL=""
    if [ -n "$CFG_AI_TRANSLATE_KEY" ]; then
        ask_value  "API URL" "$(load_env_value "AI_TRANSLATE_API_URL" "https://api.openai.com/v1/chat/completions")" CFG_AI_TRANSLATE_URL
        ask_value  "模型"    "$(load_env_value "AI_TRANSLATE_MODEL" "gpt-3.5-turbo")" CFG_AI_TRANSLATE_MODEL
    fi
    if [ -n "$CFG_AI_TRANSLATE_KEY" ]; then
        echo "  (可选) 配置更多服务商以启用负载均衡:"
        ask_secret "OpenRouter API Key (留空跳过)" "$(load_env_value "OPENROUTER_API_KEY" "")" CFG_OPENROUTER_KEY
        echo "  HiAgent 用于文章改写和 AI 问答（专有协议，Key 共享，App ID 可分开）"
        ask_secret "HiAgent API Key (留空跳过)"   "$(load_env_value "HIAGENT_API_KEY" "")"    CFG_HIAGENT_KEY
        if [ -n "$CFG_HIAGENT_KEY" ]; then
            ask_value  "HiAgent 文章改写 App ID" "$(load_env_value "HIAGENT_APP_ID" "")"    CFG_HIAGENT_APP_ID
            ask_value  "HiAgent AI问答 App ID (留空则与文章改写共用)" "$(load_env_value "HIAGENT_QA_APP_ID" "${CFG_HIAGENT_APP_ID}")" CFG_HIAGENT_QA_APP_ID
        else
            CFG_HIAGENT_APP_ID=""
            CFG_HIAGENT_QA_APP_ID=""
        fi
    else
        CFG_OPENROUTER_KEY=""
        CFG_HIAGENT_KEY=""
        CFG_HIAGENT_APP_ID=""
        CFG_HIAGENT_QA_APP_ID=""
    fi

    echo ""
    echo "  [ 端口映射 ]"
    ask_port "前端端口" "$(load_env_value "PORT_FRONTEND" "80")" CFG_PORT_FE
    ask_port "后端端口" "$(load_env_value "PORT_BACKEND" "8080")" CFG_PORT_BE
    ask_port "MySQL 端口" "$(load_env_value "PORT_MYSQL" "3306")" CFG_PORT_DB

    echo ""
    echo "  [ CORS 跨域 ]"
    DEF_CORS=$(load_env_value "APP_CORS_ALLOWED_ORIGINS" "")
    SERVER_IP_HINT=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "")
    CORS_DEFAULT="http://localhost,http://localhost:${CFG_PORT_FE}"
    [ -n "$SERVER_IP_HINT" ] && CORS_DEFAULT="${CORS_DEFAULT},http://${SERVER_IP_HINT}:${CFG_PORT_FE}"
    [ -n "$DEF_CORS" ] && CORS_DEFAULT="$DEF_CORS"
    echo "  允许前端访问后端的域名列表（逗号分隔）"
    echo "  本地开发: http://localhost:5173"
    echo "  生产环境: https://your-domain.com"
    ask_value "CORS 允许来源" "$CORS_DEFAULT" CFG_CORS_ORIGINS

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

    # 写入 .env（用 printf 逐行写入，避免 heredoc 展开密码/JWT 中的特殊字符）
    mkdir -p "$DOCKER_DIR"
    CFG_PORT_SLAVE=$((CFG_PORT_DB + 1))
    {
        printf '# LangHack Docker 环境配置 (由部署向导生成 %s)\n' "$(date '+%Y-%m-%d %H:%M:%S')"
        printf 'DB_USERNAME=%s\n'        "$CFG_DB_USERNAME"
        printf 'DB_PASSWORD=%s\n'        "$CFG_DB_PASSWORD"
        printf 'JWT_SECRET=%s\n'         "$CFG_JWT_SECRET"
        [ -n "$CFG_REDIS_PASSWORD" ]  && printf 'REDIS_PASSWORD=%s\n'      "$CFG_REDIS_PASSWORD"  || printf '# REDIS_PASSWORD=\n'
        [ -n "$CFG_AI_TRANSLATE_URL" ] && printf 'AI_TRANSLATE_API_URL=%s\n' "$CFG_AI_TRANSLATE_URL" || printf '# AI_TRANSLATE_API_URL=\n'
        [ -n "$CFG_AI_TRANSLATE_KEY" ] && printf 'AI_TRANSLATE_API_KEY=%s\n' "$CFG_AI_TRANSLATE_KEY" || printf '# AI_TRANSLATE_API_KEY=\n'
        [ -n "$CFG_AI_TRANSLATE_MODEL" ] && printf 'AI_TRANSLATE_MODEL=%s\n' "$CFG_AI_TRANSLATE_MODEL" || printf '# AI_TRANSLATE_MODEL=\n'
        [ -n "$CFG_OPENROUTER_KEY" ] && printf 'OPENROUTER_API_KEY=%s\n' "$CFG_OPENROUTER_KEY" || printf '# OPENROUTER_API_KEY=\n'
        [ -n "$CFG_HIAGENT_KEY" ]    && printf 'HIAGENT_API_KEY=%s\n'    "$CFG_HIAGENT_KEY"    || printf '# HIAGENT_API_KEY=\n'
        [ -n "$CFG_HIAGENT_APP_ID" ] && printf 'HIAGENT_APP_ID=%s\n'    "$CFG_HIAGENT_APP_ID" || printf '# HIAGENT_APP_ID=\n'
        [ -n "$CFG_HIAGENT_QA_APP_ID" ] && printf 'HIAGENT_QA_APP_ID=%s\n' "$CFG_HIAGENT_QA_APP_ID" || printf '# HIAGENT_QA_APP_ID=\n'
        printf 'MYSQL_ROOT_PASSWORD=%s\n'          "$CFG_DB_PASSWORD"
        printf 'MYSQL_DATABASE=english_learning\n'
        printf 'MYSQL_REPLICATION_USER=replicator\n'
        printf 'MYSQL_REPLICATION_PASSWORD=%s_repl\n' "$CFG_DB_PASSWORD"
        printf 'DB_URL=jdbc:mysql://mysql-master:3306/english_learning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%%2B8&allowPublicKeyRetrieval=true\n'
        printf 'DB_SLAVE_URL=jdbc:mysql://mysql-slave:3306/english_learning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%%2B8&allowPublicKeyRetrieval=true\n'
        printf 'DB_SLAVE_USERNAME=%s\n'  "$CFG_DB_USERNAME"
        printf 'DB_SLAVE_PASSWORD=%s\n'  "$CFG_DB_PASSWORD"
        printf 'REDIS_HOST=redis\n'
        printf 'APP_CORS_ALLOWED_ORIGINS=%s\n' "$CFG_CORS_ORIGINS"
        printf 'TUTOR_API_BASE_URL=http://localhost:8000/api/tutor\n'
        printf 'VITE_TUTOR_API_BASE_URL=/tutor\n'
        printf 'PORT_FRONTEND=%s\n'      "$CFG_PORT_FE"
        printf 'PORT_BACKEND=%s\n'       "$CFG_PORT_BE"
        printf 'PORT_MYSQL=%s\n'         "$CFG_PORT_DB"
        printf 'PORT_MYSQL_SLAVE=%s\n'   "$CFG_PORT_SLAVE"
        printf 'PORT_REDIS=6379\n'
        printf 'BACKUP_ENABLED=%s\n'     "$CFG_BACKUP_ENABLED"
        printf 'BACKUP_DIR=%s\n'         "$CFG_BACKUP_DIR"
        printf 'BACKUP_RETENTION_DAYS=%s\n' "$CFG_BACKUP_RETENTION"
        printf 'AUTO_UPDATE_ENABLED=%s\n'     "$CFG_AUTO_UPDATE_ENABLED"
        printf 'AUTO_UPDATE_CRON=%s\n'        "$CFG_AUTO_UPDATE_CRON"
        printf 'AUTO_UPDATE_BRANCH=%s\n'      "$CFG_AUTO_UPDATE_BRANCH"
        printf 'AUTO_UPDATE_MAINTENANCE=%s\n' "$CFG_AUTO_UPDATE_MAINTENANCE"
        printf 'AUTO_UPDATE_PRE_BACKUP=%s\n'  "$CFG_AUTO_UPDATE_PRE_BACKUP"
    } > "$ENV_FILE"
    # 设置 .env 文件权限为 600（仅所有者可读写，保护敏感信息）
    chmod 600 "$ENV_FILE" 2>/dev/null || true
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
if [ -n "${CFG_CORS_ORIGINS:-}" ]; then
    echo "  CORS: $CFG_CORS_ORIGINS"
fi
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
OLD_BRANCH="$CURRENT"
OLD_COMMIT=$(git rev-parse HEAD 2>/dev/null || echo "")
if [ "$TARGET_BRANCH" != "$CURRENT" ]; then
    echo "[INFO] 切换分支..."
    # 检查 unstaged 和 staged 变更
    if ! git diff --quiet 2>/dev/null || ! git diff --cached --quiet 2>/dev/null; then
        git stash push -m "deploy-$(date +%s)" --quiet
        STASHED=true
    fi
    if ! git checkout "$TARGET_BRANCH" --quiet 2>/dev/null; then
        echo "[ERROR] 无法切换到分支 $TARGET_BRANCH，请检查分支是否存在"
        exit 1
    fi
    git pull origin "$TARGET_BRANCH" --ff-only --quiet 2>/dev/null || \
        echo "[WARN] 无法从远程拉取 $TARGET_BRANCH，使用本地版本"
fi

echo "[INFO] 版本: $(git rev-parse --short HEAD)"

cd "$DOCKER_DIR"

# 服务列表 & 预期健康服务数
# 注意: mysql-slave 不在 backend/frontend 的 depends_on 链中，不会被自动启动
case $SVC_MODE in
    1) SVCS="";                              expected_healthy=5 ;;
    2) SVCS="backend frontend";              expected_healthy=4 ;;  # backend + frontend + mysql-master + redis
    3) SVCS="backend";                       expected_healthy=3 ;;  # backend + mysql-master + redis
    4) SVCS="frontend";                      expected_healthy=4 ;;  # frontend + backend + mysql-master + redis (整条依赖链)
    5) SVCS="mysql-master mysql-slave redis"; expected_healthy=3 ;;
esac

# 构建
BUILD_ONLY=false
case $BUILD_MODE in
    1)
        echo "[INFO] 全量构建..."
        if [ -n "$SVCS" ]; then
            docker compose build --no-cache $SVCS
        else
            docker compose build --no-cache
        fi
        ;;
    2)
        echo "[INFO] 增量构建..."
        if [ -n "$SVCS" ]; then
            docker compose build $SVCS
        else
            docker compose build
        fi
        ;;
    3) echo "[INFO] 跳过构建（仅重启）"; BUILD_ONLY=true ;;
esac

# 启动
if [ "$BUILD_ONLY" = true ]; then
    echo "[INFO] 重启服务..."
    if [ -n "$SVCS" ]; then
        docker compose up -d --force-recreate $SVCS
    else
        docker compose up -d --force-recreate
    fi
else
    echo "[INFO] 启动服务..."
    if [ -n "$SVCS" ]; then
        docker compose up -d $SVCS
    else
        docker compose up -d
    fi
fi

# 健康等待（快速失败：发现 unhealthy 立即报告）
echo "[INFO] 等待服务就绪 (预期 ${expected_healthy} 个 healthy)..."
timeout=180; elapsed=0; deploy_ok=false
while [ $elapsed -lt $timeout ]; do
    # 用 --format 获取结构化状态，避免不同 compose 版本输出格式差异
    statuses=$(docker compose ps --format '{{.Status}}' 2>/dev/null || true)
    if [ -z "$statuses" ]; then
        sleep 5; elapsed=$((elapsed + 5)); continue
    fi
    unhealthy=$(echo "$statuses" | grep -c "unhealthy" || true)
    healthy=$(echo   "$statuses" | grep -v "unhealthy" | grep -c "healthy" || true)
    if [ "$unhealthy" -gt 0 ]; then
        echo ""
        echo "[ERROR] 有服务启动失败（unhealthy），终止等待："
        docker compose ps
        echo ""
        echo "─── 失败服务日志 ───"
        docker compose ps --format '{{.Service}} {{.Status}}' 2>/dev/null \
          | grep unhealthy | awk '{print $1}' \
          | while read -r svc; do
              echo ">>> $svc"
              docker compose logs --tail=30 "$svc" 2>/dev/null
            done
        break
    fi
    [ "$healthy" -ge "$expected_healthy" ] && deploy_ok=true && break
    sleep 5; elapsed=$((elapsed + 5))
    printf "  等待中... %ds (healthy: %d/%d)\r" "$elapsed" "$healthy" "$expected_healthy"
done
echo ""

echo ""
echo "======================================"
docker compose ps
echo "======================================"
echo ""
if [ "$deploy_ok" = false ]; then
    echo "[ERROR] 部署失败：服务未能在 ${timeout}s 内全部就绪"
    echo ""
    # 提供回滚选项
    if [ -n "$OLD_COMMIT" ]; then
        ask_yn "是否回滚到上一个版本 (${OLD_COMMIT:0:8})？" "y" DO_ROLLBACK
        if [ "$DO_ROLLBACK" = true ]; then
            echo "[INFO] 正在回滚到 ${OLD_COMMIT:0:8}..."
            cd "$PROJECT_ROOT"
            if git checkout "$OLD_COMMIT" --force --quiet 2>/dev/null; then
                cd "$DOCKER_DIR"
                case $BUILD_MODE in
                    1|2)
                        echo "[INFO] 重新构建旧版本..."
                        if [ -n "$SVCS" ]; then
                            docker compose build $SVCS
                        else
                            docker compose build
                        fi
                        ;;
                esac
                if [ -n "$SVCS" ]; then
                    docker compose up -d $SVCS
                else
                    docker compose up -d
                fi
                echo "[INFO] 已回滚到 ${OLD_COMMIT:0:8}，请等待服务重新就绪"
            else
                echo "[ERROR] git 回滚失败，请手动处理: git checkout ${OLD_COMMIT:0:8}"
            fi
        fi
    fi
    exit 1
fi
echo "[SUCCESS] 部署完成！"
echo "  分支: $TARGET_BRANCH"
echo "  版本: $(cd "$PROJECT_ROOT" && git rev-parse --short HEAD)"
SERVER_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "localhost")
echo "  前端:        http://${SERVER_IP}:${PORT_FRONTEND}"
echo "  后端:        http://${SERVER_IP}:${PORT_BACKEND}"
echo "  AI服务管理:  http://${SERVER_IP}:${PORT_FRONTEND}/#/ops/ai-providers"

# ── AI 密钥种子化：应用启动时 @PostConstruct 自动完成，无需外部触发 ──
echo "[INFO] AI 服务商密钥将在后端启动时自动从环境变量种子化到数据库"
echo "       如需手动管理，请访问: http://${SERVER_IP}:${PORT_FRONTEND}/#/ops/ai-providers"

# ── 设置自动更新 crontab ────────────────────────────
if [ "${CFG_AUTO_UPDATE_ENABLED:-$(load_env_value "AUTO_UPDATE_ENABLED" "false" "$ENV_FILE")}" = "true" ]; then
    AU_CRON="${CFG_AUTO_UPDATE_CRON:-$(load_env_value "AUTO_UPDATE_CRON" "0 * * * *" "$ENV_FILE")}"
    AU_BRANCH="${CFG_AUTO_UPDATE_BRANCH:-$(load_env_value "AUTO_UPDATE_BRANCH" "$TARGET_BRANCH" "$ENV_FILE")}"
    SCRIPT_PATH="$PROJECT_ROOT/scripts/auto-update.sh"
    LOG_PATH="/var/log/langhack-update.log"
    CRON_CMD="$AU_CRON $SCRIPT_PATH -b $AU_BRANCH >> $LOG_PATH 2>&1"

    # 原子写法：先写临时文件，再整体替换，避免管道中断导致 crontab 被清空
    CRON_TMP=$(mktemp /tmp/langhack-deploy-cron.XXXXXX)
    crontab -l 2>/dev/null | grep -v "auto-update.sh" > "$CRON_TMP" || true
    echo "$CRON_CMD" >> "$CRON_TMP"
    if crontab "$CRON_TMP" 2>/dev/null; then
        echo "[OK] 自动更新已配置 (crontab: $AU_CRON)"
    else
        echo "[WARN] crontab 设置失败，请手动添加: $CRON_CMD"
    fi
    rm -f "$CRON_TMP"
fi

echo ""
echo "[重要] 请备份 .env 文件到安全位置！如更换服务器需一并迁移。"
echo "[警告] 请勿执行 'docker compose down -v'，该命令会删除所有数据卷！"
echo "  安全停止: docker compose down"
echo "  安全重启: docker compose restart"
