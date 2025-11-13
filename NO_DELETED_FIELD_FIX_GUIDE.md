# 移除deleted字段修复指南

## 🎯 修复目标

移除`assignments`、`student_assignments`、`units`表中的`deleted`字段，改为使用物理删除替代软删除。

## 🛠️ 已完成的代码修改

### 1. 实体类修改 ✅
- **Assignment.java**: 移除`deleted`字段及相关getter/setter
- **StudentAssignment.java**: 移除`deleted`字段及相关getter/setter
- **Unit.java**: 移除`deleted`字段及相关getter/setter

### 2. Mapper查询逻辑修改 ✅
- **AssignmentMapper.xml**: 移除所有`deleted`相关的WHERE条件
- **StudentAssignmentMapper.xml**: 移除所有`deleted`相关的WHERE条件

### 3. Service业务逻辑修改 ✅
- **AssignmentServiceImpl**: `deleteAssignment()`改为物理删除
- **UnitServiceImpl**: `deleteUnit()`改为物理删除，移除deleted查询条件

### 4. Controller接口修改 ✅
- **UnitController**: 移除`getUnitCount()`中的deleted查询条件

## 📋 数据库操作步骤

### 方法1: 重新创建表（推荐）

1. **连接数据库**
   ```bash
   mysql -u root -p
   use english_learning_platform;
   ```

2. **删除现有表**（如果存在）
   ```sql
   -- 执行 DROP_EXISTING_TABLES.sql
   DROP TABLE IF EXISTS student_assignments;
   DROP TABLE IF EXISTS assignments;
   DROP TABLE IF EXISTS units;
   ```

3. **创建新表**（无deleted字段）
   ```sql
   -- 执行 DATABASE_TABLES_NO_DELETED.sql
   -- 这将创建没有deleted字段的新表
   ```

4. **验证表结构**
   ```sql
   DESCRIBE assignments;
   DESCRIBE student_assignments;
   DESCRIBE units;
   ```

### 方法2: 手动删除字段（如果表中有数据需要保留）

```sql
-- 从assignments表删除deleted字段
ALTER TABLE assignments DROP COLUMN IF EXISTS deleted;

-- 从student_assignments表删除deleted字段
ALTER TABLE student_assignments DROP COLUMN IF EXISTS deleted;

-- 从units表删除deleted字段
ALTER TABLE units DROP COLUMN IF EXISTS deleted;

-- 删除相关的索引
DROP INDEX IF EXISTS idx_deleted ON assignments;
DROP INDEX IF EXISTS idx_deleted ON student_assignments;
DROP INDEX IF EXISTS idx_deleted ON units;
```

## 🔍 验证修复结果

### 1. 检查表结构
```sql
-- 检查是否还有deleted字段
SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME IN ('assignments', 'student_assignments', 'units')
AND COLUMN_NAME = 'deleted';
-- 应该返回空结果

-- 查看表结构
DESCRIBE assignments;
DESCRIBE student_assignments;
DESCRIBE units;
```

### 2. 测试API接口
```bash
# 重启Spring Boot应用后测试
mvn spring-boot:run

# 测试获取任务列表
curl -H "Authorization: Bearer [token]" \
     http://localhost:8080/api/assignments

# 测试删除任务
curl -X DELETE \
     -H "Authorization: Bearer [token]" \
     http://localhost:8080/api/assignments/1
```

### 3. 测试前端功能
- 访问教师端任务管理页面
- 应该不再出现"Unknown column 'a.deleted'"错误
- 创建、编辑、删除任务功能正常
- 数据正确显示和更新

## 📊 修复前后对比

### 修复前
```sql
-- 表结构包含deleted字段
CREATE TABLE assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ...
    deleted INT DEFAULT 0,  -- 软删除字段
    ...
);

-- 查询包含deleted条件
SELECT * FROM assignments WHERE deleted = 0;

-- 删除使用软删除
UPDATE assignments SET deleted = 1 WHERE id = ?;
```

### 修复后
```sql
-- 表结构不包含deleted字段
CREATE TABLE assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ...
    -- 无deleted字段
    ...
);

-- 查询不包含deleted条件
SELECT * FROM assignments WHERE 1=1;

-- 删除使用物理删除
DELETE FROM assignments WHERE id = ?;
```

## 🚨 注意事项

1. **数据备份**: 在执行删除操作前，请备份重要数据
2. **外键约束**: 确保classes、users、courses表存在
3. **应用重启**: 修改完成后需要重启Spring Boot应用
4. **测试验证**: 全面测试所有相关功能

## 🔧 故障排除

### 问题1: 外键约束错误
如果遇到外键错误，确保相关表存在：
```sql
-- 检查必要的表
SHOW TABLES LIKE 'classes';
SHOW TABLES LIKE 'users';
SHOW TABLES LIKE 'courses';
```

### 问题2: 应用启动错误
检查应用日志，确认数据库连接和表结构正确。

### 问题3: 数据丢失
如果误删数据，可以从备份恢复或重新插入测试数据。

## ✅ 修复完成标志

修复成功的标志：
1. ✅ 数据库表不再包含`deleted`字段
2. ✅ 应用启动无错误
3. ✅ 教师端任务管理页面正常访问
4. ✅ 任务CRUD操作正常工作
5. ✅ 前端数据显示正确

---

**修复完成后**，系统将使用物理删除替代软删除，数据库结构更加简洁，不再出现"Unknown column 'a.deleted'"错误！