@echo off
chcp 65001 >nul
title LangHack 批量导入真题

echo ========================================
echo   LangHack 批量导入真题
echo ========================================
echo.
echo 支持标准格式的 .md 和 .json，可递归扫描子目录。
echo 文件名包含“四级/六级/考研”时可以自动识别分类。
echo.

set /p "PAPER_DIR=请输入真题文件或目录的完整路径: "
if "%PAPER_DIR%"=="" (
  echo [错误] 路径不能为空。
  pause
  exit /b 1
)

set /p "PAPER_CATEGORY=分类（cet4/cet6/postgraduate，自动识别请直接回车）: "
set /p "SERVER_URL=LangHack 服务地址（例如 http://localhost:8080）: "
set /p "ADMIN_TOKEN=管理员 JWT Token: "

if "%SERVER_URL%"=="" (
  echo [错误] 服务地址不能为空。
  pause
  exit /b 1
)
if "%ADMIN_TOKEN%"=="" (
  echo [错误] 管理员 Token 不能为空。
  pause
  exit /b 1
)

cd /d "%~dp0frontend"
if "%PAPER_CATEGORY%"=="" (
  call npm run import:papers -- "%PAPER_DIR%" --server "%SERVER_URL%" --token "%ADMIN_TOKEN%"
) else (
  call npm run import:papers -- "%PAPER_DIR%" --category "%PAPER_CATEGORY%" --server "%SERVER_URL%" --token "%ADMIN_TOKEN%"
)

if errorlevel 1 (
  echo.
  echo [失败] 未修改现有题库，请根据上面的文件错误修正后重试。
) else (
  echo.
  echo [成功] 真题已上传到共享题库，所有学生刷新页面后即可看到。
)

pause
