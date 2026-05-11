#!/bin/bash
# 诊断文章管理页面问题

echo "=== 诊断文章管理页面问题 ==="
echo ""

echo "1. 检查数据库中的文章数量："
docker exec langhack-mysql-master mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-root123}" english_learning -e "SELECT COUNT(*) as total FROM texts WHERE deleted = 0;"
echo ""

echo "2. 检查文章表结构（是否缺少字段）："
docker exec langhack-mysql-master mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-root123}" english_learning -e "SHOW COLUMNS FROM texts;" | grep -E "featured|priority|content_type"
echo ""

echo "3. 测试 API 端点（获取文章列表）："
curl -s -X GET "http://localhost:8080/api/admin/texts?page=1&size=10" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" | jq -r '.code, .message, .data.total' 2>/dev/null || echo "需要替换 YOUR_TOKEN_HERE"
echo ""

echo "4. 检查后端日志（最近的错误）："
docker logs langhack-backend --tail 50 2>&1 | grep -i "error\|exception" | tail -10
echo ""

echo "5. 检查前端构建时间："
docker exec langhack-frontend ls -lh /usr/share/nginx/html/assets/*.js | head -3
echo ""

echo "=== 诊断完成 ==="
