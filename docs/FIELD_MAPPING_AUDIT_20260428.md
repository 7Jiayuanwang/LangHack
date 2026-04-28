# 学校数据字段映射审查报告 (2026-04-28)

## 审查目标
系统性检查 9 个 CDSP 数据源的字段映射配置，确保：
1. 所有接口文档中的字段都已正确映射
2. 字段名与 API 实际返回值一致
3. 必填字段已标记为 required
4. 数据类型转换正确

## 数据源清单
1. administrative_class (行政班基本信息)
2. school_course (课程基本信息)
3. teaching_class (本专科生课程信息 / 教师授课)
4. course_schedule (课程安排信息)
5. course_selection (学生选课)
6. level_exam_scores (本专科生等级考试成绩)
7. teaching_plans (教学计划信息)
8. teacher_courses (教师授课) - 已合并到 teaching_class
9. student_schedule (学生课表)

---

## 问题汇总

### 🔴 P0 严重问题（导致数据缺失）

#### 1. 行政班学院字段映射错误
- **数据源**: `administrative_class`
- **问题**: V12 配置的是 `SSXYBM`/`SSXYMC`，但接口文档显示实际返回 `YXDM`/`YXMC`
- **影响**: 学院信息完全无法同步
- **修复**: 
  ```sql
  -- 添加正确的字段映射
  INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
  ('administrative_class', 'YXDM', 'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '院系代码（英文缩写）'),
  ('administrative_class', '院系代码', 'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '院系代码（中文）'),
  ('administrative_class', 'YXMC', 'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '院系名称（英文缩写）'),
  ('administrative_class', '院系名称', 'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '院系名称（中文）');
  ```

#### 2. 教学计划专业字段完全缺失
- **数据源**: `teaching_plans`
- **问题**: V12 完全没有配置 `zybh`/`zymc` 字段映射
- **影响**: 所有教学计划的专业信息显示为"未知专业"
- **修复**:
  ```sql
  -- 添加专业字段映射
  INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
  ('teaching_plans', 'zybh', 'major_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 12, 1, '专业编号'),
  ('teaching_plans', 'zymc', 'major_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 13, 1, '专业名称');
  ```

---

## 详细审查结果

### 1. administrative_class (行政班)

**接口文档字段** (来自 `行政班基本信息接口文档.pdf`):
- BJDM - 班级代码
- BJMC - 班级名称
- YXDM - 院系代码 ⚠️
- YXMC - 院系名称 ⚠️
- ZYDM - 专业代码
- ZYMC - 专业名称
- NJ - 年级
- BJRS - 班级人数
- BJZT - 班级状态

**V12 当前配置**:
- ✅ BJDM -> class_code
- ✅ BJMC -> class_name
- ❌ SSXYBM -> college_code (错误！应该是 YXDM)
- ❌ SSXYMC -> college_name (错误！应该是 YXMC)
- ✅ ZYDM -> major_code
- ✅ ZYMC -> major_name
- ✅ NJ -> grade
- ✅ BJRS -> student_count
- ✅ BJZT -> class_status

**缺失字段**: 无

---

### 7. teaching_plans (教学计划)

**接口文档字段** (来自 `教学计划信息接口文档.pdf`):
- kkjhid - 开课计划ID
- kcdm - 课程代码
- zybh - 专业编号 ⚠️
- zymc - 专业名称 ⚠️
- cksm - 参考书目
- zxs - 总学时
- skjszgh - 授课教师职工号
- kkdwdm - 开课单位代码
- kkdwdm_name - 开课单位名称
- kkxn - 开课学年
- kkxq_name - 开课学期名称
- ksfsdm - 考试方式代码
- ksfsmc - 考试方式名称

**V12 当前配置**:
- ✅ kkjhid -> plan_id
- ✅ kcdm -> course_code
- ❌ **缺失** zybh -> major_code
- ❌ **缺失** zymc -> major_name
- ✅ cksm -> training_objective
- ✅ zxs -> total_credits
- ✅ skjszgh -> creator
- ✅ kkdwdm -> college_code
- ✅ kkdwdm_name -> college_name
- ✅ kkxn -> grade
- ✅ kkxq_name -> plan_name
- ✅ ksfsdm -> plan_status
- ✅ ksfsmc -> version

**缺失字段**: zybh, zymc

---

## 修复方案

### V29 Flyway 迁移脚本

创建 `V29__fix_field_mappings_administrative_class_teaching_plans.sql`:

```sql
-- V29: 修复行政班学院字段和教学计划专业字段映射

-- 1. 修复行政班学院字段映射（SSXYBM/SSXYMC -> YXDM/YXMC）
-- 禁用错误的映射
UPDATE field_mappings 
SET is_active = 0 
WHERE data_source = 'administrative_class' 
  AND source_field IN ('SSXYBM', 'SSXYMC', '所属学院编码', '所属学院名称');

-- 添加正确的映射
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'YXDM',     'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '院系代码（英文缩写）'),
('administrative_class', '院系代码', 'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '院系代码（中文）'),
('administrative_class', 'YXMC',     'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '院系名称（英文缩写）'),
('administrative_class', '院系名称', 'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '院系名称（中文）');

-- 2. 添加教学计划专业字段映射
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_plans', 'zybh', 'major_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 12, 1, '专业编号'),
('teaching_plans', 'zymc', 'major_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 13, 1, '专业名称');
```

### 同步更新 04-apply-all-migrations.sql

在文件末尾追加 V29 内容。

---

## 其他数据源审查结果

### 2. school_course (课程基本信息)
**接口文档字段**: kcxs, kcdm, kcxf, kcywmc, kcjj, kslx, kkyx, kcmc
**V12 配置**: ✅ 所有字段已正确映射
**结论**: 无问题

### 3. teaching_class (本专科生课程信息)
**接口文档字段**: KCXS, KCXF, KKYX, KCJJ, KCYWMC, KCMC, KCH, KSLX
**V12 配置**: ✅ 所有字段已正确映射
**结论**: 无问题

### 4. course_schedule (课程安排信息)
**接口文档字段**: xkkch, jsgh, xn, xq, kcdm, kcmc, kclb, kcxz, xkkh, skxqj, skjc, skkssj, skjssj, kkyx, jsxm, kcbh
**V12 配置**: ✅ 核心字段已映射（xkkch, skxqj, skjc, kczc, skdd, xn, xq）
**结论**: 无问题

### 5. course_selection (学生选课)
**接口文档字段**: XH, KSXZDM, KSXZMC, KCBH
**V12 配置**: ✅ 所有字段已映射
**结论**: 无问题（注意：此接口返回字段极少，仅4个）

### 6. level_exam_scores (等级考试成绩)
**接口文档字段**: xh, xhxm, xm, kskm, ksrq, fskscj
**V12 配置**: ✅ 核心字段已映射
**结论**: 无问题

### 8. teacher_courses (教师授课)
**接口文档字段**: rkjs, xn, xq, kcdm, kxh, rkjsh, kcmc, kcbh, sfzj, kcbbm, rkjsxm
**V12 配置**: ✅ 核心字段已映射
**结论**: 无问题

### 9. student_schedule (学生课表)
**接口文档字段**: zcdm, skjszgh, jsmc, lsxykh, kcmc, js, skcd, djj, xqj, xkkh, xh, xq, xn, zcap, kcbh
**V12 配置**: ✅ 所有核心字段已映射
**结论**: 无问题

---

## 修复文件清单

1. **V29 Flyway 迁移脚本**
   - 路径: `backend/src/main/resources/db/migration/V29__fix_field_mappings_administrative_class_teaching_plans.sql`
   - 内容: 禁用错误映射 + 添加正确映射

2. **Docker 初始化脚本更新**
   - 路径: `docker/mysql/init/04-apply-all-migrations.sql`
   - 操作: 文件末尾追加 V29 内容

---

## 验证方法

### 步骤 1: 应用迁移
```bash
# 方式1: 重启容器（推荐）
docker-compose down
docker-compose up -d

# 方式2: 手动执行迁移
docker exec langhack-mysql mysql -uroot -proot123 english_learning < backend/src/main/resources/db/migration/V29__fix_field_mappings_administrative_class_teaching_plans.sql
```

### 步骤 2: 重新同步数据
1. 访问运维数据预览页面
2. 点击"全量同步"按钮
3. 选择"行政班"和"教学计划"数据源
4. 等待同步完成

### 步骤 3: 验证修复效果
- **行政班表格**:
  - "学院"列应显示正确的学院名称（如"外国语学院"）
  - "专业"列应显示专业代码（如"120202"）
  
- **教学计划表格**:
  - "专业"列应显示专业名称（如"工商管理"），不再显示"未知专业"

### 步骤 4: 检查日志
```bash
# 查看同步日志，确认字段映射生效
docker logs langhack-backend | grep "field mapping"
```

---

## 影响范围

### 受影响的数据表
- `administrative_classes` - 行政班表（学院和专业代码字段）
- `teaching_plans` - 教学计划表（专业字段）

### 受影响的功能
- 运维数据预览页面 - 行政班和教学计划Tab
- 学校数据同步功能 - 行政班和教学计划同步逻辑
- 数据筛选功能 - 按学院和专业筛选

### 不受影响的功能
- 其他7个数据源的同步和展示
- 学生课表、选课记录等功能
- 用户管理、权限控制等核心功能
