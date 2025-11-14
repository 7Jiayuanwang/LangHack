@echo off
chcp 65001 > nul
echo ========================================
echo 英语学习平台 - 数据库初始化
echo ========================================
echo.

REM 设置变量
set MYSQL_USER=root
set MYSQL_PASSWORD=962464Yyy
set DB_NAME=english_learning
set SQL_FILE=backend\src\main\resources\db\migration\weakness_analysis_system.sql

echo 正在初始化数据库表...
echo SQL文件: %SQL_FILE%
echo.

REM 使用type命令输出SQL内容并通过管道传给mysql
type "%SQL_FILE%" | mysql -u %MYSQL_USER% -p%MYSQL_PASSWORD% %DB_NAME%

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 初始化成功！
    echo ========================================
    echo.
    echo 已创建以下表：
    echo   - knowledge_points ^(知识点表^)
    echo   - questions ^(题目表^)  
    echo   - answer_records ^(答题记录表^)
    echo   - weakness_analysis ^(薄弱点分析表^)
    echo   - learning_suggestions ^(学习建议表^)
    echo.
    echo 已预置29个CET知识点
    echo.
    echo 现在可以启动后端服务了！
) else (
    echo.
    echo ========================================
    echo 初始化失败！错误代码: %ERRORLEVEL%
    echo ========================================
    echo.
    echo 请检查：
    echo   1. MySQL服务是否运行？
    echo      检查方法: services.msc 查找 MySQL
    echo.
    echo   2. 密码是否正确？
    echo      当前使用密码: 962464Yyy
    echo.
    echo   3. 数据库是否存在？
    echo      执行: mysql -u root -p -e "SHOW DATABASES;"
    echo.
)

pause
