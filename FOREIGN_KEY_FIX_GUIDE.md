# 外键约束错误修复指南

## 🚨 错误描述

**错误信息**: `Cannot drop table 'assignments' referenced by a foreign key constraint 'learning_records_ibfk_3' on table 'learning_records'.`

**错误原因**: `learning_records` 表中有外键约束引用了 `assignments` 表，无法直接删除被引用的表。

## 🔍 问题分析

外键约束关系：
```
learning_records (子表)
    └── assignments (父表) ← 被引用，无法删除
```

正确的删除顺序：
1. 先删除子表（引用其他表的表）
2. 再删除父表（被其他表引用的表）

## 🛠️ 解决方案

### 方法1: 自动删除脚本（推荐）

执行以下SQL脚本，它会按正确顺序删除表：

```sql
-- 直接执行 SAFE_DROP_TABLES.sql 的内容
DROP TABLE IF EXISTS learning_records;
DROP TABLE IF EXISTS student_assignments;
DROP TABLE IF EXISTS assignments;
DROP TABLE IF EXISTS units;
```

### 方法2: 手动分步删除

#### 步骤1: 查看外键约束
```sql
-- 查看所有相关的外键约束
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM
    information_schema.KEY_COLUMN_USAGE
WHERE
    TABLE_SCHEMA = DATABASE()
    AND REFERENCED_TABLE_NAME IN ('assignments', 'student_assignments', 'units');
```

#### 步骤2: 删除外键约束（如果需要保留子表）
```sql
-- 如果你想保留 learning_records 表，可以先删除外键约束
-- 注意：你需要知道具体的约束名称
ALTER TABLE learning_records DROP FOREIGN KEY learning_records_ibfk_3;
```

#### 步骤3: 按顺序删除表
```sql
-- 1. 先删除子表
DROP TABLE IF EXISTS learning_records;

-- 2. 再删除父表
DROP TABLE IF EXISTS student_assignments;
DROP TABLE IF EXISTS assignments;
DROP TABLE IF EXISTS units;
```

### 方法3: 临时禁用外键检查

**⚠️ 警告：这种方法有风险，仅建议在开发环境使用**

```sql
-- 临时禁用外键检查
SET FOREIGN_KEY_CHECKS = 0;

-- 现在可以按任意顺序删除表
DROP TABLE IF EXISTS assignments;
DROP TABLE IF EXISTS student_assignments;
DROP TABLE IF EXISTS units;
DROP TABLE IF EXISTS learning_records;

-- 重新启用外键检查
SET FOREIGN_KEY_CHECKS = 1;
```

## 📋 推荐的执行步骤

### 步骤1: 连接数据库
```bash
mysql -u root -p
use english_learning;  -- 使用你的数据库名称
```

### 步骤2: 查看当前的表关系
```sql
-- 执行 FIX_FOREIGN_KEY_CONSTRAINT.sql
-- 查看哪些表引用了我们要删除的表
```

### 步骤3: 执行安全删除
```sql
-- 执行 SAFE_DROP_TABLES.sql
-- 这会按正确的顺序删除表
```

### 步骤4: 创建新表
```sql
-- 现在可以执行 DATABASE_TABLES_NO_DELETED.sql
-- 创建没有 deleted 字段的新表
```

### 步骤5: 验证结果
```sql
-- 检查表是否正确创建
SHOW TABLES LIKE 'assignments';
SHOW TABLES LIKE 'student_assignments';
SHOW TABLES LIKE 'units';

-- 检查表结构
DESCRIBE assignments;
```

## 🔧 其他可能的外键约束

除了 `learning_records`，还可能有其他表引用这些表。常见的可能：

1. **learning_records** → **assignments**
2. **student_progress** → **assignments**
3. **task_submissions** → **assignments**
4. **assignment_feedback** → **assignments**

如果遇到类似错误，请先删除这些子表。

## 🚀 删除后的重建

删除完成后，执行新表创建：

```sql
-- 执行 DATABASE_TABLES_NO_DELETED.sql
-- 这将创建没有 deleted 字段的新表结构
```

新的表结构特点：
- ✅ 无 `deleted` 字段
- ✅ 使用物理删除
- ✅ 简化的表结构
- ✅ 更好的性能

## ✅ 成功验证标准

修复成功的标志：
1. ✅ 所有旧表成功删除
2. ✅ 新表成功创建（无deleted字段）
3. ✅ Spring Boot应用启动无错误
4. ✅ 教师端任务管理功能正常
5. ✅ 不再出现外键约束错误

## 🆘 如果仍有问题

如果按照上述步骤仍无法解决：

1. **查看完整的表结构**:
   ```sql
   SHOW TABLES;
   SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE();
   ```

2. **查看所有外键约束**:
   ```sql
   SELECT * FROM information_schema.TABLE_CONSTRAINTS
   WHERE TABLE_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'FOREIGN KEY';
   ```

3. **提供错误信息**:
   - 完整的错误消息
   - 你执行的SQL语句
   - 当前的表列表

---

**执行完成后的结果**: 所有相关的表将被正确删除，可以重新创建没有deleted字段的新表，彻底解决外键约束问题！