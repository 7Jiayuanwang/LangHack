#!/bin/bash

# ========================================
# 腾讯云一键部署脚本
# 使用方法: bash auto-deploy.sh
# ========================================

set -e  # 遇到错误立即退出

echo "========================================="
echo "🚀 英语学习平台 - 腾讯云自动部署脚本"
echo "========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置变量（请根据实际情况修改）
MYSQL_ROOT_PASSWORD="your_mysql_password"  # 修改为您的 MySQL root 密码
PROJECT_DIR="/root/english-learning-full"
DB_NAME="english_learning"

# 检查是否为 root 用户
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}❌ 请使用 root 用户运行此脚本${NC}"
    exit 1
fi

# 步骤 1: 安装必要软件
echo -e "${GREEN}📦 步骤 1/7: 安装必要软件...${NC}"

# 检测操作系统
if [ -f /etc/redhat-release ]; then
    OS="centos"
    echo "检测到 CentOS 系统"
elif [ -f /etc/lsb-release ]; then
    OS="ubuntu"
    echo "检测到 Ubuntu 系统"
else
    echo -e "${RED}❌ 不支持的操作系统${NC}"
    exit 1
fi

# 更新系统
echo "更新系统..."
if [ "$OS" = "centos" ]; then
    yum update -y
else
    apt update && apt upgrade -y
fi

# 安装 JDK 17
echo "安装 JDK 17..."
if ! command -v java &> /dev/null; then
    if [ "$OS" = "centos" ]; then
        yum install -y java-17-openjdk java-17-openjdk-devel
    else
        apt install -y openjdk-17-jdk
    fi
fi

# 安装 Maven
echo "安装 Maven..."
if ! command -v mvn &> /dev/null; then
    if [ "$OS" = "centos" ]; then
        yum install -y maven
    else
        apt install -y maven
    fi
fi

# 安装 Node.js
echo "安装 Node.js..."
if ! command -v node &> /dev/null; then
    curl -fsSL https://rpm.nodesource.com/setup_16.x | bash - 2>/dev/null || \
    curl -fsSL https://deb.nodesource.com/setup_16.x | bash -

    if [ "$OS" = "centos" ]; then
        yum install -y nodejs
    else
        apt install -y nodejs
    fi
fi

# 安装 MySQL
echo "安装 MySQL 8.0..."
if ! command -v mysql &> /dev/null; then
    if [ "$OS" = "centos" ]; then
        wget https://dev.mysql.com/get/mysql80-community-release-el7-3.noarch.rpm
        rpm -ivh mysql80-community-release-el7-3.noarch.rpm
        yum install -y mysql-server
    else
        apt install -y mysql-server
    fi

    systemctl start mysqld || systemctl start mysql
    systemctl enable mysqld || systemctl enable mysql
fi

# 安装 Nginx
echo "安装 Nginx..."
if ! command -v nginx &> /dev/null; then
    if [ "$OS" = "centos" ]; then
        yum install -y nginx
    else
        apt install -y nginx
    fi

    systemctl start nginx
    systemctl enable nginx
fi

echo -e "${GREEN}✅ 软件安装完成${NC}"
echo ""

# 步骤 2: 配置 MySQL
echo -e "${GREEN}📊 步骤 2/7: 配置 MySQL 数据库...${NC}"

# 设置 MySQL root 密码（如果是新安装）
if [ "$OS" = "centos" ]; then
    TEMP_PASS=$(grep 'temporary password' /var/log/mysqld.log 2>/dev/null | awk '{print $NF}' || echo "")
    if [ -n "$TEMP_PASS" ]; then
        mysql --connect-expired-password -u root -p"$TEMP_PASS" <<EOF
ALTER USER 'root'@'localhost' IDENTIFIED BY '$MYSQL_ROOT_PASSWORD';
FLUSH PRIVILEGES;
EOF
    fi
fi

# 创建数据库
mysql -u root -p"$MYSQL_ROOT_PASSWORD" <<EOF
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EOF

# 导入数据
if [ -f "$PROJECT_DIR/COMPLETE_DATABASE.sql" ]; then
    echo "导入数据库..."
    mysql -u root -p"$MYSQL_ROOT_PASSWORD" $DB_NAME < "$PROJECT_DIR/COMPLETE_DATABASE.sql"
fi

echo -e "${GREEN}✅ 数据库配置完成${NC}"
echo ""

# 步骤 3: 修改后端配置
echo -e "${GREEN}⚙️  步骤 3/7: 修改后端配置...${NC}"

cd "$PROJECT_DIR/backend"

# 修改 application.yml
sed -i "s/password: root/password: $MYSQL_ROOT_PASSWORD/g" src/main/resources/application.yml
sed -i "s/serverTimezone=GMT%2B8/serverTimezone=Asia\/Shanghai/g" src/main/resources/application.yml

echo -e "${GREEN}✅ 后端配置完成${NC}"
echo ""

# 步骤 4: 构建后端
echo -e "${GREEN}🔨 步骤 4/7: 构建后端...${NC}"

mvn clean package -DskipTests

if [ ! -f "target/english-learning-platform-1.0.0.jar" ]; then
    echo -e "${RED}❌ 后端构建失败${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 后端构建完成${NC}"
echo ""

# 步骤 5: 创建后端服务
echo -e "${GREEN}🔧 步骤 5/7: 创建后端服务...${NC}"

cat > /etc/systemd/system/english-backend.service <<EOF
[Unit]
Description=English Learning Platform Backend
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$PROJECT_DIR/backend
ExecStart=/usr/bin/java -jar $PROJECT_DIR/backend/target/english-learning-platform-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable english-backend
systemctl restart english-backend

# 等待后端启动
echo "等待后端启动..."
sleep 10

if systemctl is-active --quiet english-backend; then
    echo -e "${GREEN}✅ 后端服务启动成功${NC}"
else
    echo -e "${RED}❌ 后端服务启动失败，查看日志: journalctl -u english-backend -xe${NC}"
fi

echo ""

# 步骤 6: 构建前端
echo -e "${GREEN}🎨 步骤 6/7: 构建前端...${NC}"

cd "$PROJECT_DIR/frontend"

# 修改 API 地址为使用 Nginx 代理
sed -i "s|baseURL:.*|baseURL: '/api',|g" src/utils/request.js

# 安装依赖并构建
npm install --production
npm run build

if [ ! -d "dist" ]; then
    echo -e "${RED}❌ 前端构建失败${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 前端构建完成${NC}"
echo ""

# 步骤 7: 配置 Nginx
echo -e "${GREEN}🌐 步骤 7/7: 配置 Nginx...${NC}"

# 获取服务器 IP
SERVER_IP=$(curl -s ifconfig.me)

cat > /etc/nginx/conf.d/english-learning.conf <<EOF
server {
    listen 80;
    server_name $SERVER_IP _;

    root $PROJECT_DIR/frontend/dist;
    index index.html;

    location / {
        try_files \$uri \$uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    client_max_body_size 50M;
}
EOF

# 删除默认配置
rm -f /etc/nginx/sites-enabled/default
rm -f /etc/nginx/conf.d/default.conf

# 测试并重启 Nginx
nginx -t
systemctl restart nginx

echo -e "${GREEN}✅ Nginx 配置完成${NC}"
echo ""

# 完成
echo "========================================="
echo -e "${GREEN}🎉 部署完成！${NC}"
echo "========================================="
echo ""
echo -e "${YELLOW}📋 部署信息：${NC}"
echo "🌐 访问地址: http://$SERVER_IP"
echo "👤 默认账户: admin"
echo "🔑 默认密码: admin123"
echo ""
echo -e "${YELLOW}📊 服务状态：${NC}"
systemctl status english-backend --no-pager | grep Active
systemctl status nginx --no-pager | grep Active
systemctl status mysqld --no-pager | grep Active 2>/dev/null || systemctl status mysql --no-pager | grep Active
echo ""
echo -e "${YELLOW}📖 查看日志：${NC}"
echo "后端日志: journalctl -u english-backend -f"
echo "Nginx 日志: tail -f /var/log/nginx/error.log"
echo ""
echo -e "${GREEN}✅ 请在浏览器中访问: http://$SERVER_IP${NC}"
echo "========================================="
