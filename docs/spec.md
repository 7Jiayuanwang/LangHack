# 项目功能规格说明

> **状态说明**：所有核心功能均已实现。本文档记录各模块的技术规格，供开发参考。
> 新的开发任务请参阅根目录 `TODO.md`，AI 工作上下文请参阅根目录 `CLAUDE.md`。

---

## 已实现功能清单

### 用户与权限系统
- [x] 五种角色：ADMIN / TEACHER / STUDENT / USER / OPS
- [x] JWT 认证，`Authorization: Bearer <token>`
- [x] 路由级权限守卫（前端 Vue Router + 后端 @PreAuthorize）
- [x] OPS 角色提权机制（ElevationDialog）

### 课程管理
- [x] 三级层级结构：课程 → 单元 → 课文
- [x] Markdown 格式课文，交互式阅读
- [x] 管理员创建/编辑，教师浏览，学生学习
- [x] 课文版本历史（Article Version System）

### 任务系统
- [x] 教师发布任务（指定班级、截止日期、关联课文）
- [x] 学生查看并完成任务
- [x] 任务完成率统计（教师视角）
- [x] 详见 `docs/ASSIGNMENT_FEATURES.md`

### 学习记录
- [x] 手动完成阅读任务（替代自动 10 分钟计时器）
- [x] 时长记录存入 `text_learning_records`（student_id, text_id, start_time, end_time）
- [x] 多维度学习统计（学习时长、词汇掌握、任务完成率）

### 生词本与间隔复习（SM-2）
- [x] SM-2 算法实现：`frontend/src/utils/sm2.js`（标准 SuperMemo-2 算法，2026-06-06 统一废弃旧 ebbinghaus.js）
- [x] 参数存储：`repetition_count`, `easiness_factor`, `next_review_date`（后端 `vocabulary_books` 表）
- [x] Quality (Q) 评分 0–5，Interval 指数增长（1天 → 6天 → 15天 → 37天...）
- [x] 个性化难度因子 (EF 1.3-5.0)，根据实际表现动态调整间隔
- [x] 详细说明见 [`docs/SM2_ALGORITHM_GUIDE.md`](SM2_ALGORITHM_GUIDE.md)

### 词典系统
- [x] 三源查词：本地 DB → ECDICT → WordNet（JWI）
- [x] 查词时自动持久化（`wordNetService.getOrCreateWord`）
- [x] 弹窗显示：音标、发音、中文释义、英文释义、同义词/反义词
- [x] "加入生词本"按钮

### 交互式阅读与协作标注
- [x] 文本选中弹出菜单（复制 / 划线 / AI 问答）
- [x] 划线标注：学生标注对同课文所有读者可见（`shared_underlines` 表）
- [x] AI 问答：选中文本后发送给 AI，预加载上下文

### AI 助教
- [x] 基于 OpenRouter（DeepSeek 等模型）
- [x] 流式响应（Server-Sent Events）
- [x] 上下文感知：携带选中文本和课文片段
- [x] 自动版本化（OpenRouter DeepSeek Auto-Versioning）

### 班级系统
- [x] 教师创建班级，学生加入班级
- [x] 任务按班级分配
- [x] 班级成员管理

### 运维面板（OPS 角色）
- [x] 系统监控、数据备份、AI 服务管理
- [x] Feature Toggle 管理（控制学生端导航项显示）
- [x] 部署管理（零停机热部署 — Deployer Sidecar 蓝绿部署）

### 移动端布局
- [x] 所有角色均使用移动端底部导航栏设计
- [x] 共享布局组件：`src/views/shared/MobileLayout.vue`（role prop: admin/teacher/user/ops）
- [x] 学生端独立布局：`src/views/student/Layout.vue`（含 feature toggle 过滤）

---

## 数据库关键表

| 表名 | 说明 |
|------|------|
| `users` | 用户（含角色字段） |
| `courses` | 课程 |
| `units` | 单元 |
| `texts` | 课文（Markdown 内容） |
| `text_versions` | 课文版本历史 |
| `assignments` | 任务 |
| `assignment_submissions` | 任务提交 |
| `classes` | 班级 |
| `class_members` | 班级成员 |
| `vocabulary_words` | 用户生词本 |
| `text_learning_records` | 阅读记录 |
| `shared_underlines` | 协作划线 |
| `media` | 媒体文件（BLOB） |
| `feature_toggles` | 功能开关 |

所有 schema 变更通过 Flyway 脚本管理：
`backend/src/main/resources/db/migration/V{n}__{description}.sql`

---

## API 端点规范

- `GET/POST /api/public/**` — 无需认证
- `/api/admin/**` — 需要 ADMIN 角色
- `/api/teacher/**` — 需要 TEACHER 角色
- `/api/student/**` — 需要 STUDENT 或 USER 角色
- 统一响应格式：`{ "code": 200, "data": ..., "message": "..." }`

详细 API 文档见 `docs/API接口文档.md`。
