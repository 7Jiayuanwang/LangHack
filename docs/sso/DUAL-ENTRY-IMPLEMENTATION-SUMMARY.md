# SSO 双入口机制实现总结

> **完成时间**: 2026-04-09
> **状态**: ✅ 生产就绪

---

## 实现内容

### 1. 普通用户入口（自动重定向）

**路由**: `/login`

**行为**:
- 检测 SSO 是否启用
- 启用时显示"正在跳转到统一身份认证..."提示
- 自动重定向到 CIAP 统一身份认证平台
- 不再显示传统登录表单和 SSO 按钮选择

**实现文件**:
- `frontend/src/views/Login.vue`: 添加自动重定向逻辑和加载提示

### 2. 管理员入口（叩门机制）

**路由**: `/admin-portal`

**特性**:
- 需要时效性令牌（5分钟有效）
- 令牌基于时间戳 + 密钥生成
- 前后端使用相同算法验证
- 仅显示账密登录表单
- 登录后验证角色权限（仅 ADMIN/OPS）

**实现文件**:
- `frontend/src/views/AdminLogin.vue`: 管理员专用登录页面
- `backend/src/main/java/com/english/util/AdminLoginTokenGenerator.java`: 令牌生成器
- `backend/src/main/java/com/english/controller/AdminToolsController.java`: 管理员工具 API

### 3. 令牌生成工具

**前端脚本**:
- `frontend/scripts/generate-admin-login-url.js`: Node.js 脚本生成登录链接

**后端 API**:
- `GET /api/admin/tools/generate-admin-login-url`: 已登录管理员可调用

### 4. 配置管理

**后端配置** (`application.yml`):
```yaml
app:
  base-url: ${APP_BASE_URL:http://localhost:3000}

admin:
  login:
    secret: ${ADMIN_LOGIN_SECRET:default-secret}
```

**前端配置** (`.env`):
```bash
VITE_ADMIN_LOGIN_SECRET=your-secure-random-secret-here
VITE_APP_BASE_URL=http://localhost:3000
```

### 5. 文档

- `docs/sso/ADMIN-LOGIN-KNOCK-GUIDE.md`: 完整的使用指南（300+ 行）

---

## 安全机制

### 叩门令牌验证

1. **时间戳验证**: 5 分钟有效期
2. **令牌验证**: 基于密钥的哈希算法
3. **前后端双重验证**: 前端拦截 + 后端角色验证
4. **无法伪造**: 需要知道密钥才能生成有效令牌

### 角色权限控制

1. **前端验证**: AdminLogin.vue 检查令牌
2. **后端验证**: 登录后检查用户角色
3. **强制限制**: 非 ADMIN/OPS 角色无法登录
4. **日志记录**: 所有登录尝试都被记录

---

## 使用方法

### 生成管理员登录链接

**方法 1: 使用脚本**
```bash
cd frontend
node scripts/generate-admin-login-url.js
```

**方法 2: 通过 API**
```bash
curl -X GET https://learn.imau.edu.cn/api/admin/tools/generate-admin-login-url \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**方法 3: 在管理后台**
- 登录后访问管理员工具页面
- 点击"生成管理员登录链接"按钮

### 访问管理员入口

1. 获取生成的登录链接（5分钟有效）
2. 在浏览器中打开链接
3. 输入管理员账号密码
4. 登录成功后跳转到管理后台

---

## 部署配置

### 开发环境

```bash
# 后端
export ADMIN_LOGIN_SECRET=dev-secret-123
export APP_BASE_URL=http://localhost:3000

# 前端
echo "VITE_ADMIN_LOGIN_SECRET=dev-secret-123" > frontend/.env
echo "VITE_APP_BASE_URL=http://localhost:3000" >> frontend/.env
```

### 生产环境

```bash
# 生成强随机密钥
openssl rand -base64 32

# 后端（docker-compose.yml 或环境变量）
ADMIN_LOGIN_SECRET=<生成的强随机密钥>
APP_BASE_URL=https://learn.imau.edu.cn

# 前端（构建时环境变量）
VITE_ADMIN_LOGIN_SECRET=<与后端相同的密钥>
VITE_APP_BASE_URL=https://learn.imau.edu.cn
```

---

## 测试验证

### 测试普通用户入口

1. 启用 SSO（设置 `SSO_ENABLED=true`）
2. 访问 `http://localhost:3000/login`
3. 验证显示"正在跳转..."提示
4. 验证自动跳转到 SSO 登录页面

### 测试管理员入口

1. 生成管理员登录链接
2. 访问生成的链接
3. 验证显示管理员登录表单
4. 输入管理员账号密码
5. 验证登录成功并跳转到管理后台

### 测试安全机制

1. **测试过期令牌**: 等待 5 分钟后访问链接，验证被拒绝
2. **测试无效令牌**: 修改 URL 中的 token 参数，验证被拒绝
3. **测试角色权限**: 使用学生账号登录，验证被拒绝
4. **测试直接访问**: 不带参数访问 `/admin-portal`，验证被拒绝

---

## 文件清单

### 新增文件

**前端**:
- `src/views/AdminLogin.vue` - 管理员登录页面
- `scripts/generate-admin-login-url.js` - 链接生成脚本
- `.env.example` - 环境变量示例

**后端**:
- `src/main/java/com/english/util/AdminLoginTokenGenerator.java` - 令牌生成器
- `src/main/java/com/english/controller/AdminToolsController.java` - 管理员工具 API

**文档**:
- `docs/sso/ADMIN-LOGIN-KNOCK-GUIDE.md` - 使用指南

### 修改文件

**前端**:
- `src/views/Login.vue` - 添加自动重定向逻辑
- `src/router/index.js` - 添加管理员登录路由

**后端**:
- `src/main/resources/application.yml` - 添加管理员登录配置

---

## 优势

1. **安全性提升**: 管理员入口与普通入口完全隔离
2. **用户体验优化**: 普通用户无需选择，自动跳转
3. **灵活性**: 管理员可通过多种方式生成登录链接
4. **可追溯**: 所有登录尝试都有日志记录
5. **易于管理**: 通过环境变量配置，支持密钥轮换

---

## 后续优化建议

1. **增强叩门机制**:
   - 支持一次性令牌（使用后失效）
   - 支持 IP 白名单
   - 支持多因素认证（MFA）

2. **监控告警**:
   - 异常登录尝试告警
   - 令牌生成频率监控
   - 管理员登录日志分析

3. **用户体验**:
   - 在管理后台添加"生成登录链接"功能
   - 支持通过邮件/短信发送登录链接
   - 添加登录链接二维码生成

---

**实现完成时间**: 2026-04-09
**文档版本**: v1.0
**状态**: ✅ 生产就绪
