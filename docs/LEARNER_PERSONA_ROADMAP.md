# LangHack 学习者画像全程路线图 (V1 / V2 / V3)

## Context

LangHack 已有完整的学习行为数据（learning_records + text_learning_records）、词汇掌握（vocabulary_books）、阅读偏好（user_text_reads）、推荐系统（RecommendationServiceImpl）和教师学生关系（TeacherDataController + teacher_student_binding），但缺少统一的"学习者画像层"将这些数据转化为可操作的标签和策略。

**关键设计决策 — 阅读水平用内容版本而非管理员难度标签衡量**：平台为每篇文章 AI 生成 CET-4 / CET-6 / Academic（考研）/ 中英对照四种版本，学生自主选择阅读版本。学生阅读版本的分布（如 CET-4:60% / CET-6:30% / Academic:10%）比管理员手动标注的 EASY/MEDIUM/HARD 更客观地反映其英语水平和备考目标。当前 `text_learning_records` 表缺少 `content_version` 字段，V1 需补齐此数据缺口。

本计划将画像系统从 V1 规则画像一直规划到 V3 AI 增强，45 个任务，每个任务有明确依赖、文件清单和测试要求。V1 新增 1 列 + 1 枚举（不建新表），V2 增加事件追踪和快照持久化，V3 增加 LLM 摘要和学生端展示。

---

## 架构决策（跨阶段统一）

### 包结构
- DTOs: `com.english.dto.learner`
- Enums: `com.english.enums`
- Service/Impl: `com.english.service` / `com.english.service.impl`
- Controller: `com.english.controller`
- Mapper/Entity: `com.english.mapper` / `com.english.entity`
- Frontend: `frontend/src/components/learner/`, `frontend/src/api/learnerProfile.js`

### Feature Toggle Matrix

| Phase | Toggle | Default | Mechanism |
|-------|--------|---------|-----------|
| V1 | `learner-profile.enabled` | `true` | `@Value` yaml |
| V1 | `recommendation.persona.enabled` | `false` | `@Value` yaml |
| V1 | `teacher.learner_profile_enabled` | `true` | `SystemConfigService` DB |
| V1 | `admin.tag_management` | `true` | `SystemConfigService` DB (V38 seed) |
| V2 | `learner-profile.tracking.enabled` | `false` | `@Value` yaml |
| V2 | `learner-profile.snapshot.enabled` | `false` | `@Value` yaml |
| V2 | `admin.persona_analytics` | `false` | `FeatureToggleService` DB |
| V3 | `learner-profile.llm-summary.enabled` | `false` | `@Value` yaml |
| V3 | `student.learner_profile_enabled` | `false` | `SystemConfigService` DB |

### Flyway 版本分配

| Version | Phase | 变更 | 状态 |
|---------|-------|------|------|
| V31 | V1 | `ALTER TABLE text_learning_records ADD COLUMN content_version` | ✅ 已完成 |
| V32 | V2 | `CREATE TABLE recommendation_events` | ✅ 已完成 |
| V33 | V2 | `CREATE TABLE learner_profile_snapshots` | ✅ 已完成 |
| V34 | V2 | `CREATE TABLE tag_recalibration_log` | ✅ 已完成 |
| V35 | V3 | `CREATE TABLE learner_profile_summaries` | ✅ 已完成 |
| V36 | V1* | `CREATE TABLE learner_composite_tags` | ✅ 已完成（提前到 V1） |
| V38 | V1* | Seed 9 个基础标签定义 + `admin.tag_management` toggle | ✅ 已完成 |

> *V36/V38 原计划 V3，因标签全量可管理化需求提前到 V1 实现。

V1 对已有表 `text_learning_records` 新增 1 列 `content_version`（Flyway V31），不建新表。`learner_composite_tags` 表（Flyway V36）和种子数据（V38）已提前从 V3 并入 V1。其余画像数据按需计算。

---

## PHASE V1: 规则画像 (~9 人天) ✅ 已完成

### V1-A: 数据缺口修复（前置任务）

**T-001 | 修复活跃天数计算桩代码** ✅
- 依赖: 无 | 阻塞: T-008, T-009
- 修改:
  - `backend/.../mapper/LearningRecordMapper.java` — 新增 `countActiveDaysSince(studentId, since)` 和 `selectDistinctStudyDates(studentId)` 两个 `@Select`
  - `backend/.../service/LearningRecordsService.java` — 新增 `countActiveDays(Long studentId, int lastNDays)` 接口方法
  - `backend/.../service/impl/LearningRecordsServiceImpl.java` — 用真实查询替换 `calculateConsecutiveDays()` 桩（当前始终返回 7）
- 测试: `LearningRecordsActiveDaysTest.java` — 0 条记录返回 0、不连续间隔正确计算、午夜边界
- 工量: S

**T-002 | 新增任意日期范围学习时长查询** ✅
- 依赖: 无 | 阻塞: T-008, T-009
- 修改:
  - `LearningRecordMapper.java` — 新增 `sumDurationSecondsBetween(studentId, since, until)`，单条记录上限 2 小时 `LEAST(duration_seconds, 7200)`
  - `LearningRecordsService.java` — 新增 `getStudyMinutesInRange(Long studentId, LocalDateTime from, LocalDateTime to)`
  - `LearningRecordsServiceImpl.java` — 实现
- 测试: 同 T-001 测试文件
- 工量: S

**T-003 | 新增逾期复习计数查询** ✅
- 依赖: 无 | 阻塞: T-008, T-009
- 修改:
  - `VocabularyBookMapper.java` — 新增 `countOverdueReviews(studentId)` (next_review_time < NOW()) 和 `countDueReviews(studentId)` (next_review_time IS NOT NULL)
  - `VocabularyBooksService.java` — 新增 `getReviewStats(Long studentId)` 返回 overdueCount/dueCount
  - `VocabularyBooksServiceImpl.java` — 实现
- 测试: `VocabularyBooksReviewStatsTest.java`
- 工量: S

**T-004 | 内容版本偏好追踪（后端基础设施）** ✅
- 依赖: 无 | 阻塞: T-005, T-008, T-009, T-013, T-020
- **背景**: 平台已有 CET-4/CET-6/Academic/中英对照四种 AI 生成版本，学生在 `InteractiveText.vue` 中自主切换。但 `text_learning_records` 表不记录学生读的哪个版本，导致无法分析学生的英语水平和备考目标。
- 新建:
  - `db/migration/V31__add_content_version_to_text_learning_records.sql`:
    ```sql
    ALTER TABLE text_learning_records ADD COLUMN content_version VARCHAR(20) DEFAULT NULL
      COMMENT '学生阅读的内容版本: original/cet4/cet6/academic/chinese_parallel';
    CREATE INDEX idx_tlr_student_version ON text_learning_records(student_id, content_version);
    ```
  - `backend/.../enums/ContentVersion.java` — `ORIGINAL`, `CET4`, `CET6`, `ACADEMIC`, `CHINESE_PARALLEL`，含 `code` 和 `displayName` 字段
- 修改:
  - `backend/.../entity/TextLearningRecord.java` — 新增 `@TableField("content_version") private String contentVersion;`
  - `backend/.../mapper/TextLearningRecordMapper.java` — 新增:
    - `selectVersionDistribution(studentId)` → `SELECT content_version, COUNT(*) as count, SUM(duration_seconds) as total_seconds FROM text_learning_records WHERE student_id=#{studentId} AND content_version IS NOT NULL GROUP BY content_version ORDER BY total_seconds DESC`
    - `selectPrimaryVersion(studentId)` → 返回时长最长的版本（对标旧的 `selectMostReadDifficulty`）
  - `backend/.../service/impl/LearningRecordsServiceImpl.java`:
    - `startReadingSession(studentId, textId, assignmentId)` → 增加 `contentVersion` 参数
    - `recordLearning()` → 若 payload 含 `contentVersion` 字段，更新 `TextLearningRecord.contentVersion`
  - `backend/.../controller/StudentLearningController.java`:
    - `POST /api/student/learning/texts/{textId}/start` → 新增可选参数 `contentVersion`
    - `POST /api/student/learning/record/learning` → body 新增可选字段 `contentVersion`
- 测试: `ContentVersionTrackingTest.java` — 含版本记录/分布查询/空数据降级/枚举覆盖
- 工量: M

**T-004b | 前端内容版本上报** ✅
- 依赖: T-004 | 阻塞: T-008（数据收集前置）
- **目标**: 让前端在阅读时把当前版本告知后端，使 `text_learning_records.content_version` 有值
- 修改:
  - `frontend/src/views/student/InteractiveText.vue`:
    - `startReading()` 方法（~line 1653）: 调用 `POST /texts/{textId}/start` 时增加 `contentVersion` 参数，值取 `currentVersion.value`（默认 `'original'`）
    - `switchVersion(version)` 方法（~line 1573）: 版本切换后调用 `POST /record/learning` 时带上新的 `contentVersion`
    - `saveLearningRecord()` 方法（~line 926）: 周期保存 payload 增加 `contentVersion: currentVersion.value`
    - `onBeforeUnmount` 的 keepalive fetch（~line 1741）: body 增加 `contentVersion`
  - `frontend/src/api/student.js`:
    - `startTextReading(textId, assignmentId, contentVersion)` → query string 增加 `contentVersion`
    - `recordLearning(data)` → data 对象支持 `contentVersion` 字段
  - `frontend/src/views/user/SimpleTextView.vue`:
    - `handleVersionChange(versionKey)` 后追加版本上报（仅登录用户）: 调用 `POST /api/public/texts/{textId}/version-read?version={versionKey}`
  - `backend/.../controller/PublicTextController.java`:
    - 新增 `POST /api/public/texts/{textId}/version-read?version=cet4` — 轻量级版本阅读记录（写入 `text_learning_records`），对未登录用户返回 success(null) no-op
- 测试: 前端手动验证版本切换后 Network 面板可见 contentVersion 参数
- 工量: M
- 完成于: 2026-05-28

---

### V1-B: 后端核心（DTO + Service + 标签引擎）

**T-005 | 创建 9 个画像 DTO** ✅
- 依赖: T-004 (ContentVersion enum) | 阻塞: T-008, T-011, T-012, T-015
- 新建: `backend/.../dto/learner/` 下 9 个类 — `LearnerProfileDto`, `*IdentityDto`, `*AbilityDto`, `*HabitDto`, `*PreferenceDto`, `*RiskDto`, `*TagDto`, `*EvidenceDto`, `*ClassSummaryDto`
- 字段定义: 按主规范 Section 6 中的字段表，**PreferenceDto 变更如下**:
  - ~~preferredDifficulty (String)~~ → `primaryContentVersion (String)` — 学生阅读时长最长的内容版本（original/cet4/cet6/academic）
  - ~~easyReadRatio / mediumReadRatio / hardReadRatio~~ → `versionDistribution (Map<String, Double>)` — 各版本阅读时长占比（如 `{"cet4": 0.55, "cet6": 0.30, "academic": 0.10, "original": 0.05}`）
  - 新增: `targetExamLevel (String)` — 基于 versionDistribution 推断的备考目标（CET-4 / CET-6 / 考研），可空
  - 保留: `topCategories (List<String>)` — 阅读主题偏好（与版本正交）
- 测试: `LearnerProfileDtoTest.java` — 字段可达、序列化往返
- 工量: M

**T-006 | 创建画像枚举** ✅
- 依赖: 无 | 阻塞: T-008, T-009
- 新建:
  - `backend/.../enums/ProfileStatus.java` — READY / PARTIAL / EMPTY
  - `backend/.../enums/RiskLevel.java` — LOW / MEDIUM / HIGH
  - ~~`backend/.../enums/LearnerTagCode.java`~~ — **已废弃**: 标签定义已迁移到 `learner_composite_tags` 表（DB 驱动），枚举文件仍存在但不再被 `LearnerProfileServiceImpl` 使用
- 注: `ContentVersion` 枚举已在 T-004 中创建
- 工量: S

**T-007 | 创建 LearnerProfileService 接口** ✅
- 依赖: T-005 | 阻塞: T-008
- 新建: `backend/.../service/LearnerProfileService.java` — `getUserProfile(Long userId)`, `getClassProfileSummary(Long classId)`
- 工量: S

**T-008 | 实现 LearnerProfileServiceImpl 数据聚合** ✅
- 依赖: T-001 ~ T-007 全部 | 阻塞: T-009, T-011, T-012, T-013
- 新建: `backend/.../service/impl/LearnerProfileServiceImpl.java`
- 注入: LearningRecordsService, VocabularyBooksService, TextLearningRecordMapper, UserService, StudentClassMapper, SchoolStudentMapper, CompositeTagEngine
- 内部方法: `buildIdentity()`, `buildAbility()`, `buildHabit()`, `buildPreference()`, `buildRisk()`, `buildMetricsMap()`, `buildEvidenceSummary()`
- **标签评估**: 由 `CompositeTagEngine.evaluateAll(metricsMap)` 统一执行，不再使用内部 `buildTags()` 方法
- **buildPreference() 变更**:
  - 调用 `TextLearningRecordMapper.selectVersionDistribution(studentId)` 获取各版本阅读时长分布
  - 计算 `primaryContentVersion`（时长最长的版本）和 `versionDistribution`（各版本占比）
  - 推断 `targetExamLevel`: CET-4 版本占比 ≥50% → "CET-4"；CET-6 ≥40% → "CET-6"；Academic ≥30% → "考研"；否则 null
  - 降级: 若无版本数据（用户从未通过学生端阅读），退回到 `UserTextReadMapper.selectMostReadDifficulty()` 填充 `primaryContentVersion = "unknown"`
- 缓存: `@Cacheable("learner-profile:user")` / `@Cacheable("learner-profile:class")`
- 开关: `@Value("${learner-profile.enabled:true}")`
- 工量: L

**T-009 | 实现 9 个 DB 驱动标签规则** ✅ (原"6 个标签评估规则"，已扩展)
- 依赖: T-006, T-008 | 阻塞: T-011, T-013
- **架构变更**: 标签评估已从 `LearnerProfileServiceImpl.buildTags()` 迁移到 `CompositeTagEngine.evaluateAll(metricsMap)`
- 实际实现: `LearnerProfileServiceImpl.buildMetricsMap()` 计算 16 个指标 → 传入 `CompositeTagEngine` → 两遍求值
- **9 个标签定义（V38 种子数据）**:
  - `INACTIVE_RISK` — `activeDays14d < 3 AND studyMinutes14d > 0`
  - `VOCAB_WEAK` — `totalWords >= 30 AND weakRatio >= 0.6`
  - `REVIEW_OVERDUE` — `dueReviewCount > 0 AND overdueReviewRatio >= 0.5`
  - `CONSISTENT_LEARNER` — `activeDays7d >= 5`
  - `CONTENT_LEVEL_STUCK` — `textsRead >= 40 AND versionDistSize = 1`
  - `CONTENT_LEVEL_JUMP` — 默认禁用，规则占位
  - `IMPROVING_TREND` — `oldActiveDays14d > 0 AND trendRatio >= 1.3`
  - `DECLINING_TREND` — `oldActiveDays14d > 0 AND trendRatio <= 0.6`
  - `REVIEW_CHAMPION` — 连续 3 次快照 `overdueRatio < 0.05`
- 测试: `LearnerProfileServiceTest.java` — metricsMap 指标值验证 + mock CompositeTagEngine; `LearnerProfileV3Test.java` — CompositeTagEngine 两遍求值/metric 操作符/AND 组合
- 工量: M

**T-010 | 画像缓存配置** ✅
- 依赖: 无 | 阻塞: T-008
- 修改:
  - `backend/.../config/CacheConfig.java` — 新增 `"learner-profile:user"` 和 `"learner-profile:class"` 缓存条目，10 分钟 TTL
  - `application.yml` — 新增 `learner-profile:` 配置段
- 工量: S

---

### V1-C: API 层

**T-011 | 创建 LearnerProfileController (Admin/OPS)** ✅
- 依赖: T-005, T-008 | 阻塞: T-015, T-019
- 新建: `backend/.../controller/LearnerProfileController.java`
- 端点:
  - `GET /api/admin/learner-profile/{userId}` — `@PreAuthorize("hasAnyRole('ADMIN','OPS')")` → `Result<LearnerProfileDto>`
  - `GET /api/admin/learner-profile/class/{classId}` — 同上 → `Result<List<LearnerProfileClassSummaryDto>>`
- 错误: 400/404/500 按主规范 Section 7
- 测试: `LearnerProfileControllerTest.java`
- 工量: M

**T-012 | TeacherDataController 增加画像端点** ✅
- 依赖: T-005, T-008 | 阻塞: T-016, T-019
- 修改: `TeacherDataController.java`
  - 新增注入 `LearnerProfileService`
  - 新增常量 `TEACHER_LEARNER_PROFILE_TOGGLE`
  - 新增 `getStudentProfile(studentId, auth)` — 完整的开关→校验→绑定→映射→画像流程
  - 提取 `hasTeacherStudentBinding()` 公用
  - 新增 `resolvePlatformUserByStudentId()`, `isTeacherLearnerProfileEnabled()`
- 测试: `TeacherDataControllerProfileTest.java` — 6 个场景（开关关闭/空参数/无绑定403/无平台用户404/成功/回归）
- 工量: M

---

### V1-D: 推荐系统接入

**T-013 | PersonaSignals + 推荐重排** ✅
- 依赖: T-008, T-009 | 阻塞: T-019
- 修改: `RecommendationServiceImpl.java`
  - 新增注入 `LearnerProfileService`
  - 新增 `PersonaSignals` 内部类:
    - `primaryContentVersion` (String) — 学生主要阅读版本
    - `targetExamLevel` (String) — 备考目标（CET-4/CET-6/考研）
    - `tags` (Set<String>) — 激活的标签 codes
    - `riskLevel` (RiskLevel)
  - 新增 `loadPersonaSignals()`, `applyPersonaRerank()`, `computePersonaAdjustment()`
  - 修改 `recommend()` 在 CF 评分后调用重排
  - 修改 `fallback()`:
    - Tier 2 原逻辑: 按 `selectMostReadDifficulty()` 匹配 EASY/MEDIUM/HARD → **保留但降权**
    - 新增 Tier 1.5: 若 `targetExamLevel` 可用，优先推荐与备考目标相关的文章（按 category/tag 匹配，而非 difficulty）
    - 提取 `resolvePreferredContentLevel()` 替代 `resolvePreferredDifficulty()`
  - 新增 `@Value` 配置: `recommendation.persona.*`
- 约束: 默认关闭；单篇调整 [-0.12, +0.12]
- 修改: `application.yml` — 新增 `recommendation.persona.*` 配置块
- 测试: `RecommendationPersonaTest.java` — 开关关闭不变/同版本加分/VOCAB_WEAK 降权/冷启动 fallback/画像异常降级/无版本数据时退回 difficulty 匹配
- 工量: M

**T-014 | Fallback 方法拆分** ✅
- 依赖: T-013 | 阻塞: 无
- 修改: `RecommendationServiceImpl.java` — 拆分 `fillFeatured()`, `fillPreferredContentLevel()`, `fillLatest()`，`fallback()` 变为编排器
- 测试: 纯重构，行为不变，CI 回归验证
- 工量: S
- 完成于: 2026-05-28

---

### V1-E: 前端

**T-015 | 创建 learnerProfile API 模块** ✅
- 依赖: T-011, T-012 | 阻塞: T-016, T-017
- 新建: `frontend/src/api/learnerProfile.js` — `getLearnerProfile(userId)`, `getClassLearnerProfileSummary(classId)`, `getTeacherStudentProfile(studentId)`
- 工量: S

**T-016 | 创建画像卡片组件** ✅
- 依赖: T-015 | 阻塞: T-017, T-018
- 新建:
  - `frontend/src/components/learner/LearnerProfileCard.vue` — 接收 `profile` prop，展示身份/能力/习惯/偏好/标签/建议，含加载/空/错误态
    - **偏好区块**: 显示版本阅读分布条形图（CET-4 / CET-6 / Academic / 原文 各占比）+ 推断的备考目标标签
  - `frontend/src/components/learner/LearnerRiskTagList.vue` — 接收 `tags` 数组，按 tagType 着色的 el-tag 列表（含新标签 `CONTENT_LEVEL_STUCK` / `CONTENT_LEVEL_JUMP`）
- 工量: M

**T-017 | Teacher 端 MyStudents.vue 画像集成** ✅
- 依赖: T-015, T-016 | 阻塞: T-019
- 修改: `frontend/src/views/teacher/MyStudents.vue`
  - 每个学生卡片加"查看画像"按钮
  - 用 `el-drawer` 展示 `LearnerProfileCard`
  - 用 `FeatureGate` 包裹按钮尊重开关
- 工量: M

**T-018 | Admin 端 Classes.vue 画像摘要集成** ✅
- 依赖: T-015, T-016 | 阻塞: T-019
- 修改: `frontend/src/views/admin/Classes.vue`
  - 班级详情弹窗新增"画像摘要"区块
  - 学生列表显示高优先级标签
  - 新增"风险学生"筛选
- 工量: M

---

### V1-F: 测试与收尾

**T-019 | V1 端到端集成测试 + 配置验证** ✅
- 依赖: T-009, T-011, T-012, T-013, T-017, T-018 | 阻塞: 无
- 新建: `LearnerProfileIntegrationTest.java`
- 验证: 主规范 Section 16 全部验收标准
- 工量: M

**T-020 | V1 Docker init 同步** ✅
- 依赖: T-004 (V31 迁移) | 阻塞: 无
- 修改: `docker/mysql/init/04-apply-all-migrations.sql` — 追加 V31（`ALTER TABLE text_learning_records ADD COLUMN content_version`）
- **已部分完成**: V36（learner_composite_tags 建表）和 V38（种子数据 + feature toggle）已同步到 04-apply-all-migrations.sql
- 同步: `00-base-tables.sql` — 在 `text_learning_records` 建表语句中包含 `content_version` 列
- 工量: S

---

## PHASE V2: 行为反馈闭环 (~8 人天) ✅ 已完成

### V2-A: Schema

**T-021 | Flyway V32: recommendation_events 表** ✅
- 依赖: V1 完成 | 阻塞: T-024, T-025
- 新建: `db/migration/V32__recommendation_events.sql`
- 字段: id, user_id, text_id, event_type (IMPRESSION/CLICK/DISMISS/BOOKMARK), source, position, persona_tags, created_time
- 索引: idx_user, idx_text, idx_user_type, idx_created
- 工量: S

**T-022 | Flyway V33: learner_profile_snapshots 表** ✅
- 依赖: V1 完成 | 阻塞: T-026
- 新建: `db/migration/V33__learner_profile_snapshots.sql`
- 字段: id, user_id, snapshot_date, profile_status, risk_level, active_days_7d/14d, study_minutes_14d, inactive_days, new/learning/mastered_count, weak_ratio, overdue_review_count/ratio, **primary_content_version**, **version_distribution_json**, matched_tag_codes, tag_scores_json, created_time
- **变更**: 移除旧的 `preferred_difficulty` / `easy_read_ratio` / `medium_read_ratio` / `hard_read_ratio`，替换为版本分布数据
- 唯一键: (user_id, snapshot_date)
- 工量: S

**T-023 | Flyway V34: tag_recalibration_log 表** ✅
- 依赖: V1 完成 | 阻塞: T-027
- 新建: `db/migration/V34__tag_recalibration_log.sql`
- 字段: id, tag_code, old_threshold, new_threshold, reason, affected_user_count, operator, created_time
- 工量: S

---

### V2-B: 事件追踪后端

**T-024 | 创建推荐事件 Entity/Mapper/Service** ✅
- 依赖: T-021 | 阻塞: T-025, T-029
- 新建:
  - `entity/RecommendationEvent.java`
  - `mapper/RecommendationEventMapper.java` — 含 CTR by source、CTR by persona tag 聚合查询
  - `service/RecommendationTrackingService.java` + `impl/RecommendationTrackingServiceImpl.java`
- 方法: `trackEvent(userId, textId, eventType, source, position)`, `getClickThroughRateBySource(lastNDays)`, `getPersonaEffectivenessMetrics(lastNDays)`
- 开关: `@Value("${learner-profile.tracking.enabled:false}")`，关闭时 trackEvent 为 no-op
- 测试: `RecommendationTrackingServiceTest.java`
- 工量: M

**T-025 | 推荐事件埋点接入** ✅
- 依赖: T-024 | 阻塞: T-029
- 修改:
  - `RecommendationServiceImpl.java` — 注入 TrackingService，recommend() 返回后批量记录 IMPRESSION
  - 新建 `controller/RecommendationEventController.java` — `POST /api/recommendation/event` 接受前端 CLICK/DISMISS/BOOKMARK
  - `frontend/src/api/learnerProfile.js` — 新增 `trackRecommendationEvent(textId, eventType)`
  - 前端文章卡片组件 — 点击时发 CLICK 事件
- 测试: `RecommendationEventControllerTest.java`
- 工量: M

---

### V2-C: 画像快照持久化

**T-026 | 快照 Entity/Mapper/Service + 定时任务** ✅
- 依赖: T-022, T-008 | 阻塞: T-027, T-029, T-038
- 新建:
  - `entity/LearnerProfileSnapshot.java`
  - `mapper/LearnerProfileSnapshotMapper.java` — 含按用户+日期范围查历史、按班级+日期查风险分布
  - `service/LearnerProfileSnapshotService.java` + `impl/LearnerProfileSnapshotServiceImpl.java`
  - `scheduler/LearnerProfileSnapshotScheduler.java` — `@Scheduled(cron = "0 30 2 * * ?")` 每日凌晨 2:30
- 方法: `takeSnapshot(userId)`, `takeDailySnapshotsForAllActiveUsers()`, `getSnapshotHistory(userId, lastNDays)`, `getClassSnapshotSummary(classId, date)`
- 开关: `@Value("${learner-profile.snapshot.enabled:false}")`
- 测试: `LearnerProfileSnapshotServiceTest.java` — 快照创建/同日去重/历史查询/开关关闭
- 工量: L

---

### V2-D: 标签校准引擎

**T-027 | 标签校准分析服务** ✅
- 依赖: T-023, T-026, T-024 | 阻塞: T-028, T-029
- 新建:
  - `entity/TagRecalibrationLog.java`
  - `mapper/TagRecalibrationLogMapper.java`
  - `service/TagRecalibrationService.java` + `impl/TagRecalibrationServiceImpl.java`
- 功能:
  - 对比标签激活 vs 未激活用户的 CTR
  - 检测标签命中率过高(>80%)或过低(<5%)
  - 利用快照历史检测标签"翻转"（同一用户频繁激活/失活）
  - 所有阈值变更写审计日志
  - V2 仅提案，实际变更需管理员通过 API 确认
- 修改:
  - ~~`enums/LearnerTagCode.java`~~ — **不再需要**: 阈值已存储在 `learner_composite_tags.rule_definition` JSON 中，管理员通过 Admin CRUD 直接调整
  - `CompositeTagEngineImpl.java` — 标签规则已从 DB 读取，阈值变更无需修改代码
- 测试: `TagRecalibrationServiceTest.java`
- 工量: L

**T-028 | 标签校准 Admin API** ✅
- 依赖: T-027 | 阻塞: T-029
- 修改: `LearnerProfileController.java` — 新增 3 个 ADMIN 端点:
  - `GET /api/admin/learner-profile/tags/effectiveness`
  - `GET /api/admin/learner-profile/tags/proposals`
  - `POST /api/admin/learner-profile/tags/recalibrate`
- 测试: `LearnerProfileTagAdminTest.java`
- 工量: M

---

### V2-E: 分析看板

**T-029 | Admin 画像分析 API** ✅
- 依赖: T-024, T-026, T-027 | 阻塞: T-030
- 修改: `LearnerProfileController.java` — 新增分析端点:
  - `GET /api/admin/learner-profile/analytics/overview` — 平台风险分布、标签命中率、平均活跃天数
  - `GET /api/admin/learner-profile/analytics/trends?lastNDays=30` — 快照日趋势
  - `GET /api/admin/learner-profile/analytics/recommendation-effectiveness` — CTR by source + persona 提升量
- 新建 DTO: `PersonaAnalyticsOverviewDto`, `PersonaTrendPointDto`, `RecommendationEffectivenessDto`
- 测试: `PersonaAnalyticsServiceTest.java`
- 工量: M

**T-030 | Admin 画像分析前端页面** ✅
- 依赖: T-029 | 阻塞: 无
- 新建: `frontend/src/views/admin/PersonaAnalytics.vue`
  - 风险分布饼图
  - 标签命中率柱状图
  - 30 天趋势折线图
  - 推荐效果表格
  - 标签校准提案列表 + 操作按钮
- 修改: `frontend/src/router/index.js` — 新增 admin 路由 `persona-analytics`，`meta.featureKey: 'admin.persona_analytics'`
- 工量: L

**T-031 | V2 Docker init 同步** ✅
- 依赖: T-021, T-022, T-023 | 阻塞: 无
- 修改: `docker/mysql/init/04-apply-all-migrations.sql` — 追加 V32/V33/V34
- 工量: S

**T-032 | V2 端到端集成测试** ✅
- 依赖: 全部 V2 | 阻塞: 无
- 新建: `LearnerProfileV2IntegrationTest.java` — 事件追踪 → 快照 → 校准提案全链路
- 工量: M

---

## PHASE V3: AI 增强 + 学生端 (~12 人天) ✅ 已完成

### V3-A: Schema

**T-033 | Flyway V35: learner_profile_summaries 表** ✅
- 依赖: V2 完成 | 阻塞: T-035
- 新建: `db/migration/V35__learner_profile_summaries.sql`
- 字段: id, user_id, summary_type (PERSONAL/TEACHER_FACED/CLASS_COHORT), language, summary_text, model_id, input_snapshot_id, input_tags_json, generation_time_ms, created_time, updated_time
- 工量: S

**T-034 | Flyway V36: learner_composite_tags 表** ✅ 已完成
- 依赖: ~~V2 完成~~ | 阻塞: T-039
- **提前到 V1 实现**: `learner_composite_tags` 表已在 Flyway V36 建表，V38 种子 9 个基础标签定义
- 字段: id, code, display_name, tag_type, priority, rule_definition (JSON), action_hint, enabled, created_time, updated_time
- 唯一键: code
- 完成于: 2026-05-27

---

### V3-B: LLM 集成

**T-035 | LLM 画像摘要服务** ✅
- 依赖: T-033, T-008, T-026 | 阻塞: T-036, T-037
- 新建:
  - `entity/LearnerProfileSummary.java`
  - `mapper/LearnerProfileSummaryMapper.java`
  - `service/LearnerProfileSummaryService.java` + `impl/LearnerProfileSummaryServiceImpl.java`
- 流程: 获取画像 → 获取快照趋势 → 构建结构化 prompt → 调用 OpenRouterService → 存储结果
- prompt 模板: 生成 3 段中文摘要 — (1)整体学习状态 (2)优势与关注点 (3)具体建议
- 开关: `@Value("${learner-profile.llm-summary.enabled:false}")`
- 测试: `LearnerProfileSummaryServiceTest.java` — mock OpenRouterService
- 工量: L
- 新建: `backend/src/main/resources/prompts/learner-profile-summary.txt`

**T-036 | LLM 摘要接入 DTO 和 API** ✅
- 依赖: T-035 | 阻塞: T-037, T-040
- 修改:
  - `LearnerProfileDto.java` — 新增 `naturalLanguageSummary` 字段（可选）
  - `LearnerProfileServiceImpl.java` — 画像构建后，若 LLM 开关开启则附加最新摘要
  - `LearnerProfileController.java` — 新增 `POST /api/admin/learner-profile/{userId}/generate-summary` 按需触发
- 工量: M

---

### V3-C: 学生端画像

**T-037 | 学生端画像 API + 前端** ✅
- 依赖: T-036 | 阻塞: T-041
- 新建:
  - `controller/StudentProfileController.java` — `GET /api/student/profile/me`, `GET /api/student/profile/me/summary`，`@PreAuthorize("hasAnyRole('STUDENT','USER')")`
  - `dto/learner/StudentFacingProfileDto.java` — 精选子集：身份、能力摘要（鼓励性措辞）、习惯亮点、学习建议、LLM 摘要。排除：风险等级、流失标记、原始比率。
  - `frontend/src/views/student/MyProfile.vue` — 学习进度环形图、活跃日热力图、优势徽章、"我的学习计划"（来自 LLM 摘要）
  - `frontend/src/api/learnerProfile.js` — 新增 `getMyProfile()`, `getMyProfileSummary()`
- 修改: `frontend/src/router/index.js` — 新增 student 路由 `my-profile`，`meta.featureKey: 'student.learner_profile_enabled'`
- 测试: `StudentProfileControllerTest.java` — 仅能看自己、风险数据不泄漏、未认证 401
- 工量: L

---

### V3-D: 高级标签引擎

**T-038 | 趋势标签** ✅ 已完成
- 依赖: T-026 (快照) | 阻塞: T-040
- **提前到 V1 实现**: 趋势标签已作为 DB 驱动规则实现（V38 种子数据），不再依赖枚举硬编码
- 实际实现的趋势标签:
  - `IMPROVING_TREND` (进步趋势): `trendRatio >= 1.3 AND oldActiveDays14d > 0`
  - `DECLINING_TREND` (下滑趋势): `trendRatio <= 0.6 AND oldActiveDays14d > 0`
  - `REVIEW_CHAMPION` (复习达人): 连续 3 次快照 `overdueRatio < 0.05`
- 指标由 `LearnerProfileServiceImpl.buildMetricsMap()` 从快照数据中提取
- 条件: 无快照时优雅跳过（指标为 null → 条件不满足）
- 完成于: 2026-05-27

**T-039 | 组合标签引擎** ✅ 已完成
- 依赖: T-034, T-009 | 阻塞: T-040
- **提前到 V1 实现**: `CompositeTagEngine` 已全面实现，支持 metric 阈值条件 + tag 引用条件
- 实际实现:
  - `entity/LearnerCompositeTag.java`
  - `mapper/LearnerCompositeTagMapper.java` — `selectAllEnabled()`, `selectAllOrdered()`, `countByCode()`, `countByCodeExcluding()`
  - `service/CompositeTagEngine.java` + `impl/CompositeTagEngineImpl.java`
- 核心方法: `evaluateAll(Map<String, Double> metrics)` — 两遍求值（Pass 1 纯 metric 标签, Pass 2 组合标签）
- 规则格式: `{"conditions": [{"source":"metric","field":"...","operator":"GTE","value":N}], "combiner": "AND|OR"}`
- 扩展: `LearnerProfileController.java` 新增 Admin CRUD 6 个端点管理标签定义
- 前端: `TagManagement.vue` — 可视化规则编辑器 + 标签列表管理
- 测试: `LearnerProfileV3Test.java` — metric LT/GTE、AND 组合、两遍求值
- 完成于: 2026-05-27

---

### V3-E: 班级队列分析

**T-040 | 班级队列分析服务 + API** ✅
- 依赖: T-026, T-035, T-038, T-039 | 阻塞: T-041
- 新建:
  - `dto/learner/ClassCohortAnalysisDto.java` — 含 riskDistribution, tagHitDistribution, avgMetrics, topRiskStudents, trendComparison, llmCohortSummary, subgroups
  - `dto/learner/CohortSubgroupDto.java` — 子群名称、学生数、均值指标、建议动作
  - `service/ClassCohortAnalysisService.java` + `impl/ClassCohortAnalysisServiceImpl.java`
- 功能: 批量生成班级全员画像 → 按标签组合自动分群 → 计算班级聚合指标 → 可选 LLM 队列摘要 → 多班级对比
- 修改: `LearnerProfileController.java` — 新增:
  - `GET /api/admin/learner-profile/class/{classId}/cohort-analysis`
  - `GET /api/admin/learner-profile/class-comparison?classIds=1,2,3`
- 测试: `ClassCohortAnalysisServiceTest.java`
- 工量: L

**T-041 | 班级队列分析前端** ✅
- 依赖: T-040, T-037 | 阻塞: 无
- 新建: `frontend/src/views/admin/ClassCohortAnalysis.vue`
  - 班级选择器
  - 风险分布饼图 + 标签命中柱状图
  - 子群卡片（群名、人数、关键指标、建议动作）
  - 14 天趋势对比
  - LLM 队列摘要文本块
  - "班级对比"模式：雷达图对比 2-3 个班级
- 修改: `frontend/src/router/index.js` — 新增 admin 路由 `class-cohort`
- 修改: `Classes.vue` — 班级行增加"画像分析"跳转按钮
- 工量: L

---

### V3-F: 测试与收尾

**T-042 | V3 Docker init 同步** ✅
- 依赖: T-033 | 阻塞: 无
- 修改: `docker/mysql/init/04-apply-all-migrations.sql` — 追加 V35（V36/V38 已在 V1 阶段提前同步）
- 工量: S

**T-043 | V3 端到端集成测试** ✅
- 依赖: 全部 V3 | 阻塞: 无
- 新建: `LearnerProfileV3IntegrationTest.java`
- 工量: M

**T-044 | 全量配置 + 文档更新** ✅
- 依赖: 全部 | 阻塞: 无
- 修改:
  - `application.yml` — V3 配置: `learner-profile.llm-summary.*`
  - `docs/LEARNER_PERSONA_V1_MASTER_SPEC.md` — Section 18 重写: V2/V3 实施细节（数据库表、服务方法签名、API 端点、DTO、前端文件、Feature Toggle、测试覆盖、实施偏差表）
- 工量: S
- 完成于: 2026-05-28

---

## 依赖关系图

```
✅ = 已完成 | ⚠️ = 部分完成 | ⏭️ = 跳过 | ❌ = 未完成

V1-A: 数据缺口 + 版本追踪 — 全部完成
✅ T-001 (活跃天数)     ─┐
✅ T-002 (学习时长)     ─┤
✅ T-003 (逾期复习)     ─┼─→ ✅ T-008 (ServiceImpl + CompositeTagEngine)
✅ T-004 (版本追踪后端) ─┤         │
✅ T-004b(版本追踪前端) ─┘         │
                                   ├─→ ✅ T-009 (9标签) ─→ ✅ T-013 (重排) ─→ ✅ T-014 (拆分)
✅ T-005 (DTOs) ─────────→ T-008   │
✅ T-006 (枚举) ─────────→ T-008   │
✅ T-007 (接口) ─────────→ T-008   │
✅ T-010 (缓存配置) ─────→ T-008   │
                                   ├─→ ✅ T-011 (Admin Ctrl+CRUD) ─→ ✅ T-015 (API.js) ─→ ✅ T-016 (组件)
                                   └─→ ✅ T-012 (Teacher Ctrl) ─────→ ✅ T-015           ├→ ✅ T-017 (教师端)
                                                                                          ├→ ✅ T-018 (管理端)
                                                                                          └→ ✅ TagManagement.vue
                                                              ✅ T-019 (V1集成测试) / ✅ T-020 (Docker)

V1 提前完成的 V3 任务:
  ✅ T-034 (V36组合表) → ✅ T-039 (组合引擎) → 已集成到 T-008/T-009/T-011
  ✅ T-038 (趋势标签) → 已作为 V38 种子数据的一部分

V2: 行为反馈 — 全部完成
✅ T-021 (V32事件表) ──→ ✅ T-024 (追踪服务) ──→ ✅ T-025 (埋点) ──→ ✅ T-029 (分析API) ──→ ✅ T-030 (分析前端)
✅ T-022 (V33快照表) ──→ ✅ T-026 (快照服务) ──→ ✅ T-027 (校准) ──→ ✅ T-028 (校准API) ─→ ✅ T-029
✅ T-023 (V34校准表) ──→ ✅ T-027              ✅ T-031 (Docker) / ✅ T-032 (V2集成测试)

V3: AI增强 — 全部完成
✅ T-033 (V35摘要表) ──→ ✅ T-035 (LLM服务) ──→ ✅ T-036 (摘要API) ──→ ✅ T-037 (学生端)
✅ T-026 (快照) ──→ ✅ T-040 (队列分析) ──→ ✅ T-041 (队列前端)
                  ✅ T-042 (Docker V35) / ✅ T-043 (V3集成测试) / ✅ T-044 (配置文档)
```

## 工量汇总

| Phase | 任务数 | ✅ 已完成 | ⚠️ 部分完成 | ⏭️ 跳过 | ❌ 未完成 | 完成率 |
|-------|--------|----------|------------|---------|----------|--------|
| V1 | 21 | 21 | 0 | 0 | 0 | 100% |
| V2 | 12 | 12 | 0 | 0 | 0 | 100% |
| V3 | 12 | 12 | 0 | 0 | 0 | 100% |
| **合计** | **45** | **45** | **0** | **0** | **0** | **100%** |

### 剩余工作

无。全部 45 个任务已完成。

> **注**: T-035 的 prompt 模板内联在 Java 代码中（非外部文件），功能等价，不视为遗留项。

## 回退安全矩阵

| 场景 | 切换开关 | 效果 | 数据风险 |
|------|---------|------|---------|
| V1 画像异常 | `learner-profile.enabled: false` | 画像 API 返回不可用 | 无（画像按需计算，V31 列仅是追踪数据） |
| 版本追踪异常 | 无需开关 | `content_version` 列为 NULL → 画像退回无版本数据模式 | 无（graceful degradation） |
| 推荐干扰 | `recommendation.persona.enabled: false` | 重排禁用，纯 CF | 无 |
| Teacher 画像异常 | `teacher.learner_profile_enabled: false` (DB) | 教师端返回错误 | 无 |
| 标签管理异常 | `admin.tag_management: false` (DB) | 管理页面隐藏，标签引擎仍正常运行 | 无 |
| V2 追踪造成负载 | `learner-profile.tracking.enabled: false` | 事件记录停止 | 已有事件保留 |
| V2 快照太慢 | `learner-profile.snapshot.enabled: false` | 调度器停止 | 已有快照保留 |
| V3 LLM 成本过高 | `learner-profile.llm-summary.enabled: false` | 不再调 LLM | 已有摘要保留 |
| V3 学生端投诉 | `student.learner_profile_enabled: false` (DB) | 学生路由隐藏 | 无 |

## 验证方式

1. **V1 验证**: 后端 `mvn test` 覆盖 T-004 版本追踪 + T-009 标签边界 + T-011/T-012 控制器 + T-013 推荐回归；前端手动验证: InteractiveText.vue 版本切换后 Network 面板可见 contentVersion 参数 + Teacher 抽屉和 Admin 摘要中版本分布正确展示
2. **V2 验证**: 事件追踪 → 快照（含版本分布）→ 校准提案全链路自动化测试；分析看板手动验证图表
3. **V3 验证**: mock LLM 的摘要生成 + 趋势标签 + 组合标签自动测试；学生端和队列分析手动验证 UI

---

**最后更新**: 2026-05-28
