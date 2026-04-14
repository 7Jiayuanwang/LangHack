# LangHack 统一身份认证集成指南

> **版本**: v1.0
> **更新时间**: 2026-04-09
> **状态**: 生产就绪

---

## 一、功能概述

LangHack 已集成内蒙古农业大学统一身份认证平台（CIAP 7.0），支持 **CAS 2.0** 和 **OAuth 2.0** 双协议，实现单点登录（SSO）功能。

### 核心特性

- ✅ **双协议支持**: CAS 2.0 / OAuth 2.0 可配置切换
- ✅ **自动角色识别**: 基于 memberOf 属性自动映射用户角色
- ✅ **人工审批机制**: 高权限角色（ADMIN/OPS）需管理员确认
- ✅ **低耦合设计**: 使用 Spring Profile 隔离，不影响原有认证
- ✅ **开关控制**: 三层开关（Profile + 环境变量 + 数据库配置）
- ✅ **双入口机制**: SSO 入口（学生/教师）+ 账密入口（运维备用）

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 (Vue 3)                            │
├─────────────────────────────────────────────────────────────┤
│  Login.vue (SSO 按钮)  │  SsoCallback.vue  │  待审批管理    │
└────────────────┬────────────────────────────────────────────┘
                 │ HTTPS
┌────────────────┴────────────────────────────────────────────┐
│                  Spring Security 层                          │
├─────────────────────────────────────────────────────────────┤
│  /api/auth/sso/** (permitAll)  │  JWT 认证 (不变)           │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────┴────────────────────────────────────────────┐
│                    SSO 服务层                                │
├─────────────────────────────────────────────────────────────┤
│  SsoService (接口)                                           │
│  ├─ CasSsoService (CAS 2.0 实现)                            │
│  └─ OAuth2SsoService (OAuth 2.0 实现)                       │
│  SsoUserMappingService (用户映射)                           │
│  RoleMappingService (角色识别)                              │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────┴────────────────────────────────────────────┐
│              CIAP 7.0 统一身份认证平台                       │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 数据库设计

#### sso_user_bindings (SSO 用户绑定表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 本地用户ID |
| sso_provider | VARCHAR(50) | SSO提供商: CAS, OAUTH2 |
| sso_user_id | VARCHAR(255) | SSO用户唯一标识 |
| sso_username | VARCHAR(255) | SSO用户名 |
| sso_email | VARCHAR(255) | SSO邮箱 |
| sso_real_name | VARCHAR(255) | SSO真实姓名 |
| sso_member_of | TEXT | SSO组织单位 (JSON数组) |
| auto_approved | TINYINT(1) | 是否自动审批: 0=待审批, 1=已审批 |
| last_login_time | DATETIME | 最后登录时间 |

#### sso_config (SSO 配置表)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| config_key | VARCHAR(100) | 配置键 |
| config_value | TEXT | 配置值 |
| config_type | VARCHAR(50) | 配置类型: SYSTEM, CAS, OAUTH2, ROLE_MAPPING |

#### users 表扩展字段

| 字段 | 类型 | 说明 |
|------|------|------|
| sso_bound | TINYINT(1) | 是否通过SSO绑定 |
| sso_provider | VARCHAR(50) | SSO提供商 |
| sso_subject | VARCHAR(255) | SSO唯一标识 |

---

## 三、配置说明

### 3.1 三层开关控制

#### 第一层：Spring Profile（编译时）

```yaml
# application.yml (develop 分支 - 禁用 SSO)
spring:
  profiles:
    active: default

# application.yml (master 分支 - 启用 SSO)
spring:
  profiles:
    active: sso
```

#### 第二层：环境变量（运行时）

```bash
# 必须显式启用
SSO_ENABLED=true

# 协议选择
SSO_PROTOCOL=CAS  # 或 OAUTH2
```

#### 第三层：数据库配置（动态）

```sql
-- 动态开关（优先级最高）
UPDATE sso_config SET config_value = 'true' WHERE config_key = 'sso.enabled';
```

### 3.2 CAS 配置

```yaml
# application-sso.yml
sso:
  enabled: ${SSO_ENABLED:false}
  protocol: CAS

  cas:
    enabled: true
    server-url: https://ids.imau.edu.cn/authserver
    service-url: ${APP_BASE_URL}/api/auth/sso/callback
    attributes:
      username: uid
      email: mail
      real-name: cn
      member-of: memberOf
```

**环境变量**:
```bash
SSO_ENABLED=true
SSO_PROTOCOL=CAS
SSO_CAS_SERVER_URL=https://ids.imau.edu.cn/authserver
APP_BASE_URL=https://learn.imau.edu.cn
```

### 3.3 OAuth 2.0 配置

```yaml
# application-sso.yml
sso:
  enabled: ${SSO_ENABLED:false}
  protocol: OAUTH2

  oauth2:
    enabled: true
    authorization-url: https://ids.imau.edu.cn/authserver/oauth2.0/authorize
    token-url: https://ids.imau.edu.cn/authserver/oauth2.0/accessToken
    userinfo-url: https://ids.imau.edu.cn/authserver/oauthApi/user/profile
    client-id: ${SSO_OAUTH2_CLIENT_ID}
    client-secret: ${SSO_OAUTH2_CLIENT_SECRET}
    redirect-uri: ${APP_BASE_URL}/api/auth/sso/callback
```

**环境变量**:
```bash
SSO_ENABLED=true
SSO_PROTOCOL=OAUTH2
SSO_OAUTH2_CLIENT_ID=your_client_id
SSO_OAUTH2_CLIENT_SECRET=your_client_secret
APP_BASE_URL=https://learn.imau.edu.cn
```

### 3.4 角色映射配置

```yaml
# application-sso.yml
sso:
  role-mapping:
    default-rules:
      "ou=管理员": ADMIN
      "ou=运维": OPS
      "ou=教师": TEACHER
      "ou=学生": STUDENT

  auto-approval:
    enabled: true
    allowed-roles:
      - TEACHER
      - STUDENT
      - USER
```

**自定义映射规则**（数据库）:
```sql
INSERT INTO sso_config (config_key, config_value, config_type, description) VALUES
('sso.role_mapping.ou=信息中心', 'OPS', 'ROLE_MAPPING', '信息中心映射为运维'),
('sso.role_mapping.ou=教务处', 'ADMIN', 'ROLE_MAPPING', '教务处映射为管理员');
```

---

## 四、部署流程

### 4.1 开发环境（develop 分支）

**无需任何配置**，SSO 功能完全禁用，保持原有账密登录。

```bash
# 启动后端
cd backend
mvn spring-boot:run

# 启动前端
cd frontend
npm run dev
```

### 4.2 生产环境（master 分支）

#### 步骤 1: 配置环境变量

```bash
# docker-compose.yml 或 .env
SSO_ENABLED=true
SSO_PROTOCOL=CAS
SSO_CAS_SERVER_URL=https://ids.imau.edu.cn/authserver
APP_BASE_URL=https://learn.imau.edu.cn
```

#### 步骤 2: 启动服务

```bash
# 使用 Docker Compose
docker-compose up -d

# 或手动启动
cd backend
mvn clean package -DskipTests
java -jar -Dspring.profiles.active=sso target/langhack.jar
```

#### 步骤 3: 验证 SSO 状态

```bash
# 检查 SSO 是否启用
curl https://learn.imau.edu.cn/api/auth/sso/status

# 预期响应
{
  "code": 200,
  "data": {
    "enabled": true,
    "protocol": "CAS"
  }
}
```

#### 步骤 4: 在 Minos Console 注册应用

1. 登录 Minos Console 身份管理平台
2. 添加应用：LangHack 英语学习平台
3. 配置回调地址：`https://learn.imau.edu.cn/api/auth/sso/callback`
4. 为用户组授权访问该应用

---

## 五、使用指南

### 5.1 用户登录流程

#### SSO 登录（学生/教师）

1. 访问 `https://learn.imau.edu.cn/login`
2. 点击"统一身份认证登录"按钮
3. 跳转到 CIAP 登录页面
4. 输入学校账号密码
5. 认证成功后自动跳转回系统
6. 根据角色进入对应页面

#### 首次登录

- **可自动识别角色**: 直接登录成功
- **无法识别角色**: 创建待审批记录，提示"账号审批中"
- **高权限角色**: 需管理员人工确认

### 5.2 管理员审批流程

1. 登录管理员账号
2. 访问 `/admin/sso-pending`
3. 查看待审批用户列表
4. 确认用户信息和推断角色
5. 选择最终角色并点击"确认"
6. 用户下次登录即可正常使用

### 5.3 角色映射规则

系统根据 `memberOf` 属性自动识别角色：

| memberOf 包含 | 映射角色 | 是否需要审批 |
|--------------|---------|------------|
| ou=管理员, ou=admin | ADMIN | ✅ 需要 |
| ou=运维, ou=ops | OPS | ✅ 需要 |
| ou=教师, ou=teacher | TEACHER | ❌ 自动 |
| ou=学生, ou=student | STUDENT | ❌ 自动 |
| 无法识别 | USER | ✅ 需要 |

---

## 六、API 接口

### 6.1 前端 API

#### 获取 SSO 登录 URL

```javascript
GET /api/auth/sso/login-url

Response:
{
  "code": 200,
  "data": {
    "loginUrl": "https://ids.imau.edu.cn/authserver/login?service=...",
    "protocol": "CAS"
  }
}
```

#### SSO 回调处理

```javascript
POST /api/auth/sso/callback
Content-Type: application/json

Body (CAS):
{
  "ticket": "ST-123-abc..."
}

Body (OAuth 2.0):
{
  "code": "AUTH_CODE",
  "state": "RANDOM_STATE"
}

Response (成功):
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "user": {
      "id": 1,
      "username": "2023122066164",
      "role": "STUDENT",
      ...
    }
  }
}

Response (待审批):
{
  "code": 400,
  "message": "您的账号正在审批中，请联系管理员"
}
```

#### 检查 SSO 状态

```javascript
GET /api/auth/sso/status

Response:
{
  "code": 200,
  "data": {
    "enabled": true,
    "protocol": "CAS"
  }
}
```

### 6.2 管理员 API

#### 获取待审批用户

```javascript
GET /api/admin/sso/pending-approvals

Response:
{
  "code": 200,
  "data": [
    {
      "bindingId": 1,
      "userId": 100,
      "username": "teacher001",
      "realName": "张三",
      "email": "zhangsan@imau.edu.cn",
      "inferredRole": "TEACHER",
      "memberOf": ["ou=教师,ou=用户,dc=imau,dc=edu,dc=cn"],
      "createdTime": "2026-04-09T10:30:00"
    }
  ]
}
```

#### 审批用户

```javascript
POST /api/admin/sso/approve-user
Content-Type: application/json

Body:
{
  "bindingId": 1,
  "approvedRole": "TEACHER"
}

Response:
{
  "code": 200,
  "message": "审批成功"
}
```

#### 拒绝用户

```javascript
POST /api/admin/sso/reject-user
Content-Type: application/json

Body:
{
  "bindingId": 1
}

Response:
{
  "code": 200,
  "message": "已拒绝"
}
```

---

## 七、故障排查

### 7.1 常见问题

#### 问题 1: SSO 按钮不显示

**原因**: SSO 功能未启用

**解决**:
```bash
# 检查环境变量
echo $SSO_ENABLED

# 检查 Profile
curl http://localhost:8080/actuator/env | grep spring.profiles.active

# 检查数据库配置
mysql -e "SELECT * FROM sso_config WHERE config_key = 'sso.enabled'"
```

#### 问题 2: 回调失败 "Invalid state"

**原因**: Session 丢失或 state 不匹配

**解决**:
- 检查 Redis 是否正常运行
- 确认回调地址配置正确
- 检查浏览器是否禁用 Cookie

#### 问题 3: 用户一直处于待审批状态

**原因**: 角色映射规则未匹配或需要人工审批

**解决**:
```sql
-- 查看用户的 memberOf
SELECT sso_member_of FROM sso_user_bindings WHERE sso_username = 'xxx';

-- 检查角色映射规则
SELECT * FROM sso_config WHERE config_type = 'ROLE_MAPPING';

-- 手动审批
UPDATE sso_user_bindings SET auto_approved = 1 WHERE id = xxx;
UPDATE users SET role = 'TEACHER', status = 'ACTIVE' WHERE id = xxx;
```

#### 问题 4: CAS ticket 验证失败

**原因**: 回调地址不匹配或网络问题

**解决**:
```bash
# 检查回调地址配置
echo $APP_BASE_URL

# 测试 CAS 服务器连通性
curl -I https://ids.imau.edu.cn/authserver

# 查看后端日志
tail -f logs/spring.log | grep CAS
```

### 7.2 调试命令

```bash
# 查看 SSO 绑定
mysql -e "SELECT * FROM sso_user_bindings ORDER BY created_time DESC LIMIT 10"

# 查看 SSO 登录日志
mysql -e "SELECT * FROM sso_login_logs ORDER BY created_time DESC LIMIT 20"

# 查看待审批用户
mysql -e "SELECT b.*, u.username, u.role FROM sso_user_bindings b JOIN users u ON b.user_id = u.id WHERE b.auto_approved = 0"

# 清除 Redis 缓存
redis-cli FLUSHDB
```

---

## 八、安全注意事项

### 8.1 密钥管理

- ❌ **禁止**将 `client_secret` 提交到版本控制
- ✅ 使用环境变量或密钥管理服务
- ✅ 定期轮换密钥

### 8.2 回调地址验证

- ✅ 在 Minos Console 中严格配置回调地址白名单
- ✅ 后端验证 state 参数防止 CSRF
- ✅ 使用 HTTPS 传输

### 8.3 权限控制

- ✅ 高权限角色（ADMIN/OPS）必须人工审批
- ✅ 定期审计 SSO 用户绑定
- ✅ 记录所有 SSO 登录日志

---

## 九、监控指标

### 9.1 关键指标

- SSO 登录成功率
- 待审批用户数量
- 角色识别准确率
- 平均审批时间

### 9.2 告警规则

```sql
-- SSO 登录失败率过高
SELECT COUNT(*) FROM sso_login_logs
WHERE login_result = 'FAILED'
  AND created_time > DATE_SUB(NOW(), INTERVAL 1 HOUR);

-- 待审批用户积压
SELECT COUNT(*) FROM sso_user_bindings WHERE auto_approved = 0;
```

---

## 十、测试

### 10.1 后端测试

#### 运行所有 SSO 测试

```bash
cd backend
mvn test -Dtest="com.english.sso.**"
```

#### 测试覆盖

- **CasSsoServiceTest**: CAS 2.0 协议实现测试
  - 登录 URL 生成
  - Ticket 验证
  - XML 响应解析
  - 错误处理

- **RoleMappingServiceTest**: 角色映射服务测试
  - 基于 memberOf 的角色识别
  - 角色优先级
  - 自动审批规则
  - 边界情况处理

- **SsoAuthControllerTest**: SSO 认证控制器测试
  - 登录 URL 获取
  - 回调处理
  - 状态查询
  - 错误响应

### 10.2 前端测试

#### 运行所有 SSO 测试

```bash
cd frontend
npx vitest run tests/views/public/SsoCallback.spec.js
npx vitest run tests/views/admin/SsoPendingApprovals.spec.js
npx vitest run tests/composables/useSsoLogin.spec.js
```

#### 测试覆盖

- **SsoCallback.spec.js**: SSO 回调页面测试
  - CAS ticket 处理
  - OAuth 2.0 code 处理
  - 角色路由跳转
  - 错误处理和显示
  - 加载状态

- **SsoPendingApprovals.spec.js**: 管理员审批页面测试
  - 待审批用户列表加载
  - 搜索和过滤
  - 用户审批
  - 用户拒绝
  - 角色选择
  - 错误处理

- **useSsoLogin.spec.js**: SSO 登录组合式函数测试
  - SSO 状态检查
  - 登录 URL 获取
  - 页面跳转
  - 加载状态
  - 错误处理

### 10.3 集成测试

#### 完整 SSO 登录流程测试

1. **准备环境**
   ```bash
   # 启动测试环境
   docker-compose -f docker-compose.test.yml up -d

   # 设置 SSO 环境变量
   export SSO_ENABLED=true
   export SSO_PROTOCOL=CAS
   export SSO_CAS_SERVER_URL=https://ids.imau.edu.cn/authserver
   ```

2. **测试步骤**
   - 访问登录页面，验证 SSO 按钮显示
   - 点击 SSO 按钮，验证跳转到 CIAP 登录页
   - 在 CIAP 登录页输入凭据
   - 验证回调处理和 JWT token 生成
   - 验证角色识别和页面跳转
   - 验证待审批用户的处理流程

3. **验证点**
   - SSO 按钮仅在 `ssoEnabled=true` 时显示
   - 传统登录仍然可用（双入口机制）
   - 高权限角色需要人工审批
   - 低权限角色自动审批
   - 审批后用户可正常登录

### 10.4 性能测试

```bash
# 并发登录测试
ab -n 100 -c 10 https://learn.imau.edu.cn/api/auth/sso/login-url

# 回调处理性能测试
ab -n 100 -c 10 -p callback.json -T application/json \
   https://learn.imau.edu.cn/api/auth/sso/callback
```

---

## 十一、附录

### 11.1 相关文档

- [统一身份认证接入指南](E:\项目\兴趣小组\智能与安全社团\服务器使用\服务接入\内蒙古农业大学-CAS&OAuth集成接口资料\内蒙古农业大学-CAS&OAuth集成接口资料\统一身份认证接入指南.md)
- [CIAP 7.0 CAS 集成手册](官方文档)
- [CIAP 7.0 OAuth 2.0 集成手册](官方文档)

### 11.2 技术支持

- **开发团队**: LangHack Team
- **学校 IT 支持**: 信息技术中心
- **问题反馈**: GitHub Issues

---

**文档版本**: v1.0
**最后更新**: 2026-04-09
