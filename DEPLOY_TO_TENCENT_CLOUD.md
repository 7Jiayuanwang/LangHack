# 腾讯云部署指南

## 📋 部署前准备

### 1. 购买腾讯云服务器（CVM）

**推荐配置**：
- CPU: 2核
- 内存: 4GB
- 带宽: 3Mbps
- 操作系统: **CentOS 7.6** 或 **Ubuntu 20.04**
- 系统盘: 40GB

### 2. 安全组配置

在腾讯云控制台配置安全组，开放以下端口：
- **22** - SSH 登录
- **80** - HTTP（前端访问）
- **443** - HTTPS（可选，用于 SSL）
- **8080** - 后端 API（可选，建议使用 Nginx 反向代理）
- **3306** - MySQL（建议仅内网访问，不对外开放）

---

## 🚀 部署步骤

### 第一步：连接服务器

使用 SSH 连接到您的腾讯云服务器：

```bash
ssh root@your_server_ip
# 输入密码或使用密钥登录
```

---

### 第二步：安装必要软件

#### 1. 更新系统

```bash
# CentOS
yum update -y

# Ubuntu
apt update && apt upgrade -y
```

#### 2. 安装 JDK 17

```bash
# CentOS
yum install -y java-17-openjdk java-17-openjdk-devel

# Ubuntu
apt install -y openjdk-17-jdk

# 验证安装
java -version
```

#### 3. 安装 MySQL 8.0

**CentOS:**
```bash
# 下载 MySQL yum 仓库
wget https://dev.mysql.com/get/mysql80-community-release-el7-3.noarch.rpm
rpm -ivh mysql80-community-release-el7-3.noarch.rpm

# 安装 MySQL
yum install -y mysql-server

# 启动 MySQL
systemctl start mysqld
systemctl enable mysqld

# 获取临时密码
grep 'temporary password' /var/log/mysqld.log
```

**Ubuntu:**
```bash
apt install -y mysql-server

# 启动 MySQL
systemctl start mysql
systemctl enable mysql
```

#### 4. 安装 Nginx

```bash
# CentOS
yum install -y nginx

# Ubuntu
apt install -y nginx

# 启动 Nginx
systemctl start nginx
systemctl enable nginx
```

#### 5. 安装 Node.js（用于前端构建）

```bash
# 安装 Node.js 16
curl -fsSL https://rpm.nodesource.com/setup_16.x | bash -  # CentOS
# 或
curl -fsSL https://deb.nodesource.com/setup_16.x | bash -  # Ubuntu

# CentOS
yum install -y nodejs

# Ubuntu
apt install -y nodejs

# 验证
node -v
npm -v
```

---

### 第三步：配置 MySQL 数据库

#### 1. 设置 MySQL root 密码

```bash
# 登录 MySQL
mysql -u root -p
# 输入临时密码（如果有）

# 修改 root 密码
ALTER USER 'root'@'localhost' IDENTIFIED BY 'your_strong_password';

# 创建数据库
CREATE DATABASE english_learning CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 退出
exit;
```

#### 2. 导入数据库

```bash
# 上传 COMPLETE_DATABASE.sql 到服务器（稍后说明）
mysql -u root -p english_learning < /root/COMPLETE_DATABASE.sql
```

---

### 第四步：上传项目文件

#### 方法 1：使用 Git（推荐）

**在本地先创建 Git 仓库**：

```bash
# 在您的本地 Windows 上
cd C:\Users\COLORFUL\Desktop\english-learning-full

# 初始化 Git（如果还没有）
git init
git add .
git commit -m "Initial commit"

# 推送到 GitHub 或 Gitee
git remote add origin https://github.com/your-username/english-learning-full.git
git push -u origin main
```

**在服务器上拉取**：

```bash
cd /root
git clone https://github.com/your-username/english-learning-full.git
cd english-learning-full
```

#### 方法 2：使用 SFTP/SCP

在本地使用 WinSCP、FileZilla 或命令行：

```bash
# 上传整个项目
scp -r C:\Users\COLORFUL\Desktop\english-learning-full root@your_server_ip:/root/
```

---

### 第五步：部署后端

#### 1. 修改配置文件

```bash
cd /root/english-learning-full/backend
vi src/main/resources/application.yml
```

修改以下内容：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/english_learning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_mysql_password  # 修改为您的 MySQL 密码
```

#### 2. 构建后端

```bash
cd /root/english-learning-full/backend

# 如果没有 Maven，先安装
# CentOS
yum install -y maven

# Ubuntu
apt install -y maven

# 构建项目（跳过测试）
mvn clean package -DskipTests
```

#### 3. 创建后端服务

创建 systemd 服务文件：

```bash
vi /etc/systemd/system/english-backend.service
```

内容：

```ini
[Unit]
Description=English Learning Platform Backend
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/root/english-learning-full/backend
ExecStart=/usr/bin/java -jar /root/english-learning-full/backend/target/english-learning-platform-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
systemctl daemon-reload
systemctl start english-backend
systemctl enable english-backend

# 查看状态
systemctl status english-backend

# 查看日志
journalctl -u english-backend -f
```

---

### 第六步：部署前端

#### 1. 修改前端 API 地址

```bash
cd /root/english-learning-full/frontend
vi src/utils/request.js
```

修改为生产环境 API 地址：

```javascript
const service = axios.create({
  baseURL: 'http://your_server_ip:8080',  // 修改为您的服务器 IP
  timeout: 10000
})
```

或者使用 Nginx 反向代理（推荐）：

```javascript
const service = axios.create({
  baseURL: '/api',  // 使用相对路径，通过 Nginx 代理
  timeout: 10000
})
```

#### 2. 构建前端

```bash
cd /root/english-learning-full/frontend

# 安装依赖
npm install

# 生产环境构建
npm run build
```

构建完成后，会生成 `dist` 目录。

---

### 第七步：配置 Nginx

#### 1. 创建 Nginx 配置

```bash
vi /etc/nginx/conf.d/english-learning.conf
```

内容：

```nginx
server {
    listen 80;
    server_name your_domain.com;  # 修改为您的域名或服务器 IP

    # 前端静态文件
    root /root/english-learning-full/frontend/dist;
    index index.html;

    # 前端路由
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反向代理
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 文件上传大小限制
    client_max_body_size 50M;
}
```

#### 2. 测试并重启 Nginx

```bash
# 测试配置
nginx -t

# 重启 Nginx
systemctl restart nginx
```

---

## 🌐 访问应用

### 使用 IP 访问

浏览器打开：`http://your_server_ip`

### 使用域名访问（需要先配置域名解析）

1. 在腾讯云控制台或域名服务商处添加 A 记录，指向服务器 IP
2. 等待 DNS 生效（通常几分钟到几小时）
3. 浏览器打开：`http://your_domain.com`

### 登录账户

- **用户名**: admin
- **密码**: admin123

---

## 🔒 配置 HTTPS（可选但推荐）

### 1. 安装 Certbot

```bash
# CentOS
yum install -y epel-release
yum install -y certbot python3-certbot-nginx

# Ubuntu
apt install -y certbot python3-certbot-nginx
```

### 2. 申请免费 SSL 证书

```bash
certbot --nginx -d your_domain.com
```

按提示操作，Certbot 会自动配置 Nginx HTTPS。

### 3. 自动续期

```bash
# 添加定时任务
crontab -e

# 添加以下行（每天凌晨 2 点检查并续期）
0 2 * * * /usr/bin/certbot renew --quiet
```

---

## 📊 监控和维护

### 查看后端日志

```bash
# 实时查看日志
journalctl -u english-backend -f

# 查看最近 100 行
journalctl -u english-backend -n 100
```

### 查看 Nginx 日志

```bash
# 访问日志
tail -f /var/log/nginx/access.log

# 错误日志
tail -f /var/log/nginx/error.log
```

### 重启服务

```bash
# 重启后端
systemctl restart english-backend

# 重启 Nginx
systemctl restart nginx

# 重启 MySQL
systemctl restart mysqld  # CentOS
systemctl restart mysql   # Ubuntu
```

---

## 🛠️ 故障排查

### 后端无法启动

```bash
# 查看详细日志
journalctl -u english-backend -xe

# 检查端口占用
netstat -tunlp | grep 8080

# 检查 Java 进程
ps aux | grep java
```

### 前端无法访问

```bash
# 检查 Nginx 状态
systemctl status nginx

# 检查 Nginx 配置
nginx -t

# 查看错误日志
tail -f /var/log/nginx/error.log
```

### 数据库连接失败

```bash
# 检查 MySQL 状态
systemctl status mysqld

# 测试连接
mysql -u root -p -e "SHOW DATABASES;"

# 查看 MySQL 错误日志
tail -f /var/log/mysqld.log  # CentOS
tail -f /var/log/mysql/error.log  # Ubuntu
```

---

## 📦 快速部署脚本

创建一个自动部署脚本（在服务器上）：

```bash
vi /root/deploy.sh
```

内容：

```bash
#!/bin/bash

echo "🚀 开始部署英语学习平台..."

# 进入项目目录
cd /root/english-learning-full

# 拉取最新代码（如果使用 Git）
git pull

# 构建后端
echo "📦 构建后端..."
cd backend
mvn clean package -DskipTests

# 构建前端
echo "📦 构建前端..."
cd ../frontend
npm install
npm run build

# 重启后端服务
echo "🔄 重启后端服务..."
systemctl restart english-backend

# 重启 Nginx
echo "🔄 重启 Nginx..."
systemctl restart nginx

echo "✅ 部署完成！"
echo "🌐 访问地址: http://$(curl -s ifconfig.me)"
```

赋予执行权限：

```bash
chmod +x /root/deploy.sh
```

以后更新只需运行：

```bash
/root/deploy.sh
```

---

## 📝 备份策略

### 数据库备份

创建备份脚本：

```bash
vi /root/backup_db.sh
```

内容：

```bash
#!/bin/bash
BACKUP_DIR="/root/backups"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

mysqldump -u root -pyour_password english_learning > $BACKUP_DIR/english_learning_$DATE.sql

# 保留最近 7 天的备份
find $BACKUP_DIR -name "*.sql" -mtime +7 -delete

echo "✅ 数据库备份完成: $BACKUP_DIR/english_learning_$DATE.sql"
```

设置定时任务（每天凌晨 3 点备份）：

```bash
crontab -e

# 添加
0 3 * * * /root/backup_db.sh
```

---

## ✅ 部署检查清单

部署完成后，请确认：

- [ ] 服务器安全组已开放必要端口
- [ ] MySQL 服务正常运行
- [ ] 数据库已创建并导入数据
- [ ] 后端服务正常启动（8080 端口）
- [ ] 前端已构建并部署到 Nginx
- [ ] Nginx 正常运行（80/443 端口）
- [ ] 可以通过浏览器访问应用
- [ ] 可以正常登录（admin/admin123）
- [ ] 所有功能正常使用
- [ ] 已配置数据库自动备份
- [ ] （可选）已配置 HTTPS

---

## 📞 常见问题

### 1. 端口访问不了？

检查腾讯云安全组是否开放对应端口。

### 2. 前端页面空白？

查看浏览器控制台（F12）是否有 API 请求错误，检查 `request.js` 中的 `baseURL` 配置。

### 3. 后端启动失败？

查看日志 `journalctl -u english-backend -xe`，通常是配置文件或数据库连接问题。

### 4. 数据库连接超时？

检查 MySQL 是否启动，防火墙是否阻止，用户权限是否正确。

---

**祝您部署顺利！🎉**

如有问题，请查看日志文件排查。
