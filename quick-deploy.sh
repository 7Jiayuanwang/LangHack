#!/bin/bash
# ========================================
# 超简化一键部署命令
# 直接在腾讯云终端复制粘贴执行
# ========================================

# 第一步：设置您的 MySQL 密码（修改这里！）
export MYSQL_PASS="MyPassword123!"

# 第二步：下载部署脚本并执行
curl -fsSL https://gitee.com/mirrors/english-learning-full/raw/main/auto-deploy.sh | bash -s "$MYSQL_PASS"

# 如果上面的命令失败，使用下面的完整命令：

# 安装必要工具
yum install -y git wget curl || apt install -y git wget curl

# 克隆项目
cd /root
git clone https://gitee.com/your-username/english-learning-full.git
cd english-learning-full

# 修改密码
sed -i "s/your_mysql_password/$MYSQL_PASS/g" auto-deploy.sh

# 执行部署
chmod +x auto-deploy.sh
bash auto-deploy.sh
