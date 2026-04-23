# 管理员登录叩门机制使用指南

> **版本**: v1.0
> **更新时间**: 2026-04-09
> **状态**: 生产就绪

---

## 一、功能概述

为了提高系统安全性，LangHack 实现了双入口登录机制：

- **普通用户入口**: `/login` - 启用 SSO 后自动重定向到统一身份认证
- **管理员入口**: `/admin-portal` - 需要叩门令牌，仅供管理员和运维使用

### 核心特性

✅ **自动重定向**: 普通用户访问 `/login` 时自动跳转到 SSO
✅ **叩门机制**: 管理员入口需要时效性令牌（5分钟有效）
✅ **角色验证**: 登录后验证用户角色，非管理员/运维无法访问
✅ **安全隔离**: 管理员入口与普通入口完全隔离

---

## 二、工作原理

### 2.1 普通用户登录流程

```
用户访问 /login
    ↓
检测 SSO 是否启用
    ↓
是 → 显示"正在跳转..."提示
    ↓
自动调用 handleSsoLogin()
    ↓
跳转到 CIAP 统一身份认证
    ↓
认证成功后回调到 /sso/callback
    ↓
根据角色跳转到对应页面
```

### 2.2 管理员登录流程

```
管理员获取登录链接
    ↓
访问 /admin-portal?token=xxx&t=timestamp
    ↓
前端验证令牌和时间戳
    ↓
验证通过 → 显示账密登录表单
    ↓
输入管理员账号密码
    ↓
后端验证角色权限
    ↓
仅 ADMIN/OPS 角色可登录
```

### 2.3 叩门令牌生成算法

```javascript
function generateToken(timestamp, secret) {
  const str = timestamp + secret;
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash;
  }
  return Math.abs(hash).toString(36);
}
```

**特点**:
- 基于时间戳 + 密钥生成
- 前后端使用相同算法
- 5 分钟有效期
- 无法伪造（需要知道密钥）

---

## 三、配置说明

### 3.1 后端配置

**application.yml**:
```yaml
app:
  base-url: ${APP_BASE_URL:http://localhost:3000}

admin:
  login:
    secret: ${ADMIN_LOGIN_SECRET:default-secret}
```

**环境变量**:
```bash
# 生产环境必须设置
ADMIN_LOGIN_SECRET=your-secure-random-secret-here
APP_BASE_URL=https://learn.imau.edu.cn
```

**生成强随机密钥**:
```bash
# Linux/Mac
openssl rand -base64 32

# 或使用 Node.js
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

### 3.2 前端配置

**创建 .env 文件**:
```bash
cd frontend
cp .env.production.example .env
```

**编辑 .env**:
```bash
# 必须与后端保持一致
VITE_ADMIN_LOGIN_SECRET=your-secure-random-secret-here
VITE_APP_BASE_URL=http://localhost:3000
```

---

## 四、使用方法

### 4.1 方法一：使用脚本生成链接

```bash
cd frontend

# 开发环境
node scripts/generate-admin-login-url.js

# 生产环境
node scripts/generate-admin-login-url.js --base-url https://learn.imau.edu.cn
```

**输出示例**:
```
==============================================
管理员登录链接生成器
==============================================

配置信息:
  基础 URL: https://learn.imau.edu.cn
  密钥: ✓ 自定义密钥
  时间戳: 1712654321000
  令牌: abc123xyz
  有效期: 5 分钟

生成的管理员登录链接:
  https://learn.imau.edu.cn/admin-portal?token=abc123xyz&t=1712654321000

==============================================
```

### 4.2 方法二：通过 API 生成链接

**前提**: 已登录的管理员或运维账号

```bash
# 获取管理员登录链接
curl -X GET https://learn.imau.edu.cn/api/admin/tools/generate-admin-login-url \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "loginUrl": "https://learn.imau.edu.cn/admin-portal?token=abc123xyz&t=1712654321000",
    "expiresIn": 300,
    "expiresAt": 1712654621000
  }
}
```

### 4.3 方法三：在管理后台生成

1. 登录管理员账号
2. 访问 `/admin/tools` 或 `/ops/tools`
3. 点击"生成管理员登录链接"按钮
4. 复制生成的链接
5. 通过安全渠道分享给其他管理员

---

## 五、安全最佳实践

### 5.1 密钥管理

❌ **禁止**:
- 使用默认密钥 `default-secret`
- 将密钥提交到版本控制
- 在代码中硬编码密钥
- 通过不安全渠道传输密钥

✅ **推荐**:
- 使用强随机密钥（至少 32 字节）
- 通过环境变量配置
- 定期轮换密钥（建议每季度）
- 使用密钥管理服务（如 AWS Secrets Manager）

### 5.2 链接分享

❌ **禁止**:
- 通过邮件明文发送
- 在公共聊天群分享
- 保存到浏览器书签
- 截图后分享

✅ **推荐**:
- 使用加密通讯工具（如企业微信、钉钉）
- 一对一私聊发送
- 告知接收者 5 分钟有效期
- 使用后立即删除聊天记录

### 5.3 访问控制

✅ **强制措施**:
- 前端验证令牌和时间戳
- 后端验证用户角色
- 记录所有管理员登录日志
- 异常登录告警

### 5.4 监控告警

```sql
-- 监控管理员登录失败
SELECT COUNT(*) FROM login_logs
WHERE login_type = 'ADMIN_PORTAL'
  AND login_result = 'FAILED'
  AND created_time > DATE_SUB(NOW(), INTERVAL 1 HOUR);

-- 监控非授权访问尝试
SELECT * FROM access_logs
WHERE path = '/admin-portal'
  AND (token IS NULL OR token_valid = 0)
  AND created_time > DATE_SUB(NOW(), INTERVAL 1 DAY);
```

---

## 六、故障排查

### 6.1 常见问题

#### 问题 1: "访问被拒绝：缺少有效的访问令牌"

**原因**: 直接访问 `/admin-portal` 没有携带令牌

**解决**:
```bash
# 生成新的登录链接
node scripts/generate-admin-login-url.js
```

#### 问题 2: "访问被拒绝：令牌已过期"

**原因**: 令牌超过 5 分钟有效期

**解决**:
- 重新生成新的登录链接
- 建议在生成后立即使用

#### 问题 3: "访问被拒绝：令牌无效"

**原因**: 前后端密钥不一致

**解决**:
```bash
# 检查后端密钥
echo $ADMIN_LOGIN_SECRET

# 检查前端密钥
cat frontend/.env | grep VITE_ADMIN_LOGIN_SECRET

# 确保两者一致
```

#### 问题 4: "权限不足：此入口仅供管理员和运维使用"

**原因**: 使用非管理员账号登录

**解决**:
- 确认账号角色为 ADMIN 或 OPS
- 联系超级管理员分配权限

### 6.2 调试命令

```bash
# 测试令牌生成
node -e "
const timestamp = Date.now();
const secret = 'your-secret';
const str = timestamp + secret;
let hash = 0;
for (let i = 0; i < str.length; i++) {
  const char = str.charCodeAt(i);
  hash = ((hash << 5) - hash) + char;
  hash = hash & hash;
}
console.log('Token:', Math.abs(hash).toString(36));
console.log('Timestamp:', timestamp);
"

# 验证令牌有效期
node -e "
const timestamp = 1712654321000; // 替换为实际时间戳
const now = Date.now();
const diff = now - timestamp;
console.log('已过时间（秒）:', Math.floor(diff / 1000));
console.log('是否有效:', diff < 5 * 60 * 1000);
"
```

---

## 七、部署检查清单

### 7.1 开发环境

- [ ] 后端配置 `ADMIN_LOGIN_SECRET`
- [ ] 前端配置 `VITE_ADMIN_LOGIN_SECRET`
- [ ] 两者密钥一致
- [ ] 测试生成登录链接
- [ ] 测试令牌验证
- [ ] 测试角色权限

### 7.2 生产环境

- [ ] 使用强随机密钥（≥32字节）
- [ ] 密钥通过环境变量配置
- [ ] 密钥未提交到版本控制
- [ ] 配置 `APP_BASE_URL` 为生产域名
- [ ] 测试 SSO 自动重定向
- [ ] 测试管理员登录流程
- [ ] 配置登录日志监控
- [ ] 配置异常告警

---

## 八、API 参考

### 8.1 生成管理员登录链接

```http
GET /api/admin/tools/generate-admin-login-url
Authorization: Bearer {jwt_token}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "loginUrl": "https://learn.imau.edu.cn/admin-portal?token=abc123xyz&t=1712654321000",
    "expiresIn": 300,
    "expiresAt": 1712654621000
  }
}
```

### 8.2 管理员登录

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "user": {
      "id": 1,
      "username": "admin",
      "role": "ADMIN"
    }
  }
}
```

---

## 九、技术支持

- **开发团队**: LangHack Team
- **问题反馈**: GitHub Issues
- **紧急联系**: 系统管理员

---

**文档版本**: v1.0
**最后更新**: 2026-04-09
