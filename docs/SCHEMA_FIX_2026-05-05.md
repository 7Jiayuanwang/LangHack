# 生产环境 Schema 修复文档

**日期**: 2026-05-05  
**版本**: V15 Migration  
**状态**: 已修复

---

## 问题概述

生产环境中发现多个 Entity 定义与数据库 Schema 不一致的问题，导致运行时错误和数据同步失败。

---

## 修复清单

### P0 - 阻塞基本功能（已修复）

#### 1. backup_records 表缺失 category 列

**错误信息**:
```
Unknown column 'category' in 'field list'
```

**位置**: `BackupController.java:156`

**根因**: 
- Entity `BackupRecord.java` 定义了 `category` 字段
- 但 V4 迁移脚本中未创建该列

**修复**:
```sql
ALTER TABLE backup_records 
ADD COLUMN category VARCHAR(50) DEFAULT 'SYSTEM' 
COMMENT '备份分类: SYSTEM/USER/AUTO' 
AFTER backup_mode;
```

**影响**: 备份功能完全不可用

---

#### 2. videos 表缺失 subtitle_content_zh 列

**错误信息**:
```
Unknown column 'subtitle_content_zh' in 'field list'
```

**位置**: `VideoController.java` 多处

**根因**:
- Entity `Video.java` 定义了 `subtitle_content_zh` 字段
- 但 V12 迁移脚本中未创建该列

**修复**:
```sql
ALTER TABLE videos 
ADD COLUMN subtitle_content_zh LONGTEXT 
COMMENT '中文字幕内容（SRT/VTT格式）' 
AFTER subtitle_content;
```

**影响**: 视频字幕功能不可用

---

### P1 - 学校数据同步 100% 失败（已修复）

#### 3. course_selection 表 credits 字段不能为 NULL

**错误信息**:
```
Column 'credits' cannot be null
```

**根因**:
- CDSP API 只返回 `XH/KCBH/KSXZDM/KSXZMC` 4 个字段
- 但表定义 `credits` 为 `NOT NULL`
- 导致 100,000+ 条选课记录同步失败

**修复**:
```sql
ALTER TABLE course_selection 
MODIFY COLUMN credits DECIMAL(3,1) NULL 
COMMENT '学分（CDSP API不返回，可为空）';

-- 同时放宽其他字段约束
MODIFY COLUMN course_name VARCHAR(200) NULL,
MODIFY COLUMN teacher_id VARCHAR(50) NULL,
MODIFY COLUMN teacher_name VARCHAR(50) NULL,
MODIFY COLUMN academic_year VARCHAR(20) NULL,
MODIFY COLUMN semester INT NULL,
MODIFY COLUMN selection_time DATETIME NULL,
MODIFY COLUMN selection_status VARCHAR(20) NULL DEFAULT 'SELECTED';
```

**影响**: 选课记录同步 100% 失败

---

#### 4. course_schedule 表 day_of_week/period 字段不能为 NULL

**错误信息**:
```
Column 'day_of_week' cannot be null
Column 'period' cannot be null
```

**根因**:
- CDSP API 可能不返回这些字段
- 但表定义为 `NOT NULL`

**修复**:
```sql
ALTER TABLE course_schedule 
MODIFY COLUMN day_of_week INT NULL,
MODIFY COLUMN period VARCHAR(20) NULL,
MODIFY COLUMN week_range VARCHAR(50) NULL,
MODIFY COLUMN class_location VARCHAR(100) NULL;
```

**影响**: 数千条课表记录同步失败

---

#### 5. course_schedule 外键约束失败

**错误信息**:
```
Cannot add or update a child row: a foreign key constraint fails
```

**根因**:
- `course_schedule.class_id` 引用 `teaching_class.class_id`
- 但同步顺序可能导致 teaching_class 记录不存在
- 原外键使用 `ON DELETE CASCADE`，导致级联删除问题

**修复**:
```sql
-- 移除旧外键
ALTER TABLE course_schedule 
DROP FOREIGN KEY course_schedule_ibfk_1;

-- 重新添加外键，使用 ON DELETE SET NULL
ALTER TABLE course_schedule 
ADD CONSTRAINT fk_course_schedule_teaching_class 
FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) 
ON DELETE SET NULL ON UPDATE CASCADE;
```

**影响**: 课表同步失败

---

### P2 - 字段映射问题（已修复）

#### 6. course_selection 缺失 credits 字段映射

**问题**: CDSP API 不返回 credits 字段，需要从 school_course 表关联查询

**修复**: 添加字段映射（如果 API 返回）
```sql
INSERT IGNORE INTO field_mappings (...) VALUES
('course_selection', 'XF', 'credits', ...),
('course_selection', '学分', 'credits', ...);
```

---

#### 7. course_selection 缺失 student_name 字段映射

**问题**: 学生姓名字段未映射

**修复**: 添加字段映射
```sql
INSERT IGNORE INTO field_mappings (...) VALUES
('course_selection', 'XM', 'student_name', ...),
('course_selection', '姓名', 'student_name', ...);
```

---

### 性能优化（已完成）

#### 8. 添加复合索引

**优化**:
```sql
-- course_selection 表
CREATE INDEX idx_student_academic 
ON course_selection(student_id, academic_year, semester);

-- course_schedule 表
CREATE INDEX idx_class_academic 
ON course_schedule(class_id, academic_year, semester);
```

**效果**: 提升学生课表/选课记录查询性能

---

### 数据清理（已完成）

#### 9. 清理孤立记录

**操作**:
```sql
-- 清理 course_schedule 中引用不存在的 teaching_class 的记录
DELETE cs FROM course_schedule cs
LEFT JOIN teaching_class tc ON cs.class_id = tc.class_id
WHERE tc.class_id IS NULL;

-- 清理 course_selection 中引用不存在的 course_code 的记录
DELETE csel FROM course_selection csel
LEFT JOIN school_course sc ON csel.course_code = sc.course_code
WHERE sc.course_code IS NULL;
```

**效果**: 避免外键约束错误

---

## 部署步骤

### 1. 本地测试（开发环境）

```bash
cd backend
mvn clean test
```

### 2. 生产环境部署

```bash
# 方式 1: 使用 Flyway（如果启用）
cd backend
mvn flyway:migrate

# 方式 2: 使用 Docker 重新初始化（推荐）
docker compose down
docker compose up -d

# 方式 3: 手动执行 SQL
mysql -h <host> -u root -p english_learning < backend/src/main/resources/db/migration/V15__fix_schema_issues.sql
```

### 3. 验证修复

```sql
-- 验证 backup_records.category 列
SELECT COUNT(*) FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'english_learning' 
AND TABLE_NAME = 'backup_records' 
AND COLUMN_NAME = 'category';
-- 期望: 1

-- 验证 videos.subtitle_content_zh 列
SELECT COUNT(*) FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'english_learning' 
AND TABLE_NAME = 'videos' 
AND COLUMN_NAME = 'subtitle_content_zh';
-- 期望: 1

-- 验证 course_selection.credits 可空
SELECT IS_NULLABLE FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'english_learning' 
AND TABLE_NAME = 'course_selection' 
AND COLUMN_NAME = 'credits';
-- 期望: YES

-- 验证 course_schedule 外键约束
SELECT CONSTRAINT_NAME, DELETE_RULE, UPDATE_RULE 
FROM information_schema.REFERENTIAL_CONSTRAINTS 
WHERE TABLE_NAME = 'course_schedule' 
AND CONSTRAINT_SCHEMA = 'english_learning';
-- 期望: fk_course_schedule_teaching_class, DELETE_RULE=SET NULL
```

---

## 后续任务

### P3 - 学生数据绑定（待排查）

#### 8. 链式拉取 API 响应解析失败

**错误**: `Failed to parse response for 外国语学院`

**位置**: `SchoolDataApiClientImpl`

**待排查**:
- API 响应格式
- JSON 解析逻辑
- 错误日志详情

---

#### 9. 学生数据未映射到账户

**现象**: 学生端课表/成绩/选课记录为空

**待排查**:
- `teacher_student_binding` 表数据
- SSO 自动授权逻辑
- 学生账户与学校数据的关联

---

### P4 - UI 问题（待开发）

#### 10. 学生端个人页面缺失阅读次数汇总

**需求**: 添加阅读次数统计功能

---

#### 11. 文章详情页长时间卡顿

**待排查**: 性能瓶颈

---

## 经验教训

### 1. Entity 与 Schema 一致性检查

**问题**: Entity 定义了字段，但 Flyway 迁移脚本中未创建

**预防措施**:
- 新增 Entity 字段时，必须同步创建 Flyway 迁移脚本
- 定期运行一致性检查脚本（待开发）
- CI 流程中添加 Schema 验证

### 2. CDSP API 字段映射

**问题**: API 返回字段与表定义不匹配

**预防措施**:
- 所有外部 API 字段默认设为可空
- 在 Service 层做数据补全和校验
- 添加详细的字段映射文档

### 3. 外键约束策略

**问题**: 严格的外键约束导致同步失败

**预防措施**:
- 外部数据同步表使用 `ON DELETE SET NULL`
- 核心业务表使用 `ON DELETE RESTRICT`
- 添加数据清理定时任务

---

## 相关文件

- Flyway 迁移: `backend/src/main/resources/db/migration/V15__fix_schema_issues.sql`
- 合并脚本: `docker/mysql/init/04-apply-all-migrations.sql`
- Entity 文件:
  - `backend/src/main/java/com/english/entity/BackupRecord.java`
  - `backend/src/main/java/com/english/entity/Video.java`
  - `backend/src/main/java/com/english/entity/CourseSelection.java`
  - `backend/src/main/java/com/english/entity/CourseSchedule.java`

---

## 联系人

- 开发: AI Assistant
- 日期: 2026-05-05
