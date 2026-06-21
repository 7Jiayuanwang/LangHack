#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
# Hotfix: 扩展 school_student.phone 字段长度
# 问题: CDSP 返回 32 字符 MD5 哈希，原 VARCHAR(30) 导致 73409 条记录失败
# 修复: 扩展到 VARCHAR(50)
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}======================================${NC}"
echo -e "${YELLOW}Hotfix: 扩展 phone 字段长度${NC}"
echo -e "${YELLOW}======================================${NC}"

# 读取 Docker Compose 环境变量
if [ ! -f "docker/.env" ]; then
    echo -e "${RED}错误: docker/.env 文件不存在${NC}"
    exit 1
fi

source docker/.env

# 获取 MySQL root 密码
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-}
if [ -z "$MYSQL_ROOT_PASSWORD" ]; then
    echo -e "${RED}错误: MYSQL_ROOT_PASSWORD 未设置${NC}"
    exit 1
fi

# 连接信息
MYSQL_CONTAINER="langhack-mysql-master"
MYSQL_DB="english_learning"

echo -e "${YELLOW}1. 检查当前 phone 字段长度...${NC}"
CURRENT_LENGTH=$(docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DB" -sN <<EOF
SELECT CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = '$MYSQL_DB'
  AND TABLE_NAME = 'school_student'
  AND COLUMN_NAME = 'phone';
EOF
)

if [ -z "$CURRENT_LENGTH" ]; then
    echo -e "${RED}错误: 未找到 phone 字段${NC}"
    exit 1
fi

echo -e "   当前长度: ${YELLOW}VARCHAR($CURRENT_LENGTH)${NC}"

if [ "$CURRENT_LENGTH" -ge 50 ]; then
    echo -e "${GREEN}✓ phone 字段长度已满足要求 (>= 50)，无需修改${NC}"
    exit 0
fi

echo -e "${YELLOW}2. 执行字段扩展...${NC}"
docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DB" <<'EOF'
ALTER TABLE school_student
    MODIFY COLUMN phone VARCHAR(50) NULL COMMENT '联系电话（CDSP 返回 MD5 哈希 32 字符）';
EOF

echo -e "${GREEN}✓ 字段扩展完成${NC}"

echo -e "${YELLOW}3. 验证修改结果...${NC}"
NEW_LENGTH=$(docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DB" -sN <<EOF
SELECT CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = '$MYSQL_DB'
  AND TABLE_NAME = 'school_student'
  AND COLUMN_NAME = 'phone';
EOF
)

echo -e "   修改后长度: ${GREEN}VARCHAR($NEW_LENGTH)${NC}"

if [ "$NEW_LENGTH" -ge 50 ]; then
    echo -e "${GREEN}✓ 验证成功${NC}"
else
    echo -e "${RED}✗ 验证失败：长度仍为 $NEW_LENGTH${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}Hotfix 应用完成！${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""
echo -e "下一步操作："
echo -e "1. 重新触发学生数据同步"
echo -e "2. 检查同步日志确认失败数为 0"
