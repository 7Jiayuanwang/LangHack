#!/usr/bin/env bash
# ==============================================================================
# LangHack 一键部署脚本 (Docker Compose)
#
# 用法:
#   ./scripts/deploy.sh                      # 交互式选择分支
#   ./scripts/deploy.sh -b master            # 指定分支
#   ./scripts/deploy.sh --branch feature/x   # 指定分支（长参数）
#   ./scripts/deploy.sh -c                   # 使用当前分支，跳过选择
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DOCKER_DIR="$PROJECT_ROOT/docker"

TARGET_BRANCH=""
USE_CURRENT=false

# ── 参数解析 ──────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        -b|--branch)
            TARGET_BRANCH="$2"
            shift 2
            ;;
        -c|--current)
            USE_CURRENT=true
            shift
            ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "  -b, --branch <分支名>   指定部署分支"
            echo "  -c, --current           使用当前分支，跳过选择"
            echo "  -h, --help              显示帮助信息"
            echo ""
            echo "示例:"
            echo "  $0                      交互式选择分支"
            echo "  $0 -b master            部署 master 分支"
            echo "  $0 -c                   直接使用当前分支部署"
            exit 0
            ;;
        *)
            echo "[ERROR] 未知参数: $1"
            echo "运行 $0 --help 查看帮助"
            exit 1
            ;;
    esac
done

echo "======================================"
echo " LangHack 部署脚本 v2.0"
echo "======================================"

# ── 检查基础依赖 ──────────────────────────────────────
command -v docker &>/dev/null || { echo "[ERROR] Docker 未安装，请先安装 Docker 20+"; exit 1; }
docker compose version &>/dev/null || { echo "[ERROR] Docker Compose V2 未安装"; exit 1; }
command -v git &>/dev/null || { echo "[ERROR] Git 未安装"; exit 1; }

# ── 分支选择 ──────────────────────────────────────────
cd "$PROJECT_ROOT"
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
echo "[INFO] 当前分支: $CURRENT_BRANCH"

if [ "$USE_CURRENT" = true ]; then
    TARGET_BRANCH="$CURRENT_BRANCH"
    echo "[INFO] 使用当前分支: $TARGET_BRANCH"
elif [ -z "$TARGET_BRANCH" ]; then
    # 交互式分支选择
    echo "[INFO] 正在获取远程分支列表..."
    git fetch --all --quiet 2>/dev/null || echo "[WARN] 无法连接远程仓库，仅显示本地分支"

    # 收集分支列表：远程分支 + 本地分支（去重）
    mapfile -t REMOTE_BRANCHES < <(git branch -r --no-color 2>/dev/null \
        | grep -v 'HEAD' \
        | sed 's|origin/||;s|^[[:space:]]*||' \
        | sort -u)

    mapfile -t LOCAL_BRANCHES < <(git branch --no-color 2>/dev/null \
        | sed 's|^\*||;s|^[[:space:]]*||' \
        | sort -u)

    # 合并去重
    mapfile -t BRANCHES < <(printf '%s\n' "${REMOTE_BRANCHES[@]}" "${LOCAL_BRANCHES[@]}" | sort -u)

    if [ ${#BRANCHES[@]} -eq 0 ]; then
        echo "[WARN] 未获取到分支列表，使用当前分支: $CURRENT_BRANCH"
        TARGET_BRANCH="$CURRENT_BRANCH"
    else
        echo ""
        echo "可用分支:"
        for i in "${!BRANCHES[@]}"; do
            branch="${BRANCHES[$i]}"
            marker=""
            if [ "$branch" = "$CURRENT_BRANCH" ]; then
                marker=" ← 当前"
            fi
            printf "  %2d) %s%s\n" $((i + 1)) "$branch" "$marker"
        done
        echo ""

        while true; do
            read -rp "请选择分支编号 [1-${#BRANCHES[@]}]: " choice
            if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le ${#BRANCHES[@]} ]; then
                TARGET_BRANCH="${BRANCHES[$((choice - 1))]}"
                break
            else
                echo "[ERROR] 无效选择，请输入 1 到 ${#BRANCHES[@]} 之间的数字"
            fi
        done
    fi
else
    # 验证指定的分支是否存在
    echo "[INFO] 正在获取远程分支..."
    git fetch --all --quiet 2>/dev/null || true

    if ! git rev-parse --verify "origin/$TARGET_BRANCH" &>/dev/null && \
       ! git rev-parse --verify "$TARGET_BRANCH" &>/dev/null; then
        echo "[ERROR] 分支 '$TARGET_BRANCH' 不存在（远程和本地均未找到）"
        echo "[INFO] 可用分支:"
        git branch -r --no-color 2>/dev/null | grep -v 'HEAD' | sed 's|origin/||;s|^[[:space:]]*||' | sort -u | sed 's|^|  |'
        exit 1
    fi
fi

echo "[INFO] 目标部署分支: $TARGET_BRANCH"

# ── 切换分支 ──────────────────────────────────────────
if [ "$TARGET_BRANCH" != "$CURRENT_BRANCH" ]; then
    echo "[INFO] 切换到分支: $TARGET_BRANCH ..."

    # 检查未提交的更改
    if ! git diff --quiet 2>/dev/null || ! git diff --cached --quiet 2>/dev/null; then
        echo "[WARN] 检测到未提交的更改"
        read -rp "是否暂存更改并继续？(y/n): " confirm
        if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
            git stash push -m "deploy-auto-stash-$(date +%Y%m%d_%H%M%S)"
            echo "[INFO] 更改已暂存 (git stash pop 可恢复)"
        else
            echo "[INFO] 部署已取消"
            exit 0
        fi
    fi

    git checkout "$TARGET_BRANCH" || { echo "[ERROR] 切换分支失败"; exit 1; }
    git pull origin "$TARGET_BRANCH" --ff-only 2>/dev/null || echo "[WARN] 拉取最新代码失败，使用本地版本"
else
    echo "[INFO] 拉取最新代码..."
    git pull origin "$TARGET_BRANCH" --ff-only 2>/dev/null || echo "[WARN] 拉取失败，使用本地版本"
fi

echo "[INFO] 当前版本: $(git rev-parse --short HEAD) — $(git log -1 --format='%s')"

# ── 检查 .env 文件 ────────────────────────────────────
if [ ! -f "$DOCKER_DIR/.env" ]; then
    echo "[INFO] .env 文件不存在，从模板创建..."
    cp "$DOCKER_DIR/.env.example" "$DOCKER_DIR/.env"
    echo "[WARN] 请编辑 $DOCKER_DIR/.env 填写真实配置后重新运行！"
    exit 1
fi

# 检查必填环境变量
source "$DOCKER_DIR/.env"
required_vars=(DB_PASSWORD REDIS_PASSWORD JWT_SECRET OPENROUTER_API_KEY)
for var in "${required_vars[@]}"; do
    val="${!var:-}"
    if [ -z "$val" ] || [[ "$val" == *"your_"* ]] || [[ "$val" == *"_here"* ]]; then
        echo "[ERROR] 环境变量 $var 未正确设置，请编辑 $DOCKER_DIR/.env"
        exit 1
    fi
done

echo "[INFO] 环境变量检查通过"

# ── 构建和启动 ────────────────────────────────────────
cd "$DOCKER_DIR"
echo "[INFO] 拉取基础镜像..."
docker compose pull mysql-master mysql-slave redis 2>/dev/null || true

echo "[INFO] 构建应用镜像..."
docker compose build app

echo "[INFO] 启动所有服务..."
docker compose up -d

# ── 等待服务健康 ──────────────────────────────────────
echo "[INFO] 等待服务启动（最多 120s）..."
timeout=120
elapsed=0
while [ $elapsed -lt $timeout ]; do
    if docker compose ps | grep -q "healthy"; then
        healthy_count=$(docker compose ps | grep -c "healthy" || true)
        if [ "$healthy_count" -ge 3 ]; then
            break
        fi
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    echo "  等待中... ${elapsed}s"
done

# ── 最终状态 ──────────────────────────────────────────
echo ""
echo "======================================"
docker compose ps
echo "======================================"
echo ""
echo "[SUCCESS] 部署完成！"
echo "  分支:     $TARGET_BRANCH"
echo "  版本:     $(cd "$PROJECT_ROOT" && git rev-parse --short HEAD)"
echo "  应用地址: http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo 'localhost'):8080"
echo "  健康检查: http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo 'localhost'):8080/actuator/health"
echo ""
echo "[提示] 首次部署需配置 MySQL 主从复制，参考 docs/DEPLOYMENT.md"
