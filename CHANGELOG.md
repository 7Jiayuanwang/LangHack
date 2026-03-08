# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

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
