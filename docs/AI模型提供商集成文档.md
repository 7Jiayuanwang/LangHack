# LangHack AI 模型提供商集成文档

> 本文档提炼整合 LangHack 项目中所有 AI 模型提供商的配置、请求协议和路由架构。
> 最后更新: 2026-04-17

---

## 目录

- [1. 提供商总览](#1-提供商总览)
- [2. HiAgent (文章版本生成 / AI 问答)](#2-hiagent-文章版本生成--ai-问答)
- [3. OpenRouter (文章版本生成 — 备用)](#3-openrouter-文章版本生成--备用)
- [4. SiliconFlow + Qwen (翻译服务)](#4-siliconflow--qwen-翻译服务)
- [5. 腾讯云 ASR (语音识别)](#5-腾讯云-asr-语音识别)
- [6. 多提供商路由架构](#6-多提供商路由架构)
- [7. 熔断器配置](#7-熔断器配置)
- [8. 环境变量速查表](#8-环境变量速查表)
- [9. ai_provider 表与动态路由](#9-ai_provider-表与动态路由)
- [10. 指标监控 (AiCallMetricsService)](#10-指标监控-aicallmetricsservice)

---

## 1. 提供商总览

| 提供商 | 协议类型 | 功能领域 | 实现类 | 状态 |
|--------|---------|---------|--------|------|
| **HiAgent** | 自有两步协议 | 文章版本生成、AI 问答 | `HiAgentServiceImpl` | 活跃 (主) |
| **OpenRouter** | OpenAI Chat Completions | 文章版本生成 (备用) | `OpenRouterServiceImpl` | 活跃 (备) |
| **SiliconFlow + Qwen** | OpenAI Chat Completions | 文本翻译 | `TencentTranslateService` | 活跃 |
| **腾讯云 ASR** | 腾讯云 SDK | 语音识别 | `TencentAsrService` | 活跃 |
| **HunyuanConfig** | — | — (未使用) | `HunyuanConfig` | 废弃 |

---

## 2. HiAgent (文章版本生成 / AI 问答)

### 2.1 配置

**配置类**: `com.english.config.HiAgentConfig`
**YAML 前缀**: `hiagent`

> **⚠️ 应用隔离要求**：AI 改写（CET-4/6/学术版/中英对照）与 AI 问答（文章划词问答）必须使用**两套完全不同**的 HiAgent 应用凭证。QA 绝不复用改写凭证。流式问答若在输出前失败，可尝试运维端另行配置的 `chat` 提供商；未配置可用提供商时仍会返回错误。历史文字问答默认不转发，仅在学生主动勾选后限量用于故障回退。

| 属性 | YAML 键 | 环境变量 | 默认值 | 用途 |
|------|--------|---------|--------|------|
| apiKey | `hiagent.api-key` | `HIAGENT_API_KEY` | 空 | 改写应用 |
| apiUrl | `hiagent.api-url` | `HIAGENT_API_URL` | `https://agent.imau.edu.cn:32300/api/proxy/api/v1` | 共用 |
| appId | `hiagent.app-id` | `HIAGENT_APP_ID` | 空 | 改写应用 |
| userId | `hiagent.user-id` | `HIAGENT_USER_ID` | `LangHack_System` | 共用 |
| qaApiKey | `hiagent.qa-api-key` | `HIAGENT_QA_API_KEY` | 空（**必须独立配置**，不 fallback） | 问答应用 |
| qaAppId | `hiagent.qa-app-id` | `HIAGENT_QA_APP_ID` | 空（**必须独立配置**，不 fallback） | 问答应用 |
| timeout | `hiagent.timeout` | — | 60000 (ms) | 共用 |
| maxRetries | `hiagent.max-retries` | — | 3 | 共用 |

### 2.2 请求协议

HiAgent 使用**自有的两步协议**，不兼容 OpenAI API：

```
Step 1: POST {apiUrl}/create_conversation  →  获取 AppConversationID
Step 2: POST {apiUrl}/chat_query_v2        →  发送 Query，获取 answer
```

#### Step 1: 创建会话

**请求头**:

| Header | 值 | 说明 |
|--------|---|------|
| `Content-Type` | `application/json` | — |
| `Apikey` | `{apiKey}` | API 密钥 |
| `Appid` | `{appId}` | 应用 ID |
| `X-App-Id` | `{appId}` | 同 Appid |
| `X-Date` | `20260417T120000Z` | UTC 时间，格式 `yyyyMMdd'T'HHmmss'Z'` |

**请求体**:

```json
{
  "UserID": "LangHack_System",
  "Inputs": {}
}
```

**响应体**:

```json
{
  "Conversation": {
    "AppConversationID": "conv-abc123-def456"
  }
}
```

#### Step 2: 发送查询

**请求头**: 同 Step 1

**请求体**:

```json
{
  "AppConversationID": "conv-abc123-def456",
  "Query": "Please rewrite the following English article to match CET-4 difficulty level...",
  "UserID": "LangHack_System",
  "ResponseMode": "blocking"
}
```

**响应体**:

```json
{
  "answer": "The rewritten CET-4 level article content..."
}
```

### 2.3 独立请求样例 (curl)

#### 场景 A: 文章版本生成 (CET-4)

```bash
#!/bin/bash
# HiAgent 文章版本生成 — 完整 curl 示例
# 替换以下变量为实际值

HIAGENT_API_URL="https://agent.imau.edu.cn:32300/api/proxy/api/v1"
HIAGENT_API_KEY="your-hiagent-api-key"
HIAGENT_APP_ID="your-hiagent-app-id"
USER_ID="LangHack_System"

# 获取当前 UTC 时间 (格式: yyyyMMddTHHmmssZ)
X_DATE=$(TZ=UTC date +%Y%m%dT%H%M%SZ)

# ---- Step 1: 创建会话 ----
echo ">>> Step 1: Creating conversation..."

STEP1_RESPONSE=$(curl -s -X POST \
  "${HIAGENT_API_URL}/create_conversation" \
  -H "Content-Type: application/json" \
  -H "Apikey: ${HIAGENT_API_KEY}" \
  -H "Appid: ${HIAGENT_APP_ID}" \
  -H "X-App-Id: ${HIAGENT_APP_ID}" \
  -H "X-Date: ${X_DATE}" \
  -d "{
    \"UserID\": \"${USER_ID}\",
    \"Inputs\": {}
  }")

echo "Step 1 Response: ${STEP1_RESPONSE}"

# 提取 AppConversationID (需要 jq)
CONVERSATION_ID=$(echo "${STEP1_RESPONSE}" | jq -r '.Conversation.AppConversationID')

if [ -z "${CONVERSATION_ID}" ] || [ "${CONVERSATION_ID}" = "null" ]; then
  echo "ERROR: Failed to create conversation"
  exit 1
fi

echo "Conversation ID: ${CONVERSATION_ID}"

# 刷新 X-Date (两步之间可能有时间差)
X_DATE=$(TZ=UTC date +%Y%m%dT%H%M%SZ)

# ---- Step 2: 发送查询 ----
echo ">>> Step 2: Sending query..."

ORIGINAL_CONTENT="Artificial intelligence has transformed the way we interact with technology. Machine learning algorithms now power everything from recommendation systems to autonomous vehicles, fundamentally altering industries and creating new paradigms for human-computer interaction."

PROMPT="Please rewrite the following English article to match CET-4 difficulty level. Only return the rewritten text.

${ORIGINAL_CONTENT}"

STEP2_RESPONSE=$(curl -s -X POST \
  "${HIAGENT_API_URL}/chat_query_v2" \
  -H "Content-Type: application/json" \
  -H "Apikey: ${HIAGENT_API_KEY}" \
  -H "Appid: ${HIAGENT_APP_ID}" \
  -H "X-App-Id: ${HIAGENT_APP_ID}" \
  -H "X-Date: ${X_DATE}" \
  -d "{
    \"AppConversationID\": \"${CONVERSATION_ID}\",
    \"Query\": $(echo "${PROMPT}" | jq -Rs .),
    \"UserID\": \"${USER_ID}\",
    \"ResponseMode\": \"blocking\"
  }")

echo "Step 2 Response: ${STEP2_RESPONSE}"

# 提取答案
ANSWER=$(echo "${STEP2_RESPONSE}" | jq -r '.answer // .Answer')
echo "Generated CET-4 Version: ${ANSWER}"
```

#### 场景 B: AI 问答 (文章选词提问)

```bash
#!/bin/bash
# HiAgent AI 问答 — 必须使用 QA 专用凭据（与改写凭据完全隔离，不 fallback）
# 若未配置 HIAGENT_QA_API_KEY / HIAGENT_QA_APP_ID，后端将抛 BusinessException 并禁用 AI 问答

HIAGENT_API_URL="https://agent.imau.edu.cn:32300/api/proxy/api/v1"
HIAGENT_QA_API_KEY="${HIAGENT_QA_API_KEY:?AI 问答要求独立 QA 密钥，未配置则禁用}"
HIAGENT_QA_APP_ID="${HIAGENT_QA_APP_ID:?AI 问答要求独立 QA App ID，未配置则禁用}"
USER_ID="LangHack_System"

X_DATE=$(TZ=UTC date +%Y%m%dT%H%M%SZ)

# ---- Step 1: 创建 QA 会话 ----
CONV_RESP=$(curl -s -X POST \
  "${HIAGENT_API_URL}/create_conversation" \
  -H "Content-Type: application/json" \
  -H "Apikey: ${HIAGENT_QA_API_KEY}" \
  -H "Appid: ${HIAGENT_QA_APP_ID}" \
  -H "X-App-Id: ${HIAGENT_QA_APP_ID}" \
  -H "X-Date: ${X_DATE}" \
  -d "{\"UserID\": \"${USER_ID}\", \"Inputs\": {}}")

CONVERSATION_ID=$(echo "${CONV_RESP}" | jq -r '.Conversation.AppConversationID')
echo "QA Conversation ID: ${CONVERSATION_ID}"

# ---- Step 2: 发送 QA 查询 ----
X_DATE=$(TZ=UTC date +%Y%m%dT%H%M%SZ)

QA_PROMPT='你是一位专业的英语学习辅导老师。学生正在阅读一篇英语文章，并选中了一段文本。请根据选中的文本和文章上下文，用中文回答学生的问题。回答要详细、专业，帮助学生理解英语内容。

【选中文本】
Artificial intelligence has transformed the way we interact with technology.

【文章上下文】
Artificial intelligence has transformed the way we interact with technology. Machine learning algorithms now power everything from recommendation systems to autonomous vehicles.

【学生提问】
这里的 "transformed" 和 "changed" 有什么区别？'

QA_RESP=$(curl -s -X POST \
  "${HIAGENT_API_URL}/chat_query_v2" \
  -H "Content-Type: application/json" \
  -H "Apikey: ${HIAGENT_QA_API_KEY}" \
  -H "Appid: ${HIAGENT_QA_APP_ID}" \
  -H "X-App-Id: ${HIAGENT_QA_APP_ID}" \
  -H "X-Date: ${X_DATE}" \
  -d "{
    \"AppConversationID\": \"${CONVERSATION_ID}\",
    \"Query\": $(echo "${QA_PROMPT}" | jq -Rs .),
    \"UserID\": \"${USER_ID}\",
    \"ResponseMode\": \"blocking\"
  }")

echo "QA Answer: $(echo "${QA_RESP}" | jq -r '.answer // .Answer')"
```

### 2.4 Prompt 模板

| 功能 | Prompt |
|------|--------|
| CET-4 版本 | `Please rewrite the following English article to match CET-4 difficulty level. Only return the rewritten text.\n\n{content}` |
| CET-6 版本 | `Please rewrite the following English article to match CET-6 difficulty level. Only return the rewritten text.\n\n{content}` |
| 学术版 | `Please rewrite the following English article in an academic style. Only return the rewritten text.\n\n{content}` |
| 中英对照版 | `Please create a Chinese-English parallel text version of the following English article. Format: English paragraph, then Chinese paragraph. Only return the parallel text.\n\n{content}` |
| Markdown CET-4 | `Please rewrite the following Markdown article to match CET-4 difficulty level. PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n{content}` |
| Markdown CET-6 | `Please rewrite the following Markdown article to match CET-6 difficulty level. PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n{content}` |
| Markdown 学术版 | `Please rewrite the following Markdown article in an academic style. PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n{content}` |
| Markdown 中英对照 | `Please create a Chinese-English parallel text version of the following Markdown article. Format: English paragraph, then Chinese paragraph. PRESERVE ALL Markdown formatting. Only return the parallel Markdown.\n\n{content}` |
| AI 问答 | `你是一位专业的英语学习辅导老师。学生正在阅读一篇英语文章，并选中了一段文本。请根据选中的文本和文章上下文，用中文回答学生的问题。回答要详细、专业，帮助学生理解英语内容。\n\n【选中文本】\n{selectedText}\n\n【文章上下文】\n{articleContext}\n\n【学生提问】\n{userQuestion}` |

### 2.5 重试策略

- 线性退避: 1s, 2s, 3s (每次 attempt × 1000ms)
- 最大重试次数: 3 (`hiagent.max-retries`)
- Q&A 使用独立熔断器 `hiagent-qa`

---

## 3. OpenRouter (文章版本生成 — 备用)

### 3.1 配置

**配置类**: `com.english.config.OpenRouterConfig`
**YAML 前缀**: `openrouter`

| 属性 | YAML 键 | 环境变量 | 默认值 |
|------|--------|---------|--------|
| apiKey | `openrouter.api-key` | `OPENROUTER_API_KEY` | 空 |
| apiUrl | `openrouter.api-url` | `OPENROUTER_API_URL` | `https://openrouter.ai/api/v1/chat/completions` |
| model | `openrouter.model` | `OPENROUTER_MODEL` | `tngtech/deepseek-r1t2-chimera:free` |
| timeout | `openrouter.timeout` | — | 60000 (ms) |

### 3.2 请求协议

OpenRouter 使用标准 **OpenAI Chat Completions API**，额外附加两个自定义 Header：

**请求头**:

| Header | 值 | 说明 |
|--------|---|------|
| `Content-Type` | `application/json` | — |
| `Authorization` | `Bearer {apiKey}` | Bearer 认证 |
| `HTTP-Referer` | `http://localhost:8080` | OpenRouter 应用标识 |
| `X-Title` | `English Learning Platform` | OpenRouter 应用名称 |

**请求体**:

```json
{
  "model": "tngtech/deepseek-r1t2-chimera:free",
  "messages": [
    {
      "role": "user",
      "content": "Please rewrite the following English article to match CET-4 difficulty level. Only return the rewritten text.\n\n..."
    }
  ]
}
```

**响应体**:

```json
{
  "choices": [
    {
      "message": {
        "content": "The rewritten CET-4 level article content..."
      }
    }
  ]
}
```

### 3.3 独立请求样例 (curl)

#### 场景: 文章版本生成 (学术版)

```bash
#!/bin/bash
# OpenRouter 文章版本生成 — 完整 curl 示例

OPENROUTER_API_URL="https://openrouter.ai/api/v1/chat/completions"
OPENROUTER_API_KEY="your-openrouter-api-key"
OPENROUTER_MODEL="tngtech/deepseek-r1t2-chimera:free"

ORIGINAL_CONTENT="Artificial intelligence has transformed the way we interact with technology. Machine learning algorithms now power everything from recommendation systems to autonomous vehicles, fundamentally altering industries and creating new paradigms for human-computer interaction."

PROMPT="Please rewrite the following English article in an academic style. Only return the rewritten text.

${ORIGINAL_CONTENT}"

RESPONSE=$(curl -s -X POST \
  "${OPENROUTER_API_URL}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${OPENROUTER_API_KEY}" \
  -H "HTTP-Referer: http://localhost:8080" \
  -H "X-Title: English Learning Platform" \
  -d "{
    \"model\": \"${OPENROUTER_MODEL}\",
    \"messages\": [
      {
        \"role\": \"user\",
        \"content\": $(echo "${PROMPT}" | jq -Rs .)
      }
    ]
  }")

echo "Response: ${RESPONSE}"

# 提取生成内容
RESULT=$(echo "${RESPONSE}" | jq -r '.choices[0].message.content')
echo "Academic Version: ${RESULT}"
```

#### 场景: Markdown 中英对照版

```bash
#!/bin/bash
# OpenRouter Markdown 中英对照版 — curl 示例

OPENROUTER_API_URL="https://openrouter.ai/api/v1/chat/completions"
OPENROUTER_API_KEY="your-openrouter-api-key"
OPENROUTER_MODEL="tngtech/deepseek-r1t2-chimera:free"

MARKDOWN_CONTENT="# The Future of AI\n\nAI is changing the world.\n\n![AI Image](/api/media/123/download)"

PROMPT="Please create a Chinese-English parallel text version of the following Markdown article. Format: English paragraph, then Chinese paragraph. PRESERVE ALL Markdown formatting. Only return the parallel Markdown.

${MARKDOWN_CONTENT}"

RESPONSE=$(curl -s -X POST \
  "${OPENROUTER_API_URL}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${OPENROUTER_API_KEY}" \
  -H "HTTP-Referer: http://localhost:8080" \
  -H "X-Title: English Learning Platform" \
  -d "{
    \"model\": \"${OPENROUTER_MODEL}\",
    \"messages\": [
      {
        \"role\": \"user\",
        \"content\": $(echo "${PROMPT}" | jq -Rs .)
      }
    ]
  }")

echo "Chinese-Parallel Markdown: $(echo "${RESPONSE}" | jq -r '.choices[0].message.content')"
```

### 3.4 Prompt 模板

与 HiAgent 相同（见 2.4 节），唯一差异是 Markdown 版本的 Prompt 加了 `PRESERVE ALL Markdown formatting` 要求。

---

## 4. SiliconFlow + Qwen (翻译服务)

### 4.1 配置

**实现类**: `com.english.service.TencentTranslateService` (名称有误导性，实际为通用 OpenAI 兼容翻译)
**YAML 前缀**: `ai.translate`

| 属性 | YAML 键 | 环境变量 | 默认值 |
|------|--------|---------|--------|
| apiUrl | `ai.translate.api-url` | `AI_TRANSLATE_API_URL` | `https://api.siliconflow.cn/v1/chat/completions` |
| apiKey | `ai.translate.api-key` | `AI_TRANSLATE_API_KEY` | 空 |
| model | `ai.translate.model` | `AI_TRANSLATE_MODEL` | `Qwen/Qwen2.5-7B-Instruct` |
| timeout | `ai.translate.timeout` | `AI_TRANSLATE_TIMEOUT` | 30000 (ms) |

**配置解析优先级** (由高到低):

1. `ai_provider` 表 — 加权轮询选取 (`aiProviderService.pick("translate")`)
2. `system_config` 表 — `ai_translate` 组
3. `application.yml` 默认值 — 上述环境变量

### 4.2 请求协议

标准 **OpenAI Chat Completions API**，URL 自动规范化 (缺失时追加 `/v1/chat/completions`)：

**请求头**:

| Header | 值 | 说明 |
|--------|---|------|
| `Content-Type` | `application/json` | — |
| `Authorization` | `Bearer {apiKey}` | Bearer 认证 |

**请求体**:

```json
{
  "model": "Qwen/Qwen2.5-7B-Instruct",
  "messages": [
    {
      "role": "user",
      "content": "将以下英语翻译为中文，只返回翻译结果，不要附加任何解释。不要使用 LaTeX 数学标记（如 \\(...\\) 或 $$...$$），用普通文本表示数学公式（如 2^t 写作 2的t次方）:\nHello, world!"
    }
  ]
}
```

**响应体**:

```json
{
  "choices": [
    {
      "message": {
        "content": "你好，世界！"
      }
    }
  ]
}
```

### 4.3 独立请求样例 (curl)

#### 场景 A: 英译中

```bash
#!/bin/bash
# SiliconFlow + Qwen 英译中 — 完整 curl 示例

API_URL="https://api.siliconflow.cn/v1/chat/completions"
API_KEY="your-siliconflow-api-key"
MODEL="Qwen/Qwen2.5-7B-Instruct"

SOURCE_TEXT="Artificial intelligence has transformed the way we interact with technology, enabling machines to learn from experience and perform tasks that typically require human intelligence."

PROMPT="将以下英语翻译为中文，只返回翻译结果，不要附加任何解释。不要使用 LaTeX 数学标记（如 \\(...\\) 或 $$...$$），用普通文本表示数学公式（如 2^t 写作 2的t次方）:
${SOURCE_TEXT}"

RESPONSE=$(curl -s -X POST \
  "${API_URL}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${API_KEY}" \
  -d "{
    \"model\": \"${MODEL}\",
    \"messages\": [
      {
        \"role\": \"user\",
        \"content\": $(echo "${PROMPT}" | jq -Rs .)
      }
    ]
  }")

echo "Translation: $(echo "${RESPONSE}" | jq -r '.choices[0].message.content')"
```

#### 场景 B: 中译英

```bash
#!/bin/bash
# SiliconFlow + Qwen 中译英 — 完整 curl 示例

API_URL="https://api.siliconflow.cn/v1/chat/completions"
API_KEY="your-siliconflow-api-key"
MODEL="Qwen/Qwen2.5-7B-Instruct"

SOURCE_TEXT="人工智能已经改变了我们与技术交互的方式，使机器能够从经验中学习并执行通常需要人类智能的任务。"

PROMPT="将以下中文翻译为英语，只返回翻译结果，不要附加任何解释。不要使用 LaTeX 数学标记（如 \\(...\\) 或 $$...$$），用普通文本表示数学公式（如 2^t 写作 2的t次方）:
${SOURCE_TEXT}"

RESPONSE=$(curl -s -X POST \
  "${API_URL}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${API_KEY}" \
  -d "{
    \"model\": \"${MODEL}\",
    \"messages\": [
      {
        \"role\": \"user\",
        \"content\": $(echo "${PROMPT}" | jq -Rs .)
      }
    ]
  }")

echo "Translation: $(echo "${RESPONSE}" | jq -r '.choices[0].message.content')"
```

#### 场景 C: 自动检测语言方向

```bash
#!/bin/bash
# 自动检测: 如果包含中文字符 → 中译英, 否则 → 英译中

API_URL="https://api.siliconflow.cn/v1/chat/completions"
API_KEY="your-siliconflow-api-key"
MODEL="Qwen/Qwen2.5-7B-Instruct"

TEXT="人工智能改变了世界"  # 或 "AI changed the world"

# 检测是否包含中文字符
if echo "${TEXT}" | grep -qP '[\x{4e00}-\x{9fa5}]'; then
  SOURCE_LANG="中文"
  TARGET_LANG="英语"
else
  SOURCE_LANG="英语"
  TARGET_LANG="中文"
fi

PROMPT="将以下${SOURCE_LANG}翻译为${TARGET_LANG}，只返回翻译结果，不要附加任何解释。不要使用 LaTeX 数学标记（如 \\(...\\) 或 $$...$$），用普通文本表示数学公式（如 2^t 写作 2的t次方）:
${TEXT}"

RESPONSE=$(curl -s -X POST \
  "${API_URL}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${API_KEY}" \
  -d "{
    \"model\": \"${MODEL}\",
    \"messages\": [
      {
        \"role\": \"user\",
        \"content\": $(echo "${PROMPT}" | jq -Rs .)
      }
    ]
  }")

echo "Auto Translation (${SOURCE_LANG}→${TARGET_LANG}): $(echo "${RESPONSE}" | jq -r '.choices[0].message.content')"
```

### 4.4 翻译 Prompt 模板

```
将以下{sourceLang}翻译为{targetLang}，只返回翻译结果，不要附加任何解释。
不要使用 LaTeX 数学标记（如 \(...\) 或 $$...$$），用普通文本表示数学公式（如 2^t 写作 2的t次方）:
{text}
```

**语言映射**:

| 代码 | Prompt 中的名称 |
|------|---------------|
| `en` | 英语 |
| `zh` | 中文 |
| `auto` | 原文 |

### 4.5 并发控制

- `Semaphore(10)`: 最多 10 个并发翻译请求
- 获取超时: 5 秒 (`tryAcquire(5, TimeUnit.SECONDS)`)
- 超时后抛出 `BusinessException("翻译服务繁忙，请稍后重试")`

---

## 5. 腾讯云 ASR (语音识别)

### 5.1 配置

**实现类**: `com.english.service.TencentAsrService`
**YAML 前缀**: `tencent.asr`

| 属性 | YAML 键 | 环境变量 | 默认值 |
|------|--------|---------|--------|
| secretId | `tencent.asr.secret-id` | `TENCENT_ASR_SECRET_ID` | 空 |
| secretKey | `tencent.asr.secret-key` | `TENCENT_ASR_SECRET_KEY` | 空 |
| region | `tencent.asr.region` | `TENCENT_ASR_REGION` | `ap-guangzhou` |

### 5.2 请求协议

腾讯云 ASR 使用 **原生 Java SDK**，不是 REST API。底层通过 `asr.tencentcloudapi.com` 端点通信。

**SDK 调用链**:

```
AsrClient (初始化: Credential + HttpProfile + ClientProfile)
  └→ SentenceRecognition(request)
       ├→ SourceType: 1 (Base64 数据包传输)
       ├→ VoiceFormat: "webm" | "mp3" | ...
       ├→ Data: Base64 编码的音频数据
       └→ EngSerViceType: "16k_zh" | "16k_en"
```

**响应**: `SentenceRecognitionResponse.getResult()` → 识别出的文本

### 5.3 独立请求样例

#### 场景 A: Java SDK 完整示例

```java
import com.tencentcloudapi.asr.v20190614.AsrClient;
import com.tencentcloudapi.asr.v20190614.models.SentenceRecognitionRequest;
import com.tencentcloudapi.asr.v20190614.models.SentenceRecognitionResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;

/**
 * 腾讯云 ASR 语音识别 — 独立可运行示例
 *
 * 依赖 (Maven):
 *   <dependency>
 *     <groupId>com.tencentcloudapi</groupId>
 *     <artifactId>tencentcloud-sdk-java-asr</artifactId>
 *     <version>3.1.xxx</version>
 *   </dependency>
 */
public class TencentAsrExample {

    public static void main(String[] args) {
        // 1. 配置凭据
        String secretId = System.getenv("TENCENT_ASR_SECRET_ID");
        String secretKey = System.getenv("TENCENT_ASR_SECRET_KEY");
        String region = "ap-guangzhou";

        if (secretId == null || secretKey == null) {
            System.err.println("请设置 TENCENT_ASR_SECRET_ID 和 TENCENT_ASR_SECRET_KEY 环境变量");
            return;
        }

        // 2. 创建客户端
        Credential cred = new Credential(secretId, secretKey);

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("asr.tencentcloudapi.com");
        httpProfile.setConnTimeout(10);  // 连接超时: 10 秒
        httpProfile.setReadTimeout(30);  // 读取超时: 30 秒

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        AsrClient client = new AsrClient(cred, region, clientProfile);

        // 3. 构建请求
        SentenceRecognitionRequest req = new SentenceRecognitionRequest();
        req.setSourceType(1L);                                    // 1: Base64 数据包传输
        req.setVoiceFormat("webm");                               // 音频格式
        req.setData(base64EncodeAudio("recording.webm"));         // Base64 音频数据
        req.setEngSerViceType("16k_en");                          // 引擎: 16k_en (英文)

        // 4. 调用识别
        try {
            SentenceRecognitionResponse resp = client.SentenceRecognition(req);
            System.out.println("识别结果: " + resp.getResult());
        } catch (Exception e) {
            System.err.println("识别失败: " + e.getMessage());
        }
    }

    /** 模拟: 读取音频文件并 Base64 编码 */
    private static String base64EncodeAudio(String filePath) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath));
            return java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("读取音频文件失败: " + e.getMessage());
        }
    }
}
```

#### 场景 B: 使用腾讯云 API Explorer 直接测试 (REST)

腾讯云 ASR 也支持通过 TC3-HMAC-SHA256 签名协议直接 HTTP 调用。以下为 Python 示例：

```python
#!/usr/bin/env python3
"""腾讯云 ASR 一句话识别 — Python 独立示例 (HTTP REST)"""

import base64, hashlib, hmac, json, time
from datetime import datetime, timezone

# ---- 配置 ----
SECRET_ID = "your-tencent-secret-id"
SECRET_KEY = "your-tencent-secret-key"
REGION = "ap-guangzhou"
SERVICE = "asr"
HOST = "asr.tencentcloudapi.com"
ACTION = "SentenceRecognition"
VERSION = "2019-06-14"

# ---- 读取音频并 Base64 编码 ----
with open("recording.webm", "rb") as f:
    audio_base64 = base64.b64encode(f.read()).decode()

# ---- 构建请求体 ----
payload = json.dumps({
    "SourceType": 1,           # 1: Base64 数据包
    "VoiceFormat": "webm",
    "Data": audio_base64,
    "EngSerViceType": "16k_en" # 16k_zh (中文) 或 16k_en (英文)
})

# ---- TC3-HMAC-SHA256 签名 ----
timestamp = int(time.time())
date = datetime.fromtimestamp(timestamp, tz=timezone.utc).strftime("%Y-%m-%d")

# 1. 拼接规范请求串
http_method = "POST"
canonical_uri = "/"
canonical_querystring = ""
ct = "application/json; charset=utf-8"
hashed_payload = hashlib.sha256(payload.encode("utf-8")).hexdigest()
canonical_headers = f"content-type:{ct}\nhost:{HOST}\nx-tc-action:{ACTION.lower()}\n"
signed_headers = "content-type;host;x-tc-action"
canonical_request = f"{http_method}\n{canonical_uri}\n{canonical_querystring}\n{canonical_headers}\n{signed_headers}\n{hashed_payload}"

# 2. 拼接待签名字符串
algorithm = "TC3-HMAC-SHA256"
credential_scope = f"{date}/{SERVICE}/tc3_request"
hashed_canonical = hashlib.sha256(canonical_request.encode("utf-8")).hexdigest()
string_to_sign = f"{algorithm}\n{timestamp}\n{credential_scope}\n{hashed_canonical}"

# 3. 计算签名
def sign(key, msg):
    return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()

secret_date = sign(f"TC3{SECRET_KEY}".encode("utf-8"), date)
secret_service = sign(secret_date, SERVICE)
secret_signing = sign(secret_service, "tc3_request")
signature = hmac.new(secret_signing, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()

# 4. 构建 Authorization
authorization = f"{algorithm} Credential={SECRET_ID}/{credential_scope}, SignedHeaders={signed_headers}, Signature={signature}"

# ---- 发送请求 ----
import urllib.request

headers = {
    "Authorization": authorization,
    "Content-Type": ct,
    "Host": HOST,
    "X-TC-Action": ACTION,
    "X-TC-Timestamp": str(timestamp),
    "X-TC-Version": VERSION,
    "X-TC-Region": REGION,
}

req = urllib.request.Request(
    f"https://{HOST}",
    data=payload.encode("utf-8"),
    headers=headers,
    method="POST"
)

with urllib.request.urlopen(req, timeout=30) as resp:
    result = json.loads(resp.read().decode())
    print("识别结果:", result["Response"]["Result"])
```

### 5.4 引擎类型映射

| 语言代码 | EngSerViceType | 说明 |
|---------|---------------|------|
| `zh` | `16k_zh` | 中文 16k 采样率 |
| `en` | `16k_en` | 英文 16k 采样率 |

### 5.5 超时配置

| 参数 | 值 | 说明 |
|------|---|------|
| 连接超时 | 10s | `httpProfile.setConnTimeout(10)` |
| 读取超时 | 30s | `httpProfile.setReadTimeout(30)` |

---

## 6. 多提供商路由架构

### 6.1 文章版本生成: 主/备模式

```
用户请求 → ArticleVersionService.checkAndGenerateVersions()
  ├─ HiAgent (主) ←── 成功 ──→ 返回结果
  │     └── 失败 ──→ OpenRouter (备) ←── 成功 ──→ 返回结果
  │                                   └── 失败 ──→ 记录 null, 继续其他版本
  └─ 并行执行 4 种版本 (CET4/CET6/Academic/ChineseParallel)
```

**关键代码** (`ArticleVersionServiceImpl.createVersionTask`):

```java
try {
    return primaryGenerator.apply(content);    // HiAgent
} catch (Exception ex) {
    return fallbackGenerator.apply(content);   // OpenRouter
}
```

- 每个版本任务独立: 120 秒超时
- 全局等待: 300 秒 (`CompletableFuture.allOf().get(300, SECONDS)`)
- 线程池: `versionGeneratorExecutor` (core=8, max=32, queue=200)

### 6.2 翻译服务: 加权轮询模式

```
翻译请求 → TencentTranslateService.translate()
  └─ resolveConfig()
       ├─ 1. aiProviderService.pick("translate")  ← 加权轮询选取
       │     └─ 从 ai_provider 表中按权重选一个 enabled=1 的记录
       ├─ 2. systemConfigService.getByGroup("ai_translate")  ← 回退
       └─ 3. application.yml 默认值  ← 最终回退
```

### 6.3 HiAgent 排除说明

HiAgent 使用自有协议 (非 OpenAI 兼容)，**不参与** `ai_provider` 表的负载均衡池。在 `AiProviderServiceImpl.seedFromEnv()` 中有明确注释：

> HiAgent 走专有协议（非 OpenAI 兼容），不加入 ai_provider 负载均衡池

---

## 7. 熔断器配置

| 熔断器名称 | 服务 | 失败率阈值 | 等待时间 | 滑动窗口 | 最小调用数 |
|-----------|------|----------|---------|---------|----------|
| `hiagent` | HiAgent 文章生成 | 50% | 30s | 10 | 5 |
| `hiagent-qa` | HiAgent AI 问答 | 50% | 15s | 5 | 3 |
| `openrouter` | OpenRouter | 50% | 30s | 10 | 5 |
| `tencent-translate` | 翻译服务 | 60% | 20s | 8 | 3 |

**忽略的异常**: `BusinessException` (不会触发熔断)

**熔断触发后的行为**:
- HiAgent/OpenRouter: 抛出 `BusinessException("AI 服务暂时不可用，请稍后重试")`
- 翻译: 抛出 `BusinessException("翻译服务暂时不可用，请稍后重试")`

---

## 8. 环境变量速查表

| 环境变量 | 用途 | 必填 | 默认值 |
|---------|------|------|--------|
| `HIAGENT_API_KEY` | HiAgent API 密钥 | 是 | — |
| `HIAGENT_API_URL` | HiAgent API 地址 | 否 | `https://agent.imau.edu.cn:32300/api/proxy/api/v1` |
| `HIAGENT_APP_ID` | HiAgent 应用 ID | 是 | — |
| `HIAGENT_USER_ID` | HiAgent 用户标识 | 否 | `LangHack_System` |
| `HIAGENT_QA_API_KEY` | HiAgent QA 密钥（AI 问答专用，与改写凭据必须不同） | 使用 HiAgent 问答时必填 | —（不复用改写凭证；流式问答可回退已配置的 `chat` 提供商） |
| `HIAGENT_QA_APP_ID` | HiAgent QA 应用 ID（AI 问答专用，与改写凭据必须不同） | 使用 HiAgent 问答时必填 | —（不复用改写凭证；流式问答可回退已配置的 `chat` 提供商） |
| `OPENROUTER_API_KEY` | OpenRouter API 密钥 | 推荐 | — |
| `OPENROUTER_API_URL` | OpenRouter API 地址 | 否 | `https://openrouter.ai/api/v1/chat/completions` |
| `OPENROUTER_MODEL` | OpenRouter 模型 | 否 | `tngtech/deepseek-r1t2-chimera:free` |
| `AI_TRANSLATE_API_URL` | 翻译 API 地址 | 否 | `https://api.siliconflow.cn/v1/chat/completions` |
| `AI_TRANSLATE_API_KEY` | 翻译 API 密钥 | 推荐 | — |
| `AI_TRANSLATE_MODEL` | 翻译模型 | 否 | `Qwen/Qwen2.5-7B-Instruct` |
| `AI_TRANSLATE_TIMEOUT` | 翻译超时 (ms) | 否 | 30000 |
| `TENCENT_ASR_SECRET_ID` | 腾讯云 ASR SecretId | 视需求 | — |
| `TENCENT_ASR_SECRET_KEY` | 腾讯云 ASR SecretKey | 视需求 | — |
| `TENCENT_ASR_REGION` | 腾讯云 ASR 区域 | 否 | `ap-guangzhou` |

---

## 9. ai_provider 表与动态路由

### 9.1 表结构

`ai_provider` 表用于动态管理 OpenAI 兼容的翻译服务提供商，支持运维面板实时增删改。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (自增) | 主键 |
| service | VARCHAR | 服务类型 (如 `translate`) |
| name | VARCHAR | 服务商名称 |
| api_url | VARCHAR | API 地址 |
| api_key | VARCHAR | API 密钥 |
| app_id | VARCHAR | 应用 ID (可选) |
| model | VARCHAR | 模型名称 |
| weight | INT | 权重 (越大越优先) |
| enabled | INT | 1=启用, 0=禁用 |
| timeout_ms | INT | 超时 (ms) |
| remark | VARCHAR | 备注 |
| updated_by | VARCHAR | 最后修改人 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

### 9.2 种子数据 (seedFromEnv)

首次启动时，若 `ai_provider` 表中 `service='translate'` 无记录，自动从环境变量插入：

| 服务商 | 来源 | 模型 | 权重 |
|--------|------|------|------|
| 默认服务商 (env) | `AI_TRANSLATE_API_URL` + `AI_TRANSLATE_API_KEY` | `AI_TRANSLATE_MODEL` | 3 |
| OpenRouter | `OPENROUTER_API_KEY` | `openai/gpt-3.5-turbo` | 2 |

### 9.3 加权轮询算法

```java
// AiProviderServiceImpl.pick(String service)
// 1. 查询 enabled=1 的记录，按 weight 降序
// 2. 构建 weighted pool: 每个 provider 重复 weight 次
// 3. AtomicInteger 计数器取模轮询
AtomicInteger counter = counters.computeIfAbsent(service, k -> new AtomicInteger(0));
int idx = Math.abs(counter.getAndIncrement() % pool.size());
return pool.get(idx);
```

### 9.4 连通性测试

`AiProviderServiceImpl.test(Long id, String testText)` 发送 OpenAI 兼容请求测试连通性：
- 自动追加 `/v1/chat/completions` (如果 URL 中缺失)
- 检测 HTML 响应 (配置错误的 URL)
- 返回 `{success, output, latencyMs, error}`

---

## 10. 指标监控 (AiCallMetricsService)

### 10.1 跟踪的服务

| 服务名常量 | 对应实现 |
|-----------|---------|
| `HIAGENT` | HiAgentServiceImpl |
| `OPENROUTER` | OpenRouterServiceImpl |
| `TENCENT_TRANSLATE` | TencentTranslateService |
| `TENCENT_ASR` | TencentAsrService |
| `BILIBILI` | Bilibili 相关 |

### 10.2 Redis 计数器模式

```
ai:today:{SERVICE}:count:{yyyyMMdd}       → 总调用次数
ai:today:{SERVICE}:success:{yyyyMMdd}     → 成功次数
ai:today:{SERVICE}:total_time:{yyyyMMdd}  → 总响应时间 (ms)
```

TTL: 2 天

### 10.3 日志批量写入

- 内存缓冲: `ConcurrentLinkedQueue<AiCallLog>` (最大 500 条)
- 定时刷新: 每 10 秒 (`@Scheduled(fixedRate = 10000)`)
- 批量插入: 每批最多 200 条
- 溢出策略: 缓冲区满时丢弃最旧记录 (`buffer.poll()`)

### 10.4 今日统计 API

`getTodayStats()` 返回每个服务的:
- `totalCalls` / `successCalls` / `failedCalls`
- `successRate` (百分比)
- `avgResponseTimeMs` (平均响应时间)

---

## 附录: HTTP 连接池配置

所有 HTTP 客户端使用统一的连接池配置:

| 参数 | 值 | 说明 |
|------|---|------|
| maxTotal | 50 | 最大连接数 |
| defaultMaxPerRoute | 10 | 每路由最大连接数 |

**涉及的服务**:
- HiAgentServiceImpl
- OpenRouterServiceImpl
- TencentTranslateService
- AiProviderServiceImpl.test()
