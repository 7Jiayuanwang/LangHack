#!/bin/bash
# 添加标签的脚本

API_BASE="http://localhost:8080"

echo "=== 步骤 1: 管理员登录 ==="
# 用管理员账号登录（假设默认管理员 admin/admin123）
LOGIN_RESPONSE=$(curl -s -X POST "${API_BASE}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

echo "登录响应: $LOGIN_RESPONSE"

# 提取 token
TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | sed 's/"token":"\(.*\)"/\1/')

if [ -z "$TOKEN" ]; then
  echo "❌ 登录失败，无法获取 token"
  exit 1
fi

echo "✅ 登录成功，token: ${TOKEN:0:20}..."

echo ""
echo "=== 步骤 2: 添加标签 ==="

# 定义标签
declare -a TAGS=(
  '{"name":"日常对话","color":"#409EFF","sortOrder":1}'
  '{"name":"科技文章","color":"#67C23A","sortOrder":2}'
  '{"name":"新闻时事","color":"#E6A23C","sortOrder":3}'
  '{"name":"文学作品","color":"#F56C6C","sortOrder":4}'
  '{"name":"学术论文","color":"#909399","sortOrder":5}'
)

for tag_data in "${TAGS[@]}"; do
  tag_name=$(echo "$tag_data" | grep -o '"name":"[^"]*"' | sed 's/"name":"\(.*\)"/\1/')
  echo "正在添加标签: $tag_name"

  RESPONSE=$(curl -s -X POST "${API_BASE}/api/admin/content-tags" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "$tag_data")

  echo "  响应: $RESPONSE"
done

echo ""
echo "=== 完成 ==="
echo "标签已添加，现在可以在上传页面看到这些标签了！"
