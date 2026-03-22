#!/bin/bash

# ==============================================================================
# LangHack 交互式部署向导
# 所有参数通过交互式菜单配置，零手动编辑文件
#
# 用法:
#   ./deploy.sh              # 启动交互式向导
#   ./deploy.sh --quick      # 快速模式（跳过已配置项）
#   ./deploy.sh --help       # 帮助
# ==============================================================================

set -uo pipefail

# ── 颜色和样式 ────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m'

QUICK_MODE=false

# ── 参数解析 ──────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --quick|-q)
            QUICK_MODE=true
            shift
            ;;
        --help|-h)
            echo "LangHack 交互式部署向导"
            echo ""
            echo "用法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "  --quick, -q   快速模式（已配置的项直接跳过）"
            echo "  --help, -h    显示帮助"
            exit 0
            ;;
        *)
            echo -e "${RED}未知参数: $1${NC}"
            exit 1
            ;;
    esac
done

# ── 工具函数 ──────────────────────────────────────────

# 打印步骤标题
step_header() {
    local step="$1" total="$2" title="$3"
    echo ""
    echo -e "${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}${BOLD}  步骤 ${step}/${total}: ${title}${NC}"
    echo -e "${CYAN}${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# 打印分组标题
group_header() {
    echo ""
    echo -e "  ${BOLD}[ $1 ]${NC}"
}

# 读取用户输入（带默认值）
# 用法: ask_value "提示" "默认值" result_var
ask_value() {
    local prompt="$1" default="$2" var_name="$3"
    local input
    if [ -n "$default" ]; then
        read -rp "$(echo -e "  ${prompt} ${DIM}[${default}]${NC}: ")" input
        eval "$var_name=\"${input:-$default}\""
    else
        read -rp "$(echo -e "  ${prompt}: ")" input
        eval "$var_name=\"$input\""
    fi
}

# 读取密码（隐藏输入）
ask_secret() {
    local prompt="$1" default="$2" var_name="$3"
    local input
    if [ -n "$default" ]; then
        local masked
        if [ ${#default} -gt 6 ]; then
            masked="${default:0:3}***${default: -3}"
        else
            masked="***"
        fi
        read -rp "$(echo -e "  ${prompt} ${DIM}[${masked}]${NC}: ")" input
        eval "$var_name=\"${input:-$default}\""
    else
        read -rp "$(echo -e "  ${prompt}: ")" input
        eval "$var_name=\"$input\""
    fi
}

# 单选菜单
# 用法: ask_choice "问题" "选项1|选项2|选项3" result_var [default_index]
ask_choice() {
    local question="$1" var_name="$3" default_idx="${4:-1}"
    IFS='|' read -ra options <<< "$2"

    echo -e "  ${question}"
    for i in "${!options[@]}"; do
        local marker=""
        if [ $((i + 1)) -eq "$default_idx" ]; then
            marker=" ${GREEN}← 推荐${NC}"
        fi
        echo -e "    ${CYAN}$((i + 1)))${NC} ${options[$i]}${marker}"
    done

    while true; do
        read -rp "$(echo -e "  选择 ${DIM}[${default_idx}]${NC}: ")" choice
        choice="${choice:-$default_idx}"
        if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le ${#options[@]} ]; then
            eval "$var_name=$choice"
            return
        fi
        echo -e "  ${RED}请输入 1-${#options[@]}${NC}"
    done
}

# 是/否选择
ask_yn() {
    local question="$1" default="$2" var_name="$3"
    local hint
    if [ "$default" = "y" ]; then hint="Y/n"; else hint="y/N"; fi
    while true; do
        read -rp "$(echo -e "  ${question} ${DIM}[${hint}]${NC}: ")" answer
        answer="${answer:-$default}"
        case "${answer,,}" in
            y|yes) eval "$var_name=true"; return ;;
            n|no)  eval "$var_name=false"; return ;;
            *) echo -e "  ${RED}请输入 y 或 n${NC}" ;;
        esac
    done
}

# 加载已有 .env 文件中的值
load_env_value() {
    local key="$1" default="$2"
    if [ -f ".env" ]; then
        local val
        val=$(grep -E "^${key}=" .env 2>/dev/null | head -1 | cut -d'=' -f2-)
        if [ -n "$val" ]; then
            echo "$val"
            return
        fi
    fi
    echo "$default"
}

# 检查值是否是占位符
is_placeholder() {
    local val="$1"
    [[ "$val" == *"your"* ]] || [[ "$val" == *"here"* ]] || [[ "$val" == *"change_this"* ]] || [ -z "$val" ]
}

# ══════════════════════════════════════════════════════
# 主流程
# ══════════════════════════════════════════════════════

clear 2>/dev/null || true
echo -e "${GREEN}${BOLD}"
echo "  ╔═══════════════════════════════════════════╗"
echo "  ║     LangHack 交互式部署向导 v2.0          ║"
echo "  ║     英语学习平台一键部署                   ║"
echo "  ╚═══════════════════════════════════════════╝"
echo -e "${NC}"

TOTAL_STEPS=6

# ==============================================================
# 步骤 1: 分支选择
# ==============================================================
step_header 1 $TOTAL_STEPS "选择部署分支"

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
echo -e "  ${DIM}当前分支: ${CURRENT_BRANCH}${NC}"

echo -e "  ${YELLOW}正在获取远程分支...${NC}"
git fetch --all --quiet 2>/dev/null || echo -e "  ${DIM}(离线模式，仅显示本地分支)${NC}"

mapfile -t BRANCH_LIST < <({
    git branch -r --no-color 2>/dev/null | grep -v 'HEAD' | sed 's|origin/||;s|^[[:space:]]*||'
    git branch --no-color 2>/dev/null | sed 's|^\*||;s|^[[:space:]]*||'
} | sort -u)

if [ ${#BRANCH_LIST[@]} -eq 0 ]; then
    BRANCH_LIST=("$CURRENT_BRANCH")
fi

echo ""
echo -e "  ${BOLD}可用分支:${NC}"
default_branch_idx=1
for i in "${!BRANCH_LIST[@]}"; do
    branch="${BRANCH_LIST[$i]}"
    marker=""
    if [ "$branch" = "$CURRENT_BRANCH" ]; then
        marker=" ${GREEN}← 当前${NC}"
        default_branch_idx=$((i + 1))
    fi
    printf "    ${CYAN}%2d)${NC} %s%b\n" $((i + 1)) "$branch" "$marker"
done
echo ""

while true; do
    read -rp "$(echo -e "  选择分支编号 ${DIM}[${default_branch_idx}]${NC}: ")" branch_choice
    branch_choice="${branch_choice:-$default_branch_idx}"
    if [[ "$branch_choice" =~ ^[0-9]+$ ]] && [ "$branch_choice" -ge 1 ] && [ "$branch_choice" -le ${#BRANCH_LIST[@]} ]; then
        TARGET_BRANCH="${BRANCH_LIST[$((branch_choice - 1))]}"
        break
    fi
    echo -e "  ${RED}请输入 1-${#BRANCH_LIST[@]}${NC}"
done

echo -e "  ${GREEN}✓ 分支: ${TARGET_BRANCH}${NC}"

# ==============================================================
# 步骤 2: 部署模式
# ==============================================================
step_header 2 $TOTAL_STEPS "选择部署模式"

DEPLOY_MODE=1
ask_choice "选择部署方式:" "Docker 容器部署|本地开发启动" DEPLOY_MODE 1

case $DEPLOY_MODE in
    1) echo -e "  ${GREEN}✓ Docker 容器部署${NC}" ;;
    2) echo -e "  ${GREEN}✓ 本地开发启动${NC}" ;;
esac

# ==============================================================
# 步骤 3: 环境变量配置
# ==============================================================
step_header 3 $TOTAL_STEPS "配置环境变量"

# 检查是否有现有 .env
if [ -f ".env" ] && [ "$QUICK_MODE" = true ]; then
    echo -e "  ${GREEN}检测到已有 .env 配置，快速模式跳过。${NC}"
    ask_yn "是否要修改现有配置？" "n" EDIT_ENV
    if [ "$EDIT_ENV" = false ]; then
        source .env 2>/dev/null || true
        # 快速读取关键变量
        CFG_DB_PASSWORD=$(load_env_value "DB_PASSWORD" "")
        CFG_DB_USERNAME=$(load_env_value "DB_USERNAME" "root")
        CFG_DB_URL=$(load_env_value "DB_URL" "")
        CFG_JWT_SECRET=$(load_env_value "JWT_SECRET" "")
        CFG_REDIS_HOST=$(load_env_value "REDIS_HOST" "localhost")
        CFG_REDIS_PORT=$(load_env_value "REDIS_PORT" "6379")
        CFG_REDIS_PASSWORD=$(load_env_value "REDIS_PASSWORD" "")
        CFG_OPENROUTER_API_KEY=$(load_env_value "OPENROUTER_API_KEY" "")
        CFG_HIAGENT_API_KEY=$(load_env_value "HIAGENT_API_KEY" "")
        CFG_HIAGENT_API_URL=$(load_env_value "HIAGENT_API_URL" "")
        CFG_HIAGENT_APP_ID=$(load_env_value "HIAGENT_APP_ID" "")
        CFG_TENCENT_SECRET_ID=$(load_env_value "TENCENT_SECRET_ID" "")
        CFG_TENCENT_SECRET_KEY=$(load_env_value "TENCENT_SECRET_KEY" "")
        CFG_CORS_ORIGINS=$(load_env_value "APP_CORS_ALLOWED_ORIGINS" "http://localhost:3000,http://localhost:5173")
        CFG_PORT_FRONTEND=$(load_env_value "PORT_FRONTEND" "80")
        CFG_PORT_BACKEND=$(load_env_value "PORT_BACKEND" "8080")
        CFG_PORT_MYSQL=$(load_env_value "PORT_MYSQL" "3306")
        CFG_BACKUP_ENABLED=$(load_env_value "BACKUP_ENABLED" "true")
        CFG_BACKUP_DIR=$(load_env_value "BACKUP_DIR" "./backups")
        CFG_BACKUP_CRON=$(load_env_value "BACKUP_CRON" "0 0 2 * * ?")
        CFG_BACKUP_RETENTION=$(load_env_value "BACKUP_RETENTION_DAYS" "30")
        CFG_AUTO_UPDATE_ENABLED=$(load_env_value "AUTO_UPDATE_ENABLED" "false")
        CFG_AUTO_UPDATE_CRON=$(load_env_value "AUTO_UPDATE_CRON" "0 * * * *")
        CFG_AUTO_UPDATE_BRANCH=$(load_env_value "AUTO_UPDATE_BRANCH" "$TARGET_BRANCH")
        CFG_AUTO_UPDATE_MAINTENANCE=$(load_env_value "AUTO_UPDATE_MAINTENANCE" "true")
        CFG_AUTO_UPDATE_PRE_BACKUP=$(load_env_value "AUTO_UPDATE_PRE_BACKUP" "true")
        # 跳到步骤4
        SKIP_ENV=true
    fi
fi

if [ "${SKIP_ENV:-false}" = false ]; then
    # 加载现有值作为默认
    DEF_DB_PASSWORD=$(load_env_value "DB_PASSWORD" "")
    DEF_DB_USERNAME=$(load_env_value "DB_USERNAME" "root")
    DEF_JWT_SECRET=$(load_env_value "JWT_SECRET" "")
    DEF_REDIS_HOST=$(load_env_value "REDIS_HOST" "localhost")
    DEF_REDIS_PORT=$(load_env_value "REDIS_PORT" "6379")
    DEF_REDIS_PASSWORD=$(load_env_value "REDIS_PASSWORD" "")
    DEF_OPENROUTER_KEY=$(load_env_value "OPENROUTER_API_KEY" "")
    DEF_HIAGENT_KEY=$(load_env_value "HIAGENT_API_KEY" "")
    DEF_HIAGENT_URL=$(load_env_value "HIAGENT_API_URL" "https://agent.imau.edu.cn:32300/api/proxy/api/v1")
    DEF_HIAGENT_APP=$(load_env_value "HIAGENT_APP_ID" "")
    DEF_TENCENT_ID=$(load_env_value "TENCENT_SECRET_ID" "")
    DEF_TENCENT_KEY=$(load_env_value "TENCENT_SECRET_KEY" "")
    DEF_CORS=$(load_env_value "APP_CORS_ALLOWED_ORIGINS" "http://localhost:3000,http://localhost:5173")
    DEF_PORT_FE=$(load_env_value "PORT_FRONTEND" "80")
    DEF_PORT_BE=$(load_env_value "PORT_BACKEND" "8080")
    DEF_PORT_DB=$(load_env_value "PORT_MYSQL" "3306")
    DEF_BACKUP_ENABLED=$(load_env_value "BACKUP_ENABLED" "true")
    DEF_BACKUP_DIR=$(load_env_value "BACKUP_DIR" "./backups")
    DEF_BACKUP_CRON=$(load_env_value "BACKUP_CRON" "0 0 2 * * ?")
    DEF_BACKUP_RETENTION=$(load_env_value "BACKUP_RETENTION_DAYS" "30")
    DEF_AUTO_UPDATE_ENABLED=$(load_env_value "AUTO_UPDATE_ENABLED" "false")
    DEF_AUTO_UPDATE_BRANCH=$(load_env_value "AUTO_UPDATE_BRANCH" "$TARGET_BRANCH")

    # -- 数据库 --
    group_header "数据库配置"
    ask_value "MySQL 用户名" "$DEF_DB_USERNAME" CFG_DB_USERNAME

    if is_placeholder "$DEF_DB_PASSWORD"; then
        ask_secret "MySQL 密码 (必填)" "" CFG_DB_PASSWORD
        while [ -z "$CFG_DB_PASSWORD" ]; do
            echo -e "  ${RED}密码不能为空${NC}"
            ask_secret "MySQL 密码 (必填)" "" CFG_DB_PASSWORD
        done
    else
        ask_secret "MySQL 密码" "$DEF_DB_PASSWORD" CFG_DB_PASSWORD
    fi

    if [ "$DEPLOY_MODE" -eq 1 ]; then
        # Docker 模式，数据库地址固定
        CFG_DB_URL="jdbc:mysql://mysql:3306/english_learning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true"
        echo -e "  ${DIM}数据库地址: Docker 内部 (mysql:3306)${NC}"
    else
        DEF_DB_URL=$(load_env_value "DB_URL" "jdbc:mysql://localhost:3306/english_learning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true")
        ask_value "数据库 JDBC URL" "$DEF_DB_URL" CFG_DB_URL
    fi

    # -- JWT --
    group_header "JWT 认证"
    if is_placeholder "$DEF_JWT_SECRET"; then
        echo -e "  ${DIM}(自动生成 64 字符安全密钥)${NC}"
        CFG_JWT_SECRET=$(openssl rand -base64 48 2>/dev/null || cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 48 | head -1 || echo "CHANGE_THIS_$(date +%s)_RANDOM_SECRET_KEY_32_CHARS")
        echo -e "  ${GREEN}✓ JWT 密钥已自动生成${NC}"
    else
        ask_yn "重新生成 JWT 密钥？(会使所有用户 token 失效)" "n" REGEN_JWT
        if [ "$REGEN_JWT" = true ]; then
            CFG_JWT_SECRET=$(openssl rand -base64 48 2>/dev/null || cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 48 | head -1)
            echo -e "  ${GREEN}✓ JWT 密钥已重新生成${NC}"
        else
            CFG_JWT_SECRET="$DEF_JWT_SECRET"
        fi
    fi

    # -- Redis --
    group_header "Redis 配置"
    if [ "$DEPLOY_MODE" -eq 1 ]; then
        CFG_REDIS_HOST="redis"
        echo -e "  ${DIM}Redis 地址: Docker 内部 (redis:6379)${NC}"
    else
        ask_value "Redis 主机" "$DEF_REDIS_HOST" CFG_REDIS_HOST
    fi
    ask_value "Redis 端口" "$DEF_REDIS_PORT" CFG_REDIS_PORT
    ask_secret "Redis 密码 (可留空)" "$DEF_REDIS_PASSWORD" CFG_REDIS_PASSWORD

    # -- AI 服务 --
    group_header "AI 服务配置"
    echo -e "  ${DIM}提示: 不使用的服务直接回车跳过${NC}"

    ask_secret "OpenRouter API Key" "$DEF_OPENROUTER_KEY" CFG_OPENROUTER_API_KEY
    ask_secret "HiAgent API Key (可选)" "$DEF_HIAGENT_KEY" CFG_HIAGENT_API_KEY

    if [ -n "$CFG_HIAGENT_API_KEY" ]; then
        ask_value "HiAgent API URL" "$DEF_HIAGENT_URL" CFG_HIAGENT_API_URL
        ask_value "HiAgent App ID" "$DEF_HIAGENT_APP" CFG_HIAGENT_APP_ID
    else
        CFG_HIAGENT_API_URL=""
        CFG_HIAGENT_APP_ID=""
    fi

    # -- 腾讯云 --
    group_header "腾讯云服务 (翻译/语音识别)"
    echo -e "  ${DIM}提示: 不使用翻译和 ASR 功能可跳过${NC}"

    ask_secret "腾讯云 SecretId (可选)" "$DEF_TENCENT_ID" CFG_TENCENT_SECRET_ID
    ask_secret "腾讯云 SecretKey (可选)" "$DEF_TENCENT_KEY" CFG_TENCENT_SECRET_KEY

    # -- 网络配置 --
    group_header "网络和端口"
    ask_value "CORS 允许的前端地址 (逗号分隔)" "$DEF_CORS" CFG_CORS_ORIGINS
    ask_value "前端端口" "$DEF_PORT_FE" CFG_PORT_FRONTEND
    ask_value "后端端口" "$DEF_PORT_BE" CFG_PORT_BACKEND
    ask_value "MySQL 端口" "$DEF_PORT_DB" CFG_PORT_MYSQL

    # -- 备份 --
    group_header "自动备份"
    ask_yn "启用自动备份？" "y" CFG_BACKUP_ENABLED_YN
    if [ "$CFG_BACKUP_ENABLED_YN" = true ]; then
        CFG_BACKUP_ENABLED="true"
        ask_value "备份存储目录" "$DEF_BACKUP_DIR" CFG_BACKUP_DIR
        ask_value "备份 Cron 表达式" "$DEF_BACKUP_CRON" CFG_BACKUP_CRON
        ask_value "备份保留天数" "$DEF_BACKUP_RETENTION" CFG_BACKUP_RETENTION
    else
        CFG_BACKUP_ENABLED="false"
        CFG_BACKUP_DIR="$DEF_BACKUP_DIR"
        CFG_BACKUP_CRON="$DEF_BACKUP_CRON"
        CFG_BACKUP_RETENTION="$DEF_BACKUP_RETENTION"
    fi

    # -- 自动更新 --
    group_header "自动更新"
    echo -e "  ${DIM}提示: 定期从 GitHub 拉取代码并自动重建部署${NC}"
    ask_yn "启用定期自动更新？" "n" CFG_AUTO_UPDATE_ENABLED_YN
    if [ "$CFG_AUTO_UPDATE_ENABLED_YN" = true ]; then
        CFG_AUTO_UPDATE_ENABLED="true"

        echo -e "  ${BOLD}更新检查间隔:${NC}"
        ask_choice "选择频率:" "每 5 分钟|每 10 分钟|每 30 分钟|每小时" AUTO_UPDATE_FREQ 4
        case $AUTO_UPDATE_FREQ in
            1) CFG_AUTO_UPDATE_CRON="*/5 * * * *" ;;
            2) CFG_AUTO_UPDATE_CRON="*/10 * * * *" ;;
            3) CFG_AUTO_UPDATE_CRON="*/30 * * * *" ;;
            4) CFG_AUTO_UPDATE_CRON="0 * * * *" ;;
        esac

        ask_value "跟踪分支" "${DEF_AUTO_UPDATE_BRANCH}" CFG_AUTO_UPDATE_BRANCH
        ask_yn "更新前自动备份数据库？" "y" CFG_AUTO_UPDATE_PRE_BACKUP_YN
        CFG_AUTO_UPDATE_PRE_BACKUP="true"
        [ "$CFG_AUTO_UPDATE_PRE_BACKUP_YN" = false ] && CFG_AUTO_UPDATE_PRE_BACKUP="false"
        ask_yn "更新时启用维护模式？" "y" CFG_AUTO_UPDATE_MAINTENANCE_YN
        CFG_AUTO_UPDATE_MAINTENANCE="true"
        [ "$CFG_AUTO_UPDATE_MAINTENANCE_YN" = false ] && CFG_AUTO_UPDATE_MAINTENANCE="false"
    else
        CFG_AUTO_UPDATE_ENABLED="false"
        CFG_AUTO_UPDATE_CRON="0 * * * *"
        CFG_AUTO_UPDATE_BRANCH="$TARGET_BRANCH"
        CFG_AUTO_UPDATE_MAINTENANCE="true"
        CFG_AUTO_UPDATE_PRE_BACKUP="true"
    fi

    echo -e "  ${GREEN}✓ 环境变量配置完成${NC}"
fi

# ==============================================================
# 步骤 4: 构建选项 (仅 Docker 模式)
# ==============================================================
step_header 4 $TOTAL_STEPS "构建选项"

BUILD_MODE=2
SERVICES_MODE=1

if [ "$DEPLOY_MODE" -eq 1 ]; then
    ask_choice "选择构建方式:" "全量重建 (--no-cache)|增量构建 (使用缓存)|仅重启 (不重新构建)" BUILD_MODE 2
    echo ""
    ask_choice "选择部署服务:" "全部服务|仅后端 (backend + mysql + redis)|仅前端 (frontend)|仅基础设施 (mysql + redis)" SERVICES_MODE 1
else
    ask_choice "选择启动方式:" "前后端一起启动|仅启动后端|仅启动前端" SERVICES_MODE 1
fi

# ==============================================================
# 步骤 5: 确认配置
# ==============================================================
step_header 5 $TOTAL_STEPS "确认配置"

echo ""
echo -e "  ${BOLD}部署配置摘要:${NC}"
echo -e "  ┌──────────────────────────────────────────┐"
echo -e "  │ 分支:        ${CYAN}${TARGET_BRANCH}${NC}"

if [ "$DEPLOY_MODE" -eq 1 ]; then
    mode_label="Docker 容器"
else
    mode_label="本地开发"
fi
echo -e "  │ 模式:        ${CYAN}${mode_label}${NC}"

case $BUILD_MODE in
    1) build_label="全量重建" ;;
    2) build_label="增量构建" ;;
    3) build_label="仅重启" ;;
esac
echo -e "  │ 构建:        ${CYAN}${build_label}${NC}"

case $SERVICES_MODE in
    1) svc_label="全部服务" ;;
    2) svc_label="仅后端" ;;
    3) svc_label="仅前端" ;;
    4) svc_label="仅基础设施" ;;
esac
echo -e "  │ 服务:        ${CYAN}${svc_label}${NC}"

echo -e "  │ 数据库:      ${CYAN}${CFG_DB_USERNAME}@***${NC}"
echo -e "  │ Redis:       ${CYAN}${CFG_REDIS_HOST}:${CFG_REDIS_PORT}${NC}"

ai_status=""
[ -n "${CFG_OPENROUTER_API_KEY:-}" ] && ! is_placeholder "${CFG_OPENROUTER_API_KEY:-}" && ai_status="${ai_status}OpenRouter "
[ -n "${CFG_HIAGENT_API_KEY:-}" ] && ai_status="${ai_status}HiAgent "
[ -n "${CFG_TENCENT_SECRET_ID:-}" ] && ! is_placeholder "${CFG_TENCENT_SECRET_ID:-}" && ai_status="${ai_status}腾讯云 "
[ -z "$ai_status" ] && ai_status="未配置"
echo -e "  │ AI 服务:     ${CYAN}${ai_status}${NC}"

echo -e "  │ 端口:        ${CYAN}前端:${CFG_PORT_FRONTEND:-80} 后端:${CFG_PORT_BACKEND:-8080} DB:${CFG_PORT_MYSQL:-3306}${NC}"
echo -e "  │ 备份:        ${CYAN}${CFG_BACKUP_ENABLED:-true}${NC}"
echo -e "  │ CORS:        ${CYAN}${CFG_CORS_ORIGINS:-http://localhost:3000}${NC}"
if [ "${CFG_AUTO_UPDATE_ENABLED:-false}" = "true" ]; then
echo -e "  │ 自动更新:    ${CYAN}已启用 (${CFG_AUTO_UPDATE_CRON}) → ${CFG_AUTO_UPDATE_BRANCH}${NC}"
else
echo -e "  │ 自动更新:    ${CYAN}未启用${NC}"
fi
echo -e "  └──────────────────────────────────────────┘"
echo ""

ask_yn "确认开始部署？" "y" CONFIRM_DEPLOY

if [ "$CONFIRM_DEPLOY" = false ]; then
    echo -e "${YELLOW}部署已取消。${NC}"
    exit 0
fi

# ==============================================================
# 步骤 6: 执行部署
# ==============================================================
step_header 6 $TOTAL_STEPS "执行部署"

# ── 6.1 切换分支 ──────────────────────────────────────
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

if [ "$TARGET_BRANCH" != "$CURRENT_BRANCH" ]; then
    echo -e "  ${YELLOW}切换分支: ${CURRENT_BRANCH} → ${TARGET_BRANCH}${NC}"

    if ! git diff --quiet 2>/dev/null || ! git diff --cached --quiet 2>/dev/null; then
        git stash push -m "deploy-auto-stash-$(date +%Y%m%d_%H%M%S)" --quiet
        echo -e "  ${DIM}已自动暂存未提交的更改${NC}"
    fi

    git checkout "$TARGET_BRANCH" --quiet || { echo -e "  ${RED}切换分支失败！${NC}"; exit 1; }
fi

echo -e "  ${YELLOW}拉取最新代码...${NC}"
git pull origin "$TARGET_BRANCH" --ff-only --quiet 2>/dev/null || echo -e "  ${DIM}拉取失败，使用本地代码${NC}"
echo -e "  ${GREEN}✓ 代码就绪: $(git rev-parse --short HEAD)${NC}"

# ── 6.2 写入 .env ────────────────────────────────────
if [ "${SKIP_ENV:-false}" = false ]; then
    echo -e "  ${YELLOW}写入 .env 配置...${NC}"
    cat > .env <<ENVFILE
# =====================================================================
# LangHack 环境配置 (由部署向导自动生成)
# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')
# 部署分支: ${TARGET_BRANCH}
# =====================================================================

# ── CORS 跨域 ────────────────────────────────────────
APP_CORS_ALLOWED_ORIGINS=${CFG_CORS_ORIGINS}

# ── 数据库 ────────────────────────────────────────────
DB_URL=${CFG_DB_URL}
DB_USERNAME=${CFG_DB_USERNAME}
DB_PASSWORD=${CFG_DB_PASSWORD}

# ── JWT ───────────────────────────────────────────────
JWT_SECRET=${CFG_JWT_SECRET}

# ── Redis ─────────────────────────────────────────────
REDIS_HOST=${CFG_REDIS_HOST}
REDIS_PORT=${CFG_REDIS_PORT}
$([ -n "${CFG_REDIS_PASSWORD}" ] && echo "REDIS_PASSWORD=${CFG_REDIS_PASSWORD}" || echo "# REDIS_PASSWORD=")

# ── AI 服务 ───────────────────────────────────────────
$([ -n "${CFG_OPENROUTER_API_KEY}" ] && echo "OPENROUTER_API_KEY=${CFG_OPENROUTER_API_KEY}" || echo "# OPENROUTER_API_KEY=")
$([ -n "${CFG_HIAGENT_API_KEY}" ] && echo "HIAGENT_API_KEY=${CFG_HIAGENT_API_KEY}" || echo "# HIAGENT_API_KEY=")
$([ -n "${CFG_HIAGENT_API_URL}" ] && echo "HIAGENT_API_URL=${CFG_HIAGENT_API_URL}" || echo "# HIAGENT_API_URL=")
$([ -n "${CFG_HIAGENT_APP_ID}" ] && echo "HIAGENT_APP_ID=${CFG_HIAGENT_APP_ID}" || echo "# HIAGENT_APP_ID=")

# ── 腾讯云 ────────────────────────────────────────────
$([ -n "${CFG_TENCENT_SECRET_ID}" ] && echo "TENCENT_SECRET_ID=${CFG_TENCENT_SECRET_ID}" || echo "# TENCENT_SECRET_ID=")
$([ -n "${CFG_TENCENT_SECRET_KEY}" ] && echo "TENCENT_SECRET_KEY=${CFG_TENCENT_SECRET_KEY}" || echo "# TENCENT_SECRET_KEY=")

# ── 端口 ──────────────────────────────────────────────
PORT_FRONTEND=${CFG_PORT_FRONTEND:-80}
PORT_BACKEND=${CFG_PORT_BACKEND:-8080}
PORT_MYSQL=${CFG_PORT_MYSQL:-3306}

# ── 备份 ──────────────────────────────────────────────
BACKUP_ENABLED=${CFG_BACKUP_ENABLED}
BACKUP_DIR=${CFG_BACKUP_DIR}
BACKUP_CRON=${CFG_BACKUP_CRON}
BACKUP_RETENTION_DAYS=${CFG_BACKUP_RETENTION}

# ── 自动更新 ──────────────────────────────────────────
AUTO_UPDATE_ENABLED=${CFG_AUTO_UPDATE_ENABLED}
AUTO_UPDATE_CRON=${CFG_AUTO_UPDATE_CRON}
AUTO_UPDATE_BRANCH=${CFG_AUTO_UPDATE_BRANCH}
AUTO_UPDATE_MAINTENANCE=${CFG_AUTO_UPDATE_MAINTENANCE}
AUTO_UPDATE_PRE_BACKUP=${CFG_AUTO_UPDATE_PRE_BACKUP}
ENVFILE
    echo -e "  ${GREEN}✓ .env 已写入${NC}"
fi

# ── 6.3 执行构建/启动 ────────────────────────────────
if [ "$DEPLOY_MODE" -eq 1 ]; then
    # ===== Docker 部署 =====
    echo -e "  ${YELLOW}Docker 容器部署...${NC}"

    # 确定服务列表
    case $SERVICES_MODE in
        1) SERVICES="" ;;             # 全部
        2) SERVICES="backend mysql redis" ;;
        3) SERVICES="frontend" ;;
        4) SERVICES="mysql redis" ;;
    esac

    # 确定构建参数
    case $BUILD_MODE in
        1) BUILD_ARGS="--no-cache" ;;
        2) BUILD_ARGS="" ;;
        3) BUILD_ARGS="__SKIP_BUILD__" ;;
    esac

    # 构建
    if [ "$BUILD_ARGS" != "__SKIP_BUILD__" ]; then
        echo -e "  ${CYAN}构建镜像...${NC}"
        if [ -n "$SERVICES" ]; then
            docker-compose build $BUILD_ARGS $SERVICES
        else
            docker-compose build $BUILD_ARGS
        fi
    fi

    # 启动
    echo -e "  ${CYAN}启动容器...${NC}"
    if [ -n "$SERVICES" ]; then
        docker-compose up -d $SERVICES
    else
        docker-compose up -d
    fi

    if [ $? -eq 0 ]; then
        echo ""
        echo -e "  ${CYAN}等待服务启动...${NC}"
        sleep 8

        # 健康检查
        if curl -sf "http://localhost:${CFG_PORT_BACKEND:-8080}/actuator/health" > /dev/null 2>&1; then
            echo -e "  ${GREEN}✓ 后端健康检查通过${NC}"
        else
            echo -e "  ${YELLOW}⚠ 后端仍在启动中，请稍后检查${NC}"
        fi

        echo ""
        echo -e "${GREEN}${BOLD}  ╔═══════════════════════════════════════╗${NC}"
        echo -e "${GREEN}${BOLD}  ║         🎉 部署成功！                ║${NC}"
        echo -e "${GREEN}${BOLD}  ╚═══════════════════════════════════════╝${NC}"
        echo ""
        echo -e "  分支:   ${CYAN}${TARGET_BRANCH}${NC}"
        echo -e "  版本:   ${CYAN}$(git rev-parse --short HEAD) — $(git log -1 --format='%s')${NC}"
        echo -e ""
        echo -e "  ${BOLD}访问地址:${NC}"
        echo -e "    前端: ${CYAN}http://localhost:${CFG_PORT_FRONTEND:-80}${NC}"
        echo -e "    后端: ${CYAN}http://localhost:${CFG_PORT_BACKEND:-8080}${NC}"
        echo -e ""
        echo -e "  ${BOLD}常用命令:${NC}"
        echo -e "    查看日志:  ${DIM}docker-compose logs -f${NC}"
        echo -e "    查看状态:  ${DIM}docker-compose ps${NC}"
        echo -e "    停止服务:  ${DIM}docker-compose down${NC}"
        echo -e "    重启服务:  ${DIM}docker-compose restart${NC}"

        # ── 设置自动更新 crontab ─────────────────────
        if [ "${CFG_AUTO_UPDATE_ENABLED:-false}" = "true" ]; then
            SCRIPT_PATH="$(cd "$(dirname "$0")" && pwd)/scripts/auto-update.sh"
            LOG_PATH="/var/log/langhack-update.log"
            CRON_CMD="${CFG_AUTO_UPDATE_CRON} ${SCRIPT_PATH} -b ${CFG_AUTO_UPDATE_BRANCH} >> ${LOG_PATH} 2>&1"

            # 移除旧条目，添加新条目
            ( crontab -l 2>/dev/null | grep -v "auto-update.sh" || true; echo "$CRON_CMD" ) | crontab - 2>/dev/null && \
                echo -e "  ${GREEN}✓ 自动更新已配置 (crontab: ${CFG_AUTO_UPDATE_CRON})${NC}" || \
                echo -e "  ${YELLOW}⚠ crontab 设置失败，请手动添加: ${CRON_CMD}${NC}"
        fi

        # ── .env 备份提醒 ────────────────────────────
        echo ""
        echo -e "  ${YELLOW}${BOLD}重要提醒:${NC}"
        echo -e "  ${YELLOW}请备份 .env 文件到安全位置！如更换服务器需一并迁移。${NC}"
    else
        echo -e "  ${RED}❌ 部署失败，请检查错误日志！${NC}"
        exit 1
    fi

else
    # ===== 本地开发 =====
    echo -e "  ${YELLOW}本地开发启动...${NC}"

    # 加载环境变量
    set -a
    source .env
    set +a

    mkdir -p logs

    # 停止已有进程
    pkill -f 'spring-boot:run' 2>/dev/null && echo -e "  ${DIM}已停止旧后端进程${NC}"
    pkill -f 'vite.*--port' 2>/dev/null && echo -e "  ${DIM}已停止旧前端进程${NC}"
    sleep 2

    case $SERVICES_MODE in
        1|2)
            echo -e "  ${CYAN}启动后端...${NC}"
            cd backend
            nohup mvn spring-boot:run > ../logs/backend.log 2>&1 &
            BE_PID=$!
            echo -e "  ${GREEN}✓ 后端已启动 (PID: $BE_PID)${NC}"
            cd ..
            ;;
    esac

    case $SERVICES_MODE in
        1|3)
            echo -e "  ${CYAN}启动前端...${NC}"
            cd frontend
            nohup npm run dev > ../logs/frontend.log 2>&1 &
            FE_PID=$!
            echo -e "  ${GREEN}✓ 前端已启动 (PID: $FE_PID)${NC}"
            cd ..
            ;;
    esac

    echo ""
    echo -e "${GREEN}${BOLD}  🎉 启动完成！${NC}"
    echo ""
    echo -e "  ${BOLD}访问地址:${NC}"
    echo -e "    前端: ${CYAN}http://localhost:3000${NC}"
    echo -e "    后端: ${CYAN}http://localhost:8080${NC}"
    echo ""
    echo -e "  ${BOLD}日志:${NC}"
    echo -e "    后端: ${DIM}logs/backend.log${NC}"
    echo -e "    前端: ${DIM}logs/frontend.log${NC}"
fi
