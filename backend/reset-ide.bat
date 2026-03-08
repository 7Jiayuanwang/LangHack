@echo off
echo ========================================
echo 重置IDE配置 - 强制使用Java 17
echo ========================================

:: 停止所有Java和IDE进程
echo 停止IDE相关进程...
taskkill /f /im idea64.exe 2>nul
taskkill /f /im idea.exe 2>nul
taskkill /f /im java.exe 2>nul

:: 清理项目缓存
echo 清理项目缓存...
if exist target (
    echo 删除target文件夹...
    rmdir /s /q target
)

if exist .idea (
    echo 删除IDE配置文件夹...
    rmdir /s /q .idea
)

if exist .gradle (
    echo 删除Gradle缓存...
    rmdir /s /q .gradle
)

:: 设置环境变量
echo 设置Java 17环境变量...
set JAVA_HOME=D:\Appdizhi\JAVA\jdk-17.0.12+7
set MAVEN_HOME=D:\Appdizhi\MAVEN\apache-maven-3.9.6
set Path=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%Path%

:: 验证Java版本
echo 验证Java版本...
java -version
echo.

:: 验证Maven版本
echo 验证Maven版本...
mvn -version
echo.

:: 使用Maven重新编译
echo 使用Maven重新编译项目...
mvn clean compile

echo ========================================
echo 重置完成！
echo 请重新打开IDE并导入项目：
echo 1. File -^> Open -^> 选择项目文件夹
echo 2. 确保选择JDK 17
echo 3. 重新导入Maven项目
echo ========================================
pause