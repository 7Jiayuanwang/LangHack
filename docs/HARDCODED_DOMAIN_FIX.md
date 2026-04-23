# 硬编码域名修复报告

## 问题描述

检查代码中是否存在硬编码的 `langhack.imau.edu.cn` 导致 `isa.imau.edu.cn` 无法正常使用。

---

## 检查结果

### ✅ 已修复的硬编码

| 文件 | 行号 | 原代码 | 修复后 | 影响 |
|------|------|--------|--------|------|
| `frontend/src/config/site.js` | 4 | `ARTICLES_PATH: 'https://langhack.imau.edu.cn/articles'` | `ARTICLES_PATH: '/articles'` | **高** - 文章页面跳转会固定到 langhack 域名 |

**修复说明**:
- 将绝对路径改为相对路径 `/articles`
- 现在会自动使用当前访问的域名
- 在 `langhack.imau.edu.cn` 上访问 → 跳转到 `https://langhack.imau.edu.cn/articles`
- 在 `isa.imau.edu.cn` 上访问 → 跳转到 `https://isa.imau.edu.cn/articles`

### ✅ 无需修复的硬编码（仅为示例/注释）

| 文件 | 类型 | 说明 |
|------|------|------|
| `backend/src/main/resources/application-sso.yml` | 配置注释 | 仅为配置示例，实际使用环境变量 |
| `backend/src/main/java/com/english/sso/controller/SsoAuthController.java` | 错误提示 | 异常消息中的示例域名 |
| `backend/src/main/java/com/english/sso/config/SsoProperties.java` | JavaDoc | 文档注释中的示例 |
| `backend/src/test/java/com/english/sso/service/OAuth2SsoServiceTest.java` | 单元测试 | 测试用例的 mock 数据 |

---

## 配置验证

### 关键环境变量（必须正确配置）

以下环境变量决定了实际运行时使用的域名，**不受代码中硬编码影响**：

```bash
# SSO 配置（后端）
SSO_CAS_SERVER_NAME=https://your-domain.com  # ← 决定 SSO 回调地址
APP_BASE_URL=https://your-domain.com         # ← 决定前端重定向地址

# CORS 配置（后端）
APP_CORS_ALLOWED_ORIGINS=https://your-domain.com  # ← 决定允许的前端域名

# SSL 配置（前端）
SSL_DOMAIN=your-domain.com                   # ← 决定 SSL 证书域名
```

### 配置检查命令

在服务器上运行以下命令，确认配置正确：

```bash
# 检查后端环境变量
docker exec langhack-backend env | grep -E "SSO_CAS_SERVER_NAME|APP_BASE_URL|APP_CORS"

# 检查前端构建参数
docker inspect langhack-frontend | jq '.[0].Config.Env' | grep -E "VITE_"

# 检查 .env 文件
grep -E "SSO_CAS_SERVER_NAME|APP_BASE_URL|APP_CORS|SSL_DOMAIN" docker/.env
```

---

## 部署验证

### 应用服务器 (langhack.imau.edu.cn)

```bash
# 1. 检查文章跳转
curl -I https://langhack.imau.edu.cn/articles
# 预期: 200 OK

# 2. 检查 SSO 回调
curl -I https://langhack.imau.edu.cn/api/auth/sso/callback
# 预期: 302 重定向到 CAS 登录页

# 3. 检查 CORS
curl -H "Origin: https://langhack.imau.edu.cn" \
     -H "Access-Control-Request-Method: GET" \
     -X OPTIONS https://langhack.imau.edu.cn/api/health
# 预期: Access-Control-Allow-Origin: https://langhack.imau.edu.cn
```

### 开发服务器 (isa.imau.edu.cn)

```bash
# 1. 检查文章跳转
curl -I https://isa.imau.edu.cn/articles
# 预期: 200 OK

# 2. 检查 SSO 回调
curl -I https://isa.imau.edu.cn/api/auth/sso/callback
# 预期: 302 重定向到 CAS 登录页

# 3. 检查 CORS
curl -H "Origin: https://isa.imau.edu.cn" \
     -H "Access-Control-Request-Method: GET" \
     -X OPTIONS https://isa.imau.edu.cn/api/health
# 预期: Access-Control-Allow-Origin: https://isa.imau.edu.cn
```

---

## 前端重新构建

修复 `site.js` 后，需要重新构建前端镜像：

### 方法一：完整重新部署（推荐）

```bash
cd /path/to/LangHack
git pull
bash scripts/deploy.sh
# 选择: 全量重建 (--no-cache)
```

### 方法二：仅重建前端

```bash
cd /path/to/LangHack
git pull

# 重建前端镜像
docker compose build --no-cache frontend

# 重启前端容器
docker compose up -d frontend

# 验证
docker logs langhack-frontend
```

### 方法三：本地构建后复制（快速）

```bash
# 在本地开发机
cd frontend
npm run build

# 复制到服务器
scp -r dist/ user@server:/tmp/frontend-dist/

# 在服务器上
docker cp /tmp/frontend-dist/. langhack-frontend:/usr/share/nginx/html/
docker exec langhack-frontend nginx -s reload
```

---

## 测试场景

### 场景 1: 文章页面跳转

**操作**: 在文章阅读页面点击"返回文章列表"按钮

**预期**:
- 在 `langhack.imau.edu.cn` 上 → 跳转到 `https://langhack.imau.edu.cn/articles`
- 在 `isa.imau.edu.cn` 上 → 跳转到 `https://isa.imau.edu.cn/articles`

**验证代码位置**:
- `frontend/src/views/user/SimpleTextView.vue:707`
- `frontend/src/views/student/InteractiveText.vue:1107`

### 场景 2: SSO 登录回调

**操作**: 访问首页，触发 SSO 登录

**预期**:
- 在 `langhack.imau.edu.cn` 上 → 回调到 `https://langhack.imau.edu.cn/api/auth/sso/callback`
- 在 `isa.imau.edu.cn` 上 → 回调到 `https://isa.imau.edu.cn/api/auth/sso/callback`

**验证方法**:
```bash
# 查看后端日志中的 service URL
docker logs langhack-backend | grep "CAS service URL"
```

### 场景 3: CORS 跨域请求

**操作**: 前端调用后端 API

**预期**:
- 在 `langhack.imau.edu.cn` 上 → 请求头 `Origin: https://langhack.imau.edu.cn` 被允许
- 在 `isa.imau.edu.cn` 上 → 请求头 `Origin: https://isa.imau.edu.cn` 被允许

**验证方法**:
```bash
# 浏览器 F12 → Network → 查看 API 请求的响应头
# 应包含: Access-Control-Allow-Origin: https://当前域名
```

---

## 回归测试清单

修复后需要在**两台服务器**上分别测试以下功能:

- [ ] 首页访问正常
- [ ] SSO 登录成功
- [ ] 文章列表加载正常
- [ ] 文章阅读页面正常
- [ ] 点击"返回文章列表"跳转到正确域名
- [ ] 管理员叩门入口可访问
- [ ] 运维面板功能正常
- [ ] 学校数据同步正常（如已启用）

---

## 总结

### 修复内容
✅ 修复了 `frontend/src/config/site.js` 中硬编码的 `langhack.imau.edu.cn`

### 影响范围
- **前端**: 文章页面跳转逻辑
- **后端**: 无影响（已使用环境变量）

### 部署要求
- 需要重新构建前端镜像
- 两台服务器需要独立配置 `.env` 文件

### 验证状态
- [x] 代码检查完成
- [ ] 应用服务器验证（待部署后测试）
- [ ] 开发服务器验证（待部署后测试）

---

最后更新: 2026-04-21
