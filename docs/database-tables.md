# 数据库表登记

> 所有表必须在此文件中登记，建表前先查阅确认表名未被占用。
> 规范详见：[docs/DATABASE_COLLABORATION_GUIDE.md](DATABASE_COLLABORATION_GUIDE.md)
>
> 负责人约定：原有表负责人留空，新增表负责人为 dctx479。

---

## 用户模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| users | 用户基本信息 | | 初始 | 正常 | 00-base-tables.sql |
| student_classes | 学生-班级关联 | | 初始 | 正常 | 00-base-tables.sql |
| sso_user_bindings | SSO用户绑定 | | 2026-04-10 | 正常 | V8 |
| sso_login_logs | SSO登录日志 | | 2026-04-10 | 正常 | V8 |
| sso_config | SSO配置 | | 2026-04-10 | 正常 | V8 |
| user_tags | 用户标签 | | 2026-04-12 | 正常 | V7 |

## 课程模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| courses | 课程信息 | | 初始 | 正常 | 00-base-tables.sql |
| units | 单元信息 | | 初始 | 正常 | 00-base-tables.sql |

## 课文模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| texts | 课文内容（含多难度版本） | | 初始 | 正常 | 00-base-tables.sql |
| text_learning_records | 文章学习记录 | | V2 | 正常 | V2 |
| user_text_reads | 用户文章已读记录 | | V2 | 正常 | V2 |
| article_favorites | 文章收藏 | | V7 | 正常 | V7 |
| text_exercises | 课文练习 | | — | ⚠️ 无DDL | Entity only |
| exercise_options | 练习选项 | | — | ⚠️ 无DDL | Entity only |
| student_exercise_answers | 学生练习答案 | | — | ⚠️ 无DDL | Entity only |

## 班级模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| classes | 班级信息 | | 初始 | 正常 | 00-base-tables.sql |

## 任务模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| assignments | 任务/作业 | | 初始 | 正常 | 00-base-tables.sql |
| score_records | 成绩记录 | | V1 | 正常 | V1 |

## 词汇模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| words | 单词表 | | 初始 | 正常 | 00-base-tables.sql |
| vocabulary_books | 生词本（含SM2复习） | | 初始 | 正常 | 00-base-tables.sql |
| unit_words | 单元-单词关联 | | — | ⚠️ 无DDL | Entity only |
| ecdict_words | ECDICT词典（74万词） | | — | 正常 | 04-apply-all-migrations.sql |

## 学习模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| learning_records | 学习记录 | | 初始 | 正常 | 00-base-tables.sql |

## 媒体模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| media | 媒体文件（BLOB存储） | | V3 | 正常 | V3 |
| underlines | 用户划线标注 | | V3 | 正常 | V3 |

## 系统模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| system_config | 系统配置 | | V6 | 正常 | V6 |
| feature_override | 功能开关覆盖 | | V7 | 正常 | V7 |

## 备份与更新模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| backup_records | 数据库备份记录 | | V4 | 正常 | V4 |
| backup_operation_logs | 备份操作日志 | | V4 | 正常 | V4 |
| backup_config | 备份配置 | | V4 | 正常 | V4 |
| update_config | 自更新配置 | | V4 | 正常 | V4 |

## 监控模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| access_log | 访问日志 | | V5 | 正常 | V5 |
| access_stats_hourly | 每小时访问统计 | | V5 | 正常 | V5 |
| access_stats_daily | 每日访问统计 | | V5 | 正常 | V5 |
| ai_call_log | AI调用日志 | | V5 | 正常 | V5 |
| ai_call_stats_daily | AI调用日统计 | | V5 | 正常 | V5 |
| ai_provider | AI提供商配置 | | V6 | 正常 | V6 |

## 学校数据模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| administrative_class | 行政班基本信息 | | V9 | 正常 | V9 / 08-v16 |
| course | 课程基本信息（教务系统） | | V9 | 正常 | V9 / 08-v16 |
| teaching_class | 教学班信息 | | V9 | 正常 | V9 / 08-v16 |
| course_schedule | 课程安排 | | V9 | 正常 | V9 / 08-v16 |
| course_selection | 选课记录 | | V9 | 正常 | V9 / 08-v16 |
| level_exam_scores | 等级考试成绩 | | V9 | 正常 | V9 / 08-v16 |
| teaching_plans | 教学计划主表 | | V9 | 正常 | V9 / 08-v16 |
| teaching_plan_courses | 教学计划课程明细 | | V9 | 正常 | V9 / 08-v16 |

## 学习者画像模块

| 表名 | 说明 | 负责人 | 创建日期 | 状态 | 来源 |
|------|------|--------|----------|------|------|
| learner_composite_tags | 学习标签定义（DB 驱动规则引擎） | dctx479 | 2026-05-27 | 正常 | V36 + V38 种子 |
| learner_profile_snapshots | 学习者画像每日快照 | dctx479 | 2026-05-27 | 正常 | V33 |
| learner_profile_summaries | LLM 生成画像摘要 | dctx479 | 2026-05-27 | 正常 | V35 |
| recommendation_events | 推荐事件追踪（曝光/点击） | dctx479 | 2026-05-27 | 正常 | V32 |
| tag_recalibration_log | 标签阈值校准日志 | dctx479 | 2026-05-27 | 正常 | V34 |

---

## ⚠️ 无DDL的Entity（需补建表脚本）

以下表仅有 Java Entity（`@TableName`），无任何 Flyway 迁移或 Docker init SQL 中的 CREATE TABLE。
全新部署（`docker compose down -v`）后查询这些表将报 SQL 错误。

| 表名 | Entity 类 | 说明 | 建议操作 |
|------|-----------|------|----------|
| text_exercises | TextExercise | 课文练习 | 补充 Flyway 迁移 + 同步 04 |
| exercise_options | ExerciseOption | 练习选项 | 补充 Flyway 迁移 + 同步 04 |
| student_exercise_answers | StudentExerciseAnswer | 学生练习答案 | 补充 Flyway 迁移 + 同步 04 |
| unit_words | UnitWord | 单元-单词关联 | 补充 Flyway 迁移 + 同步 04 |

---

## 已废弃

| 表名 | 原模块 | 废弃日期 | 原因 | 替代表 |
|------|--------|----------|------|--------|
| （暂无） | | | | |

---

## 统计

| 分类 | 数量 |
|------|------|
| 正常表 | 46 |
| 无DDL（Entity only） | 4 |
| 已废弃 | 0 |
| **合计** | **50** |
