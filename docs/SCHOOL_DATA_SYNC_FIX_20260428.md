# 学校数据同步问题修复记录 (2026-04-28)

## 问题概述

学生课表同步出现大量失败（6239/100000 条），同时教学计划课程明细接口持续返回 404 错误。

## 问题 1：学生课表同步失败

### 症状
```
Field 'day_of_week' doesn't have a default value
Required field '星期几' is null for data source: student_schedule
Required field '第几节' is null for data source: student_schedule
```

### 根本原因
CDSP API 返回的某些课程（如"灌溉排水工程学课程设计"）没有固定上课时间，导致"星期几"和"第几节"字段为 null。但数据库表 `student_schedule` 的 `day_of_week` 和 `period` 列定义为 NOT NULL，导致插入失败。

### 修复方案
1. **数据库层面**：将 `day_of_week` 和 `period` 改为可选字段（允许 NULL）
2. **字段映射层面**：更新 `field_mappings` 表，将这两个字段的 `is_required` 标记为 0

### 修复文件
- `backend/src/main/resources/db/migration/V28__fix_student_schedule_nullable_fields.sql`
- `docker/mysql/init/04-apply-all-migrations.sql`（同步更新）

### 修复内容
```sql
-- 修改表结构
ALTER TABLE student_schedule
    MODIFY COLUMN day_of_week TINYINT NULL COMMENT '星期几 1-7，NULL 表示无固定时间',
    MODIFY COLUMN period VARCHAR(20) NULL COMMENT '第几节，NULL 表示无固定时间';

-- 更新字段映射
UPDATE field_mappings
SET is_required = 0
WHERE data_source = 'student_schedule'
  AND target_field IN ('day_of_week', 'period');
```

## 问题 2：教学计划课程明细 404 错误

### 症状
```
学校数据 API HTTP 错误 url=https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715289655026466836/{planId}/courses 
status=404 NOT_FOUND
```

所有 planId 的课程明细请求均返回 404（1125149, 1125156, 1141924, 1148172, 1148179, 1190549, 1190585, 1193681, 1193746, 1193754 等）。

### 根本原因
根据 CDSP 接口文档（`教学计划信息接口文档.pdf`），教学计划接口只返回计划基本信息，**不提供课程明细子接口**。代码中尝试访问 `/{planId}/courses` 是错误的假设。

### 修复方案
注释掉 `SchoolDataPersistServiceImpl.upsertTeachingPlan()` 中调用 `syncTeachingPlanCourses()` 的代码，避免产生大量无意义的 404 错误日志。

### 修复文件
- `backend/src/main/java/com/english/service/impl/SchoolDataPersistServiceImpl.java:417-419`

### 修复内容
```java
// 注释掉：CDSP 教学计划接口不提供课程明细子接口（/{planId}/courses 返回 404）
// 根据接口文档，教学计划信息接口只返回计划基本信息，不包含课程列表
// if (entity.getPlanId() != null) {
//     syncTeachingPlanCourses(entity.getPlanId());
// }
```

## 验证方法

### 问题 1 验证
1. 部署修复后的代码
2. 执行学生课表全量同步：`POST /api/admin/sync/full?dataSource=student_schedule`
3. 检查同步日志，确认失败率降至 0 或接近 0
4. 查询数据库：
   ```sql
   SELECT COUNT(*) FROM student_schedule WHERE day_of_week IS NULL OR period IS NULL;
   ```
   应返回非零值（课程设计类课程）

### 问题 2 验证
1. 部署修复后的代码
2. 执行教学计划全量同步：`POST /api/admin/sync/full?dataSource=teaching_plans`
3. 检查日志，确认不再出现 404 错误

## 影响范围
- **学生课表**：修复后可正常同步所有课程，包括无固定时间的课程设计类课程
- **教学计划**：不再产生无意义的 404 错误日志，减少日志噪音
- **向后兼容**：已有数据不受影响，新数据可正常插入

## 相关文档
- CDSP 接口文档：`E:\项目\兴趣小组\智能与安全社团\服务器使用\数据资源\接口文档\学生课表接口文档.pdf`
- CDSP 接口文档：`E:\项目\兴趣小组\智能与安全社团\服务器使用\数据资源\接口文档\教学计划信息接口文档.pdf`
- 历史修复记录：`docs/SCHOOL_DATA_FIX.md`
- 历史修复记录：`docs/SCHOOL_DATA_API_FIX.md`
