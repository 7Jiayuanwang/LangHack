# 学校数据接入 — 官方接口规范文档

> **数据来源**: `E:\项目\兴趣小组\智能与安全社团\服务器使用\数据资源\接口文档\` 目录下的9个官方PDF文档
> **版本**: v2.0
> **创建日期**: 2026-04-15
> **最近修订**: 2026-04-20 — 按 PDF 原文核对，修正认证、URL、xn/xq 格式和字段大小写约定
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

学校教务系统通过 CDSP（校园数据服务平台，cdsp.imau.edu.cn）提供 9 个 REST API 接口，基于 **OData v2** 协议。每个接口使用独立的完整 URL（非共享 base + path 模式），使用 `appId + appSecret + timestamp` 签名认证。

### 9个接口总览

| 序号 | 接口名称 | 完整 URL | 对应数据源 | 对应本地表 |
|------|---------|---------|-----------|-----------|
| 1 | 行政班基本信息 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1710949911983374368` | `administrative_class` | `administrative_class` |
| 2 | 课程基本信息 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715290945064353805` | `school_course` | `course` |
| 3 | 本专科生课程信息 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1721804030860152850` | `teaching_class` | `teaching_class` |
| 4 | 课程安排信息 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715290377801515044` | `course_schedule` | `course_schedule` |
| 5 | 学生选课 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794` | `course_selection` | `course_selection` |
| 6 | 本专科生等级考试成绩 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/DS0076` | `level_exam_scores` | `level_exam_scores` |
| 7 | 教学计划信息 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715289655026466836` | `teaching_plans` | `teaching_plans` |
| 8 | 教师授课 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294645677801497` | `teacher_courses` | `teaching_class`（补充） |
| 9 | 学生课表 | `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294397274341377` | `student_schedule` | `student_schedule` |

> 端点映射在代码中定义于 `SchoolDataProperties.java:97-107`，也可通过 `school.data.api.endpoints` 配置覆盖。

---

## 通用说明

### API 认证方式（2026-04-18 从 Bearer Token 迁移）

```
appId:     <应用ID>
timestamp: <毫秒时间戳，每次请求实时生成>
sign:      Base64( hex(MD5(appId + appSecret + timestamp)).getBytes(UTF-8) )
Content-Type: application/json
Accept:       application/json
```

签名算法：先对 `appId + appSecret + timestamp` 做 MD5 → 得到 32 字符 hex 字符串 → 对该字符串做 Base64 编码。

环境变量：`SCHOOL_API_APP_ID` + `SCHOOL_API_APP_SECRET`（旧 `SCHOOL_API_TOKEN` 已废弃）。

### 请求方式

所有接口均为 `GET` 请求。

### ⚠️ 请求参数规范（核心）

**1. 不支持 OData `$filter` URI customQuery（错误码 `IY1103`）**

CDSP v2 虽基于 OData，但仅支持 OData 标准的分页参数（`$top`, `$skip`），不支持 `$filter`。错误日志示例：

```
HTTP 400 Bad Request
[IY1103] NOT SUPPORTED: URI: customQuery
URL: .../cdsp_XXX?$filter=xh eq '2023122066164' and xn eq '2025-2026'
```

所有条件过滤必须使用 PDF 第 3 节定义的**原生查询参数**（如 `xh`, `xn`, `xq`, `jgh`），以 URL query string 形式传递：

```
✅ .../cdsp_XXX?xh=2023122066164&xn=2025&xq=秋
❌ .../cdsp_XXX?$filter=xh eq '2023122066164'
```

**2. `updatedSince` 参数会被静默忽略**

项目内 `SchoolDataSyncServiceImpl.doIncrementalSync()` 向所有端点传递 `updatedSince=<ISO8601>`，该参数**不在 PDF 也不在 OData 标准中**，学校 API 会忽略该参数并返回全量数据。即"增量同步"实际退化为全量拉取（结合 UPSERT 策略保证最终一致性，功能上无误，但流量和耗时为全量）。

**3. 字段大小写约定（按端点区分！）**

CDSP 端点对参数名和返回字段的大小写敏感，且**每个端点的约定不同**：

| 端点 | 字段大小写 | 示例 |
|------|-----------|------|
| `administrative_class` | **UPPERCASE** | `XZBH`, `BJMC`, `SSXYMC` |
| `teaching_class` | **UPPERCASE** | `KCMC`, `KCH`, `KKYX` |
| `course_selection` | **UPPERCASE** | `XH`, `KSXZDM`, `KCBH` |
| `school_course` | lowercase | `kcdm`, `kcmc`, `kcxf` |
| `course_schedule` | lowercase | `xn`, `xq`, `skxqj`, `skjc`, `jsgh` |
| `teacher_courses` | lowercase | `rkjs`, `kcdm`, `xn`, `xq` |
| `teaching_plans` | lowercase | `zxs`, `skjszgh`, `kkjhid` |
| `level_exam_scores` | lowercase | `xh`, `kskm`, `ksrq`, `fskscj` |
| `student_schedule` | lowercase | `xh`, `xn`, `xq`, `skxqj`, `skjc` |

### ⚠️ 通用查询参数（xn/xq）格式

本项目内部格式与学校 API 格式**不一致**，必须在调用 API 前做转换（`SchoolDataApiClientImpl.toSchoolAcademicYear/toSchoolSemester`）：

| 参数 | 类型 | 学校 API 格式（PDF 权威） | 本项目内部格式 | 转换规则 |
|------|------|------------------------|-------------|---------|
| `xn` | numeric | **单起始年份** 如 `"2025"` | `"2025-2026"` | 取破折号前的部分 |
| `xq` | varchar | **中文汉字** `"秋"`/`"春"`/`"夏"` | `Integer` `1`/`2`/`3` | 1→秋, 2→春, 3→夏 |

PDF 返回示例对照（来自《学生课表接口文档.pdf》第 4 节）：

```json
{
  "学年": "2024",
  "学期": "春"
}
```

### 学期编码映射

| 本地 Integer | 学校 API varchar | 含义 |
|--------------|-----------------|------|
| `1` | `"秋"` | 第一学期（秋季） |
| `2` | `"春"` | 第二学期（春季） |
| `3` | `"夏"` | 第三学期（夏季短学期） |

### 字段命名规范

- **API 返回字段（source_field）**: 小写或大写**拼音缩写**（按端点区分，见上表）
- **本地数据库字段（target_field）**: 使用**下划线命名**（snake_case），如 `class_code`, `course_code`, `academic_year`

### 响应结构

OData v2 标准包装：

```json
{
  "code": 200,
  "msg": "success",
  "value": [ { ...记录1... }, { ...记录2... } ]
}
```

解析策略（`SchoolDataApiClientImpl.parseResponse`）：
1. 先尝试直接解析为 JSON 数组
2. 失败后解析为包装对象，依次取 `value` → `data` → `result` → `records` → `list` → `items` 字段
3. 业务错误码 `code != 200` 记录为 error 并返回空集

---

## 1. 行政班基本信息接口

**完整 URL**: `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1710949911983374368`
**对应数据源**: `administrative_class`
**对应本地表**: `administrative_class`
**字段大小写**: **UPPERCASE**（`XZBH`, `BJMC`, `SSXYMC` 等）

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

**完整 URL**: `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715290945064353805`
**对应数据源**: `school_course`
**对应本地表**: `course`
**字段大小写**: lowercase（`kcdm`, `kcmc`, `kcxf` 等）

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

**完整 URL**: `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1721804030860152850`
**对应数据源**: `teaching_class`
**对应本地表**: `teaching_class`
**字段大小写**: **UPPERCASE**（`KCH`, `KCMC`, `KCXS`, `KCXF`, `KKYX` 等）

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

**完整 URL**: `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715290377801515044`
**对应数据源**: `course_schedule`
**对应本地表**: `course_schedule`
**字段大小写**: lowercase（`xkkch`, `skxqj`, `skjc`, `zcd`, `skdd` 等）

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

**完整 URL**: `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794`
**对应数据源**: `course_selection`
**对应本地表**: `course_selection`
**字段大小写**: **UPPERCASE**（`XH`, `KSXZDM`, `KSXZMC`, `KCBH` 等）

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

**完整 URL**: `https://cdsp.imau.edu.cn/cdsp/data-api/v2/DS0076`
**对应数据源**: `level_exam_scores`
**对应本地表**: `level_exam_scores`
**字段大小写**: lowercase（`xh`, `kskm`, `ksrq`, `fskscj` 等）

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

**完整 URL**: `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715289655026466836`
**对应数据源**: `teaching_plans`
**对应本地表**: `teaching_plans`
**字段大小写**: lowercase（`zxs`, `skjszgh`, `kkjhid`, `kkxn`, `kkxq` 等）

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

**完整 URL**: `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294645677801497`
**对应数据源**: `teacher_courses`（与《本专科生课程信息接口》共同填充 `teaching_class` 表）
**对应本地表**: `teaching_class`
**字段大小写**: lowercase（`rkjsh`, `rkjsxm`, `kcdm`, `xn`, `xq` 等）

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

**完整 URL**: `https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294397274341377`
**对应数据源**: `student_schedule`
**对应本地表**: `student_schedule`（V12 迁移已创建）
**字段大小写**: lowercase

> 学生维度课表数据，包含学生选课后的完整课表信息。

### 请求参数

| 参数 | 类型 | 必填 | 学校 API 格式 | 说明 |
|------|------|------|-------------|------|
| `xh` | varchar | 是 | 如 `"2023122066164"` | 学号 |
| `xn` | numeric | 是 | **单起始年份** `"2025"`（非 `"2025-2026"`） | 学年 |
| `xq` | varchar | 是 | **中文汉字** `"秋"`/`"春"`/`"夏"`（非整数） | 学期 |

**请求示例**：

```
GET .../cdsp_1715294397274341377?xh=2023122066164&xn=2025&xq=秋
```

⚠️ **不要使用 OData `$filter`**（`[IY1103] NOT SUPPORTED: URI: customQuery`）。

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
- [x] 数据库表创建（V12 Flyway 迁移）
- [x] Entity/Mapper 实现（`StudentSchedule` + `StudentScheduleMapper`）
- [x] API 客户端 `fetchStudentSchedules` 方法（参数格式转换已对齐 PDF）
- [x] 同步服务集成（`SchoolDataSyncServiceImpl.syncStudentSchedule`）
- [x] 字段映射配置（V12 Part 3）
- [x] 前端学生课表页面（`StudentScheduleController` + 前端已实现）

---

## 字段映射总表

> 字段映射的权威存储位置：`field_mappings` 表（由 V12 Flyway 迁移 Part 3 seed）。
> 如需调整某端点的 source_field，请先对照 PDF 原文确认大小写，再通过运维面板或 Flyway 补丁修改。

### 按端点字段大小写索引

| 数据源 | source_field 大小写 | 典型字段 | V12 初始化状态 |
|-------|-------------------|---------|--------------|
| `administrative_class` | **UPPERCASE** | `XZBH`, `BJMC`, `SSXYMC`, `SSNJ`, `BZRGH` | V12 Part 3 |
| `school_course` | lowercase | `kcdm`, `kcmc`, `kcxf`, `kslx`, `kkyx` | V12 Part 3 |
| `teaching_class` | **UPPERCASE** | `KCH`, `KCMC`, `KCXS`, `KCXF`, `KKYX` | V12 Part 3 |
| `course_schedule` | lowercase | `xkkch`, `skxqj`, `skjc`, `zcd`, `skdd` | V12 Part 3 |
| `course_selection` | **UPPERCASE** | `XH`, `KSXZDM`, `KSXZMC`, `KCBH` | V12 Part 3 |
| `level_exam_scores` | lowercase | `xh`, `kskm`, `ksrq`, `fskscj` | V12 Part 3 |
| `teaching_plans` | lowercase | `zxs`, `kkjhid`, `kkxn`, `kkxq`, `skjszgh` | V12 Part 3 |
| `teacher_courses` | lowercase | `rkjsh`, `rkjsxm`, `kcdm`, `xn`, `xq` | V12 Part 3 |
| `student_schedule` | lowercase | `xh`, `xn`, `xq`, `skxqj`, `skjc`, `cdmc` | V12 Part 3 |

### 历史差异说明（已在 v2.0 更正）

v1.0 版本中的 "V12 vs 官方规范差异表" 基于"所有字段应为小写"的错误假设，误将部分 UPPERCASE 端点（`administrative_class`, `teaching_class`, `course_selection`）标记为需要改小写。**实际上这三个端点的 UPPERCASE 源字段是正确的**，不应修改。

2026-04-20 v2.0 修订后，权威顺序：
1. **PDF 原文**（`E:\项目\兴趣小组\智能与安全社团\服务器使用\数据资源\接口文档\`）
2. **本文档**（与 PDF 保持同步）
3. **V12 Flyway 迁移**（Part 3 INSERT 语句，与本文档保持同步）

如发现三者不一致，以 PDF 为准并同步更新另两者。

---

## 修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-04-20 | v2.0 | **重大修订**：按 PDF 原文核对，修正：①API base URL 改为 `cdsp.imau.edu.cn/cdsp/data-api/v2/`，每端点独立完整 URL；② 认证从 Bearer Token 改为 `appId + timestamp + sign` 签名；③ `xn` 格式为单起始年份（非 `2025-2026`）；④ `xq` 格式为中文汉字 `秋/春/夏`（非整数 `3/12/16`）；⑤ 新增 IY1103 错误说明（不支持 OData `$filter`）；⑥ 新增 `updatedSince` 被忽略说明；⑦ 新增每端点字段大小写约定表（UPPERCASE vs lowercase） |
| 2026-04-15 | v1.0 | 初始版本，基于9个官方PDF文档整理（注：当时对认证方式和 xn/xq 格式描述有误，已在 v2.0 更正） |
