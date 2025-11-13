@echo off
REM ========================================
REM 打包项目文件用于上传到腾讯云
REM ========================================

echo 正在打包项目...
echo.

REM 设置项目路径
set PROJECT_DIR=C:\Users\COLORFUL\Desktop\english-learning-full
set OUTPUT_DIR=C:\Users\COLORFUL\Desktop
set OUTPUT_FILE=english-learning-deploy.zip

REM 进入项目目录
cd /d %PROJECT_DIR%

REM 删除旧的压缩包
if exist "%OUTPUT_DIR%\%OUTPUT_FILE%" (
    echo 删除旧的压缩包...
    del "%OUTPUT_DIR%\%OUTPUT_FILE%"
)

REM 创建压缩包（需要安装 7-Zip 或使用 PowerShell）
echo 创建压缩包...
powershell -command "Compress-Archive -Path '%PROJECT_DIR%\*' -DestinationPath '%OUTPUT_DIR%\%OUTPUT_FILE%' -Force"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo ✅ 打包完成！
    echo 📦 文件位置: %OUTPUT_DIR%\%OUTPUT_FILE%
    echo.
    echo 📋 下一步操作：
    echo 1. 使用 WinSCP 或 FileZilla 连接到腾讯云服务器
    echo 2. 上传 %OUTPUT_FILE% 到服务器 /root/ 目录
    echo 3. 在服务器上解压: unzip english-learning-deploy.zip
    echo 4. 按照 DEPLOY_TO_TENCENT_CLOUD.md 继续部署
    echo ========================================
    echo.
) else (
    echo.
    echo ❌ 打包失败！请检查错误信息。
    echo.
)

pause
