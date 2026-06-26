# 上传功能 API 完整指南

本文档覆盖 LangHack 平台全部上传功能的接口定义、请求/响应示例和可复现的 cURL / JS 脚本。

---

## 目录

- [架构总览](#架构总览)
- [前置条件：获取 JWT Token](#前置条件获取-jwt-token)
  - [方式一：密码登录（开发环境 / admin 账号）](#方式一密码登录开发环境--admin-账号)
  - [方式二：SSO 认证（生产环境）](#方式二sso-认证生产环境)
  - [方式三：浏览器提取（最快捷）](#方式三浏览器提取最快捷)
- [模块一：媒体文件上传](#模块一媒体文件上传)
  - [1.1 上传媒体文件](#11-上传媒体文件)
  - [1.2 获取媒体详情](#12-获取媒体详情)
  - [1.3 下载/流式播放媒体](#13-下载流式播放媒体)
  - [1.4 删除媒体](#14-删除媒体)
  - [1.5 重试视频转码（管理员）](#15-重试视频转码管理员)
- [模块二：文章上传](#模块二文章上传)
  - [2.1 上传文章](#21-上传文章)
  - [2.2 获取我的文章列表](#22-获取我的文章列表)
  - [2.3 获取文章详情](#23-获取文章详情)
  - [2.4 修改文章](#24-修改文章)
  - [2.5 删除文章](#25-删除文章)
  - [2.6 获取公开文章列表](#26-获取公开文章列表)
  - [2.7 点赞文章](#27-点赞文章)
- [模块三：视频上传](#模块三视频上传)
  - [3.1 上传视频](#31-上传视频)
  - [3.2 获取我的视频列表](#32-获取我的视频列表)
  - [3.3 获取视频详情](#33-获取视频详情)
  - [3.4 修改视频](#34-修改视频)
  - [3.5 删除视频](#35-删除视频)
  - [3.6 点赞视频](#36-点赞视频)
- [完整流程脚本](#完整流程脚本)
  - [Bash 完整流程](#bash-完整流程)
  - [JavaScript (Node.js) 完整流程](#javascript-nodejs-完整流程)
- [附录](#附录)
  - [A. 统一响应结构 Result](#a-统一响应结构-result)
  - [B. 实体字段说明](#b-实体字段说明)
  - [C. 文件限制汇总](#c-文件限制汇总)
  - [D. 错误码与错误消息](#d-错误码与错误消息)

---

## 架构总览

系统上传功能分两层：

```
┌───────────────────────────────────────────────────────────────┐
│  第一层：媒体文件上传 (二进制文件)                              │
│  POST /api/media/upload                                      │
│  Content-Type: multipart/form-data                           │
│  → 返回 mediaId（后续其他接口引用此 ID）                       │
└────────────────────────┬──────────────────────────────────────┘
                         │ mediaId / download URL
                         ▼
┌───────────────────────────────────────────────────────────────┐
│  第二层：内容条目创建 (业务元数据)                              │
│  Content-Type: application/json                              │
│  ├── 文章: POST /api/user/texts/upload   引用 media URL      │
│  └── 视频: POST /api/user/videos/upload  引用 mediaId        │
└───────────────────────────────────────────────────────────────┘
```

**关键设计**：文件传输与业务数据分离。先上传文件拿到 `mediaId`，再创建文章/视频条目引用它。

---

## 前置条件：获取 JWT Token

所有上传接口都需要 JWT 认证。系统支持两种认证方式：密码登录和 SSO 单点登录。

### 方式一：密码登录（开发环境 / admin 账号）

适用于开发环境或生产环境中的管理员账号。SSO 启用后密码登录接口仍然可用，但 SSO 自动创建的用户没有密码，只能走 SSO 流程。

#### 请求

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "student1",
    "password": "123456"
  }'
```

#### 成功响应

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdHVkZW50MSIsImlhdCI6MTcxNzU4MDAwMCwiZXhwIjoxNzE3NjY2NDAwfQ.xxxxx",
    "user": {
      "id": 1,
      "username": "student1",
      "realName": "张三",
      "role": "STUDENT",
      "email": "student1@example.com"
    },
    "expiresIn": 86400000
  }
}
```

#### 错误响应

```json
{ "code": 500, "message": "用户不存在" }
{ "code": 500, "message": "密码错误" }
{ "code": 500, "message": "账户已被禁用，请联系管理员" }
```

> 系统启动时自动创建默认管理员账号，在生产环境也可直接密码登录。

### 方式二：SSO 认证（生产环境）

生产环境使用 CAS 单点登录。SSO 流程涉及浏览器重定向，不能一条 cURL 完成，需要分步操作。

#### SSO 认证流程

```
浏览器                       后端                       CAS认证服务器
  │                           │                           │
  │ ① GET /api/auth/sso/login-url                         │
  │──────────────────────────►│                           │
  │ ← { loginUrl }            │                           │
  │                           │                           │
  │ ② 浏览器跳转到 CAS 登录页                              │
  │───────────────────────────────────────────────────────►│
  │                           │         用户输入账号密码    │
  │                           │                           │
  │ ③ CAS 302 重定向                                      │
  │◄────────── /api/auth/sso/callback?ticket=ST-xxx ──────│
  │                           │                           │
  │                      ④ 后端验证 ticket，生成 JWT        │
  │                      ⑤ 一次性 code 存入 Redis (5分钟)  │
  │                           │                           │
  │ ⑥ 302 → /sso/callback?code={uuid}                    │
  │◄──────────────────────────│                           │
  │                           │                           │
  │ ⑦ POST /api/auth/sso/token  { "code": "{uuid}" }     │
  │──────────────────────────►│                           │
  │ ← { token, user }         │ (Redis 删除 code，一次性)  │
```

#### 步骤 1：获取 CAS 登录 URL

```bash
curl -s https://langhack.imau.edu.cn/api/auth/sso/login-url | jq '.'
```

响应：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "loginUrl": "https://cas.imau.edu.cn/cas/login?service=https%3A%2F%2Flanghack.imau.edu.cn%2Fapi%2Fauth%2Fsso%2Fcallback",
    "protocol": "CAS"
  }
}
```

#### 步骤 2：浏览器完成 CAS 登录

在浏览器中打开上一步返回的 `loginUrl`，完成学校统一身份认证登录。

登录成功后浏览器地址栏会跳转到类似：
```
https://langhack.imau.edu.cn/sso/callback?code=a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

从地址栏复制 `code` 参数值（UUID 格式）。

#### 步骤 3：用一次性 code 换取 JWT Token

```bash
curl -s -X POST https://langhack.imau.edu.cn/api/auth/sso/token \
  -H "Content-Type: application/json" \
  -d '{"code":"a1b2c3d4-e5f6-7890-abcd-ef1234567890"}'
```

成功响应：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyMDIyMTAxMjM0Iiwicm9sZSI6IlNUVURFTlQiLCJpYXQiOjE3MTc1ODAwMDAsImV4cCI6MTcxNzY2NjQwMH0.xxxxx",
    "user": {
      "id": 42,
      "username": "2022101234",
      "realName": "张三",
      "role": "STUDENT",
      "status": "ACTIVE",
      "ssoBound": 1,
      "ssoProvider": "CAS"
    },
    "expiresIn": 86400000
  }
}
```

错误响应：
```json
{ "code": 500, "message": "授权码无效或已过期，请重新登录", "data": { "errorCode": "CODE_EXPIRED" } }
{ "code": 500, "message": "缺少授权码", "data": { "errorCode": "MISSING_PARAM" } }
```

> **注意**：一次性 code 在 Redis 中有 5 分钟有效期，且只能使用一次。换取后立即删除。

#### SSO 完整脚本（交互式）

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="https://langhack.imau.edu.cn"

# 1. 获取 CAS 登录 URL
SSO_RESP=$(curl -s "$BASE_URL/api/auth/sso/login-url")
LOGIN_URL=$(echo "$SSO_RESP" | jq -r '.data.loginUrl')
echo "请在浏览器中打开: $LOGIN_URL"
echo ""
echo "登录后浏览器跳转到 /sso/callback?code=xxx"
echo "请复制地址栏中的 code 参数值:"
read -rp "code: " ONE_TIME_CODE

# 2. 换取 JWT Token
TOKEN_RESP=$(curl -s -X POST "$BASE_URL/api/auth/sso/token" \
  -H "Content-Type: application/json" \
  -d '{"code":"'"$ONE_TIME_CODE"'"}')

TOKEN=$(echo "$TOKEN_RESP" | jq -r '.data.token // empty')
if [ -z "$TOKEN" ]; then
  echo "换取 token 失败: $(echo "$TOKEN_RESP" | jq -r '.message')"
  exit 1
fi

echo "Token 获取成功: ${TOKEN:0:50}..."
echo ""
echo "使用方式:"
echo "  export TOKEN=\"$TOKEN\""
echo "  curl -H \"Authorization: Bearer \$TOKEN\" $BASE_URL/api/user/texts/my-uploads"
```

#### 其他 SSO 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/sso/status` | 检查 SSO 是否启用，返回 `{ enabled, protocol }` |
| GET | `/api/auth/sso/login-url` | 获取 CAS 登录跳转地址 |
| GET | `/api/auth/sso/callback` | CAS 认证回调（后端自动处理，不直接调用） |
| POST | `/api/auth/sso/token` | 用一次性 code 换取 JWT |
| GET | `/api/auth/sso/logout-url` | 获取 CAS 登出 URL |

### 方式三：浏览器提取（最快捷）

适合临时调试，不需要脚本。

1. 在浏览器中正常登录 LangHack（密码或 SSO 均可）
2. 按 F12 打开开发者工具
3. 在 Console 中执行：
   ```javascript
   localStorage.getItem('token')
   ```
4. 复制输出的 token 字符串用于 cURL 调用

### 后续请求携带 Token

```bash
# 在所有后续请求中添加此 Header：
-H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

> 以下示例中 `$TOKEN` 表示上面获取到的 JWT token 字符串（无论通过哪种方式获取）。

---

## 模块一：媒体文件上传

**控制器**: `MediaController.java` (`/api/media`)
**服务实现**: `MediaServiceImpl.java`
**前端 API**: `frontend/src/api/media.js`

### 1.1 上传媒体文件

上传图片或视频文件到服务器。这是所有上传功能的基础步骤。

| 属性 | 值 |
|------|-----|
| URL | `POST /api/media/upload` |
| 权限 | 任何已登录用户 (`isAuthenticated()`) |
| Content-Type | `multipart/form-data` |
| 并发限制 | 最多 5 个并发上传（Semaphore），超时 30s |

#### 请求参数（表单字段）

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `file` | File | 是 | 要上传的文件 |
| `mediaType` | String | 是 | `"IMAGE"` 或 `"VIDEO"` |

#### 文件限制

| 类型 | 最大大小 | 后端允许扩展名 | 前端允许 MIME |
|------|---------|---------------|-------------|
| IMAGE | 10 MB | jpg, jpeg, png, gif, webp | image/jpeg, image/png, image/gif, image/webp |
| VIDEO | 512 MB | mp4, webm, ogg, mov, avi, mkv | video/mp4, video/webm, video/ogg |

> 注意：后端视频扩展名比前端更宽松（多了 mov, avi, mkv），但前端会先行校验。通过 cURL 直接调用后端时可以上传 mov/avi/mkv。

#### 存储策略

| 类型 | 存储方式 | 说明 |
|------|---------|------|
| IMAGE | 数据库 BLOB (`media.file_content`) | 直接存入 DB |
| VIDEO | 文件系统 | 路径格式: `{storagePath}/video/{yyyy/MM}/{UUID}.{ext}` |

#### cURL 示例 — 上传图片

```bash
curl -X POST http://localhost:8080/api/media/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/cover.jpg" \
  -F "mediaType=IMAGE"
```

#### cURL 示例 — 上传视频

```bash
curl -X POST http://localhost:8080/api/media/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/lecture.mp4" \
  -F "mediaType=VIDEO"
```

#### 成功响应 (200)

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "mediaId": 42,
    "fileName": "lecture.mp4",
    "mediaType": "VIDEO",
    "fileSize": 15728640,
    "url": "/api/media/42/download"
  }
}
```

#### 错误响应示例

**文件为空**:
```json
{ "code": 500, "message": "文件不能为空" }
```

**文件过大**:
```json
{ "code": 500, "message": "文件大小超过限制（最大 512MB）" }
```

**不支持的类型**:
```json
{ "code": 500, "message": "不支持的文件类型" }
```

**服务器队列满**:
```json
{ "code": 500, "message": "服务器上传队列已满，请稍后重试" }
```

**未授权**:
```json
{ "code": 500, "message": "未授权" }
```

#### 视频转码流程（自动触发）

视频上传后后端自动执行：

1. **ffprobe 探测** → 获取视频编码(codec)、分辨率(width/height)
2. **判断是否需要转码** → 如果编码不兼容浏览器播放
3. **需要转码** → `transcodeStatus = "PENDING"` → 事务提交后异步调用 `transcodeAsync(mediaId)`
4. **不需要转码** → `transcodeStatus = "SKIPPED"`

转码状态值: `PENDING` → `PROCESSING` → `COMPLETED` / `FAILED` / `SKIPPED`

#### 前端调用示例 (JavaScript)

```javascript
import { uploadMedia } from '@/api/media'

// 上传图片
const fileInput = document.querySelector('input[type="file"]')
const file = fileInput.files[0]

try {
  const response = await uploadMedia(file, 'IMAGE')
  if (response.code === 200) {
    console.log('媒体ID:', response.data.mediaId)
    console.log('下载URL:', response.data.url)
  }
} catch (error) {
  console.error('上传失败:', error.message)
}
```

`uploadMedia` 函数内部实现（`frontend/src/api/media.js`）：
```javascript
export function uploadMedia(file, mediaType) {
  // 前端校验: 文件大小 + MIME 类型
  const validationError = validateFile(file, mediaType)
  if (validationError) {
    return Promise.reject(new Error(validationError))
  }

  const formData = new FormData()
  formData.append('file', file)
  formData.append('mediaType', mediaType)

  return request({
    url: '/api/media/upload',
    method: 'post',
    data: formData,
    timeout: 600000,  // 10 分钟超时
    headers: { 'Content-Type': undefined }  // 让浏览器自动设置 boundary
  })
}
```

---

### 1.2 获取媒体详情

| 属性 | 值 |
|------|-----|
| URL | `GET /api/media/{mediaId}` |
| 权限 | 无需认证 |

#### cURL 示例

```bash
curl http://localhost:8080/api/media/42
```

#### 成功响应 (200)

返回安全 DTO，不暴露 `fileContent`/`filePath`/`uploadedBy` 等内部字段。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 42,
    "fileName": "lecture.mp4",
    "mediaType": "VIDEO",
    "fileSize": 15728640,
    "mimeType": "video/mp4",
    "textId": null,
    "description": null,
    "width": 1920,
    "height": 1080,
    "duration": null,
    "transcodeStatus": "SKIPPED",
    "transcodeError": null,
    "originalCodec": "h264"
  }
}
```

#### 错误响应

```json
{ "code": 500, "message": "媒体不存在" }
```

---

### 1.3 下载/流式播放媒体

| 属性 | 值 |
|------|-----|
| URL | `GET /api/media/{mediaId}/download` |
| 权限 | 无需认证 |
| 特性 | 支持 HTTP Range 请求（视频分段加载/拖拽进度条） |

#### cURL 示例 — 完整下载

```bash
curl -o output.mp4 http://localhost:8080/api/media/42/download
```

#### cURL 示例 — Range 请求（流式播放）

```bash
curl -H "Range: bytes=0-1048575" \
  http://localhost:8080/api/media/42/download \
  -o chunk.mp4
```

#### 成功响应

**完整文件 (200)**:
```
HTTP/1.1 200 OK
Content-Type: video/mp4
Content-Disposition: inline; filename="lecture.mp4"
Content-Length: 15728640
Accept-Ranges: bytes

[二进制文件内容]
```

**分段响应 (206)**:
```
HTTP/1.1 206 Partial Content
Content-Type: video/mp4
Content-Range: bytes 0-1048575/15728640
Content-Length: 1048576

[分段二进制内容]
```

#### 在 HTML 中使用

```html
<!-- 图片 -->
<img src="/api/media/10/download" alt="封面图" />

<!-- 视频播放器 -->
<video src="/api/media/42/download" controls width="100%"></video>

<!-- Markdown 中引用 -->
![封面图](/api/media/10/download)
```

---

### 1.4 删除媒体

| 属性 | 值 |
|------|-----|
| URL | `DELETE /api/media/{mediaId}` |
| 权限 | 上传者本人 或 ADMIN |

#### cURL 示例

```bash
curl -X DELETE http://localhost:8080/api/media/42 \
  -H "Authorization: Bearer $TOKEN"
```

#### 成功响应 (200)

```json
{ "code": 200, "message": "删除成功" }
```

#### 错误响应

```json
{ "code": 500, "message": "媒体不存在" }
{ "code": 500, "message": "无权删除此资源" }
```

---

### 1.5 重试视频转码（管理员）

| 属性 | 值 |
|------|-----|
| URL | `POST /api/media/{mediaId}/retry-transcode` |
| 权限 | 仅 ADMIN |

#### cURL 示例

```bash
curl -X POST http://localhost:8080/api/media/42/retry-transcode \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

#### 成功响应 (200)

```json
{ "code": 200, "message": "已重新加入转码队列" }
```

#### 错误响应

```json
{ "code": 500, "message": "媒体不存在" }
{ "code": 500, "message": "仅视频支持重试转码" }
{ "code": 500, "message": "当前正在转码中" }
```

---

## 模块二：文章上传

**控制器**: `UserTextController.java` (`/api/user/texts`)
**前端页面**: `frontend/src/views/student/UserTexts.vue`

### 2.1 上传文章

| 属性 | 值 |
|------|-----|
| URL | `POST /api/user/texts/upload` |
| 权限 | `hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')` |
| Content-Type | `application/json` |

#### 请求参数（JSON Body）

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `title` | String | **是** | - | 课文标题（不能为空） |
| `content` | String | 条件必填 | - | 纯文本内容（TEXT 类型时必填） |
| `markdownContent` | String | 条件必填 | - | Markdown 内容（MARKDOWN 类型时必填） |
| `contentType` | String | 否 | `"TEXT"` | 内容类型：`"TEXT"` 或 `"MARKDOWN"` |
| `difficulty` | String | 否 | `"MEDIUM"` | 难度：`"EASY"` / `"MEDIUM"` / `"HARD"` |
| `tags` | String | 否 | `""` | 标签（逗号分隔字符串） |
| `coverUrl` | String | 否 | null | 封面图 URL（通常为 `/api/media/{id}/download`） |
| `audioUrl` | String | 否 | null | 音频 URL |

#### 校验规则

- `title` 不能为空或全空格
- `contentType = "TEXT"` → `content` 不能为空
- `contentType = "MARKDOWN"` → `markdownContent` 不能为空（`content` 自动填为空字符串）

#### 服务端自动设置的字段

| 字段 | 自动值 | 说明 |
|------|--------|------|
| `unitId` | `999` | 用户上传专用单元（固定值） |
| `uploadedBy` | 当前用户 ID | 从 JWT token 解析 |
| `isPublic` | `1` | 学生上传默认公开 |
| `status` | `"PENDING"` | 默认待审核 |
| `viewCount` | `0` | 初始化 |
| `likeCount` | `0` | 初始化 |
| `orderNum` | 自动递增 | 当前单元中最大排序号 + 1 |

#### cURL 示例 — 上传纯文本文章

```bash
curl -X POST http://localhost:8080/api/user/texts/upload \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "My First English Essay",
    "content": "English is a global language spoken by millions of people around the world. Learning English opens doors to international communication, better career opportunities, and access to a vast wealth of knowledge.",
    "contentType": "TEXT",
    "difficulty": "EASY",
    "tags": "essay,beginner"
  }'
```

#### cURL 示例 — 上传 Markdown 文章（带封面图）

先上传封面图获取 mediaId，再创建文章引用封面 URL：

```bash
# 步骤1: 上传封面图
COVER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/media/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/cover.jpg" \
  -F "mediaType=IMAGE")

# 提取 mediaId（需要 jq）
COVER_MEDIA_ID=$(echo "$COVER_RESPONSE" | jq -r '.data.mediaId')
echo "封面图 mediaId: $COVER_MEDIA_ID"

# 步骤2: 创建 Markdown 文章
curl -X POST http://localhost:8080/api/user/texts/upload \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Advanced Grammar: Subjunctive Mood",
    "content": "",
    "markdownContent": "# Subjunctive Mood\n\n## Introduction\n\nThe subjunctive mood is used to express **wishes**, **hypothetical situations**, and **demands**.\n\n## Examples\n\n- If I **were** you, I would study harder.\n- She suggested that he **be** on time.\n- It is essential that every student **submit** the assignment.\n\n## Practice\n\n> Try rewriting these sentences using the subjunctive mood.\n\n1. I wish I (be) ______ taller.\n2. The teacher demanded that he (leave) ______ the room.",
    "contentType": "MARKDOWN",
    "difficulty": "HARD",
    "tags": "grammar,advanced,subjunctive",
    "coverUrl": "/api/media/'"$COVER_MEDIA_ID"'/download"
  }'
```

#### 成功响应 (200)

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "textId": 156,
    "message": "课文上传成功"
  }
}
```

#### 错误响应示例

```json
{ "code": 500, "message": "用户不存在" }
{ "code": 500, "message": "课文标题不能为空" }
{ "code": 500, "message": "课文内容不能为空" }
{ "code": 500, "message": "Markdown内容不能为空" }
{ "code": 500, "message": "单元课文数量已满，请联系管理员" }
{ "code": 500, "message": "上传失败，请稍后重试" }
```

---

### 2.2 获取我的文章列表

| 属性 | 值 |
|------|-----|
| URL | `GET /api/user/texts/my-uploads` |
| 权限 | 已登录用户 |

#### cURL 示例

```bash
curl http://localhost:8080/api/user/texts/my-uploads \
  -H "Authorization: Bearer $TOKEN"
```

#### 成功响应 (200)

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 156,
      "unitId": 999,
      "title": "My First English Essay",
      "content": "English is a global language...",
      "markdownContent": null,
      "contentType": "TEXT",
      "difficulty": "EASY",
      "tags": "essay,beginner",
      "coverUrl": null,
      "audioUrl": null,
      "uploadedBy": 1,
      "isPublic": 1,
      "status": "PENDING",
      "viewCount": 0,
      "likeCount": 0,
      "orderNum": 1,
      "deleted": 0,
      "createdAt": "2026-06-05T10:30:00",
      "updatedAt": "2026-06-05T10:30:00"
    }
  ]
}
```

---

### 2.3 获取文章详情

| 属性 | 值 |
|------|-----|
| URL | `GET /api/user/texts/{textId}` |
| 权限 | 上传者本人 / ADMIN / 公开文章任何人可看 |
| 副作用 | 自动递增 `viewCount` |

#### cURL 示例

```bash
curl http://localhost:8080/api/user/texts/156 \
  -H "Authorization: Bearer $TOKEN"
```

#### 成功响应 (200)

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 156,
    "unitId": 999,
    "title": "My First English Essay",
    "content": "English is a global language spoken by millions...",
    "markdownContent": null,
    "contentType": "TEXT",
    "coverUrl": null,
    "audioUrl": null,
    "difficulty": "EASY",
    "orderNum": 1,
    "uploadedBy": 1,
    "tags": "essay,beginner",
    "viewCount": 1,
    "likeCount": 0,
    "isPublic": 1,
    "status": "PENDING",
    "isFeatured": null,
    "featuredPriority": null,
    "deleted": 0,
    "createdAt": "2026-06-05T10:30:00",
    "updatedAt": "2026-06-05T10:30:00"
  }
}
```

#### 错误响应

```json
{ "code": 500, "message": "课文不存在" }
{ "code": 500, "message": "没有权限查看此课文" }
```

---

### 2.4 修改文章

| 属性 | 值 |
|------|-----|
| URL | `PUT /api/user/texts/{textId}` |
| 权限 | 仅上传者本人 |
| 副作用 | `status` 自动重置为 `"PENDING"`（需重新审核） |

#### 请求参数（JSON Body，所有字段均可选）

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `title` | String | 新标题（不能为空） |
| `content` | String | 新纯文本内容 |
| `markdownContent` | String | 新 Markdown 内容 |
| `contentType` | String | `"TEXT"` / `"MARKDOWN"` |
| `difficulty` | String | `"EASY"` / `"MEDIUM"` / `"HARD"` |
| `tags` | String | 新标签 |
| `coverUrl` | String | 新封面 URL |
| `audioUrl` | String | 新音频 URL |

只传需要修改的字段即可（部分更新）。

#### cURL 示例

```bash
curl -X PUT http://localhost:8080/api/user/texts/156 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "My First English Essay (Revised)",
    "difficulty": "MEDIUM",
    "tags": "essay,beginner,revised"
  }'
```

#### 成功响应 (200)

```json
{ "code": 200, "message": "更新成功" }
```

#### 错误响应

```json
{ "code": 500, "message": "课文不存在" }
{ "code": 500, "message": "没有权限修改此课文" }
{ "code": 500, "message": "课文标题不能为空" }
{ "code": 500, "message": "课文内容不能为空" }
{ "code": 500, "message": "Markdown内容不能为空" }
```

---

### 2.5 删除文章

| 属性 | 值 |
|------|-----|
| URL | `DELETE /api/user/texts/{textId}` |
| 权限 | 仅上传者本人 |
| 限制 | `APPROVED` 状态的文章不可删除 |

#### cURL 示例

```bash
curl -X DELETE http://localhost:8080/api/user/texts/156 \
  -H "Authorization: Bearer $TOKEN"
```

#### 成功响应 (200)

```json
{ "code": 200, "message": "删除成功" }
```

#### 错误响应

```json
{ "code": 500, "message": "课文不存在" }
{ "code": 500, "message": "没有权限删除此课文" }
{ "code": 500, "message": "已审核通过的内容不可删除，如需调整请走修改流程" }
```

---

### 2.6 获取公开文章列表

| 属性 | 值 |
|------|-----|
| URL | `GET /api/user/texts/public` |
| 权限 | 已登录用户 |

#### 请求参数（Query String）

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `page` | int | 否 | 1 | 页码（最小 1） |
| `size` | int | 否 | 20 | 每页条数（1-100） |
| `difficulty` | String | 否 | - | 按难度筛选 |
| `keyword` | String | 否 | - | 关键词搜索（最长 100 字符） |

#### cURL 示例

```bash
curl "http://localhost:8080/api/user/texts/public?page=1&size=10&difficulty=EASY" \
  -H "Authorization: Bearer $TOKEN"
```

#### 成功响应 (200)

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 156,
      "title": "My First English Essay",
      "content": "English is a global language...",
      "difficulty": "EASY",
      "tags": "essay,beginner",
      "viewCount": 5,
      "likeCount": 2,
      "isPublic": 1,
      "status": "APPROVED",
      "createdAt": "2026-06-05T10:30:00"
    }
  ]
}
```

---

### 2.7 点赞文章

| 属性 | 值 |
|------|-----|
| URL | `POST /api/user/texts/{textId}/like` |
| 权限 | `hasAnyRole('USER', 'STUDENT')` |
| 限制 | 只能点赞 `isPublic = 1` 的文章 |

#### cURL 示例

```bash
curl -X POST http://localhost:8080/api/user/texts/156/like \
  -H "Authorization: Bearer $TOKEN"
```

#### 成功响应 (200)

```json
{ "code": 200, "message": "点赞成功" }
```

#### 错误响应

```json
{ "code": 500, "message": "课文不存在" }
{ "code": 500, "message": "只能点赞公开课文" }
```

---

## 模块三：视频上传

**控制器**: `UserVideoController.java` (`/api/user/videos`)
**前端 API**: `frontend/src/api/video.js`

### 3.1 上传视频

**两步流程**：先上传视频文件到媒体服务获取 `mediaId`，再创建视频条目。

| 属性 | 值 |
|------|-----|
| URL | `POST /api/user/videos/upload` |
| 权限 | `hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')` |
| Content-Type | `application/json` |

#### 请求参数（JSON Body）

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `title` | String | **是** | - | 视频标题（不能为空） |
| `mediaId` | Long | **是** | - | 视频文件的 media ID（先通过 `POST /api/media/upload` 上传获得） |
| `description` | String | 否 | `""` | 视频描述 |
| `tags` | String | 否 | `""` | 标签（逗号分隔） |
| `subtitleContent` | String | 否 | null | 英文字幕内容（SRT/VTT 格式文本） |
| `subtitleContentZh` | String | 否 | null | 中文字幕内容 |
| `coverMediaId` | Long | 否 | null | 封面图的 media ID（先通过 `POST /api/media/upload` 上传图片获得） |

#### 服务端自动设置的字段

| 字段 | 自动值 | 说明 |
|------|--------|------|
| `userId` | 当前用户 ID | 从 JWT token 解析 |
| `isPublic` | `0` | **默认不公开**（与文章默认公开不同！） |
| `status` | `"PENDING"` | 默认待审核 |

#### cURL 示例 — 完整两步流程

```bash
# ============================================
# 步骤1: 上传视频文件
# ============================================
VIDEO_RESPONSE=$(curl -s -X POST http://localhost:8080/api/media/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/english-lesson.mp4" \
  -F "mediaType=VIDEO")

VIDEO_MEDIA_ID=$(echo "$VIDEO_RESPONSE" | jq -r '.data.mediaId')
echo "视频文件 mediaId: $VIDEO_MEDIA_ID"
# 输出: 视频文件 mediaId: 42

# ============================================
# 步骤2（可选）: 上传封面图
# ============================================
COVER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/media/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/thumbnail.jpg" \
  -F "mediaType=IMAGE")

COVER_MEDIA_ID=$(echo "$COVER_RESPONSE" | jq -r '.data.mediaId')
echo "封面图 mediaId: $COVER_MEDIA_ID"
# 输出: 封面图 mediaId: 43

# ============================================
# 步骤3: 创建视频条目
# ============================================
curl -X POST http://localhost:8080/api/user/videos/upload \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "English Pronunciation: TH Sound",
    "mediaId": '"$VIDEO_MEDIA_ID"',
    "description": "Learn how to properly pronounce the TH sound in English. Covers both voiced and voiceless TH.",
    "tags": "pronunciation,beginner,phonetics",
    "coverMediaId": '"$COVER_MEDIA_ID"',
    "subtitleContent": "1\n00:00:01,000 --> 00:00:05,000\nHello everyone, today we will learn about the TH sound.\n\n2\n00:00:05,500 --> 00:00:10,000\nThere are two types: voiced and voiceless.",
    "subtitleContentZh": "1\n00:00:01,000 --> 00:00:05,000\n大家好，今天我们来学习TH发音。\n\n2\n00:00:05,500 --> 00:00:10,000\nTH发音分两种：浊音和清音。"
  }'
```

#### cURL 示例 — 最简上传（仅必填字段）

```bash
curl -X POST http://localhost:8080/api/user/videos/upload \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Quick English Tips",
    "mediaId": 42
  }'
```

#### 成功响应 (200)

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "videoId": 15,
    "message": "视频上传成功"
  }
}
```

#### 错误响应示例

```json
{ "code": 500, "message": "用户不存在" }
{ "code": 500, "message": "视频标题不能为空" }
{ "code": 500, "message": "视频文件不能为空" }
{ "code": 500, "message": "上传失败，请稍后重试" }
```

---

### 3.2 获取我的视频列表

| 属性 | 值 |
|------|-----|
| URL | `GET /api/user/videos/my-uploads` |
| 权限 | 已登录用户 |

#### cURL 示例

```bash
curl http://localhost:8080/api/user/videos/my-uploads \
  -H "Authorization: Bearer $TOKEN"
```

#### 成功响应 (200)

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 15,
      "title": "English Pronunciation: TH Sound",
      "description": "Learn how to properly pronounce the TH sound...",
      "mediaId": 42,
      "coverMediaId": 43,
      "videoUrl": null,
      "coverUrl": null,
      "subtitleContent": "1\n00:00:01,000 --> 00:00:05,000\n...",
      "subtitleContentZh": "1\n00:00:01,000 --> 00:00:05,000\n...",
      "userId": 1,
      "isPublic": 0,
      "isFeatured": null,
      "featuredPriority": null,
      "tags": "pronunciation,beginner,phonetics",
      "viewCount": 0,
      "likeCount": 0,
      "status": "PENDING",
      "associatedTextId": null,
      "transcriptionStatus": null,
      "transcodeStatus": "SKIPPED",
      "transcodeError": null,
      "deleted": 0,
      "createdTime": "2026-06-05T11:00:00",
      "updatedTime": "2026-06-05T11:00:00"
    }
  ]
}
```

> `transcodeStatus` / `transcodeError` 是运行时从关联 `media` 表动态获取的虚拟字段，不存储在 `videos` 表中。

---

### 3.3 获取视频详情

| 属性 | 值 |
|------|-----|
| URL | `GET /api/user/videos/{videoId}` |
| 权限 | 上传者本人 / 公开视频任何人可看 |
| 副作用 | 自动递增 `viewCount` |

#### cURL 示例

```bash
curl http://localhost:8080/api/user/videos/15 \
  -H "Authorization: Bearer $TOKEN"
```

#### 成功响应 (200)

与 3.2 列表中单条记录结构相同。

#### 错误响应

```json
{ "code": 500, "message": "视频不存在" }
{ "code": 500, "message": "没有权限查看此视频" }
```

---

### 3.4 修改视频

| 属性 | 值 |
|------|-----|
| URL | `PUT /api/user/videos/{videoId}` |
| 权限 | 仅上传者本人 |
| 副作用 | `status` 自动重置为 `"PENDING"` |

#### 请求参数（JSON Body，所有字段均可选）

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `title` | String | 新标题（不能为空） |
| `description` | String | 新描述 |
| `tags` | String | 新标签 |
| `subtitleContent` | String | 新英文字幕 |
| `subtitleContentZh` | String | 新中文字幕 |

#### cURL 示例

```bash
curl -X PUT http://localhost:8080/api/user/videos/15 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "English Pronunciation: TH Sound (Updated)",
    "description": "Updated version with more examples.",
    "tags": "pronunciation,beginner,phonetics,updated"
  }'
```

#### 成功响应 (200)

```json
{ "code": 200, "message": "更新成功" }
```

#### 错误响应

```json
{ "code": 500, "message": "视频不存在" }
{ "code": 500, "message": "没有权限修改此视频" }
{ "code": 500, "message": "视频标题不能为空" }
```

---

### 3.5 删除视频

| 属性 | 值 |
|------|-----|
| URL | `DELETE /api/user/videos/{videoId}` |
| 权限 | 仅上传者本人 |
| 限制 | `APPROVED` 状态的视频不可删除 |

#### cURL 示例

```bash
curl -X DELETE http://localhost:8080/api/user/videos/15 \
  -H "Authorization: Bearer $TOKEN"
```

#### 成功响应 (200)

```json
{ "code": 200, "message": "删除成功" }
```

#### 错误响应

```json
{ "code": 500, "message": "视频不存在" }
{ "code": 500, "message": "没有权限删除此视频" }
{ "code": 500, "message": "已审核通过的内容不可删除，如需调整请走修改流程" }
```

---

### 3.6 点赞视频

| 属性 | 值 |
|------|-----|
| URL | `POST /api/user/videos/{videoId}/like` |
| 权限 | 已登录用户 |
| 限制 | 只能点赞 `isPublic = 1` 的视频 |

#### cURL 示例

```bash
curl -X POST http://localhost:8080/api/user/videos/15/like \
  -H "Authorization: Bearer $TOKEN"
```

#### 成功响应 (200)

```json
{ "code": 200, "message": "点赞成功" }
```

#### 错误响应

```json
{ "code": 500, "message": "视频不存在" }
{ "code": 500, "message": "只能点赞公开视频" }
```

---

## 完整流程脚本

### Bash 完整流程

一键运行，完成登录→上传封面→上传视频文件→创建文章→创建视频的全流程。

> **SSO 环境注意**：此脚本使用密码登录，适用于开发环境或管理员账号。SSO 自动创建的用户没有密码，需替换登录部分为 [方式二：SSO 认证](#方式二sso-认证生产环境) 中的交互式脚本获取 Token，或直接通过 [方式三：浏览器 DevTools](#方式三浏览器-devtools-提取) 提取后赋值给 `TOKEN` 变量。

```bash
#!/usr/bin/env bash
set -euo pipefail

# ========== 配置 ==========
BASE_URL="http://localhost:8080"
USERNAME="student1"
PASSWORD="123456"
# SSO 环境：注释掉上方账号密码和下方登录步骤，直接设置:
# TOKEN="从浏览器DevTools或SSO脚本获取的JWT"

# ========== 1. 登录获取 Token ==========
echo ">>> 1. 登录..."
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"'"$USERNAME"'","password":"'"$PASSWORD"'"}')

TOKEN=$(echo "$LOGIN_RESP" | jq -r '.data.token')
if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  echo "登录失败: $LOGIN_RESP"
  exit 1
fi
echo "登录成功, Token: ${TOKEN:0:20}..."

AUTH="Authorization: Bearer $TOKEN"

# ========== 2. 上传封面图 ==========
echo ""
echo ">>> 2. 上传封面图..."

# 创建测试图片（1x1 红色像素 JPEG）
COVER_FILE=$(mktemp /tmp/cover_XXXXXX.jpg)
printf '\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00' > "$COVER_FILE"
# 如果有真实图片，替换为: COVER_FILE="/path/to/real-cover.jpg"

COVER_RESP=$(curl -s -X POST "$BASE_URL/api/media/upload" \
  -H "$AUTH" \
  -F "file=@$COVER_FILE" \
  -F "mediaType=IMAGE")

COVER_ID=$(echo "$COVER_RESP" | jq -r '.data.mediaId // empty')
if [ -z "$COVER_ID" ]; then
  echo "封面上传失败: $COVER_RESP"
  echo "(继续执行，不使用封面)"
  COVER_URL=""
else
  COVER_URL="/api/media/$COVER_ID/download"
  echo "封面上传成功, mediaId=$COVER_ID, url=$COVER_URL"
fi

rm -f "$COVER_FILE"

# ========== 3. 创建文章 (纯文本) ==========
echo ""
echo ">>> 3. 创建纯文本文章..."
TEXT_RESP=$(curl -s -X POST "$BASE_URL/api/user/texts/upload" \
  -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "The Importance of Reading in English",
    "content": "Reading is one of the most effective ways to improve your English skills. When you read regularly, you naturally expand your vocabulary, improve your grammar, and develop a better understanding of sentence structure.\n\nStart with materials at your level and gradually increase the difficulty. News articles, short stories, and graded readers are all excellent choices for English learners.",
    "contentType": "TEXT",
    "difficulty": "MEDIUM",
    "tags": "reading,study-tips,vocabulary",
    "coverUrl": "'"$COVER_URL"'"
  }')

TEXT_ID=$(echo "$TEXT_RESP" | jq -r '.data.textId // empty')
echo "文章创建结果: $TEXT_RESP"
[ -n "$TEXT_ID" ] && echo "文章 ID: $TEXT_ID"

# ========== 4. 创建文章 (Markdown) ==========
echo ""
echo ">>> 4. 创建 Markdown 文章..."
MD_RESP=$(curl -s -X POST "$BASE_URL/api/user/texts/upload" \
  -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Common English Idioms",
    "content": "",
    "markdownContent": "# Common English Idioms\n\n## Weather Idioms\n\n| Idiom | Meaning | Example |\n|-------|---------|--------|\n| Under the weather | Feeling sick | I am feeling **under the weather** today. |\n| Rain check | Postpone | Can I take a **rain check** on dinner? |\n\n## Animal Idioms\n\n- **Let the cat out of the bag** — Reveal a secret\n- **Kill two birds with one stone** — Accomplish two things at once\n\n> Practice using these idioms in your daily conversations!",
    "contentType": "MARKDOWN",
    "difficulty": "MEDIUM",
    "tags": "idioms,vocabulary,intermediate"
  }')

MD_ID=$(echo "$MD_RESP" | jq -r '.data.textId // empty')
echo "Markdown 文章创建结果: $MD_RESP"
[ -n "$MD_ID" ] && echo "Markdown 文章 ID: $MD_ID"

# ========== 5. 上传视频文件 ==========
echo ""
echo ">>> 5. 上传视频文件..."
# 替换为真实视频文件路径
VIDEO_FILE="/path/to/english-lesson.mp4"
if [ ! -f "$VIDEO_FILE" ]; then
  echo "视频文件不存在: $VIDEO_FILE (跳过视频上传流程)"
  echo ""
  echo "========== 完成 =========="
  echo "文章 ID: ${TEXT_ID:-无}"
  echo "Markdown 文章 ID: ${MD_ID:-无}"
  exit 0
fi

VIDEO_RESP=$(curl -s -X POST "$BASE_URL/api/media/upload" \
  -H "$AUTH" \
  -F "file=@$VIDEO_FILE" \
  -F "mediaType=VIDEO")

VIDEO_MEDIA_ID=$(echo "$VIDEO_RESP" | jq -r '.data.mediaId // empty')
if [ -z "$VIDEO_MEDIA_ID" ]; then
  echo "视频文件上传失败: $VIDEO_RESP"
  exit 1
fi
echo "视频文件上传成功, mediaId=$VIDEO_MEDIA_ID"

# ========== 6. 创建视频条目 ==========
echo ""
echo ">>> 6. 创建视频条目..."
VID_RESP=$(curl -s -X POST "$BASE_URL/api/user/videos/upload" \
  -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Daily English Conversation Practice",
    "mediaId": '"$VIDEO_MEDIA_ID"',
    "description": "Practice common daily conversations in English. Perfect for beginners.",
    "tags": "conversation,beginner,daily-english",
    "subtitleContent": "1\n00:00:01,000 --> 00:00:04,000\nHello! How are you today?\n\n2\n00:00:04,500 --> 00:00:07,000\nI am fine, thank you. And you?",
    "subtitleContentZh": "1\n00:00:01,000 --> 00:00:04,000\n你好！你今天怎么样？\n\n2\n00:00:04,500 --> 00:00:07,000\n我很好，谢谢。你呢？"
  }')

VIDEO_ID=$(echo "$VID_RESP" | jq -r '.data.videoId // empty')
echo "视频创建结果: $VID_RESP"
[ -n "$VIDEO_ID" ] && echo "视频 ID: $VIDEO_ID"

# ========== 7. 验证：查看我的上传列表 ==========
echo ""
echo ">>> 7. 验证：我的文章列表"
curl -s "$BASE_URL/api/user/texts/my-uploads" -H "$AUTH" | jq '.data | length'

echo ""
echo ">>> 8. 验证：我的视频列表"
curl -s "$BASE_URL/api/user/videos/my-uploads" -H "$AUTH" | jq '.data | length'

# ========== 完成 ==========
echo ""
echo "========== 全部完成 =========="
echo "封面图 mediaId: ${COVER_ID:-无}"
echo "文章 ID: ${TEXT_ID:-无}"
echo "Markdown 文章 ID: ${MD_ID:-无}"
echo "视频 mediaId: ${VIDEO_MEDIA_ID:-无}"
echo "视频 ID: ${VIDEO_ID:-无}"
```

---

### JavaScript (Node.js) 完整流程

> **SSO 环境注意**：同上，SSO 用户需通过浏览器 DevTools 或 SSO 流程获取 Token，然后直接赋值给 `TOKEN` 常量，跳过密码登录步骤。

```javascript
/**
 * LangHack 上传功能完整测试脚本
 * 运行: node upload-test.js
 * 依赖: node >= 18 (内置 fetch)
 */

const fs = require('fs')
const path = require('path')

const BASE_URL = 'http://localhost:8080'
const USERNAME = 'student1'
const PASSWORD = '123456'
// SSO 环境：注释掉上方账号密码和 main() 中的登录步骤，直接设置:
// const TOKEN = '从浏览器DevTools或SSO脚本获取的JWT'

async function request(url, options = {}) {
  const resp = await fetch(`${BASE_URL}${url}`, options)
  return resp.json()
}

async function main() {
  // ========== 1. 登录 ==========
  console.log('>>> 1. 登录...')
  const loginResp = await request('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USERNAME, password: PASSWORD })
  })

  if (loginResp.code !== 200) {
    console.error('登录失败:', loginResp)
    process.exit(1)
  }

  const TOKEN = loginResp.data.token
  const AUTH_HEADERS = {
    'Authorization': `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  }
  console.log(`登录成功, 用户: ${loginResp.data.user.username}, 角色: ${loginResp.data.user.role}`)

  // ========== 2. 上传封面图 ==========
  console.log('\n>>> 2. 上传封面图...')
  let coverMediaId = null

  const coverPath = './test-cover.jpg'  // 替换为真实图片路径
  if (fs.existsSync(coverPath)) {
    const coverFile = fs.readFileSync(coverPath)
    const formData = new FormData()
    formData.append('file', new Blob([coverFile], { type: 'image/jpeg' }), 'cover.jpg')
    formData.append('mediaType', 'IMAGE')

    const coverResp = await request('/api/media/upload', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${TOKEN}` },
      body: formData
    })

    if (coverResp.code === 200) {
      coverMediaId = coverResp.data.mediaId
      console.log(`封面上传成功: mediaId=${coverMediaId}, url=${coverResp.data.url}`)
    } else {
      console.log('封面上传失败:', coverResp.message)
    }
  } else {
    console.log('跳过封面上传（文件不存在）')
  }

  // ========== 3. 创建纯文本文章 ==========
  console.log('\n>>> 3. 创建纯文本文章...')
  const textResp = await request('/api/user/texts/upload', {
    method: 'POST',
    headers: AUTH_HEADERS,
    body: JSON.stringify({
      title: 'The Importance of Reading in English',
      content: 'Reading is one of the most effective ways to improve your English skills. When you read regularly, you naturally expand your vocabulary.',
      contentType: 'TEXT',
      difficulty: 'MEDIUM',
      tags: 'reading,study-tips',
      coverUrl: coverMediaId ? `/api/media/${coverMediaId}/download` : undefined
    })
  })

  if (textResp.code === 200) {
    console.log(`文章创建成功: textId=${textResp.data.textId}`)
  } else {
    console.log('文章创建失败:', textResp.message)
  }

  // ========== 4. 创建 Markdown 文章 ==========
  console.log('\n>>> 4. 创建 Markdown 文章...')
  const mdResp = await request('/api/user/texts/upload', {
    method: 'POST',
    headers: AUTH_HEADERS,
    body: JSON.stringify({
      title: 'Common English Idioms',
      content: '',
      markdownContent: '# Common English Idioms\n\n## Weather Idioms\n\n- **Under the weather** — Feeling sick\n- **Rain check** — Postpone\n\n> Use these in daily conversations!',
      contentType: 'MARKDOWN',
      difficulty: 'MEDIUM',
      tags: 'idioms,vocabulary'
    })
  })

  if (mdResp.code === 200) {
    console.log(`Markdown 文章创建成功: textId=${mdResp.data.textId}`)
  } else {
    console.log('Markdown 文章创建失败:', mdResp.message)
  }

  // ========== 5. 上传视频 (如果有视频文件) ==========
  const videoPath = './test-video.mp4'  // 替换为真实视频路径
  if (fs.existsSync(videoPath)) {
    console.log('\n>>> 5. 上传视频文件...')
    const videoFile = fs.readFileSync(videoPath)
    const videoFormData = new FormData()
    videoFormData.append('file', new Blob([videoFile], { type: 'video/mp4' }), 'lesson.mp4')
    videoFormData.append('mediaType', 'VIDEO')

    const videoMediaResp = await request('/api/media/upload', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${TOKEN}` },
      body: videoFormData
    })

    if (videoMediaResp.code === 200) {
      const videoMediaId = videoMediaResp.data.mediaId
      console.log(`视频文件上传成功: mediaId=${videoMediaId}`)

      // 创建视频条目
      console.log('\n>>> 6. 创建视频条目...')
      const vidResp = await request('/api/user/videos/upload', {
        method: 'POST',
        headers: AUTH_HEADERS,
        body: JSON.stringify({
          title: 'Daily English Conversation',
          mediaId: videoMediaId,
          description: 'Practice daily conversations in English.',
          tags: 'conversation,beginner',
          coverMediaId: coverMediaId,
          subtitleContent: '1\n00:00:01,000 --> 00:00:04,000\nHello! How are you?',
          subtitleContentZh: '1\n00:00:01,000 --> 00:00:04,000\n你好！你怎么样？'
        })
      })

      if (vidResp.code === 200) {
        console.log(`视频创建成功: videoId=${vidResp.data.videoId}`)
      } else {
        console.log('视频创建失败:', vidResp.message)
      }
    } else {
      console.log('视频文件上传失败:', videoMediaResp.message)
    }
  } else {
    console.log('\n>>> 5. 跳过视频上传（文件不存在）')
  }

  // ========== 验证 ==========
  console.log('\n>>> 验证：查看我的上传...')
  const myTexts = await request('/api/user/texts/my-uploads', {
    headers: { 'Authorization': `Bearer ${TOKEN}` }
  })
  console.log(`我的文章数量: ${myTexts.data?.length || 0}`)

  const myVideos = await request('/api/user/videos/my-uploads', {
    headers: { 'Authorization': `Bearer ${TOKEN}` }
  })
  console.log(`我的视频数量: ${myVideos.data?.length || 0}`)

  console.log('\n========== 完成 ==========')
}

main().catch(console.error)
```

---

## 附录

### A. 统一响应结构 Result

所有接口统一返回 `Result<T>` 结构（`com.english.dto.Result`）：

```json
{
  "code": 200,          // 200=成功, 500=失败
  "message": "操作成功", // 人类可读消息
  "data": { ... },      // 业务数据（成功时存在，@JsonInclude(NON_NULL) 控制）
  "detail": "..."       // 仅 OPS 角色可见的错误详情（通常为 null）
}
```

### B. 实体字段说明

#### B.1 Media 实体（`media` 表）

| 字段 | DB列名 | Java类型 | 说明 |
|------|--------|----------|------|
| id | id | Long | 自增主键 |
| mediaType | media_type | String | `IMAGE` / `VIDEO` |
| fileName | file_name | String | 原始文件名 |
| filePath | file_path | String | 文件系统路径（仅VIDEO） |
| fileContent | file_content | byte[] | 文件内容BLOB（仅IMAGE） |
| fileSize | file_size | Long | 文件大小(bytes) |
| mimeType | mime_type | String | MIME类型 |
| width | width | Integer | 视频宽度(px) |
| height | height | Integer | 视频高度(px) |
| duration | duration | Integer | 时长(秒) |
| thumbnailPath | thumbnail_path | String | 缩略图路径 |
| uploadedBy | uploaded_by | Long | 上传者 user.id |
| textId | text_id | Long | 关联的文章ID |
| description | description | String | 描述 |
| status | status | String | 状态 |
| transcodeStatus | transcode_status | String | 转码状态 |
| transcodeError | transcode_error | String | 转码错误信息 |
| originalCodec | original_codec | String | 原始视频编码 |
| deleted | deleted | Integer | 软删除 (0/1, @TableLogic) |
| createdAt | created_time | LocalDateTime | 创建时间 |
| updatedAt | updated_time | LocalDateTime | 更新时间 |

#### B.2 Text 实体（`texts` 表）

| 字段 | DB列名 | Java类型 | 说明 |
|------|--------|----------|------|
| id | id | Long | 自增主键 |
| unitId | unit_id | Long | 所属单元（用户上传=999） |
| title | title | String | 标题 |
| content | content | String | 纯文本内容 (NOT NULL) |
| markdownContent | markdown_content | String | Markdown内容 |
| contentType | content_type | String | `TEXT` / `MARKDOWN` |
| coverUrl | cover_url | String | 封面图URL |
| audioUrl | audio_url | String | 音频URL |
| difficulty | difficulty | String | `EASY`/`MEDIUM`/`HARD` |
| orderNum | order_num | Integer | 排序号 |
| uploadedBy | uploaded_by | Long | 上传者ID |
| tags | tags | String | 标签(逗号分隔) |
| viewCount | view_count | Integer | 浏览次数 |
| likeCount | like_count | Integer | 点赞次数 |
| isPublic | is_public | Integer | 是否公开(0/1) |
| status | status | String | `PENDING`/`APPROVED`/`REJECTED` |
| isFeatured | is_featured | Integer | 是否推荐 |
| featuredPriority | featured_priority | Integer | 推荐优先级 |
| deleted | deleted | Integer | 软删除 |
| createdAt | created_time | LocalDateTime | 创建时间 |
| updatedAt | updated_time | LocalDateTime | 更新时间 |

> Text 还有 `cet4Content`, `cet6Content`, `academicContent`, `chineseParallelContent` 等版本字段及对应 markdown 版本，由系统生成，用户上传时不涉及。

#### B.3 Video 实体（`videos` 表）

| 字段 | DB列名 | Java类型 | 说明 |
|------|--------|----------|------|
| id | id | Long | 自增主键 |
| title | title | String | 标题 |
| description | description | String | 描述 |
| mediaId | media_id | Long | 关联media表(视频文件) |
| coverMediaId | cover_media_id | Long | 关联media表(封面图) |
| videoUrl | video_url | String | 视频URL(备用) |
| coverUrl | cover_url | String | 封面URL(备用) |
| subtitleContent | subtitle_content | String | 英文字幕 |
| subtitleContentZh | subtitle_content_zh | String | 中文字幕 |
| userId | user_id | Long | 上传者ID |
| isPublic | is_public | Integer | 是否公开(0/1) |
| isFeatured | is_featured | Integer | 是否推荐 |
| featuredPriority | featured_priority | Integer | 推荐优先级 |
| tags | tags | String | 标签 |
| viewCount | view_count | Integer | 浏览次数 |
| likeCount | like_count | Integer | 点赞次数 |
| status | status | String | `PENDING`/`APPROVED`/`REJECTED` |
| associatedTextId | associated_text_id | Long | 关联的文章ID |
| transcriptionStatus | transcription_status | String | 语音识别状态 |
| deleted | deleted | Integer | 软删除 |
| createdTime | created_time | LocalDateTime | 创建时间 |
| updatedTime | updated_time | LocalDateTime | 更新时间 |
| transcodeStatus | - | String | **虚拟字段** (@TableField(exist=false)，运行时从media表获取) |
| transcodeError | - | String | **虚拟字段** |

### C. 文件限制汇总

| 维度 | IMAGE | VIDEO |
|------|-------|-------|
| **最大大小（后端）** | 10 MB (可配置 `media.upload.max-image-size`) | 512 MB (可配置 `media.upload.max-video-size`) |
| **后端允许扩展名** | jpg, jpeg, png, gif, webp | mp4, webm, ogg, mov, avi, mkv |
| **后端 MIME 校验** | 以 `image/` 开头 | 以 `video/` 开头 |
| **前端允许 MIME** | image/jpeg, image/png, image/gif, image/webp | video/mp4, video/webm, video/ogg |
| **存储方式** | 数据库 BLOB | 文件系统 |
| **并发上传** | 最多 5 个（共享信号量） | 同左 |
| **上传超时（前端）** | 10 分钟 | 10 分钟 |

### D. 错误码与错误消息

| 接口 | 错误消息 | 触发条件 |
|------|---------|---------|
| **通用** | `"未授权"` | 未携带/无效 JWT Token |
| **通用** | `"用户不存在"` | Token 中的用户名在DB中找不到 |
| **媒体上传** | `"文件不能为空"` | 上传空文件 |
| **媒体上传** | `"文件大小超过限制（最大 XMB）"` | 超过大小限制 |
| **媒体上传** | `"不支持的文件类型"` | MIME/扩展名不在白名单 |
| **媒体上传** | `"服务器上传队列已满，请稍后重试"` | 并发超过5个，等待30s超时 |
| **媒体上传** | `"上传操作被中断"` | 线程中断 |
| **文章上传** | `"课文标题不能为空"` | title为null/空/全空格 |
| **文章上传** | `"课文内容不能为空"` | TEXT类型 content为空 |
| **文章上传** | `"Markdown内容不能为空"` | MARKDOWN类型 markdownContent为空 |
| **文章上传** | `"单元课文数量已满，请联系管理员"` | orderNum接近Integer.MAX_VALUE |
| **视频上传** | `"视频标题不能为空"` | title为null/空 |
| **视频上传** | `"视频文件不能为空"` | mediaId为null |
| **修改/删除** | `"没有权限修改/删除此课文/视频"` | 非上传者本人操作 |
| **删除** | `"已审核通过的内容不可删除，如需调整请走修改流程"` | status=APPROVED时尝试删除 |
| **媒体删除** | `"无权删除此资源"` | 非上传者且非ADMIN |
| **转码重试** | `"仅视频支持重试转码"` | 对非VIDEO类型重试 |
| **转码重试** | `"当前正在转码中"` | transcodeStatus=PROCESSING时重试 |
