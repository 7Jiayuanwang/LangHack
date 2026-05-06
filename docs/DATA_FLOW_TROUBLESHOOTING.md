# 数据流映射问题排查指南

## 问题现象
拉取到的数据不能映射到前端页面，用户前端页面看不到相关数据。

## 排查步骤

### 1. 运行诊断脚本
```bash
# 检查数据库数据和同步状态
bash scripts/diagnose-data-flow.sh

# 检查前端字段绑定
bash scripts/check-field-binding.sh
```

### 2. 数据流完整链路

```
CDSP API (中文字段)
    ↓
field_mappings 表 (source_field → target_field)
    ↓
Entity 类 (camelCase Java 字段)
    ↓
MyBatis Plus (自动 snake_case ↔ camelCase)
    ↓
数据库表 (snake_case 列名)
    ↓
Controller 查询
    ↓
Jackson 序列化 (camelCase JSON)
    ↓
前端 API 调用
    ↓
Vue 组件显示 (camelCase 绑定)
```

### 3. 常见问题及解决方案

#### 问题 A：数据库表为空
**症状**：`SELECT COUNT(*) FROM course_selection` 返回 0

**原因**：
1. 从未同步过数据
2. 同步失败（外键约束、字段映射错误）
3. 数据被外键检查跳过

**解决**：
```bash
# 1. 检查同步日志
docker exec langhack-mysql-master mysql -uroot -p$MYSQL_ROOT_PASSWORD english_learning -e "
SELECT data_source, status, total_count, success_count, fail_count, created_at
FROM sync_log
WHERE data_source = 'course_selection'
ORDER BY created_at DESC LIMIT 5;
"

# 2. 按顺序重新同步
# 运维端 → 学校数据同步 → 选择数据源 → 全量同步
# 顺序：school_course → teacher_courses → course_selection
```

#### 问题 B：外键约束导致数据被跳过
**症状**：同步日志显示大量失败，或后端日志有"外键检查失败"警告

**原因**：
- `course_selection.class_id` 在 `teaching_class` 表中不存在
- `course_selection.course_code` 在 `school_course` 表中不存在

**解决**：
```bash
# 1. 检查孤立记录
docker exec langhack-mysql-master mysql -uroot -p$MYSQL_ROOT_PASSWORD english_learning -e "
SELECT COUNT(*) as orphaned
FROM course_selection cs
LEFT JOIN teaching_class tc ON cs.class_id = tc.class_id
WHERE tc.class_id IS NULL;
"

# 2. 先同步依赖数据源
# teacher_courses → teaching_class 表
# school_course → school_course 表

# 3. 再同步 course_selection
```

#### 问题 C：字段映射不完整
**症状**：部分字段有值，部分字段为 NULL 或 undefined

**原因**：
- `field_mappings` 表缺少某些字段的映射
- `source_field` 与 CDSP API 返回的字段名不匹配

**解决**：
```bash
# 1. 检查字段映射
docker exec langhack-mysql-master mysql -uroot -p$MYSQL_ROOT_PASSWORD english_learning -e "
SELECT source_field, target_field, converter
FROM field_mappings
WHERE data_source = 'course_selection'
ORDER BY source_field;
"

# 2. 对比 CDSP API 文档，补充缺失的映射
# 参考：docs/SCHOOL_DATA_API_SPEC.md
```

#### 问题 D：前端字段名不匹配
**症状**：后端返回数据，但前端显示 undefined

**原因**：
- 前端使用 snake_case（如 `item.student_id`）
- 后端返回 camelCase（如 `studentId`）

**解决**：
```javascript
// ❌ 错误
{{ item.student_id }}
{{ item.course_name }}

// ✅ 正确
{{ item.studentId }}
{{ item.courseName }}
```

#### 问题 E：查询参数格式错误
**症状**：API 调用成功但返回空数组

**原因**：
- 学年参数格式错误（应为单年如"2026"，不是"2025-2026"）
- 学期参数格式错误（应为"秋"或"春"，或 1/2）

**解决**：
```javascript
// ✅ 正确格式
getMyCourseSelections({
  academicYear: '2026',  // 单年
  semester: '秋'         // 中文或数字 1/2
})

// ❌ 错误格式
getMyCourseSelections({
  academicYear: '2025-2026',  // 不支持
  semester: 'fall'            // 不支持英文
})
```

### 4. 验证修复

#### 4.1 验证数据库有数据
```bash
docker exec langhack-mysql-master mysql -uroot -p$MYSQL_ROOT_PASSWORD english_learning -e "
SELECT student_id, student_name, course_name, teacher_name, credits
FROM course_selection
LIMIT 5;
"
```

#### 4.2 验证后端 API 返回数据
```bash
# 获取 JWT token（登录后从浏览器 DevTools → Application → Local Storage 复制）
TOKEN="your_jwt_token"

# 调用学生端选课记录 API
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/student/course-selections?academicYear=2026&semester=秋"
```

#### 4.3 验证前端显示
1. 打开浏览器 DevTools → Network
2. 访问学生端选课记录页面
3. 查看 API 请求和响应
4. 确认响应数据格式和字段名

### 5. 字段名对照表

| 数据库列名 | Entity 字段 | JSON 字段 | 前端绑定 |
|-----------|------------|----------|---------|
| student_id | studentId | studentId | item.studentId |
| student_name | studentName | studentName | item.studentName |
| course_code | courseCode | courseCode | item.courseCode |
| course_name | courseName | courseName | item.courseName |
| class_id | classId | classId | item.classId |
| teacher_name | teacherName | teacherName | item.teacherName |
| credits | credits | credits | item.credits |
| class_time | classTime | classTime | item.classTime |
| class_location | classLocation | classLocation | item.classLocation |
| total_grade | totalGrade | totalGrade | item.totalGrade |

**规则**：
- 数据库使用 snake_case
- Java/JSON/前端统一使用 camelCase
- MyBatis Plus 自动转换

### 6. 快速修复流程

```bash
# 1. 运行诊断
bash scripts/diagnose-data-flow.sh

# 2. 如果数据库为空，按顺序同步
# 运维端 → 学校数据同步 → 全量同步
# 顺序：school_course → teacher_courses → course_selection

# 3. 如果有外键问题，先同步依赖表
# teacher_courses（填充 teaching_class）
# school_course（填充 school_course）

# 4. 检查前端字段绑定
bash scripts/check-field-binding.sh

# 5. 验证修复
# 浏览器访问学生端选课记录页面
# DevTools → Network 查看 API 响应
```

## 相关文件

- 诊断脚本：`scripts/diagnose-data-flow.sh`
- 字段绑定检查：`scripts/check-field-binding.sh`
- 学生端页面：`frontend/src/views/student/CourseSelections.vue`
- 运维端页面：`frontend/src/views/ops/SchoolDataBrowser.vue`
- 后端 Controller：`backend/src/main/java/com/english/controller/StudentDataController.java`
- Entity 定义：`backend/src/main/java/com/english/entity/CourseSelection.java`
- 字段映射服务：`backend/src/main/java/com/english/service/impl/FieldMappingServiceImpl.java`
