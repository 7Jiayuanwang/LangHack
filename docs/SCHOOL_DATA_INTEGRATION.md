# 学校数据接入完整方案

> 版本: v1.0  
> 创建日期: 2026-04-09  
> 最后更新: 2026-04-09

---

## 目录

1. [概述](#概述)
2. [数据同步策略](#数据同步策略)
3. [数据库表结构](#数据库表结构)
4. [数据关系图](#数据关系图)
5. [接口映射关系](#接口映射关系)
6. [字段映射规则](#字段映射规则)
7. [同步流程](#同步流程)
8. [错误处理](#错误处理)

---

## 概述

本文档描述了学校教务系统数据接入方案，包括9个核心接口的数据结构、数据库设计、同步策略和实施细节。

### 接入方式对比

| 方式 | 优势 | 劣势 | 适用场景 |
|------|------|------|---------|
| **API接口** | 标准化、安全、易维护 | 依赖网络、有调用限制 | 实时性要求高、数据量适中 |
| **ETL直连** | 性能高、数据完整 | 耦合度高、需数据库权限 | 批量同步、离线分析 |

### 数据流向

\`\`\`
学校数据源 (API/数据库)
    ↓
SchoolDataClient (API/ETL客户端)
    ↓
FieldMappingService (字段映射)
    ↓
本地同步表 (school_*_sync)
    ↓
业务系统使用
\`\`\`

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


#### 1.2 课程表 (course)

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
- idx_is_passed (is_passed)

**业务规则**:
- 同一学生同一类型考试同一日期只能有一条记录
- 英语类考试需要存储细分项成绩（听说读写译）
- 合格标准: 英语四级425分，六级425分

---

## 接口映射关系

| 接口文档 | 主要对应表 | 关联表 | 同步频率 |
|---------|-----------|--------|---------|
| 行政班基本信息接口 | administrative_class | - | 每学期初 |
| 课程基本信息接口 | course | - | 每学期初 |
| 本专科生课程信息接口 | course | teaching_class | 每学期初 |
| 课程安排信息接口 | course_schedule | teaching_class | 每学期初 |
| 学生选课接口 | course_selection | teaching_class, course | 选课期间每小时 |
| 教师授课接口 | teaching_class | course, course_selection | 每学期初 |
| 本专科生等级考试成绩接口 | level_exam_scores | - | 成绩发布后 |
| 教学计划信息接口 | teaching_plans, teaching_plan_courses | course | 每学年初 |
| 学生课表接口 | course_schedule | teaching_class, course_selection | 每学期初 |

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

