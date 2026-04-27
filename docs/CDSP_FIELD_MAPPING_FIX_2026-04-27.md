# CDSP API 字段映射与分页修复总结

**修复日期**: 2026-04-27  
**问题来源**: 学生课表同步失败，1000 条数据全部失败，错误 `Field 'class_id' doesn't have a default value`

---

## 一、问题诊断

### 1.1 根本原因

**字段映射不匹配**：
- CDSP API 实际返回**中文字段名**（如 `学号`、`选课课号`、`课程名称`）
- 但 V12 field_mappings 只配置了**英文缩写**（`xh`、`xkkh`、`kcmc`）
- 导致字段映射失败，`classId` 为 NULL，触发 NOT NULL 约束

**证据**：
- 日志 recordPreview 显示：`{周次代码=..., 上课教师职工号=..., 教师名称=..., 课程名称=...}`
- PDF 文档第 4 节返回示例确认：`{"学年":"2024","学期":"春","课程名称":"路桥基础工程"}`

### 1.2 次要问题

**分页限制**：
- 首页返回 1000 条时，未启动分页拉取
- 分页检测仅依赖响应中的 `pages`/`hasNext` 字段，但 CDSP API 可能不返回这些字段

---

## 二、修复方案

### 2.1 字段映射修复（V12 整合）

**修复文件**：
- `backend/src/main/resources/db/migration/V12__school_data_integration.sql`
- `docker/mysql/init/04-apply-all-migrations.sql`

**修复内容**：
为所有 9 个数据源添加**中文+英文双格式映射**：

```sql
-- 示例：student_schedule
('student_schedule', '学号',     'studentId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号（中文字段名）'),
('student_schedule', 'xh',      'studentId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号（英文缩写）'),
('student_schedule', '选课课号', 'classId',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '选课课号（中文字段名）'),
('student_schedule', 'xkkh',    'classId',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '选课课号（英文缩写）'),
```

**覆盖数据源**：
1. administrative_class
2. school_course
3. teaching_class
4. course_schedule
5. course_selection
6. level_exam_scores
7. teaching_plans
8. teacher_courses
9. student_schedule

**删除文件**：
- `V27__fix_student_schedule_field_mappings.sql`（已合并到 V12）
- `V28__add_chinese_field_mappings_all_sources.sql`（已合并到 V12）

### 2.2 分页循环优化

**修复文件**：
- `backend/src/main/java/com/english/service/impl/SchoolDataApiClientImpl.java`

**关键改进**：

1. **启发式分页检测**（line 312-318）：
```java
// 如果首页数据量 >= 1000，大概率还有更多数据
boolean likelyHasMore = firstPage.rows.size() >= 1000;

// 如果首页已完整且数据量 < 1000，直接返回
if (firstSummary.isComplete() && !likelyHasMore) {
    return new SchoolDataFetchResult(firstPage.rows, firstSummary);
}
```

2. **HTTP 方法自适应**（line 336-365）：
```java
// GET 失败时自动切换到 POST
if (usePostMethod) {
    nextPage = fetchPagePost(fullUrl, pageParams);
} else {
    nextPage = fetchPage(buildUrl(fullUrl, pageParams));
}

// 如果 GET 分页失败，尝试切换到 POST
if (!usePostMethod) {
    logger.warn("分页参数 GET 不支持，切换到 POST 方法");
    usePostMethod = true;
    nextPage = fetchPagePost(fullUrl, pageParams);
}
```

3. **循环拉取直到完整**（line 367-387）：
```java
while (fetchedPages < maxPages) {
    // 拉取下一页
    // 如果返回空页或数据量 < pageSize，停止循环
    if (nextPage.rows.isEmpty() || nextPage.rows.size() < pageSize) {
        break;
    }
    allRows.addAll(nextPage.rows);
    fetchedPages++;
}
```

---

## 三、验证步骤

### 3.1 前置条件

1. 启动 Docker Desktop
2. 确保 MySQL 容器运行正常

### 3.2 重启应用

```bash
cd G:\GitHub_local\project\英语学习平台\LangHack
docker-compose restart backend
```

### 3.3 验证字段映射

**检查数据库**：
```sql
-- 查看 student_schedule 字段映射（应有中文+英文双格式）
SELECT source_field, target_field, remark 
FROM field_mappings 
WHERE data_source = 'student_schedule' 
ORDER BY sort_order, source_field;

-- 预期结果：每个 target_field 有 2 条记录（中文+英文）
```

### 3.4 验证同步功能

**触发同步**：
- 运维面板 → 学校数据配置 → 手动同步 → student_schedule

**检查日志**：
```bash
docker logs langhack-backend | grep "全量同步完成.*student_schedule"
```

**预期输出**：
```
全量同步完成: 数据源=student_schedule, 总数=1000+, 成功=1000+, 失败=0, 跳过=0
```

**检查数据**：
```sql
-- 查看同步的学生课表数据
SELECT COUNT(*) FROM student_schedule;
SELECT * FROM student_schedule LIMIT 10;

-- 验证 class_id 字段不为空
SELECT COUNT(*) FROM student_schedule WHERE class_id IS NULL;
-- 预期结果：0
```

### 3.5 验证分页功能

**检查日志中的分页信息**：
```bash
docker logs langhack-backend | grep "检测到大量数据\|拉取第.*页\|分页拉取完成"
```

**预期输出**：
```
检测到大量数据（>= 1000 条），启动分页拉取 dataSource=student_schedule firstPageRows=1000
拉取第 2 页 dataSource=student_schedule pageRows=500 totalRows=1500
分页拉取完成（最后一页） dataSource=student_schedule totalPages=2 totalRows=1500
```

---

## 四、回滚方案

如果修复后出现问题，可以回滚：

### 4.1 回滚代码

```bash
cd G:\GitHub_local\project\英语学习平台\LangHack
git log --oneline -5  # 查看最近提交
git revert <commit-hash>  # 回滚到修复前
docker-compose restart backend
```

### 4.2 回滚数据库

```sql
-- 删除新增的中文字段映射
DELETE FROM field_mappings 
WHERE data_source IN ('student_schedule', 'administrative_class', 'school_course', 
                      'teaching_class', 'course_schedule', 'course_selection', 
                      'level_exam_scores', 'teaching_plans', 'teacher_courses')
  AND remark LIKE '%中文%';
```

---

## 五、后续优化建议

### 5.1 监控告警

添加同步失败率监控：
- 失败率 > 10% → 发送告警
- 连续 3 次全部失败 → 紧急告警

### 5.2 数据质量检查

定期检查关键字段：
```sql
-- 检查 class_id 为空的记录
SELECT COUNT(*) FROM student_schedule WHERE class_id IS NULL;

-- 检查学年学期分布
SELECT academic_year, semester, COUNT(*) 
FROM student_schedule 
GROUP BY academic_year, semester;
```

### 5.3 性能优化

- 考虑增加 `student_schedule` 表的复合索引：
  ```sql
  CREATE INDEX idx_student_year_semester 
  ON student_schedule(student_id, academic_year, semester);
  ```

---

## 六、相关文档

- **CDSP API 规范**: `docs/SCHOOL_DATA_API_SPEC.md`
- **字段映射说明**: `docs/SCHOOL_DATA_FIX.md`
- **PDF 接口文档**: `E:\项目\兴趣小组\智能与安全社团\服务器使用\数据资源\接口文档\`
- **记忆文档**: 
  - `memory/project_cdsp_api_conventions.md`（CDSP API 三大陷阱）
  - `memory/feedback_cdsp_beauty_parameter.md`（$beauty 参数问题）

---

## 七、技术债务

1. **V12 迁移脚本过大**（28KB）：考虑拆分为多个文件
2. **字段映射冗余**：中文+英文双格式导致映射表膨胀，未来可考虑在代码层做别名查表
3. **分页最大页数限制**（100 页）：如果单个数据源超过 10 万条，需要调整 `maxPages` 参数

---

**修复完成，等待验证。**
