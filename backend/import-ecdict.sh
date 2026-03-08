#!/bin/bash

# ECDICT 数据导入脚本
# 用于一次性将 ECDICT CSV 数据导入到数据库

echo "==================================="
echo "  ECDICT 数据导入工具"
echo "==================================="
echo ""

# 读取数据库配置：优先使用环境变量，未设置则读取 .env 文件
if [ -f "../.env" ]; then
    set -a; source "../.env"; set +a
elif [ -f ".env" ]; then
    set -a; source ".env"; set +a
fi

DB_HOST="${DB_HOST:-localhost}"
DB_USER="${DB_USERNAME:-root}"
DB_PASS="${DB_PASSWORD}"

if [ -z "$DB_PASS" ]; then
    echo "错误: 未设置 DB_PASSWORD 环境变量"
    echo "请先设置: export DB_PASSWORD=<your_password>"
    echo "或在项目根目录创建 .env 文件，包含 DB_PASSWORD=<your_password>"
    exit 1
fi

MYSQL_CMD="mysql -h $DB_HOST -u $DB_USER -p$DB_PASS"

# 检查 ECDICT 文件是否存在
ECDICT_FILE=""
if [ -f "ECDICT-master/ecdict.csv" ]; then
    ECDICT_FILE="ECDICT-master/ecdict.csv"
elif [ -f "../ECDICT-master/ecdict.csv" ]; then
    ECDICT_FILE="../ECDICT-master/ecdict.csv"
else
    echo "错误: 找不到 ECDICT 数据文件"
    echo "请确保 ECDICT-master/ecdict.csv 文件存在"
    echo ""
    echo "下载地址: https://github.com/skywind3000/ECDICT"
    exit 1
fi

echo "✓ 找到 ECDICT 文件: $ECDICT_FILE"
echo ""

# 检查数据库连接
echo "检查数据库连接..."
$MYSQL_CMD -e "USE english_learning;" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "错误: 无法连接到数据库"
    echo "请检查数据库配置（application.yml）"
    exit 1
fi

echo "✓ 数据库连接正常"
echo ""

# 检查表是否存在
TABLE_EXISTS=$($MYSQL_CMD -D english_learning -e "SHOW TABLES LIKE 'ecdict_words';" 2>/dev/null | grep -c "ecdict_words")

if [ $TABLE_EXISTS -eq 0 ]; then
    echo "创建 ecdict_words 表..."
    $MYSQL_CMD -D english_learning < src/main/resources/db/migration/V4__create_ecdict_words_table.sql
    if [ $? -eq 0 ]; then
        echo "✓ 表创建成功"
    else
        echo "错误: 表创建失败"
        exit 1
    fi
else
    echo "✓ ecdict_words 表已存在"
fi

echo ""

# 检查是否已有数据
ROW_COUNT=$($MYSQL_CMD -D english_learning -e "SELECT COUNT(*) FROM ecdict_words;" 2>/dev/null | tail -n 1)

if [ "$ROW_COUNT" -gt 0 ]; then
    echo "警告: 数据库中已有 $ROW_COUNT 条数据"
    echo "是否要清空并重新导入？(y/n)"
    read -r CONFIRM
    if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
        echo "导入已取消"
        exit 0
    fi

    echo "清空现有数据..."
    $MYSQL_CMD -D english_learning -e "TRUNCATE TABLE ecdict_words;"
    echo "✓ 数据已清空"
fi

echo ""
echo "开始导入数据..."
echo "这可能需要 5-10 分钟，请耐心等待..."
echo ""

# 编译项目
echo "编译项目..."
mvn clean compile -DskipTests -q

if [ $? -ne 0 ]; then
    echo "编译失败，请检查错误信息"
    exit 1
fi

# 运行导入工具
java -cp "target/classes:$(mvn dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout -q)" \
    com.english.util.ECDICTImporter

if [ $? -eq 0 ]; then
    echo ""
    echo "==================================="
    echo "  导入完成！"
    echo "==================================="
    echo ""
    echo "现在可以正常启动后端服务："
    echo "  mvn spring-boot:run"
    echo ""
else
    echo ""
    echo "导入失败，请检查错误信息"
    exit 1
fi
