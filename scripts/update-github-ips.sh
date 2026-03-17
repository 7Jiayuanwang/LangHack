#!/usr/bin/env bash
# ==============================================================================
# GitHub Webhook IP 白名单自动更新
# 从 GitHub Meta API 获取最新的 hooks IP 段，生成 nginx include 文件
#
# 用法:
#   chmod +x scripts/update-github-ips.sh
#   # 手动执行
#   ./scripts/update-github-ips.sh
#   # 或由 systemd timer 定期触发（推荐每天一次）
# ==============================================================================
set -euo pipefail

# ── 配置 ────────────────────────────────────────────────────
GITHUB_META_URL="https://api.github.com/meta"
NGINX_CONF="/etc/nginx/github-ips.conf"
TEMP_FILE="/tmp/github-ips-new.conf"
LOG_TAG="github-ip-update"

# ── 依赖检查 ────────────────────────────────────────────────
for cmd in curl python3 nginx; do
    command -v "$cmd" &>/dev/null || { echo "[$LOG_TAG] ERROR: $cmd not found"; exit 1; }
done

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$LOG_TAG] $*"
}

# ── 获取 GitHub hooks IP 段 ─────────────────────────────────
log "正在获取 GitHub Meta API..."
META_JSON=$(curl -sf --max-time 15 "$GITHUB_META_URL" || echo "")

if [ -z "$META_JSON" ]; then
    log "ERROR: 无法获取 GitHub Meta API，保留现有配置"
    exit 1
fi

# 提取 hooks 字段的 IP 段，生成 nginx allow 指令
ALLOW_LINES=$(echo "$META_JSON" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    hooks = data.get('hooks', [])
    if not hooks:
        sys.exit(1)
    for ip in hooks:
        print(f'allow {ip};')
except Exception:
    sys.exit(1)
" || echo "")

if [ -z "$ALLOW_LINES" ]; then
    log "ERROR: 解析 hooks IP 失败，保留现有配置"
    exit 1
fi

IP_COUNT=$(echo "$ALLOW_LINES" | wc -l)
log "获取到 $IP_COUNT 个 IP 段"

# ── 生成新的配置文件 ─────────────────────────────────────────
cat > "$TEMP_FILE" <<EOF
# GitHub Webhook IP 白名单（自动生成，勿手动修改）
# 更新时间: $(date '+%Y-%m-%d %H:%M:%S')
# 来源: $GITHUB_META_URL → hooks
# IP 段数量: $IP_COUNT
$ALLOW_LINES
EOF

# ── 比较是否有变化 ───────────────────────────────────────────
if [ -f "$NGINX_CONF" ]; then
    # 只比较 allow 行（忽略注释中的时间戳）
    OLD_IPS=$(grep '^allow ' "$NGINX_CONF" 2>/dev/null | sort || echo "")
    NEW_IPS=$(grep '^allow ' "$TEMP_FILE" | sort)

    if [ "$OLD_IPS" = "$NEW_IPS" ]; then
        log "IP 段无变化，跳过更新"
        rm -f "$TEMP_FILE"
        exit 0
    fi
    log "检测到 IP 段变化！"
    log "  旧: $(echo "$OLD_IPS" | wc -l) 条"
    log "  新: $(echo "$NEW_IPS" | wc -l) 条"
else
    log "首次创建 $NGINX_CONF"
fi

# ── 备份旧配置 ──────────────────────────────────────────────
if [ -f "$NGINX_CONF" ]; then
    cp "$NGINX_CONF" "${NGINX_CONF}.bak"
    log "旧配置已备份: ${NGINX_CONF}.bak"
fi

# ── 写入新配置 ──────────────────────────────────────────────
cp "$TEMP_FILE" "$NGINX_CONF"
rm -f "$TEMP_FILE"

# ── 验证 nginx 配置 ─────────────────────────────────────────
if nginx -t 2>/dev/null; then
    log "Nginx 配置验证通过，正在 reload..."
    nginx -s reload
    log "Nginx reload 完成，IP 白名单已更新"
else
    log "ERROR: Nginx 配置验证失败！正在回滚..."
    if [ -f "${NGINX_CONF}.bak" ]; then
        cp "${NGINX_CONF}.bak" "$NGINX_CONF"
        log "已回滚到旧配置"
    fi
    exit 1
fi
