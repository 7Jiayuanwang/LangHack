# 腾讯云 WebShell 终端部署指南

## 🌐 适用场景
直接在腾讯云控制台的网页终端（WebShell）中操作，无需安装 SSH 客户端。

---

## 📋 部署步骤

### 第一步：登录腾讯云控制台

1. 打开浏览器，访问：https://console.cloud.tencent.com/
2. 登录您的腾讯云账号
3. 进入 **云服务器 CVM** → **实例列表**
4. 找到您的服务器，点击右侧的 **登录** 按钮
5. 选择 **标准登录方式** 或 **VNC 登录**
6. 输入密码，进入终端

---

### 第二步：下载项目文件

#### 方法 1：从 GitHub/Gitee 克隆（推荐）

如果您已将项目推送到代码仓库：

```bash
# 进入 root 目录
cd /root

# 克隆项目
git clone https://github.com/your-username/english-learning-full.git

# 如果没有 git，先安装
yum install -y git  # CentOS
# 或
apt install -y git  # Ubuntu
```

#### 方法 2：使用腾讯云对象存储 COS（推荐用于大文件）

**在本地先上传到 COS：**

1. 在腾讯云控制台打开 **对象存储 COS**
2. 创建一个存储桶
3. 上传 `english-learning-deploy.zip`
4. 获取文件的访问 URL

**在终端下载：**

```bash
cd /root

# 使用 wget 下载（替换为您的 COS URL）
wget https://your-bucket.cos.ap-guangzhou.myqcloud.com/english-learning-deploy.zip

# 解压
unzip english-learning-deploy.zip

# 如果没有 unzip，先安装
yum install -y unzip  # CentOS
# 或
apt install -y unzip  # Ubuntu
```

#### 方法 3：使用 curl 从临时链接下载

如果您将压缩包上传到了其他地方（如网盘、临时文件分享）：

```bash
cd /root

# 下载文件
curl -L -o english-learning.zip "https://your-download-link.com/file.zip"

# 解压
unzip english-learning.zip
```

---

### 第三步：修改配置

在终端中使用 `vi` 编辑器修改配置：

```bash
cd /root/english-learning-full

# 修改自动部署脚本中的 MySQL 密码
vi auto-deploy.sh
```

**vi 编辑器使用方法：**

1. 按 `i` 进入编辑模式
2. 找到第 15 行：`MYSQL_ROOT_PASSWORD="your_mysql_password"`
3. 修改为您想要的密码，例如：`MYSQL_ROOT_PASSWORD="MyPassword123!"`
4. 按 `ESC` 退出编辑模式
5. 输入 `:wq` 然后按 `Enter` 保存并退出

或者使用更简单的 `sed` 命令直接替换：

```bash
# 直接替换密码（推荐）
sed -i 's/your_mysql_password/MyPassword123!/g' auto-deploy.sh
```

---

### 第四步：运行一键部署脚本

```bash
cd /root/english-learning-full

# 赋予执行权限
chmod +x auto-deploy.sh

# 运行部署脚本
bash auto-deploy.sh
```

**部署过程中：**
- 会自动安装所有必要软件（JDK、Maven、Node.js、MySQL、Nginx）
- 配置数据库并导入数据
- 构建后端和前端
- 启动所有服务

**预计时间：** 10-20 分钟（取决于网络速度）

---

### 第五步：查看部署结果

部署完成后，终端会显示：

```
=========================================
🎉 部署完成！
=========================================

📋 部署信息：
🌐 访问地址: http://123.456.789.123
👤 默认账户: admin
🔑 默认密码: admin123

📊 服务状态：
Active: active (running)
...

✅ 请在浏览器中访问: http://123.456.789.123
=========================================
```

复制访问地址，在浏览器中打开即可！

---

## 🔍 常用终端命令

### 查看服务状态

```bash
# 查看后端服务
systemctl status english-backend

# 查看 Nginx
systemctl status nginx

# 查看 MySQL
systemctl status mysqld  # CentOS
systemctl status mysql   # Ubuntu
```

### 查看日志

```bash
# 实时查看后端日志
journalctl -u english-backend -f

# 查看 Nginx 错误日志
tail -f /var/log/nginx/error.log

# 查看最近 50 行后端日志
journalctl -u english-backend -n 50
```

### 重启服务

```bash
# 重启后端
systemctl restart english-backend

# 重启 Nginx
systemctl restart nginx

# 重启 MySQL
systemctl restart mysqld  # 或 mysql
```

### 检查端口

```bash
# 查看 8080 端口（后端）
netstat -tunlp | grep 8080

# 查看 80 端口（Nginx）
netstat -tunlp | grep :80
```

---

## 🛠️ 手动逐步部署（如果自动脚本失败）

如果自动脚本执行失败，可以手动一步步执行：

### 1. 安装软件

```bash
# 更新系统
yum update -y  # CentOS
# 或
apt update && apt upgrade -y  # Ubuntu

# 安装 JDK 17
yum install -y java-17-openjdk java-17-openjdk-devel  # CentOS
# 或
apt install -y openjdk-17-jdk  # Ubuntu

# 验证 Java
java -version

# 安装 Maven
yum install -y maven  # CentOS
# 或
apt install -y maven  # Ubuntu

# 安装 Node.js 16
curl -fsSL https://rpm.nodesource.com/setup_16.x | bash -  # CentOS
# 或
curl -fsSL https://deb.nodesource.com/setup_16.x | bash -  # Ubuntu

yum install -y nodejs  # CentOS
# 或
apt install -y nodejs  # Ubuntu

# 验证 Node.js
node -v
npm -v

# 安装 MySQL
yum install -y mysql-server  # CentOS (如果失败，使用下面的方法)
# 或
apt install -y mysql-server  # Ubuntu

# CentOS 安装 MySQL 8.0（推荐）
wget https://dev.mysql.com/get/mysql80-community-release-el7-3.noarch.rpm
rpm -ivh mysql80-community-release-el7-3.noarch.rpm
yum install -y mysql-server

# 启动 MySQL
systemctl start mysqld  # CentOS
# 或
systemctl start mysql  # Ubuntu
systemctl enable mysqld  # 或 mysql

# 安装 Nginx
yum install -y nginx  # CentOS
# 或
apt install -y nginx  # Ubuntu

systemctl start nginx
systemctl enable nginx
```

### 2. 配置 MySQL

```bash
# CentOS - 获取临时密码
grep 'temporary password' /var/log/mysqld.log

# 登录 MySQL（输入临时密码，Ubuntu 可能直接登录）
mysql -u root -p

# 在 MySQL 命令行中执行：
ALTER USER 'root'@'localhost' IDENTIFIED BY 'MyPassword123!';
CREATE DATABASE english_learning CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
exit;

# 导入数据库
cd /root/english-learning-full
mysql -u root -pMyPassword123! english_learning < COMPLETE_DATABASE.sql
```

### 3. 构建后端

```bash
cd /root/english-learning-full/backend

# 修改数据库密码
vi src/main/resources/application.yml
# 按 i 编辑，修改 password: root 为 password: MyPassword123!
# 按 ESC，输入 :wq 保存

# 或使用 sed 直接替换
sed -i 's/password: root/password: MyPassword123!/g' src/main/resources/application.yml

# 构建
mvn clean package -DskipTests
```

### 4. 创建后端服务

```bash
# 创建服务文件
cat > /etc/systemd/system/english-backend.service <<EOF
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
EOF

# 启动服务
systemctl daemon-reload
systemctl start english-backend
systemctl enable english-backend

# 查看状态
systemctl status english-backend
```

### 5. 构建前端

```bash
cd /root/english-learning-full/frontend

# 修改 API 地址
sed -i "s|baseURL:.*|baseURL: '/api',|g" src/utils/request.js

# 安装依赖
npm install --production

# 构建
npm run build
```

### 6. 配置 Nginx

```bash
# 获取服务器 IP
curl ifconfig.me

# 创建 Nginx 配置
cat > /etc/nginx/conf.d/english-learning.conf <<'EOF'
server {
    listen 80;
    server_name _;

    root /root/english-learning-full/frontend/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    client_max_body_size 50M;
}
EOF

# 删除默认配置
rm -f /etc/nginx/sites-enabled/default
rm -f /etc/nginx/conf.d/default.conf

# 测试配置
nginx -t

# 重启 Nginx
systemctl restart nginx
```

### 7. 检查部署

```bash
# 检查后端
curl http://localhost:8080

# 检查前端
curl http://localhost

# 查看所有服务状态
systemctl status english-backend
systemctl status nginx
systemctl status mysqld  # 或 mysql
```

---

## ✅ 验证部署成功

1. **在浏览器中访问**：`http://您的服务器IP`
2. **登录测试**：
   - 用户名：`admin`
   - 密码：`admin123`
3. **功能测试**：
   - 进入课程管理
   - 点击课文管理
   - 查看是否能正常加载数据

---

## 🔥 快速命令参考

```bash
# 一键重新部署（更新后）
cd /root/english-learning-full && bash auto-deploy.sh

# 查看后端日志
journalctl -u english-backend -f

# 重启后端
systemctl restart english-backend

# 重启 Nginx
systemctl restart nginx

# 查看服务器 IP
curl ifconfig.me

# 检查端口占用
netstat -tunlp | grep -E '80|8080|3306'

# 数据库备份
mysqldump -u root -pMyPassword123! english_learning > backup.sql

# 清理旧的构建文件
cd /root/english-learning-full/backend && mvn clean
cd /root/english-learning-full/frontend && rm -rf node_modules dist
```

---

## 🚨 常见问题

### 问题 1：提示 "No package xxx available"

**解决**：先更新软件源
```bash
yum clean all && yum makecache  # CentOS
apt update  # Ubuntu
```

### 问题 2：npm install 很慢

**解决**：使用国内镜像
```bash
npm config set registry https://registry.npmmirror.com
```

### 问题 3：后端启动失败

**查看详细错误**：
```bash
journalctl -u english-backend -xe
```

常见原因：
- 数据库连接失败 → 检查密码和数据库是否创建
- 端口被占用 → `netstat -tunlp | grep 8080`
- JAR 文件不存在 → 重新执行 `mvn package`

### 问题 4：无法访问网页

1. **检查安全组**：在腾讯云控制台确保开放了 80 端口
2. **检查 Nginx**：`systemctl status nginx`
3. **检查防火墙**：
```bash
# CentOS
firewall-cmd --list-all
firewall-cmd --zone=public --add-port=80/tcp --permanent
firewall-cmd --reload

# Ubuntu
ufw status
ufw allow 80/tcp
```

---

## 📞 获取帮助

如果遇到问题，请提供以下信息：

1. 操作系统版本：`cat /etc/os-release`
2. 错误日志：`journalctl -u english-backend -n 100`
3. 服务状态：`systemctl status english-backend nginx mysqld`
4. 端口状态：`netstat -tunlp | grep -E '80|8080|3306'`

---

**祝您部署顺利！🎉**
