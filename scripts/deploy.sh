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
OFFLINE_MODE=false
STASHED=false
OLD_BRANCH=""
OLD_COMMIT=""
CRON_TMP=""
BUILD_PID=""

# ── 信号处理 & 清理 ─────────────────────────────────
deploy_cleanup() {
    local exit_code=$?

    # 清理后台构建进程
    if [ -n "$BUILD_PID" ] && kill -0 "$BUILD_PID" 2>/dev/null; then
        echo ""
        echo "[清理] 终止后台构建进程 (PID: $BUILD_PID)..."
        kill "$BUILD_PID" 2>/dev/null || true
        wait "$BUILD_PID" 2>/dev/null || true
    fi

    if [ "$STASHED" = true ]; then
        echo ""
        echo "[提示] 部署前保存了本地修改，恢复命令: git stash pop"
        echo "  查看: git stash list | head -1"
    fi
    if [ $exit_code -ne 0 ] && [ -n "$OLD_BRANCH" ]; then
        echo "[提示] 原始分支: $OLD_BRANCH"
        echo "[提示] 如需回退: git checkout $OLD_BRANCH"
    fi
    # 清理可能残留的 crontab 临时文件
    [ -n "$CRON_TMP" ] && rm -f "$CRON_TMP" 2>/dev/null || true
    # 清理构建日志
    rm -f /tmp/langhack-deploy-build-$$.log 2>/dev/null || true
}
trap deploy_cleanup EXIT

# 捕获中断信号（Ctrl+C）并优雅退出
trap_interrupt() {
    echo ""
    echo "[WARN] 收到中断信号 (Ctrl+C)，正在安全退出..."

    # 立即终止后台构建进程
    if [ -n "$BUILD_PID" ] && kill -0 "$BUILD_PID" 2>/dev/null; then
        echo "[清理] 终止后台构建进程 (PID: $BUILD_PID)..."
        kill "$BUILD_PID" 2>/dev/null || true
        wait "$BUILD_PID" 2>/dev/null || true
    fi

    echo "[INFO] 已执行的操作不会回滚，如需清理请手动执行:"
    echo "  - 停止容器: cd $DOCKER_DIR && docker compose down"
    echo "  - 回退代码: cd $PROJECT_ROOT && git checkout $OLD_BRANCH"
    exit 130
}
trap trap_interrupt SIGINT SIGTERM

# ── 参数解析 ──────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --quick|-q) QUICK_MODE=true; shift ;;
        --offline|--local-only) OFFLINE_MODE=true; shift ;;
        --help|-h)
            echo "LangHack Docker Compose 交互式部署"
            echo ""
            echo "用法: $0 [选项]"
            echo "  --quick, -q          快速模式（跳过已配置项）"
            echo "  --offline, --local-only  离线模式（跳过 git pull，使用本地代码）"
            echo "  --help, -h           显示帮助"
            exit 0
            ;;
        *) echo "[ERROR] 未知参数: $1"; exit 1 ;;
    esac
done

# ── 工具函数 ──────────────────────────────────────────
ask_value() {
    local prompt="$1"
    local default="$2"
    local var_name="$3"
    local input
    if [ -n "$default" ]; then
        read -rp "  ${prompt} [${default}]: " input
        # 清理 ANSI 转义序列、控制字符和 shell 元字符
        input=$(echo "${input:-$default}" | LC_ALL=C sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[[:cntrl:]]//g' | sed 's/[$`!\\]/\\&/g')
        printf -v "$var_name" '%s' "$input"
    else
        read -rp "  ${prompt}: " input
        # 清理 ANSI 转义序列、控制字符和 shell 元字符
        input=$(echo "$input" | LC_ALL=C sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[[:cntrl:]]//g' | sed 's/[$`!\\]/\\&/g')
        printf -v "$var_name" '%s' "$input"
    fi
}

ask_secret() {
    local prompt="$1"
    local default="$2"
    local var_name="$3"
    local input masked=""
    if [ -n "$default" ]; then
        [ ${#default} -gt 6 ] && masked="${default:0:3}***${default: -3}" || masked="***"
        read -rp "  ${prompt} [${masked}]: " input
        input=$(echo "${input:-$default}" | LC_ALL=C sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[[:cntrl:]]//g')
        printf -v "$var_name" '%s' "$input"
    else
        read -rp "  ${prompt}: " input
        input=$(echo "$input" | LC_ALL=C sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[[:cntrl:]]//g')
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
    local question="$1"
    local default="$2"
    local var_name="$3"
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
    local key="$1"
    local default="$2"
    local file="${3:-$DOCKER_DIR/.env}"
    if [ -f "$file" ]; then
        local val; val=$(grep -E "^${key}=" "$file" 2>/dev/null | grep -v '^\s*#' | head -1 | cut -d'=' -f2- | LC_ALL=C sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[[:cntrl:]]//g')
        [ -n "$val" ] && echo "$val" && return
    fi
    echo "$default"
}

set_env_value() {
    local file="${1:-}" key="${2:-}" value="${3:-}"
    [ -z "$file" ] && { echo "[ERROR] set_env_value: file parameter required"; return 1; }
    local tmp="${file}.tmp.$$"
    local found=false
    touch "$file"
    while IFS= read -r line || [ -n "$line" ]; do
        if [[ "$line" == "$key="* ]] || [[ "$line" == "# $key="* ]] || [[ "$line" == "#$key="* ]]; then
            printf '%s=%s\n' "$key" "$value" >> "$tmp"
            found=true
        else
            printf '%s\n' "$line" >> "$tmp"
        fi
    done < "$file"
    if [ "$found" = false ]; then
        printf '%s=%s\n' "$key" "$value" >> "$tmp"
    fi
    mv "$tmp" "$file"
}

normalize_registry_prefix() {
    local registry="$1"
    registry="${registry#http://}"
    registry="${registry#https://}"
    registry="${registry%/}"
    [ -n "$registry" ] && printf '%s/' "$registry"
}

probe_public_image() {
    local image="$1"
    local timeout=30  # 探测超时 30 秒
    echo "  [INFO] 探测公共镜像源: docker pull $image (超时 ${timeout}s)"

    # 使用后台进程 + timeout 避免 docker pull 卡死
    if command -v timeout >/dev/null 2>&1; then
        if timeout "$timeout" docker pull "$image" >/dev/null 2>&1; then
            return 0
        fi
    else
        # 无 timeout 命令时使用后台进程 + kill
        docker pull "$image" >/dev/null 2>&1 &
        local pull_pid=$!
        local elapsed=0
        while kill -0 "$pull_pid" 2>/dev/null && [ $elapsed -lt $timeout ]; do
            sleep 2
            elapsed=$((elapsed + 2))
        done
        if kill -0 "$pull_pid" 2>/dev/null; then
            kill "$pull_pid" 2>/dev/null || true
            wait "$pull_pid" 2>/dev/null || true
            return 1
        fi
        wait "$pull_pid" 2>/dev/null && return 0 || return 1
    fi
    return 1
}

probe_local_registry() {
    local registry="$1"
    local image="$2"
    local timeout=30
    local full_image="${registry}/${image}"

    echo "  [INFO] 探测本地 registry: docker pull $full_image (超时 ${timeout}s)"

    # 使用后台进程 + timeout 避免 docker pull 卡死
    if command -v timeout >/dev/null 2>&1; then
        if timeout "$timeout" docker pull "$full_image" >/dev/null 2>&1; then
            return 0
        fi
    else
        # 无 timeout 命令时使用后台进程 + kill
        docker pull "$full_image" >/dev/null 2>&1 &
        local pull_pid=$!
        local elapsed=0
        while kill -0 "$pull_pid" 2>/dev/null && [ $elapsed -lt $timeout ]; do
            sleep 2
            elapsed=$((elapsed + 2))
        done
        if kill -0 "$pull_pid" 2>/dev/null; then
            kill "$pull_pid" 2>/dev/null || true
            wait "$pull_pid" 2>/dev/null || true
            return 1
        fi
        wait "$pull_pid" 2>/dev/null && return 0 || return 1
    fi
    return 1
}

resolve_image_registry_prefix() {
    local mode="$1"
    local local_registry="$2"
    local probe_image="$3"
    RESOLVED_IMAGE_REGISTRY_PREFIX=""
    RESOLVED_IMAGE_SOURCE_LABEL="公共镜像"

    case "${mode,,}" in
        public)
            RESOLVED_IMAGE_REGISTRY_PREFIX=""
            RESOLVED_IMAGE_SOURCE_LABEL="公共镜像（强制）"
            ;;
        local)
            RESOLVED_IMAGE_REGISTRY_PREFIX="$(normalize_registry_prefix "$local_registry")"
            RESOLVED_IMAGE_SOURCE_LABEL="本地 registry (${local_registry})"
            ;;
        auto|"")
            if probe_public_image "$probe_image"; then
                echo "  [OK] 公共镜像源可用"
                RESOLVED_IMAGE_REGISTRY_PREFIX=""
                RESOLVED_IMAGE_SOURCE_LABEL="公共镜像（自动）"
            else
                echo "  [WARN] 公共镜像不可达，尝试本地 registry: $local_registry"
                # 验证本地 registry 是否可达
                if probe_local_registry "$local_registry" "$probe_image"; then
                    echo "  [OK] 本地 registry 可用"
                    RESOLVED_IMAGE_REGISTRY_PREFIX="$(normalize_registry_prefix "$local_registry")"
                    RESOLVED_IMAGE_SOURCE_LABEL="本地 registry (${local_registry}, 公共镜像不可达自动降级)"
                else
                    echo "  [ERROR] 公共镜像和本地 registry 都不可用"
                    echo "  [ERROR] 公共镜像: 不可达"
                    echo "  [ERROR] 本地 registry ($local_registry): 不可达或镜像不存在"
                    echo ""
                    echo "  [修复建议]:"
                    echo "    1. 检查网络连接"
                    echo "    2. 同步镜像到本地 registry: bash scripts/sync-docker-images.sh --registry $local_registry"
                    echo "    3. 或强制使用公共镜像: 在 .env 中设置 IMAGE_SOURCE_MODE=public"
                    exit 1
                fi
            fi
            ;;
        *)
            echo "[ERROR] IMAGE_SOURCE_MODE 无效: $mode (有效值: auto/public/local)"
            exit 1
            ;;
    esac

    if [ -z "$RESOLVED_IMAGE_REGISTRY_PREFIX" ]; then
        echo "  [INFO] Docker 镜像前缀: (空，使用公共镜像)"
    else
        echo "  [INFO] Docker 镜像前缀: $RESOLVED_IMAGE_REGISTRY_PREFIX"
    fi
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
    local prompt="$1"
    local default="$2"
    local var_name="$3"
    local port="$default"

    # 首次检查：如果默认端口被占用，自动寻找可用端口
    if ! check_port "$port"; then
        echo "  [WARN] 默认端口 ${port} 已被占用，自动寻找可用端口..."
        local suggest=$((port + 1))
        local max_suggest=$((port + 100))
        [ "$max_suggest" -gt 65535 ] && max_suggest=65535
        while ! check_port "$suggest" && [ "$suggest" -le "$max_suggest" ]; do suggest=$((suggest + 1)); done
        if [ "$suggest" -le "$max_suggest" ]; then
            echo "  [建议] 可用端口: ${suggest}"
            port="$suggest"
        fi
    fi

    # 循环验证用户输入
    while true; do
        ask_value "$prompt" "$port" "$var_name"
        port="${!var_name}"

        # 验证端口号范围
        if ! [[ "$port" =~ ^[0-9]+$ ]] || [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
            echo "  [ERROR] 端口号必须在 1-65535 范围内，请重新输入"
            continue
        fi

        # 检查端口占用
        if ! check_port "$port"; then
            echo "  [WARN] 端口 ${port} 已被占用，请换一个端口"
            # 自动寻找下一个可用端口作为建议
            local suggest=$((port + 1))
            local max_suggest=$((port + 100))
            [ "$max_suggest" -gt 65535 ] && max_suggest=65535
            while ! check_port "$suggest" && [ "$suggest" -le "$max_suggest" ]; do suggest=$((suggest + 1)); done
            if [ "$suggest" -le "$max_suggest" ]; then
                echo "  [建议] 可用端口: ${suggest}"
                port="$suggest"
            fi
            continue
        fi

        # 端口有效且可用
        break
    done
}

# ── 部署验证函数 ──────────────────────────────────────
# 检查旧容器残留（避免 docker compose down 后仍有旧容器）
check_old_containers() {
    local old_containers
    old_containers=$(docker ps -a --filter "name=langhack" --format "{{.Names}}\t{{.Status}}" 2>/dev/null | grep -v "Up.*seconds" || echo "")

    if [ -n "$old_containers" ]; then
        echo ""
        echo "  [WARN] 检测到可能的旧容器："
        echo "$old_containers" | sed 's/^/    /'
        echo ""
        ask_yn "是否清理这些容器？" "y" DO_CLEANUP
        if [ "$DO_CLEANUP" = "true" ]; then
            echo "  [清理] 停止并删除旧容器..."
            docker ps -a --filter "name=langhack" --format "{{.Names}}" | xargs -r docker rm -f 2>/dev/null || true
            echo "  [OK] 清理完成"
        fi
    fi
}

# 验证数据库初始化（检查关键表是否存在）
verify_database_init() {
    local mysql_container
    mysql_container=$(docker ps --format '{{.Names}}' | grep -E 'mysql-master|langhack-mysql-master' | head -1 || echo "")

    if [ -z "$mysql_container" ]; then
        echo "  [SKIP] MySQL 容器未运行，跳过数据库验证"
        return 0
    fi

    echo "  [验证] 检查数据库初始化状态..."

    local root_pw
    root_pw=$(grep -E "^MYSQL_ROOT_PASSWORD=" "$DOCKER_DIR/.env" 2>/dev/null | cut -d'=' -f2- || echo "")

    if [ -z "$root_pw" ]; then
        echo "  [WARN] 无法读取 MYSQL_ROOT_PASSWORD，跳过验证"
        return 0
    fi

    local table_count
    table_count=$(docker exec "$mysql_container" \
        mysql -uroot -p"$root_pw" english_learning \
        -sN -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='english_learning';" \
        2>/dev/null || echo "0")

    if [ "${table_count:-0}" -lt 10 ]; then
        echo ""
        echo "  [ERROR] 数据库初始化不完整，检测到表数量: $table_count"
        echo "  [提示] 可能原因："
        echo "    1. 数据卷未完全删除，MySQL 跳过了初始化脚本"
        echo "    2. 初始化脚本执行失败"
        echo ""
        echo "  [修复] 建议执行以下命令重新初始化："
        echo "    cd $DOCKER_DIR"
        echo "    docker compose down"
        echo "    docker volume rm langhack_mysql_master_data langhack_mysql_slave_data"
        echo "    docker compose up -d"
        echo ""
        return 1
    fi

    echo "  [OK] 数据库初始化验证通过（表数量: $table_count）"
    return 0
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

# 尝试获取远程分支列表（带超时）
FETCH_TIMEOUT=30
FETCH_SUCCESS=false
echo "  [INFO] 获取远程分支列表（超时 ${FETCH_TIMEOUT}s）..."

if command -v timeout >/dev/null 2>&1; then
    if timeout "$FETCH_TIMEOUT" git fetch --all --quiet 2>/dev/null; then
        FETCH_SUCCESS=true
    fi
else
    if git fetch --all --quiet 2>/dev/null; then
        FETCH_SUCCESS=true
    fi
fi

if [ "$FETCH_SUCCESS" = false ]; then
    echo "  [WARN] 无法连接远程仓库（网络故障或超时），将使用本地分支列表"
    echo "  [INFO] 离线部署模式：只能选择本地已有的分支"
fi

mapfile -t BRANCH_LIST < <({
    git branch -r --no-color 2>/dev/null | grep -v 'HEAD' | sed 's|origin/||;s|^[[:space:]]*||'
    git branch --no-color 2>/dev/null | sed 's|^\*||;s|^[[:space:]]*||'
} | grep -v 'HEAD detached' | grep -v '^$' | sort -u)

[ ${#BRANCH_LIST[@]} -eq 0 ] && BRANCH_LIST=("master")

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
        echo "  HiAgent 专有协议 — AI 改写与 AI 问答必须使用两套完全不同的应用凭证（Key+AppID 均独立）"
        ask_secret "HiAgent 改写 API Key (留空跳过改写)"    "$(load_env_value "HIAGENT_API_KEY" "")"    CFG_HIAGENT_KEY
        if [ -n "$CFG_HIAGENT_KEY" ]; then
            ask_value  "HiAgent 改写 App ID" "$(load_env_value "HIAGENT_APP_ID" "")"    CFG_HIAGENT_APP_ID
        else
            CFG_HIAGENT_APP_ID=""
        fi
        echo "  [提示] AI 问答需独立的 HiAgent 应用，不 fallback 到改写凭证；留空则禁用问答功能。"
        ask_secret "HiAgent 问答 API Key (留空禁用问答)"    "$(load_env_value "HIAGENT_QA_API_KEY" "")" CFG_HIAGENT_QA_KEY
        if [ -n "$CFG_HIAGENT_QA_KEY" ]; then
            # App ID 必填校验
            while true; do
                ask_value  "HiAgent 问答 App ID" "$(load_env_value "HIAGENT_QA_APP_ID" "")" CFG_HIAGENT_QA_APP_ID
                if [ -n "$CFG_HIAGENT_QA_APP_ID" ]; then
                    break
                fi
                echo "  [ERROR] HiAgent 问答 App ID 不能为空（已提供 API Key）"
            done
            # 校验：问答凭证必须与改写凭证完全不同
            if [ -n "$CFG_HIAGENT_KEY" ] && [ "$CFG_HIAGENT_QA_KEY" = "$CFG_HIAGENT_KEY" ]; then
                echo "  [WARN] 问答 API Key 与改写 API Key 相同，违反"两套完全不同"的应用隔离要求。"
                echo "         请在 HiAgent 控制台创建独立的问答应用后重新填入。"
            fi
            if [ -n "$CFG_HIAGENT_APP_ID" ] && [ "$CFG_HIAGENT_QA_APP_ID" = "$CFG_HIAGENT_APP_ID" ]; then
                echo "  [WARN] 问答 App ID 与改写 App ID 相同，违反应用隔离要求。"
            fi
        else
            CFG_HIAGENT_QA_APP_ID=""
        fi
    else
        CFG_OPENROUTER_KEY=""
        CFG_HIAGENT_KEY=""
        CFG_HIAGENT_APP_ID=""
        CFG_HIAGENT_QA_KEY=""
        CFG_HIAGENT_QA_APP_ID=""
    fi

    echo ""
    echo "  [ 端口映射 ]"
    ask_port "前端端口" "$(load_env_value "PORT_FRONTEND" "80")" CFG_PORT_FE
    ask_port "后端端口" "$(load_env_value "PORT_BACKEND" "8080")" CFG_PORT_BE
    ask_port "MySQL 端口" "$(load_env_value "PORT_MYSQL" "3306")" CFG_PORT_DB

    # 提前计算服务器 IP（HTTPS 和 CORS 配置都需要）
    SERVER_IP_HINT=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "")

    echo ""
    echo "  [ HTTPS/SSL ]"
    DEF_HTTPS=$(load_env_value "HTTPS_ENABLED" "false")
    HTTPS_DEFAULT="n"; [ "$DEF_HTTPS" = "true" ] && HTTPS_DEFAULT="y"
    ask_yn "启用 HTTPS？" "$HTTPS_DEFAULT" CFG_HTTPS_YN
    if [ "$CFG_HTTPS_YN" = true ]; then
        CFG_HTTPS_ENABLED="true"
        echo ""
        ask_choice "SSL 证书方案:" "Let's Encrypt (需公网域名+DNS)|自签名证书 (内网/测试推荐)|已有证书 (手动指定路径)" SSL_MODE 2

        DEF_SSL_DOMAIN=$(load_env_value "SSL_DOMAIN" "${SERVER_IP_HINT:-localhost}")

        case $SSL_MODE in
            1)  # Let's Encrypt（需公网域名）
                ask_value "域名 (如 example.com)" "$DEF_SSL_DOMAIN" CFG_SSL_DOMAIN
                while [ -z "$CFG_SSL_DOMAIN" ]; do
                    echo "  [ERROR] Let's Encrypt 必须提供有效域名"
                    ask_value "域名" "" CFG_SSL_DOMAIN
                done
                CFG_SSL_CERT="/etc/letsencrypt/live/${CFG_SSL_DOMAIN}/fullchain.pem"
                CFG_SSL_KEY="/etc/letsencrypt/live/${CFG_SSL_DOMAIN}/privkey.pem"
                echo ""
                echo "  [提示] 部署前请先在服务器上获取证书:"
                echo "    sudo apt install -y certbot"
                echo "    sudo certbot certonly --standalone -d ${CFG_SSL_DOMAIN}"
                echo "  证书自动续期将在部署完成后自动配置 crontab"
                ;;
            2)  # 自签名证书（内网推荐）
                ask_value "域名或服务器 IP" "$DEF_SSL_DOMAIN" CFG_SSL_DOMAIN
                SSL_DIR="$DOCKER_DIR/ssl"
                # .env 使用相对路径（相对于 docker-compose.yml 所在目录）
                CFG_SSL_CERT="./ssl/fullchain.pem"
                CFG_SSL_KEY="./ssl/privkey.pem"

                if [ -f "$SSL_DIR/fullchain.pem" ] && [ -f "$SSL_DIR/privkey.pem" ]; then
                    echo "  [INFO] 检测到已有自签名证书"
                    ask_yn "重新生成？" "n" REGEN_CERT
                else
                    REGEN_CERT="true"
                fi

                if [ "$REGEN_CERT" = true ]; then
                    if ! command -v openssl &>/dev/null; then
                        echo "  [ERROR] 未安装 openssl，无法生成证书"
                        echo "  安装: sudo apt install -y openssl"
                        CFG_HTTPS_ENABLED="false"
                    else
                        echo "  [INFO] 生成自签名证书..."
                        mkdir -p "$SSL_DIR"
                        # 检测是 IP 地址还是主机名
                        if [[ "$CFG_SSL_DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
                            SAN="IP:${CFG_SSL_DOMAIN}"
                        else
                            SAN="DNS:${CFG_SSL_DOMAIN}"
                        fi
                        if openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
                            -keyout "$SSL_DIR/privkey.pem" \
                            -out "$SSL_DIR/fullchain.pem" \
                            -subj "/C=CN/ST=Local/L=Local/O=LangHack/CN=${CFG_SSL_DOMAIN}" \
                            -addext "subjectAltName=${SAN}" \
                            2>/dev/null; then
                            chmod 600 "$SSL_DIR/privkey.pem"
                            echo "  [OK] 自签名证书已生成 (有效期 10 年)"
                            echo "  [注意] 浏览器会显示安全警告，可手动信任证书"
                        else
                            echo "  [ERROR] 证书生成失败"
                            CFG_HTTPS_ENABLED="false"
                        fi
                    fi
                fi
                ;;
            3)  # 已有证书
                ask_value "域名或服务器 IP" "$DEF_SSL_DOMAIN" CFG_SSL_DOMAIN
                DEF_SSL_CERT=$(load_env_value "SSL_CERT_PATH" "")
                DEF_SSL_KEY=$(load_env_value "SSL_KEY_PATH" "")
                ask_value "证书文件路径 (fullchain.pem)" "$DEF_SSL_CERT" CFG_SSL_CERT
                while [ -z "$CFG_SSL_CERT" ]; do
                    echo "  [ERROR] 证书路径不能为空"
                    ask_value "证书文件路径" "" CFG_SSL_CERT
                done
                ask_value "私钥文件路径 (privkey.pem)" "$DEF_SSL_KEY" CFG_SSL_KEY
                while [ -z "$CFG_SSL_KEY" ]; do
                    echo "  [ERROR] 私钥路径不能为空"
                    ask_value "私钥文件路径" "" CFG_SSL_KEY
                done
                [ ! -f "$CFG_SSL_CERT" ] && echo "  [WARN] 证书文件暂不存在: $CFG_SSL_CERT (请在部署前放置)"
                [ ! -f "$CFG_SSL_KEY" ]  && echo "  [WARN] 私钥文件暂不存在: $CFG_SSL_KEY (请在部署前放置)"
                ;;
        esac

        if [ "$CFG_HTTPS_ENABLED" = "true" ]; then
            ask_port "HTTPS 端口" "$(load_env_value "PORT_HTTPS" "443")" CFG_PORT_HTTPS
        fi
    else
        CFG_HTTPS_ENABLED="false"
        CFG_SSL_DOMAIN=""
        CFG_SSL_CERT=""
        CFG_SSL_KEY=""
        CFG_PORT_HTTPS=""
    fi

    echo ""
    echo "  [ CORS 跨域 ]"
    DEF_CORS=$(load_env_value "APP_CORS_ALLOWED_ORIGINS" "")
    CORS_DEFAULT="http://localhost,http://localhost:${CFG_PORT_FE}"
    [ -n "$SERVER_IP_HINT" ] && CORS_DEFAULT="${CORS_DEFAULT},http://${SERVER_IP_HINT}:${CFG_PORT_FE}"
    # HTTPS 启用时自动添加 https:// 来源
    if [ "$CFG_HTTPS_ENABLED" = "true" ] && [ -n "$CFG_SSL_DOMAIN" ]; then
        HTTPS_ORIGIN="https://${CFG_SSL_DOMAIN}"
        [ "${CFG_PORT_HTTPS:-443}" != "443" ] && HTTPS_ORIGIN="${HTTPS_ORIGIN}:${CFG_PORT_HTTPS}"
        CORS_DEFAULT="${CORS_DEFAULT},${HTTPS_ORIGIN}"
    fi
    [ -n "$DEF_CORS" ] && CORS_DEFAULT="$DEF_CORS"
    echo "  允许前端访问后端的域名列表（逗号分隔）"
    echo "  本地开发: http://localhost:5173"
    echo "  生产环境: https://your-domain.com"
    ask_value "CORS 允许来源" "$CORS_DEFAULT" CFG_CORS_ORIGINS

    echo ""
    echo "  [ 统一身份认证 (SSO) ]"
    DEF_SSO_ENABLED=$(load_env_value "SSO_ENABLED" "false")
    CFG_SSO_ENABLED="false"
    CFG_SSO_SERVER_URL=$(load_env_value "SSO_CAS_SERVER_URL" "https://ids.imau.edu.cn/authserver")
    if [ "$DEF_SSO_ENABLED" = "true" ]; then
        ask_yn "启用 SSO 统一身份认证？" "y" CFG_SSO_YN
    else
        ask_yn "启用 SSO 统一身份认证？" "n" CFG_SSO_YN
    fi
    if [ "$CFG_SSO_YN" = true ]; then
        CFG_SSO_ENABLED="true"
        ask_value "CAS 服务器地址" "$CFG_SSO_SERVER_URL" CFG_SSO_SERVER_URL

        # APP_BASE_URL：用于构建 SSO 回调地址 {APP_BASE_URL}/api/auth/sso/callback
        echo ""
        echo "  [ 应用地址 (APP_BASE_URL) ]"
        echo "  用于构建 SSO 回调地址: {APP_BASE_URL}/api/auth/sso/callback"
        echo "  需要在统一身份认证平台注册此回调地址"
        DEF_APP_BASE_URL=$(load_env_value "APP_BASE_URL" "")
        if [ -z "$DEF_APP_BASE_URL" ]; then
            # 智能推断默认值：优先 HTTPS 域名，其次 HTTP+IP+端口
            if [ "$CFG_HTTPS_ENABLED" = "true" ] && [ -n "$CFG_SSL_DOMAIN" ]; then
                if [ "${CFG_PORT_HTTPS:-443}" = "443" ]; then
                    DEF_APP_BASE_URL="https://${CFG_SSL_DOMAIN}"
                else
                    DEF_APP_BASE_URL="https://${CFG_SSL_DOMAIN}:${CFG_PORT_HTTPS}"
                fi
            elif [ -n "$SERVER_IP_HINT" ]; then
                if [ "$CFG_PORT_BE" = "80" ]; then
                    DEF_APP_BASE_URL="http://${SERVER_IP_HINT}"
                else
                    DEF_APP_BASE_URL="http://${SERVER_IP_HINT}:${CFG_PORT_BE}"
                fi
            else
                DEF_APP_BASE_URL="http://localhost:${CFG_PORT_BE}"
            fi
        fi
        ask_value "应用基础 URL" "$DEF_APP_BASE_URL" CFG_APP_BASE_URL
        # 去除末尾斜杠
        CFG_APP_BASE_URL="${CFG_APP_BASE_URL%/}"

        # SSO_CAS_SERVER_NAME：对应官方 web.xml 的 serverName（固定配置，不从请求头推断）
        echo ""
        echo "  [ CAS Server Name (应用服务器地址) ]"
        echo "  对应官方 web.xml 的 serverName 参数"
        echo "  格式: 协议://域名[:端口]  示例: https://langhack.imau.edu.cn"
        echo "  service URL = serverName + /api/auth/sso/callback"
        DEF_CAS_SERVER_NAME=$(load_env_value "SSO_CAS_SERVER_NAME" "$CFG_APP_BASE_URL")
        ask_value "CAS Server Name" "$DEF_CAS_SERVER_NAME" CFG_CAS_SERVER_NAME
        CFG_CAS_SERVER_NAME="${CFG_CAS_SERVER_NAME%/}"

        echo ""
        echo "  [ 管理员叩门入口 ]"
        echo "  SSO 启用后，管理员/运维需通过隐藏入口登录"
        DEF_ADMIN_SECRET=$(load_env_value "ADMIN_LOGIN_SECRET" "")
        if [ -n "$DEF_ADMIN_SECRET" ]; then
            ask_secret "叩门密钥 (回车保留当前)" "$DEF_ADMIN_SECRET" CFG_ADMIN_SECRET
        else
            # 自动生成随机密钥（openssl rand 跨平台兼容，避免 tr/urandom 在 Windows 下异常）
            CFG_ADMIN_SECRET=$(openssl rand -hex 16 2>/dev/null || LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom 2>/dev/null | head -c 32 || echo "knock$(date +%s)")
            echo "  已自动生成叩门密钥: ${CFG_ADMIN_SECRET}"
            ask_secret "叩门密钥 (回车使用自动生成)" "$CFG_ADMIN_SECRET" CFG_ADMIN_SECRET
        fi
        DEF_ADMIN_PATH=$(load_env_value "ADMIN_PORTAL_PATH" "")
        if [ -n "$DEF_ADMIN_PATH" ]; then
            ask_value "管理员入口路径" "$DEF_ADMIN_PATH" CFG_ADMIN_PATH
        else
            # 自动生成随机入口路径（openssl rand 跨平台兼容）
            CFG_ADMIN_PATH=$(openssl rand -hex 6 2>/dev/null || LC_ALL=C tr -dc 'a-z0-9' </dev/urandom 2>/dev/null | head -c 12 || echo "portal$(date +%s)")
            echo "  已自动生成入口路径: /${CFG_ADMIN_PATH}"
            ask_value "管理员入口路径 (不含前导/)" "$CFG_ADMIN_PATH" CFG_ADMIN_PATH
        fi
        # 去除用户可能输入的前导 /
        CFG_ADMIN_PATH="${CFG_ADMIN_PATH#/}"
    else
        CFG_ADMIN_SECRET=""
        CFG_ADMIN_PATH=""
    fi

    echo ""
    echo "  [ 学校数据集成 ]"
    echo "  (教务数据接入：行政班、课表、教学计划等)"
    DEF_SCHOOL_SYNC=$(load_env_value "SCHOOL_SYNC_ENABLED" "false")
    SCHOOL_DEFAULT="n"; [ "$DEF_SCHOOL_SYNC" = "true" ] && SCHOOL_DEFAULT="y"
    ask_yn "启用学校数据集成？" "$SCHOOL_DEFAULT" CFG_SCHOOL_YN
    # 初始化所有变量为空（禁用时清空）
    CFG_SCHOOL_DATA_SOURCE=""
    CFG_SCHOOL_APP_ID=""
    CFG_SCHOOL_APP_SECRET=""
    CFG_SCHOOL_ETL_JDBC_URL=""
    CFG_SCHOOL_ETL_USERNAME=""
    CFG_SCHOOL_ETL_PASSWORD=""
    CFG_SCHOOL_SYNC_ENABLED="false"
    CFG_SCHOOL_FEATURE_ENABLED="false"
    if [ "$CFG_SCHOOL_YN" = true ]; then
        DEF_SOURCE=$(load_env_value "SCHOOL_DATA_SOURCE" "api")
        SOURCE_DEFAULT_IDX=1; [ "$DEF_SOURCE" = "etl" ] && SOURCE_DEFAULT_IDX=2
        ask_choice "数据源类型:" "API 接口 (HTTPS+签名，推荐)|ETL 数据库 (JDBC 直连)" SCHOOL_SRC_MODE "$SOURCE_DEFAULT_IDX"
        case $SCHOOL_SRC_MODE in
            1)
                CFG_SCHOOL_DATA_SOURCE="api"
                echo "  [提示] 9 个数据接口 URL 已在代码中内置 (cdsp.imau.edu.cn)"
                echo "         认证方式: sign = Base64(MD5(appId + appSecret + timestamp))"
                DEF_APP_ID=$(load_env_value "SCHOOL_API_APP_ID" "")
                ask_value "App ID" "$DEF_APP_ID" CFG_SCHOOL_APP_ID
                DEF_APP_SECRET=$(load_env_value "SCHOOL_API_APP_SECRET" "")
                ask_secret "App Secret" "$DEF_APP_SECRET" CFG_SCHOOL_APP_SECRET
                ;;
            2)
                CFG_SCHOOL_DATA_SOURCE="etl"
                echo "  [提示] 7 个查询 SQL 已在代码中内置，只需配置数据库连接"
                DEF_JDBC_URL=$(load_env_value "SCHOOL_ETL_JDBC_URL" "jdbc:mysql://school-db.imau.edu.cn:3306/school_db")
                ask_value "JDBC URL" "$DEF_JDBC_URL" CFG_SCHOOL_ETL_JDBC_URL
                DEF_ETL_USER=$(load_env_value "SCHOOL_ETL_USERNAME" "")
                ask_value "数据库用户名" "$DEF_ETL_USER" CFG_SCHOOL_ETL_USERNAME
                DEF_ETL_PASS=$(load_env_value "SCHOOL_ETL_PASSWORD" "")
                ask_secret "数据库密码" "$DEF_ETL_PASS" CFG_SCHOOL_ETL_PASSWORD
                ;;
        esac
        echo ""
        echo "  [提示] 定时同步计划（默认）: 全量 02:00 / 增量每小时 / 校验 04:00"
        ask_yn "启用定时同步？" "y" SCHOOL_SYNC_YN
        [ "$SCHOOL_SYNC_YN" = true ] && CFG_SCHOOL_SYNC_ENABLED="true"
        echo ""
        echo "  [提示] 启用功能开关后，运维端显示'数据浏览/数据配置'，学生端显示'课表'"
        ask_yn "启用功能开关（运维端+学生端入口）？" "y" SCHOOL_FEATURE_YN
        [ "$SCHOOL_FEATURE_YN" = true ] && CFG_SCHOOL_FEATURE_ENABLED="true"
    fi

    echo ""
    echo "  [ Docker 镜像源 ]"
    DEF_IMAGE_SOURCE_MODE=$(load_env_value "IMAGE_SOURCE_MODE" "auto")
    case "${DEF_IMAGE_SOURCE_MODE,,}" in
        auto) IMAGE_SOURCE_DEFAULT_IDX=1 ;;
        public) IMAGE_SOURCE_DEFAULT_IDX=2 ;;
        local) IMAGE_SOURCE_DEFAULT_IDX=3 ;;
        *) IMAGE_SOURCE_DEFAULT_IDX=1 ;;
    esac
    ask_choice "镜像源模式:" "自动降级 (公共不可达时切换本地 registry)|强制公共镜像|强制本地 registry" IMAGE_SOURCE_CHOICE "$IMAGE_SOURCE_DEFAULT_IDX"
    case "$IMAGE_SOURCE_CHOICE" in
        1) CFG_IMAGE_SOURCE_MODE="auto" ;;
        2) CFG_IMAGE_SOURCE_MODE="public" ;;
        3) CFG_IMAGE_SOURCE_MODE="local" ;;
    esac
    ask_value "本地 registry 地址" "$(load_env_value "LOCAL_REGISTRY" "localhost:5000")" CFG_LOCAL_REGISTRY
    ask_value "公共镜像探测镜像" "$(load_env_value "PUBLIC_IMAGE_PROBE" "node:20-alpine")" CFG_PUBLIC_IMAGE_PROBE
    CFG_IMAGE_REGISTRY_PREFIX=""

    echo ""
    echo "  [ 构建网络环境 ]"
    echo "  决定 Docker 构建阶段如何下载依赖（npm 包、Maven 包、Alpine apk）"
    DEF_BUILD_ENV=$(load_env_value "BUILD_NETWORK_ENV" "public")
    case "${DEF_BUILD_ENV,,}" in
        intranet|local) BUILD_ENV_DEFAULT_IDX=2 ;;
        *) BUILD_ENV_DEFAULT_IDX=1 ;;
    esac
    ask_choice "构建网络环境:" "公网构建 (依赖从公网 npm/Maven Central 下载)|内网构建 (依赖从内网 Nexus 代理下载)" BUILD_ENV_CHOICE "$BUILD_ENV_DEFAULT_IDX"
    CFG_NPM_REGISTRY=""
    CFG_MAVEN_MIRROR_URL=""
    CFG_ALPINE_MIRROR=""
    CFG_BUILD_NETWORK_ENV="public"
    if [ "$BUILD_ENV_CHOICE" = "2" ]; then
        CFG_BUILD_NETWORK_ENV="intranet"
        echo ""
        echo "  [提示] 内网 Nexus 通常为 http://172.20.14.249:8081（或实际内网地址）"
        echo "  [提示] 留空则该依赖类型仍走公网（可按需只配其中几项）"
        echo ""
        DEF_NPM=$(load_env_value "NPM_REGISTRY" "")
        if [ -z "$DEF_NPM" ]; then
            _local_reg=$(load_env_value "LOCAL_REGISTRY" "")
            _extracted_ip=$(echo "$_local_reg" | sed 's/:[0-9]*//' | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' || true)
            if [ -n "$_extracted_ip" ]; then
                DEF_NPM="http://${_extracted_ip}:8081/repository/npm-proxy/"
            fi
        fi
        ask_value "npm 内网镜像源 URL (留空跳过)" "$(load_env_value "NPM_REGISTRY" "${DEF_NPM:-http://172.20.14.249:8081/repository/npm-proxy/}")" CFG_NPM_REGISTRY
        ask_value "Maven 内网镜像源 URL (留空跳过)" "$(load_env_value "MAVEN_MIRROR_URL" "http://172.20.14.249:8081/repository/maven-public/")" CFG_MAVEN_MIRROR_URL
        ask_value "Alpine apk 内网镜像源 URL (留空跳过)" "$(load_env_value "ALPINE_MIRROR" "http://172.20.14.249:8081/repository/alpine-mirror/")" CFG_ALPINE_MIRROR
        echo ""
        echo "  [配置汇总]"
        [ -n "$CFG_NPM_REGISTRY" ]     && echo "    npm      → $CFG_NPM_REGISTRY"     || echo "    npm      → (跳过，使用公网)"
        [ -n "$CFG_MAVEN_MIRROR_URL" ] && echo "    Maven    → $CFG_MAVEN_MIRROR_URL" || echo "    Maven    → (跳过，使用公网)"
        [ -n "$CFG_ALPINE_MIRROR" ]    && echo "    Alpine   → $CFG_ALPINE_MIRROR"    || echo "    Alpine   → (跳过，使用公网)"
    fi

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
        [ -n "$CFG_HIAGENT_QA_KEY" ]    && printf 'HIAGENT_QA_API_KEY=%s\n' "$CFG_HIAGENT_QA_KEY"    || printf '# HIAGENT_QA_API_KEY=\n'
        [ -n "$CFG_HIAGENT_QA_APP_ID" ] && printf 'HIAGENT_QA_APP_ID=%s\n' "$CFG_HIAGENT_QA_APP_ID" || printf '# HIAGENT_QA_APP_ID=\n'
        printf 'MYSQL_ROOT_PASSWORD=%s\n'          "$CFG_DB_PASSWORD"
        printf 'MYSQL_DATABASE=english_learning\n'
        printf 'MYSQL_REPLICATION_USER=replicator\n'
        printf 'MYSQL_REPLICATION_PASSWORD=%s_repl\n' "$CFG_DB_PASSWORD"
        # 若应用用户不是 root，写入 MYSQL_APP_USER/PASSWORD 供 init-db.sh 自动创建
        if [ "$CFG_DB_USERNAME" != "root" ]; then
            printf 'MYSQL_APP_USER=%s\n'     "$CFG_DB_USERNAME"
            printf 'MYSQL_APP_PASSWORD=%s\n' "$CFG_DB_PASSWORD"
        fi
        printf 'DB_URL=jdbc:mysql://mysql-master:3306/english_learning?useSSL=false&serverTimezone=GMT%%2B8&allowPublicKeyRetrieval=true\n'
        printf 'DB_SLAVE_URL=jdbc:mysql://mysql-slave:3306/english_learning?useSSL=false&serverTimezone=GMT%%2B8&allowPublicKeyRetrieval=true\n'
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
        printf 'IMAGE_SOURCE_MODE=%s\n' "${CFG_IMAGE_SOURCE_MODE:-auto}"
        printf 'LOCAL_REGISTRY=%s\n' "${CFG_LOCAL_REGISTRY:-localhost:5000}"
        printf 'IMAGE_REGISTRY_PREFIX=%s\n' "${CFG_IMAGE_REGISTRY_PREFIX:-}"
        printf 'PUBLIC_IMAGE_PROBE=%s\n' "${CFG_PUBLIC_IMAGE_PROBE:-node:20-alpine}"
        printf 'BUILD_NETWORK_ENV=%s\n' "${CFG_BUILD_NETWORK_ENV:-public}"
        [ -n "${CFG_NPM_REGISTRY:-}" ]     && printf 'NPM_REGISTRY=%s\n'     "$CFG_NPM_REGISTRY"     || printf '# NPM_REGISTRY=\n'
        [ -n "${CFG_MAVEN_MIRROR_URL:-}" ] && printf 'MAVEN_MIRROR_URL=%s\n' "$CFG_MAVEN_MIRROR_URL" || printf '# MAVEN_MIRROR_URL=\n'
        [ -n "${CFG_ALPINE_MIRROR:-}" ]    && printf 'ALPINE_MIRROR=%s\n'    "$CFG_ALPINE_MIRROR"    || printf '# ALPINE_MIRROR=\n'
        printf 'BACKUP_ENABLED=%s\n'     "$CFG_BACKUP_ENABLED"
        printf 'BACKUP_DIR=%s\n'         "$CFG_BACKUP_DIR"
        printf 'BACKUP_RETENTION_DAYS=%s\n' "$CFG_BACKUP_RETENTION"
        printf 'AUTO_UPDATE_ENABLED=%s\n'     "$CFG_AUTO_UPDATE_ENABLED"
        printf 'AUTO_UPDATE_CRON=%s\n'        "$CFG_AUTO_UPDATE_CRON"
        printf 'AUTO_UPDATE_BRANCH=%s\n'      "$CFG_AUTO_UPDATE_BRANCH"
        printf 'AUTO_UPDATE_MAINTENANCE=%s\n' "$CFG_AUTO_UPDATE_MAINTENANCE"
        printf 'AUTO_UPDATE_PRE_BACKUP=%s\n'  "$CFG_AUTO_UPDATE_PRE_BACKUP"
        # HTTPS/SSL
        printf 'HTTPS_ENABLED=%s\n' "$CFG_HTTPS_ENABLED"
        if [ "$CFG_HTTPS_ENABLED" = "true" ]; then
            printf 'PORT_HTTPS=%s\n'     "$CFG_PORT_HTTPS"
            printf 'SSL_CERT_PATH=%s\n'  "$CFG_SSL_CERT"
            printf 'SSL_KEY_PATH=%s\n'   "$CFG_SSL_KEY"
            printf 'SSL_DOMAIN=%s\n'     "$CFG_SSL_DOMAIN"
        fi
        printf 'SSO_ENABLED=%s\n'          "$CFG_SSO_ENABLED"
        printf 'SSO_PROTOCOL=CAS\n'
        printf 'SSO_CAS_ENABLED=%s\n'      "$CFG_SSO_ENABLED"
        printf 'SSO_CAS_SERVER_URL=%s\n'   "$CFG_SSO_SERVER_URL"
        if [ "$CFG_SSO_ENABLED" = "true" ]; then
            printf 'APP_BASE_URL=%s\n'          "${CFG_APP_BASE_URL:-}"
            printf 'SSO_CAS_SERVER_NAME=%s\n'   "${CFG_CAS_SERVER_NAME:-}"
            printf 'ADMIN_LOGIN_SECRET=%s\n'     "$CFG_ADMIN_SECRET"
            printf 'ADMIN_PORTAL_PATH=%s\n'   "$CFG_ADMIN_PATH"
        fi
        # ── 学校数据集成 ──────────────────────────────────────
        if [ "$CFG_SCHOOL_YN" = true ]; then
            printf 'SCHOOL_DATA_SOURCE=%s\n'   "$CFG_SCHOOL_DATA_SOURCE"
            if [ "$CFG_SCHOOL_DATA_SOURCE" = "api" ]; then
                printf 'SCHOOL_API_APP_ID=%s\n'     "$CFG_SCHOOL_APP_ID"
                printf 'SCHOOL_API_APP_SECRET=%s\n' "$CFG_SCHOOL_APP_SECRET"
            fi
            if [ "$CFG_SCHOOL_DATA_SOURCE" = "etl" ]; then
                printf 'SCHOOL_ETL_JDBC_URL=%s\n'   "$CFG_SCHOOL_ETL_JDBC_URL"
                printf 'SCHOOL_ETL_USERNAME=%s\n'   "$CFG_SCHOOL_ETL_USERNAME"
                printf 'SCHOOL_ETL_PASSWORD=%s\n'   "$CFG_SCHOOL_ETL_PASSWORD"
            fi
            printf 'SCHOOL_SYNC_ENABLED=%s\n' "$CFG_SCHOOL_SYNC_ENABLED"
        else
            printf '# 学校数据集成未启用（可在运维面板 → 学校数据配置中手动启用）\n'
            printf 'SCHOOL_SYNC_ENABLED=false\n'
        fi
    } > "$ENV_FILE"
    # 设置 .env 文件权限为 600（仅所有者可读写，保护敏感信息）
    chmod 600 "$ENV_FILE" 2>/dev/null || true
    echo "  [OK] 配置已写入 $ENV_FILE"

    # ── 生成/清理 HTTPS docker-compose override ──
    OVERRIDE_FILE="$DOCKER_DIR/docker-compose.override.yml"
    if [ "$CFG_HTTPS_ENABLED" = "true" ]; then
        cat > "$OVERRIDE_FILE" << 'HTTPS_OVERRIDE'
# HTTPS 配置 (由部署向导自动生成，请勿手动编辑)
# 修改证书路径请编辑 .env 中的 SSL_CERT_PATH / SSL_KEY_PATH
services:
  frontend:
    ports:
      - "${PORT_HTTPS:-443}:443"
    volumes:
      - ${SSL_CERT_PATH}:/etc/nginx/ssl/fullchain.pem:ro
      - ${SSL_KEY_PATH}:/etc/nginx/ssl/privkey.pem:ro
      - ../frontend/nginx-ssl.conf:/etc/nginx/conf.d/default.conf:ro
HTTPS_OVERRIDE
        echo "  [OK] HTTPS docker-compose override 已生成"
    else
        if [ -f "$OVERRIDE_FILE" ]; then
            rm -f "$OVERRIDE_FILE"
            echo "  [INFO] 已移除 HTTPS override (HTTPS 未启用)"
        fi
    fi
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
HTTPS_ENABLED_VAL=$(load_env_value "HTTPS_ENABLED" "false" "$ENV_FILE")
PORT_HTTPS_VAL=$(load_env_value "PORT_HTTPS" "443" "$ENV_FILE")
SSL_DOMAIN_VAL=$(load_env_value "SSL_DOMAIN" "" "$ENV_FILE")
IMAGE_SOURCE_MODE_VAL="${IMAGE_SOURCE_MODE:-$(load_env_value "IMAGE_SOURCE_MODE" "auto" "$ENV_FILE")}"
LOCAL_REGISTRY_VAL="${LOCAL_REGISTRY:-$(load_env_value "LOCAL_REGISTRY" "localhost:5000" "$ENV_FILE")}"
PUBLIC_IMAGE_PROBE_VAL="${PUBLIC_IMAGE_PROBE:-$(load_env_value "PUBLIC_IMAGE_PROBE" "node:20-alpine" "$ENV_FILE")}"
echo "  [OK] 环境变量检查通过"

echo ""
echo "  [ Docker 镜像源 ]"
resolve_image_registry_prefix "$IMAGE_SOURCE_MODE_VAL" "$LOCAL_REGISTRY_VAL" "$PUBLIC_IMAGE_PROBE_VAL"
set_env_value "$ENV_FILE" "IMAGE_REGISTRY_PREFIX" "$RESOLVED_IMAGE_REGISTRY_PREFIX"

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
if [ "${CFG_HTTPS_ENABLED:-$HTTPS_ENABLED_VAL}" = "true" ]; then
    echo "  HTTPS: 已启用 (${CFG_SSL_DOMAIN:-$SSL_DOMAIN_VAL}:${CFG_PORT_HTTPS:-$PORT_HTTPS_VAL})"
else
    echo "  HTTPS: 未启用"
fi
if [ "${CFG_AUTO_UPDATE_ENABLED:-false}" = "true" ]; then
    echo "  自动更新: 已启用 (${CFG_AUTO_UPDATE_CRON}) → ${CFG_AUTO_UPDATE_BRANCH}"
else
    echo "  自动更新: 未启用"
fi
echo "  Docker 镜像源: ${RESOLVED_IMAGE_SOURCE_LABEL:-$(load_env_value "IMAGE_REGISTRY_PREFIX" "" "$ENV_FILE")}"
_build_env_label="${CFG_BUILD_NETWORK_ENV:-$(load_env_value "BUILD_NETWORK_ENV" "public" "$ENV_FILE")}"
if [ "${_build_env_label}" = "intranet" ]; then
    echo "  构建网络: 内网 (Nexus 代理)"
    _npm=$([ -n "${CFG_NPM_REGISTRY:-}" ] && echo "$CFG_NPM_REGISTRY" || load_env_value "NPM_REGISTRY" "" "$ENV_FILE")
    _mvn=$([ -n "${CFG_MAVEN_MIRROR_URL:-}" ] && echo "$CFG_MAVEN_MIRROR_URL" || load_env_value "MAVEN_MIRROR_URL" "" "$ENV_FILE")
    _apk=$([ -n "${CFG_ALPINE_MIRROR:-}" ] && echo "$CFG_ALPINE_MIRROR" || load_env_value "ALPINE_MIRROR" "" "$ENV_FILE")
    [ -n "$_npm" ] && echo "    npm    → $_npm" || echo "    npm    → (公网)"
    [ -n "$_mvn" ] && echo "    Maven  → $_mvn" || echo "    Maven  → (公网)"
    [ -n "$_apk" ] && echo "    Alpine → $_apk" || echo "    Alpine → (公网)"
else
    echo "  构建网络: 公网"
fi
if [ "${CFG_SSO_ENABLED:-false}" = "true" ]; then
    echo "  SSO: 已启用 (${CFG_SSO_SERVER_URL})"
    echo "  应用地址 (APP_BASE_URL): ${CFG_APP_BASE_URL}"
    echo "  回调地址: ${CFG_APP_BASE_URL}/api/auth/sso/callback"
    echo "  管理员入口: /${CFG_ADMIN_PATH}"
else
    echo "  SSO: 未启用"
fi
if [ "${CFG_SCHOOL_YN:-false}" = true ]; then
    echo "  学校数据: 已启用 (${CFG_SCHOOL_DATA_SOURCE})"
    if [ "$CFG_SCHOOL_DATA_SOURCE" = "api" ]; then
        echo "    认证方式: appId + appSecret 签名"
        [ -n "$CFG_SCHOOL_APP_ID" ] && echo "    App ID: ${CFG_SCHOOL_APP_ID}" || echo "    App ID: (未配置)"
    elif [ "$CFG_SCHOOL_DATA_SOURCE" = "etl" ]; then
        echo "    JDBC: ${CFG_SCHOOL_ETL_JDBC_URL}"
    fi
    echo "    定时同步: ${CFG_SCHOOL_SYNC_ENABLED}"
    echo "    功能开关: ${CFG_SCHOOL_FEATURE_ENABLED}"
else
    echo "  学校数据: 未启用"
fi

ask_yn "开始部署？" "y" GO
[ "$GO" = false ] && echo "已取消" && exit 0

# ══════════════════════════════════════════════════════
# 步骤 5: 执行
# ══════════════════════════════════════════════════════
echo ""
echo "── 步骤 5/5: 执行部署 ──"

# 切换分支 & 拉取最新代码
CURRENT=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
OLD_BRANCH="$CURRENT"
OLD_COMMIT=$(git rev-parse HEAD 2>/dev/null || echo "")

# 检查 unstaged 和 staged 变更
if ! git diff --quiet 2>/dev/null || ! git diff --cached --quiet 2>/dev/null; then
    echo "[INFO] 保存本地未提交的修改..."
    git stash push -m "deploy-$(date +%s)" --quiet
    STASHED=true
fi

if [ "$TARGET_BRANCH" != "$CURRENT" ]; then
    echo "[INFO] 切换分支 $CURRENT → $TARGET_BRANCH..."
    if ! git checkout "$TARGET_BRANCH" --quiet 2>/dev/null; then
        echo "[ERROR] 无法切换到分支 $TARGET_BRANCH，请检查分支是否存在"
        exit 1
    fi
elif [ "$CURRENT" = "HEAD" ]; then
    # 游离 HEAD 状态：直接切换到目标分支
    echo "[INFO] 当前处于游离 HEAD 状态，切换到分支 $TARGET_BRANCH..."
    if ! git checkout "$TARGET_BRANCH" --quiet 2>/dev/null; then
        echo "[ERROR] 无法切换到分支 $TARGET_BRANCH，请检查分支是否存在"
        exit 1
    fi
fi

# 尝试拉取远程最新代码（带超时和离线降级）
if [ "$OFFLINE_MODE" = true ]; then
    echo "[INFO] 离线模式已启用，跳过 git pull"
    echo "[INFO] 使用本地当前代码进行部署"

    # 检查是否有未提交的更改
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        echo "[WARN] 检测到未提交的本地更改，将包含在部署中"
    fi
else
    echo "[INFO] 拉取远程最新代码..."
    PULL_TIMEOUT=60
    PULL_SUCCESS=false

    if command -v timeout >/dev/null 2>&1; then
        if timeout "$PULL_TIMEOUT" git pull origin "$TARGET_BRANCH" --ff-only --quiet 2>/dev/null; then
            PULL_SUCCESS=true
            echo "[OK] 远程代码拉取成功"
        fi
    else
        if git pull origin "$TARGET_BRANCH" --ff-only --quiet 2>/dev/null; then
            PULL_SUCCESS=true
            echo "[OK] 远程代码拉取成功"
        fi
    fi

    if [ "$PULL_SUCCESS" = false ]; then
        echo "[WARN] 无法从远程拉取 $TARGET_BRANCH（网络故障或超时）"

        # 检查本地是否有 origin/$TARGET_BRANCH 引用
        if git rev-parse "origin/$TARGET_BRANCH" >/dev/null 2>&1; then
            LOCAL_HEAD=$(git rev-parse HEAD)
            CACHED_REMOTE=$(git rev-parse "origin/$TARGET_BRANCH")

            if [ "$LOCAL_HEAD" != "$CACHED_REMOTE" ]; then
                echo "[INFO] 检测到本地缓存的远程分支有更新，尝试合并..."
                if git merge "origin/$TARGET_BRANCH" --ff-only --quiet 2>/dev/null; then
                    echo "[OK] 使用本地缓存的远程分支进行部署"
                    echo "[INFO]   本地 HEAD: ${LOCAL_HEAD:0:8}"
                    echo "[INFO]   缓存远程: ${CACHED_REMOTE:0:8}"
                else
                    echo "[WARN] 无法快进合并缓存的远程分支，使用当前本地版本"
                fi
            else
                echo "[INFO] 本地 HEAD 与缓存的远程分支一致，使用当前版本"
            fi
        else
            echo "[WARN] 本地无远程分支引用，使用当前本地版本"
        fi

        echo "[INFO] 离线部署模式：使用本地代码进行部署"
    fi
fi

# 拉取 Git LFS 文件（database/*.sql 等大文件）
if command -v git-lfs &>/dev/null || git lfs version &>/dev/null 2>&1; then
    echo "[INFO] 拉取 Git LFS 文件（database/*.sql）..."
    # 添加 30 秒超时，避免网络故障时长时间卡顿
    if command -v timeout &>/dev/null; then
        if ! timeout 30 git lfs pull 2>/dev/null; then
            echo "[WARN] git lfs pull 失败或超时（可能网络故障或无 LFS 文件），继续部署"
        fi
    else
        # 无 timeout 命令时，后台执行并等待最多 30 秒
        git lfs pull 2>/dev/null &
        LFS_PID=$!
        for i in {1..30}; do
            if ! kill -0 $LFS_PID 2>/dev/null; then
                break
            fi
            sleep 1
        done
        if kill -0 $LFS_PID 2>/dev/null; then
            kill $LFS_PID 2>/dev/null
            echo "[WARN] git lfs pull 超时（30s），继续部署"
        fi
    fi
else
    echo "[WARN] git-lfs 未安装，跳过 LFS 文件拉取"
fi

echo "[INFO] 版本: $(git rev-parse --short HEAD)"

# 检查旧容器残留
check_old_containers

cd "$DOCKER_DIR"

# 服务列表 & 预期健康服务数
# 部署策略：所有服务进程必须 healthy 才算部署成功（任意服务故障则阻塞并报错）
# 注意：从库 healthcheck 只检查 MySQL 进程存活，不检查复制状态
#       复制未配置时从库仍为 healthy，DynamicDataSource 在运行时检测并降级
case $SVC_MODE in
    1) SVCS="";                              expected_healthy=5 ;;  # 全部 5 个服务
    2) SVCS="backend frontend";              expected_healthy=5 ;;  # 依赖链拉起全部
    3) SVCS="backend";                       expected_healthy=4 ;;  # backend + mysql-master + mysql-slave + redis
    4) SVCS="frontend";                      expected_healthy=5 ;;  # 整条依赖链
    5) SVCS="mysql-master mysql-slave redis"; expected_healthy=3 ;; # 3 个基础设施服务
esac

# 构建
BUILD_ONLY=false
case $BUILD_MODE in
    1)
        echo "[INFO] 全量构建（可能需要 5-15 分钟，请耐心等待）..."
        if [ -n "$SVCS" ]; then
            docker compose build --no-cache $SVCS
        else
            docker compose build --no-cache
        fi
        if [ $? -ne 0 ]; then
            echo "[ERROR] 构建失败"
            exit 1
        fi
        echo "[OK] 构建完成 ✓"
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

# ── 健康等待：所有服务必须 healthy，任意 unhealthy 立即失败 ──────────────
# 诊断函数：打印失败服务的日志和排错建议
print_failed_services() {
    echo ""
    echo "======================================"
    docker compose ps
    echo "======================================"
    echo ""
    echo "─── 失败服务日志（最近 50 行）───"
    docker compose ps --format '{{.Service}} {{.Status}}' 2>/dev/null \
      | grep -E 'unhealthy|Exit|exited' | awk '{print $1}' \
      | while read -r svc; do
          echo ""
          echo ">>> [$svc] 日志:"
          docker compose logs --tail=50 "$svc" 2>/dev/null
          echo ""
          echo ">>> [$svc] 排错建议:"
          case "$svc" in
              mysql-master|mysql-slave)
                  echo "    - 检查 MySQL 密码是否正确: docker exec $svc mysqladmin ping -uroot -p\$DB_PASSWORD"
                  echo "    - 检查数据卷权限: docker exec $svc ls -la /var/lib/mysql"
                  echo "    - 查看完整日志: docker compose logs $svc"
                  ;;
              redis)
                  echo "    - 检查 Redis 密码配置: docker exec $svc redis-cli ping"
                  echo "    - 查看完整日志: docker compose logs $svc"
                  ;;
              backend)
                  echo "    - 检查数据库连接: 确认 DB_URL / DB_USERNAME / DB_PASSWORD 正确"
                  echo "    - 检查 JWT_SECRET 是否已设置"
                  echo "    - 查看完整日志: docker compose logs backend"
                  ;;
              frontend)
                  echo "    - 检查 nginx 配置: docker exec $svc nginx -t"
                  echo "    - 查看完整日志: docker compose logs frontend"
                  ;;
          esac
        done
}

# 回滚函数
do_rollback() {
    local old_commit="$1"
    echo "[INFO] 正在回滚到 ${old_commit:0:8}..."
    cd "$PROJECT_ROOT"
    if git checkout "$old_commit" --force --quiet 2>/dev/null; then
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
        echo "[INFO] 已回滚到 ${old_commit:0:8}，请等待服务重新就绪"
    else
        echo "[ERROR] git 回滚失败，请手动处理: git checkout ${old_commit:0:8}"
    fi
}

echo "[INFO] 等待服务就绪 (预期 ${expected_healthy} 个 healthy)..."
timeout=180; elapsed=0; deploy_ok=false; fail_reason=""
while [ $elapsed -lt $timeout ]; do
    # 获取每个服务的名称和状态
    svc_statuses=$(docker compose ps --format '{{.Service}}\t{{.Status}}' 2>/dev/null || true)
    if [ -z "$svc_statuses" ]; then
        sleep 5; elapsed=$((elapsed + 5)); continue
    fi

    # 快速失败：任意服务 unhealthy 或 exited 立即终止
    failed_svcs=$(echo "$svc_statuses" | awk -F'\t' '$2 ~ /unhealthy|Exit [^0]|exited/ {print $1}' || true)
    if [ -n "$failed_svcs" ]; then
        fail_reason="unhealthy"
        echo ""
        echo "[ERROR] 以下服务启动失败，终止部署："
        echo "$failed_svcs" | sed 's/^/         /'
        print_failed_services
        break
    fi

    healthy=$(echo "$svc_statuses" | awk -F'\t' '$2 ~ /healthy/ && $2 !~ /unhealthy/' | wc -l | tr -d ' ')
    if [ "$healthy" -ge "$expected_healthy" ]; then
        deploy_ok=true
        break
    fi

    sleep 5; elapsed=$((elapsed + 5))
    printf "  等待中... %ds (healthy: %d/%d)\r" "$elapsed" "$healthy" "$expected_healthy"
done
echo ""

# 容器健康检查通过后，验证后端 API 可用性
if [ "$deploy_ok" = true ]; then
    echo "[INFO] 验证后端 API 可用性..."
    api_wait=0
    api_ok=false
    while [ $api_wait -lt 60 ]; do
        if curl -sf "http://localhost:${PORT_BACKEND:-8080}/api/health" > /dev/null 2>&1; then
            echo "[OK] 后端 API 健康检查通过"
            api_ok=true
            break
        fi
        sleep 3
        api_wait=$((api_wait + 3))
    done
    if [ "$api_ok" = false ]; then
        echo "[WARN] 后端 API 健康检查超时（60s），但容器状态正常"
        echo "[WARN] 可能原因：应用启动慢、端口映射错误、或 /api/health 端点不可用"
    fi
fi

if [ "$deploy_ok" = false ]; then
    if [ "$fail_reason" != "unhealthy" ]; then
        # 超时：打印当前状态
        echo "[ERROR] 部署超时：服务未能在 ${timeout}s 内全部就绪"
        print_failed_services
    fi
    echo ""
    # 提供回滚选项（交互模式下询问，非交互模式自动回滚）
    if [ -n "$OLD_COMMIT" ]; then
        if [ -t 0 ]; then
            ask_yn "是否回滚到上一个版本 (${OLD_COMMIT:0:8})？" "y" DO_ROLLBACK
            [ "$DO_ROLLBACK" = true ] && do_rollback "$OLD_COMMIT"
        else
            echo "[INFO] 非交互模式，自动回滚到 ${OLD_COMMIT:0:8}"
            do_rollback "$OLD_COMMIT"
        fi
    fi
    exit 1
fi

echo ""
echo "======================================"
docker compose ps
echo "======================================"
echo ""

# 验证数据库初始化
if ! verify_database_init; then
    echo "[ERROR] 数据库初始化验证失败，部署终止"
    exit 1
fi

# ── 数据库迁移与 Schema 验证 ──────────────────────────────────────────
# 策略：版本不同步 → 阻塞；迁移失败 → 阻塞；Schema 缺表 → 阻塞
# 数据源：docker/mysql/init/04-apply-all-migrations.sql（全幂等，V1~Vn）
echo "[INFO] 应用增量数据库迁移..."
DB_ROOT_PW=$(load_env_value "MYSQL_ROOT_PASSWORD" "" "$ENV_FILE")
[ -z "$DB_ROOT_PW" ] && DB_ROOT_PW=$(load_env_value "DB_PASSWORD" "" "$ENV_FILE")
DB_NAME="${MYSQL_DATABASE:-english_learning}"
MASTER_CONTAINER="langhack-mysql-master"
SLAVE_CONTAINER="langhack-mysql-slave"
MIGRATION_SQL="$PROJECT_ROOT/docker/mysql/init/04-apply-all-migrations.sql"
FEATURE_TOGGLE_REPAIR_SQL="$PROJECT_ROOT/scripts/repair-feature-toggle-descriptions.sql"
PREPARED_MIGRATION_SQL=""
FLYWAY_DIR="$PROJECT_ROOT/backend/src/main/resources/db/migration"

# 等待 mysql-master 容器可用（最多 60s）
mc_wait=0
until docker exec "$MASTER_CONTAINER" mysqladmin ping -uroot -p"$DB_ROOT_PW" --silent 2>/dev/null; do
    mc_wait=$((mc_wait + 2)); sleep 2
    if [ $mc_wait -ge 60 ]; then
        echo "[ERROR] mysql-master 在 60s 内未就绪，无法执行数据库迁移，部署终止"
        echo "        排查: docker compose logs mysql-master"
        exit 1
    fi
done

if [ ! -f "$MIGRATION_SQL" ]; then
    echo "[ERROR] 迁移脚本不存在: $MIGRATION_SQL"
    exit 1
fi

# 版本同步强制检查：Flyway 文件数必须与合并脚本一致，否则阻塞部署
flyway_count=$(ls "$FLYWAY_DIR"/V*.sql 2>/dev/null | wc -l | tr -d ' \n\r' || echo 0)
merged_count=$(grep -cE '^-- V[0-9]+__.*\.sql$' "$MIGRATION_SQL" 2>/dev/null | tr -d ' \n\r' || echo 0)
if [ "$flyway_count" -gt "$merged_count" ]; then
    echo "[ERROR] ══════════════════════════════════════════════════════"
    echo "[ERROR] 迁移脚本版本不同步，部署终止"
    echo "[ERROR]   Flyway 文件数: ${flyway_count}"
    echo "[ERROR]   合并脚本版本: ${merged_count}"
    echo "[ERROR] 缺失版本块："
    for f in "$FLYWAY_DIR"/V*.sql; do
        vn=$(basename "$f" | grep -oE '^V[0-9]+' || true)
        grep -q "^--   ${vn}__" "$MIGRATION_SQL" 2>/dev/null || echo "[ERROR]   [缺失] $f"
    done
    echo "[ERROR] 请将新迁移同步到 docker/mysql/init/04-apply-all-migrations.sql 后重新部署"
    echo "[ERROR] ══════════════════════════════════════════════════════"
    exit 1
else
    echo "[OK] 迁移版本同步检查通过（Flyway: ${flyway_count}, 合并脚本: ${merged_count}）"
fi

# 从头部列表提取最高版本号用于日志
max_ver=$(grep -oE '^-- V[0-9]+__' "$MIGRATION_SQL" 2>/dev/null | grep -oE '[0-9]+' | sort -n | tail -1 | tr -d ' \n\r' || echo "?")
echo "[INFO] 执行迁移脚本: docker/mysql/init/04-apply-all-migrations.sql (V1~V${max_ver}, DB: ${DB_NAME})"
PREPARED_MIGRATION_SQL=$(mktemp /tmp/langhack-deploy-migrate.XXXXXX.sql)
if ! bash "$PROJECT_ROOT/scripts/prepare-mysql-cli-sql.sh" "$MIGRATION_SQL" "$PREPARED_MIGRATION_SQL"; then
    echo "[ERROR] 迁移脚本预处理失败"
    rm -f "$PREPARED_MIGRATION_SQL" 2>/dev/null || true
    exit 1
fi
MIGRATE_LOG=$(mktemp /tmp/langhack-deploy-migrate-output.XXXXXX.log)
docker exec -i "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" "$DB_NAME" < "$PREPARED_MIGRATION_SQL" > "$MIGRATE_LOG" 2>&1 &
MIGRATE_PID=$!
MIGRATE_ELAPSED=0
while kill -0 "$MIGRATE_PID" 2>/dev/null; do
    sleep 15
    if kill -0 "$MIGRATE_PID" 2>/dev/null; then
        MIGRATE_ELAPSED=$((MIGRATE_ELAPSED + 15))
        MIGRATE_ACTIVITY=$(bash "$PROJECT_ROOT/scripts/describe-mysql-activity.sh" "$MASTER_CONTAINER" "$DB_ROOT_PW" "$DB_NAME" || true)
        if [ -n "$MIGRATE_ACTIVITY" ]; then
            echo "[INFO] MIGRATE: ${MIGRATE_ACTIVITY} (已耗时 ${MIGRATE_ELAPSED}s)"
        else
            echo "[INFO] MIGRATE: 正在执行数据库迁移 (已耗时 ${MIGRATE_ELAPSED}s)"
        fi
    fi
done
if wait "$MIGRATE_PID"; then
    MIGRATE_EXIT=0
else
    MIGRATE_EXIT=$?
fi
MIGRATE_OUTPUT=$(cat "$MIGRATE_LOG")
rm -f "$MIGRATE_LOG" 2>/dev/null || true
rm -f "$PREPARED_MIGRATION_SQL" 2>/dev/null || true
MIGRATE_ERRORS=$(echo "$MIGRATE_OUTPUT" | grep -v "Using a password on the command line interface can be insecure" | grep -v "^$" || true)
if [ $MIGRATE_EXIT -ne 0 ]; then
    echo "[ERROR] 数据库增量迁移失败，退出码: $MIGRATE_EXIT"
    [ -n "$MIGRATE_ERRORS" ] && echo "$MIGRATE_ERRORS"
    exit 1
elif [ -n "$MIGRATE_ERRORS" ]; then
    echo "[WARN] 迁移完成但有警告:"
    echo "$MIGRATE_ERRORS"
fi
echo "[OK] 数据库增量迁移完成（V1~V${max_ver} 全量幂等）"

if [ -f "$FEATURE_TOGGLE_REPAIR_SQL" ]; then
    echo "[INFO] 修复功能开关描述文本..."
    if docker exec -i "$MASTER_CONTAINER" mysql --default-character-set=utf8mb4 -uroot -p"$DB_ROOT_PW" "$DB_NAME" < "$FEATURE_TOGGLE_REPAIR_SQL" >/dev/null 2>&1; then
        echo "[OK] 功能开关描述修复完成"
    else
        echo "[WARN] 功能开关描述修复失败，继续部署"
    fi
fi

# ── 数据导入检查与补全 ──────────────────────────────────────────
# docker compose down -v 后重建时，Docker init 脚本可能未成功导入种子数据
# 此步骤检测关键表数据量，为空时自动从 database/ 和 backups/ 导入
echo "[INFO] 检查数据库种子数据..."

# 检查关键表的数据量（texts=文章, ecdict_words=词典, article=课文）
TEXTS_COUNT=$(docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" "$DB_NAME" \
    -sN -e "SELECT COUNT(*) FROM texts;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
ECDICT_COUNT=$(docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" "$DB_NAME" \
    -sN -e "SELECT COUNT(*) FROM ecdict_words;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
ARTICLE_COUNT=$(docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" "$DB_NAME" \
    -sN -e "SELECT COUNT(*) FROM article;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")

NEED_SEED=false
[ "${TEXTS_COUNT:-0}" = "0" ] && NEED_SEED=true
[ "${ECDICT_COUNT:-0}" = "0" ] && NEED_SEED=true

if [ "$NEED_SEED" = true ]; then
    echo "[WARN] 检测到空数据库（texts: ${TEXTS_COUNT}, ecdict_words: ${ECDICT_COUNT}, article: ${ARTICLE_COUNT}）"
    echo "[INFO] 开始自动导入种子数据..."

    # ① 导入全量备份（database/ 中最新的 .sql，优先全量备份）
    SEED_DIR="$PROJECT_ROOT/database"
    BACKUP_SEED_DIR="$PROJECT_ROOT/backups"
    SEED_FILE=""
    for dir in "$SEED_DIR" "$BACKUP_SEED_DIR"; do
        if [ -d "$dir" ]; then
            for file in "$dir"/*.sql "$dir"/*.sql.gz; do
                [ -f "$file" ] || continue
                # 跳过增量备份
                basename "$file" | grep -q "_incr_" && continue
                # 跳过过小文件
                fsize=$(stat -c%s "$file" 2>/dev/null || echo "0")
                [ "$fsize" -lt 1024 ] && continue
                # 跳过非备份文件（schema_export, texts_full_export 等单独处理）
                basename "$file" | grep -qi "schema_export" && continue
                # 选修改时间最新的
                if [ -z "$SEED_FILE" ]; then
                    SEED_FILE="$file"
                else
                    cur_mtime=$(stat -c%Y "$file" 2>/dev/null || echo "0")
                    best_mtime=$(stat -c%Y "$SEED_FILE" 2>/dev/null || echo "0")
                    [ "$cur_mtime" -gt "$best_mtime" ] && SEED_FILE="$file"
                fi
            done
        fi
    done

    if [ -n "$SEED_FILE" ] && [ -f "$SEED_FILE" ]; then
        echo "[INFO] 导入全量备份: $(basename "$SEED_FILE")"
        case "$SEED_FILE" in
            *.sql.gz)
                if ! zcat "$SEED_FILE" | sed '/^\/\*!50013 DEFINER/d; /^\/\*!50003 CREATE.*DEFINER/d; /SET @@GLOBAL.GTID_PURGED/d; /SET @@SESSION.SQL_LOG_BIN/d' | docker exec -i "$MASTER_CONTAINER" \
                    mysql --default-character-set=utf8mb4 -uroot -p"$DB_ROOT_PW" "$DB_NAME" 2>&1 | grep -v "Using a password"; then
                    echo "[ERROR] 全量备份导入失败: $(basename "$SEED_FILE")"
                    echo "[ERROR] 请检查备份文件完整性或数据库连接"
                    exit 1
                fi
                ;;
            *.sql)
                if ! sed '/^\/\*!50013 DEFINER/d; /^\/\*!50003 CREATE.*DEFINER/d; /SET @@GLOBAL.GTID_PURGED/d; /SET @@SESSION.SQL_LOG_BIN/d' "$SEED_FILE" | docker exec -i "$MASTER_CONTAINER" mysql --default-character-set=utf8mb4 -uroot -p"$DB_ROOT_PW" "$DB_NAME" \
                    2>&1 | grep -v "Using a password"; then
                    echo "[ERROR] 全量备份导入失败: $(basename "$SEED_FILE")"
                    echo "[ERROR] 请检查备份文件完整性或数据库连接"
                    exit 1
                fi
                ;;
        esac
        echo "[OK] 全量备份导入完成"
    else
        echo "[WARN] 未找到全量备份文件，跳过数据导入"
    fi

    # ② 导入课文数据（3Blue1Brown 等）
    ARTICLE_DIR="$PROJECT_ROOT/database/3Blue1Brown"
    if [ -d "$ARTICLE_DIR" ]; then
        ARTICLE_COUNT_AFTER=$(docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" "$DB_NAME" \
            -sN -e "SELECT COUNT(*) FROM article;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
        if [ "${ARTICLE_COUNT_AFTER:-0}" = "0" ]; then
            echo "[INFO] 导入课文数据 (3Blue1Brown)..."
            for f in "$ARTICLE_DIR"/*.sql; do
                [ -f "$f" ] || continue
                docker exec -i "$MASTER_CONTAINER" mysql --default-character-set=utf8mb4 -uroot -p"$DB_ROOT_PW" "$DB_NAME" \
                    < "$f" 2>&1 | grep -v "Using a password" || true
            done
            echo "[OK] 课文数据导入完成"
        fi
    fi

    # ③ 导入 ECDICT 词典（74万词）
    ECDICT_COUNT_AFTER=$(docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" "$DB_NAME" \
        -sN -e "SELECT COUNT(*) FROM ecdict_words;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
    if [ "${ECDICT_COUNT_AFTER:-0}" -lt 100000 ]; then
        ECDICT_CSV_PATH="$PROJECT_ROOT/ECDICT-master/ecdict.csv"
        if [ -f "$ECDICT_CSV_PATH" ]; then
            echo "[INFO] 导入 ECDICT 词典（预计 1-3 分钟）..."
            # 通过 docker cp + LOAD DATA 方式导入（比管道快得多）
            docker cp "$ECDICT_CSV_PATH" "$MASTER_CONTAINER:/tmp/ecdict.csv"
            docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" -e "SET GLOBAL local_infile=ON;" 2>/dev/null || true
            docker exec "$MASTER_CONTAINER" mysql --default-character-set=utf8mb4 -uroot -p"$DB_ROOT_PW" --local-infile=1 "$DB_NAME" -e \
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
            docker exec "$MASTER_CONTAINER" rm -f /tmp/ecdict.csv
            echo "[OK] ECDICT 词典导入完成"
        else
            echo "[WARN] ECDICT CSV 文件不存在: $ECDICT_CSV_PATH，跳过词典导入"
        fi
    fi

    # 验证导入结果
    TEXTS_FINAL=$(docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" "$DB_NAME" \
        -sN -e "SELECT COUNT(*) FROM texts;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
    ECDICT_FINAL=$(docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" "$DB_NAME" \
        -sN -e "SELECT COUNT(*) FROM ecdict_words;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
    ARTICLE_FINAL=$(docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" "$DB_NAME" \
        -sN -e "SELECT COUNT(*) FROM article;" 2>/dev/null | tr -d '[:space:]\r' || echo "0")
    echo "[OK] 种子数据导入完成（texts: ${TEXTS_FINAL}, ecdict_words: ${ECDICT_FINAL}, article: ${ARTICLE_FINAL}）"
else
    echo "[OK] 数据库已有种子数据（texts: ${TEXTS_COUNT}, ecdict_words: ${ECDICT_COUNT}, article: ${ARTICLE_COUNT}）"
fi

# Schema 验证：确认关键表存在（与 auto-update.sh 保持一致）
echo "[INFO] 验证数据库 Schema 完整性..."
SCHEMA_MISSING=""
for tbl in users system_config article_favorites user_tags feature_override \
           administrative_class school_course teaching_class course_schedule \
           course_selection level_exam_scores teaching_plans teaching_plan_courses \
           student_schedule sync_logs field_mappings school_student school_teacher \
           teacher_student_binding; do
    exists=$(docker exec "$MASTER_CONTAINER" \
        mysql -uroot -p"$DB_ROOT_PW" english_learning \
        -sN -e "SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='${tbl}';" \
        2>/dev/null | tr -d '[:space:]' || echo "0")
    [ "${exists:-0}" = "0" ] && SCHEMA_MISSING="${SCHEMA_MISSING} ${tbl}"
done
if [ -n "$SCHEMA_MISSING" ]; then
    echo "[ERROR] 数据库缺少关键表:${SCHEMA_MISSING}"
    echo "[ERROR] 迁移可能未完整执行，请检查迁移脚本后重新部署"
    exit 1
fi
echo "[OK] Schema 验证通过（含学校数据关键表）"

# ── 验证并修复从库复制 ────────────────────────────────────────────
echo "[INFO] 验证从库复制状态..."

# 获取当前复制状态
get_replica_status() {
    docker exec "$SLAVE_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" \
        -e "SHOW REPLICA STATUS\G" 2>/dev/null || true
}

REPLICA_STATUS=$(get_replica_status)
IO_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_IO_Running:" | awk '{print $2}' || echo "NULL")
SQL_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_SQL_Running:" | awk '{print $2}' || echo "NULL")
LAG=$(echo "$REPLICA_STATUS" | grep "Seconds_Behind_Source:" | awk '{print $2}' || echo "NULL")
LAST_IO_ERR=$(echo "$REPLICA_STATUS" | grep "Last_IO_Error:" | sed 's/.*Last_IO_Error: //' || echo "")
LAST_SQL_ERR=$(echo "$REPLICA_STATUS" | grep "Last_SQL_Error:" | sed 's/.*Last_SQL_Error: //' || echo "")

replication_ok=false

if [ "$IO_RUNNING" != "Yes" ] || [ "$SQL_RUNNING" != "Yes" ]; then
    if docker exec "$SLAVE_CONTAINER" test -f /var/lib/mysql/.replication-init-pending 2>/dev/null \
       && docker exec "$SLAVE_CONTAINER" test -x /var/lib/mysql/init-replication.sh 2>/dev/null; then
        echo "[INFO] 检测到从库为首次初始化，执行完整复制初始化脚本..."
        REPL_INIT_LOG="/tmp/langhack-slave-init-$$.log"
        if docker exec "$SLAVE_CONTAINER" bash /var/lib/mysql/init-replication.sh > "$REPL_INIT_LOG" 2>&1; then
            echo "[OK] 从库完整复制初始化完成"
            docker exec "$SLAVE_CONTAINER" rm -f /var/lib/mysql/.replication-init-pending /var/lib/mysql/init-replication.sh 2>/dev/null || true
            REPLICA_STATUS=$(get_replica_status)
            IO_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_IO_Running:" | awk '{print $2}' || echo "NULL")
            SQL_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_SQL_Running:" | awk '{print $2}' || echo "NULL")
            LAG=$(echo "$REPLICA_STATUS" | grep "Seconds_Behind_Source:" | awk '{print $2}' || echo "NULL")
            if [ "$IO_RUNNING" = "Yes" ] && [ "$SQL_RUNNING" = "Yes" ]; then
                echo "[OK] 从库复制运行正常（IO: Yes, SQL: Yes, 延迟: ${LAG}s）"
                replication_ok=true
            fi
        else
            echo "[WARN] 从库完整复制初始化脚本执行失败，回退到轻量修复流程"
            tail -50 "$REPL_INIT_LOG" 2>/dev/null | sed 's/^/         /' || true
        fi
        rm -f "$REPL_INIT_LOG" 2>/dev/null || true
    fi
fi

if [ "$IO_RUNNING" = "Yes" ] && [ "$SQL_RUNNING" = "Yes" ]; then
    echo "[OK] 从库复制运行正常（IO: Yes, SQL: Yes, 延迟: ${LAG}s）"
    replication_ok=true
elif [ "$replication_ok" = false ]; then
    # 复制未配置或线程未运行 → 尝试自动修复
    if [ "$IO_RUNNING" = "NULL" ]; then
        echo "[WARN] 从库复制未配置（SHOW REPLICA STATUS 为空），尝试自动初始化..."
    else
        echo "[WARN] 从库复制线程未运行（IO: ${IO_RUNNING}, SQL: ${SQL_RUNNING}），尝试自动修复..."
        [ -n "$LAST_IO_ERR" ] && echo "[WARN]   IO 错误: $LAST_IO_ERR"
        [ -n "$LAST_SQL_ERR" ] && echo "[WARN]   SQL 错误: $LAST_SQL_ERR"
    fi

    echo "[INFO] 在从库容器内重新执行复制初始化脚本..."
    REPL_USER=$(load_env_value "MYSQL_REPLICATION_USER" "replicator" "$ENV_FILE")
    REPL_PASS=$(load_env_value "MYSQL_REPLICATION_PASSWORD" "" "$ENV_FILE")

    # 获取主库 GTID
    MASTER_GTID=$(docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" \
        -N -e "SELECT @@global.gtid_executed;" 2>/dev/null | tr -d '[:space:]' || echo "")

    if [ -n "$MASTER_GTID" ] && [ "$MASTER_GTID" != "" ]; then
        echo "[INFO] 主库 GTID: ${MASTER_GTID}"
        REPAIR_OUTPUT=$(docker exec "$SLAVE_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" 2>&1 <<-EOSQL
            STOP REPLICA;
            RESET MASTER;
            SET GLOBAL gtid_purged='${MASTER_GTID}';
            CHANGE REPLICATION SOURCE TO
                SOURCE_HOST='mysql-master',
                SOURCE_PORT=3306,
                SOURCE_USER='${REPL_USER}',
                SOURCE_PASSWORD='${REPL_PASS}',
                SOURCE_AUTO_POSITION=1,
                GET_SOURCE_PUBLIC_KEY=1;
            START REPLICA;
EOSQL
        )
    else
        echo "[INFO] 无法获取主库 GTID，使用 auto-position 直接配置..."
        REPAIR_OUTPUT=$(docker exec "$SLAVE_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" 2>&1 <<-EOSQL
            STOP REPLICA;
            RESET MASTER;
            CHANGE REPLICATION SOURCE TO
                SOURCE_HOST='mysql-master',
                SOURCE_PORT=3306,
                SOURCE_USER='${REPL_USER}',
                SOURCE_PASSWORD='${REPL_PASS}',
                SOURCE_AUTO_POSITION=1,
                GET_SOURCE_PUBLIC_KEY=1;
            START REPLICA;
EOSQL
        )
    fi

    REPAIR_ERRORS=$(echo "$REPAIR_OUTPUT" | grep -v "Using a password" | grep -v "^$" || true)
    [ -n "$REPAIR_ERRORS" ] && echo "[WARN] 修复输出: $REPAIR_ERRORS"

    # 等待复制线程启动（最多 30s）
    # 自动跳过 GTID 错误：部署过程中主库可能产生 backup_records 等表的 UPDATE/DELETE，
    # 但从库是从空库初始化的，这些记录不存在，导致 Error 1032 (HA_ERR_KEY_NOT_FOUND)
    echo "[INFO] 等待复制线程启动..."
    GTID_SKIP_COUNT=0
    MAX_GTID_SKIPS=10  # 最多跳过 10 个错误事务，防止无限循环
    repair_wait=0
    while [ $repair_wait -lt 30 ]; do
        sleep 3; repair_wait=$((repair_wait + 3))
        REPLICA_STATUS=$(get_replica_status)
        IO_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_IO_Running:" | awk '{print $2}' || echo "NULL")
        SQL_RUNNING=$(echo "$REPLICA_STATUS" | grep "Replica_SQL_Running:" | awk '{print $2}' || echo "NULL")
        LAG=$(echo "$REPLICA_STATUS" | grep "Seconds_Behind_Source:" | awk '{print $2}' || echo "NULL")
        if [ "$IO_RUNNING" = "Yes" ] && [ "$SQL_RUNNING" = "Yes" ]; then
            echo "[OK] 从库复制修复成功（IO: Yes, SQL: Yes, 延迟: ${LAG}s）"
            replication_ok=true
            break
        fi
        # SQL 线程停止且 IO 正常 → 检查是否为可跳过的 GTID 错误（1032/1062）
        if [ "$IO_RUNNING" = "Yes" ] && [ "$SQL_RUNNING" = "No" ] && [ $GTID_SKIP_COUNT -lt $MAX_GTID_SKIPS ]; then
            # MySQL 8.0 多线程复制：SHOW REPLICA STATUS 的 Last_SQL_Errno 可能为 0
            # 真正的错误在 performance_schema.replication_applier_status_by_worker
            SQL_ERRNO=$(echo "$REPLICA_STATUS" | grep "Last_SQL_Errno:" | awk '{print $2}' || echo "0")
            APPLYING_GTID=$(echo "$REPLICA_STATUS" | grep "APPLYING_TRANSACTION:" | head -1 | awk '{print $2}' || echo "")
            # Fallback 1: 查 performance_schema（MTS 模式下错误在此）
            if [ -z "$APPLYING_GTID" ] || [ "$SQL_ERRNO" = "0" ] || [ -z "$SQL_ERRNO" ]; then
                WORKER_ERR=$(docker exec "$SLAVE_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" -N -e \
                    "SELECT LAST_ERROR_NUMBER, LAST_SEEN_TRANSACTION FROM performance_schema.replication_applier_status_by_worker WHERE LAST_ERROR_NUMBER <> 0 LIMIT 1;" \
                    2>/dev/null || true)
                if [ -n "$WORKER_ERR" ]; then
                    SQL_ERRNO=$(echo "$WORKER_ERR" | awk '{print $1}')
                    APPLYING_GTID=$(echo "$WORKER_ERR" | awk '{print $2}')
                fi
            fi
            # Fallback 2: 从 Last_SQL_Error 消息中提取 GTID（格式: "transaction 'UUID:N'"）
            if [ -z "$APPLYING_GTID" ]; then
                LAST_SQL_ERR_LOOP=$(echo "$REPLICA_STATUS" | grep "Last_SQL_Error:" | sed 's/.*Last_SQL_Error: //' || echo "")
                APPLYING_GTID=$(echo "$LAST_SQL_ERR_LOOP" | grep -oP "transaction '\\K[^']+" || echo "")
            fi
            if [ "$SQL_ERRNO" = "1032" ] || [ "$SQL_ERRNO" = "1062" ]; then
                if [ -n "$APPLYING_GTID" ]; then
                    echo "[WARN] 检测到可跳过的复制错误 (Errno: ${SQL_ERRNO})，跳过事务: ${APPLYING_GTID}"
                    # 注入空事务跳过错误的 GTID
                    docker exec "$SLAVE_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" -e \
                        "SET GTID_NEXT='${APPLYING_GTID}'; BEGIN; COMMIT; SET GTID_NEXT='AUTOMATIC';" \
                        2>/dev/null || true
                    docker exec "$SLAVE_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" -e \
                        "START REPLICA;" 2>/dev/null || true
                    GTID_SKIP_COUNT=$((GTID_SKIP_COUNT + 1))
                    sleep 2
                    continue  # 立即重新检查状态
                fi
            fi
        fi
        echo "[INFO] 等待复制线程... (${repair_wait}s, IO: ${IO_RUNNING}, SQL: ${SQL_RUNNING})"
    done
    [ $GTID_SKIP_COUNT -gt 0 ] && echo "[INFO] 共跳过 ${GTID_SKIP_COUNT} 个错误 GTID 事务"

    if [ "$replication_ok" = false ]; then
        LAST_IO_ERR=$(echo "$REPLICA_STATUS" | grep "Last_IO_Error:" | sed 's/.*Last_IO_Error: //' || echo "")
        LAST_SQL_ERR=$(echo "$REPLICA_STATUS" | grep "Last_SQL_Error:" | sed 's/.*Last_SQL_Error: //' || echo "")
        APPLYING_GTID=$(echo "$REPLICA_STATUS" | grep "APPLYING_TRANSACTION:" | head -1 | awk '{print $2}' || echo "")
        SQL_ERRNO=$(echo "$REPLICA_STATUS" | grep "Last_SQL_Errno:" | awk '{print $2}' || echo "0")
        # 多线程复制：从 performance_schema 获取真实的错误 GTID
        if [ -z "$APPLYING_GTID" ] || [ "$SQL_ERRNO" = "0" ] || [ -z "$SQL_ERRNO" ]; then
            WORKER_ERR=$(docker exec "$SLAVE_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" -N -e \
                "SELECT LAST_ERROR_NUMBER, LAST_SEEN_TRANSACTION FROM performance_schema.replication_applier_status_by_worker WHERE LAST_ERROR_NUMBER <> 0 LIMIT 1;" \
                2>/dev/null || true)
            if [ -n "$WORKER_ERR" ]; then
                [ -z "$SQL_ERRNO" ] || [ "$SQL_ERRNO" = "0" ] && SQL_ERRNO=$(echo "$WORKER_ERR" | awk '{print $1}')
                [ -z "$APPLYING_GTID" ] && APPLYING_GTID=$(echo "$WORKER_ERR" | awk '{print $2}')
            fi
        fi
        # Fallback: 从 Last_SQL_Error 消息中提取 GTID
        if [ -z "$APPLYING_GTID" ] && [ -n "$LAST_SQL_ERR" ]; then
            APPLYING_GTID=$(echo "$LAST_SQL_ERR" | grep -oP "transaction '\\K[^']+" || echo "")
        fi
        echo ""
        echo "[ERROR] ══════════════════════════════════════════════════════"
        echo "[ERROR] 从库复制初始化失败，部署终止"
        echo "[ERROR] IO Thread:  ${IO_RUNNING}"
        echo "[ERROR] SQL Thread: ${SQL_RUNNING}"
        [ -n "$LAST_IO_ERR" ]  && echo "[ERROR] IO 错误:  $LAST_IO_ERR"
        [ -n "$LAST_SQL_ERR" ] && echo "[ERROR] SQL 错误: $LAST_SQL_ERR"
        [ -n "$SQL_ERRNO" ] && [ "$SQL_ERRNO" != "0" ] && echo "[ERROR] SQL 错误码: ${SQL_ERRNO}"
        [ -n "$APPLYING_GTID" ] && echo "[ERROR] 卡住的事务: ${APPLYING_GTID}"
        [ $GTID_SKIP_COUNT -ge $MAX_GTID_SKIPS ] && echo "[ERROR] 已达到最大跳过次数 (${MAX_GTID_SKIPS})，仍有未解决的错误"
        echo "[ERROR] ══════════════════════════════════════════════════════"
        echo ""
        echo "── 手动排查步骤 ──────────────────────────────────────────────"
        echo "  # 1. 查看从库完整日志"
        echo "  docker compose logs mysql-slave"
        echo ""
        echo "  # 2. 检查复制用户是否存在于主库"
        echo "  docker exec $MASTER_CONTAINER mysql -uroot -p\"\$DB_ROOT_PW\" \\"
        echo "    -e \"SELECT user,host FROM mysql.user WHERE user='${REPL_USER}';\""
        echo ""
        echo "  # 3. 检查主库 binlog 是否开启"
        echo "  docker exec $MASTER_CONTAINER mysql -uroot -p\"\$DB_ROOT_PW\" \\"
        echo "    -e \"SHOW VARIABLES LIKE 'log_bin'; SHOW VARIABLES LIKE 'gtid_mode';\""
        echo ""
        echo "  # 4. 手动跳过错误事务（如 Error 1032/1062）"
        echo "  docker exec $SLAVE_CONTAINER mysql -uroot -p\"\$DB_ROOT_PW\" \\"
        echo "    -e \"SET GTID_NEXT='${APPLYING_GTID:-UNKNOWN}'; BEGIN; COMMIT; SET GTID_NEXT='AUTOMATIC'; START REPLICA;\""
        echo ""
        echo "  # 5. 手动重置并重试"
        echo "  docker exec $SLAVE_CONTAINER mysql -uroot -p\"\$DB_ROOT_PW\" \\"
        echo "    -e \"STOP REPLICA; RESET REPLICA ALL;\""
        echo "  docker compose restart mysql-slave"
        echo "──────────────────────────────────────────────────────────────"
        exit 1
    fi
fi

# 等待复制延迟归零（最多 60s）
if [ "$replication_ok" = true ] && [ "$LAG" != "0" ] && [ "$LAG" != "NULL" ]; then
    echo "[INFO] 等待从库追上主库（当前延迟: ${LAG}s）..."
    lag_wait=0
    while [ $lag_wait -lt 60 ]; do
        sleep 3; lag_wait=$((lag_wait + 3))
        LAG=$(docker exec "$SLAVE_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" \
            -e "SHOW REPLICA STATUS\G" 2>/dev/null \
            | grep "Seconds_Behind_Source:" | awk '{print $2}' || echo "NULL")
        [ "$LAG" = "0" ] && echo "[OK] 从库已追上主库（延迟: 0s）" && break
        printf "  延迟: %ss (等待 %ds)\r" "$LAG" "$lag_wait"
    done
    [ "$LAG" != "0" ] && echo "[WARN] 从库延迟 ${LAG}s，超过等待时间，继续部署"
fi

echo "[SUCCESS] 部署完成！"
echo "  分支: $TARGET_BRANCH"
echo "  版本: $(cd "$PROJECT_ROOT" && git rev-parse --short HEAD)"
SERVER_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "localhost")
if [ "$HTTPS_ENABLED_VAL" = "true" ]; then
    HTTPS_PORT_SUFFIX=""; [ "$PORT_HTTPS_VAL" != "443" ] && HTTPS_PORT_SUFFIX=":${PORT_HTTPS_VAL}"
    DISPLAY_HOST="${SSL_DOMAIN_VAL:-$SERVER_IP}"
    echo "  前端 (HTTPS): https://${DISPLAY_HOST}${HTTPS_PORT_SUFFIX}"
    echo "  前端 (HTTP):  http://${SERVER_IP}:${PORT_FRONTEND} (→ 自动跳转 HTTPS)"
    echo "  后端:         http://${SERVER_IP}:${PORT_BACKEND}"
    echo "  AI服务管理:   https://${DISPLAY_HOST}${HTTPS_PORT_SUFFIX}/#/ops/ai-providers"
else
    echo "  前端:        http://${SERVER_IP}:${PORT_FRONTEND}"
    echo "  后端:        http://${SERVER_IP}:${PORT_BACKEND}"
    echo "  AI服务管理:  http://${SERVER_IP}:${PORT_FRONTEND}/#/ops/ai-providers"
fi

# ── AI 密钥种子化：应用启动时 @PostConstruct 自动完成，无需外部触发 ──
echo "[INFO] AI 服务商密钥将在后端启动时自动从环境变量种子化到数据库"
echo "       如需手动管理，请访问运维面板 → AI服务管理"

# ── 学校数据功能开关激活 ───────────────────────────────────────
if [ "${CFG_SCHOOL_FEATURE_ENABLED:-false}" = "true" ]; then
    echo "[INFO] 激活学校数据功能开关..."
    docker exec "$MASTER_CONTAINER" mysql -uroot -p"$DB_ROOT_PW" "$DB_NAME" -e \
        "INSERT IGNORE INTO system_config (config_group, config_key, config_value, description, is_secret)
         VALUES
           ('feature_toggle', 'ops.school_data_browser', 'true', '运维端学校数据浏览功能（行政班、课程、教学班等）', 0),
           ('feature_toggle', 'ops.school_data_config', 'true', '运维端学校数据源配置功能（API/ETL切换、连接测试）', 0),
           ('feature_toggle', 'page_schedule', 'true', '学生端课表查看功能（从教务系统同步课表数据）', 0);
         UPDATE system_config SET config_value='true'
         WHERE config_group='feature_toggle'
           AND config_key IN ('ops.school_data_browser', 'ops.school_data_config', 'page_schedule');" \
        2>&1 | grep -v "Using a password" || true
    echo "[OK] 学校数据功能开关已启用（运维端入口 + 学生端课表）"
fi

# ── 学校数据 API 连通性验证（端到端保障）───────────────────────
# 配置完成后主动验证 appId/appSecret 是否真能从学校 API 拉到数据。
# 此步骤不阻塞部署（学校 API 可能暂时不可达），但会给出明确提示。
SCHOOL_API_APP_ID_VAL=$(load_env_value "SCHOOL_API_APP_ID" "" "$ENV_FILE")
SCHOOL_API_APP_SECRET_VAL=$(load_env_value "SCHOOL_API_APP_SECRET" "" "$ENV_FILE")
if [ -n "$SCHOOL_API_APP_ID_VAL" ] && [ -n "$SCHOOL_API_APP_SECRET_VAL" ]; then
    echo ""
    echo "[INFO] 验证学校 API 连通性（appId=${SCHOOL_API_APP_ID_VAL:0:8}..., appSecret=***）..."
    BACKEND_URL="http://localhost:${PORT_BACKEND:-8080}"
    # 使用默认 admin 账号登录（部署后运维应立即修改）
    LOGIN_RESP=$(curl -sS --max-time 10 \
        -X POST "${BACKEND_URL}/api/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"admin123"}' 2>/dev/null || echo "{}")
    ADMIN_JWT=$(echo "$LOGIN_RESP" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
    if [ -n "$ADMIN_JWT" ]; then
        TEST_RESP=$(curl -sS --max-time 30 \
            -X POST "${BACKEND_URL}/api/ops/school-data/test-connection" \
            -H "Authorization: Bearer ${ADMIN_JWT}" 2>/dev/null || echo "{}")
        if echo "$TEST_RESP" | grep -q '"success":true'; then
            echo "[OK] ✅ 学校 API 连接测试通过 — 同步链路就绪"
        else
            echo "[WARN] ⚠️  学校 API 连接测试失败"
            echo "       响应: $(echo "$TEST_RESP" | head -c 200)"
            echo "       排查: 1) App ID/Secret 正确性  2) cdsp.imau.edu.cn 网络可达  3) 后端日志 docker logs langhack-backend | grep -i school"
            echo "       修复后可运维面板 → 学校数据配置 → 测试连接 重新验证"
        fi
    else
        echo "[WARN] 无法获取 admin JWT 自动验证连接（可能 admin 密码已修改）"
        echo "       请登录运维面板 → 学校数据配置 → 测试连接 手动验证"
    fi
else
    echo "[INFO] 学校 API 认证未配置（SCHOOL_API_APP_ID/SECRET 为空），跳过连通性验证"
    echo "       如需启用，请在运维面板 → 学校数据配置 → 填入 App ID / App Secret → 保存 → 测试连接"
fi

# ── SSO 管理员叩门入口信息 ──
SSO_ENABLED_VAL="${CFG_SSO_ENABLED:-$(load_env_value "SSO_ENABLED" "false" "$ENV_FILE")}"
if [ "$SSO_ENABLED_VAL" = "true" ]; then
    ADMIN_PATH_VAL="${CFG_ADMIN_PATH:-$(load_env_value "ADMIN_PORTAL_PATH" "" "$ENV_FILE")}"
    ADMIN_SECRET_VAL="${CFG_ADMIN_SECRET:-$(load_env_value "ADMIN_LOGIN_SECRET" "" "$ENV_FILE")}"
    if [ -n "$ADMIN_PATH_VAL" ]; then
        if [ "$HTTPS_ENABLED_VAL" = "true" ]; then
            DISPLAY_HOST="${SSL_DOMAIN_VAL:-$SERVER_IP}"
            HTTPS_PORT_SUFFIX=""; [ "$PORT_HTTPS_VAL" != "443" ] && HTTPS_PORT_SUFFIX=":${PORT_HTTPS_VAL}"
            ADMIN_BASE="https://${DISPLAY_HOST}${HTTPS_PORT_SUFFIX}"
        else
            PORT_FRONTEND="${CFG_PORT_FE:-$(load_env_value "PORT_FRONTEND" "80" "$ENV_FILE")}"
            ADMIN_BASE="http://${SERVER_IP}:${PORT_FRONTEND}"
        fi
        echo ""
        echo "── 管理员叩门入口 ──"
        echo "  入口路径: ${ADMIN_BASE}/${ADMIN_PATH_VAL}"
        echo "  生成叩门链接 (在服务器上运行):"
        echo "    ADMIN_LOGIN_SECRET='${ADMIN_SECRET_VAL}' \\"
        echo "    ADMIN_PORTAL_PATH='${ADMIN_PATH_VAL}' \\"
        echo "    node frontend/scripts/generate-admin-login-url.js --base-url ${ADMIN_BASE}"
        echo ""
        echo "  提示: 叩门链接 5 分钟有效，请通过安全渠道分享给管理员"
    fi
fi

# ── 设置 Let's Encrypt 证书自动续期 ────────────────────────────
if [ "$HTTPS_ENABLED_VAL" = "true" ]; then
    SSL_CERT_VAL=$(load_env_value "SSL_CERT_PATH" "" "$ENV_FILE")
    if [[ "$SSL_CERT_VAL" == /etc/letsencrypt/* ]]; then
        RENEW_CMD="30 2 * * * certbot renew --quiet --deploy-hook 'docker compose -f ${DOCKER_DIR}/docker-compose.yml restart frontend' >> /var/log/certbot-renew.log 2>&1"
        CRON_TMP=$(mktemp /tmp/langhack-deploy-cron.XXXXXX)
        crontab -l 2>/dev/null | grep -v "certbot renew" > "$CRON_TMP" || true
        echo "$RENEW_CMD" >> "$CRON_TMP"
        if crontab "$CRON_TMP" 2>/dev/null; then
            echo "[OK] Let's Encrypt 证书自动续期已配置 (每天 2:30 检查)"
        else
            echo "[WARN] 证书续期 crontab 设置失败，请手动添加:"
            echo "  $RENEW_CMD"
        fi
        rm -f "$CRON_TMP"
    fi
fi

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
if [ "$HTTPS_ENABLED_VAL" = "true" ]; then
    echo "[重要] HTTPS 已启用。如使用自签名证书，用户首次访问需手动信任。"
fi
echo "[警告] 请勿执行 'docker compose down -v'，该命令会删除 MySQL/Redis 数据卷！"
echo "  安全停止: docker compose down"
echo "  安全重启: docker compose restart"
echo ""
echo "── 历史数据导入（可选）────────────────────────────────────"
echo "  首次部署时，03-import-backup.sh 自动从 database/ 和 backups/"
echo "  中选取修改时间最新的备份文件导入（.sql 或 .sql.gz 均支持）。"
echo ""
echo "  如需向已运行的数据库手动导入数据："
echo "     DB_PW=\$(grep '^DB_PASSWORD=' $ENV_FILE | cut -d= -f2-)"
echo "     # 导入 .sql 文件："
echo "     docker exec -i langhack-mysql-master mysql -uroot -p\"\$DB_PW\" english_learning < backups/<your-file>.sql"
echo "     # 导入 .sql.gz 压缩文件："
echo "     zcat backups/<your-file>.sql.gz | docker exec -i langhack-mysql-master mysql -uroot -p\"\$DB_PW\" english_learning"
echo "────────────────────────────────────────────────────────────"
