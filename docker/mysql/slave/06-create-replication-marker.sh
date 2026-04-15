#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────
# 从库复制初始化标记脚本
# 在 docker-entrypoint-initdb.d 阶段执行，创建标记文件
# 实际的复制初始化将在容器启动后由 deploy.sh 自动执行
# ─────────────────────────────────────────────────────────────────────────

set -e

MARKER_FILE="/var/lib/mysql/.replication-init-pending"
INIT_SCRIPT="/docker-entrypoint-initdb.d/init-replication.sh"

echo "[replication-marker] ======================================"
echo "[replication-marker] 从库复制初始化标记"
echo "[replication-marker] 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "[replication-marker] ======================================"

# 创建标记文件
touch "$MARKER_FILE"
echo "[replication-marker] ✅ 标记已创建: $MARKER_FILE"

# 复制初始化脚本到持久化位置
if [ -f "$INIT_SCRIPT" ]; then
    cp "$INIT_SCRIPT" /var/lib/mysql/init-replication.sh
    chmod +x /var/lib/mysql/init-replication.sh
    echo "[replication-marker] ✅ 初始化脚本已复制到: /var/lib/mysql/init-replication.sh"
fi

echo "[replication-marker] ======================================"
echo "[replication-marker] 复制将在容器启动后由 deploy.sh 自动配置"
echo "[replication-marker] init-replication.sh 现在包含 mysqldump 数据同步，无需额外操作"
echo "[replication-marker] ======================================"
