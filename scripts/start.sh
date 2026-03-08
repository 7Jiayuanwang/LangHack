#!/bin/bash

# 英语学习平台 — 本地开发启动脚本
# 用法: ./scripts/start.sh (从项目根目录运行)

# 定位项目根目录 (脚本位于 scripts/ 下)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🚀 启动英语学习平台..."
echo "📂 项目根目录: $PROJECT_ROOT"

# 加载环境变量 (从 .env 文件)
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo "📋 加载 .env 环境变量..."
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
else
    echo "⚠️  未发现 .env 文件，将使用 application.yml 默认配置。"
    echo "   (建议运行: cp .env.example .env 并编辑)"
fi

# 创建日志目录
mkdir -p "$PROJECT_ROOT/logs"

# 停止已有的后端和前端进程 (支持重启)
echo "📋 检查并停止已有服务..."
EXISTING_BACKEND=$(pgrep -f "spring-boot:run" 2>/dev/null)
EXISTING_FRONTEND=$(pgrep -f "vite.*--port" 2>/dev/null)

if [ -n "$EXISTING_BACKEND" ] || [ -n "$EXISTING_FRONTEND" ]; then
    echo "🔄 发现正在运行的服务，正在重启..."
    [ -n "$EXISTING_BACKEND" ] && kill $EXISTING_BACKEND 2>/dev/null && echo "   ⏹ 后端服务已停止 (PID: $EXISTING_BACKEND)"
    [ -n "$EXISTING_FRONTEND" ] && kill $EXISTING_FRONTEND 2>/dev/null && echo "   ⏹ 前端服务已停止 (PID: $EXISTING_FRONTEND)"
    sleep 3
else
    echo "   未发现已运行的服务，直接启动。"
fi

# 启动后端
echo "🔧 启动后端服务..."
cd "$PROJECT_ROOT/backend"
nohup mvn spring-boot:run > "$PROJECT_ROOT/logs/backend.log" 2>&1 &
BACKEND_PID=$!
echo "✅ 后端服务已启动 (PID: $BACKEND_PID)"

# 等待后端启动
echo "⏳ 等待后端服务启动..."
sleep 15

# 检查后端健康状态
if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
    echo "✅ 后端服务健康检查通过"
else
    echo "⚠️  后端服务可能启动失败，请检查日志: logs/backend.log"
fi

# 启动前端
echo "🎨 启动前端服务..."
cd "$PROJECT_ROOT/frontend"
nohup npm run dev > "$PROJECT_ROOT/logs/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "✅ 前端服务已启动 (PID: $FRONTEND_PID)"

# 等待前端启动
echo "⏳ 等待前端服务启动..."
sleep 10

# 检查前端服务
if curl -s -I http://localhost:3000 > /dev/null 2>&1; then
    echo "✅ 前端服务健康检查通过"
else
    echo "⚠️  前端服务可能启动失败，请检查日志: logs/frontend.log"
fi

echo ""
echo "🎉 英语学习平台启动完成！"
echo ""
echo "📋 访问地址："
echo "   管理端: http://localhost:3000/admin"
echo "   教师端: http://localhost:3000/teacher"
echo "   学生端: http://localhost:3000/student"
echo ""
echo "📝 日志文件："
echo "   后端日志: $PROJECT_ROOT/logs/backend.log"
echo "   前端日志: $PROJECT_ROOT/logs/frontend.log"
echo ""
echo "🛑 停止服务："
echo "   停止后端: kill $BACKEND_PID"
echo "   停止前端: kill $FRONTEND_PID"
echo "   停止所有: pkill -f 'mvn.*spring-boot:run|npm.*dev'"