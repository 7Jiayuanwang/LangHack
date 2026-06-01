# LangHack 学习者画像 V1 主规范

> 状态: 当前单一事实源
> 基线: `feat/learner-persona-design` 已 rebase 到 `origin/develop@c9b41a7` 之后编写
> 目标: 将学习者画像的设计、任务、契约与代码级改造收敛为一份后续开发直接遵循的主规范。

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [设计原则](#2-设计原则)
3. [V1 范围](#3-v1-范围)
4. [五层画像模型](#4-五层画像模型)
5. [标签字典](#5-标签字典)
6. [DTO 契约](#6-dto-契约)
7. [API 契约](#7-api-契约)
8. [后端实施方案](#8-后端实施方案)
9. [TeacherDataController 代码级改造](#9-teacherdatacontroller-代码级改造)
10. [RecommendationServiceImpl 代码级改造](#10-recommendationserviceimpl-代码级改造)
11. [前端实施方案](#11-前端实施方案)
12. [配置方案](#12-配置方案)
13. [测试方案](#13-测试方案)
14. [任务拆解与开发顺序](#14-任务拆解与开发顺序)
15. [缓存与灰度方案](#15-缓存与灰度方案)
16. [验收标准](#16-验收标准)
17. [风险与回退](#17-风险与回退)
18. [后续扩展](#18-后续扩展)

---

## 1. 背景与目标

LangHack 已具备学习者画像 V1 所需的核心数据基础：

- 学习行为：`learning_records`
- 词汇掌握：`vocabulary_books`
- 阅读偏好：`user_text_reads`
- 推荐系统：`RecommendationServiceImpl`
- 教师学生关系：`TeacherDataController` + `teacher_student_binding`

本方案借鉴两个外部项目的核心方法：

1. **《构建用户画像》**的"三阶段蒸馏"：定义标签体系 -> 基于证据打标 -> 汇总生成画像
2. **《AI赋能云平台》**的"五层 Persona"结构 + "画像反哺策略"设计原则

V1 目标不是建设一个完整画像平台，而是优先落地三件事：

1. 生成**可信、可追溯**的学习者画像
2. 让教师/管理端能**查看画像与风险标签**
3. 让推荐系统在**可回退前提下**接入画像重排

### 非目标

V1 明确不做：
- 画像大宽表 / 离线数仓
- 微服务拆分
- 学生端画像展示
- LLM 自动总结
- 实时事件流画像
- Mongo 文档画像仓库
- 复杂机器学习分群

---

## 2. 设计原则

### 2.1 借鉴方法，不照搬架构

外部项目的微服务拆分、Mongo/VOC 管道、客服策略映射，不直接照搬。LangHack 采用**单体内聚、规则优先、逐步增强**的实现路线。

### 2.2 先规则画像，后智能画像

V1 优先利用现有结构化数据做规则打分和标签归纳，先解决"可用"和"可落地"。后续再引入 AI 总结、学习风格摘要和自然语言解释。

### 2.3 画像必须可追溯

每个标签、分层、风险结论，都应能追溯到明确证据：最近 14 天学习时长、连续活跃天数、词汇掌握分布、文章阅读偏好等。

### 2.4 画像必须服务动作

画像不是展示面板，而是策略输入。每个画像结果至少映射到一个系统动作：推荐排序调整、复习频率调整、教师提醒、学生提醒。

### 2.5 V1 实施策略

采用 **"按需计算 + 短 TTL 缓存 + 可选重排"** 路线：
- 画像先不落库，按请求实时聚合
- 聚合结果按用户短期缓存
- 推荐系统只在候选排序阶段读取画像结果
- 所有画像相关能力都能通过配置关闭

---

## 3. V1 范围

### 功能交付

- 9 个 DB 驱动标签（全量可管理化，管理员可增删改查）
- 标签规则引擎 `CompositeTagEngine`：支持 metric 阈值条件 + tag 引用条件，两遍求值
- 单用户画像接口
- 班级画像摘要接口
- 教师端单学生画像查看接口
- Teacher/Admin 端画像展示入口
- Admin 端标签管理页面（CRUD + 启用/禁用）
- 推荐系统轻量重排
- 配置开关、缓存、日志、测试

### 画像生成策略

采用：
- **按需计算**
- **短 TTL 缓存**
- **推荐端轻量重排**

不在 V1 先落库快照；若性能不够，再补缓存或快照表。

---

## 4. 五层画像模型

适配学习场景的五层 Persona 结构。

### Layer 0: 基础身份层

回答"这个学习者是谁"。

候选字段：用户角色、注册时长、班级/课程归属、是否接入学校数据、年级/专业

用途：区分校内学生、泛用户、教师；作为推荐和统计口径的过滤维度

### Layer 1: 学习能力层

回答"这个学习者目前学得怎么样"。

候选指标：词汇掌握分布 (`NEW/LEARNING/MASTERED`)、单词复习成功率、文章阅读完成率、作业正确率

示例标签：`词汇基础薄弱`、`阅读理解稳定`

### Layer 2: 学习习惯层

回答"这个学习者是怎么学的"。

候选指标：最近 7/14/30 天学习频率、单次学习时长中位数、学习活跃时段、复习是否按时完成

示例标签：`近14天高活跃`、`复习拖延型`

### Layer 3: 兴趣偏好层

回答"这个学习者更愿意学什么"。

候选指标：高频阅读主题、常点击文章难度、收藏/完成率较高的内容类型

示例标签：`中等难度偏好`

### Layer 4: 风险与潜力层

回答"这个学习者是否需要被干预"。

候选指标：活跃度下降斜率、连续未学习天数、已加入生词本但长期未复习占比

示例标签：`流失风险`、`复习积压`

---

## 5. 标签字典

> **架构变更（2026-05-27）**：标签系统已从硬编码枚举 (`LearnerTagCode`) 全面迁移到 DB 驱动规则引擎。所有标签定义存储在 `learner_composite_tags` 表中，管理员可通过 Admin 端标签管理页面进行 CRUD 操作。标签评估由 `CompositeTagEngine.evaluateAll(Map<String, Double> metrics)` 统一执行。

### 5.1 标签架构

- **存储**: `learner_composite_tags` 表（Flyway V36 建表，V38 种子数据）
- **引擎**: `CompositeTagEngineImpl` — 两遍求值：Pass 1 求值纯 metric 条件标签，Pass 2 求值引用其他标签的组合标签
- **指标来源**: `LearnerProfileServiceImpl.buildMetricsMap()` 生成 16 个 `Map<String, Double>` 指标
- **管理**: Admin 端 `/admin/tag-management` 页面 + CRUD API
- **缓存**: 标签定义变更时自动清除 `learner-profile:user` 和 `learner-profile:class` 缓存

### 5.2 规则定义格式

```json
{
  "conditions": [
    {"source": "metric", "field": "activeDays14d", "operator": "LT", "value": 3},
    {"source": "metric", "field": "studyMinutes14d", "operator": "GT", "value": 0}
  ],
  "combiner": "AND"
}
```

**条件类型**:
- `source: "metric"` — 比较指标数值。操作符: `LT`, `LTE`, `GT`, `GTE`, `EQ`（EQ 使用 0.0001 epsilon）
- `source: "tag"` — 引用其他标签的匹配状态。操作符: `MATCHED`, `NOT_MATCHED`

**组合方式**: `AND`（全部满足）或 `OR`（任一满足）

### 5.3 标签总表（9 个基础标签）

| code | displayName | tagType | priority | 规则条件 | actionHint |
|------|-------------|---------|----------|----------|------------|
| `INACTIVE_RISK` | 流失风险 | risk | 10 | `activeDays14d < 3 AND studyMinutes14d > 0` | 建议增加学习提醒频率 |
| `VOCAB_WEAK` | 词汇弱势 | risk | 20 | `totalWords >= 30 AND weakRatio >= 0.6` | 建议加强词汇复习 |
| `REVIEW_OVERDUE` | 复习逾期 | risk | 30 | `dueReviewCount > 0 AND overdueReviewRatio >= 0.5` | 请尽快完成待复习词汇 |
| `CONSISTENT_LEARNER` | 学习稳定 | positive | 40 | `activeDays7d >= 5` | 保持良好的学习节奏 |
| `CONTENT_LEVEL_STUCK` | 版本停滞 | ability | 50 | `textsRead >= 40 AND versionDistSize = 1` | 建议尝试更高难度版本 |
| `CONTENT_LEVEL_JUMP` | 版本跳跃 | ability | 60 | `activeDays7d >= 0`（默认禁用） | 注意难度跨度是否过大 |
| `IMPROVING_TREND` | 进步趋势 | positive | 70 | `oldActiveDays14d > 0 AND trendRatio >= 1.3` | 学习活跃度明显提升 |
| `DECLINING_TREND` | 下滑趋势 | risk | 80 | `oldActiveDays14d > 0 AND trendRatio <= 0.6` | 学习活跃度出现下降 |
| `REVIEW_CHAMPION` | 复习达人 | positive | 90 | `snap0OverdueRatio < 0.05 AND snap1OverdueRatio < 0.05 AND snap2OverdueRatio < 0.05` | 复习习惯非常好 |

### 5.4 指标字典（16 个预计算指标）

| 指标 key | 中文名 | 数据来源 |
|----------|--------|----------|
| `activeDays7d` | 近7天活跃天数 | learning_records |
| `activeDays14d` | 近14天活跃天数 | learning_records |
| `studyMinutes14d` | 近14天学习分钟数 | learning_records |
| `consecutiveDays` | 连续学习天数 | learning_records |
| `overdueReviewRatio` | 逾期复习占比 | vocabulary_books |
| `overdueReviewCount` | 逾期复习数量 | vocabulary_books |
| `dueReviewCount` | 应复习总量 | vocabulary_books |
| `totalWords` | 总词汇量 | vocabulary_books |
| `weakRatio` | 词汇弱势率 | vocabulary_books |
| `textsRead` | 已读文章数 | text_learning_records |
| `versionDistSize` | 阅读版本种类数 | text_learning_records |
| `trendRatio` | 活跃趋势比 | learner_profile_snapshots |
| `oldActiveDays14d` | 前14天活跃天数 | learner_profile_snapshots |
| `snap0OverdueRatio` | 最近快照逾期率 | learner_profile_snapshots |
| `snap1OverdueRatio` | 前1次快照逾期率 | learner_profile_snapshots |
| `snap2OverdueRatio` | 前2次快照逾期率 | learner_profile_snapshots |

### 5.5 标签合理性设计要点

- **INACTIVE_RISK**: 附加 `studyMinutes14d > 0` 条件，避免新注册零活跃用户被误标
- **VOCAB_WEAK**: 要求 `totalWords >= 30`，避免刚开始学习的用户因 weakRatio=1.0 被误标
- **CONTENT_LEVEL_STUCK**: 阈值 `textsRead >= 40`，避免只学 CET-4 的用户被过早标记
- **CONTENT_LEVEL_JUMP**: 默认禁用 (`enabled=0`)，规则占位，待后续细化
- **趋势标签**: 依赖快照数据，无快照时优雅跳过（指标为 null → 条件不满足）
- **REVIEW_CHAMPION**: 需连续 3 次快照逾期率 < 5%，防止偶然达标

### 5.6 标签可扩展性

管理员可通过 Admin 端标签管理页面：
- **新增标签**: 自定义 code、名称、类型、优先级、规则条件
- **编辑标签**: 调整阈值、修改规则组合方式
- **启用/禁用**: 不删除定义，仅控制是否参与评估
- **删除标签**: 彻底移除

新增标签类型支持: `risk`（风险）、`positive`（正面）、`ability`（能力）、`habit`（习惯）、`preference`（偏好）

---

## 6. DTO 契约

### 6.1 统一约定

**响应包裹**：所有接口统一使用 `com.english.dto.Result<T>`

**字段命名**：JSON 使用 `camelCase`，DTO 字段命名与 JSON 保持一致

**V1 枚举值**：

| 枚举 | 值 |
|------|-----|
| `profileStatus` | `READY` / `PARTIAL` / `EMPTY` |
| `tagType` | `activity` / `ability` / `preference` / `habit` / `risk` / `positive` |
| `riskLevel` | `LOW` / `MEDIUM` / `HIGH` |
| `difficulty` | `EASY` / `MEDIUM` / `HARD` / `UNKNOWN` |

### 6.2 DTO 包结构

目录：`backend/src/main/java/com/english/dto/learner/`

类列表：
- `LearnerProfileDto`
- `LearnerProfileIdentityDto`
- `LearnerProfileAbilityDto`
- `LearnerProfileHabitDto`
- `LearnerProfilePreferenceDto`
- `LearnerProfileRiskDto`
- `LearnerProfileTagDto`
- `LearnerProfileEvidenceDto`
- `LearnerProfileClassSummaryDto`

### 6.3 `LearnerProfileDto`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | 平台用户 ID |
| `profileStatus` | `String` | 是 | `READY/PARTIAL/EMPTY` |
| `generatedAt` | `String` | 是 | ISO-8601 时间 |
| `identity` | `LearnerProfileIdentityDto` | 是 | 身份层 |
| `ability` | `LearnerProfileAbilityDto` | 是 | 能力层 |
| `habit` | `LearnerProfileHabitDto` | 是 | 习惯层 |
| `preference` | `LearnerProfilePreferenceDto` | 是 | 偏好层 |
| `risk` | `LearnerProfileRiskDto` | 是 | 风险层 |
| `tags` | `List<LearnerProfileTagDto>` | 是 | 命中标签列表，按 `priority desc` 排序 |
| `evidenceSummary` | `Map<String,Object>` | 否 | 前端快速展示用摘要 |
| `actionHints` | `List<String>` | 否 | 聚合后的建议动作 |

### 6.4 `LearnerProfileIdentityDto`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `role` | `String` | 是 | 如 `STUDENT` |
| `memberDays` | `Integer` | 是 | 注册天数 |
| `classId` | `Long` | 否 | 班级 ID |
| `className` | `String` | 否 | 班级名称 |
| `grade` | `String` | 否 | 年级 |
| `collegeName` | `String` | 否 | 学院 |

### 6.5 `LearnerProfileAbilityDto`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `newCount` | `Integer` | 是 | NEW 词条数 |
| `learningCount` | `Integer` | 是 | LEARNING 词条数 |
| `masteredCount` | `Integer` | 是 | MASTERED 词条数 |
| `weakRatio` | `Double` | 是 | `(new+learning)/total` |
| `readingCompletionRate` | `Double` | 否 | 阅读完成率 |
| `exerciseAccuracyRate` | `Double` | 否 | 练习正确率 |

### 6.6 `LearnerProfileHabitDto`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `activeDays7d` | `Integer` | 是 | 近 7 天活跃天数 |
| `activeDays14d` | `Integer` | 是 | 近 14 天活跃天数 |
| `studyMinutes14d` | `Integer` | 是 | 近 14 天学习分钟数 |
| `inactiveDays` | `Integer` | 是 | 连续未学习天数 |
| `topStudyPeriod` | `String` | 否 | 活跃时段，如 `20:00-22:00` |
| `overdueReviewCount` | `Integer` | 是 | 已逾期未复习数量 |
| `dueReviewCount` | `Integer` | 是 | 应复习总量 |
| `overdueReviewRatio` | `Double` | 是 | 逾期占比 |

### 6.7 `LearnerProfilePreferenceDto`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `preferredDifficulty` | `String` | 是 | `EASY/MEDIUM/HARD/UNKNOWN` |
| `easyReadRatio` | `Double` | 否 | 简单难度阅读占比 |
| `mediumReadRatio` | `Double` | 否 | 中等难度阅读占比 |
| `hardReadRatio` | `Double` | 否 | 高难度阅读占比 |
| `favoriteTopics` | `List<String>` | 否 | 高频主题列表 |

### 6.8 `LearnerProfileRiskDto`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `riskLevel` | `String` | 是 | `LOW/MEDIUM/HIGH` |
| `churnRisk` | `Boolean` | 是 | 是否命中流失风险 |
| `reviewBacklogRisk` | `Boolean` | 是 | 是否存在复习积压 |
| `inactiveDays` | `Integer` | 是 | 连续未学习天数 |
| `activityDropRatio` | `Double` | 否 | 活跃下降比例 |

### 6.9 `LearnerProfileTagDto`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `code` | `String` | 是 | 稳定英文标签 code |
| `displayName` | `String` | 是 | 中文展示名 |
| `tagType` | `String` | 是 | `activity/ability/preference/habit/risk` |
| `priority` | `Integer` | 是 | 越大越重要 |
| `matched` | `Boolean` | 是 | 当前是否命中 |
| `score` | `Double` | 否 | 规则强度分数 0~1 |
| `actionHint` | `String` | 否 | 动作建议 |
| `evidence` | `LearnerProfileEvidenceDto` | 否 | 标签证据 |

### 6.10 `LearnerProfileEvidenceDto`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `source` | `String` | 是 | 数据来源，如 `learning_records` |
| `window` | `String` | 否 | 时间窗或统计窗口 |
| `summary` | `String` | 否 | 人类可读摘要 |
| `metrics` | `Map<String,Object>` | 是 | 证据指标明细 |

### 6.11 `LearnerProfileClassSummaryDto`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | `Long` | 是 | 用户 ID |
| `studentName` | `String` | 是 | 学生姓名 |
| `classId` | `Long` | 是 | 班级 ID |
| `className` | `String` | 否 | 班级名称 |
| `riskLevel` | `String` | 是 | `LOW/MEDIUM/HIGH` |
| `topTagCodes` | `List<String>` | 是 | 高优先级标签 code 列表 |
| `topTagDisplayNames` | `List<String>` | 是 | 中文标签名列表 |
| `activeDays14d` | `Integer` | 是 | 近14天活跃天数 |
| `inactiveDays` | `Integer` | 是 | 连续未学习天数 |
| `preferredDifficulty` | `String` | 否 | 偏好难度 |
| `overdueReviewRatio` | `Double` | 否 | 复习逾期占比 |
| `actionHints` | `List<String>` | 否 | 建议动作 |

### 6.12 Java DTO 最小骨架

```java
public class LearnerProfileDto {
    private Long userId;
    private String profileStatus;
    private String generatedAt;
    private LearnerProfileIdentityDto identity;
    private LearnerProfileAbilityDto ability;
    private LearnerProfileHabitDto habit;
    private LearnerProfilePreferenceDto preference;
    private LearnerProfileRiskDto risk;
    private List<LearnerProfileTagDto> tags;
    private Map<String, Object> evidenceSummary;
    private List<String> actionHints;
}
```

```java
public class LearnerProfileTagDto {
    private String code;
    private String displayName;
    private String tagType;
    private Integer priority;
    private Boolean matched;
    private Double score;
    private String actionHint;
    private LearnerProfileEvidenceDto evidence;
}
```

### 6.13 JSON 响应示例

#### 单用户画像

```json
{
  "userId": 10086,
  "profileStatus": "READY",
  "generatedAt": "2026-05-27T21:30:00",
  "identity": {
    "role": "STUDENT",
    "memberDays": 132,
    "classId": 12,
    "className": "英语1班",
    "grade": "2023",
    "collegeName": "外国语学院"
  },
  "ability": {
    "newCount": 42,
    "learningCount": 118,
    "masteredCount": 76,
    "weakRatio": 0.68,
    "readingCompletionRate": 0.71,
    "exerciseAccuracyRate": 0.83
  },
  "habit": {
    "activeDays7d": 4,
    "activeDays14d": 9,
    "studyMinutes14d": 286,
    "inactiveDays": 0,
    "topStudyPeriod": "20:00-22:00",
    "overdueReviewCount": 11,
    "dueReviewCount": 29,
    "overdueReviewRatio": 0.38
  },
  "preference": {
    "preferredDifficulty": "MEDIUM",
    "easyReadRatio": 0.21,
    "mediumReadRatio": 0.55,
    "hardReadRatio": 0.24,
    "favoriteTopics": ["technology", "campus"]
  },
  "risk": {
    "riskLevel": "MEDIUM",
    "churnRisk": true,
    "reviewBacklogRisk": true,
    "inactiveDays": 0,
    "activityDropRatio": 0.2
  },
  "tags": [],
  "evidenceSummary": {
    "activeDays14d": 9,
    "weakRatio": 0.68,
    "preferredDifficulty": "MEDIUM",
    "overdueReviewRatio": 0.38
  },
  "actionHints": [
    "优先推荐中等难度文章",
    "加强复习提醒"
  ]
}
```

#### 班级画像摘要

```json
{
  "userId": 10086,
  "studentName": "张三",
  "classId": 12,
  "className": "英语1班",
  "riskLevel": "HIGH",
  "topTagCodes": ["INACTIVE_RISK", "DECLINING_TREND"],
  "topTagDisplayNames": ["流失风险", "下滑趋势"],
  "activeDays14d": 1,
  "inactiveDays": 9,
  "preferredDifficulty": "MEDIUM",
  "overdueReviewRatio": 0.38,
  "actionHints": ["建议教师主动跟进", "建议优先触发学习提醒"]
}
```

### 6.14 V1 DTO 约束

1. **不返回敏感原始数据**：不返回完整学习记录明细，不返回考试成绩个人原始明细给班级摘要接口
2. **标签必须可追溯**：每个标签都必须带 evidence
3. **允许部分画像**：数据不足时使用 `PARTIAL`，不要抛异常
4. **优先返回稳定字段**：V1 不引入频繁变动的字段命名
5. **推荐重排只依赖 code，不依赖中文 displayName**

---

## 7. API 契约

### 7.1 Admin / OPS 单用户画像

```http
GET /api/admin/learner-profile/{userId}
```

- 权限：`@PreAuthorize("hasAnyRole('ADMIN','OPS')")`
- Path 参数：`userId (Long, 必填)`
- 返回：`Result<LearnerProfileDto>`

| code | message | 说明 |
|------|---------|------|
| `200` | `操作成功` | 正常返回 |
| `400` | `userId 不能为空` | 参数错误 |
| `404` | `用户不存在` | 用户不存在 |
| `500` | `获取学习者画像失败，请稍后重试` | 服务异常 |

### 7.2 Admin / OPS 班级画像摘要

```http
GET /api/admin/learner-profile/class/{classId}
```

- 权限：`@PreAuthorize("hasAnyRole('ADMIN','OPS')")`
- Path 参数：`classId (Long, 必填)`
- 返回：`Result<List<LearnerProfileClassSummaryDto>>`

| code | message | 说明 |
|------|---------|------|
| `200` | `操作成功` | 正常返回 |
| `400` | `classId 不能为空` | 参数错误 |
| `404` | `班级不存在` | 班级不存在 |
| `500` | `获取班级画像摘要失败，请稍后重试` | 服务异常 |

### 7.3 Teacher 单学生画像

```http
GET /api/teacher/student/{studentId}/profile
```

- 权限：`@PreAuthorize("hasAnyRole('TEACHER')")`
- Path 参数：`studentId (String, 必填)` — 学校学生学号
- 返回：`Result<LearnerProfileDto>`

处理规则：
1. 先根据教师身份查 `teacher_student_binding`
2. 若不存在绑定关系，直接拒绝
3. 若存在绑定关系，再映射到平台用户并生成画像
4. 若学校学生已同步但未绑定平台用户，返回友好提示

| code | message | 说明 |
|------|---------|------|
| `200` | `操作成功` | 正常返回 |
| `403` | `无权查看该学生画像` | 教师未绑定该学生 |
| `404` | `未找到该学生对应的平台用户` | 学校学生存在，但平台侧未建立映射 |
| `500` | `获取学生画像失败，请稍后重试` | 服务异常 |

### 7.4 Admin 标签管理 CRUD

```http
GET    /api/admin/learner-profile/tag-definitions
POST   /api/admin/learner-profile/tag-definitions
PUT    /api/admin/learner-profile/tag-definitions/{id}
DELETE /api/admin/learner-profile/tag-definitions/{id}
PATCH  /api/admin/learner-profile/tag-definitions/{id}/enabled
GET    /api/admin/learner-profile/tag-definitions/metrics
```

- 权限：`@PreAuthorize("hasAnyRole('ADMIN','OPS')")`
- `GET /tag-definitions` — 返回所有标签定义（含禁用的），按 priority 排序
- `POST /tag-definitions` — 创建新标签，校验 code 唯一性 + ruleDefinition JSON 格式
- `PUT /tag-definitions/{id}` — 更新标签（code 不可修改），校验同上
- `DELETE /tag-definitions/{id}` — 删除标签定义
- `PATCH /tag-definitions/{id}/enabled` — 切换启用状态，请求体: `{"enabled": true/false}`
- `GET /tag-definitions/metrics` — 返回可用指标字典 `Map<String, String>`（key→中文名，16 个指标）

所有写操作成功后自动清除 `learner-profile:user` 和 `learner-profile:class` 缓存。

### 7.5 前端联调契约

**Teacher 端**：`frontend/src/views/teacher/MyStudents.vue` 调用 `getTeacherStudentProfile(studentId)`

期望 UI 字段：头像/姓名/班级/学院/年级、风险等级、标签列表、活跃度/掌握度/复习积压摘要、建议动作

**Admin 端**：`frontend/src/views/admin/Classes.vue` 调用 `getClassLearnerProfileSummary(classId)`

期望 UI 字段：风险名单、学生活跃概况、高优先级标签、复习拖延情况

---

## 8. 后端实施方案

### 8.1 新增代码结构

| 类型 | 文件 |
|------|------|
| Service 接口 | `backend/src/main/java/com/english/service/LearnerProfileService.java` |
| Service 实现 | `backend/src/main/java/com/english/service/impl/LearnerProfileServiceImpl.java` |
| 标签引擎接口 | `backend/src/main/java/com/english/service/CompositeTagEngine.java` |
| 标签引擎实现 | `backend/src/main/java/com/english/service/impl/CompositeTagEngineImpl.java` |
| Controller | `backend/src/main/java/com/english/controller/LearnerProfileController.java` |
| DTO | `backend/src/main/java/com/english/dto/learner/*` |
| 标签实体 | `backend/src/main/java/com/english/entity/LearnerCompositeTag.java` |
| 标签 Mapper | `backend/src/main/java/com/english/mapper/LearnerCompositeTagMapper.java` |

### 8.2 改造文件

| 文件 | 改造内容 |
|------|----------|
| `TeacherDataController.java` | 新增教师侧画像接口 |
| `RecommendationServiceImpl.java` | 新增画像重排 |
| `application.yml` | 新增配置项 |

### 8.3 复用现有类

- `LearningRecordsServiceImpl.java` — 学习记录查询
- `RecommendationServiceImpl.java` — 推荐主链路
- `TeacherDataController.java` — 教师身份、绑定校验
- `LearningRecord.java`, `VocabularyBook.java`, `User.java` — 实体
- `UserTextReadMapper.java` — 阅读偏好
- `SchoolStudentMapper.java` — 学校学生信息

### 8.4 Service 接口设计

```java
public interface LearnerProfileService {
    LearnerProfileDto getUserProfile(Long userId);
    List<LearnerProfileClassSummaryDto> getClassProfileSummary(Long classId);
}
```

### 8.5 ServiceImpl 内部方法拆分

```java
private IdentitySection buildIdentity(Long userId)
private AbilitySection buildAbility(Long userId)
private HabitSection buildHabit(Long userId)
private PreferenceSection buildPreference(Long userId)
private RiskSection buildRisk(Long userId, Set<String> tagCodes)
private Map<String, Double> buildMetricsMap(habit, ability, preference, userId)
private Map<String, Object> buildEvidenceSummary(...)
```

标签评估已从 `buildTags()` 迁移到 `CompositeTagEngine.evaluateAll(metricsMap)`。

### 8.6 CompositeTagEngine 架构

`CompositeTagEngine` 统一处理所有标签的评估，替代原来散落在 `LearnerProfileServiceImpl` 中的硬编码标签规则。

```java
public interface CompositeTagEngine {
    List<LearnerTagDto> evaluateCompositeTags(Set<String> baseTagCodes);
    List<LearnerTagDto> evaluateAll(Map<String, Double> metrics);
}
```

**`evaluateAll(metrics)` 两遍求值流程**:

1. 从 DB 读取所有 `enabled=1` 的标签定义
2. **Pass 1**: 评估所有纯 metric 条件标签（`source: "metric"` only），记录已匹配的 codes
3. **Pass 2**: 评估引用其他标签的组合标签（含 `source: "tag"` 条件），基于 Pass 1 的匹配结果
4. 去重（按 code），保留更高优先级（priority 数值更小），按 priority 升序排序

**`buildMetricsMap()` 指标计算**:

```java
Map<String, Double> buildMetricsMap(habit, ability, preference, userId) {
    // 基础指标: activeDays7d, activeDays14d, studyMinutes14d, consecutiveDays
    // 复习指标: overdueReviewRatio, overdueReviewCount, dueReviewCount
    // 词汇指标: totalWords, weakRatio
    // 阅读指标: textsRead, versionDistSize
    // 趋势指标: trendRatio, oldActiveDays14d (来自快照)
    // 快照逾期率: snap0/1/2OverdueRatio (来自近 3 次快照)
}
```

### 8.7 数据聚合逻辑

#### 身份层

数据来源：`users`, `student_class`, `school_student`

输出：`userId`, `role`, `memberDays`, `classId`, `className`, `grade`, `collegeName`

#### 能力层

数据来源：`vocabulary_books`, `learning_records`

输出：`masteryDistribution`, `readingCompletionRate`, `exerciseAccuracyRate`

#### 习惯层

数据来源：`learning_records`

输出：`activeDays7d`, `activeDays14d`, `studyMinutes14d`, `inactiveDays`, `topStudyPeriod`

#### 偏好层

数据来源：已读文章 + 文章难度字段

输出：`preferredDifficulty`, `favoriteTopics`

#### 风险层

数据来源：近7/14/28天学习趋势、到期未复习词条

输出：`churnRisk`, `reviewBacklogRisk`, `riskTags`

---

## 9. TeacherDataController 代码级改造

文件：`backend/src/main/java/com/english/controller/TeacherDataController.java`

### 9.1 改造目标

在教师已有的"学生查询"链路上增加画像接口，复用现有身份解析、绑定校验和开关机制。

### 9.2 新增注入

```java
@Autowired
private LearnerProfileService learnerProfileService;

private static final String TEACHER_LEARNER_PROFILE_TOGGLE = "teacher.learner_profile_enabled";
```

### 9.3 新增接口方法

```java
@GetMapping("/student/{studentId}/profile")
@PreAuthorize("hasAnyRole('TEACHER')")
public Result<LearnerProfileDto> getStudentProfile(
        @PathVariable("studentId") String studentId,
        Authentication authentication)
```

执行顺序：
1. 检查功能开关 `isTeacherLearnerProfileEnabled()`
2. 校验 `studentId` 非空
3. 解析当前教师 `teacherId`
4. 校验教师与学生是否存在绑定关系
5. 从 `studentId` 映射到平台 `User`
6. 调用 `learnerProfileService.getUserProfile(platformUserId)`
7. 返回 `Result.success(profile)`

### 9.4 新增辅助方法

#### 功能开关

```java
private boolean isTeacherLearnerProfileEnabled() {
    String value = systemConfigService.get("feature_toggle", TEACHER_LEARNER_PROFILE_TOGGLE, "true");
    return value == null || "true".equalsIgnoreCase(value) || "1".equals(value);
}
```

#### 绑定关系校验（从 `getStudentScores()` 中提取公用）

```java
private boolean hasTeacherStudentBinding(String teacherId, String studentId) {
    QueryWrapper<TeacherStudentBinding> wrapper = new QueryWrapper<>();
    wrapper.eq("teacher_id", teacherId)
           .eq("student_id", studentId)
           .last("LIMIT 1");
    return teacherStudentBindingMapper.selectOne(wrapper) != null;
}
```

#### 平台用户映射

```java
private User resolvePlatformUserByStudentId(String studentId) {
    return userService.getUserByUsername(studentId);
}
```

V1 采用最小映射策略：`User.username == 学号`（SSO 接入约定）。若后续不统一，再升级为专门映射表。

### 9.5 完整伪代码

```java
@GetMapping("/student/{studentId}/profile")
@PreAuthorize("hasAnyRole('TEACHER')")
public Result<LearnerProfileDto> getStudentProfile(
        @PathVariable("studentId") String studentId,
        Authentication authentication) {
    try {
        if (!isTeacherLearnerProfileEnabled()) {
            return Result.error("教师端学习者画像功能已关闭");
        }
        if (studentId == null || studentId.trim().isEmpty()) {
            return Result.error("studentId 不能为空");
        }

        String teacherId = getTeacherId(authentication);
        if (!hasTeacherStudentBinding(teacherId, studentId)) {
            return Result.error(403, "无权查看该学生画像");
        }

        User platformUser = resolvePlatformUserByStudentId(studentId);
        if (platformUser == null) {
            return Result.error(404, "未找到该学生对应的平台用户");
        }

        LearnerProfileDto profile = learnerProfileService.getUserProfile(platformUser.getId());
        return Result.success(profile);
    } catch (Exception e) {
        logger.error("获取教师学生画像失败 studentId={}", studentId, e);
        return Result.error("获取学生画像失败，请稍后重试");
    }
}
```

### 9.6 改造清单

**必改**：
- 新增 `LearnerProfileService` 注入
- 新增 `TEACHER_LEARNER_PROFILE_TOGGLE`
- 新增 `getStudentProfile(...)`
- 提取 `hasTeacherStudentBinding(...)`
- 让 `getStudentScores(...)` 复用绑定校验

**可选**：
- 新增 `resolvePlatformUserByStudentId(...)`
- 新增 `isTeacherLearnerProfileEnabled()`

**不做**：
- 不在 Controller 内自己计算画像
- 不在 Controller 内直接拼复杂统计 SQL
- 不把 Teacher 专用 DTO 另起一套

---

## 10. RecommendationServiceImpl 代码级改造

文件：`backend/src/main/java/com/english/service/impl/RecommendationServiceImpl.java`

### 10.1 改造目标

- 不改变 `recommend(Long userId, int count, Collection<Long> excludeIds)` 方法签名
- 不改变现有 Item-CF / User-CF 主链路
- 只在候选排序阶段叠加小幅 bonus / penalty
- 冷启动与 fallback 仍保持现有可用性

### 10.2 新增字段

```java
@Autowired
private LearnerProfileService learnerProfileService;

@Value("${recommendation.persona.enabled:false}")
private boolean personaEnabled;

@Value("${recommendation.persona.difficulty-bonus:0.08}")
private double difficultyBonus;

@Value("${recommendation.persona.easy-content-bonus:0.06}")
private double easyContentBonus;

@Value("${recommendation.persona.hard-content-penalty:0.08}")
private double hardContentPenalty;
```

### 10.3 PersonaSignals 轻量信号对象

不直接在推荐逻辑里读 `LearnerProfileDto`，提炼为轻量信号：

```java
private static class PersonaSignals {
    private String preferredDifficulty;
    private boolean vocabWeak;
    private boolean highActive;
    private boolean lowActive;
    private boolean reviewDelayed;
    private boolean churnRisk;
}
```

### 10.4 新增辅助方法

#### 加载画像信号

```java
private PersonaSignals loadPersonaSignals(Long userId) {
    if (!isPersonaEnabled()) return null;
    LearnerProfileDto profile = learnerProfileService.getUserProfile(userId);
    if (profile == null || profile.getTags() == null) return null;

    PersonaSignals signals = new PersonaSignals();
    if (profile.getPreference() != null) {
        signals.preferredDifficulty = profile.getPreference().getPreferredDifficulty();
    }
    for (LearnerProfileTagDto tag : profile.getTags()) {
        if (Boolean.TRUE.equals(tag.getMatched())) {
            switch (tag.getCode()) {
                case "VOCAB_WEAK" -> signals.vocabWeak = true;
                case "CONSISTENT_LEARNER" -> signals.highActive = true;
                case "INACTIVE_RISK" -> signals.lowActive = true;
                case "REVIEW_OVERDUE" -> signals.reviewDelayed = true;
                case "DECLINING_TREND" -> signals.churnRisk = true;
            }
        }
    }
    return signals;
}
```

#### 画像重排入口

```java
private void applyPersonaRerank(Map<Long, Double> scoreMap, PersonaSignals signals)
```

内部批量 `textService.listByIds(scoreMap.keySet())`，不要为每个候选单独查 DB。

#### 单篇文章 bonus 计算

```java
private double computePersonaAdjustment(Text text, PersonaSignals signals)
```

#### fallback 偏好难度选择

将：`String preferredDifficulty = userTextReadMapper.selectMostReadDifficulty(userId);`

改为：

```java
private String resolvePreferredDifficulty(Long userId, PersonaSignals signals) {
    if (signals != null && signals.preferredDifficulty != null && !signals.preferredDifficulty.isBlank()
            && !"UNKNOWN".equals(signals.preferredDifficulty)) {
        return signals.preferredDifficulty;
    }
    return userTextReadMapper.selectMostReadDifficulty(userId);
}
```

### 10.5 重排规则

V1 不做复杂数学模型，只做小幅加减分。

| 规则 | 条件 | 调整 |
|------|------|------|
| 难度偏好匹配 | `signals.preferredDifficulty == text.getDifficulty()` | `+ difficultyBonus` |
| 词汇薄弱降权 | `signals.vocabWeak && text.getDifficulty() == HARD` | `- hardContentPenalty` |
| 低活跃/流失风险 | `signals.lowActive \|\| signals.churnRisk` | `EASY/MEDIUM` 给轻微 bonus，`HARD` 不加分 |
| 高活跃探索 | `signals.highActive` | `MEDIUM/HARD` 给极小 bonus |

单篇文章总调整值控制在 `[-0.12, +0.12]`，不让 persona 覆盖 CF 主分数。

### 10.6 fallback 拆分

原方法签名改为：

```java
private List<Text> fallback(Long userId, int count, Set<Long> excludeSet,
                            LinkedHashSet<Long> accumulatedIds, PersonaSignals signals)
```

拆分子步骤：

```java
private void fillFeatured(int count, Set<Long> excludeSet, LinkedHashSet<Long> accumulatedIds)
private void fillPreferredDifficulty(Long userId, int count, Set<Long> excludeSet,
                                     LinkedHashSet<Long> accumulatedIds, PersonaSignals signals)
private void fillLatest(int count, Set<Long> excludeSet, LinkedHashSet<Long> accumulatedIds)
```

### 10.7 recommend() 主流程伪代码

```java
public List<Text> recommend(Long userId, int count, Collection<Long> excludeIds) {
    if (userId == null || count <= 0) return Collections.emptyList();

    List<Long> readTextIds = userTextReadMapper.selectReadTextIds(userId);
    Set<Long> excludeSet = new HashSet<>(readTextIds);
    if (excludeIds != null && !excludeIds.isEmpty()) excludeSet.addAll(excludeIds);

    PersonaSignals signals = loadPersonaSignals(userId);

    if (readTextIds.size() < coldStartMinReads) {
        return fallback(userId, count, excludeSet, new LinkedHashSet<>(), signals);
    }

    Map<Long, Double> scoreMap = new HashMap<>();
    accumulateItemCfScores(readTextIds, excludeSet, scoreMap);
    accumulateUserCfScores(userId, excludeSet, scoreMap);
    applyPersonaRerank(scoreMap, signals);

    LinkedHashSet<Long> topIds = scoreMap.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .limit(count)
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    if (topIds.size() < count) {
        return fallback(userId, count, excludeSet, topIds, signals);
    }
    return materializeInOrder(topIds);
}
```

### 10.8 改造清单

**必改**：
- 注入 `LearnerProfileService`
- 新增 `recommendation.persona.*` 配置项
- 新增 `PersonaSignals`
- 新增 `loadPersonaSignals(...)`
- 新增 `applyPersonaRerank(...)`
- 改造 `fallback(...)` 签名
- 提取 `resolvePreferredDifficulty(...)`

**推荐改**：
- 拆分 `fillFeatured(...)` / `fillPreferredDifficulty(...)` / `fillLatest(...)`

**不做**：
- 不在 RecommendationServiceImpl 里复制画像计算逻辑
- 不为重排新建复杂 SQL / 新表
- 不让 persona 成为推荐主分数来源

---

## 11. 前端实施方案

### 11.1 API 封装

新增文件：`frontend/src/api/learnerProfile.js`

```js
// 画像查询
export function getLearnerProfile(userId)
export function getClassLearnerProfileSummary(classId)
export function getTeacherStudentProfile(studentId)

// 标签管理 CRUD
export function listLearnerTags()
export function createLearnerTag(data)
export function updateLearnerTag(id, data)
export function deleteLearnerTag(id)
export function toggleLearnerTag(id, enabled)
export function listTagMetrics()
```

### 11.2 Admin 端标签管理页面

新增文件：`frontend/src/views/admin/TagManagement.vue`

功能：
- **标签列表**: el-table 展示编码、名称、类型（彩色 el-tag）、优先级、规则预览（中文可读）、行动建议、启用开关、操作按钮
- **新增/编辑对话框**: 可视化规则编辑器
  - 指标选择（下拉框，从 `/tag-definitions/metrics` API 动态加载）
  - 操作符选择（<, <=, >, >=, =）
  - 阈值输入
  - 标签引用（编码 + MATCHED/NOT_MATCHED）
  - 规则组合方式（AND/OR）
- **启用/禁用**: el-switch 直接切换
- **删除**: 二次确认对话框

路由: `/admin/tag-management`，`meta: { featureKey: 'admin.tag_management' }`

导航: MobileLayout 管理端「更多」菜单中，使用 EditPen 图标

### 11.3 Teacher 端展示

落点：`frontend/src/views/teacher/MyStudents.vue`

改造：每个学生卡片新增"查看画像"按钮，打开抽屉或对话框显示画像卡片。

新增组件：
- `frontend/src/components/learner/LearnerProfileCard.vue`
- `frontend/src/components/learner/LearnerRiskTagList.vue`

展示结构：
- 顶部：学生姓名 / 班级 / 学院 / 年级 / 风险等级
- 中部：命中标签、活跃度 / 掌握度 / 偏好摘要
- 底部：证据摘要 + 建议动作

### 11.3 Admin 端展示

落点：`frontend/src/views/admin/Classes.vue`

改造：在班级详情中增加"画像摘要"区块，对每个学生展示高优先级标签，增加"风险学生"快速筛选视图。

V1 不单独起新页面，先挂在现有页面验证业务价值。

---

## 12. 配置方案

### 12.1 application.yml 新增

```yaml
recommendation:
  persona:
    enabled: false
    difficulty-bonus: 0.08
    easy-content-bonus: 0.06
    hard-content-penalty: 0.08

learner-profile:
  enabled: true
  cache-ttl-minutes: 10
```

### 12.2 Teacher 侧功能开关

走 `system_config` 表：
- group: `feature_toggle`
- key: `teacher.learner_profile_enabled`
- 读取方式：`SystemConfigService.get("feature_toggle", key, defaultValue)`

### 12.3 Admin 标签管理功能开关

走 `system_config` 表（V38 迁移自动种子）：
- group: `feature_toggle`
- key: `admin.tag_management`
- default: `true`
- 前端: 路由和导航菜单通过 `featureKey` 控制可见性

### 12.4 推荐侧功能开关

走 `@Value`：
- `recommendation.persona.enabled` 默认 `false`，保证零风险上线

---

## 13. 测试方案

### 13.1 后端单元测试

新增：
- `backend/src/test/java/com/english/service/LearnerProfileServiceTest.java`
- `backend/src/test/java/com/english/service/LearnerProfileV3Test.java` — CompositeTagEngine 测试
- `backend/src/test/java/com/english/controller/LearnerProfileControllerTest.java`

重点覆盖：
- 空数据用户返回 `EMPTY`/`PARTIAL`
- 9 个标签各自命中场景（通过 mock CompositeTagEngine 验证）
- `buildMetricsMap()` 生成的 16 个指标值正确
- CompositeTagEngine 两遍求值逻辑（metric LT/GTE、AND/OR 组合、tag 引用）
- 标签管理 CRUD 端点（code 唯一性、JSON 校验、缓存清除）
- 无权限教师访问他人学生画像返回 403
- 推荐开关关闭/开启回归

### 13.2 TeacherDataControllerTest

新增测试点：
1. 开关关闭时返回错误
2. `studentId` 为空返回错误
3. 教师未绑定该学生返回 403
4. 学生已绑定但平台用户不存在返回 404
5. 成功返回 `Result<LearnerProfileDto>`
6. `getStudentScores()` 与新提取的绑定校验共用逻辑不回归

### 13.3 RecommendationServiceImplTest

新增测试点：
1. persona 开关关闭时，推荐顺序与旧逻辑一致
2. persona 开启时，同难度候选获得小幅加分
3. `VOCAB_WEAK` 时高难度内容被降权
4. 冷启动用户依然能 fallback 成功
5. fallback 优先使用 persona 偏好难度
6. 画像服务异常时推荐仍能返回结果

### 13.4 前端验证

重点页面：`MyStudents.vue` / `Classes.vue`

验证点：加载态、空态、异常态、标签展示与接口一致、教师仅能查看已绑定学生

---

## 14. 任务拆解与开发顺序

### 14.1 里程碑总览

| 里程碑 | 目标 | 估时 | 依赖 |
|------|------|------|------|
| M1 | 规则与数据契约定稿 | 0.5d | 无 |
| M2 | 后端画像计算落地 | 1.5d | M1 |
| M3 | 画像读取接口落地 | 1d | M2 |
| M4 | 推荐轻量重排接入 | 1d | M2 |
| M5 | 管理端画像卡片 | 1d | M3 |
| M6 | 测试与上线保护 | 0.8d | M3/M4/M5 |
| **合计** | — | **5.8d** | — |

### 14.2 依赖关系

```text
LP-1 → LP-2 → LP-3 → LP-5
          └──→ LP-4
LP-3 + LP-4 + LP-5 → LP-6
```

关键路径：`LP-1.2 → LP-2.2 → LP-3.1 → LP-5.2 → LP-6.4`

### 14.3 开发阶段

#### Phase A：画像内核
1. DTO
2. `LearnerProfileService`
3. 标签规则
4. 单元测试

#### Phase B：接口
5. `LearnerProfileController`
6. `TeacherDataController` 教师侧画像接口

#### Phase C：前端显示
7. Teacher 端画像卡片
8. Admin 端班级画像摘要

#### Phase D：推荐接入
9. `RecommendationServiceImpl` persona 开关
10. rerank
11. fallback 偏好难度接入

#### Phase E：上线保护
12. 缓存
13. 日志
14. 灰度验证

### 14.4 最小落地路径

1. 先完成 LP-1 规则与契约
2. 直接做 LP-2 按需画像计算
3. 先只开放 LP-3.1 单用户画像接口
4. 做 LP-5.2 单用户画像卡片验证展示效果
5. 再做 LP-4 推荐重排开关接入
6. 最后补 LP-3.2 班级摘要和 LP-6 上线保护

### 14.5 优先级

- **P0**: LP-1 / LP-2 / LP-3.1 / LP-5.2
- **P1**: LP-4 / LP-3.2
- **P2**: LP-6 深化、缓存增强、快照持久化

---

## 15. 缓存与灰度方案

### 15.1 缓存策略

单用户画像：`@Cacheable(value = "learner-profile:user", key = "#userId")`

班级画像摘要：`@Cacheable(value = "learner-profile:class", key = "#classId")`

当前全局 Redis TTL 为 10 分钟，V1 可直接复用。

### 15.2 灰度策略

1. 仅开放 Admin / Teacher 查询接口
2. 前端仅教师端可见
3. 推荐重排默认关闭
4. 先在测试环境打开推荐重排
5. 观察推荐结果后再逐步开启

---

## 16. 验收标准

### 功能验收
- [ ] 9 个基础标签均可通过 DB 驱动规则引擎命中
- [ ] Admin 单用户画像接口可用
- [ ] Admin 班级摘要接口可用
- [ ] Admin 标签管理 CRUD 可用（增删改查 + 启用/禁用）
- [ ] Teacher 单学生画像接口可用且有权限校验
- [ ] 推荐开关关闭时行为不变
- [ ] 推荐开关开启时仅做轻量重排

### 质量验收
- [ ] 空数据用户返回 `EMPTY` 或 `PARTIAL`
- [ ] 无权限教师访问返回 403
- [ ] 画像接口统一 `Result<T>`
- [ ] 标签 code 稳定、中文展示名不参与程序判断
- [ ] 有 controller / service / recommendation 回归测试
- [ ] UI 含加载态、空态、异常态
- [ ] 发布前有回退方案

---

## 17. 风险与回退

### 17.1 主要风险

| 风险 | 对策 |
|------|------|
| 学号与平台 `username` 不完全一致 | V1 用 `username == studentId` 映射，后续补映射表 |
| 偏好证据先天弱于行为证据 | V1 只做弱偏好标签，V2 再补埋点 |
| persona 调整过强扰动推荐结果 | 只做候选顺序微调 + 配置开关 |
| 按需计算性能不足 | 先做单用户缓存，再按 classId 缓存 |
| 标签定义不合理导致误标 | 阈值已优化（新用户/低词汇量保护），管理员可随时通过 Admin 端调整 |
| `user_tags` 结构过轻 | V1 先不强依赖 `user_tags` 存储 |

### 17.2 回退方式

#### 回退 1：关闭推荐 persona

```yaml
recommendation.persona.enabled: false
```

#### 回退 2：关闭 Teacher 侧画像

`feature_toggle.teacher.learner_profile_enabled = false`

#### 回退 3：关闭整个画像功能

```yaml
learner-profile.enabled: false
```

#### 回退 4：隐藏前端入口

- Teacher 页隐藏"查看画像"按钮
- Admin 页隐藏画像摘要区块

---

## 18. 后续扩展

> **更新 (2026-05-28)**: V2/V3 均已实现并合入 `feat/learner-persona-design` 分支。以下记录实际实施细节。

### 已提前完成（原 V3 计划，已并入 V1）

- **`learner_composite_tags` 表**: Flyway V36 建表 + V38 种子 9 个基础标签（原 T-034）
- **`CompositeTagEngine` 组合标签引擎**: 支持 metric 阈值条件 + tag 引用条件，两遍求值（原 T-039）
- **趋势标签**: `IMPROVING_TREND` / `DECLINING_TREND` / `REVIEW_CHAMPION` — 基于快照数据的趋势评估（原 T-038）
- **Admin 标签管理 CRUD**: 6 个 API 端点 + `TagManagement.vue` 前端页面

### V2：行为闭环增强 ✅ 已完成

#### 数据库表（均已同步至 `04-apply-all-migrations.sql`）

| 表 | Flyway | 用途 |
|----|--------|------|
| `recommendation_events` | V32 | 推荐事件追踪（IMPRESSION/CLICK/DISMISS/BOOKMARK） |
| `learner_profile_snapshots` | V33 | 学习者画像每日快照（含风险等级、活跃指标、标签匹配） |
| `tag_recalibration_log` | V34 | 标签阈值校准审计日志 |

#### 后端服务

| 类 | 说明 |
|----|------|
| `RecommendationTrackingService` / `Impl` | 事件追踪：`trackEvent()`, `trackBatchImpressions()`, CTR/persona 聚合查询。开关: `learner-profile.tracking.enabled` |
| `LearnerProfileSnapshotService` / `Impl` | 快照管理：`takeSnapshot()`, `takeDailySnapshotsForAllActiveUsers()`, `getSnapshotHistory()`。开关: `learner-profile.snapshot.enabled` |
| `TagRecalibrationService` / `Impl` | 标签校准：`getTagEffectiveness()`, `getRecalibrationProposals()`, `recalibrate()`。命中率 >80% 建议收紧，<5% 建议放宽 |
| `LearnerProfileSnapshotScheduler` | 定时任务 `@Scheduled(cron = "0 30 2 * * ?")` 每日凌晨 2:30 批量快照 |

#### API 端点（`LearnerProfileController` 扩展）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/analytics/overview` | 平台风险分布 + CTR + 标签有效性聚合 |
| GET | `/analytics/trends?lastNDays=30` | 快照日趋势（风险分布、活跃度、逾期率）+ 标签命中计数 |
| GET | `/analytics/recommendation-effectiveness?lastNDays=7` | CTR by source + persona 提升量 |
| GET | `/tags/effectiveness?lastNDays=30` | 标签维度的展示/点击/CTR |
| GET | `/tags/proposals` | 校准建议（收紧/放宽 + 命中率 + 原因） |
| POST | `/tags/recalibrate` | 执行校准（写审计日志），body: `{tagCode, newThreshold, reason}` |
| GET | `/{userId}/snapshots?lastNDays=30` | 用户快照历史 |
| POST | `/api/recommendation/event` | 前端上报 CLICK/DISMISS/BOOKMARK 事件 |

#### 前端

| 文件 | 说明 |
|------|------|
| `PersonaAnalytics.vue` | Admin 画像分析看板：风险趋势折线图、风险分布饼图、活跃度+逾期率双轴图、标签命中柱状图、推荐效果表格、标签有效性表格、校准建议卡片。ECharts (vue-echarts v8) 集成 |
| `learnerProfile.js` | 新增 `getAnalyticsOverview`, `getAnalyticsTrends`, `getTagEffectiveness`, `getRecalibrationProposals`, `submitRecalibration`, `getSnapshotHistory`, `trackRecommendationEvent` |

#### 功能开关

| 开关 | 默认 | 机制 |
|------|------|------|
| `learner-profile.tracking.enabled` | `false` | `@Value` yaml |
| `learner-profile.snapshot.enabled` | `false` | `@Value` yaml |
| `admin.persona_analytics` | `false` | `FeatureToggleService` DB |

#### 测试

- `RecommendationTrackingServiceTest.java` — 10 个用例
- `LearnerProfileSnapshotServiceTest.java` — 9 个用例
- `TagRecalibrationServiceTest.java` — 8 个用例
- `LearnerProfileV2IntegrationTest.java` — 7 个用例（MockMvc 端到端）

### V3：AI 增强 + 学生端 ✅ 已完成

#### 数据库表

| 表 | Flyway | 用途 |
|----|--------|------|
| `learner_profile_summaries` | V35 | LLM 生成画像摘要（支持 PERSONAL/TEACHER_FACING/CLASS_COHORT 三种类型） |

#### 后端服务

| 类 | 说明 |
|----|------|
| `LearnerProfileSummaryService` / `Impl` | LLM 摘要：`generateSummary(userId, type)`, `getLatestSummary()`。通过 `AiProviderRuntimeService.completeIfConfigured()` 调用 LLM。开关: `learner-profile.llm-summary.enabled` |
| `ClassCohortAnalysisService` / `Impl` | 班级队列分析：`analyzeCohort(classId)`, `compareClasses(classIds)`。批量画像→标签分群→聚合指标→可选 LLM 队列摘要 |
| `StudentProfileController` | 学生端画像 API：`GET /api/student/profile/me`, `GET /api/student/profile/me/summary`。过滤 risk 标签，仅返回 positive/ability 标签 |

#### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/learner-profile/{userId}/generate-summary` | 按需触发 LLM 摘要生成 |
| GET | `/api/admin/learner-profile/class/{classId}/cohort-analysis` | 班级队列分析（风险分布、标签命中、子群分析） |
| GET | `/api/admin/learner-profile/class-comparison?classIds=1,2,3` | 多班级对比（最多 5 个） |
| GET | `/api/student/profile/me` | 学生端画像（隐藏风险标签，附 LLM 摘要） |
| GET | `/api/student/profile/me/summary` | 学生端 LLM 摘要文本 |

#### DTO

| DTO | 说明 |
|-----|------|
| `StudentFacingProfileDto` | 学生视角画像：身份、能力、活跃度、备考目标、正面标签、LLM 摘要 |
| `ClassCohortAnalysisDto` | 班级队列分析：classId、总人数、风险分布、标签命中分布、平均指标、高风险学生、子群 |
| `CohortSubgroupDto` | 子群：群名、学生数、均值指标、建议动作 |

#### 前端

| 文件 | 说明 |
|------|------|
| `MyProfile.vue` | 学生端画像页：活跃度统计、词汇能力、备考目标、学习徽章、LLM 建议 |
| `ClassCohortAnalysis.vue` | Admin 队列分析页：班级选择器、概览统计、风险分布条、详细指标 |
| `learnerProfile.js` | 新增 `getMyProfile`, `getMyProfileSummary`, `generateLearnerSummary`, `getCohortAnalysis`, `compareClasses` |

#### 功能开关

| 开关 | 默认 | 机制 |
|------|------|------|
| `learner-profile.llm-summary.enabled` | `false` | `@Value` yaml |
| `student.learner_profile_enabled` | `false` | `SystemConfigService` DB |

#### 测试

- `LearnerProfileV3Test.java` — CompositeTagEngine + 相关 V3 服务单元测试
- `LearnerProfileV3IntegrationTest.java` — 15 个用例（LLM 摘要、队列分析、班级对比、学生端画像）

#### 实施偏差说明

| 原计划 | 实际实现 | 原因 |
|--------|----------|------|
| 外部 prompt 模板文件 `prompts/learner-profile-summary.txt` | prompt 内联在 `LearnerProfileSummaryServiceImpl.buildSummaryPrompt()` | 单一模板不需要外部文件，内联更易维护 |
| `PersonaTrendPointDto` 承载全部趋势数据 | Controller 直接使用 `Map<String, Object>` 包装 `dailyPoints + tagHitCounts` | 更灵活，避免 DTO 膨胀 |
| `T-014` Fallback 方法拆分 | 跳过 | 可选优化，三策略在单方法内，功能正确 |
