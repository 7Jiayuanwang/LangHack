# SSO 双入口机制快速参考

## 普通用户登录

**入口**: `https://learn.imau.edu.cn/login`

**流程**:
```
访问 /login → 自动跳转 → CIAP 统一身份认证 → 登录成功
```

**特点**:
- ✅ 无需选择，自动重定向
- ✅ 使用学校统一账号
- ✅ 支持学生和教师

---

## 管理员登录

**入口**: `https://learn.imau.edu.cn/admin-portal?token=xxx&t=timestamp`

**生成链接**:
```bash
# 方法 1: 使用脚本
cd frontend
node scripts/generate-admin-login-url.js

# 方法 2: 通过 API
curl -X GET https://learn.imau.edu.cn/api/admin/tools/generate-admin-login-url \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**特点**:
- 🔐 需要叩门令牌
- ⏱️ 5 分钟有效期
- 👑 仅限 ADMIN/OPS 角色
- 🔒 账密登录

---

## 配置要点

### 后端 (application.yml)
```yaml
admin:
  login:
    secret: ${ADMIN_LOGIN_SECRET}
```

### 前端 (.env)
```bash
VITE_ADMIN_LOGIN_SECRET=<与后端相同>
```

### 环境变量
```bash
# 生产环境必须设置
ADMIN_LOGIN_SECRET=<强随机密钥>
APP_BASE_URL=https://learn.imau.edu.cn
```

---

## 安全检查

- [ ] 使用强随机密钥（≥32字节）
- [ ] 前后端密钥一致
- [ ] 密钥未提交到版本控制
- [ ] 通过安全渠道分享链接
- [ ] 5分钟内使用链接
- [ ] 使用后删除聊天记录

---

## 故障排查

| 错误信息 | 原因 | 解决方法 |
|---------|------|---------|
| 缺少有效的访问令牌 | 直接访问 /admin-portal | 生成新链接 |
| 令牌已过期 | 超过 5 分钟 | 重新生成链接 |
| 令牌无效 | 前后端密钥不一致 | 检查配置 |
| 权限不足 | 非管理员账号 | 使用管理员账号 |

---

## 文档链接

- 详细指南: `docs/sso/ADMIN-LOGIN-KNOCK-GUIDE.md`
- 实现总结: `docs/sso/DUAL-ENTRY-IMPLEMENTATION-SUMMARY.md`
- SSO 集成: `docs/sso/SSO-INTEGRATION-GUIDE.md`
