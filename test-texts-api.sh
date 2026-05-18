#!/bin/bash
# 测试文章管理 API

echo "=== 测试文章管理 API ==="
echo ""

# 获取管理员 token（需要先登录）
echo "1. 登录获取 token："
TOKEN=$(curl -s -X POST "http://localhost:8080/api/public/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  echo "登录失败，请检查用户名密码"
  exit 1
fi

echo "Token: ${TOKEN:0:20}..."
echo ""

echo "2. 测试获取文章统计："
curl -s -X GET "http://localhost:8080/api/admin/texts/stats" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

echo "3. 测试获取文章列表（第1页，10条）："
curl -s -X GET "http://localhost:8080/api/admin/texts?page=1&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq '{code, message, data: {total: .data.total, records_count: (.data.records | length)}}'
echo ""

echo "4. 检查返回的第一篇文章结构："
curl -s -X GET "http://localhost:8080/api/admin/texts?page=1&size=1" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.records[0] | keys'
echo ""

echo "=== 测试完成 ==="
