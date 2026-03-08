#!/bin/bash

# DevTools 自动重启测试脚本

echo "=== DevTools 自动重启测试 ==="
echo ""

# 1. 测试当前版本
echo "1. 测试当前版本..."
RESPONSE=$(curl -s http://localhost:8080/api/test/devtools)
echo "   响应: $RESPONSE"
echo ""

# 2. 修改代码
echo "2. 修改测试控制器..."
sed -i 's/Version 2/Version 3/g' /home/teruun/projects/LangHack/backend/src/main/java/com/english/test/DevToolsTestController.java
echo "   ✓ 已修改代码"
echo ""

# 3. 编译
echo "3. 编译代码..."
cd /home/teruun/projects/LangHack/backend
mvn compile -q -DskipTests
echo "   ✓ 编译完成"
echo ""

# 4. 等待DevTools重启
echo "4. 等待 DevTools 自动重启（10秒）..."
for i in {10..1}; do
    echo -n "   $i..."
    sleep 1
done
echo ""
echo ""

# 5. 测试新版本
echo "5. 测试新版本..."
NEW_RESPONSE=$(curl -s http://localhost:8080/api/test/devtools)
echo "   响应: $NEW_RESPONSE"
echo ""

# 6. 验证结果
if [[ "$NEW_RESPONSE" == *"Version 3"* ]]; then
    echo "✅ DevTools 自动重启成功！"
    echo "   代码已自动重新加载，无需手动重启服务器"
else
    echo "❌ DevTools 可能未工作"
    echo "   请检查配置或手动重启服务器"
fi

echo ""
echo "=== 测试完成 ==="
