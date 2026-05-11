# LangHack 学校数据对接与数据库规模详细说明

> 生成日期：2026-05-06 | 基于代码库实际状态
> 相关文档：`docs/推荐算法详细设计与实现.md`（推荐算法说明文档已存在）

---

## 目录

1. [学校数据对接总览](#一学校数据对接总览)
2. [9 大数据源详解](#二9-大数据源详解)
3. [双模式接入架构](#三双模式接入架构)
4. [数据同步机制](#四数据同步机制)
5. [数据库全量表清单](#五数据库全量表清单)
6. [数据库规模评估](#六数据库规模评估)
7. [关键架构图](#七关键架构图)

---

## 一、学校数据对接总览

LangHack 已与内蒙古农业大学（IMAU）的校园数据共享平台（CDSP）完成深度集成，覆盖学生从入学到毕业的全周期英语学习数据。

### 对接范围一览

| 维度 | 对接内容 | 数据源数量 |
|------|---------|-----------|
| 学生学籍 | 行政班级归属、学号、姓名 | 1 |
| 课程体系 | 学校课程目录、教学班级、教学计划 | 3 |
| 选课关系 | 学生选课记录、教师授课关系 | 2 |
| 课表数据 | 班级课表、学生个人课表 | 2 |
| 学业成绩 | 四六级考试成绩 | 1 |

**总计：9 个数据源，覆盖 5 个业务维度。**

---

## 二、9 大数据源详解

### 2.1 行政班级（administrative_class）

| 属性 | 说明 |
|------|------|
| **数据内容** | 学生所属的行政班级信息（非教学班），例如"计算机科学与技术2023-1班" |
| **关键字段** | 班级名称、班级代码、所属学院、年级 |
| **业务用途** | 学生分班管理、按学院批量操作、链式拉取的起点 |
| **API 端点** | `cdsp.imau.edu.cn` → administrative_class endpoint |
| **ETL 视图** | `v_administrative_class` |

### 2.2 教学班级（teaching_class）

| 属性 | 说明 |
|------|------|
| **数据内容** | 英语教学班的组班信息，同一行政班可能拆分到不同英语教学班 |
| **关键字段** | 教学班名称、教学班代码、课程编号、任课教师、学生名单 |
| **业务用途** | 教学分组、教师-班级关联、班级学习进度统计 |
| **API 端点** | `cdsp.imau.edu.cn` → teaching_class endpoint |
| **ETL 视图** | `v_teaching_class` |

### 2.3 学校课程（school_course）

| 属性 | 说明 |
|------|------|
| **数据内容** | 学校开设的英语相关课程目录（含课程代码 KKYX） |
| **关键字段** | 课程代码、课程名称、学分、学时、开课学院 |
| **业务用途** | 课程目录展示、课程-教学班关联、链式拉取的中间节点 |
| **API 端点** | `cdsp.imau.edu.cn` → school_course endpoint |
| **ETL 视图** | `v_school_course` |

### 2.4 教师课程（teacher_courses）

| 属性 | 说明 |
|------|------|
| **数据内容** | 教师与课程的授课关联关系 |
| **关键字段** | 教师工号、教师姓名、课程代码（kcdm）、授课学期 |
| **业务用途** | 教师权限自动授予、教师-课程绑定、教学管理 |
| **API 端点** | `cdsp.imau.edu.cn` → teacher_courses endpoint |
| **ETL 视图** | `v_teacher_courses` |
| **⚠️ 注意事项** | 映射后的课程代码字段名为 `classId`（非 `courseClassCode`），字段映射需双向查表 |

### 2.5 选课记录（course_selection）

| 属性 | 说明 |
|------|------|
| **数据内容** | 学生选课记录，记录每个学生选修了哪门英语课程（含课程编号 KCBH） |
| **关键字段** | 学号、学生姓名、课程编号、选课学期 |
| **业务用途** | 学生-课程绑定、SSO 自动授权的核心数据源、学习记录归属 |
| **API 端点** | `cdsp.imau.edu.cn` → course_selection endpoint |
| **ETL 视图** | `v_course_selection` |
| **⚠️ 注意事项** | KCBH 字段在 CDSP 中可能被忽略，需首条探测后切换客户端过滤模式 |

### 2.6 课程表（course_schedule）

| 属性 | 说明 |
|------|------|
| **数据内容** | 班级课程安排（上课时间、地点、周次） |
| **关键字段** | 课程代码、上课时间、上课地点、教学周次、学年（xn）、学期（xq） |
| **业务用途** | 课表展示、上课提醒、学习计划安排 |
| **API 端点** | `cdsp.imau.edu.cn` → course_schedule endpoint |
| **ETL 视图** | `v_course_schedule` |
| **⚠️ 注意事项** | xn=单年（如"2023"），xq=中文（如"秋"），非标准格式 |

### 2.7 学生个人课表（student_schedule）

| 属性 | 说明 |
|------|------|
| **数据内容** | 单个学生的完整课表（包含所有课程，不仅英语） |
| **关键字段** | 学号、课程名称、上课时间、上课地点、周次 |
| **业务用途** | 学生端课表查询、学习时间冲突检测 |
| **API 端点** | `cdsp.imau.edu.cn` → student_schedule endpoint |
| **⚠️ 注意事项** | 该端点仅支持 POST 方法，不支持 GET 查询参数 |

### 2.8 等级考试成绩（level_exam_scores）

| 属性 | 说明 |
|------|------|
| **数据内容** | 全国大学英语四、六级考试（CET-4/CET-6）成绩 |
| **关键字段** | 学号、姓名、考试类型（四级/六级）、总分、各单项分、考试批次 |
| **业务用途** | 学生英语水平评估、分级教学参考、学习效果评估 |
| **API 端点** | `cdsp.imau.edu.cn` → level_exam_scores endpoint（DS0076） |
| **同步策略** | 学生端懒加载（按需拉取），陈旧度阈值 1 天 |

### 2.9 教学计划（teaching_plans）

| 属性 | 说明 |
|------|------|
| **数据内容** | 课程教学大纲/计划（含课程目标、内容安排、教材信息） |
| **关键字段** | 课程代码、教学目标、教学内容、学时分配、教材 |
| **业务用途** | 教学内容参考、学习路径规划、课程大纲展示 |
| **API 端点** | `cdsp.imau.edu.cn` → teaching_plans endpoint |
| **ETL 视图** | `v_teaching_plans` |

---

## 三、双模式接入架构

### 3.1 架构总览

```
┌──────────────────────────────────────────────────────────────┐
│                      业务层 (Service)                         │
│               SchoolDataSyncServiceImpl                       │
│         SchoolDataQueryController (查询)                       │
│         SchoolDataSyncController (同步)                       │
└────────────────────────┬─────────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────────┐
│                委托层 (Delegate)                               │
│           SchoolDataApiClientDelegate                         │
│           根据 SCHOOL_DATA_SOURCE 环境变量切换                  │
└────────────┬────────────────────────────┬────────────────────┘
             │                            │
┌────────────▼────────────┐ ┌─────────────▼───────────────────┐
│   API 模式 (默认)        │ │   ETL 模式 (JDBC 直连)           │
│                         │ │                                 │
│   认证：appId +          │ │   连接：HikariCP 独立连接池       │
│   appSecret + timestamp  │ │   数据源：学校提供只读视图         │
│   签名：Base64(MD5(      │ │   查询：SELECT * FROM v_xxx      │
│   appId+appSecret+       │ │   WHERE updated_at >= ?         │
│   timestamp))            │ │                                 │
│                         │ │                                 │
│   端点：cdsp.imau.edu.cn │ │   必需视图(8个)：                 │
│   方法：REST API         │ │   v_administrative_class         │
│                         │ │   v_school_course                │
│   实现类：               │ │   v_teaching_class               │
│   SchoolDataApiClient    │ │   v_course_schedule              │
│   Impl.java              │ │   v_course_selection             │
│                         │ │   v_level_exam_scores            │
│                         │ │   v_teaching_plans               │
│                         │ │   v_teacher_courses              │
│                         │ │                                 │
│                         │ │   实现类：                        │
│                         │ │   SchoolDataEtlClientImpl.java   │
└─────────────────────────┘ └─────────────────────────────────┘
```

### 3.2 API 模式认证流程

```
客户端                          CDSP 服务器
  │                                │
  │  1. 生成 timestamp (毫秒)       │
  │  2. 计算 sign =                │
  │     Base64(MD5(                │
  │       appId +                  │
  │       appSecret +              │
  │       timestamp                │
  │     ))                         │
  │                                │
  │  3. 发送请求 (Headers:         │
  │     appId, timestamp, sign)    │
  │──────────────────────────────→ │
  │                                │
  │                    4. 验证签名  │
  │                    5. 返回数据  │
  │ ←──────────────────────────────│
```

### 3.3 ETL 模式前置条件

| 要求 | 说明 |
|------|------|
| 网络 | 需要学校提供数据库直连权限（防火墙白名单） |
| 数据库 | MySQL 或兼容数据库，学校需创建 8 个只读视图 |
| 视图规范 | 每个视图必须包含 `updated_at` 字段（用于增量同步） |
| 配置 | `SCHOOL_ETL_JDBC_URL`、`SCHOOL_ETL_USERNAME`、`SCHOOL_ETL_PASSWORD` |
| 文档参考 | `docs/ETL_MODE_SETUP.md`（配置指南）、`docs/etl/create_views.sql`（视图创建脚本） |

### 3.4 模式切换

```bash
# 环境变量切换（docker-compose.yml 或 .env）
SCHOOL_DATA_SOURCE=api   # 默认：REST API 模式
SCHOOL_DATA_SOURCE=etl   # JDBC 直连模式
```

---

## 四、数据同步机制

### 4.1 同步策略

| 策略 | 触发方式 | 频率 | 说明 |
|------|---------|------|------|
| 全量同步 | 手动触发 / 首次部署 | 按需 | 清空并全量导入所有数据 |
| 增量同步 | Cron 定时任务 | 默认每天 3:30 | 基于 `updated_at` 字段增量更新 |
| 链式拉取 | 手动触发 | 按需 | 学院 → 课程 → 教师 → 学生 → 绑定 |
| 懒加载 | 学生端按需触发 | 实时 | 四六级成绩等重度数据，陈旧度 1 天 |

### 4.2 链式拉取流程

```
① 选择学院
    │
    ▼
② 拉取学院课程 (school_course, 按 KKYX 过滤)
    │
    ▼
③ 拉取教师课程 (teacher_courses, 按 kcdm 匹配)
    │
    ├──────────────────────────────┐
    ▼                              ▼
④ 拉取选课记录                   ⑤ 创建教师账号
   (course_selection,                (SSO 自动授权)
    按 KCBH 匹配)
    │
    ▼
⑥ 创建学生账号 + 建立师生绑定
   (school_student + school_teacher + teacher_student_binding)
```

### 4.3 同步技术细节

| 特性 | 实现 |
|------|------|
| 分页支持 | 1000 条/页，自动翻页直至数据全部获取 |
| 错误处理 | 单页失败不影响其他页，记录 SyncLog |
| 事务管理 | 按数据源分批提交，避免大事务 |
| 字段映射 | `SchoolDataFieldAlias` 双向查表，支持中英文 key 兼容 |
| 去重逻辑 | `ON DUPLICATE KEY UPDATE` 幂等写入 |
| 调度配置 | `school.data.sync.cron` 可自定义 |

### 4.4 SSO 自动授权

```
用户通过 CAS 登录
    │
    ▼
检查 school_student 表（按学号匹配）
    ├── 找到 → 授予 STUDENT 角色，关联班级
    └── 未找到
          │
          ▼
       检查 school_teacher 表（按工号匹配）
          ├── 找到 → 授予 TEACHER 角色，关联课程
          └── 未找到 → 走普通注册流程
```

**Feature Toggle 控制**：
- `sso.auto_grant_student`：是否自动授权学生
- `sso.auto_grant_teacher`：是否自动授权教师
- 默认关闭，按需开启

---

## 五、数据库全量表清单

### 5.1 完整表列表（按分类）

LangHack 数据库共 **54 个 Entity 类**，对应约 **50 张数据表**（部分 Entity 为中间对象或视图映射）。

#### 核心业务表（7 张）

| 表名 | Entity 类 | 说明 |
|------|----------|------|
| `users` | `User.java` | 用户主表（含角色、密码、昵称、头像） |
| `texts` | `Text.java` | 课文/文章（含各级词汇标注、难度分级） |
| `words` | `Word.java` | 单词表 |
| `media` | `Media.java` | 媒体文件（图片/视频 BLOB 存储） |
| `courses` | `Course.java` | 课程表 |
| `units` | `Unit.java` | 单元表 |
| `unit_words` | `UnitWord.java` | 单元-单词关联 |

#### 学习系统表（10 张）

| 表名 | Entity 类 | 说明 |
|------|----------|------|
| `assignments` | `Assignment.java` | 学习任务/作业 |
| `reading_progress` | （TextLearningRecord 相关） | 阅读进度追踪 |
| `text_learning_records` | `TextLearningRecord.java` | 课文学习记录 |
| `vocabulary_books` | `VocabularyBook.java` | 生词本（含 SM-2 间隔复习算法） |
| `learning_records` | `LearningRecord.java` | 综合学习记录 |
| `score_records` | `ScoreRecord.java` | 评分记录 |
| `interaction_scores` | `InteractionScore.java` | 交互评分（推荐算法用） |
| `article_favorites` | `ArticleFavorite.java` | 文章收藏 |
| `underlines` | `Underline.java` | 文章划线和笔记 |
| `text_exercises` | `TextExercise.java` | 课文练习 |
| `student_exercise_answers` | `StudentExerciseAnswer.java` | 学生答题记录 |
| `exercise_options` | `ExerciseOption.java` | 练习选项 |

#### 学校数据表（9 张）

| 表名 | Entity 类 | 对应数据源 |
|------|----------|-----------|
| `administrative_classes` | `AdministrativeClass.java` | 行政班级 |
| `teaching_classes` | `TeachingClass.java` | 教学班级 |
| `school_courses` | `SchoolCourse.java` | 学校课程 |
| `teacher_courses` | （TeacherStudentBinding 关联） | 教师授课 |
| `course_selections` | `CourseSelection.java` | 学生选课 |
| `course_schedules` | `CourseSchedule.java` | 课程表 |
| `student_schedules` | `StudentSchedule.java` | 学生个人课表 |
| `level_exam_scores` | `LevelExamScore.java` | 四六级成绩 |
| `teaching_plans` | `TeachingPlan.java` | 教学计划 |
| `teaching_plan_courses` | `TeachingPlanCourse.java` | 教学计划-课程关联 |

#### 学校人员表（3 张）

| 表名 | Entity 类 | 说明 |
|------|----------|------|
| `school_students` | `SchoolStudent.java` | 学校学生目录 |
| `school_teachers` | `SchoolTeacher.java` | 学校教师目录 |
| `teacher_student_bindings` | `TeacherStudentBinding.java` | 教师-学生绑定关系 |

#### 推荐算法表（4 张）

| 表名 | Entity 类 | 说明 |
|------|----------|------|
| `text_similarity_matrix` | `TextSimilarityMatrix.java` | 文章相似度矩阵（Item-CF 离线计算） |
| `text_similarity_matrix_tmp` | `TextSimilarityMatrixTmp.java` | 相似度矩阵临时表（计算中间态） |
| `user_similarity_matrix` | `UserSimilarityMatrix.java` | 用户相似度矩阵（User-CF 离线计算） |
| `user_similarity_matrix_tmp` | `UserSimilarityMatrixTmp.java` | 用户相似度临时表 |

#### 字典与配置表（6 张）

| 表名 | Entity 类 | 说明 |
|------|----------|------|
| `ecdict` | `ECDICTWord.java` | ECDICT 英语词典（74 万词条） |
| `system_config` | `SystemConfig.java` | 系统配置（键值对） |
| `feature_toggles` | `FeatureOverride.java` | 功能开关 |
| `field_mappings` | `FieldMapping.java` | 学校数据字段映射 |
| `ai_providers` | （AiProvider 相关） | AI 服务商配置 |
| `sso_config` | （SsoConfig 相关） | SSO 集成配置 |

#### 日志与运维表（8 张）

| 表名 | Entity 类 | 说明 |
|------|----------|------|
| `access_logs` | `AccessLog.java` | 访问日志 |
| `access_stats_daily` | `AccessStatsDaily.java` | 每日访问统计 |
| `access_stats_hourly` | `AccessStatsHourly.java` | 每小时访问统计 |
| `ai_call_logs` | `AiCallLog.java` | AI 调用日志 |
| `ai_call_stats_daily` | `AiCallStatsDaily.java` | AI 调用每日统计 |
| `sync_logs` | `SyncLog.java` | 学校数据同步日志 |
| `backup_records` | `BackupRecord.java` | 备份记录 |
| `backup_operation_logs` | `BackupOperationLog.java` | 备份操作日志 |

#### 备份配置表（2 张）

| 表名 | Entity 类 | 说明 |
|------|----------|------|
| `backup_configs` | `BackupConfig.java` | 备份配置 |
| `update_configs` | `UpdateConfig.java` | 更新/升级配置 |

#### 班级关联表（2 张）

| 表名 | Entity 类 | 说明 |
|------|----------|------|
| `classes` | `Class.java` | 班级表 |
| `student_classes` | `StudentClass.java` | 学生-班级关联 |

### 5.2 Flyway 迁移历史

| 版本 | 文件名 | 主要内容 |
|------|--------|---------|
| V0 | `V0__base_tables.sql` | 核心基础表（users, texts, words, courses 等） |
| V1 | `V1__scoring_and_assignments.sql` | 评分系统 + 作业系统 |
| V2 | `V2__text_and_learning.sql` | 课文扩展 + 学习记录 |
| V3 | `V3__media_and_annotations.sql` | 媒体文件 + 批注 |
| V4 | `V4__backup_and_update_system.sql` | 备份系统和更新配置 |
| V5 | `V5__monitoring_and_access.sql` | 监控和访问日志 |
| V6 | `V6__system_config_and_ai_providers.sql` | 系统配置 + AI 服务商 |
| V7 | `V7__feature_toggles.sql` | 功能开关系统 |
| V8 | `V8__sso_integration.sql` | SSO 统一认证集成 |
| V9 | `V9__school_data_sync.sql` | 学校数据同步基础表 |
| V10 | `V10__user_and_text_updates.sql` | 用户扩展 + 课文精选 + 生词本 SM-2 |
| V11 | `V11__role_mapping_cleanup.sql` | 角色映射清理 |
| V12 | `V12__school_data_integration.sql` | **学校数据集成合并脚本（~481 行）** |
| V13 | `V13__feature_toggles_and_ai_config.sql` | 功能开关 + AI 配置增强 |
| V14 | `V14__recommendation_algorithm.sql` | 推荐算法矩阵表 |
| V999 | `V999__seed_data.sql` | 种子数据 |

> **说明**：V12 是合并脚本，将原 V12.1/V13/V14/V16/V18/V19/V20 共 7 个独立迁移合并为一个，便于维护。

---

## 六、数据库规模评估

### 6.1 表数量统计

| 分类 | 表数量 | 占比 |
|------|--------|------|
| 核心业务 | 7 | 14% |
| 学习系统 | 10 | 20% |
| 学校数据 | 9 | 18% |
| 学校人员 | 3 | 6% |
| 推荐算法 | 4 | 8% |
| 字典与配置 | 6 | 12% |
| 日志与运维 | 8 | 16% |
| 备份配置 | 2 | 4% |
| 班级关联 | 2 | 4% |
| **总计** | **~50** | 100% |

### 6.2 数据量级估计

#### 固定数据（不随用户增长）

| 数据项 | 行数 | 存储量（估算） |
|--------|------|--------------|
| ECDICT 词典 | **74 万** | ~200 MB |
| Flyway 种子数据 | ~50 | < 1 MB |
| 系统配置 | ~30 | < 1 MB |
| 功能开关 | ~20 | < 1 MB |

#### 增长数据（随学校导入和用户使用增长）

| 数据项 | 基础量级 | 增长因子 | 一年后预估（1 万学生） |
|--------|---------|---------|---------------------|
| 用户（users） | ~10 | 1x 学生数 | ~10,000 |
| 学校学生（school_students） | 0 | 1x 学生数 | ~10,000 |
| 学校教师（school_teachers） | 0 | 0.02x 学生数 | ~200 |
| 行政班级 | 0 | ~100/学院 | ~500 |
| 教学班级 | 0 | ~200/学院 | ~1,000 |
| 选课记录 | 0 | 1x 学生数 | ~10,000 |
| 课程表 | 0 | ~500/学期 | ~1,000 |
| 四六级成绩 | 0 | 1-2x 学生数 | ~15,000 |
| 阅读进度 | 0 | 50x 活跃学生 | ~500,000 |
| 生词本 | 0 | 100x 活跃学生 | ~1,000,000 |
| 学习记录 | 0 | 200x 活跃学生 | ~2,000,000 |
| 访问日志 | 0 | 500x 日活 | ~500,000/天 |
| 推荐相似度矩阵 | 0 | n×n（n=文章数） | ~1,000×1,000 = 1,000,000 |

### 6.3 存储量预估

```
初始部署（仅 ECDICT + 种子数据）：~200 MB
  + 导入 1 万学生学校数据：  +50 MB
  + 运行 3 个月学习记录：    +100 MB
  + 访问日志（3 个月）：     +200 MB
  + 推荐算法矩阵：           +50 MB
  ─────────────────────────────────
  3 个月总存储预估：          ~600 MB
  1 年总存储预估：            ~2-5 GB（取决于活跃度和日志保留策略）
```

### 6.4 关键 SQL 查询

```sql
-- 获取精确表行数
SELECT 
    table_name, 
    table_rows, 
    ROUND(data_length / 1024 / 1024, 2) AS data_mb,
    ROUND(index_length / 1024 / 1024, 2) AS index_mb
FROM information_schema.tables 
WHERE table_schema = 'english_learning'
ORDER BY table_rows DESC;

-- 查看数据库总大小
SELECT 
    table_schema AS 'database',
    ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS 'total_mb'
FROM information_schema.tables
WHERE table_schema = 'english_learning';

-- 查看学校数据各表数据量
SELECT 'school_students' AS tbl, COUNT(*) AS cnt FROM school_students
UNION ALL SELECT 'school_teachers', COUNT(*) FROM school_teachers
UNION ALL SELECT 'administrative_classes', COUNT(*) FROM administrative_classes
UNION ALL SELECT 'teaching_classes', COUNT(*) FROM teaching_classes
UNION ALL SELECT 'school_courses', COUNT(*) FROM school_courses
UNION ALL SELECT 'course_selections', COUNT(*) FROM course_selections
UNION ALL SELECT 'course_schedules', COUNT(*) FROM course_schedules
UNION ALL SELECT 'level_exam_scores', COUNT(*) FROM level_exam_scores
UNION ALL SELECT 'teaching_plans', COUNT(*) FROM teaching_plans;
```

---

## 七、关键架构图

### 7.1 学校数据集成交互全景

```
┌──────────────────────────────┐       ┌──────────────────────────┐
│         LangHack 平台         │       │     学校 CDSP / 数据库     │
│                              │       │                          │
│  ┌────────────────────┐     │       │  ┌────────────────────┐  │
│  │  运维面板           │     │       │  │  CDSP REST API     │  │
│  │  SchoolDataConfig   │─────┼───────┼──│  cdsp.imau.edu.cn   │  │
│  │  .vue               │     │       │  │  (appId+签名认证)   │  │
│  └────────────────────┘     │       │  └────────────────────┘  │
│                              │       │           │              │
│  ┌────────────────────┐     │       │  ┌────────▼───────────┐  │
│  │  SchoolDataSync     │     │       │  │  学校数据库         │  │
│  │  Controller         │─────┼───────┼──│  (JDBC 直连)       │  │
│  │  (定时/手动触发)     │     │       │  │  v_administrative   │  │
│  └────────┬───────────┘     │       │  │  _class ...         │  │
│           │                  │       │  └────────────────────┘  │
│  ┌────────▼───────────┐     │       └──────────────────────────┘
│  │  SchoolDataApi      │     │
│  │  ClientDelegate     │     │       ┌──────────────────────────┐
│  │  (API / ETL 切换)   │     │       │   LangHack 数据库         │
│  └────────┬───────────┘     │       │                          │
│           │                  │       │  school_students         │
│  ┌────────▼───────────┐     │       │  school_teachers         │
│  │  SchoolDataSync     │     │       │  administrative_classes  │
│  │  ServiceImpl        │─────┼───────│  teaching_classes        │
│  │  (全量/增量/链式)    │     │       │  school_courses          │
│  └────────┬───────────┘     │       │  course_selections       │
│           │                  │       │  course_schedules        │
│  ┌────────▼───────────┐     │       │  level_exam_scores       │
│  │  SSO 自动授权       │     │       │  teaching_plans          │
│  │  (CAS 回调拦截)     │     │       │  sync_logs               │
│  └────────────────────┘     │       └──────────────────────────┘
└──────────────────────────────┘
```

### 7.2 数据同步时序

```
Cron 触发 (3:30)
    │
    ▼
SyncLog 创建同步记录 (status=RUNNING)
    │
    ├──→ ① administrative_class (全量/增量)
    ├──→ ② school_course
    ├──→ ③ teaching_class
    ├──→ ④ teacher_courses
    ├──→ ⑤ course_selection
    ├──→ ⑥ course_schedule
    ├──→ ⑦ teaching_plans
    │
    ▼
更新 SyncLog (status=SUCCESS, 记录 synced_count)
    │
    ▼
Feature Toggle 检查
    ├── sso.auto_grant_student = true → 为新学生创建账号
    └── sso.auto_grant_teacher = true → 为新教师创建账号
```

---

## 附录：关键配置文件索引

| 文件 | 路径 | 说明 |
|------|------|------|
| 学校数据属性配置 | `backend/src/main/java/com/english/config/SchoolDataProperties.java` | API 端点、ETL 查询、同步参数 |
| API 客户端实现 | `backend/src/main/java/com/english/service/impl/SchoolDataApiClientImpl.java` | CDSP REST API 调用、签名认证 |
| ETL 客户端实现 | `backend/src/main/java/com/english/service/impl/SchoolDataEtlClientImpl.java` | JDBC 直连查询 |
| 同步服务实现 | `backend/src/main/java/com/english/service/impl/SchoolDataSyncServiceImpl.java` | 全量/增量/链式同步 |
| 委托路由器 | `backend/src/main/java/com/english/service/impl/SchoolDataApiClientDelegate.java` | API/ETL 模式自动切换 |
| 同步调度器 | `backend/src/main/java/com/english/scheduler/SchoolDataSyncScheduler.java` | Cron 定时任务 |
| 部署向导 | `scripts/deploy.sh` | 交互式部署配置向导（含学校数据配置） |
| ETL 配置指南 | `docs/ETL_MODE_SETUP.md` | ETL 模式完整配置说明 |
| ETL 视图脚本 | `docs/etl/create_views.sql` | 学校需执行的视图创建 SQL |
| 字段映射指南 | `docs/etl/field_mapping_guide.md` | CDSP 字段名到系统字段名映射 |
| 数据表结构 | `backend/src/main/resources/db/migration/V12__school_data_integration.sql` | 学校数据全量表结构（~481 行） |

---

> **文档版本**：v1.0 | **下次更新时机**：接入新学校或新增数据源时
