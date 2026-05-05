#!/bin/bash
# ==============================================================================
# LangHack 本地热部署脚本 (hot-update.sh)
# 合并了 start.sh 的本地进程管理 + deploy.sh 的安全数据库迁移逻辑
# 适用场景：后端修改代码、新增数据库字段后，一键应用修改并重启前后端服务。
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ─────────────────────────────────────────────
# 0. 加载环境变量
# ─────────────────────────────────────────────
if [ -f "$PROJECT_ROOT/.env" ]; then
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
fi

DB_USER=${DB_USERNAME:-root}
DB_PW=${DB_PASSWORD:-}
RUN_DB_MIGRATIONS=${HOT_UPDATE_RUN_MIGRATIONS:-true}

# 创建日志目录
mkdir -p "$PROJECT_ROOT/logs"

# ─────────────────────────────────────────────
# 菜单
# ─────────────────────────────────────────────
show_menu() {
    echo ""
    echo "╔══════════════════════════════════════════╗"
    echo "║     LangHack 本地部署与迁移脚本         ║"
    echo "╠══════════════════════════════════════════╣"
    echo "║  [1] 重启前后端服务（跳过数据库迁移）   ║"
    echo "║  [2] 仅同步数据库（不启动服务）         ║"
    echo "║  [3] 全量执行（数据库 + 前后端）         ║"
    echo "║  [0] 退出                                ║"
    echo "╚══════════════════════════════════════════╝"
    echo ""
}

read_choice() {
    echo -n "请选择操作 [0-3]: "
    read -r CHOICE
}

# ─────────────────────────────────────────────
# 数据库迁移（公共函数）
# ─────────────────────────────────────────────
run_db_migration() {
    if [ "$RUN_DB_MIGRATIONS" != "true" ]; then
        echo "⚠️  HOT_UPDATE_RUN_MIGRATIONS=false，已按配置跳过数据库迁移。"
        echo "   整合版迁移链适合作为全新数据库初始稳定版；如需执行请先确认数据库策略并备份。"
        return 0
    fi

    echo "🗄️ 应用数据库增量迁移..."
    FLYWAY_DIR="$PROJECT_ROOT/backend/src/main/resources/db/migration"
    MIGRATION_SQL="$PROJECT_ROOT/docker/mysql/init/04-apply-all-migrations.sql"

    if [ -f "$MIGRATION_SQL" ] && [ -d "$FLYWAY_DIR" ]; then
        flyway_count=$(ls "$FLYWAY_DIR"/V*.sql 2>/dev/null | wc -l | tr -d ' ' || echo 0)
        merged_count=$(grep -cE '^--   V[0-9]+__' "$MIGRATION_SQL" 2>/dev/null | tr -d ' ' || echo 0)
        if [ "$flyway_count" -gt "$merged_count" ]; then
            echo "❌ [错误] 迁移脚本版本不同步！"
            echo "   Flyway 目录有 ${flyway_count} 个文件，但合并脚本只有 ${merged_count} 个版本块。"
            echo "   👉 请先将新增的 V*.sql 复制追加到 docker/mysql/init/04-apply-all-migrations.sql 中！"
            return 1
        fi
        echo "✅ 脚本同步校验通过。"

        if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "langhack-mysql-master"; then
            echo "   🐳 检测到本地 Docker MySQL，正在注入合并脚本..."
            docker exec -i langhack-mysql-master mysql -u"$DB_USER" -p"$DB_PW" english_learning < "$MIGRATION_SQL" 2>/dev/null
            echo "✅ 数据库更新完成 (Docker 模式)。"
        elif command -v mysql &>/dev/null; then
            echo "   💻 检测到本地 mysql 客户端，正在连接 127.0.0.1 注入脚本..."
            mysql -h 127.0.0.1 -u"$DB_USER" -p"$DB_PW" english_learning < "$MIGRATION_SQL" 2>/dev/null
            echo "✅ 数据库更新完成 (本地模式)。"
        else
            echo "⚠️  未检测到运行中的 Docker MySQL 或本地 mysql 客户端，跳过手动 SQL 注入。"
        fi
    else
        echo "⚠️  跳过数据库迁移（合并脚本或 Flyway 目录不存在）。"
    fi
}

# ─────────────────────────────────────────────
# 停止已有服务（公共函数）
# ─────────────────────────────────────────────
stop_existing_services() {
    echo "📋 检查并停止已有服务..."

    # Windows MINGW / Git Bash 兼容：统一用 ps aux + grep 检测进程
    EXISTING_BACKEND=$(ps aux 2>/dev/null | grep "spring-boot:run" | grep -v grep | awk '{print $2}' | tr -d ' ' || true)
    EXISTING_FRONTEND=$(ps aux 2>/dev/null | grep -E "vite|node.*3000" | grep -v grep | awk '{print $2}' | tr -d ' ' || true)

    if [ -n "$EXISTING_BACKEND" ] || [ -n "$EXISTING_FRONTEND" ]; then
        echo "🔄 发现正在运行的服务，正在重启..."
        [ -n "$EXISTING_BACKEND" ] && kill $EXISTING_BACKEND 2>/dev/null && echo "   ⏹ 后端服务已停止 (PID: $EXISTING_BACKEND)"
        [ -n "$EXISTING_FRONTEND" ] && kill $EXISTING_FRONTEND 2>/dev/null && echo "   ⏹ 前端服务已停止 (PID: $EXISTING_FRONTEND)"
        sleep 3
    else
        echo "   未发现已运行的服务，直接启动。"
    fi
}

# ─────────────────────────────────────────────
# 启动后端
# ─────────────────────────────────────────────
start_backend() {
    echo "🔧 启动后端服务..."
    cd "$PROJECT_ROOT/backend"
    nohup mvn spring-boot:run > "$PROJECT_ROOT/logs/backend.log" 2>&1 &
    BACKEND_PID=$!
    echo "✅ 后端服务已启动 (PID: $BACKEND_PID)"

    echo "⏳ 等待后端服务启动..."
    health_wait=0
    while [ $health_wait -lt 60 ]; do
        if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
            echo ""
            echo "✅ 后端服务健康检查通过"
            return 0
        fi
        sleep 3
        health_wait=$((health_wait + 3))
        printf "."
    done
    echo ""
    echo "⚠️  后端服务启动超时，请检查日志: logs/backend.log"
}

# ─────────────────────────────────────────────
# 启动前端
# ─────────────────────────────────────────────
start_frontend() {
    echo "🎨 启动前端服务..."
    cd "$PROJECT_ROOT/frontend"
    nohup npm run dev > "$PROJECT_ROOT/logs/frontend.log" 2>&1 &
    FRONTEND_PID=$!
    echo "✅ 前端服务已启动 (PID: $FRONTEND_PID)"

    echo "⏳ 等待前端服务启动..."
    sleep 10

    if curl -s -I http://localhost:3000 > /dev/null 2>&1; then
        echo "✅ 前端服务健康检查通过"
    else
        echo "⚠️  前端服务可能启动失败，请检查日志: logs/frontend.log"
    fi
}

# ─────────────────────────────────────────────
# 选项 1: 仅重启前后端
# ─────────────────────────────────────────────
option_restart() {
    echo ""
    echo "══════════════════════════════"
    echo "  [1] 重启前后端服务"
    echo "══════════════════════════════"

    stop_existing_services
    start_backend
    start_frontend

    echo ""
    echo "🎉 前后端已重启完成！"
    echo ""
    echo "📋 访问地址："
    echo "   管理端: http://localhost:3000/admin"
    echo "   教师端: http://localhost:3000/teacher"
    echo "   学生端: http://localhost:3000/student"
    echo "   后端API: http://localhost:8080"
}

# ─────────────────────────────────────────────
# 选项 2: 仅同步数据库
# ─────────────────────────────────────────────
option_db_only() {
    echo ""
    echo "══════════════════════════════"
    echo "  [2] 仅同步数据库"
    echo "══════════════════════════════"

    run_db_migration

    echo ""
    echo "✅ 数据库同步完成。"
}

# ─────────────────────────────────────────────
# 选项 3: 全量执行
# ─────────────────────────────────────────────
option_full() {
    echo ""
    echo "══════════════════════════════"
    echo "  [3] 全量执行（数据库 + 前后端）"
    echo "══════════════════════════════"

    stop_existing_services
    run_db_migration
    start_backend
    start_frontend

    echo ""
    echo "🎉 全量部署完成！"
    echo ""
    echo "📋 访问地址："
    echo "   管理端: http://localhost:3000/admin"
    echo "   教师端: http://localhost:3000/teacher"
    echo "   学生端: http://localhost:3000/student"
    echo "   后端API: http://localhost:8080"
}

# ─────────────────────────────────────────────
# 主循环
# ─────────────────────────────────────────────
while true; do
    show_menu
    read_choice

    case "$CHOICE" in
        1)
            option_restart
            break
            ;;
        2)
            option_db_only
            break
            ;;
        3)
            option_full
            break
            ;;
        0)
            echo "已退出。"
            exit 0
            ;;
        *)
            echo "无效选择，请输入 0-3"
            ;;
    esac
done
