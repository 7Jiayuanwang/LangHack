# 学校数据接入完整方案

> 版本: v3.0
> 创建日期: 2026-04-09
> 最后更新: 2026-04-12

---

## 目录

1. [概述](#概述)
2. [系统架构](#系统架构)
3. [数据同步策略](#数据同步策略)
4. [数据库表结构](#数据库表结构)
5. [接口映射关系](#接口映射关系)
6. [字段映射规则](#字段映射规则)
7. [同步流程](#同步流程)
8. [API接口文档](#api接口文档)
9. [定时调度](#定时调度)
10. [监控与告警](#监控与告警)
11. [配置说明](#配置说明)
12. [错误处理](#错误处理)
13. [测试覆盖](#测试覆盖)

---

## 概述

本文档描述了学校教务系统数据接入方案，包括9个核心接口的数据结构、数据库设计、同步策略和实施细节。

### 接入方式

支持 **两种接入方式**，通过 `school.data.source` 配置切换：

| 模式 | 配置值 | 实现类 | 说明 |
|------|--------|--------|------|
| **API接口** | `api`（默认） | `SchoolDataApiClientImpl` | 通过学校REST API获取数据，Bearer Token认证 |
| **ETL直连** | `etl` | `SchoolDataEtlClientImpl` | 通过JDBC直连学校数据库，独立连接池 |

### 数据流向

```
┌──────────────────────────────────────────────────────┐
│  school.data.source = api                           │
│  学校教务API (https://api.imau.edu.cn, Bearer Token) │
│      |                                               │
│      v                                               │
│  SchoolDataApiClientImpl (RestTemplate + 响应解析)    │
└──────────────────────────────────────────────────────┘
                    or
┌──────────────────────────────────────────────────────┐
│  school.data.source = etl                           │
│  学校数据库 (JDBC直连, 独立HikariCP连接池)            │
│      |                                               │
│      v                                               │
│  SchoolDataEtlClientImpl (PreparedStatement + SQL)    │
└──────────────────────────────────────────────────────┘
                    |
                    v
SchoolDataApiClientDelegate (根据 source 路由到 API/ETL 实现)
                    |
                    v
SchoolDataSyncServiceImpl (全量/增量调度 + 防重入)
    |
    v
FieldMappingServiceImpl (4种转换器: DATE/BOOLEAN/SPLIT/JSON)
    |
    v
MyBatis Mapper UPSERT (INSERT ... ON DUPLICATE KEY UPDATE)
    |
    v
本地同步表 (7个数据源)
    |
    v
业务系统使用
```

### 核心组件

| 组件 | 类名 | 职责 |
|------|------|------|
| **配置类** | `SchoolDataProperties` | `@ConfigurationProperties(prefix="school.data")`，集中管理API/ETL/Sync配置 |
| **API客户端** | `SchoolDataApiClientImpl` | 调用学校REST API，Bearer Token认证（始终注册，`@Service("schoolDataApiClientApi")`） |
| **ETL客户端** | `SchoolDataEtlClientImpl` | JDBC直连学校数据库，独立HikariCP连接池（始终注册，`@Service("schoolDataApiClientEtl")`，延迟初始化DataSource） |
| **委托代理** | `SchoolDataApiClientDelegate` | 根据 `properties.getSource()` 路由到 API/ETL 实现 |
| **配置刷新** | `SchoolDataConfigRefreshService` | DB → Properties 刷新 + 脱敏过滤 + 副作用触发 |
| **同步服务** | `SchoolDataSyncServiceImpl` | 全量/增量同步调度，数据持久化 |
| **字段映射** | `FieldMappingServiceImpl` | 源字段到目标字段的转换和映射 |
| **监控服务** | `SyncMonitorService` | 健康检查、告警生成、状态监控 |
| **定时调度** | `SchoolDataSyncScheduler` | 动态Cron调度，防重入 |
| **管理接口** | `SchoolDataSyncController` | REST API，ADMIN/OPS角色专用 |
| **启动初始化** | `SchoolDataConfigInitializer` | `@EventListener(ApplicationReadyEvent)`，启动时从DB刷新配置 |

### 7个数据源

| 数据源名称 | 对应表 | 唯一键 | UPSERT策略 |
|-----------|--------|--------|-----------|
| `administrative_class` | administrative_class | class_code | ON DUPLICATE KEY UPDATE |
| `school_course` | school_course | course_code | ON DUPLICATE KEY UPDATE |
| `teaching_class` | teaching_class | class_id | ON DUPLICATE KEY UPDATE |
| `course_schedule` | course_schedule | (无自然唯一键) | DELETE + INSERT (按class_id+semester) |
| `course_selection` | course_selection | selection_id | ON DUPLICATE KEY UPDATE |
| `level_exam_scores` | level_exam_scores | (student_id, exam_type, exam_date) | ON DUPLICATE KEY UPDATE |
| `teaching_plans` | teaching_plans + teaching_plan_courses | plan_id / detail_id | ON DUPLICATE KEY UPDATE |

---

## 系统架构

### 分层架构

```
SchoolDataSyncController          (REST API, @PreAuthorize ADMIN/OPS)
        |
        v
SchoolDataSyncServiceImpl         (核心同步逻辑, AtomicBoolean防重入)
   |         |         |
   v         v         v
SchoolDataApiClientDelegate   FieldMappingServiceImpl   SyncMonitorService
(委托代理, 运行时路由)         (4种转换器)               (健康检查 + 告警)
   |
   ├── SchoolDataApiClientImpl     (@Service("schoolDataApiClientApi"), 始终注册)
   │   RestTemplate + Bearer Token
   │   学校API端点映射: xzxx/kcxx/bzkcxx/kcap/xsxk/djcj/jxjh
   │
   └── SchoolDataEtlClientImpl     (@Service("schoolDataApiClientEtl"), 始终注册)
       独立HikariCP连接池(延迟初始化) + PreparedStatement
       配置SQL查询映射
   |
   v
7个Mapper写入方法（6个UPSERT + 1个DELETE+INSERT）      field_mappings表配置
   |
   v
7个同步数据表
```

### SyncLog生命周期

每次同步操作都记录一条 `sync_logs` 记录：

```
RUNNING  ─────>  SUCCESS
   |                (正常完成，记录totalCount/successCount/failCount)
   |
   └────────>  FAILED
                (异常，记录errorMessage，截断至500字符)
```

### 防重入机制

使用 `AtomicBoolean` 防止同一类型的同步并发执行：

- `fullSyncRunning`: 全量同步防重入
- `incrementalSyncRunning`: 增量同步防重入

如果上一次同步尚未完成，新的同步请求会被直接拒绝（不会排队等待）。

### 错误隔离

每个数据源的同步无事务包裹（`persistData` 方法不标注 `@Transactional`，因为 Spring AOP 自调用无法代理）：
- 一个数据源失败不影响其他数据源
- `fullSyncAll` / `incrementalSyncAll` 遍历7个数据源，逐个执行
- 单条记录失败会被捕获跳过，不影响同数据源的其他记录（catch and continue）

---

## 数据同步策略

### 同步优先级与频率

| 优先级 | 表名 | 同步频率 | 同步方式 | 原因 |
|-------|------|---------|---------|------|
| **P0** | course | 每学期初 | 全量 | 基础数据，其他表依赖 |
| **P0** | administrative_class | 每学期初 | 全量 | 基础数据，学生关联 |
| **P1** | teaching_class | 每学期初 + 选课期间每日 | 全量 + 增量 | 教学组织核心 |
| **P1** | course_schedule | 每学期初 | 全量 | 课表安排 |
| **P2** | course_selection | 选课期间每小时 + 学期中每日 | 增量 | 高频变更 |
| **P2** | teaching_plans | 每学年初 | 全量 | 低频变更 |
| **P2** | teaching_plan_courses | 每学年初 | 全量 | 培养方案明细 |
| **P3** | level_exam_scores | 成绩发布后 | 增量 | 按需同步 |

### 同步时间窗口

| 时间段 | 同步任务 | 说明 |
|-------|---------|------|
| **每日 02:00** | 全量同步基础数据 | 课程、行政班、教学班 |
| **每日 06:00** | 增量同步选课数据 | 选课记录、成绩更新 |
| **选课期间每小时** | 增量同步选课 | 高峰期实时性保障 |
| **每学期初** | 全量同步所有表 | 数据完整性校验 |
| **每学年初** | 同步教学计划 | 培养方案更新 |

### 数据质量控制

| 检查项 | 规则 | 处理方式 |
|-------|------|---------|
| **必填字段** | 不能为空 | 记录错误日志，跳过该条记录 |
| **外键完整性** | 关联记录必须存在 | 先同步父表，再同步子表 |
| **数据格式** | 日期/数字格式校验 | 使用转换器，失败则使用默认值 |
| **重复数据** | 唯一键冲突 | 更新已有记录（upsert） |
| **数据一致性** | 学时 = 理论 + 实验 + 实践 | 记录警告，不阻塞同步 |

---

## 数据库表结构

### 1. 基础数据层 (Foundation Layer)

#### 1.1 行政班表 (administrative_class)

**用途**: 存储学校行政班级基本信息

| 字段名 | 类型 | 约束 | 说明 | 学校字段 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 | - |
| class_code | VARCHAR(50) | NOT NULL, UNIQUE | 行政班编号 | XZBH |
| class_name | VARCHAR(100) | NOT NULL | 行政班名称 | XZBMC |
| class_short_name | VARCHAR(50) | NULL | 行政班简称 | XZBJC |
| college_code | VARCHAR(50) | NOT NULL | 学院编号 | YXBH |
| college_name | VARCHAR(100) | NOT NULL | 学院名称 | YXMC |
| major_code | VARCHAR(50) | NOT NULL | 专业编号 | ZYBH |
| major_name | VARCHAR(100) | NOT NULL | 专业名称 | ZYMC |
| grade | VARCHAR(10) | NOT NULL | 年级 | NJ |
| class_type | VARCHAR(20) | NULL | 班级类型 | BJLX |
| head_teacher_code | VARCHAR(50) | NULL | 班主任工号 | BZRGH |
| head_teacher_name | VARCHAR(50) | NULL | 班主任姓名 | BZRXM |
| student_count | INT | DEFAULT 0 | 学生人数 | XSRS |
| male_count | INT | DEFAULT 0 | 男生人数 | NSRS |
| female_count | INT | DEFAULT 0 | 女生人数 | NSRS_NV |
| class_status | VARCHAR(20) | NOT NULL | 班级状态 | BJZT |
| establish_date | DATE | NULL | 成立日期 | CLRQ |
| graduate_date | DATE | NULL | 毕业日期 | BYRQ |
| remark | VARCHAR(500) | NULL | 备注 | BZ |
| sync_time | DATETIME | NOT NULL | 同步时间 | - |
| sync_source | VARCHAR(20) | NOT NULL | 同步来源 | - |
| created_at | DATETIME | NOT NULL | 创建时间 | CJSJ |
| updated_at | DATETIME | NOT NULL | 更新时间 | GXSJ |

**索引**:
- `idx_college` (college_code)
- `idx_major` (major_code)
- `idx_grade` (grade)
- `idx_sync_time` (sync_time)

**业务规则**:
- 班级编号全局唯一
- 学生人数 = 男生人数 + 女生人数
- 班级状态: 在读/毕业/解散


#### 1.2 课程表 (school_course)

**用途**: 存储课程基本信息

| 字段名 | 类型 | 约束 | 说明 | 学校字段 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 | - |
| course_code | VARCHAR(50) | NOT NULL, UNIQUE | 课程编号 | KCBH |
| course_name | VARCHAR(200) | NOT NULL | 课程名称 | KCMC |
| course_name_en | VARCHAR(200) | NULL | 课程英文名称 | KCYWMC |
| course_short_name | VARCHAR(100) | NULL | 课程简称 | KCJC |
| course_nature | VARCHAR(50) | NOT NULL | 课程性质 | KCXZ |
| course_category | VARCHAR(50) | NOT NULL | 课程类别 | KCLB |
| credits | DECIMAL(3,1) | NOT NULL | 学分 | XF |
| total_hours | INT | NOT NULL | 总学时 | ZXS |
| theory_hours | INT | NULL | 理论学时 | LLXS |
| experiment_hours | INT | NULL | 实验学时 | SYXS |
| practice_hours | INT | NULL | 实践学时 | SJXS |
| computer_hours | INT | NULL | 上机学时 | SJXS_PC |
| college_code | VARCHAR(50) | NOT NULL | 开课学院编号 | KKYXBH |
| college_name | VARCHAR(100) | NOT NULL | 开课学院名称 | KKYXMC |
| department_code | VARCHAR(50) | NULL | 开课系部编号 | KKXBBH |
| department_name | VARCHAR(100) | NULL | 开课系部名称 | KKXBMC |
| leader_code | VARCHAR(50) | NULL | 课程负责人工号 | KCFZRGH |
| leader_name | VARCHAR(50) | NULL | 课程负责人姓名 | KCFZRXM |
| assessment_method | VARCHAR(50) | NOT NULL | 考核方式 | KHFS |
| is_bilingual | VARCHAR(10) | NULL | 是否双语教学 | SFSYJX |
| is_online | VARCHAR(10) | NULL | 是否网络课程 | SFWLKC |
| applicable_majors | VARCHAR(500) | NULL | 适用专业 | SYZY |
| prerequisite_codes | VARCHAR(500) | NULL | 先修课程编号 | XXKCBH |
| prerequisite_names | VARCHAR(500) | NULL | 先修课程名称 | XXKCMC |
| course_intro | TEXT | NULL | 课程简介 | KCJJ |
| teaching_objectives | TEXT | NULL | 教学目标 | JXMB |
| course_status | VARCHAR(20) | NOT NULL | 课程状态 | KCZT |
| sync_time | DATETIME | NOT NULL | 同步时间 | - |
| sync_source | VARCHAR(20) | NOT NULL | 同步来源 | - |
| created_at | DATETIME | NOT NULL | 创建时间 | CJSJ |
| updated_at | DATETIME | NOT NULL | 更新时间 | GXSJ |

**索引**:
- `idx_college` (college_code)
- `idx_nature` (course_nature)
- `idx_category` (course_category)
- `idx_sync_time` (sync_time)

**业务规则**:
- 课程编号全局唯一
- 学时分配: 总学时 = 理论 + 实验 + 实践 + 上机
- 先修课程支持多个，逗号分隔
- 课程状态: 在用/停用

---

### 2. 教学组织层 (Teaching Organization Layer)

#### 2.1 教学班表 (teaching_class)

**用途**: 存储教学班信息（一个课程可以有多个教学班）

| 字段名 | 类型 | 约束 | 说明 | 学校字段 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 | - |
| class_id | VARCHAR(50) | NOT NULL, UNIQUE | 教学班号 | JXBID |
| class_name | VARCHAR(100) | NULL | 教学班名称 | JXBMC |
| course_code | VARCHAR(50) | NOT NULL, FK | 课程编号 | KCBH |
| course_name | VARCHAR(200) | NOT NULL | 课程名称 | KCMC |
| teacher_id | VARCHAR(50) | NOT NULL | 授课教师工号 | JGH |
| teacher_name | VARCHAR(50) | NOT NULL | 授课教师姓名 | XM |
| academic_year | VARCHAR(20) | NOT NULL | 学年 | XNM |
| semester | INT | NOT NULL | 学期 | XQM |
| capacity | INT | NOT NULL | 班级容量 | - |
| enrolled_count | INT | DEFAULT 0 | 已选人数 | - |
| class_status | VARCHAR(20) | NOT NULL | 课程状态 | - |
| sync_time | DATETIME | NOT NULL | 同步时间 | - |
| sync_source | VARCHAR(20) | NOT NULL | 同步来源 | - |
| created_at | DATETIME | NOT NULL | 创建时间 | - |
| updated_at | DATETIME | NOT NULL | 更新时间 | - |

**索引**:
- `idx_course` (course_code)
- `idx_teacher` (teacher_id)
- `idx_academic_year_semester` (academic_year, semester)
- `idx_sync_time` (sync_time)

**外键**:
- `course_code` → `course(course_code)`

**业务规则**:
- 教学班号全局唯一
- 已选人数不能超过容量
- 学期编码: 3=第一学期, 12=第二学期, 16=第三学期

#### 2.2 课程安排表 (course_schedule)

**用途**: 存储课程的时间地点安排

| 字段名 | 类型 | 约束 | 说明 | 学校字段 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 | - |
| class_id | VARCHAR(50) | NOT NULL, FK | 教学班号 | JXBID |
| day_of_week | INT | NOT NULL | 星期几 (1-7) | XQJ |
| period | VARCHAR(20) | NOT NULL | 节次 (如"1-2") | JCS |
| week_range | VARCHAR(50) | NOT NULL | 周次 (如"1-16周") | ZCD |
| class_location | VARCHAR(100) | NOT NULL | 上课地点 | CDMC |
| academic_year | VARCHAR(20) | NOT NULL | 学年 | XNM |
| semester | INT | NOT NULL | 学期 | XQM |
| sync_time | DATETIME | NOT NULL | 同步时间 | - |
| sync_source | VARCHAR(20) | NOT NULL | 同步来源 | - |
| created_at | DATETIME | NOT NULL | 创建时间 | - |
| updated_at | DATETIME | NOT NULL | 更新时间 | - |

**索引**:
- `idx_class_id` (class_id)
- `idx_day_period` (day_of_week, period)
- `idx_academic_year_semester` (academic_year, semester)
- `idx_sync_time` (sync_time)

**外键**:
- `class_id` → `teaching_class(class_id)`

**业务规则**:
- 一个教学班可以有多个时间安排
- 星期几: 1=周一, 7=周日
- 节次格式: "1-2" 表示第1-2节连续上课
- 周次格式: "1-16周" 或 "1-16周(单)" 或 "1-16周(双)"

---

### 3. 选课与成绩层 (Enrollment & Grade Layer)

#### 3.1 选课记录表 (course_selection)

**用途**: 存储学生选课记录和成绩

| 字段名 | 类型 | 约束 | 说明 | 学校字段 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 | - |
| selection_id | VARCHAR(50) | NOT NULL, UNIQUE | 选课记录ID | - |
| student_id | VARCHAR(50) | NOT NULL | 学号 | studentId |
| student_name | VARCHAR(100) | NOT NULL | 学生姓名 | studentName |
| course_code | VARCHAR(50) | NOT NULL, FK | 课程代码 | courseCode |
| course_name | VARCHAR(200) | NOT NULL | 课程名称 | courseName |
| class_id | VARCHAR(50) | NOT NULL, FK | 教学班号 | classId |
| teacher_id | VARCHAR(50) | NOT NULL | 授课教师工号 | teacherId |
| teacher_name | VARCHAR(50) | NOT NULL | 授课教师姓名 | teacherName |
| credits | DECIMAL(3,1) | NOT NULL | 学分 | credits |
| academic_year | VARCHAR(20) | NOT NULL | 学年 | academicYear |
| semester | INT | NOT NULL | 学期 | semester |
| class_time | VARCHAR(100) | NULL | 上课时间 | classTime |
| class_location | VARCHAR(100) | NULL | 上课地点 | classLocation |
| selection_time | DATETIME | NOT NULL | 选课时间 | selectionTime |
| selection_status | VARCHAR(20) | NOT NULL | 选课状态 | selectionStatus |
| regular_grade | DECIMAL(5,2) | NULL | 平时成绩 | regularGrade |
| final_grade | DECIMAL(5,2) | NULL | 期末成绩 | finalGrade |
| total_grade | DECIMAL(5,2) | NULL | 总评成绩 | totalGrade |
| grade_point | DECIMAL(3,2) | NULL | 绩点 | gradePoint |
| attendance_count | INT | DEFAULT 0 | 出勤次数 | attendanceCount |
| absence_count | INT | DEFAULT 0 | 缺勤次数 | absenceCount |
| grade_remark | VARCHAR(500) | NULL | 成绩备注 | gradeRemark |
| sync_time | DATETIME | NOT NULL | 同步时间 | - |
| sync_source | VARCHAR(20) | NOT NULL | 同步来源 | - |
| created_at | DATETIME | NOT NULL | 创建时间 | - |
| updated_at | DATETIME | NOT NULL | 更新时间 | - |

**索引**:
- `idx_student` (student_id)
- `idx_course` (course_code)
- `idx_class` (class_id)
- `idx_academic_year_semester` (academic_year, semester)
- `idx_selection_status` (selection_status)
- `idx_sync_time` (sync_time)

**外键**:
- `course_code` → `course(course_code)`
- `class_id` → `teaching_class(class_id)`

**业务规则**:
- 选课状态: SELECTED(已选)/DROPPED(已退)/COMPLETED(已完成)
- 绩点计算: 根据总评成绩映射到0-5.0
- 总评成绩 = 平时成绩 × 权重 + 期末成绩 × 权重


#### 3.2 等级考试成绩表 (level_exam_scores)

**用途**: 存储学生参加等级考试（如英语四六级、计算机等级考试）的成绩

**索引**:
- uk_student_exam (student_id, exam_type, exam_date) UNIQUE
- idx_student_id (student_id)
- idx_exam_type (exam_type)
- idx_exam_date (exam_date)
- idx_is_passed (is_passed)
- idx_sync_time (sync_time)

**业务规则**:
- 同一学生同一类型考试同一日期只能有一条记录
- 英语类考试需要存储细分项成绩（听说读写译）
- 合格标准: 英语四级425分，六级425分

#### 4.1 教学计划主表 (teaching_plans)

**用途**: 存储专业培养方案/教学计划

**索引**:
- uk_plan_id (plan_id) UNIQUE
- idx_major_grade (major_code, grade)
- idx_plan_status (plan_status)
- idx_effective_date (effective_date)
- idx_sync_time (sync_time)

**业务规则**:
- 同一专业同一年级可能有多个版本的教学计划
- 计划状态: 草稿/审核中/已审核/已发布/已废止
- 生效日期和失效日期决定计划的有效期

#### 4.2 教学计划课程明细表 (teaching_plan_courses)

**用途**: 存储教学计划下的课程明细

**索引**:
- uk_detail_id (detail_id) UNIQUE
- idx_plan_id (plan_id)
- idx_course_code (course_code)
- idx_sync_time (sync_time)

**外键**:
- plan_id → teaching_plans(plan_id) ON DELETE CASCADE
- course_code → school_course(course_code) ON DELETE RESTRICT

**业务规则**:
- 一个教学计划包含多门课程
- 删除教学计划时级联删除课程明细
- 课程代码必须在学校课程表中存在

---

## 接口映射关系

| 数据源名称 | API端点路径 | 主要对应表 | 关联表 | 同步频率 |
|-----------|------------|-----------|--------|---------|
| 行政班 | `/api/xzxx` | administrative_class | - | 每学期初 |
| 课程 | `/api/kcxx` | school_course | - | 每学期初 |
| 本专科课程 | `/api/bzkcxx` | school_course, teaching_class | teaching_class | 每学期初 |
| 课程安排 | `/api/kcap` | course_schedule | teaching_class | 每学期初 |
| 学生选课 | `/api/xsxk` | course_selection | teaching_class, course | 选课期间每小时 |
| 等级考试成绩 | `/api/djcj` | level_exam_scores | - | 成绩发布后 |
| 教学计划 | `/api/jxjh` | teaching_plans, teaching_plan_courses | course | 每学年初 |
| 教师授课 | `/api/jssk` | teaching_class | course, course_selection | 每学期初 |
| 学生课表 | `/api/xskb` | course_schedule | teaching_class, course_selection | 每学期初 |

---

## 字段映射规则

### 行政班字段映射

| 学校字段 | 本地字段 | 转换器 | 说明 |
|---------|---------|--------|------|
| XZBH | class_code | - | 行政班编号 |
| XZBMC | class_name | - | 行政班名称 |
| YXBH | college_code | - | 学院编号 |
| ZYBH | major_code | - | 专业编号 |
| NJ | grade | - | 年级 |
| XSRS | student_count | - | 学生人数 |
| BJZT | class_status | - | 班级状态 |

### 课程字段映射

| 学校字段 | 本地字段 | 转换器 | 说明 |
|---------|---------|--------|------|
| KCBH | course_code | - | 课程编号 |
| KCMC | course_name | - | 课程名称 |
| XF | credits | - | 学分 |
| ZXS | total_hours | - | 总学时 |
| KCXZ | course_nature | - | 课程性质 |
| KCLB | course_category | - | 课程类别 |

### 教学班字段映射

| 学校字段 | 本地字段 | 转换器 | 说明 |
|---------|---------|--------|------|
| JXBID | class_id | - | 教学班号 |
| KCBH | course_code | - | 课程编号 |
| JGH | teacher_id | - | 教师工号 |
| XNM | academic_year | - | 学年 |
| XQM | semester | - | 学期 |

### 课程安排字段映射

| 学校字段 | 本地字段 | 转换器 | 说明 |
|---------|---------|--------|------|
| XQJ | day_of_week | - | 星期几 (1-7) |
| JCS | period | - | 节次 (如"1-2") |
| ZCD | week_range | - | 周次 (如"1-16周") |
| CDMC | class_location | - | 上课地点 |

---

## 同步流程

### 全量同步流程

1. **准备阶段**
   - 检查网络连接
   - 验证API Token或数据库连接
   - 创建同步日志记录

2. **数据拉取**
   - 按优先级顺序拉取数据（P0 → P1 → P2 → P3）
   - 分批拉取（batch_size=1000）
   - 记录拉取进度

3. **数据转换**
   - 读取字段映射配置
   - 应用转换器（DATE/BOOLEAN/SPLIT/JSON）
   - 数据校验（必填字段、格式校验）

4. **数据写入**
   - 使用 UPSERT 策略（INSERT ... ON DUPLICATE KEY UPDATE）
   - 事务性写入（主从表一起提交）
   - 记录写入统计

5. **完成阶段**
   - 更新同步日志状态
   - 发送同步报告
   - 触发告警（如果有错误）

### 增量同步流程

1. **获取最后同步时间**
   - 从 sync_logs 表查询最后成功同步时间
   - 作为增量查询的起始时间

2. **拉取增量数据**
   - 使用 last_sync_time 作为筛选条件
   - 只拉取变更的记录

3. **数据处理**
   - 与全量同步相同的转换和校验流程
   - 特别处理删除的记录（软删除）

4. **错误回退**
   - 如果错误率 > 10%，自动触发全量同步
   - 记录回退原因

---

## API接口文档

所有接口路径前缀: `/api/admin/sync`，需要 ADMIN 或 OPS 角色。

### 同步操作

| 方法 | 路径 | 说明 | 请求参数 |
|------|------|------|---------|
| POST | `/full/{dataSource}` | 全量同步指定数据源 | `triggerType` (默认 MANUAL) |
| POST | `/incremental/{dataSource}` | 增量同步指定数据源 | `triggerType` (默认 MANUAL) |
| POST | `/full-all` | 全量同步所有7个数据源 | `triggerType` (默认 MANUAL) |
| POST | `/incremental-all` | 增量同步所有7个数据源 | `triggerType` (默认 MANUAL) |

### 状态查询

| 方法 | 路径 | 说明 | 请求参数 |
|------|------|------|---------|
| GET | `/status` | 同步状态概览（含各数据源本地数据量和最后同步状态） | - |
| GET | `/health` | 健康检查（含告警级别和汇总） | - |
| GET | `/validate` | 数据校验（检查字段映射和同步状态） | - |
| GET | `/test-connection` | 测试学校API连通性 | - |

### 日志与告警

| 方法 | 路径 | 说明 | 请求参数 |
|------|------|------|---------|
| GET | `/logs/{dataSource}` | 获取同步历史 | `limit` (默认 20) |
| GET | `/alerts` | 获取最近告警 | `limit` (默认 10) |

### 字段映射管理

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| GET | `/field-mappings/{dataSource}` | 获取字段映射配置 | - |
| PUT | `/field-mappings` | 更新字段映射 | `FieldMapping` JSON |

### 响应格式

所有接口返回 `Result<T>` 统一响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

### triggerType 枚举

| 值 | 说明 |
|----|------|
| `MANUAL` | 手动触发（默认） |
| `SCHEDULED` | 定时调度触发 |

### dataSource 枚举

| 值 | 对应表 |
|----|--------|
| `administrative_class` | 行政班 |
| `school_course` | 学校课程 |
| `teaching_class` | 教学班 |
| `course_schedule` | 课程安排 |
| `course_selection` | 选课记录 |
| `level_exam_scores` | 等级考试成绩 |
| `teaching_plans` | 教学计划 |

---

## 定时调度

`SchoolDataSyncScheduler` 基于 `TaskScheduler` + `CronTrigger` 实现动态调度。

### 三个定时任务

| 任务 | 默认Cron | 说明 | 配置项 |
|------|----------|------|--------|
| 全量同步 | `0 0 2 * * ?` | 每天凌晨2点 | `school.data.sync.full-sync-cron` |
| 增量同步 | `0 0 * * * ?` | 每小时整点 | `school.data.sync.incremental-sync-cron` |
| 数据校验 | `0 0 4 * * ?` | 每天凌晨4点 | `school.data.sync.validation-cron` |

### 启用/禁用

- 配置项: `school.data.sync.enabled`
- 默认值: `false`（需显式启用）
- 环境变量: `SCHOOL_SYNC_ENABLED=true`

### 调度器特性

1. **@PostConstruct 初始化**: 应用启动时自动注册所有定时任务
2. **防重入**: 每个任务类型独立的 `AtomicBoolean`，上一次未完成则跳过
3. **动态重配置**: `rescheduleAll()` 方法支持运行时修改Cron表达式
4. **优雅取消**: `cancelAll()` 方法取消所有已注册的 `ScheduledFuture`

### 执行日志示例

```
INFO  - School data sync scheduler initialized - Full: [0 0 2 * * ?], Incremental: [0 0 * * * ?], Validation: [0 0 4 * * ?]
INFO  - === 定时全量同步开始 ===
INFO  - === 定时全量同步完成: 成功=7, 失败=0 ===
```

---

## 监控与告警

`SyncMonitorService` 提供4级告警机制：

### 告警级别

| 级别 | 含义 | 颜色标识 |
|------|------|---------|
| `INFO` | 正常运行 | 绿色 |
| `WARNING` | 需要关注 | 黄色 |
| `ERROR` | 存在问题 | 红色 |
| `CRITICAL` | 严重故障 | 深红 |

### 告警触发条件

| 状态码 | 告警级别 | 触发条件 |
|--------|---------|---------|
| `NEVER_SYNCED` | CRITICAL | 数据源从未执行过同步 |
| `SYNC_STUCK` | CRITICAL | 同步运行超过2小时（可能卡住） |
| `STALE_CRITICAL` | CRITICAL | 超过72小时未同步 |
| `SYNC_FAILED` | ERROR | 最近一次同步失败 |
| `HIGH_FAIL_RATE` | ERROR | 失败率 > 30% |
| `STALE_WARNING` | WARNING | 超过24小时未同步 |
| `MODERATE_FAIL_RATE` | WARNING | 失败率 10%-30% |
| `ZERO_RECORDS` | WARNING | 连续3次同步零记录 |
| `SYNC_RUNNING` | INFO | 同步正在运行中 |
| `HEALTHY` | INFO | 同步正常 |

### 健康检查返回格式

```json
{
  "summary": {
    "totalSources": 7,
    "healthyCount": 5,
    "warningCount": 1,
    "errorCount": 1,
    "criticalCount": 0,
    "overallStatus": "ERROR",
    "checkedAt": "2026-04-12T10:30:00"
  },
  "sources": {
    "administrative_class": {
      "dataSource": "administrative_class",
      "status": "HEALTHY",
      "alertLevel": "INFO",
      "message": "同步正常",
      "lastSyncTime": "2026-04-12T02:00:15"
    }
  },
  "alerts": [ ... ]
}
```

### 告警排序

告警按严重程度排序: CRITICAL > ERROR > WARNING

---

## 配置说明

### application.yml 配置

所有学校数据相关配置统一在 `school.data` 前缀下，由 `SchoolDataProperties` 类管理：

```yaml
school:
  data:
    # 接入方式: api (REST接口) 或 etl (JDBC直连)
    source: ${SCHOOL_DATA_SOURCE:api}

    # API模式配置 (source=api时使用)
    api:
      base-url: ${SCHOOL_API_BASE_URL:https://api.imau.edu.cn}
      token: ${SCHOOL_API_TOKEN:}
      connect-timeout: ${SCHOOL_API_CONNECT_TIMEOUT:5000}
      read-timeout: ${SCHOOL_API_READ_TIMEOUT:30000}
      retry-attempts: ${SCHOOL_API_RETRY_ATTEMPTS:3}

    # ETL模式配置 (source=etl时使用)
    etl:
      jdbc-url: ${SCHOOL_ETL_JDBC_URL:jdbc:mysql://school-db.imau.edu.cn:3306/school_db}
      username: ${SCHOOL_ETL_USERNAME:}
      password: ${SCHOOL_ETL_PASSWORD:}
      driver-class-name: ${SCHOOL_ETL_DRIVER:com.mysql.cj.jdbc.Driver}
      connection-timeout: ${SCHOOL_ETL_CONNECTION_TIMEOUT:30000}
      query-timeout: ${SCHOOL_ETL_QUERY_TIMEOUT:60000}

    # 同步调度配置
    sync:
      enabled: ${SCHOOL_SYNC_ENABLED:false}
      full-sync-cron: ${SCHOOL_SYNC_FULL_CRON:0 0 2 * * ?}
      incremental-sync-cron: ${SCHOOL_SYNC_INCREMENTAL_CRON:0 0 * * * ?}
      validation-cron: ${SCHOOL_SYNC_VALIDATION_CRON:0 0 4 * * ?}
      batch-size: ${SCHOOL_SYNC_BATCH_SIZE:500}
      sync-timeout-minutes: ${SCHOOL_SYNC_TIMEOUT_MINUTES:60}
      full-sync-on-startup: ${SCHOOL_SYNC_FULL_ON_STARTUP:false}
```

### 环境变量

#### 通用配置

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SCHOOL_DATA_SOURCE` | `api` | 接入方式：`api`(REST接口) 或 `etl`(JDBC直连) |
| `SCHOOL_SYNC_ENABLED` | `false` | 是否启用数据同步定时任务 |

#### API模式环境变量 (source=api)

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SCHOOL_API_BASE_URL` | `https://api.imau.edu.cn` | 学校教务API基础地址 |
| `SCHOOL_API_TOKEN` | (空) | Bearer Token认证令牌 |
| `SCHOOL_API_CONNECT_TIMEOUT` | `5000` | 连接超时(ms) |
| `SCHOOL_API_READ_TIMEOUT` | `30000` | 读取超时(ms) |
| `SCHOOL_API_RETRY_ATTEMPTS` | `3` | 请求失败重试次数 |

#### ETL模式环境变量 (source=etl)

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SCHOOL_ETL_JDBC_URL` | `jdbc:mysql://school-db.imau.edu.cn:3306/school_db` | 学校数据库JDBC地址 |
| `SCHOOL_ETL_USERNAME` | (空) | 数据库用户名 |
| `SCHOOL_ETL_PASSWORD` | (空) | 数据库密码 |
| `SCHOOL_ETL_DRIVER` | `com.mysql.cj.jdbc.Driver` | JDBC驱动类名 |
| `SCHOOL_ETL_CONNECTION_TIMEOUT` | `30000` | 连接超时(ms) |
| `SCHOOL_ETL_QUERY_TIMEOUT` | `60000` | 查询超时(ms) |

#### 同步调度环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SCHOOL_SYNC_FULL_CRON` | `0 0 2 * * ?` | 全量同步Cron表达式(默认每天凌晨2点) |
| `SCHOOL_SYNC_INCREMENTAL_CRON` | `0 0 * * * ?` | 增量同步Cron表达式(默认每小时) |
| `SCHOOL_SYNC_VALIDATION_CRON` | `0 0 4 * * ?` | 数据校验Cron表达式(默认每天凌晨4点) |
| `SCHOOL_SYNC_BATCH_SIZE` | `500` | 批量拉取大小 |
| `SCHOOL_SYNC_TIMEOUT_MINUTES` | `60` | 同步超时时间(分钟) |
| `SCHOOL_SYNC_FULL_ON_STARTUP` | `false` | 启动时是否执行全量同步 |

### 运行时配置（DB 优先）

除了 YAML / 环境变量启动配置外，系统支持通过 **前端运维面板** 实时修改学校数据配置，无需重启服务。

#### 配置优先级

```
DB system_config (school_data 组) > application.yml / 环境变量
```

应用启动时，`SchoolDataConfigInitializer` 监听 `ApplicationReadyEvent`，从 DB 读取 `school_data` 配置组并覆盖 `SchoolDataProperties` 默认值。

#### 核心组件

| 组件 | 类名 | 职责 |
|------|------|------|
| **配置刷新** | `SchoolDataConfigRefreshService` | 从 DB 读取配置 → 写入 Properties → 触发副作用 |
| **委托代理** | `SchoolDataApiClientDelegate` | 根据 `properties.getSource()` 路由到 API/ETL 实现 |
| **启动初始化** | `SchoolDataConfigInitializer` | `@EventListener(ApplicationReadyEvent)` 启动时刷新 DB 配置 |

#### 运行时切换流程

```
运维面板保存配置
    │
    v
PUT /api/ops/school-data/config
    │
    v
SchoolDataConfigRefreshService.updateAndRefresh()
    │
    ├─1. 过滤 **** 脱敏值（不覆盖原密码/Token）
    ├─2. batchUpdate 到 DB system_config
    ├─3. refreshFromDb() → 写入 SchoolDataProperties
    ├─4. cron 变更 → scheduler.rescheduleAll()
    └─5. etl_jdbc_url 变更 → etlClient.recreateDataSource()
```

#### 脱敏保护

前端加载配置时，`is_secret=1` 的字段值会被替换为 `****` 前缀（如 `****6789`）。保存时，`updateAndRefresh()` 自动过滤包含 `****` 的值，不会用脱敏值覆盖数据库中的真实密码/Token。

#### 前端运维面板

- **路径**: `/ops/school-data`
- **API 模块**: `frontend/src/api/schoolData.js`
- **页面组件**: `frontend/src/views/ops/SchoolDataConfig.vue`
- **三个面板**: 数据源配置(API/ETL切换)、同步调度(cron/batch/timeout)、操作(测试连接+保存)

#### 后端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ops/system-config?group=school_data` | 获取配置列表（复用已有端点） |
| PUT | `/api/ops/school-data/config` | 更新配置 + 刷新 + 副作用 |
| POST | `/api/ops/school-data/test-connection` | 测试当前数据源连接 |

### 接入方式切换

通过 `SCHOOL_DATA_SOURCE` 环境变量或运维面板切换接入方式：

- **`api`** (默认): 激活 `SchoolDataApiClientImpl`，通过REST接口获取数据
  - 需配置: `SCHOOL_API_BASE_URL`, `SCHOOL_API_TOKEN`
  - 优势: 松耦合，不直接访问学校数据库
  - 限制: 部分查询(如按学生查课表)依赖API支持

- **`etl`**: 激活 `SchoolDataEtlClientImpl`，通过JDBC直连学校数据库
  - 需配置: `SCHOOL_ETL_JDBC_URL`, `SCHOOL_ETL_USERNAME`, `SCHOOL_ETL_PASSWORD`
  - 优势: 可自定义SQL查询，数据获取更灵活
  - 限制: 需要数据库直连权限，不支持按ID查询单条记录(fetchTeachingPlanCourses/fetchStudentSchedules)
  - 注意: ETL模式使用独立HikariCP连接池(SchoolETL-Pool)，不影响主库的读写分离路由

---

## 错误处理

### 错误分类

| 错误类型 | 处理策略 | 是否阻塞 |
|---------|---------|---------|
| **网络错误** | 重试3次，间隔5秒 | 是 |
| **认证失败** | 立即停止，发送告警 | 是 |
| **数据格式错误** | 记录日志，跳过该条 | 否 |
| **外键约束失败** | 先同步父表，再重试 | 否 |
| **唯一键冲突** | 更新已有记录 | 否 |
| **必填字段为空** | 使用默认值或跳过 | 否 |

### 告警机制

| 告警级别 | 触发条件 | 通知方式 |
|---------|---------|---------|
| **P0 - 紧急** | 同步完全失败 | 短信 + 邮件 + 钉钉 |
| **P1 - 重要** | 错误率 > 10% | 邮件 + 钉钉 |
| **P2 - 一般** | 错误率 > 5% | 邮件 |
| **P3 - 提示** | 有少量错误 | 日志记录 |

### 数据修复

1. **手动修复**
   - 查看 sync_logs 表的 error_message
   - 定位错误记录
   - 手动修正后重新同步

2. **自动修复**
   - 定时任务检查数据一致性
   - 自动修复常见问题（如外键缺失）
   - 记录修复日志

---

## 测试覆盖

### 单元测试

| 测试类 | 覆盖组件 | 测试场景数 |
|--------|---------|-----------|
| `FieldMappingServiceTest` | FieldMappingServiceImpl | 12个 - 映射查询、4种转换器、更新校验 |
| `SchoolDataSyncServiceTest` | SchoolDataSyncServiceImpl | 9个 - 全量/增量同步、状态概览、数据校验 |
| `SyncMonitorServiceTest` | SyncMonitorService | 9个 - 4级告警、健康检查、历史查询 |
| `SchoolDataSyncControllerTest` | SchoolDataSyncController | 12个 - 12个API端点MockMvc测试 |
| `SchoolDataConfigRefreshServiceTest` | SchoolDataConfigRefreshService | 9个 - DB刷新、脱敏过滤、cron/ETL副作用、连接测试 |

### 集成测试

`SchoolDataSyncIntegrationTest` 需要 `@SpringBootTest` + 数据库连接：

| 测试组 | 测试内容 |
|--------|---------|
| 字段映射集成 | 获取映射配置、应用映射、BOOLEAN转换器 |
| UPSERT集成 | 6个表的插入和更新验证 |
| 同步监控集成 | 健康检查、状态概览、数据校验 |
| SyncLog生命周期 | RUNNING->SUCCESS状态转换、最新日志查询 |

### 运行测试

```bash
# 单元测试（无需数据库）
cd backend && mvn test -pl . -Dtest="FieldMappingServiceTest,SchoolDataSyncServiceTest,SchoolMonitorServiceTest,SchoolDataSyncControllerTest"

# 集成测试（需要数据库连接）
cd backend && mvn test -Dtest="SchoolDataSyncIntegrationTest"
```

---

## 附录

### A. 学期编码对照表

| 编码 | 含义 |
|------|------|
| 3 | 第一学期（秋季学期） |
| 12 | 第二学期（春季学期） |
| 16 | 第三学期（夏季学期） |

### B. 课程性质对照表

| 代码 | 名称 |
|------|------|
| 01 | 必修 |
| 02 | 选修 |
| 03 | 限选 |

### C. 考核方式对照表

| 代码 | 名称 |
|------|------|
| 01 | 考试 |
| 02 | 考查 |
| 03 | 论文 |
| 04 | 实践 |

---

**文档结束**

> v3.1 更新记录 (2026-04-12):
> - 新增运行时配置机制：DB system_config (school_data组) 优先于 YAML/环境变量
> - 新增 SchoolDataConfigRefreshService：DB→Properties刷新 + 脱敏过滤 + 副作用触发
> - 新增 SchoolDataApiClientDelegate：委托代理模式，根据 source 运行时路由到 API/ETL 实现
> - 新增 SchoolDataConfigInitializer：启动时自动从DB刷新配置
> - 移除 @ConditionalOnProperty：API/ETL 客户端始终注册，由 Delegate 路由
> - 新增 ETL 延迟初始化：DataSource 在首次使用时创建，recreateDataSource() 支持热更新
> - 新增前端运维面板：/ops/school-data 配置页面（数据源、同步调度、测试连接）
> - 新增后端API：PUT /api/ops/school-data/config, POST /api/ops/school-data/test-connection
> - 新增 Flyway V18：school_data 配置种子数据
> - 新增 SchoolDataConfigRefreshServiceTest：9个单元测试
>
> v3.0 更新记录 (2026-04-12):
> - 新增双接入模式支持（API + ETL），通过 school.data.source 切换
> - 新增 SchoolDataEtlClientImpl（JDBC直连，独立HikariCP连接池）
> - 新增 SchoolDataProperties 统一配置类，替代分散的 @Value 注入
> - 重构配置结构：旧 school-data-api/school-data-sync → 新 school.data.* 嵌套前缀
> - 更新数据流向图和分层架构图反映双模式设计
> - 更新环境变量表：新增 ETL 模式和 API 高级配置变量
> - 修复 API 端点路径（使用学校实际拼音路径：xzxx/kcxx/bzkcxx/kcap/xsxk/djcj/jxjh）
> - 修复 RestTemplate 超时配置未生效问题（改用 SimpleClientHttpRequestFactory）
>
> v2.0 更新记录 (2026-04-12):
> - 新增系统架构、API接口文档、定时调度、监控与告警、配置说明、测试覆盖章节
> - 更新数据流向图反映实际实现（SchoolDataApiClientImpl, SchoolDataSyncServiceImpl等）
> - 补充7个数据源的UPSERT策略对比
> - 补充SyncLog生命周期和防重入机制说明
> - 补充4级告警触发条件和健康检查返回格式

