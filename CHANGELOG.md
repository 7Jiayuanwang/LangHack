# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [0.5.0] — 2026-04-23

### Added — 视频学习系统深度优化 (Video Learning UI/UX)
- **顶级收藏中心**: 在视频播放页顶部 Navbar 新增五角星收藏按钮，采用透明极简风格，支持全局悬浮显示。
- **Artplayer 深度定制**: 
  - 在原生播放器控制栏内集成“字幕开关（Subtitle Toggle）”自定义插件。
  - 实现字幕列表样式与播放状态的联动高亮显示。
- **互动显示切换**: 在字幕文稿区标题行右侧集成“字幕显示 / 文章显示”模式切换。

### Fixed — 移动端适配与交互逻辑
- **收藏逻辑重构**: 废弃原本的视频点赞逻辑，统一接入 `article_favorites` 收藏接口。视频收藏状态现在与“我的学习库”完全同步。
- **移动端对齐修复**: 
  - 使用新的响应式 Flex 布局确保“显示模式切换”按钮在窄屏下不掉行，保持与标题并排。
  - 优化移动端头部 Navbar 排版，返回按钮、标题与收藏按钮实现完美的空间分配。
- **全局浮窗对齐**: 修复划词查单词、AI 问答弹窗在不同尺寸设备上的偏移，通过全局 CSS 锁定 `absolute` 50% 数学中心点位置。
- **元数据文案**: 将所有“赞”相关的界面文本统一修正为更符合学习场景的“收藏”。

## [Unreleased]

### Security — 接口安全全面加固 (2026-06-17)

#### 标签白名单校验
- **`ContentTagService.filterValidTags()`**：新增服务层方法，对照 `content_tags` 表过滤非法标签；白名单 case-insensitive 比较
- **`UserVideoController`**：upload / update 接口对 tags 做白名单校验 + 必填校验，空标签或全非法标签返回 400
- **`UserTextController`**：同上，upload / update 两个入口均已加固

#### 翻译接口安全
- **Prompt Injection 防护**：`/api/translate/custom` 的 `source`/`target` 语言代码改为白名单验证（仅 `en`/`zh`/`auto`），非法值返回 400 而非拼入 AI prompt
- **批量翻译 direction 白名单**：`/api/translate/batch` 的 `direction` 字段仅接受 `en-to-zh` / `zh-to-en`

#### 媒体文件访问控制
- **`MediaController` 下载接口**：对 VIDEO 类型媒体文件，检查关联 Video 的审核状态；未审核通过或未公开的视频内容，非 owner/ADMIN 返回 403，防止绕过视频审核直接下载原始文件

#### 输入长度限制
- **`AiQaController`**：`/chat` 接口新增输入长度上限（question ≤500字、selectedText ≤2000字、articleContext ≤5000字），防止构造超大 prompt 消耗 AI 额度
- **`AnnotationController`**：创建批注时限制 selectedText ≤2000字、note ≤1000字
- **`UnderlineController`**：创建划线时限制 selectedText ≤2000字、serializedRange ≤5000字

### Added — 翻译长文本分块处理 (2026-06-17)
- **`TencentTranslateService.translateLong()`**：新增长文本翻译方法，按中英文句末标点自动切句分块（块大小 1500 字），每块独立翻译后拼接，单句超限时强制按字符截断
- **`TencentTranslateService.splitIntoChunks()`**：静态工具方法，可独立测试切块逻辑
- **`/api/translate/en-to-cn`、`/cn-to-en`、`/custom`**：单次翻译上限从 2000 字（硬拒绝）改为 10000 字（自动分块处理），不再向调用方抛出长度错误

### Fixed — 字幕 HTML 实体解码 (2026-06-17)
- **`VideoPlayer.vue` `parseVTT()`**：字幕文本提取后增加 HTML 实体反转义（`&gt;` → `>`、`&lt;` → `<`、`&amp;` → `&` 等），修复 VTT 说话人标志 `>>` 在字幕文稿中显示为 `&gt;&gt;` 的问题

### Added — SM-2 高级特性 (2026-06-06)
- **困难词加强模式**：新增 `hardWordMode` 选项，将 EF 下限从 1.3 降至 1.0，使极度困难单词的复习间隔更短
  - 实现文件：`frontend/src/utils/sm2.js`（新增可选 `options.hardWordMode` 参数）
  - 测试文件：`frontend/tests/utils/sm2-hard-word-mode.spec.js`（14 个测试用例）
  - 适用场景：专业术语、形近词、反复遗忘的"老大难"单词
- **过载保护系统**：自动分批到期单词，避免学习负担过重
  - 核心文件：`frontend/src/utils/sm2-overload-protection.js`（5 个导出函数）
  - 功能包括：优先级计算、智能分批、延期调度、提示信息生成
  - 测试文件：`frontend/tests/utils/sm2-overload-protection.spec.js`（18 个测试用例）
  - 默认配置：单日最大 50 个，紧急阈值 75 个（超过后降至 40 个）
- **数据监控与分析**：7 大监控指标 + 智能建议系统
  - 核心文件：`frontend/src/utils/sm2-analytics.js`（8 个导出函数）
  - 功能包括：EF 分布、间隔分布、记忆保持率、学习报告、CSV 导出
  - 测试文件：`frontend/tests/utils/sm2-analytics.spec.js`（18 个测试用例）
  - 自动生成 4 类建议：困难词预警、遗忘风险、过载保护、学习良好

**测试覆盖**：新增 50 个测试用例，全部通过。总测试量从 1241 增至 1291 条。

### Fixed — 间隔复习算法统一 (2026-06-06)
- **废弃 ebbinghaus.js**：删除了艾宾浩斯固定间隔实现（`frontend/src/features/VocabularyFeatures/ebbinghaus.js`），统一使用标准 SM-2 算法
- **WordRecite.vue 算法迁移**：将单词背诵页面的复习算法从 `calculateEbbinghaus`（分钟制固定间隔）切换到 `calculateSM2`（天制动态间隔）
- **数据迁移守卫**：自动检测并重置 localStorage 中遗留的分钟制间隔数据（`interval > 365`），避免"数年后复习"的荒谬结果
- **注释修正**：修复 `VocabularyBooksServiceImpl.java:227` 中的错误注释（"艾宾浩斯遗忘曲线调度" → "SM-2 调度"）
- **文档补充**：新增 `docs/SM2_ALGORITHM_GUIDE.md` 详细说明 SM-2 算法原理、与艾宾浩斯的区别、实现位置、高级特性和使用指南

**背景**：早期代码存在两套并行实现（sm2.js 和 ebbinghaus.js），主路径（Vocabulary.vue）已使用 SM-2，但 WordRecite.vue 仍在使用固定间隔艾宾浩斯算法，且两者间隔单位不兼容（分钟 vs 天）。此次统一消除了算法不一致隐患，对齐主流学习应用标准（Anki、Quizlet、Duolingo 均采用 SM-2 或其变体）。

## [0.4.0] — 2026-03-02

### Fixed — 跨平台兼容性
- **WebMvcConfig**: 静态资源路径改用 `Paths.resolve().toUri()`，消除 Windows 反斜杠与 URL 斜杠混用导致音频资源加载失败的问题
- **ECDICTService / ECDICTImporter**: `FileReader` 改为 `InputStreamReader + StandardCharsets.UTF_8`，修复 Windows 默认编码(GBK)下 ECDICT 中文释义乱码
- **start_backend.sh**: 移除 macOS Homebrew 硬编码 JAVA_HOME，改为 macOS / Linux 多平台自动检测
- **import-ecdict.sh**: 移除硬编码数据库密码 `-pDao030916`，改从 `DB_PASSWORD` 环境变量或 `.env` 文件读取
- **admin/Layout + teacher/Layout**: `userStore.userInfo.realName` 改为 `userStore.userInfo?.realName`，修复未登录状态下的 JS 运行时崩溃

### Added — 移动端 / PC 端适配
- **index.html**: viewport 新增 `viewport-fit=cover`，使 iOS 刘海屏 / Home 键安全区 `env(safe-area-inset-*)` 正式生效
- **main.js**: 导入此前被遗漏的 `mobile.scss`，激活全部 Element Plus 移动端触控 / 字号 / 弹窗优化规则
- **App.vue**: 补全 5 个缺失 CSS 变量（`--surface-color`、`--border-medium`、`--shadow-light`、`--gradient-accent`、`--gradient-secondary`），消除 Admin / Teacher 布局的样式引用错误
- **el-table 横向滚动（20 处）**: admin / teacher / student 全部数据表格包裹 `overflow-x: auto` 容器；`admin/Texts.vue`、`admin/Words.vue` 的 `overflow: hidden` 改为可滚动
- **el-dialog 自适应宽度（21 处）**: 固定像素弹窗（如 `width="800px"`）改为 `min(Npx, 90%)` / `min(Npx, 96vw)`，在各尺寸屏幕上均不溢出
- **响应式网格（4 处）**: `admin/Dashboard`、`teacher/Dashboard`、`teacher/Courses` 统计卡片从 `:span="6"` 改为 `:xs="12" :sm="12" :md="6"`；`student/UserTexts` 筛选栏改三段式断点
- **word-break**: 所有展示英语内容的容器补充 `word-break: break-word`，防止长单词撑破布局
- **触摸目标**: `AiQa` 输入框 `font-size` 15→16px（防 iOS Safari 缩放）；Articles / Dashboard 操作按钮 36→44px
- **SelectionMenu**: 触摸目标 44px + `touchend` 事件 + 视口边缘溢出钳制 + `flex-wrap`
- **ExerciseAnswer / ExerciseBuilder**: 选项 `min-height: 44px` + 工具栏 `flex-wrap` + 移动端媒体查询
- **MarkdownEditor**: preview `overflow-x: auto`、宽表格横向滚动、代码块 `-webkit-overflow-scrolling: touch`

### Improved — 部署与网络
- **nginx.conf**: 新增 gzip 压缩（11 种 MIME 类型）；`index.html` no-cache 策略；`/assets/` `max-age=31536000, immutable` 长缓存；补全 `X-Content-Type-Options / X-Frame-Options / X-XSS-Protection` 安全响应头；`font/woff2` RFC 8081 MIME 类型；AI 代理 `proxy_buffering off + proxy_read_timeout 120s`
- **request.js**: HTTP 超时 10s → 15s；超时 / 断网场景输出中文友好提示
- **.gitattributes**: 新增 `* text=auto eol=lf` 行尾规范化，防止跨平台 CRLF / LF 混用

---

## [0.3.0] — 2026-03-02

### Fixed — 安全与稳定性
- 全面修复所有 Controller 的 `getUserByUsername().getId()` NPE 链式调用，统一封装为 `getCurrentUserId()` 辅助方法
- `StudentWordController.batchOperateVocabulary`: `(List<Long>) jsonArray` 强转改为 stream 安全转换，修复 ClassCastException
- `AssignmentController.createAssignmentWithContent`: 同上，修复 List 强转问题
- `ClassController`: 角色前缀比较统一（`ROLE_ADMIN` vs DB 原始 `ADMIN`）；`teacherId` null 安全比较
- `application.yml`: 修复 `datasource / security / flyway / devtools` 缩进错误（原位于 `media:` 下）
- 移除 `AiQa.vue` 未使用的 `Loading`、`useRouter` 导入；移除 `router/index.js` 重复测试路由；移除 `vite.config.js` 引用不存在 `variables.scss` 的配置

---

## [0.2.0] — 2026-02-xx

### Added
- AI 问答后台接入（OpenRouter + `AiQa.vue`）
- WordNet 词典服务集成

---

## [0.1.0] — 2026-01-xx

### Added
- 初始版本：Spring Boot 后端 + Vue 3 前端基础架构
- JWT 多角色认证（管理员 / 教师 / 学生）
- 课程 → 单元 → 课文层级管理
- ECDICT 74 万词词典导入工具
- Docker Compose 一键部署
