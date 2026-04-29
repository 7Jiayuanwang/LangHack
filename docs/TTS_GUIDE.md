# LangHack TTS 语音合成功能 — 技术文档

> 最后更新：2026-04-29

---

## 一、方案选型过程

### 1.1 需求背景

LangHack 平台需要为英语学习者提供文本朗读功能，包含三个场景：
- **全文朗读**：整篇课文的英语发音
- **划词朗读**：选中任意文本段落即时朗读
- **翻译播放**：翻译弹窗中原文/译文的朗读

关键约束：
- 服务器**无显卡**（纯 CPU 环境）
- 高峰期 **50~100 人**同时使用
- 尽可能**低成本或免费**

### 1.2 备选方案对比

| 方案 | 费用 | 显卡 | 并发 | 音质 | 部署难度 | 结论 |
|------|------|------|------|------|---------|------|
| **本地 ChatTTS**（`/projects/TTS/`） | 免费 | ⚠️ 无显卡极慢（30~60s/段） | ❌ 串行锁，排队严重 | 中文好，英文一般 | 需装 PyTorch 2GB+ | ❌ 不适合 |
| **百度 TTS** | 注册送 10 万次，超出 ¥1.2/千次 | 不需要 | ✅ | 一般 | 需单独注册百度云 | 🟡 可选 |
| **魔搭社区 ModelScope** | 每日 2000 次免费 | 不需要 | ⚠️ 无 SLA 保障 | 好 | 需额外注册 | 🟡 可选 |
| **硅基流动 SiliconFlow** ⭐ | 注册送 ¥14 额度 | 不需要 | ✅ | 很好（CosyVoice2） | **零额外部署** | ✅ **最终选择** |
| **浏览器 Web Speech API** | 完全免费 | 不需要 | ✅ | 差，浏览器差异大 | 零部署 | ❌ 体验差 |

### 1.3 为什么选硅基流动？

1. **项目已在使用**：LangHack 的 AI 翻译功能已接入硅基流动（`api.siliconflow.cn`），API Key 现成可用
2. **OpenAI 兼容接口**：`POST /v1/audio/speech`，Java RestTemplate 几行代码搞定
3. **CosyVoice2 模型**：阿里开源的中英文 TTS，音质优秀
4. **零额外注册**：复用现有账号和 Key
5. **成本极低**：配合缓存策略，500 篇课文约 ¥7，赠送额度绑绑有余

---

## 二、硅基流动 TTS 使用教程

### 2.1 API 核心信息

| 项目 | 值 |
|------|---|
| 接口地址 | `https://api.siliconflow.cn/v1/audio/speech` |
| 鉴权方式 | `Authorization: Bearer {API Key}` |
| 推荐模型 | `FunAudioLLM/CosyVoice2-0.5B` |
| 返回格式 | 直接返回 **MP3 二进制数据**（不是 JSON） |
| 计费方式 | 按 UTF-8 字节数，约 ¥7.15/百万字节 |

### 2.2 内置发音人

| 发音人 | 代码 | 性别 | 风格 |
|--------|------|------|------|
| Alex | `FunAudioLLM/CosyVoice2-0.5B:alex` | 男 | 沉稳 |
| Benjamin | `...:benjamin` | 男 | 低沉 |
| Charles | `...:charles` | 男 | 磁性 |
| David | `...:david` | 男 | 开朗 |
| Anna | `...:anna` | 女 | 沉稳 |
| Bella | `...:bella` | 女 | 热情 |
| Claire | `...:claire` | 女 | 温柔 |
| Diana | `...:diana` | 女 | 开朗 |

### 2.3 curl 快速测试

```bash
curl -X POST https://api.siliconflow.cn/v1/audio/speech \
  -H "Authorization: Bearer 你的API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "FunAudioLLM/CosyVoice2-0.5B",
    "input": "Hello, welcome to LangHack. This is a test.",
    "voice": "FunAudioLLM/CosyVoice2-0.5B:alex",
    "response_format": "mp3",
    "speed": 1.0
  }' \
  --output test.mp3
```

### 2.4 API 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `model` | string | ✅ | `FunAudioLLM/CosyVoice2-0.5B` |
| `input` | string | ✅ | 要合成的文本（最大 128,000 字符） |
| `voice` | string | ✅ | 发音人代码（见上表） |
| `response_format` | string | 否 | `mp3`(默认) / `wav` / `opus` / `pcm` |
| `speed` | float | 否 | 语速 0.25 ~ 4.0，默认 1.0 |
| `sample_rate` | int | 否 | 采样率，MP3 支持 32000/44100 |
| `gain` | float | 否 | 音量增益 -10 ~ 10 |

### 2.5 Java 后端调用示例

```java
Map<String, Object> body = new HashMap<>();
body.put("model", "FunAudioLLM/CosyVoice2-0.5B");
body.put("input", "要合成的文本");
body.put("voice", "FunAudioLLM/CosyVoice2-0.5B:alex");
body.put("response_format", "mp3");

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.setBearerAuth(apiKey);

ResponseEntity<byte[]> response = restTemplate.exchange(
    "https://api.siliconflow.cn/v1/audio/speech",
    HttpMethod.POST,
    new HttpEntity<>(body, headers),
    byte[].class
);

byte[] audioData = response.getBody();
Files.write(Path.of("output.mp3"), audioData);
```

---

## 三、LangHack 集成架构

### 3.1 调用流程

```
用户点击朗读
    │
    ▼
前端 Vue ──POST──▶ /api/tts/synthesize { text }
                         │
                         ▼
                  后端 TtsController
                         │
                    ┌─────┴─────┐
                    │  MD5 缓存  │
                    │  命中？    │
                    └─────┬─────┘
                     是 /    \ 否
                      /      \
              返回缓存    调用硅基流动 API
              文件路径    ──POST──▶ /v1/audio/speech
                  │           │
                  │      保存到 /app/uploads/tts/{hash}.mp3
                  │           │
                  ◀───────────┘
                  │
                  ▼
          返回 { audioUrl: "/api/tts/audio/{hash}.mp3" }
                  │
                  ▼
          前端 new Audio(audioUrl).play()
```

### 3.2 文件结构

```
backend/
├── src/main/java/com/english/
│   ├── service/TtsService.java        # 核心服务：API 调用、缓存、并发去重
│   ├── controller/TtsController.java  # REST 接口 + 限流 + 安全校验
│   └── config/SecurityConfig.java     # 添加 TTS 端点权限规则
├── src/main/resources/
│   └── application.yml                # tts: 配置块
│
frontend/
├── src/views/student/InteractiveText.vue   # 全文朗读按钮 + 翻译播放 + 选中朗读
├── src/components/InteractiveText.vue      # 翻译弹窗播放按钮
└── src/components/SelectionMenu.vue        # "朗读"菜单项
│
docker/
├── .env                               # TTS_API_KEY
└── docker-compose.yml                  # 传递 TTS_API_KEY 到 backend
```

### 3.3 三个功能入口

| 功能 | 位置 | 触发方式 |
|------|------|---------|
| 全文朗读 | 页面标题栏（收藏按钮旁） | 点击 🎧 耳机按钮 |
| 划词朗读 | 选中文本后弹出菜单 | 点击「朗读」选项 |
| 翻译播放 | 翻译弹窗中原文/译文旁 | 点击 🎧 小耳机按钮 |

---

## 四、潜在问题与解决方案

### 4.1 并发问题（已解决 ✅）

**问题**：50~100 人同时请求同一篇课文的朗读

**解决方案**：

| 机制 | 说明 |
|------|------|
| 文件缓存 | `MD5(text + voice + speed)` → `/app/uploads/tts/{hash}.mp3`，一次生成永久复用 |
| 并发去重 | `ConcurrentHashMap<hash, CompletableFuture>` — 100 人同时请求同一文本，只触发 1 次 API |
| HTTP 连接池 | `PoolingHttpClientConnectionManager`，最大 20 连接 |
| 用户限流 | 每用户每分钟最多 30 次请求 |

### 4.2 API 额度与费用（已解决 ✅）

**问题**：硅基流动注册赠送 ~¥14 额度，用完需充值

**解决方案**：
- 缓存策略下同一文本只调用 1 次 API
- 500 篇课文 × 平均 2000 字符 ≈ 100 万字符 ≈ **¥7**
- 赠送额度足够覆盖，即使超出也极低

**建议**：定期在 [硅基流动控制台](https://cloud.siliconflow.cn) 查看用量

### 4.3 长文本限制（部分解决 ⚠️）

**问题**：极长课文可能超过合成限制

**当前方案**：
- 前端单次请求限制 **5000 字符**
- 全文朗读时先清理 HTML/Markdown 标记

**未来优化**：
- 后端自动分段合成 → 拼接 MP3
- 或前端分段顺序播放

### 4.4 网络故障（已解决 ✅）

**问题**：硅基流动 API 不可用时 TTS 功能中断

**解决方案**：
- 60 秒超时控制，防止线程阻塞
- 前端友好错误提示
- 已缓存的音频在 API 故障时仍可正常播放（缓存降级）

**未来优化**：添加 Resilience4j 熔断器

### 4.5 磁盘空间（需关注 ⚠️）

**问题**：缓存文件持续增长

**当前方案**：缓存目录通过 Docker volume 挂载到宿主机 `uploads/tts/`

**估算**：1 万个文件 × 平均 200KB ≈ **2GB**

**未来优化**：添加定时清理脚本，删除 N 天未访问的缓存

### 4.6 安全问题（已解决 ✅）

**问题**：TTS 接口可能被滥用

| 防护机制 | 说明 |
|---------|------|
| 认证保护 | 合成接口需登录（`authenticated()`），音频文件公开访问 |
| 用户限流 | 每用户每分钟 30 次上限 |
| 文件名校验 | 只允许 `[a-f0-9]+.mp3` 格式 |
| 路径遍历防护 | `toRealPath()` + `startsWith()` 校验 |
| 文本长度限制 | 单次最大 5000 字符 |

### 4.7 音质与发音人（可优化 🟡）

**当前方案**：默认使用 `alex`（沉稳男声），可通过环境变量 `TTS_VOICE` 全局切换

**未来优化**：
- 前端提供发音人选择器（后端已有 `GET /api/tts/voices` 接口）
- 根据文本语言自动切换发音人

---

## 五、本地 TTS 项目为何不适合

项目目录 `/projects/TTS/` 包含一个基于 ChatTTS 的本地 TTS 服务。

### 不适合的原因

| 问题 | 详情 |
|------|------|
| **需要显卡** | ChatTTS 在纯 CPU 上合成 100 字需 30~60 秒 |
| **串行处理** | 代码使用 `asyncio.Lock()` 串行化，一次只能处理 1 个请求 |
| **并发灾难** | 50 人同时用 = 第 50 人等 25 分钟 |
| **部署重** | 需安装 Python + PyTorch (~2GB) + 模型 (~400MB) |
| **英文音质一般** | ChatTTS 主要针对中文优化 |

### 何时可以考虑本地部署

- 有了**带 NVIDIA 显卡的服务器**
- 可以自部署 **CosyVoice2 模型**（同硅基流动使用的模型）
- 实现完全离线、零成本运行

---

## 六、环境配置

### 6.1 必需环境变量

```bash
# docker/.env 中添加
TTS_API_KEY=sk-your-siliconflow-api-key
```

### 6.2 可选环境变量

```bash
TTS_API_URL=https://api.siliconflow.cn/v1/audio/speech  # API 地址
TTS_MODEL=FunAudioLLM/CosyVoice2-0.5B                   # 模型
TTS_VOICE=FunAudioLLM/CosyVoice2-0.5B:alex              # 默认发音人
TTS_CACHE_DIR=/app/uploads/tts                            # 缓存目录
TTS_TIMEOUT=60000                                         # 超时(毫秒)
```

### 6.3 docker-compose.yml

```yaml
backend:
  environment:
    TTS_API_KEY: ${TTS_API_KEY:-${AI_TRANSLATE_API_KEY:-}}
```

> 如果未设置 `TTS_API_KEY`，会自动回退到 `AI_TRANSLATE_API_KEY`（翻译用的 Key）。

---

## 七、风险矩阵总览

| 风险 | 严重程度 | 状态 | 说明 |
|------|:-------:|:----:|------|
| 并发打满 API | 🔴 高 | ✅ 已解决 | 缓存 + 去重 + 限流 |
| API 额度耗尽 | 🟡 中 | ✅ 已解决 | 缓存极大降低用量 |
| 安全攻击 | 🔴 高 | ✅ 已解决 | 认证 + 限流 + 路径校验 |
| 网络故障 | 🟡 中 | ✅ 已解决 | 缓存降级 + 超时控制 |
| 长文超限 | 🟡 中 | ⚠️ 部分 | 当前限 5000 字，可分段 |
| 磁盘增长 | 🟢 低 | ⚠️ 待做 | 后续加清理脚本 |
| 音质不满意 | 🟢 低 | ⚠️ 待做 | 后续加发音人选择 |
