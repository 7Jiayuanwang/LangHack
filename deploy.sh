#!/bin/bash
# ==============================================================================
# LangHack 部署入口（重定向到 scripts/deploy.sh）
#
# 本脚本为兼容旧版入口，实际部署逻辑已迁移至 scripts/deploy.sh
# 使用 docker/ 目录下的完整 docker-compose.yml（含主从复制、健康检查等）
#
# 用法:
#   ./deploy.sh              # 启动交互式向导
#   ./deploy.sh --quick      # 快速模式
#   ./deploy.sh --help       # 帮助
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_SCRIPT="$SCRIPT_DIR/scripts/deploy.sh"

if [ ! -f "$DEPLOY_SCRIPT" ]; then
    echo "[ERROR] scripts/deploy.sh 未找到，请检查项目完整性"
    exit 1
fi

echo "[INFO] 重定向到 scripts/deploy.sh ..."
exec bash "$DEPLOY_SCRIPT" "$@"
