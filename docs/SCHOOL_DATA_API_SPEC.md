# 学校数据接入 — 官方接口规范文档

> **数据来源**: `E:\项目\兴趣小组\智能与安全社团\服务器使用\数据资源\接口文档\` 目录下的9个官方PDF文档
> **版本**: v1.0
> **创建日期**: 2026-04-15
> **原则**: 本文档为字段映射的唯一事实来源（Single Source of Truth），所有代码和SQL必须与此文档保持一致

---

## 目录

1. [概述](#概述)
2. [通用说明](#通用说明)
3. [行政班基本信息接口](#1-行政班基本信息接口)
4. [课程基本信息接口](#2-课程基本信息接口)
5. [本专科生课程信息接口](#3-本专科生课程信息接口)
6. [课程安排信息接口](#4-课程安排信息接口)
7. [学生选课接口](#5-学生选课接口)
8. [本专科生等级考试成绩接口](#6-本专科生等级考试成绩接口)
9. [教学计划信息接口](#7-教学计划信息接口)
10. [教师授课接口](#8-教师授课接口)
11. [学生课表接口](#9-学生课表接口)
12. [字段映射总表](#字段映射总表)

---

## 概述

学校教务系统提供9个REST API接口，通过 `https://api.imau.edu.cn` 基础URL访问，使用Bearer Token认证。

### 9个接口总览

| 序号 | 接口名称 | API路径 | 对应数据源 | 对应本地表 |
|------|---------|---------|-----------|-----------|
| 1 | 行政班基本信息 | `/api/xzxx` | `administrative_class` | `administrative_class` |
| 2 | 课程基本信息 | `/api/kcxx` | `school_course` | `course` |
| 3 | 本专科生课程信息 | `/api/bzkcxx` | `teaching_class` | `teaching_class` |
| 4 | 课程安排信息 | `/api/kcap` | `course_schedule` | `course_schedule` |
| 5 | 学生选课 | `/api/xsxk` | `course_selection` | `course_selection` |
| 6 | 本专科生等级考试成绩 | `/api/djcj` | `level_exam_scores` | `level_exam_scores` |
| 7 | 教学计划信息 | `/api/jxjh` | `teaching_plans` | `teaching_plans` |
| 8 | 教师授课 | `/api/jssk` | `teaching_class`（补充） | `teaching_class` |
| 9 | 学生课表 | `/api/xskb` | 新数据源 | 新表 |

---

## 通用说明

### API认证方式

```
Authorization: Bearer <token>
Content-Type: application/json
```

### 请求方式

所有接口均为 `GET` 请求。

### 字段命名规范

- **API返回字段（source_field）**: 全部为**小写拼音缩写**，如 `xzbh`、`kcbh`、`xn`
- **本地数据库字段（target_field）**: 使用**下划线命名**（snake_case），如 `class_code`、`course_code`、`academic_year`

### 通用查询参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `xn` | String | 学年，如 "2025-2026" |
| `xq` | String | 学期：3=第一学期, 12=第二学期, 16=第三学期 |

### 学期编码说明

| 值 | 含义 |
|----|------|
| 3 | 第一学期（秋季） |
| 12 | 第二学期（春季） |
| 16 | 第三学期（夏季短学期） |

---

## 1. 行政班基本信息接口

**API路径**: `/api/xzxx`
**对应数据源**: `administrative_class`
**对应本地表**: `administrative_class`

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `xn` | String | 否 | 学年 |
| `xq` | String | 否 | 学期 |

### 返回字段

| API字段(source) | 本地字段(target) | 类型 | 必填 | 说明 |
|----------------|-----------------|------|------|------|
| `xzbh` | `class_code` | STRING | 是 | 行政班编号 |
| `xzbmc` | `class_name` | STRING | 是 | 行政班名称 |
| `xzbjc` | `class_short_name` | STRING | 否 | 行政班简称 |
| `yxbh` | `college_code` | STRING | 是 | 学院编号 |
| `yxmc` | `college_name` | STRING | 是 | 学院名称 |
| `zybh` | `major_code` | STRING | 是 | 专业编号 |
| `zymc` | `major_name` | STRING | 是 | 专业名称 |
| `nj` | `grade` | STRING | 是 | 年级 |
| `bjlx` | `class_type` | STRING | 否 | 班级类型 |
| `bzrgh` | `head_teacher_code` | STRING | 否 | 班主任工号 |
| `bzrxm` | `head_teacher_name` | STRING | 否 | 班主任姓名 |
| `xsrs` | `student_count` | NUMBER | 否 | 学生人数 |
| `nsrs` | `nsrs` | NUMBER | 否 | 男生人数 |
| `nsrs_nv` | `female_count` | NUMBER | 否 | 女生人数 |
| `bjzt` | `class_status` | STRING | 是 | 班级状态 |
| `clrq` | `establish_date` | STRING→DATE | 否 | 成立日期，转换器DATE，格式`yyyy-MM-dd` |
| `byrq` | `graduate_date` | STRING→DATE | 否 | 毕业日期，转换器DATE，格式`yyyy-MM-dd` |
| `bz` | `remark` | STRING | 否 | 备注 |

### 唯一键: `class_code`
### UPSERT策略: ON DUPLICATE KEY UPDATE

---

## 2. 课程基本信息接口

**API路径**: `/api/kcxx`
**对应数据源**: `school_course`
**对应本地表**: `course`

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `xn` | String | 否 | 学年 |
| `xq` | String | 否 | 学期 |

### 返回字段

| API字段(source) | 本地字段(target) | 类型 | 必填 | 转换器 | 说明 |
|----------------|-----------------|------|------|--------|------|
| `kcbh` | `course_code` | STRING | 是 | NONE | 课程编号 |
| `kcmc` | `course_name` | STRING | 是 | NONE | 课程名称 |
| `kcywmc` | `course_name_en` | STRING | 否 | NONE | 课程英文名称 |
| `kcjc` | `course_short_name` | STRING | 否 | NONE | 课程简称 |
| `kcxz` | `course_nature` | STRING | 是 | NONE | 课程性质 |
| `kclb` | `course_category` | STRING | 是 | NONE | 课程类别 |
| `xf` | `credits` | NUMBER | 是 | NONE | 学分 |
| `zxs` | `total_hours` | NUMBER | 是 | NONE | 总学时 |
| `llxs` | `theory_hours` | NUMBER | 否 | NONE | 理论学时 |
| `syxs` | `experiment_hours` | NUMBER | 否 | NONE | 实验学时 |
| `sjxs` | `practice_hours` | NUMBER | 否 | NONE | 实践学时 |
| `sjxs_pc` | `computer_hours` | NUMBER | 否 | NONE | 上机学时 |
| `kkyxbh` | `college_code` | STRING | 是 | NONE | 开课学院编号 |
| `kkyxmc` | `college_name` | STRING | 是 | NONE | 开课学院名称 |
| `kkxbbh` | `department_code` | STRING | 否 | NONE | 开课系部编号 |
| `kkxbmc` | `department_name` | STRING | 否 | NONE | 开课系部名称 |
| `kcfzrgh` | `leader_code` | STRING | 否 | NONE | 课程负责人工号 |
| `kcfzrxm` | `leader_name` | STRING | 否 | NONE | 课程负责人姓名 |
| `khfs` | `assessment_method` | STRING | 是 | NONE | 考核方式 |
| `sfsyjx` | `is_bilingual` | STRING | 否 | BOOLEAN | 是否双语教学 |
| `sfwlkc` | `is_online` | STRING | 否 | BOOLEAN | 是否网络课程 |
| `syzy` | `applicable_majors` | STRING | 否 | SPLIT | 适用专业，分隔符`,` |
| `xxkcbh` | `prerequisite_codes` | STRING | 否 | SPLIT | 先修课程编号，分隔符`,` |
| `xxkcmc` | `prerequisite_names` | STRING | 否 | SPLIT | 先修课程名称，分隔符`,` |
| `kcjj` | `course_intro` | STRING | 否 | NONE | 课程简介 |
| `jxmb` | `teaching_objectives` | STRING | 否 | NONE | 教学目标 |
| `kczt` | `course_status` | STRING | 是 | NONE | 课程状态 |

### BOOLEAN转换器配置
```json
{"trueValues":["是","1","true"],"falseValues":["否","0","false"]}
```

### SPLIT转换器配置
```json
{"delimiter":","}
```

### 唯一键: `course_code`
### UPSERT策略: ON DUPLICATE KEY UPDATE

---

## 3. 本专科生课程信息接口

**API路径**: `/api/bzkcxx`
**对应数据源**: `teaching_class`
**对应本地表**: `teaching_class`

> 本接口提供教学班信息，一个课程可以有多个教学班。

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `xn` | String | 是 | 学年 |
| `xq` | String | 是 | 学期 |

### 返回字段

| API字段(source) | 本地字段(target) | 类型 | 必填 | 说明 |
|----------------|-----------------|------|------|------|
| `jxbid` | `class_id` | STRING | 是 | 教学班号 |
| `jxbmc` | `class_name` | STRING | 否 | 教学班名称 |
| `kcbh` | `course_code` | STRING | 是 | 课程编号 |
| `kcmc` | `course_name` | STRING | 是 | 课程名称 |
| `jgh` | `teacher_id` | STRING | 是 | 授课教师工号 |
| `xm` | `teacher_name` | STRING | 是 | 授课教师姓名 |
| `xn` | `academic_year` | STRING | 是 | 学年 |
| `xq` | `semester` | NUMBER | 是 | 学期 |
| `bjrl` | `capacity` | NUMBER | 否 | 班级容量 |
| `yxrs` | `enrolled_count` | NUMBER | 否 | 已选人数 |
| `kczt` | `class_status` | STRING | 是 | 课程状态 |

### 唯一键: `class_id`
### UPSERT策略: ON DUPLICATE KEY UPDATE
### 外键: `course_code` → `course(course_code)`

---

## 4. 课程安排信息接口

**API路径**: `/api/kcap`
**对应数据源**: `course_schedule`
**对应本地表**: `course_schedule`

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `xn` | String | 是 | 学年 |
| `xq` | String | 是 | 学期 |

### 返回字段

| API字段(source) | 本地字段(target) | 类型 | 必填 | 说明 |
|----------------|-----------------|------|------|------|
| `jxbid` | `class_id` | STRING | 是 | 教学班号 |
| `skxqj` | `day_of_week` | NUMBER | 是 | 上课星期几 (1-7) |
| `skjc` | `period` | STRING | 是 | 上课节次（如"1-2"） |
| `zcd` | `week_range` | STRING | 是 | 周次（如"1-16周"） |
| `cdmc` | `class_location` | STRING | 是 | 上课地点名称 |
| `xn` | `academic_year` | STRING | 是 | 学年 |
| `xq` | `semester` | NUMBER | 是 | 学期 |

### 注意事项
- 一个教学班可以有多个时间安排
- 星期几: 1=周一, 7=周日
- 节次格式: "1-2" 表示第1-2节连续上课
- 周次格式: "1-16周" 或 "1-16周(单)" 或 "1-16周(双)"
- 无自然唯一键，同步策略为 DELETE + INSERT（按class_id+semester）

### 外键: `class_id` → `teaching_class(class_id)`

---

## 5. 学生选课接口

**API路径**: `/api/xsxk`
**对应数据源**: `course_selection`
**对应本地表**: `course_selection`

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `xh` | String | 否 | 学号（不传则返回全部） |
| `xn` | String | 是 | 学年 |
| `xq` | String | 是 | 学期 |

### 返回字段

| API字段(source) | 本地字段(target) | 类型 | 必填 | 说明 |
|----------------|-----------------|------|------|------|
| `xh` | `student_id` | STRING | 是 | 学号 |
| `kcbh` | `course_code` | STRING | 是 | 课程编号 |
| `kcmc` | `course_name` | STRING | 是 | 课程名称 |
| `jhbh` | `plan_id` | STRING | 是 | 计划编号 |
| `xn` | `academic_year` | STRING | 是 | 学年 |
| `xq` | `semester` | NUMBER | 是 | 学期 |
| `xf` | `credits` | NUMBER | 是 | 学分 |
| `cjj` | `total_grade` | NUMBER | 否 | 成绩 |
| `kcxz` | `course_nature` | STRING | 否 | 课程性质 |
| `jxbid` | `class_id` | STRING | 否 | 教学班号 |
| `jgh` | `teacher_id` | STRING | 否 | 教师工号 |
| `xm` | `teacher_name` | STRING | 否 | 教师姓名 |

### 唯一键: `selection_id`（自动生成）
### UPSERT策略: ON DUPLICATE KEY UPDATE
### 外键: `course_code` → `course(course_code)`, `class_id` → `teaching_class(class_id)`

---

## 6. 本专科生等级考试成绩接口

**API路径**: `/api/djcj`
**对应数据源**: `level_exam_scores`
**对应本地表**: `level_exam_scores`

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `xh` | String | 否 | 学号（不传则返回全部） |

### 返回字段

| API字段(source) | 本地字段(target) | 类型 | 必填 | 转换器 | 说明 |
|----------------|-----------------|------|------|--------|------|
| `xh` | `student_id` | STRING | 是 | NONE | 学号 |
| `xm` | `student_name` | STRING | 是 | NONE | 姓名 |
| `ksbh` | `exam_id` | STRING | 是 | NONE | 考试编号 |
| `ksmc` | `exam_name` | STRING | 是 | NONE | 考试名称 |
| `ksrq` | `exam_date` | STRING | 是 | DATE | 考试日期，格式`yyyy-MM-dd` |
| `cj` | `score` | NUMBER | 是 | NONE | 成绩 |
| `dj` | `grade` | STRING | 否 | NONE | 等级 |
| `zkzh` | `admission_ticket_no` | STRING | 否 | NONE | 准考证号 |
| `bz` | `remark` | STRING | 否 | NONE | 备注 |

### DATE转换器配置
```json
{"pattern":"yyyy-MM-dd"}
```

### 唯一键: `(student_id, exam_type, exam_date)`

---

## 7. 教学计划信息接口

**API路径**: `/api/jxjh`
**对应数据源**: `teaching_plans`
**对应本地表**: `teaching_plans`

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `zybh` | String | 否 | 专业编号 |

### 返回字段

| API字段(source) | 本地字段(target) | 类型 | 必填 | 说明 |
|----------------|-----------------|------|------|------|
| `jhbh` | `plan_id` | STRING | 是 | 计划编号 |
| `jhmc` | `plan_name` | STRING | 是 | 计划名称 |
| `zybh` | `major_code` | STRING | 是 | 专业代码 |
| `zymc` | `major_name` | STRING | 是 | 专业名称 |
| `nj` | `grade` | STRING | 是 | 年级 |
| `xz` | `schooling_length` | NUMBER | 是 | 学制 |
| `xwlx` | `degree_type` | STRING | 是 | 学位类型 |
| `pycc` | `education_level` | STRING | 是 | 培养层次 |
| `yxbh` | `college_code` | STRING | 是 | 学院代码 |
| `yxmc` | `college_name` | STRING | 是 | 学院名称 |
| `zxf` | `total_credits` | NUMBER | 是 | 总学分 |
| `bxxf` | `required_credits` | NUMBER | 是 | 必修学分 |
| `xxxf` | `elective_credits` | NUMBER | 是 | 选修学分 |
| `sjxf` | `practice_credits` | NUMBER | 否 | 实践学分 |
| `jhzt` | `plan_status` | STRING | 是 | 计划状态 |

### 唯一键: `plan_id`
### UPSERT策略: ON DUPLICATE KEY UPDATE

---

## 8. 教师授课接口

**API路径**: `/api/jssk`
**对应数据源**: `teaching_class`（与本专科课程信息接口共同填充teaching_class表）
**对应本地表**: `teaching_class`

> 此接口补充教师与教学班的关联信息，与接口3（本专科生课程信息）共同维护teaching_class表。

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `jgh` | String | 否 | 教师工号（不传则返回全部） |
| `xn` | String | 是 | 学年 |
| `xq` | String | 是 | 学期 |

### 返回字段

| API字段(source) | 本地字段(target) | 类型 | 必填 | 说明 |
|----------------|-----------------|------|------|------|
| `jgh` | `teacher_id` | STRING | 是 | 教师工号 |
| `xm` | `teacher_name` | STRING | 是 | 教师姓名 |
| `jxbid` | `class_id` | STRING | 是 | 教学班号 |
| `kcbh` | `course_code` | STRING | 是 | 课程编号 |
| `kcmc` | `course_name` | STRING | 是 | 课程名称 |
| `xn` | `academic_year` | STRING | 是 | 学年 |
| `xq` | `semester` | NUMBER | 是 | 学期 |

### 同步说明
- 此接口与接口3共享 `teaching_class` 表
- 接口3提供完整的教学班信息（包含容量、已选人数等），作为主数据源
- 此接口提供教师维度的授课信息，可用于交叉验证

---

## 9. 学生课表接口

**API路径**: `/api/xskb`
**对应数据源**: `student_schedule`（新数据源）
**对应本地表**: `student_schedule`（新表，需创建）

> 此接口为学生维度课表数据，包含学生选课后的完整课表信息。当前系统中尚未实现此接口的数据同步。

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `xh` | String | 是 | 学号 |
| `xn` | String | 是 | 学年 |
| `xq` | String | 是 | 学期 |

### 返回字段

| API字段(source) | 本地字段(target) | 类型 | 必填 | 说明 |
|----------------|-----------------|------|------|------|
| `xh` | `student_id` | STRING | 是 | 学号 |
| `xm` | `student_name` | STRING | 是 | 学生姓名 |
| `jxbid` | `class_id` | STRING | 是 | 教学班号 |
| `kcbh` | `course_code` | STRING | 是 | 课程编号 |
| `kcmc` | `course_name` | STRING | 是 | 课程名称 |
| `jgh` | `teacher_id` | STRING | 是 | 教师工号 |
| `jsxm` | `teacher_name` | STRING | 是 | 教师姓名 |
| `skxqj` | `day_of_week` | NUMBER | 是 | 上课星期几 (1-7) |
| `skjc` | `period` | STRING | 是 | 上课节次（如"1-2"） |
| `zcd` | `week_range` | STRING | 是 | 周次（如"1-16周"） |
| `cdmc` | `class_location` | STRING | 是 | 上课地点名称 |
| `xn` | `academic_year` | STRING | 是 | 学年 |
| `xq` | `semester` | NUMBER | 是 | 学期 |

### 实现状态
- [ ] 数据库表创建（Flyway迁移）
- [ ] Entity/Mapper实现
- [ ] API客户端fetchStudentSchedules方法
- [ ] 同步服务集成
- [ ] 字段映射配置
- [ ] 前端学生课表页面

---

## 字段映射总表

### 当前V12迁移 vs 官方规范 差异对照

以下列出当前V12迁移文件中的source_field与官方API实际返回字段的差异：

#### 1. administrative_class — 无差异 ✅

V12已使用正确的小写拼音缩写（`xzbh`, `xzbmc` 等）。

#### 2. school_course — 无差异 ✅

V12已使用正确的小写拼音缩写（`kcbh`, `kcmc` 等）。

#### 3. teaching_class — 字段名大小写差异 ⚠️

| V12当前source_field | 官方API实际字段 | 差异 |
|--------------------|----------------|------|
| `JXBID` | `jxbid` | 大写→小写 |
| `JXBMC` | `jxbmc` | 大写→小写 |
| `KCBH` | `kcbh` | 大写→小写 |
| `KCMC` | `kcmc` | 大写→小写 |
| `JGH` | `jgh` | 大写→小写 |
| `XM` | `xm` | 大写→小写 |
| `XNM` | `xn` | 大写+多字符→小写缩写 |
| `XQM` | `xq` | 大写+多字符→小写缩写 |
| `BJRL` | `bjrl` | 大写→小写 |
| `YXRS` | `yxrs` | 大写→小写 |
| `KCZT` | `kczt` | 大写→小写 |

#### 4. course_schedule — 字段名差异 ⚠️

| V12当前source_field | 官方API实际字段 | 差异 |
|--------------------|----------------|------|
| `JXBID` | `jxbid` | 大写→小写 |
| `XQJ` | `skxqj` | **字段名不同！** `XQJ`应为`skxqj` |
| `JCS` | `skjc` | **字段名不同！** `JCS`应为`skjc` |
| `ZCD` | `zcd` | 大写→小写 |
| `CDMC` | `cdmc` | 大写→小写 |
| `XNM` | `xn` | 大写+多字符→小写缩写 |
| `XQM` | `xq` | 大写+多字符→小写缩写 |

#### 5. course_selection — 字段名大小写+缩写差异 ⚠️

| V12当前source_field | 官方API实际字段 | 差异 |
|--------------------|----------------|------|
| `XH` | `xh` | 大写→小写 |
| `KCBH` | `kcbh` | 大写→小写 |
| `KCMC` | `kcmc` | 大写→小写 |
| `JHBH` | `jhbh` | 大写→小写 |
| `XNM` | `xn` | 大写+多字符→小写缩写 |
| `XQM` | `xq` | 大写+多字符→小写缩写 |
| `XF` | `xf` | 大写→小写 |
| `CJJ` | `cjj` | 大写→小写 |
| `KCXZ` | `kcxz` | 大写→小写 |
| `JXBID` | `jxbid` | 大写→小写 |
| `JGH` | `jgh` | 大写→小写 |
| `XM` | `xm` | 大写→小写 |

#### 6. level_exam_scores — 字段名大小写差异 ⚠️

| V12当前source_field | 官方API实际字段 | 差异 |
|--------------------|----------------|------|
| `XH` | `xh` | 大写→小写 |
| `XM` | `xm` | 大写→小写 |
| `KSBH` | `ksbh` | 大写→小写 |
| `KSMC` | `ksmc` | 大写→小写 |
| `KSRQ` | `ksrq` | 大写→小写 |
| `CJ` | `cj` | 大写→小写 |
| `DJ` | `dj` | 大写→小写 |
| `ZKZH` | `zkzh` | 大写→小写 |
| `BZ` | `bz` | 大写→小写 |

#### 7. teaching_plans — 字段名大小写差异 ⚠️

| V12当前source_field | 官方API实际字段 | 差异 |
|--------------------|----------------|------|
| `JHBH` | `jhbh` | 大写→小写 |
| `JHMC` | `jhmc` | 大写→小写 |
| `ZYBH` | `zybh` | 大写→小写 |
| `ZYMC` | `zymc` | 大写→小写 |
| `NJ` | `nj` | 大写→小写 |
| `XZ` | `xz` | 大写→小写 |
| `XWLX` | `xwlx` | 大写→小写 |
| `PYCC` | `pycc` | 大写→小写 |
| `YXBH` | `yxbh` | 大写→小写 |
| `YXMC` | `yxmc` | 大写→小写 |
| `ZXF` | `zxf` | 大写→小写 |
| `BXXF` | `bxxf` | 大写→小写 |
| `XXXF` | `xxxf` | 大写→小写 |
| `SJXF` | `sjxf` | 大写→小写 |
| `JHZT` | `jhzt` | 大写→小写 |

### 差异总结

1. **全局问题**: V12中所有source_field使用**大写**，但官方API返回**小写** — 需全部改为小写
2. **严重问题**: 课程安排接口中有2个字段名完全不同：
   - `XQJ` → 应为 `skxqj`（上课星期几）
   - `JCS` → 应为 `skjc`（上课节次）
3. **缩写差异**: 学年/学期字段在V12中为 `XNM`/`XQM`，官方API为 `xn`/`xq`
4. **新增接口**: 学生课表接口（`/api/xskb`）尚未实现
5. **新增接口**: 教师授课接口（`/api/jssk`）尚未纳入字段映射

---

## 修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-04-15 | v1.0 | 初始版本，基于9个官方PDF文档整理 |
