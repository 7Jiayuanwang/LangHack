@echo off
echo ========================================
echo 英语学习平台 - 数据库初始化脚本
echo ========================================
echo.

REM 设置数据库连接信息
set MYSQL_USER=root
set MYSQL_PASSWORD=962464Yyy
set DB_NAME=english_learning

echo 正在初始化薄弱点分析系统数据库表...
echo.

REM 执行SQL脚本
mysql -u %MYSQL_USER% -p%MYSQL_PASSWORD% %DB_NAME% < backend\src\main\resources\db\migration\weakness_analysis_system.sql

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 数据库初始化成功！
    echo ========================================
    echo.
    echo 已创建以下表：
    echo - knowledge_points ^(知识点表^)
    echo - questions ^(题目表^)
    echo - answer_records ^(答题记录表^)
    echo - weakness_analysis ^(薄弱点分析表^)
    echo - learning_suggestions ^(学习建议表^)
    echo.
    echo 已预置29个CET知识点数据
    echo.
) else (
    echo.
    echo ========================================
    echo 数据库初始化失败！
    echo ========================================
    echo.
    echo 请检查：
    echo 1. MySQL服务是否运行
    echo 2. 数据库连接信息是否正确
    echo 3. 数据库 english_learning 是否存在
    echo.
)

pause
