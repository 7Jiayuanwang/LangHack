@echo off
echo ========================================
echo 正在初始化数据库...
echo ========================================
echo.

powershell -Command "Get-Content 'backend\src\main\resources\db\migration\weakness_analysis_system.sql' | mysql -u root -p962464Yyy english_learning"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo 成功！数据库已初始化
    echo.
    echo 验证结果...
    mysql -u root -p962464Yyy -e "USE english_learning; SELECT COUNT(*) as '知识点数量' FROM knowledge_points;"
    echo.
) else (
    echo.
    echo 失败！请使用MySQL Workbench手动执行SQL脚本
    echo 文件位置: backend\src\main\resources\db\migration\weakness_analysis_system.sql
    echo.
)

pause
