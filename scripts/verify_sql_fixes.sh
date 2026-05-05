#!/bin/bash
# ═══════════════════════════════════════════════════════════════
# SQL 脚本修复验证脚本
# 用途: 验证所有 SQL 脚本修复是否成功
# 使用: bash scripts/verify_sql_fixes.sh
# ═══════════════════════════════════════════════════════════════

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "═══════════════════════════════════════════════════════════════"
echo "SQL 脚本修复验证"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PASS_COUNT=0
FAIL_COUNT=0

# 检查函数
check_file() {
    local file=$1
    local pattern=$2
    local description=$3

    echo -n "检查 $description ... "

    if grep -q "$pattern" "$file"; then
        echo -e "${RED}✗ 失败${NC}"
        echo "  文件: $file"
        echo "  发现乱码: $pattern"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        return 1
    else
        echo -e "${GREEN}✓ 通过${NC}"
        PASS_COUNT=$((PASS_COUNT + 1))
        return 0
    fi
}

# 检查文件编码
check_encoding() {
    local file=$1
    local description=$2

    echo -n "检查 $description 编码 ... "

    # 使用 file 命令检查编码
    encoding=$(file -b --mime-encoding "$file")

    if [[ "$encoding" == "utf-8" ]] || [[ "$encoding" == "us-ascii" ]]; then
        echo -e "${GREEN}✓ 通过 ($encoding)${NC}"
        PASS_COUNT=$((PASS_COUNT + 1))
        return 0
    else
        echo -e "${RED}✗ 失败 ($encoding)${NC}"
        echo "  文件: $file"
        echo "  预期: utf-8, 实际: $encoding"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        return 1
    fi
}

echo "1. 检查 Flyway 迁移脚本"
echo "─────────────────────────────────────────────"

# V12 检查
V12_FILE="$PROJECT_ROOT/backend/src/main/resources/db/migration/V12__school_data_and_videos.sql"
check_file "$V12_FILE" "????" "V12 字段映射乱码"
check_encoding "$V12_FILE" "V12"

# V13 检查
V13_FILE="$PROJECT_ROOT/backend/src/main/resources/db/migration/V13__feature_toggles_and_ai_config.sql"
check_file "$V13_FILE" "????" "V13 功能开关描述乱码"
check_encoding "$V13_FILE" "V13"

# V14 检查
V14_FILE="$PROJECT_ROOT/backend/src/main/resources/db/migration/V14__enhancements.sql"
check_file "$V14_FILE" "????" "V14 文件头注释乱码"
check_encoding "$V14_FILE" "V14"

echo ""
echo "2. 检查 Docker 初始化脚本"
echo "─────────────────────────────────────────────"

# 04-apply-all-migrations.sql 检查
INIT_FILE="$PROJECT_ROOT/docker/mysql/init/04-apply-all-migrations.sql"
check_file "$INIT_FILE" "????" "04-apply-all-migrations.sql 乱码"
check_encoding "$INIT_FILE" "04-apply-all-migrations.sql"

# 00-base-tables.sql 检查
BASE_FILE="$PROJECT_ROOT/docker/mysql/init/00-base-tables.sql"
echo -n "检查 00-base-tables.sql SSO 字段 ... "
if grep -q "sso_bound\|sso_provider\|sso_subject" "$BASE_FILE"; then
    echo -e "${RED}✗ 失败${NC}"
    echo "  文件: $BASE_FILE"
    echo "  users 表仍包含 SSO 字段，应由 V8 迁移添加"
    FAIL_COUNT=$((FAIL_COUNT + 1))
else
    echo -e "${GREEN}✓ 通过${NC}"
    PASS_COUNT=$((PASS_COUNT + 1))
fi

echo ""
echo "3. 验证修复内容"
echo "─────────────────────────────────────────────"

# 验证 V12 修复内容
echo -n "验证 V12 学院代码字段映射 ... "
if grep -q "学院代码（英文缩写）" "$V12_FILE" && \
   grep -q "学院名称（中文）" "$V12_FILE" && \
   grep -q "专业代码（英文缩写）" "$V12_FILE"; then
    echo -e "${GREEN}✓ 通过${NC}"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo -e "${RED}✗ 失败${NC}"
    echo "  缺少正确的字段映射注释"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# 验证 V13 修复内容
echo -n "验证 V13 功能开关描述 ... "
if grep -q "学生端 Lazy 选课记录同步" "$V13_FILE" && \
   grep -q "教师端学校数据功能" "$V13_FILE"; then
    echo -e "${GREEN}✓ 通过${NC}"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo -e "${RED}✗ 失败${NC}"
    echo "  缺少正确的功能开关描述"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# 验证 V14 修复内容
echo -n "验证 V14 文件头注释 ... "
if grep -q "V14: 增强功能" "$V14_FILE" && \
   grep -q "整合自: V22" "$V14_FILE"; then
    echo -e "${GREEN}✓ 通过${NC}"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo -e "${RED}✗ 失败${NC}"
    echo "  文件头注释未正确修复"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "验证结果汇总"
echo "═══════════════════════════════════════════════════════════════"
echo -e "通过: ${GREEN}$PASS_COUNT${NC}"
echo -e "失败: ${RED}$FAIL_COUNT${NC}"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}✓ 所有检查通过！SQL 脚本修复成功。${NC}"
    echo ""
    echo "后续步骤:"
    echo "1. 提交修复: git add . && git commit -m 'fix: 修复 SQL 脚本字符编码问题'"
    echo "2. 重新部署: bash scripts/deploy.sh"
    echo "3. 验证数据库: bash scripts/verify_sql_fixes.sh --database"
    exit 0
else
    echo -e "${RED}✗ 发现 $FAIL_COUNT 个问题，请检查上述失败项。${NC}"
    echo ""
    echo "修复建议:"
    echo "1. 确保所有 SQL 文件使用 UTF-8 编码保存"
    echo "2. 检查 Git 配置: git config core.quotepath false"
    echo "3. 重新运行修复脚本"
    exit 1
fi
