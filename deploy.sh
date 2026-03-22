#!/bin/bash

# ==============================================================================
# 英语学习平台 (LangHack) - Docker 一键部署脚本
#
# 用法:
#   ./deploy.sh                      # 交互式选择分支
#   ./deploy.sh -b master            # 指定分支
#   ./deploy.sh --branch feature/x   # 指定分支（长参数）
#   ./deploy.sh -c                   # 使用当前分支，跳过选择
# ==============================================================================

# 设置颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

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
            echo "  $0 -b feature/monitoring 部署指定功能分支"
            echo "  $0 -c                   直接使用当前分支部署"
            exit 0
            ;;
        *)
            echo -e "${RED}未知参数: $1${NC}"
            echo "运行 $0 --help 查看帮助"
            exit 1
            ;;
    esac
done

echo -e "${GREEN}${BOLD}🚀 开始部署英语学习平台...${NC}"

# ── 分支选择 ──────────────────────────────────────────
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
echo -e "${CYAN}📌 当前分支: ${BOLD}$CURRENT_BRANCH${NC}"

if [ "$USE_CURRENT" = true ]; then
    TARGET_BRANCH="$CURRENT_BRANCH"
    echo -e "${GREEN}✅ 使用当前分支: $TARGET_BRANCH${NC}"
elif [ -z "$TARGET_BRANCH" ]; then
    # 交互式分支选择
    echo -e "${YELLOW}🔄 正在获取远程分支列表...${NC}"
    git fetch --all --quiet 2>/dev/null

    # 列出远程分支（去掉 origin/ 前缀和 HEAD）
    mapfile -t BRANCHES < <(git branch -r --no-color 2>/dev/null \
        | grep -v 'HEAD' \
        | sed 's|origin/||;s|^[[:space:]]*||' \
        | sort -u)

    if [ ${#BRANCHES[@]} -eq 0 ]; then
        echo -e "${YELLOW}⚠️ 未获取到远程分支，使用当前分支: $CURRENT_BRANCH${NC}"
        TARGET_BRANCH="$CURRENT_BRANCH"
    else
        echo ""
        echo -e "${BOLD}可用分支:${NC}"
        for i in "${!BRANCHES[@]}"; do
            branch="${BRANCHES[$i]}"
            marker=""
            if [ "$branch" = "$CURRENT_BRANCH" ]; then
                marker=" ${GREEN}← 当前${NC}"
            fi
            printf "  ${CYAN}%2d)${NC} %s%b\n" $((i + 1)) "$branch" "$marker"
        done
        echo ""

        while true; do
            read -rp "$(echo -e "${BOLD}请选择分支编号 [1-${#BRANCHES[@]}]:${NC} ")" choice
            if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le ${#BRANCHES[@]} ]; then
                TARGET_BRANCH="${BRANCHES[$((choice - 1))]}"
                break
            else
                echo -e "${RED}无效选择，请输入 1 到 ${#BRANCHES[@]} 之间的数字${NC}"
            fi
        done
    fi
fi

echo -e "${GREEN}🎯 目标部署分支: ${BOLD}$TARGET_BRANCH${NC}"

# ── 切换分支 ──────────────────────────────────────────
if [ "$TARGET_BRANCH" != "$CURRENT_BRANCH" ]; then
    echo -e "${YELLOW}🔄 切换到分支: $TARGET_BRANCH ...${NC}"

    # 检查是否有未提交的更改
    if ! git diff --quiet 2>/dev/null || ! git diff --cached --quiet 2>/dev/null; then
        echo -e "${RED}⚠️ 检测到未提交的更改！${NC}"
        read -rp "$(echo -e "${BOLD}是否暂存更改并继续？(y/n):${NC} ")" confirm
        if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
            git stash push -m "deploy-auto-stash-$(date +%Y%m%d_%H%M%S)"
            echo -e "${GREEN}✅ 更改已暂存${NC}"
        else
            echo -e "${RED}❌ 部署已取消${NC}"
            exit 1
        fi
    fi

    git checkout "$TARGET_BRANCH" || { echo -e "${RED}❌ 切换分支失败${NC}"; exit 1; }
    git pull origin "$TARGET_BRANCH" --ff-only || echo -e "${YELLOW}⚠️ 拉取最新代码失败，使用本地版本${NC}"
else
    echo -e "${YELLOW}🔄 拉取最新代码...${NC}"
    git pull origin "$TARGET_BRANCH" --ff-only 2>/dev/null || echo -e "${YELLOW}⚠️ 拉取失败，使用本地版本${NC}"
fi

echo -e "${CYAN}📦 当前版本: $(git rev-parse --short HEAD) — $(git log -1 --format='%s')${NC}"

# ── 检查 .env ──────────────────────────────────────────
if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        echo -e "${YELLOW}⚠️ 未发现 .env 文件，正从 .env.example 生成...${NC}"
        cp .env.example .env
        echo -e "${RED}🛑 请先编辑 .env 文件配置好密码和 API Keys 后重新运行此脚本！${NC}"
        exit 1
    else
        echo -e "${RED}❌ 未发现 .env 且未找到 .env.example。请手动创建 .env 文件！${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}✅ 检测到 .env 文件，开始加载配置...${NC}"

# ── Docker Compose 构建并启动 ──────────────────────────
echo -e "${GREEN}🐳 正在编译和启动 Docker 容器 (这可能需要几分钟)...${NC}"
docker-compose up -d --build

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}${BOLD}🎉 部署成功！${NC}"
    echo -e "  分支: ${CYAN}$TARGET_BRANCH${NC}"
    echo -e "  版本: ${CYAN}$(git rev-parse --short HEAD)${NC}"
    echo -e ""
    echo -e "  💻 访问地址: http://localhost (或服务器公网 IP)"
    echo -e "  🔄 查看日志: docker-compose logs -f"
else
    echo -e "${RED}❌ 部署失败，请检查上面的报错信息！${NC}"
    exit 1
fi
