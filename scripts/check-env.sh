#!/bin/bash

# 环境检查脚本
# 检查项目运行所需的环境依赖

echo "=========================================="
echo "  环境检查脚本"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查结果标志
ALL_PASSED=true

# 检查函数：比较版本号
# 用法: version_compare "实际版本" "最低要求版本"
version_compare() {
    local actual=$1
    local required=$2
    
    # 提取主版本号（第一个数字）
    local actual_major=$(echo "$actual" | grep -oE '^[0-9]+' | head -1)
    local required_major=$(echo "$required" | grep -oE '^[0-9]+' | head -1)
    
    if [ -z "$actual_major" ] || [ -z "$required_major" ]; then
        return 1
    fi
    
    if [ "$actual_major" -gt "$required_major" ]; then
        return 0
    elif [ "$actual_major" -eq "$required_major" ]; then
        # 主版本号相同，比较次版本号
        local actual_minor=$(echo "$actual" | grep -oE '^[0-9]+\.[0-9]+' | cut -d. -f2)
        local required_minor=$(echo "$required" | grep -oE '^[0-9]+\.[0-9]+' | cut -d. -f2)
        
        if [ -z "$actual_minor" ]; then
            actual_minor=0
        fi
        if [ -z "$required_minor" ]; then
            required_minor=0
        fi
        
        if [ "$actual_minor" -ge "$required_minor" ]; then
            return 0
        fi
    fi
    
    return 1
}

# 检查 JDK 11+
echo -n "检查 JDK 11+ ... "
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')
    JAVA_MAJOR=$(echo "$JAVA_VERSION" | grep -oE '^[0-9]+')
    
    if [ -n "$JAVA_MAJOR" ] && [ "$JAVA_MAJOR" -ge 11 ]; then
        echo -e "${GREEN}✓ 通过${NC} (版本: $JAVA_VERSION)"
    else
        echo -e "${RED}✗ 失败${NC} (当前版本: $JAVA_VERSION, 需要: JDK 11+)"
        ALL_PASSED=false
    fi
else
    echo -e "${RED}✗ 失败${NC} (未安装 JDK)"
    ALL_PASSED=false
fi

# 检查 Maven 3.6+
echo -n "检查 Maven 3.6+ ... "
if command -v mvn &> /dev/null; then
    MAVEN_VERSION=$(mvn -version 2>&1 | head -n 1 | awk '{print $3}')
    if version_compare "$MAVEN_VERSION" "3.6"; then
        echo -e "${GREEN}✓ 通过${NC} (版本: $MAVEN_VERSION)"
    else
        echo -e "${RED}✗ 失败${NC} (当前版本: $MAVEN_VERSION, 需要: Maven 3.6+)"
        ALL_PASSED=false
    fi
else
    echo -e "${RED}✗ 失败${NC} (未安装 Maven)"
    ALL_PASSED=false
fi

# 检查 Node.js 16+
echo -n "检查 Node.js 16+ ... "
if command -v node &> /dev/null; then
    NODE_VERSION=$(node -v | sed 's/v//')
    NODE_MAJOR=$(echo "$NODE_VERSION" | grep -oE '^[0-9]+')
    
    if [ -n "$NODE_MAJOR" ] && [ "$NODE_MAJOR" -ge 16 ]; then
        echo -e "${GREEN}✓ 通过${NC} (版本: $NODE_VERSION)"
    else
        echo -e "${RED}✗ 失败${NC} (当前版本: $NODE_VERSION, 需要: Node.js 16+)"
        ALL_PASSED=false
    fi
else
    echo -e "${RED}✗ 失败${NC} (未安装 Node.js)"
    ALL_PASSED=false
fi

# 检查 MySQL 8.0+
echo -n "检查 MySQL 8.0+ ... "
if command -v mysql &> /dev/null; then
    MYSQL_VERSION=$(mysql --version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
    if [ -n "$MYSQL_VERSION" ]; then
        MYSQL_MAJOR=$(echo "$MYSQL_VERSION" | cut -d. -f1)
        MYSQL_MINOR=$(echo "$MYSQL_VERSION" | cut -d. -f2)
        
        if [ "$MYSQL_MAJOR" -gt 8 ] || ([ "$MYSQL_MAJOR" -eq 8 ] && [ "$MYSQL_MINOR" -ge 0 ]); then
            echo -e "${GREEN}✓ 通过${NC} (版本: $MYSQL_VERSION)"
        else
            echo -e "${RED}✗ 失败${NC} (当前版本: $MYSQL_VERSION, 需要: MySQL 8.0+)"
            ALL_PASSED=false
        fi
    else
        echo -e "${YELLOW}⚠ 警告${NC} (无法获取 MySQL 版本，请手动确认版本 >= 8.0)"
    fi
else
    echo -e "${RED}✗ 失败${NC} (未安装 MySQL 或 MySQL 不在 PATH 中)"
    echo -e "${YELLOW}  提示: 如果已安装 MySQL，请确保 mysql 命令在 PATH 中${NC}"
    ALL_PASSED=false
fi

echo ""
echo "=========================================="

# 输出最终结果
if [ "$ALL_PASSED" = true ]; then
    echo -e "${GREEN}✓ 所有环境检查通过！${NC}"
    echo ""
    exit 0
else
    echo -e "${RED}✗ 部分环境检查失败，请安装或更新相应的软件${NC}"
    echo ""
    echo "安装指南："
    echo "  - Ubuntu/Debian: 运行 ./install-env-ubuntu.sh"
    echo "  - 详细指南: 查看 DETAILED_RUN_GUIDE.md"
    echo ""
    exit 1
fi

