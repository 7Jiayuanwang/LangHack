# 多服务器部署指南

本文档说明如何在多台服务器上部署 LangHack 项目。

---

## 服务器清单

| 服务器 | 域名 | 用途 | 环境 |
|--------|------|------|------|
| 应用服务器 | https://langhack.imau.edu.cn/ | 生产环境 | production |
| 开发服务器 | https://isa.imau.edu.cn/ | 开发/测试环境 | development |

---

## 部署前检查清单

### 1. 域名与 SSL 证书

**应用服务器 (langhack.imau.edu.cn)**
- [ ] 域名 DNS 解析已配置
- [ ] SSL 证书已申请（Let's Encrypt 或自签名）
- [ ] 证书路径: `/etc/letsencrypt/live/langhack.imau.edu.cn/`

**开发服务器 (isa.imau.edu.cn)**
- [ ] 域名 DNS 解析已配置
- [ ] SSL 证书已申请
- [ ] 证书路径: `/etc/letsencrypt/live/isa.imau.edu.cn/`

### 2. SSO 统一身份认证配置

**关键**: 两台服务器需要在统一身份认证平台注册不同的回调地址

**应用服务器回调地址**:
```
https://langhack.imau.edu.cn/api/auth/sso/callback
```

**开发服务器回调地址**:
```
https://isa.imau.edu.cn/api/auth/sso/callback
```

> ⚠️ 必须在 `https://ids.imau.edu.cn/authserver` 管理后台注册这两个回调地址

### 3. CORS 跨域配置

每台服务器的 `APP_CORS_ALLOWED_ORIGINS` 必须包含自己的域名:

**应用服务器**:
```bash
APP_CORS_ALLOWED_ORIGINS=https://langhack.imau.edu.cn
```

**开发服务器**:
```bash
APP_CORS_ALLOWED_ORIGINS=https://isa.imau.edu.cn,http://localhost:5173
```

### 4. 学校数据 API 配置

两台服务器可以共享同一套 `SCHOOL_API_APP_ID` 和 `SCHOOL_API_APP_SECRET`,或申请独立凭证。

---

## 部署步骤

### 应用服务器 (langhack.imau.edu.cn)

```bash
# 1. 登录服务器
ssh user@langhack.imau.edu.cn

# 2. 克隆代码
git clone <repo-url> LangHack
cd LangHack

# 3. 运行部署向导
bash scripts/deploy.sh

# 4. 向导配置要点
#    - 分支: master
#    - HTTPS: 启用
#    - SSL 域名: langhack.imau.edu.cn
#    - CORS: https://langhack.imau.edu.cn
#    - SSO: 启用
#    - SSO_CAS_SERVER_NAME: https://langhack.imau.edu.cn
#    - APP_BASE_URL: https://langhack.imau.edu.cn
#    - 学校数据: 根据需要启用
```

### 开发服务器 (isa.imau.edu.cn)

```bash
# 1. 登录服务器
ssh user@isa.imau.edu.cn

# 2. 克隆代码
git clone <repo-url> LangHack
cd LangHack

# 3. 运行部署向导
bash scripts/deploy.sh

# 4. 向导配置要点
#    - 分支: develop (或 master)
#    - HTTPS: 启用
#    - SSL 域名: isa.imau.edu.cn
#    - CORS: https://isa.imau.edu.cn,http://localhost:5173
#    - SSO: 启用
#    - SSO_CAS_SERVER_NAME: https://isa.imau.edu.cn
#    - APP_BASE_URL: https://isa.imau.edu.cn
#    - 学校数据: 可选（开发环境可使用测试数据）
```

---

## 配置文件对比

### 应用服务器 `docker/.env` (关键配置)

```bash
# HTTPS
HTTPS_ENABLED=true
PORT_HTTPS=443
SSL_CERT_PATH=/etc/letsencrypt/live/langhack.imau.edu.cn/fullchain.pem
SSL_KEY_PATH=/etc/letsencrypt/live/langhack.imau.edu.cn/privkey.pem
SSL_DOMAIN=langhack.imau.edu.cn

# CORS
APP_CORS_ALLOWED_ORIGINS=https://langhack.imau.edu.cn

# SSO
SSO_ENABLED=true
SSO_CAS_SERVER_URL=https://ids.imau.edu.cn/authserver
SSO_CAS_SERVER_NAME=https://langhack.imau.edu.cn
APP_BASE_URL=https://langhack.imau.edu.cn

# 管理员叩门入口（生产环境使用强随机密钥）
ADMIN_LOGIN_SECRET=<生成的随机密钥>
ADMIN_PORTAL_PATH=<生成的随机路径>

# 学校数据（生产凭证）
SCHOOL_API_APP_ID=<生产环境 App ID>
SCHOOL_API_APP_SECRET=<生产环境 App Secret>
SCHOOL_SYNC_ENABLED=true
```

### 开发服务器 `docker/.env` (关键配置)

```bash
# HTTPS
HTTPS_ENABLED=true
PORT_HTTPS=443
SSL_CERT_PATH=/etc/letsencrypt/live/isa.imau.edu.cn/fullchain.pem
SSL_KEY_PATH=/etc/letsencrypt/live/isa.imau.edu.cn/privkey.pem
SSL_DOMAIN=isa.imau.edu.cn

# CORS（开发环境允许本地前端）
APP_CORS_ALLOWED_ORIGINS=https://isa.imau.edu.cn,http://localhost:5173

# SSO
SSO_ENABLED=true
SSO_CAS_SERVER_URL=https://ids.imau.edu.cn/authserver
SSO_CAS_SERVER_NAME=https://isa.imau.edu.cn
APP_BASE_URL=https://isa.imau.edu.cn

# 管理员叩门入口（开发环境可使用简单密钥）
ADMIN_LOGIN_SECRET=dev-secret-2024
ADMIN_PORTAL_PATH=dev-admin

# 学校数据（测试凭证或禁用）
SCHOOL_API_APP_ID=<测试环境 App ID>
SCHOOL_API_APP_SECRET=<测试环境 App Secret>
SCHOOL_SYNC_ENABLED=false
```

---

## 部署后验证

### 1. 健康检查

**应用服务器**:
```bash
curl -k https://langhack.imau.edu.cn/api/health
# 预期: {"status":"UP"}
```

**开发服务器**:
```bash
curl -k https://isa.imau.edu.cn/api/health
# 预期: {"status":"UP"}
```

### 2. SSO 回调验证

访问以下地址,应自动跳转到统一身份认证:

- 应用服务器: https://langhack.imau.edu.cn/
- 开发服务器: https://isa.imau.edu.cn/

登录后应正确回调到各自的域名。

### 3. 学校数据 API 验证

登录运维面板 → 学校数据配置 → 测试连接

- 应用服务器: https://langhack.imau.edu.cn/#/ops/school-data-config
- 开发服务器: https://isa.imau.edu.cn/#/ops/school-data-config

---

## 常见问题

### Q1: SSO 登录后跳转到错误的域名

**原因**: `SSO_CAS_SERVER_NAME` 或 `APP_BASE_URL` 配置错误

**解决**:
```bash
# 检查配置
grep -E "SSO_CAS_SERVER_NAME|APP_BASE_URL" docker/.env

# 修改后重启
docker compose restart backend
```

### Q2: 前端显示 CORS 错误

**原因**: `APP_CORS_ALLOWED_ORIGINS` 未包含当前域名

**解决**:
```bash
# 添加域名到 CORS 白名单
echo "APP_CORS_ALLOWED_ORIGINS=https://your-domain.com" >> docker/.env
docker compose restart backend
```

### Q3: 学校数据 API 连接失败

**原因**: App ID/Secret 错误或网络不通

**解决**:
```bash
# 1. 验证凭证
grep -E "SCHOOL_API_APP_ID|SCHOOL_API_APP_SECRET" docker/.env

# 2. 测试网络连通性
docker exec langhack-backend curl -v https://cdsp.imau.edu.cn/

# 3. 查看后端日志
docker logs langhack-backend | grep -i school
```

### Q4: 两台服务器数据不同步

**说明**: 这是正常的,两台服务器使用独立的数据库

如需同步数据:
```bash
# 在应用服务器导出
docker exec langhack-mysql-master mysqldump -uroot -p"$DB_PASSWORD" \
  english_learning > backup.sql

# 传输到开发服务器
scp backup.sql user@isa.imau.edu.cn:/tmp/

# 在开发服务器导入
docker exec -i langhack-mysql-master mysql -uroot -p"$DB_PASSWORD" \
  english_learning < /tmp/backup.sql
```

---

## 端口占用检查

部署前确认端口未被占用:

```bash
# 检查 80/443/8080/3306 端口
sudo ss -tlnp | grep -E ':80|:443|:8080|:3306'

# 或使用 netstat
sudo netstat -tlnp | grep -E ':80|:443|:8080|:3306'
```

如有冲突,在 `docker/.env` 中修改端口映射:
```bash
PORT_FRONTEND=8000
PORT_BACKEND=8081
PORT_MYSQL=3307
```

---

## 自动更新配置

### 应用服务器（生产环境）

建议**禁用**自动更新,手动控制发布:
```bash
AUTO_UPDATE_ENABLED=false
```

### 开发服务器

可启用自动更新,跟踪 develop 分支:
```bash
AUTO_UPDATE_ENABLED=true
AUTO_UPDATE_CRON=0 2 * * *  # 每天凌晨 2 点
AUTO_UPDATE_BRANCH=develop
AUTO_UPDATE_PRE_BACKUP=true
```

---

## 安全建议

1. **生产环境管理员密钥**: 使用强随机密钥,定期轮换
2. **数据库密码**: 两台服务器使用不同的密码
3. **JWT 密钥**: 两台服务器使用不同的密钥（避免 token 跨环境使用）
4. **防火墙**: 仅开放 80/443 端口,其他端口仅内网访问
5. **备份**: 生产环境启用自动备份,保留 30 天

---

## 监控与日志

### 查看服务状态
```bash
docker compose ps
```

### 查看日志
```bash
# 后端日志
docker logs -f langhack-backend

# 前端日志
docker logs -f langhack-frontend

# 数据库日志
docker logs -f langhack-mysql-master
```

### 资源监控
```bash
docker stats
```

---

最后更新: 2026-04-21
