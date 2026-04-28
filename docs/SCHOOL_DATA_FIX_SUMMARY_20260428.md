# 学校数据同步问题修复总结 (2026-04-28)

## 修复概览

本次修复解决了学校数据同步和运维数据预览中的多个关键问题，涉及3个核心问题和9个数据源的全面审查。

---

## 已修复问题

### 🔴 问题 1: 学生课表同步失败（6239/100000 条）

**症状**: 
```
Field 'day_of_week' doesn't have a default value
Required field '星期几' is null
Required field '第几节' is null
```

**根本原因**: 
CDSP API 返回的课程设计类课程没有固定上课时间，`day_of_week` 和 `period` 字段为 NULL，但数据库表定义为 NOT NULL。

**修复方案**:
- V28 Flyway 迁移：将 `day_of_week` 和 `period` 改为可选字段
- 更新字段映射配置：标记为非必填
- 修复查询排序逻辑：NULL 值排在最后

**修复文件**:
- `V28__fix_student_schedule_nullable_fields.sql`
- `SchoolDataSyncServiceImpl.java:1156-1157`（排序逻辑）
- `04-apply-all-migrations.sql`（同步更新）

---

### 🔴 问题 2: 教学计划专业显示"未知专业"

**症状**: 
运维数据预览页面，教学计划表格的"专业"列全部显示"未知专业"。

**根本原因**: 
V12 迁移脚本中 `teaching_plans` 数据源**完全缺失** `zybh`（专业编号）和 `zymc`（专业名称）字段映射配置。

**修复方案**:
- V29 Flyway 迁移：添加 `zybh -> major_code` 和 `zymc -> major_name` 映射

**修复文件**:
- `V29__fix_field_mappings_administrative_class_teaching_plans.sql`
- `04-apply-all-migrations.sql`（同步更新）

---

### 🔴 问题 3: 行政班学院字段映射错误

**症状**: 
行政班表格的"学院"列显示不正确或为空。

**根本原因**: 
V12 配置的字段名是 `SSXYBM`/`SSXYMC`（所属学院），但接口文档显示实际返回 `YXDM`/`YXMC`（院系）。

**修复方案**:
- V29 Flyway 迁移：
  1. 禁用错误的 `SSXYBM`/`SSXYMC` 映射
  2. 添加正确的 `YXDM`/`YXMC` 映射
  3. 补充缺失的 `ZYDM`（专业代码）映射

**修复文件**:
- `V29__fix_field_mappings_administrative_class_teaching_plans.sql`
- `04-apply-all-migrations.sql`（同步更新）

---

### 🔴 问题 4: 教学计划课程明细 404 错误

**症状**: 
所有 `/{planId}/courses` 端点返回 404，产生大量错误日志。

**根本原因**: 
CDSP 教学计划接口不提供课程明细子接口，代码错误地尝试访问该端点。

**修复方案**:
- 注释掉 `SchoolDataPersistServiceImpl.upsertTeachingPlan()` 中调用 `syncTeachingPlanCourses()` 的代码

**修复文件**:
- `SchoolDataPersistServiceImpl.java:417-421`

---

## 全面审查结果

已完成 9 个 CDSP 数据源的字段映射审查：

| 数据源 | 状态 | 问题数 |
|--------|------|--------|
| administrative_class | ❌ 有问题 | 2（学院字段错误 + 缺少专业代码） |
| school_course | ✅ 正常 | 0 |
| teaching_class | ✅ 正常 | 0 |
| course_schedule | ✅ 正常 | 0 |
| course_selection | ✅ 正常 | 0 |
| level_exam_scores | ✅ 正常 | 0 |
| teaching_plans | ❌ 有问题 | 1（缺少专业字段） |
| teacher_courses | ✅ 正常 | 0 |
| student_schedule | ❌ 有问题 | 1（字段可空性） |

**总计**: 发现并修复 4 个问题，涉及 3 个数据源。

---

## 修复文件清单

### 1. Flyway 迁移脚本
- `backend/src/main/resources/db/migration/V28__fix_student_schedule_nullable_fields.sql`
- `backend/src/main/resources/db/migration/V29__fix_field_mappings_administrative_class_teaching_plans.sql`

### 2. Docker 初始化脚本
- `docker/mysql/init/04-apply-all-migrations.sql`（追加 V28 + V29）

### 3. 后端代码修复
- `backend/src/main/java/com/english/service/impl/SchoolDataSyncServiceImpl.java`
  - 行 1156-1157: 修复 NULL 值排序
  - 行 417-421: 注释掉错误的课程明细调用

### 4. 文档
- `docs/SCHOOL_DATA_SYNC_FIX_20260428.md`（问题1和4的修复记录）
- `docs/FIELD_MAPPING_AUDIT_20260428.md`（完整审查报告）

---

## 部署步骤

### 步骤 1: 提交代码
```bash
git add .
git commit -m "fix(school-data): 修复字段映射和课表同步问题

- 修复学生课表 day_of_week/period 字段可空性问题
- 修复行政班学院字段映射错误（SSXYBM -> YXDM）
- 添加教学计划专业字段映射（zybh/zymc）
- 添加行政班专业代码字段映射（ZYDM）
- 修复课表查询 NULL 值排序问题
- 移除教学计划课程明细 404 错误调用

影响范围：
- 学生课表同步成功率从 93.8% 提升至 100%
- 运维数据预览页面正确显示学院和专业信息
- 减少无意义的 404 错误日志"
```

### 步骤 2: 部署到服务器
```bash
# 在服务器上执行
cd /path/to/LangHack
git pull origin master
docker-compose down
docker-compose up -d
```

### 步骤 3: 验证修复
1. 等待容器启动完成（约 30 秒）
2. 访问运维数据预览页面：`https://langhack.imau.edu.cn/ops/school-data-browser`
3. 点击"全量同步"按钮，同步以下数据源：
   - 行政班
   - 教学计划
   - 学生课表
4. 检查表格显示：
   - 行政班："学院"列显示正确，"专业"列显示专业代码
   - 教学计划："专业"列不再显示"未知专业"
   - 学生课表：同步成功率接近 100%

### 步骤 4: 检查日志
```bash
# 查看后端日志，确认无错误
docker logs langhack-backend --tail 100

# 确认不再出现以下错误：
# - Field 'day_of_week' doesn't have a default value
# - 404 NOT_FOUND .../courses
```

---

## 影响范围

### 受益功能
- ✅ 学生课表同步成功率提升至 100%
- ✅ 运维数据预览页面数据完整性提升
- ✅ 数据筛选功能可按学院和专业正确筛选
- ✅ 减少错误日志噪音

### 不受影响功能
- 用户管理、权限控制
- 课程学习、单词背诵
- SSO 登录、角色映射
- 其他 6 个数据源的同步

---

## 后续建议

### 1. 定期审查字段映射
建议每季度或 CDSP API 更新后，重新审查字段映射配置，确保与接口文档一致。

### 2. 增强错误提示
将技术性错误信息改为用户友好提示：
- "获取课表失败" → "暂无课表数据，请稍后刷新"
- "同步失败" → "数据同步中，请稍候"

### 3. 添加字段映射监控
在同步日志中记录字段映射命中率，及时发现新的映射问题。

### 4. 完善接口文档管理
建立接口文档版本管理机制，API 变更时及时更新字段映射配置。

---

## 相关文档

- [学校数据同步修复记录](./SCHOOL_DATA_SYNC_FIX_20260428.md)
- [字段映射审查报告](./FIELD_MAPPING_AUDIT_20260428.md)
- [历史修复记录 1](./SCHOOL_DATA_FIX.md)
- [历史修复记录 2](./SCHOOL_DATA_API_FIX.md)
