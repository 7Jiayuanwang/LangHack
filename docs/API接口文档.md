# 后台API接口文档

## 📋 目录
- [无需登录的公开接口](#无需登录的公开接口)
- [需要登录的接口](#需要登录的接口)
- [管理员专属接口](#管理员专属接口)
- [教师专属接口](#教师专属接口)
- [学生/用户接口](#学生用户接口)

---

## 🔓 无需登录的公开接口

### 1. 认证相关 (`/api/auth`)
- **POST** `/api/auth/login` - 用户登录
- **POST** `/api/auth/register` - 用户注册（自动注册为普通USER角色）
- **POST** `/api/auth/logout` - 用户登出

### 2. 公开课文 (`/api/public/texts`)
- **GET** `/api/public/texts/random` - 获取随机课文列表
  - 参数：`count`（默认8）、`difficulty`（可选）、`includeUserContent`（可选）
- **GET** `/api/public/texts/{textId}` - 获取课文详情
- **GET** `/api/public/texts/stats` - 获取课文统计数据

### 3. 公开单词 (`/api/public/words`)
- **GET** `/api/public/words` - 获取单词列表（分页）
  - 参数：`page`（默认1）、`size`（默认100，最大100）、`keyword`（可选）
- **GET** `/api/public/words/search` - 搜索单词
  - 参数：`word`（必需）
- **GET** `/api/public/words/{wordId}` - 获取单词详情

### 4. 测试接口
- **GET** `/` - 服务状态首页
- **GET** `/api/health` - 健康检查
- **GET** `/api/test` - 接口测试

---

## 🔒 需要登录的接口

这些接口需要在请求头中携带JWT Token: `Authorization: Bearer {token}`

### 1. 用户信息 (`/api/auth`)
- **GET** `/api/auth/userinfo` - 获取当前登录用户信息
- **GET** `/api/auth/search` - 搜索用户（需要ADMIN或TEACHER角色）
  - 参数：`keyword`（必需）、`role`（可选）

### 2. WordNet词汇查询 (`/api/wordnet`)
> **注意：这些接口默认需要登录，但根据代码来看，应该是公开的。建议在SecurityConfig中添加permitAll配置**

- **GET** `/api/wordnet/word/{word}` - 获取单词详细信息（集成ECDICT词典）
- **GET** `/api/wordnet/search` - 搜索词汇
  - 参数：`keyword`（必需）、`limit`（默认10）
- **GET** `/api/wordnet/stats` - 获取词典统计信息
- **GET** `/api/wordnet/ecdict/status` - 检查ECDICT服务状态
- **POST** `/api/wordnet/text/words` - 获取文本中可点击的单词
  - 请求体：`{"text": "课文内容"}`
- **POST** `/api/wordnet/words/batch` - 批量获取多个单词的信息
  - 请求体：`{"words": ["word1", "word2", ...]}`
- **GET** `/api/wordnet/word/{word}/get-or-create` - 查询或创建单词信息
- **GET** `/api/wordnet/status` - 检查WordNet服务状态

### 3. 课程接口 (`/api/courses`)
- **GET** `/api/courses` - 获取课程列表（需要ADMIN、TEACHER或STUDENT角色）
  - 参数：`page`、`size`、`keyword`、`status`
- **GET** `/api/courses/active` - 获取激活的课程列表（需要ADMIN、TEACHER或STUDENT角色）
- **GET** `/api/courses/{id}` - 获取课程详情（需要登录）

### 4. 用户课文管理 (`/api/user/texts`)
- **POST** `/api/user/texts/upload` - 用户上传课文
- **GET** `/api/user/texts/my-uploads` - 获取用户上传的课文列表
- **GET** `/api/user/texts/{textId}` - 获取用户上传的课文详情
- **PUT** `/api/user/texts/{textId}` - 更新用户上传的课文
- **DELETE** `/api/user/texts/{textId}` - 删除用户上传的课文
- **GET** `/api/user/texts/public` - 获取所有公开的用户上传课文
  - 参数：`page`、`size`、`difficulty`、`keyword`
- **POST** `/api/user/texts/{textId}/like` - 点赞课文

---

## 👑 管理员专属接口

这些接口需要 **ADMIN** 角色

### 管理员接口 (`/api/admin`)
- 用户管理
- 课程管理（创建、更新、删除）
- 数据统计
- 其他管理功能

### 课程管理 (`/api/courses`)
- **POST** `/api/courses` - 创建课程
- **PUT** `/api/courses/{id}` - 更新课程
- **DELETE** `/api/courses/{id}` - 删除课程
- **GET** `/api/courses/stats` - 获取课程统计数据

---

## 👨‍🏫 教师专属接口

这些接口需要 **TEACHER** 或 **ADMIN** 角色

### 教师接口 (`/api/teacher`)
- 班级管理
- 任务发布
- 进度监控
- 习题管理

### 其他教师权限
- `/api/auth/search` - 搜索用户（也可以是ADMIN）

---

## 👨‍🎓 学生/用户接口

这些接口需要 **USER**、**STUDENT**、**TEACHER** 或 **ADMIN** 角色

### 学生接口 (`/api/student`)
- **GET** `/api/student/courses` - 获取学生课程
- **GET** `/api/student/assignments` - 获取任务列表
- **POST** `/api/student/words/vocabulary` - 添加生词
- 学习记录
- 练习管理
- 统计数据

### 用户接口 (`/api/user`)
- 用户课文管理（见上文）
- 个人学习数据

---

## 🔐 权限说明

### 角色层级
1. **ADMIN** - 管理员，拥有所有权限
2. **TEACHER** - 教师，拥有教学管理权限
3. **STUDENT** - 学生，拥有学习相关权限
4. **USER** - 普通用户，拥有基础浏览和上传权限

### 权限规则
| 路径模式 | 权限要求 |
|---------|---------|
| `/api/auth/**` | 公开（登录、注册等） |
| `/api/public/**` | 公开 |
| `/api/health` | 公开 |
| `/api/test` | 公开 |
| `/api/admin/**` | ADMIN |
| `/api/teacher/**` | TEACHER, ADMIN |
| `/api/student/**` | USER, STUDENT, TEACHER, ADMIN |
| `/api/user/**` | USER, STUDENT, TEACHER, ADMIN |
| 其他 | 需要登录认证 |

---

## 📝 重要说明

### 1. WordNet接口的公开访问问题
目前 `/api/wordnet/**` 接口需要登录才能访问，但从功能设计上看，这些接口应该是公开的（用于课文阅读时查询单词）。

**建议修改 SecurityConfig.java：**
```java
.antMatchers("/api/wordnet/**").permitAll()
```

### 2. JWT Token使用
所有需要登录的接口都需要在请求头中添加：
```
Authorization: Bearer {JWT_TOKEN}
```

Token在登录成功后返回，客户端需要保存并在后续请求中携带。

### 3. 用户注册
- 通过 `/api/auth/register` 注册的用户自动设置为 **USER** 角色
- 其他角色（ADMIN、TEACHER、STUDENT）需要管理员创建

### 4. CORS配置
后端已配置CORS，允许跨域访问：
- 允许所有来源（开发环境）
- 允许的方法：GET, POST, PUT, DELETE, OPTIONS
- 允许携带凭证

---

## 🚀 接口调用示例

### 登录示例
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 获取用户信息
```bash
curl -X GET http://localhost:8080/api/auth/userinfo \
  -H "Authorization: Bearer {YOUR_JWT_TOKEN}"
```

### 查询单词
```bash
# 公开接口，无需登录
curl -X GET http://localhost:8080/api/public/words/search?word=hello

# WordNet接口（目前需要登录，建议改为公开）
curl -X GET http://localhost:8080/api/wordnet/word/hello \
  -H "Authorization: Bearer {YOUR_JWT_TOKEN}"
```

### 获取随机课文
```bash
# 公开接口，无需登录
curl -X GET "http://localhost:8080/api/public/texts/random?count=8&includeUserContent=true"
```

---

## 📊 总结

### 完全公开的接口（无需登录）✅
1. **认证接口**：登录、注册、登出
2. **公开课文**：随机课文、课文详情、统计数据
3. **公开单词**：单词列表、搜索、详情
4. **测试接口**：健康检查、服务状态

### 建议设为公开的接口⚠️
- **WordNet接口**（`/api/wordnet/**`）：用于课文阅读时的单词查询，应该公开访问

### 需要登录的接口🔒
- 用户信息查询
- 课程管理
- 学习记录
- 任务管理
- 个人数据等

---

**最后更新时间**: 2025-12-06
