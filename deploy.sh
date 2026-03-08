#!/bin/bash

# ==============================================================================
# 英语学习平台 (LangHack) - Docker 一键部署脚本
# ==============================================================================

# 设置颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}🚀 开始部署英语学习平台...${NC}"

# 1. 检查环境变量文件是否配置
if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        echo -e "${YELLOW}⚠️ 未发现 .env 文件，正从 .env.example 生成...${NC}"
        cp .env.example .env
        echo -e "${RED}🛑 请先编辑 .env 文件配置好密码和 API Keys (例如 DB_PASSWORD, OPENROUTER_API_KEY) 后重新运行此脚本！${NC}"
        exit 1
    else
        echo -e "${RED}❌ 未发现 .env 且未找到 .env.example。请手动创建 .env 文件！${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}✅ 检测到 .env 文件，开始加载配置...${NC}"

# 2. 拉取最新代码 (可选)
# echo -e "${YELLOW}🔄 拉取最新代码...${NC}"
# git pull

# 3. 运行 Docker Compose
echo -e "${GREEN}🐳 正在编译和启动 Docker 容器 (这可能需要几分钟，请耐心等待)...${NC}"
docker-compose up -d --build

# 4. 检查状态
if [ $? -eq 0 ]; then
    echo -e "${GREEN}🎉 部署成功！${NC}"
    echo -e "你可以通过以下地址访问："
    echo -e "💻 学生端/教师端/管理员端: http://localhost (或服务器公网 IP)"
    echo -e "🔄 查看运行日志请运行: docker-compose logs -f"
else
    echo -e "${RED}❌ 部署失败，请检查上面的报错信息！${NC}"
fi
