-- 删除已存在的表（如果需要重新创建）
-- ⚠️ 警告：这将删除所有数据，请确认后再执行

-- 删除表（按依赖关系倒序）
DROP TABLE IF EXISTS student_assignments;
DROP TABLE IF EXISTS assignments;
DROP TABLE IF EXISTS units;

-- 验证表已删除
SELECT 'Tables dropped successfully' as status;

-- 现在可以执行 DATABASE_TABLES_NO_DELETED.sql 创建新表