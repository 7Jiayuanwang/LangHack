#!/bin/bash
# Script to start the backend with the correct JDK version (JDK 17)
# 支持 macOS (Homebrew)、Linux (OpenJDK) 和已配置 JAVA_HOME 的环境

# 如果未设置 JAVA_HOME 或当前 Java 版本不是 17，则尝试自动检测
CURRENT_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ -z "$JAVA_HOME" ] || [ "$CURRENT_VERSION" != "17" ]; then
    # macOS Homebrew 路径
    MACOS_JAVA="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    # 常见 Linux x86_64 路径
    LINUX_JAVA_AMD64="/usr/lib/jvm/java-17-openjdk-amd64"
    LINUX_JAVA_GENERIC="/usr/lib/jvm/java-17-openjdk"

    if [ -d "$MACOS_JAVA" ]; then
        export JAVA_HOME="$MACOS_JAVA"
    elif [ -d "$LINUX_JAVA_AMD64" ]; then
        export JAVA_HOME="$LINUX_JAVA_AMD64"
    elif [ -d "$LINUX_JAVA_GENERIC" ]; then
        export JAVA_HOME="$LINUX_JAVA_GENERIC"
    else
        echo "警告: 未能自动检测 JDK 17，将使用系统默认 Java。"
        echo "如遇问题，请手动执行: export JAVA_HOME=<JDK17路径>"
    fi

    if [ -n "$JAVA_HOME" ]; then
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

echo "Starting backend with JAVA_HOME=${JAVA_HOME:-<系统默认>}"
java -version

# Ensure we are in the script's directory (backend/)
cd "$(dirname "$0")"

# Run the backend
mvn spring-boot:run
