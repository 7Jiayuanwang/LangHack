#!/bin/bash
# ========================================
# LangHack 英语学习平台 - Docker 部署脚本
# ========================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示帮助信息
show_help() {
    echo "LangHack 英语学习平台 - Docker 部署脚本"
    echo ""
    echo "用法: $0 [命令]"
    echo ""
    echo "命令:"
    echo "  start       启动所有服务"
    echo "  stop        停止所有服务"
    echo "  restart     重启所有服务"
    echo "  build       构建镜像"
    echo "  rebuild     重新构建并启动"
    echo "  logs        查看日志"
    echo "  status      查看服务状态"
    echo "  clean       清理容器和镜像"
    echo "  help        显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 start    # 启动项目"
    echo "  $0 logs     # 查看日志"
}

# 检查 Docker 是否安装
check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker 未安装，请先安装 Docker"
        exit 1
    fi
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        print_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi
    print_success "Docker 环境检查通过"
}

# 获取 docker compose 命令
get_compose_cmd() {
    if docker compose version &> /dev/null 2>&1; then
        echo "docker compose"
    else
        echo "docker-compose"
    fi
}

COMPOSE_CMD=$(get_compose_cmd)

# 复制环境变量文件
setup_env() {
    if [ ! -f .env ]; then
        if [ -f .env.example ]; then
            cp .env.example .env
            print_warning "已从 .env.example 创建 .env 文件，请检查配置"
        fi
    fi
}

# 启动服务
start() {
    print_info "正在启动服务..."
    setup_env
    $COMPOSE_CMD up -d
    print_success "服务已启动"
    echo ""
    print_info "服务访问地址:"
    echo "  前端: http://localhost:${FRONTEND_PORT:-80}"
    echo "  后端: http://localhost:${BACKEND_PORT:-8080}"
    echo "  MySQL: localhost:${MYSQL_PORT:-3306}"
    echo ""
    print_info "默认管理员账号: admin / admin123"
}

# 停止服务
stop() {
    print_info "正在停止服务..."
    $COMPOSE_CMD down
    print_success "服务已停止"
}

# 重启服务
restart() {
    stop
    start
}

# 构建镜像
build() {
    print_info "正在构建镜像..."
    setup_env
    $COMPOSE_CMD build
    print_success "镜像构建完成"
}

# 重新构建并启动
rebuild() {
    print_info "正在重新构建并启动..."
    setup_env
    $COMPOSE_CMD down
    $COMPOSE_CMD build --no-cache
    $COMPOSE_CMD up -d
    print_success "重建并启动完成"
}

# 查看日志
logs() {
    $COMPOSE_CMD logs -f
}

# 查看服务状态
status() {
    print_info "服务状态:"
    $COMPOSE_CMD ps
}

# 清理
clean() {
    print_warning "这将删除所有容器、镜像和数据卷"
    read -p "确定要继续吗? (y/N) " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        print_info "正在清理..."
        $COMPOSE_CMD down -v --rmi all
        print_success "清理完成"
    else
        print_info "已取消"
    fi
}

# 主函数
main() {
    check_docker

    case "${1:-help}" in
        start)
            start
            ;;
        stop)
            stop
            ;;
        restart)
            restart
            ;;
        build)
            build
            ;;
        rebuild)
            rebuild
            ;;
        logs)
            logs
            ;;
        status)
            status
            ;;
        clean)
            clean
            ;;
        help|*)
            show_help
            ;;
    esac
}

main "$@"
