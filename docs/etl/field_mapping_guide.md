# ETL 模式字段映射配置指南

> **用途**：将学校数据库的字段映射到系统内部字段

---

## 一、字段映射机制

### 1.1 映射流程

```
学校数据库字段（中文/英文）
    ↓
字段映射服务（FieldMappingService）
    ↓
系统内部字段（英文 snake_case）
    ↓
本地数据库表
```

### 1.2 映射配置存储

字段映射配置存储在 `field_mappings` 表中（由 V12 迁移脚本创建）：

```sql
SELECT * FROM field_mappings WHERE data_source = 'administrative_class';
```

---

## 二、默认字段映射

系统已内置以下数据源的字段映射（V12 迁移脚本）：

### 2.1 行政班（administrative_class）

| 学校字段 | 系统字段 | 说明 |
|---------|---------|------|
| BJDM | classId | 班级代码 |
| BJMC | className | 班级名称 |
| XSH | collegeCode | 学院代码 |
| XYMC | collegeName | 学院名称 |
| NJ | grade | 年级 |
| BJRS | studentCount | 班级人数 |

### 2.2 课程（school_course）

| 学校字段 | 系统字段 | 说明 |
|---------|---------|------|
| KCDM | courseCode | 课程代码 |
| KCMC | courseName | 课程名称 |
| KKYX | collegeName | 开课学院 |
| XF | credits | 学分 |
| KCXZ | courseType | 课程性质 |

### 2.3 教学班（teaching_class）

| 学校字段 | 系统字段 | 说明 |
|---------|---------|------|
| JXBID | classId | 教学班号 |
| KCDM | courseCode | 课程代码 |
| KCMC | courseName | 课程名称 |
| RKJSH | teacherId | 教师工号 |
| JSXM | teacherName | 教师姓名 |
| XN | academicYear | 学年 |
| XQ | semester | 学期 |

### 2.4 课表（course_schedule）

| 学校字段 | 系统字段 | 说明 |
|---------|---------|------|
| JXBID | classId | 教学班号 |
| KCDM | courseCode | 课程代码 |
| KCMC | courseName | 课程名称 |
| SKXQJ | dayOfWeek | 星期几 |
| SKJC | period | 上课节次 |
| SKZC | weekRange | 上课周次 |
| SKDD | location | 上课地点 |

### 2.5 选课记录（course_selection）

| 学校字段 | 系统字段 | 说明 |
|---------|---------|------|
| XH | studentId | 学号 |
| XSXM | studentName | 学生姓名 |
| KCBH | courseClassCode | 课程班号 |
| KCDM | courseCode | 课程代码 |
| KCMC | courseName | 课程名称 |
| XN | academicYear | 学年 |
| XQ | semester | 学期 |

### 2.6 等级考试成绩（level_exam_scores）

| 学校字段 | 系统字段 | 说明 |
|---------|---------|------|
| XH | studentId | 学号 |
| XSXM | studentName | 学生姓名 |
| KSMC | examName | 考试名称 |
| KSSJ | examDate | 考试时间 |
| ZKZH | admissionTicket | 准考证号 |
| CJ | score | 成绩 |

### 2.7 教学计划（teaching_plans）

| 学校字段 | 系统字段 | 说明 |
|---------|---------|------|
| JHJHID | planId | 教学计划ID |
| ZYMC | majorName | 专业名称 |
| NJ | grade | 年级 |
| PYFAMC | planName | 培养方案名称 |

### 2.8 教师授课（teacher_courses）

| 学校字段 | 系统字段 | 说明 |
|---------|---------|------|
| RKJSH | teacherId | 教师工号 |
| JSXM | teacherName | 教师姓名 |
| KCDM | courseCode | 课程代码 |
| KCMC | courseName | 课程名称 |
| KCBH | classId | 课程班号 |
| XN | academicYear | 学年 |
| XQ | semester | 学期 |

### 2.9 学生课表（student_schedule）

| 学校字段 | 系统字段 | 说明 |
|---------|---------|------|
| XH / xh | studentId | 学号 |
| XKKH / xkkh | classId | 选课课号/课程班号 |
| KCBH / kcbh | courseCode | 课程编号 |
| KCMC / kcmc | courseName | 课程名称 |
| SKJSZGH / skjszgh | teacherId | 上课教师职工号 |
| JSMC / jsmc | teacherName | 教师名称 |
| XQJ / xqj | dayOfWeek | 星期几 |
| DJJ / djj | period | 第几节 |
| ZCAP / zcap | weekRange | 周次安排 |
| JS / js | classLocation | 教室 |
| XN / xn | academicYear | 学年 |
| XQ / xq | semester | 学期 |

---

## 三、自定义字段映射

### 3.1 添加新的字段映射

如果学校数据库的字段名与默认映射不同，需要在 `field_mappings` 表中添加新的映射：

```sql
INSERT INTO field_mappings (
    data_source,
    source_field,
    target_field,
    converter,
    remark
) VALUES (
    'administrative_class',  -- 数据源
    'BJ_DM',                 -- 学校字段名（实际字段名）
    'classId',               -- 系统字段名
    NULL,                    -- 转换器（可选）
    '班级代码'                -- 备注
);
```

### 3.2 字段映射规则

1. **大小写不敏感**：映射查找时会自动转换为小写
2. **支持中文字段名**：如 `班级名称` → `className`
3. **支持英文字段名**：如 `class_name` → `className`
4. **支持大写字段名**：如 `BJMC` → `className`

### 3.3 字段转换器

系统支持以下字段转换器（`converter` 字段）：

| 转换器 | 说明 | 示例 |
|-------|------|------|
| `NULL` | 无转换（默认） | 直接映射 |
| `semester_converter` | 学期转换 | `秋` → `1`, `春` → `2` |
| `date_converter` | 日期转换 | `2024-09-01` → `LocalDate` |
| `json_converter` | JSON 转换 | `{"key":"value"}` → Map |

---

## 四、字段映射验证

### 4.1 查看当前映射配置

```sql
-- 查看所有字段映射
SELECT 
    data_source,
    source_field,
    target_field,
    converter,
    remark
FROM field_mappings
ORDER BY data_source, source_field;

-- 查看特定数据源的映射
SELECT * FROM field_mappings 
WHERE data_source = 'administrative_class';
```

### 4.2 测试字段映射

通过运维面板测试字段映射：

1. 登录运维账号
2. 进入 **运维** → **学校数据配置**
3. 选择数据源
4. 点击 **测试同步** 按钮
5. 查看返回的数据是否正确映射

### 4.3 查看映射日志

```bash
# 查看后端日志中的字段映射信息
docker logs langhack-backend | grep -i "field.*mapping"

# 查看同步日志
docker logs langhack-backend | grep -i "school.*etl"
```

---

## 五、常见问题

### Q1: 学校数据库字段名与默认映射不同怎么办？

**A**: 有两种解决方案：

**方案 1**：修改 ETL 查询 SQL，使用别名

```sql
-- 在 application.yml 中修改查询
SELECT 
    BJ_DM AS BJDM,      -- 将实际字段名映射为默认字段名
    BJ_MC AS BJMC,
    XY_MC AS XYMC
FROM t_administrative_class
WHERE updated_at >= ?
```

**方案 2**：添加新的字段映射

```sql
INSERT INTO field_mappings (data_source, source_field, target_field, remark)
VALUES ('administrative_class', 'BJ_DM', 'classId', '班级代码');
```

### Q2: 如何处理学期字段的中文值？

**A**: 使用 `semester_converter` 转换器

```sql
UPDATE field_mappings 
SET converter = 'semester_converter'
WHERE data_source = 'teaching_class' 
  AND target_field = 'semester';
```

转换规则：
- `秋` / `1` → `1`
- `春` / `2` → `2`

### Q3: 字段映射不生效怎么办？

**排查步骤**：

1. **检查字段名大小写**
   ```sql
   -- 查看实际返回的字段名
   SELECT * FROM v_administrative_class LIMIT 1;
   ```

2. **检查映射配置**
   ```sql
   SELECT * FROM field_mappings 
   WHERE data_source = 'administrative_class'
     AND LOWER(source_field) = LOWER('实际字段名');
   ```

3. **查看后端日志**
   ```bash
   docker logs langhack-backend | grep -A 5 "字段映射"
   ```

4. **重启应用**
   ```bash
   docker restart langhack-backend
   ```

### Q4: 如何批量更新字段映射？

**A**: 使用 SQL 批量更新

```sql
-- 批量添加行政班字段映射
INSERT INTO field_mappings (data_source, source_field, target_field, remark) VALUES
('administrative_class', 'BJ_DM', 'classId', '班级代码'),
('administrative_class', 'BJ_MC', 'className', '班级名称'),
('administrative_class', 'XY_MC', 'collegeName', '学院名称'),
('administrative_class', 'NJ', 'grade', '年级')
ON DUPLICATE KEY UPDATE 
    target_field = VALUES(target_field),
    remark = VALUES(remark);
```

---

## 六、字段映射最佳实践

### 6.1 命名规范

- **学校字段**：保持原样（如 `BJDM`、`班级代码`）
- **系统字段**：使用 camelCase（如 `classId`、`className`）
- **数据库字段**：使用 snake_case（如 `class_id`、`class_name`）

### 6.2 映射优先级

1. **优先使用 SQL 别名**：在查询中直接映射，性能最好
2. **其次使用字段映射表**：灵活但需要额外查询
3. **最后修改代码**：不推荐，维护成本高

### 6.3 文档维护

每次添加或修改字段映射时，应同步更新：
1. `field_mappings` 表
2. 本文档
3. ETL 查询 SQL（如果使用别名）

---

## 七、字段映射示例

### 示例 1：学校使用英文字段名

**学校数据库**：
```sql
SELECT class_code, class_name, college_name FROM t_class;
```

**字段映射**：
```sql
INSERT INTO field_mappings (data_source, source_field, target_field) VALUES
('administrative_class', 'class_code', 'classId'),
('administrative_class', 'class_name', 'className'),
('administrative_class', 'college_name', 'collegeName');
```

### 示例 2：学校使用中文字段名

**学校数据库**：
```sql
SELECT 班级代码, 班级名称, 学院名称 FROM t_class;
```

**字段映射**：
```sql
INSERT INTO field_mappings (data_source, source_field, target_field) VALUES
('administrative_class', '班级代码', 'classId'),
('administrative_class', '班级名称', 'className'),
('administrative_class', '学院名称', 'collegeName');
```

### 示例 3：使用 SQL 别名（推荐）

**ETL 查询 SQL**：
```sql
SELECT 
    班级代码 AS BJDM,
    班级名称 AS BJMC,
    学院名称 AS XYMC
FROM t_class
WHERE 更新时间 >= ?
```

这样就可以使用默认的字段映射，无需额外配置。

---

## 八、附录

### 8.1 完整字段映射 SQL

```sql
-- 导出当前所有字段映射
SELECT 
    CONCAT(
        'INSERT INTO field_mappings (data_source, source_field, target_field, converter, remark) VALUES (',
        QUOTE(data_source), ', ',
        QUOTE(source_field), ', ',
        QUOTE(target_field), ', ',
        IFNULL(QUOTE(converter), 'NULL'), ', ',
        QUOTE(remark), ');'
    ) AS insert_statement
FROM field_mappings
ORDER BY data_source, source_field;
```

### 8.2 字段映射备份

```bash
# 备份字段映射配置
docker exec langhack-mysql-master mysqldump \
    -u root -p${MYSQL_ROOT_PASSWORD} \
    english_learning field_mappings \
    > field_mappings_backup_$(date +%Y%m%d).sql

# 恢复字段映射配置
docker exec -i langhack-mysql-master mysql \
    -u root -p${MYSQL_ROOT_PASSWORD} \
    english_learning < field_mappings_backup_20260427.sql
```

---

**最后更新**: 2026-04-27
