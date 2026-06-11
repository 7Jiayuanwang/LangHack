#!/bin/bash
set -e

echo "=== 安全修复测试脚本 ==="

# 1. 检查端口绑定
echo -e "\n[1/6] 检查 MySQL/Redis 端口绑定..."
if netstat -tlnp 2>/dev/null | grep -E "127.0.0.1:(3306|3307|6379)"; then
    echo "✅ 端口已绑定到 127.0.0.1"
else
    echo "⚠️  端口配置未生效，需要重启服务"
fi

# 2. 测试 API 限流
echo -e "\n[2/6] 测试 API 限流..."
echo "发送 6 次登录请求..."
for i in {1..6}; do
    response=$(curl -s -w "%{http_code}" -o /dev/null -X POST http://localhost:8080/api/auth/login \
        -H "Content-Type: application/json" -d '{"username":"test","password":"test"}')
    echo "请求 $i: HTTP $response"
    if [ "$response" = "429" ]; then
        echo "✅ 限流生效！"
        break
    fi
done

# 3. 测试 CORS
echo -e "\n[3/6] 测试 CORS 限制..."
response=$(curl -s -w "%{http_code}" -o /dev/null -H "Origin: http://unauthorized.com" http://localhost:8080/api/health)
if [ "$response" = "403" ] || [ "$response" = "401" ]; then
    echo "✅ CORS 限制生效"
else
    echo "⚠️  CORS 未生效: HTTP $response"
fi

# 4. 测试健康检查保护
echo -e "\n[4/6] 测试健康检查端点保护..."
response=$(curl -s -w "%{http_code}" -o /dev/null http://localhost:8080/api/health)
if [ "$response" = "401" ]; then
    echo "✅ 健康检查已保护（需要认证）"
else
    echo "⚠️  健康检查可公开访问: HTTP $response"
fi

# 5. 检查 Docker 容器用户
echo -e "\n[5/6] 检查容器运行用户..."
backend_user=$(docker exec langhack-backend whoami 2>/dev/null || echo "容器未运行")
if [ "$backend_user" = "root" ]; then
    echo "⚠️  容器以 root 运行（建议修复）"
elif [ "$backend_user" != "容器未运行" ]; then
    echo "✅ 容器以非root用户运行: $backend_user"
else
    echo "❌ 后端容器未运行"
fi

# 6. 检查日志文件权限
echo -e "\n[6/6] 检查日志写入权限..."
if docker exec langhack-backend ls -la /app/logs/ 2>/dev/null | grep -q "\.log"; then
    echo "✅ 日志文件正常"
else
    echo "⚠️  日志目录为空或无权限"
fi

echo -e "\n=== 测试完成 ==="
