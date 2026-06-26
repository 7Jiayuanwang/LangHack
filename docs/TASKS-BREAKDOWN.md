# 高优先级任务分解（团队协作）

> **目的**：把 `TODO.md` 中所有「优先级：高 / 最高」+ P1 任务拆成可独立分配的子任务，便于团队并行开发。
> **来源**：`TODO.md` 行 165–662
> **更新规则**：每完成一个子任务，把对应 `状态` 从 ⬜ 改为 ✅；新增任务请同步更新「任务总览」表。

---

## 一、协作约定

### 任务编号
```
{模块字母}-{主任务序号}.{子任务序号}
例：A-1.1 = 模块 A（P0 数据指标）/ 主任务 1（用户活跃度）/ 子任务 1（后端 API）
```

### 状态符号
| 符号 | 含义 |
|------|------|
| ⬜ | 待领取 |
| 🚧 | 进行中（请同步在表中写入负责人） |
| ✅ | 已完成 |
| ⛔ | 阻塞中（请在备注列写明阻塞原因） |

### 角色标签
| 标签 | 技能要求 |
|------|---------|
| `后端` | Java 17 + Spring Boot 2.7 + MyBatis Plus + Flyway |
| `前端` | Vue 3 + Element Plus + ECharts + Pinia |
| `DBA` | MySQL 8.0 + Flyway 迁移 |
| `DevOps` | Docker Compose + Nginx + Prometheus/Grafana |
| `QA` | Vitest + JUnit + Postman |

### 估时单位
- `0.1d` ≈ 1 小时
- `0.5d` ≈ 半个工作日
- `1d` = 一个工作日（按 8 小时）

### 依赖标记
- `↑ X-Y.Z` 表示需要 X-Y.Z 先完成
- `‖` 表示可与同组其他任务并行
- `无` 表示无外部依赖，可立即开工

---

## 二、任务总览

| 模块 | 主任务数 | 子任务数 | 总估时 | 关键路径 | 推荐人数 |
|------|---------|---------|--------|---------|---------|
| A. P0 数据指标可视化 | 5 | 18 | 4.5d | A-5.1 → A-5.2 → A-5.4 | 后端×1 + 前端×1 |
| B. P1 数据指标可视化 | 5 | 30 | 11d | B-1.3 → B-1.4 → B-1.8 | 后端×2 + 前端×2 |
| C. 全栈零停机热更新 | 5 | 13 | 5d | C-2.1 → C-4.1 → C-5.1 | DevOps×1 + 后端×1 |
| D. 性能压测基线 | 3 | 9 | 2.5d | D-1 → D-2 → D-3 | DevOps×1 |
| E. 运维监控体系 | 4 | 13 | 4.5d | E-1.1 → E-1.5 → E-3.1 | DevOps×1 |
| **合计** | **22** | **83** | **27.5d** | — | **建议 6 人团队，~5 个工作日** |

### 推荐人员分工
| 角色 | 主要承担 | 备选承担 |
|------|---------|---------|
| 后端 Dev #1 | A-1/2/3/4 后端 + B-1 后端 | C-5.2 |
| 后端 Dev #2 | A-5 后端 + B-2/3/4 后端 + B-5 后端 | C-2 |
| 前端 Dev #1 | A-1/2/3/4 Dashboard 卡片 + B-4 详情页 | B-2 反馈组件 |
| 前端 Dev #2 | A-5 埋点 + B-1 对话 UI + B-5 标注 UI | C-5.1 |
| DevOps | C-1/3/4 + D + E-1/3 | E-2/4 |
| QA | B-1.11 + B-5.10 + D-2 | E-1.5 验证 |

---

## 三、模块 A：P0 数据指标可视化（4.5 天）

> **目标**：5 个 Dashboard 卡片 + 5 个 Admin API + 推荐埋点
> **完成定义**：管理员登录 Dashboard 后能看到所有 5 张卡片实时显示真实数据
> **共同前置**：所有 API 必须加 `@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")`、加 Redis 缓存（TTL 5 分钟）

### A-1 用户活跃度数据展示（0.8d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| A-1.1 后端 API | 后端 | 0.3d | `users`、`access_log` 表 | `UserActivityController` + `UserActivityService` + DTO | `GET /api/admin/metrics/user-activity` 返回 `{registered, mau, dau, classCount, collegeCount, dauTrend30d[]}` | 无 | ⬜ | — |
| A-1.2 前端卡片 | 前端 | 0.3d | A-1.1 API 响应结构 | `admin/Dashboard.vue` 新增 `<UserActivityCard>` + ECharts 折线图 | 卡片显示 4 数字 + 30 天 DAU 趋势图 | ↑ A-1.1 | ⬜ | — |
| A-1.3 测试数据脚本 | 后端 | 0.2d | `docs/DATA_CAPABILITY_ASSESSMENT.md` SQL | `backend/scripts/seed-access-log.sql` | 执行后 `access_log` 至少 30 天数据 | 无 | ⬜ | — |

**📝 任务说明**
- **A-1.1**：统计平台活跃指标。DAU 用 `SELECT COUNT(DISTINCT user_id) FROM access_log WHERE DATE(access_time)=CURDATE()`；MAU 用最近 30 天。注意 collegeCount 暂时返回 0（待 B-3.4 完成后接入真实统计）。务必加 Redis `@Cacheable(value="metrics:user-activity", ttl=300)`。
- **A-1.2**：在 `admin/Dashboard.vue` 顶部新增第一张卡片。使用项目已集成的 ECharts；趋势图配色参考其他 admin 页面（蓝/绿主色）。移动端响应式（参考 `Layout` 的断点）。
- **A-1.3**：用 Python 或纯 SQL 生成模拟数据：30 天 × ~50 用户 × 每天 1-5 次访问。需符合现有 `access_log` 表结构（含 `user_id`、`access_time`、`endpoint`、`ip`）。脚本头部注释「仅用于测试，生产禁用」。

### A-2 学习行为数据展示（0.6d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| A-2.1 后端 API | 后端 | 0.3d | `learning_records`、`texts` 表 | `LearningBehaviorController` + Service + DTO | `GET /api/admin/metrics/learning-behavior` 返回 `{totalReads, totalDuration, avgDuration, hotArticlesTop10[], recordTypeDistribution[], dailyTrend[]}` | 无 | ⬜ | — |
| A-2.2 前端卡片 | 前端 | 0.3d | A-2.1 API | Dashboard `<LearningBehaviorCard>` + 饼图 + 柱状图 | 卡片显示 3 指标 + 类型饼图 + 趋势柱状图 | ↑ A-2.1 | ⬜ | — |

**📝 任务说明**
- **A-2.1**：`learning_records.record_type` 包含 `READ/LISTEN/WORD/EXERCISE` 等枚举（注意 A-5.1 会扩展）。`avgDuration = totalDuration / totalReads`。`hotArticlesTop10` JOIN `texts` 取 `title`+`view_count`。时间窗默认 30 天，URL 参数支持 `?days=N` 自定义。
- **A-2.2**：3 个指标用大号数字 + 单位（次/分钟），饼图用 record_type 分布，柱状图用最近 30 天每日记录数。注意 ECharts 容器需 `min-height:0` 否则 flex 内不可滚动（见 CLAUDE.md 陷阱表）。

### A-3 四六级成绩对比分析（0.9d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| A-3.1 后端 API | 后端 | 0.4d | `level_exam_scores` + `learning_records` | `CetComparisonController` + Service + SQL | `GET /api/admin/metrics/cet-comparison` 返回 `{platformUsersPassRate, nonPlatformPassRate, improvementPercent, scoreDistribution[]}` | 无 | ⬜ | — |
| A-3.2 前端卡片 | 前端 | 0.3d | A-3.1 API | `<CetComparisonCard>` + 双柱状图 + 进度条 | 通过率对比清晰、提升百分点高亮 | ↑ A-3.1 | ⬜ | — |
| A-3.3 数据补充 | 后端 | 0.2d | 学校数据同步接口 | 触发同步脚本 / mock 数据脚本 | `level_exam_scores` 至少 100 条记录 | 无 | ⬜ | — |

**📝 任务说明**
- **A-3.1**：核心 SQL 在 `docs/DATA_CAPABILITY_ASSESSMENT.md` 已有草稿。「使用平台」定义：在考试前 90 天内 `learning_records` 至少 10 条。通过线：CET4=425，CET6=425。**合规要求**：仅做汇总，**严禁返回个人成绩**。
- **A-3.2**：左右双柱状图（使用平台 vs 未使用平台），中间高亮 `+X%` 提升百分点。颜色：通过率绿色、未通过红色、提升幅度金色。
- **A-3.3**：优先用学校数据同步接口（`POST /api/admin/sync/level-exam-scores`）真实拉取；学校数据不可用时用 mock 脚本兜底，保证 Dashboard 不空。

### A-4 AI 调用统计展示（0.6d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| A-4.1 后端 API | 后端 | 0.3d | `ai_call_log` 表 | `AiUsageController` + Service + DTO | `GET /api/admin/metrics/ai-usage` 返回 `{totalCalls, todayCalls, successRate, avgResponseMs, byProvider[], dailyTrend[]}` | 无 | ⬜ | — |
| A-4.2 前端卡片 | 前端 | 0.3d | A-4.1 API | `<AiUsageCard>` + 折线图 + 饼图 | 4 指标 + 趋势折线 + 服务商饼图 | ↑ A-4.1 | ⬜ | — |

**📝 任务说明**
- **A-4.1**：`ai_call_log` 现有字段包含 `provider`、`status`、`response_ms`、`created_time`。`successRate = SUM(status='SUCCESS') / COUNT(*)`。`byProvider` 按 HIAGENT / OPENROUTER / SILICONFLOW / TENCENT_ASR 分组。响应时间用 AVG（非 P95，避免单查询过慢）。
- **A-4.2**：折线图横轴最近 30 天，饼图展示各服务商调用占比。`<AiUsageCard>` 预留底部插槽，后续 B-2.5 在此追加用户满意度区块（先约定接口）。

### A-5 推荐算法效果埋点（1.6d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| A-5.1 DB 迁移 | DBA | 0.2d | `learning_records.record_type` 现有枚举 | Flyway `V13__extend_record_type.sql` | 枚举新增 `RECOMMENDATION_VIEW`、`RECOMMENDATION_CLICK`、`RECOMMENDATION_REFRESH` | 无 | ⬜ | — |
| A-5.2 埋点接收 API | 后端 | 0.3d | A-5.1 枚举 | `POST /api/student/learning/record/recommendation` | 接收 `{type, articleId, position}` 写入 `learning_records` | ↑ A-5.1 | ⬜ | — |
| A-5.3 效果统计 API | 后端 | 0.3d | A-5.1 数据 | `GET /api/admin/metrics/recommendation-effectiveness` | 返回 `{ctr, refreshFreq, impressions, trend[]}` | ↑ A-5.1 | ⬜ | — |
| A-5.4 前端埋点 | 前端 | 0.3d | A-5.2 API + `src/api/learning.js` | `student/Articles.vue` 3 处调用：展示/点击/换一批 | 浏览器 Network 能观察到三种事件上报 | ↑ A-5.2 | ⬜ | — |
| A-5.5 前端卡片 | 前端 | 0.3d | A-5.3 API | Dashboard `<RecommendationCard>` + 折线图 | 显示 CTR、换一批频率、曝光数 | ↑ A-5.3 | ⬜ | — |
| A-5.6 数据补充 | QA | 0.2d | A-5.4 完成后 | 模拟点击脚本（Selenium / curl） | 30 天内至少 100 条埋点记录 | ↑ A-5.4 | ⬜ | — |

**📝 任务说明**
- **A-5.1**：注意先与 DBA 协调 Flyway 版本号（参考 `memory/feedback_flyway_version_collision.md`）。MySQL 8.0 修改 ENUM 用 `ALTER TABLE ... MODIFY COLUMN`，注意需要 `IF EXISTS` 包装防止重复执行报错。
- **A-5.2**：埋点接口需要**异步处理**避免阻塞用户。可用 `@Async` + 单独线程池（命名 `tracking-`）。`hasAnyRole('USER','STUDENT')` 权限，不要用 hasRole。
- **A-5.3**：`ctr = CLICK / VIEW`，`refreshFreq = REFRESH / 用户数`，`impressions = VIEW 总数`。注意分母为 0 的边界（直接返回 0.0 而非 NaN）。
- **A-5.4**：在 `student/Articles.vue` 三个位置埋点：① `onMounted` 推荐区可见时上报 VIEW；② 点击推荐文章时上报 CLICK；③ 点击"换一批"按钮时上报 REFRESH。避免重复上报（用 `Set` 记录已上报的 articleId）。
- **A-5.5**：与其他 4 张卡片排版统一，建议放在 Dashboard 第二行。
- **A-5.6**：用 curl 模拟 100 次 VIEW + 20 次 CLICK + 5 次 REFRESH，覆盖近 30 天每天 3-5 条事件，确保趋势图非空。

---

## 四、模块 B：P1 数据指标可视化（11 天）

> **目标**：AI 对话持久化 + 反馈 + 学院数据 + CET 详情页 + 协作标注

### B-1 AI 对话持久化与分享（5d，最大单项）

> **设计文档**：参见 `TODO.md` 行 221–348
> **建议工作流**：B-1.1 → (B-1.2 ‖ B-1.3 起步) → B-1.4 → B-1.5/B-1.6 → 前端

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| B-1.1 Flyway 迁移 | DBA | 0.3d | `TODO.md` 行 225–266 三张表 DDL | `V14__ai_conversation.sql` | 3 张表创建成功 + 索引齐全 + 外键约束 | 无 | ⬜ | — |
| B-1.2 Entity + Mapper | 后端 | 0.3d | B-1.1 表结构 | `AiConversation` / `AiMessage` / `ConversationShare` + 对应 Mapper | MP 注解齐全（含 `@TableLogic`、`@JsonProperty("is...")`） | ↑ B-1.1 | ⬜ | — |
| B-1.3 ConversationService | 后端 | 1d | B-1.2 | `ConversationService` 8 个方法（create/addMessage/get/list/share/validate/delete/listSharedView） | 单元测试覆盖核心场景 | ↑ B-1.2 | ⬜ | — |
| B-1.4 ConversationController | 后端 | 0.5d | B-1.3 | 6 个端点：POST/GET list/GET detail/DELETE/POST share/GET public-share | Postman 验证全部接口 + 权限控制 | ↑ B-1.3 | ⬜ | — |
| B-1.5 改造 AiQaController | 后端 | 0.3d | 现有 `AiQaController.chat()` + B-1.3 | `chat()` 新增 `conversationId` 参数，自动加载历史 + 持久化消息 | 多轮对话上下文连续；首次提问自动创建对话 | ↑ B-1.3 | ⬜ | — |
| B-1.6 分享 Token + 安全 | 后端 | 0.3d | `application.yml` `app.share-secret` | `ShareTokenUtil`（UUID + HMAC-SHA256） + BCrypt 密码 | Token 64 字符；密码错误返回 403；过期返回 410 | ‖ B-1.4 | ⬜ | — |
| B-1.7 前端 API 封装 | 前端 | 0.2d | B-1.4 端点 | `frontend/src/api/conversation.js` | 6 个方法导出，类型完备 | ↑ B-1.4 | ⬜ | — |
| B-1.8 历史侧边栏 | 前端 | 0.5d | B-1.7 | `AiQa.vue` 左侧可折叠侧边栏 | 列表显示标题 + 时间；点击切换对话；新建/删除按钮 | ↑ B-1.7 | ⬜ | — |
| B-1.9 分享对话框 | 前端 | 0.4d | B-1.7 | `<ShareDialog>` 组件 + 复制到剪贴板 | 7天/30天/永久选项 + 密码开关 + 二维码（可选） | ↑ B-1.7 | ⬜ | — |
| B-1.10 SharedConversation 页 | 前端 | 0.5d | B-1.4 公开端点 | `public/SharedConversation.vue` + 路由 | 只读视图 + 密码输入 + 过期提示 + 水印 | ↑ B-1.7 | ⬜ | — |
| B-1.11 测试 | QA | 0.5d | 上述全部 | Postman 集合 + Vitest 用例 | 对话创建/分享/权限/过期/并发 全部通过 | ↑ B-1.8/9/10 | ⬜ | — |

**📝 任务说明**
- **B-1.1**：三张表 DDL 完整定义见 `TODO.md:225-266`。注意 `ai_messages.role` 是 ENUM('user','assistant')，`conversation_shares.share_token` UNIQUE 索引必加。Flyway 版本号需与 DBA 协调（A-5.1 已占 V13，本任务建议 V14）。
- **B-1.2**：`AiConversation` 含软删除 `@TableLogic`；`AiMessage.role` 用 String 接收 ENUM；`ConversationShare.accessPassword` 在 Entity 层加 `@JsonIgnore` 防止泄漏。新增 Entity 同步写测试数据脚本以备后续测试使用。
- **B-1.3**：核心难点是「分享链接的权限边界」。`getConversationHistory` 需双重检查：① 是否所有者；② 若非所有者，是否通过有效的 share token 访问。建议拆为 `getByOwner()` 和 `getByShareToken()` 两个方法，避免在一处方法里做条件分支。
- **B-1.4**：6 个端点统一返回 `Result<T>`。`POST /share` 返回完整分享 URL（拼接 `app.base-url`），方便前端直接复制。注意 `GET /api/public/shared-conversations/{token}` 不要走 JWT 过滤，需在 `SecurityConfig` 加白名单。
- **B-1.5**：改造 `AiQaController.chat()` 时**保持向后兼容**——`conversationId` 是可选参数（null 时走原有逻辑，但应自动创建新对话并返回 ID）。历史消息加载注意限制条数（如最近 10 条），避免上下文过长触发模型 context limit。
- **B-1.6**：HMAC secret 配置 `app.share-secret`，从环境变量 `SHARE_SECRET` 读取（**不要硬编码**）。BCrypt cost 用 10（与现有 `passwordEncoder` 一致）。token 生成后立即 hash 入库，原值仅返回一次。
- **B-1.7**：6 个方法都用 axios，与现有 `src/api/student.js` 风格一致。`shareConversation` 返回值包含 `shareUrl` 和 `expiresAt`。
- **B-1.8**：侧边栏宽度 280px，移动端用抽屉式（参考 Element Plus `<el-drawer>`）。状态用 Pinia store（新建 `store/conversation.js`）。注意 Pinia getter 不能直接赋值（见 CLAUDE.md）。
- **B-1.9**：分享对话框含 3 个选项：过期时间（下拉 7/30/永久）、密码开关（toggle + 密码输入框）、二维码（可选，用 `qrcode` 库）。复制链接用 `navigator.clipboard.writeText`，失败降级 `document.execCommand('copy')`。
- **B-1.10**：路由 `/shared-conversation/:token` 必须**不需要登录**（在 `router/index.js` 加 `meta.public=true`）。页面顶部水印 "分享自 LangHack"，底部按钮 "在 LangHack 中打开" 跳转登录。密码错误显示 ElMessage 提示。
- **B-1.11**：Postman Collection 含 7 个场景：创建/列表/详情/删除/分享生成/分享访问/过期访问。Vitest 重点测前端组件状态切换。并发测试用 JMeter 模拟 50 人同时发消息，验证消息顺序不乱。

### B-2 AI 用户反馈（1.1d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| B-2.1 Flyway 迁移 | DBA | 0.1d | `TODO.md` 行 352–362 | `V15__ai_feedback.sql` | 表创建 + 外键 + 索引 | 无 | ⬜ | — |
| B-2.2 后端实现 | 后端 | 0.3d | B-2.1 | Entity + Service + `AiFeedbackController` | `POST /api/student/ai-qa/feedback` 接受评分/有帮助/文字 | ↑ B-2.1 | ⬜ | — |
| B-2.3 前端反馈组件 | 前端 | 0.3d | B-2.2 | `<AiFeedbackButtons>` 组件 | AI 回答后显示有/无帮助按钮 + 可选文字 | ↑ B-2.2 | ⬜ | — |
| B-2.4 Admin 汇总 API | 后端 | 0.2d | B-2.1 数据 | `GET /api/admin/metrics/ai-feedback-summary` | 返回 `{avgRating, helpfulRate, count}` | ↑ B-2.1 | ⬜ | — |
| B-2.5 Dashboard 集成 | 前端 | 0.2d | B-2.4 + A-4.2 | 在 `<AiUsageCard>` 内追加满意度区块 | 显示评分、有帮助率 | ↑ A-4.2 ‖ B-2.4 | ⬜ | — |

**📝 任务说明**
- **B-2.1**：`ai_feedback` 表 DDL 见 `TODO.md:352-362`。`call_log_id` 外键关联 `ai_call_log.id`（注意：要求 `ai_call_log` 已存在）。`rating` TINYINT 范围 1-5，业务层做边界校验。
- **B-2.2**：`POST /api/student/ai-qa/feedback` 接受 `{callLogId, rating, isHelpful, feedbackText}`。`@PreAuthorize("hasAnyRole('USER','STUDENT')")`。同一 callLogId + userId 只能反馈一次（UNIQUE 约束 + 数据库捕获 DuplicateKeyException 返回友好提示）。
- **B-2.3**：组件位置：每条 AI 回答消息下方一行小按钮：👍 / 👎 / ✍️。点击 👎 后展开文字输入框。提交后按钮变灰禁用。组件命名 `<AiFeedbackButtons :callLogId="..." />`。
- **B-2.4**：聚合查询：`SELECT AVG(rating), SUM(is_helpful)/COUNT(*) as helpful_rate, COUNT(*) FROM ai_feedback WHERE created_time >= ?`。时间窗默认 30 天。
- **B-2.5**：在 `<AiUsageCard>` 的预留底部插槽（A-4.2 中约定）添加 "用户满意度" 小节：星级显示 + 有帮助率进度条。

### B-3 学院数据支持（0.6d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| B-3.1 Flyway 迁移 | DBA | 0.1d | 现有 `classes` 表 | `V16__add_college_to_classes.sql` | `classes.college VARCHAR(100)` 加在 `name` 后 | 无 | ⬜ | — |
| B-3.2 后端调整 | 后端 | 0.2d | B-3.1 | `Class` 实体 + Service + Controller DTO | 班级创建/更新含 college；查询返回 college | ↑ B-3.1 | ⬜ | — |
| B-3.3 前端 UI | 前端 | 0.2d | B-3.2 API | `admin/Classes.vue` 增加学院输入/筛选 | 表格列 + 表单字段 + 学院列表选择 | ↑ B-3.2 | ⬜ | — |
| B-3.4 统计 API | 后端 | 0.1d | B-3.1 数据 | A-1.1 的 collegeCount 改为真实 SQL | `SELECT COUNT(DISTINCT college) FROM classes WHERE college IS NOT NULL` | ↑ B-3.1 + A-1.1 | ⬜ | — |

**📝 任务说明**
- **B-3.1**：`ALTER TABLE classes ADD COLUMN college VARCHAR(100) AFTER name`。注意 MySQL 8.0 无 `IF NOT EXISTS` 直接支持，需用存储过程包装（参考 `memory/MEMORY.md` 的 Docker/MySQL 章节）。
- **B-3.2**：`Class` 实体加 `@TableField("college")`，注意现有读写分离架构下创建/更新自动走 master（通过 `AdminMasterInterceptor`）。
- **B-3.3**：学院字段建议用 `<el-autocomplete>` 而非固定下拉（不同学校学院名不统一）。表格新增 college 列时，注意现有列宽分配（避免横向溢出）。
- **B-3.4**：注意只统计 `college IS NOT NULL AND college != ''`，避免空字符串污染。完成后回头更新 A-1.1 中 collegeCount 字段，将占位 0 替换为真实查询。

### B-4 四六级对比详情页（1d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| B-4.1 后端 API | 后端 | 0.4d | A-3.3 数据 | `GET /api/admin/metrics/cet-detailed` | 返回 `{scoreBuckets[], byExamType{CET4, CET6}, timeTrend[]}` | ↑ A-3.3 | ⬜ | — |
| B-4.2 前端独立页 | 前端 | 0.6d | B-4.1 | `admin/CetAnalysis.vue` + 路由 `/admin/cet-analysis` | 分数直方图 + 通过率趋势 + 学习时长×成绩散点图 | ↑ B-4.1 | ⬜ | — |

**📝 任务说明**
- **B-4.1**：`scoreBuckets` 按 50 分一档划分（300-350/350-400/...）。`byExamType` 分别返回 CET4 和 CET6 两组完整数据。`timeTrend` 按考试期次（如 2025-06、2025-12）排序。建议加 Redis 缓存（TTL 30 分钟）。
- **B-4.2**：页面布局：顶部筛选条（考试期次、考试类型），中部 3 张图（左：分数直方图；中：通过率趋势折线；右：学习时长 vs 成绩散点图）。散点图最关键——通过它能直观看出"学习越多分数越高"的相关性，是答辩亮点。注意添加 `<el-empty>` 处理无数据场景。

### B-5 协作式互动阅读（4.2d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| B-5.1 Flyway 迁移 | DBA | 0.3d | `TODO.md` 行 388–429 三张表 | `V17__text_annotation.sql` | 3 张表 + 复合索引 `(text_id, is_public, deleted)` | 无 | ⬜ | — |
| B-5.2 Entity + Mapper | 后端 | 0.3d | B-5.1 | `TextAnnotation` / `AnnotationUpvote` / `UserAnnotationPreferences` + Mapper | MP 注解齐全 | ↑ B-5.1 | ⬜ | — |
| B-5.3 AnnotationService | 后端 | 0.7d | B-5.2 | 8 个方法（create/get/update/delete/upvote/popular/getPref/updatePref） | 权限检查（仅自己可改）+ 软删除 + 单元测试 | ↑ B-5.2 | ⬜ | — |
| B-5.4 AnnotationController | 后端 | 0.5d | B-5.3 | 8 个端点（POST/GET/PUT/DELETE/upvote/popular/getPref/putPref） | Postman 全接口验证 + 权限 | ↑ B-5.3 | ⬜ | — |
| B-5.5 前端 API 封装 | 前端 | 0.2d | B-5.4 | `frontend/src/api/annotation.js` | 8 个方法导出 | ↑ B-5.4 | ⬜ | — |
| B-5.6 标注工具栏 | 前端 | 0.5d | B-5.5 | `<AnnotationToolbar>` 悬浮组件 | 选中文本即显示；4 色高亮 + 笔记 + 提问 + 公开/私有开关 | ↑ B-5.5 | ⬜ | — |
| B-5.7 标注渲染 | 前端 | 0.7d | B-5.5 | `ArticleDetail.vue` 注入 `<mark>` + 信息卡片 | 重叠用边框、嵌套用背景；点赞 + 删除（仅自己） | ↑ B-5.5 | ⬜ | — |
| B-5.8 标注侧边栏 | 前端 | 0.5d | B-5.5 | `<AnnotationSidebar>` 3 个 Tab | 我的/热门/全部；点击滚动到位置 | ↑ B-5.5 | ⬜ | — |
| B-5.9 偏好设置对话框 | 前端 | 0.3d | B-5.5 | `<AnnotationPreferences>` | 3 个开关 + 颜色选择；保存即生效 | ↑ B-5.5 | ⬜ | — |
| B-5.10 测试 + 性能 | QA | 0.5d | 上述全部 | Vitest 用例 + JMeter 并发标注测试 | 重叠渲染正确；100 并发无丢失；Redis 缓存命中 | ↑ B-5.6/7/8/9 | ⬜ | — |

**📝 任务说明**
- **B-5.1**：3 张表 DDL 见 `TODO.md:388-429`。重点：`text_annotations` 的复合索引 `(text_id, is_public, deleted)` 必须建（高频查询路径）；`annotation_upvotes` UNIQUE 约束 `(annotation_id, user_id)` 防止重复点赞；`user_annotation_preferences` 用 `user_id` 作主键（一人一行）。
- **B-5.2**：`TextAnnotation` 加 `@TableLogic` 软删除；`annotationType` 用 String 接收 ENUM；`isPublic` 注意 Jackson 序列化陷阱（Boolean is 开头需加 `@JsonProperty("isPublic")`，见 CLAUDE.md）。
- **B-5.3**：核心方法 `getTextAnnotations(textId, userId, includeOthers)`：① 查自己的（不论公开/私有）；② 若 `includeOthers=true` 且用户偏好允许，叠加他人的公开标注。`updateAnnotation` 必须校验 `userId == annotation.userId` 否则抛 `BusinessException`。`upvoteAnnotation` 实现 toggle：已点赞则取消并 -1，未点赞则插入并 +1（事务包裹）。
- **B-5.4**：8 个端点权限：所有 `hasAnyRole('USER','STUDENT')`；偏好设置接口隐式过滤 `userId = currentUser`（不接受外部传入 userId 防越权）。
- **B-5.5**：API 封装风格与 `src/api/student.js` 一致。`createAnnotation` 入参注意 startOffset/endOffset 顺序（start ≤ end），客户端预校验。
- **B-5.6**：工具栏出现时机：`window.getSelection().toString().length > 0`。定位：选区上方居中，箭头指向选区中点。注意移动端长按选择文本的事件兼容（`selectionchange` + `touchend`）。
- **B-5.7**：**最难任务**。重叠标注用 DOM range API 切分文本节点，按起止位置插入嵌套 `<mark>`。颜色合成：相同位置多人标注时叠加半透明色（CSS `mix-blend-mode: multiply`）。建议先用 `rangy.js` 库或参考 Hypothes.is 开源实现。
- **B-5.8**：侧边栏宽度 320px，移动端全屏抽屉。Tab 切换不重新请求（前端缓存）。点击标注项滚动到位置用 `scrollIntoView({behavior:'smooth', block:'center'})`。
- **B-5.9**：偏好设置变更**立即生效**（不需点保存按钮）—— `@change` 触发 PUT 请求 + 本地 store 更新。颜色选择用 `<el-color-picker>` 限定 4 个预设色（黄/绿/蓝/粉）。
- **B-5.10**：并发测试场景：100 用户同时对同一段落标注（验证位置冲突处理）、100 用户同时点赞同一标注（验证唯一约束）。Redis 缓存键 `annotation:text:{textId}`，写入时主动失效。

---

## 五、模块 C：全栈零停机热部署（✅ 已实现）

> **状态**：✅ 蓝绿部署 + Deployer Sidecar 已实现（2026-05-29）
> **设计文档**：`docs/ZERO_DOWNTIME_DEPLOYMENT.md`（v2.0）

### 已完成

| 子模块 | 实现方式 | 关键文件 |
|--------|----------|----------|
| 蓝绿部署脚本 | `scripts/hot-deploy.sh`（308 行），10 步蓝绿流程 | `scripts/hot-deploy.sh` |
| Deployer Sidecar | 后端通过 Docker Socket 启动独立 deployer 容器 | `docker/deployer/Dockerfile` |
| 后端 Service | 两阶段执行: `docker run -d` + `docker logs -f` | `UpdateServiceImpl.java` |
| 后端 API | SSE 流式端点 `/trigger` + `/status` + `/config` + `/deploy-history` | `UpdateController.java` |
| 前端页面 | 统一部署管理: 配置 + 触发 + SSE 日志 + 步骤进度 | `frontend/src/views/ops/Update.vue` |
| SSE 断连恢复 | 后端重启时自动 polling 等待新后端 | `Update.vue` |
| 定时调度 | Spring Cron 动态调度 | `DynamicUpdateScheduler.java` |

### 未实现（可扩展）

| 子模块 | 说明 |
|--------|------|
| Shadow Table 引擎 | DDL 变更零停机（MyBatis 拦截器双写 + RENAME TABLE），当前无需求 |
| 滚动升级引擎 | MySQL/Redis 版本升级（主从切换策略），当前无需求 |
| 自动变更检测 | `git diff` 自动分类变更类型并路由到对应策略 |

---

## 六、模块 D：性能压测基线（2.5 天）

> **目标**：建立可重复的性能基线 + 完成首轮优化
> **场景**：单机 4C8G，模拟 100/200/500 并发

### D-1 压测脚本（0.8d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| D-1.1 公开接口脚本 | DevOps | 0.2d | wrk + Lua | `perf/wrk/public-articles.lua` 等 5 个 | 文章列表/详情/词典查询/SSO 回调/feature-toggle | 无 | ⬜ | — |
| D-1.2 认证接口脚本 | DevOps | 0.3d | JWT 预生成 | `perf/wrk/student-*.lua` 5 个 | 含登录获取 token + 携带 token 的业务接口 | ‖ D-1.1 | ⬜ | — |
| D-1.3 写入接口脚本 | DevOps | 0.3d | 同上 | `perf/wrk/write-*.lua` 3 个 | 阅读进度提交 / 生词加入 / 学习记录写入 | ‖ D-1.2 | ⬜ | — |

**📝 任务说明**
- **D-1.1**：5 个公开接口：① `/api/public/articles`（文章列表）；② `/api/public/articles/{id}`（详情）；③ `/api/public/dict/word?q=hello`（词典查询）；④ `/api/public/system/feature-toggles`；⑤ `/api/public/sso/login`。Lua 脚本统一格式：定义请求 + 自定义参数。
- **D-1.2**：先用 `student/123456` 账号登录获取 JWT，存到 token 池（10 个不同账号轮换避免 rate-limit）。Lua `init()` 加载 token 列表，`request()` 随机取一个。覆盖：学生 Dashboard、生词本列表、任务列表、AI 问答、AI 翻译。
- **D-1.3**：3 个写入接口：① `POST /api/student/reading-progress`；② `POST /api/student/vocabulary`；③ `POST /api/student/learning/record`。注意写入接口压测前**必须**备份数据库（`mysqldump` 前置脚本），跑完恢复。

### D-2 基线报告（0.7d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| D-2.1 执行 + 收集 | QA | 0.3d | D-1 全部 | `perf/results/baseline-{date}.json` | 100/200/500 并发各跑 3 轮 | ↑ D-1 | ⬜ | — |
| D-2.2 慢查询 + 报告 | DevOps | 0.4d | D-2.1 + MySQL slow log | `docs/PERFORMANCE_BASELINE.md` | QPS/P50/P95/P99 + Top 10 慢查询 + 优化建议 | ↑ D-2.1 | ⬜ | — |

**📝 任务说明**
- **D-2.1**：每个场景 3 轮取中位数避免偶发抖动。压测时 MySQL 开启 `slow_query_log=ON` + `long_query_time=1`。结果 JSON 含每个接口的 throughput/latency 分位数/错误率。压测期间监控 CPU/内存/网络/磁盘 IO（建议用 `vmstat`/`iostat`）。
- **D-2.2**：报告结构：① 测试环境（机器配置/网络）；② 各接口基线表格；③ 资源瓶颈分析；④ 慢查询 Top 10 + EXPLAIN 输出；⑤ 优化建议（按 ROI 排序）。Top 10 慢查询用 `mysqldumpslow -s t /var/log/mysql/slow.log | head -50` 提取。

### D-3 性能优化（1d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| D-3.1 Redis 热点缓存 | 后端 | 0.5d | D-2.2 报告 | `@Cacheable` 注解 + Redis 配置 | 文章列表/单词查询命中率 >70%；二次压测 QPS 提升 | ↑ D-2.2 | ⬜ | — |
| D-3.2 连接池调优 | DevOps | 0.2d | HikariCP 监控 | `application.yml` 调整 maximumPoolSize/idleTimeout | 连接等待 <10ms | ‖ D-3.1 | ⬜ | — |
| D-3.3 Nginx gzip + 缓存 | DevOps | 0.2d | 现有 Nginx 配置 | `nginx.conf` gzip + 静态资源 cache-control | 静态资源传输大小减少 60%+ | ‖ D-3.1 | ⬜ | — |
| D-3.4 分页默认值 | 后端 | 0.1d | 现有 Controller | 各接口 size 默认值审计 | 默认 ≤20，最大 ≤100 | ‖ D-3.1 | ⬜ | — |

**📝 任务说明**
- **D-3.1**：按 D-2.2 报告中的高频接口优先加缓存。`@Cacheable(value="articles:list", key="#page+'_'+#size", unless="#result==null")`。注意失效策略：文章新增/更新时主动 `@CacheEvict`。单词查询用本地 Caffeine（避免 Redis 网络开销，TTL 1h）。
- **D-3.2**：当前默认 `maximumPoolSize=10`，500 并发明显不够。建议调到 20-30（同时 MySQL `max_connections` 也要相应调大）。`connectionTimeout=3000`，`idleTimeout=600000`。HikariCP metrics 必须导出供 E-1.4 用。
- **D-3.3**：gzip 配置：`gzip on; gzip_types text/css application/javascript application/json; gzip_min_length 1024;`。静态资源 `Cache-Control: public, max-age=2592000`（30 天）。注意 HTML 不缓存（`Cache-Control: no-cache`）防止前端更新看不到。
- **D-3.4**：审计所有 Controller 的 `@RequestParam("size")` 默认值，超过 20 的强制改为 20，最大值校验 100。前端 axios 也需检查 `size` 参数（避免传 1000 拖垮服务）。

---

## 七、模块 E：运维监控体系（4.5 天）

> **目标**：Prometheus + Grafana + 集中日志 + 告警 + APM
> **新增文件**：`docker-compose.monitoring.yml`、`monitoring/prometheus.yml`、`monitoring/grafana-dashboards/*.json`

### E-1 指标监控（1.6d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| E-1.1 Prometheus 容器化 | DevOps | 0.4d | 现有 docker-compose | `docker-compose.monitoring.yml` + `monitoring/prometheus.yml` | Prometheus UI 可访问，能抓取目标 | 无 | ⬜ | — |
| E-1.2 Actuator 集成 | 后端 | 0.2d | `application.yml` | `management.endpoints.web.exposure.include=health,metrics,prometheus` | `/actuator/prometheus` 返回 metrics | 无 | ⬜ | — |
| E-1.3 JVM Dashboard | DevOps | 0.3d | E-1.1 + E-1.2 | `grafana-dashboards/jvm.json` | 堆/GC/线程池图表完整 | ↑ E-1.1/1.2 | ⬜ | — |
| E-1.4 DB Dashboard | DevOps | 0.3d | HikariCP metrics | `grafana-dashboards/hikari.json` + `mysqld_exporter` | 连接池使用率/慢查询图表 | ↑ E-1.1 | ⬜ | — |
| E-1.5 API Dashboard | DevOps | 0.4d | E-1.2 metrics | `grafana-dashboards/api.json` | QPS / 延迟 P50/P95/P99 / 错误率 / 按 URI 分组 | ↑ E-1.2 | ⬜ | — |

**📝 任务说明**
- **E-1.1**：`docker-compose.monitoring.yml` 独立于主 compose，方便选择性启停。Prometheus 配置抓取间隔 15s，保留 15 天。targets 含 `backend:8080/actuator/prometheus`、`mysqld_exporter:9104`、`node_exporter:9100`。Grafana 默认账号需通过环境变量 `GF_SECURITY_ADMIN_PASSWORD` 配置（不要默认 admin/admin）。
- **E-1.2**：`application.yml` 加 `management.endpoints.web.exposure.include: health,metrics,prometheus,info`，`management.metrics.export.prometheus.enabled: true`。**注意**：暴露 metrics 后必须用 Spring Security 限制访问（仅允许 Prometheus 容器内网 IP 访问 `/actuator/prometheus`），否则信息泄漏。
- **E-1.3**：JVM Dashboard 至少包含：堆内存（已用/最大）、GC 次数/耗时、线程数（活跃/守护/峰值）、类加载数。可直接 import Grafana 社区 Dashboard ID `4701`（JVM Micrometer），再按项目需求微调。
- **E-1.4**：HikariCP metrics 通过 Actuator 自动导出（命名 `hikaricp.connections.*`）。Dashboard 含：活跃连接数、等待线程数、等待时间、连接获取时间。MySQL 部分需额外部署 `mysqld_exporter`（连接慢查询日志、表锁、复制延迟）。
- **E-1.5**：核心 Dashboard，比赛展示用。基于 `http.server.requests` metric 计算。注意按 URI 分组要排除噪声路径（如 `/actuator/**`、静态资源）。错误率公式：`sum(rate(http_server_requests_seconds_count{status=~"5..|4.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))`。

### E-2 集中式日志（1.4d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| E-2.1 选型决策 | DevOps | 0.2d | 资源约束 | `docs/LOGGING_DECISION.md` | 选择 ELK 或 Loki + 理由 | 无 | ⬜ | — |
| E-2.2 部署收集器 | DevOps | 0.5d | E-2.1 决策 | 扩展 `docker-compose.monitoring.yml` | 日志能查询，索引 30 天滚动 | ↑ E-2.1 | ⬜ | — |
| E-2.3 日志结构化 | 后端 | 0.4d | logback 配置 | JSON 格式 + traceId 字段 | 应用日志全部 JSON 输出 | 无 | ⬜ | — |
| E-2.4 Trace ID 注入 | 后端 | 0.3d | Spring Sleuth / 自定义 Filter | `TraceIdFilter` + MDC | 跨服务调用 traceId 透传 | ↑ E-2.3 | ⬜ | — |

**📝 任务说明**
- **E-2.1**：决策维度：① 资源占用（Loki 远低于 ELK）；② 查询体验（Kibana 更成熟）；③ 团队熟悉度。**推荐 Loki**（与 Grafana 共享 UI，资源开销 < 1GB 内存）。文档需明确选型理由、部署架构图、容量规划。
- **E-2.2**：Loki 用 Promtail 收集容器日志（挂载 `/var/lib/docker/containers/`）。配置标签：`service`、`level`、`traceId`。Grafana 添加 Loki 数据源，建立 LogQL 查询模板（按服务过滤、按 traceId 关联）。
- **E-2.3**：Logback 改为 `LogstashEncoder`（依赖 `net.logstash.logback:logstash-logback-encoder`）。字段：`@timestamp/level/logger/message/traceId/spanId/userId`。**保留 console 输出** + JSON 输出（分离 `STDOUT_PLAIN` 和 `STDOUT_JSON` Appender）。
- **E-2.4**：Spring Cloud Sleuth 自动注入 traceId 到 MDC 和响应头 `X-Trace-Id`。前端 axios 拦截器捕获 `X-Trace-Id` 写入页面错误提示，方便用户报障时贴回。`logback.xml` 中 `%X{traceId}` 占位输出。

### E-3 告警通知（0.7d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| E-3.1 告警规则 | DevOps | 0.4d | E-1.5 metrics | `monitoring/alert-rules.yml` | 错误率>1% / P99>2s / 服务下线 / 磁盘>80% | ↑ E-1.5 | ⬜ | — |
| E-3.2 通知通道 | DevOps | 0.3d | E-3.1 | Alertmanager 配置 + 钉钉/邮件 webhook | 触发告警能在 1 分钟内收到通知 | ↑ E-3.1 | ⬜ | — |

**📝 任务说明**
- **E-3.1**：核心 4 条规则：① `up==0`（服务下线，立即告警）；② `error_rate > 0.01 for 5m`（5 分钟错误率）；③ `histogram_quantile(0.99, ...) > 2`（P99 延迟）；④ `disk_usage > 0.8`（磁盘）。规则分 P0/P1/P2 三级，对应不同通知渠道。**避免告警风暴**：用 `for: 5m` 持续时间过滤瞬时抖动。
- **E-3.2**：Alertmanager 配置 3 个 receiver：`dingtalk-p0`（紧急告警，群机器人 + @all）、`email-p1`（普通告警，邮件）、`log-p2`（仅记录）。路由策略：severity=critical → P0，warning → P1，info → P2。配 silence 规则（部署窗口期静默）。钉钉机器人需加签名验证。

### E-4 APM 应用监控（0.7d）

| 子任务 | 角色 | 估时 | 输入 | 输出 | 验收 | 依赖 | 状态 | 负责人 |
|--------|------|------|------|------|------|------|------|--------|
| E-4.1 选型 | DevOps | 0.2d | 团队能力 | `docs/APM_DECISION.md` | SkyWalking 或 Pinpoint + 理由 | 无 | ⬜ | — |
| E-4.2 探针 + 拓扑 | DevOps | 0.5d | E-4.1 | 探针配置 + Java agent 启动参数 | 服务拓扑图 + 慢请求列表 | ↑ E-4.1 | ⬜ | — |

**📝 任务说明**
- **E-4.1**：决策维度：① 中文支持（SkyWalking 国内更主流）；② 部署复杂度（SkyWalking 单组件，Pinpoint 需 HBase）。**推荐 SkyWalking 9.x**（资源开销中等、UI 直观、Spring 生态成熟）。文档需含部署架构、性能开销评估（通常 < 5%）。
- **E-4.2**：Java agent 接入：在 Dockerfile 中添加 `JAVA_TOOL_OPTIONS="-javaagent:/agent/skywalking-agent.jar -Dskywalking.agent.service_name=langhack-backend -Dskywalking.collector.backend_service=skywalking-oap:11800"`。验收：① 调用拓扑图能看到 backend → MySQL/Redis 关系；② 慢请求列表能定位到具体 SQL；③ trace 可查询任意请求的完整调用链。

---

## 八、关键并行路径分析

### 可立即并行启动的任务（Day 1）
```
后端 #1: A-1.1 + A-2.1 + A-3.1 + A-4.1（4 个独立 API，1.3d 内完成）
后端 #2: A-5.1 → A-5.2 → A-5.3（1d 内完成推荐埋点后端链路）
前端 #1: 等 A-1.1 完成（半天）→ A-1.2，并行其他卡片
前端 #2: A-5.4（等 A-5.2）/ B-1.7 前端 API 预备
DevOps:  C-1.1 + D-1.1（蓝绿模板 + 压测脚本）
DBA:     B-1.1 + B-2.1 + B-3.1 + B-5.1 + A-5.1（5 个 Flyway 迁移，1d 完成）
```

### 单点关键路径（无法并行，决定整体工期）
- **B-1.3 ConversationService（1d）** — B-1 后续所有任务的瓶颈
- **B-5.7 标注渲染（0.7d）** — B-5 最复杂的前端任务
- **C-2.1 ShadowTableInterceptor（1d）** — 零停机的核心，需深度测试

### 跨模块依赖
- B-3.4 依赖 A-1.1 已完成（college 字段加入统计）
- B-2.5 依赖 A-4.2 已完成（在现有卡片内嵌入）
- B-4.1 依赖 A-3.3 已完成（数据准备）
- E-3.1 依赖 E-1.5 已完成（指标可观测才能配告警）

### 推荐里程碑
| 里程碑 | 时间 | 完成条件 |
|--------|------|---------|
| M1：P0 Dashboard 上线 | Day 3 EOD | A-1 ~ A-5 全部 ✅ + 后端冒烟通过 |
| M2：监控可视化 | Day 5 EOD | E-1.1 ~ E-1.5 + E-2.2 ✅ |
| M3：压测基线 | Day 5 EOD | D-1 + D-2 ✅，报告产出 |
| M4：AI 对话功能 | Day 7 EOD | B-1 全部 ✅ |
| M5：协作标注 | Day 9 EOD | B-5 全部 ✅ |
| M6：零停机首次演练 | Day 10 EOD | C-1 + C-4 ✅，演练成功 |

---

## 九、风险与建议

### 高风险项
| 风险 | 影响 | 应对 |
|------|------|------|
| C-2.1 ShadowTableInterceptor 双写不一致 | 数据损坏 | 必须配合 `system_config` 开关 + 灰度 + 数据校验脚本 |
| B-1.6 分享 Token 泄漏 | 隐私事故 | HMAC 强校验 + 默认 7 天过期 + 访问日志 |
| B-5.7 重叠标注渲染性能 | 长文章卡顿 | 虚拟滚动 + Web Worker + Redis 缓存（TTL 5 分钟） |
| A-3.1 学校数据隐私 | 合规 | 仅做汇总统计，不展示个人成绩；脱敏字段 |
| C-3.1 主从切换数据丢失 | 数据丢失 | 切换前确认 `Seconds_Behind_Master=0` + 半同步复制 |

### 协作纪律
1. **领取任务**：在表格 `状态` 列改为 🚧 并填入负责人
2. **每日同步**：每个负责人在 standup 同步当前任务的阻塞点
3. **代码评审**：跨角色任务（如后端→前端）须先 PR 评审 API 契约
4. **Flyway 协调**：DBA 统一分配 V13~V17 版本号，避免撞车（参考 `memory/feedback_flyway_version_collision.md`）
5. **完成定义**：每个子任务的 `验收` 列必须全部满足才能改 ✅

### 文档同步
- 完成主任务后，在 `TODO.md` 对应条目从 ❌ 改为 ✅
- 重要架构决策写入 `docs/`（如 E-2.1、E-4.1 的选型）
- 经验教训写入 `memory/lessons-learned.md`

---

**文档版本**：v1.0 · 创建于 2026-05-20 · 来源 `TODO.md` 拆分
