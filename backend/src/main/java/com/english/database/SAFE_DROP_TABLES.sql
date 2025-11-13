-- 安全删除表脚本（处理外键约束）
-- 按照正确的顺序删除表，避免外键约束冲突

-- 1. 首先删除引用其他表的表（子表）
-- 如果 learning_records 表存在并引用 assignments 表
DROP TABLE IF EXISTS learning_records;

-- 如果其他表引用 assignments 或 student_assignments，也在这里删除
-- (请根据实际情况添加)

-- 2. 然后删除被引用的表（父表）
DROP TABLE IF EXISTS student_assignments;
DROP TABLE IF EXISTS assignments;
DROP TABLE IF EXISTS units;

-- 3. 验证表是否已删除
SELECT
    TABLE_NAME,
    CASE
        WHEN TABLE_NAME IS NULL THEN '表已删除'
        ELSE '表仍存在'
    END as status
FROM
    information_schema.TABLES
WHERE
    TABLE_SCHEMA = 'english_learning'
    AND TABLE_NAME IN ('assignments', 'student_assignments', 'units', 'learning_records')
ORDER BY TABLE_NAME;

-- 4. 显示剩余的表（用于确认）
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'english_learning'
ORDER BY TABLE_NAME;