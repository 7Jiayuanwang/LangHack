@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

:: ========================================
:: LangHack 英语学习平台 - Docker 部署脚本 (Windows)
:: ========================================

title LangHack Docker 部署

:: 检查命令参数
if "%1"=="" goto :help
if "%1"=="start" goto :start
if "%1"=="stop" goto :stop
if "%1"=="restart" goto :restart
if "%1"=="build" goto :build
if "%1"=="rebuild" goto :rebuild
if "%1"=="logs" goto :logs
if "%1"=="status" goto :status
if "%1"=="clean" goto :clean
if "%1"=="help" goto :help
goto :help

:check_docker
echo [INFO] 检查 Docker 环境...
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker 未安装，请先安装 Docker Desktop
    exit /b 1
)
docker compose version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Compose 未安装
    exit /b 1
)
echo [SUCCESS] Docker 环境检查通过
exit /b 0

:setup_env
if not exist .env (
    if exist .env.example (
        copy .env.example .env >nul
        echo [WARNING] 已从 .env.example 创建 .env 文件，请检查配置
    )
)
exit /b 0

:start
call :check_docker
if errorlevel 1 exit /b 1
call :setup_env
echo [INFO] 正在启动服务...
docker compose up -d
if errorlevel 1 (
    echo [ERROR] 启动失败
    exit /b 1
)
echo.
echo [SUCCESS] 服务已启动
echo.
echo [INFO] 服务访问地址:
echo   前端: http://localhost:80
echo   后端: http://localhost:8080
echo   MySQL: localhost:3306
echo.
echo [INFO] 默认管理员账号: admin / admin123
goto :eof

:stop
call :check_docker
if errorlevel 1 exit /b 1
echo [INFO] 正在停止服务...
docker compose down
echo [SUCCESS] 服务已停止
goto :eof

:restart
call :stop
call :start
goto :eof

:build
call :check_docker
if errorlevel 1 exit /b 1
call :setup_env
echo [INFO] 正在构建镜像...
docker compose build
echo [SUCCESS] 镜像构建完成
goto :eof

:rebuild
call :check_docker
if errorlevel 1 exit /b 1
call :setup_env
echo [INFO] 正在重新构建并启动...
docker compose down
docker compose build --no-cache
docker compose up -d
echo [SUCCESS] 重建并启动完成
goto :eof

:logs
call :check_docker
if errorlevel 1 exit /b 1
docker compose logs -f
goto :eof

:status
call :check_docker
if errorlevel 1 exit /b 1
echo [INFO] 服务状态:
docker compose ps
goto :eof

:clean
call :check_docker
if errorlevel 1 exit /b 1
echo [WARNING] 这将删除所有容器、镜像和数据卷
set /p confirm="确定要继续吗? (y/N) "
if /i "!confirm!"=="y" (
    echo [INFO] 正在清理...
    docker compose down -v --rmi all
    echo [SUCCESS] 清理完成
) else (
    echo [INFO] 已取消
)
goto :eof

:help
echo.
echo LangHack 英语学习平台 - Docker 部署脚本
echo.
echo 用法: docker.bat [命令]
echo.
echo 命令:
echo   start       启动所有服务
echo   stop        停止所有服务
echo   restart     重启所有服务
echo   build       构建镜像
echo   rebuild     重新构建并启动
echo   logs        查看日志
echo   status      查看服务状态
echo   clean       清理容器和镜像
echo   help        显示帮助信息
echo.
echo 示例:
echo   docker.bat start    # 启动项目
echo   docker.bat logs     # 查看日志
echo.
goto :eof
