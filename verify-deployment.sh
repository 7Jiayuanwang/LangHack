#!/bin/bash
# 验证部署是否包含最新修改

echo "=== 验证部署状态 ==="
echo ""

echo "1. 检查 Nginx 配置（备份下载超时）："
docker exec langhack-frontend grep -A2 "backup.*download" /etc/nginx/nginx.conf | grep "proxy_read_timeout"
echo ""

echo "2. 检查前端构建时间："
docker exec langhack-frontend ls -lh /usr/share/nginx/html/assets/*.js | head -3
echo ""

echo "3. 检查前端文件内容（功能开关批量添加）："
docker exec langhack-frontend grep -c "添加用户到标签" /usr/share/nginx/html/assets/*.js 2>/dev/null | grep -v ":0" | head -5
echo ""

echo "4. 检查容器创建时间："
docker ps --format "table {{.Names}}\t{{.CreatedAt}}" | grep langhack
echo ""

echo "5. 当前 Git 版本："
cd /data/LangHack && git log --oneline -1
