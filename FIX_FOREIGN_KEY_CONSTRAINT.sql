-- 解决外键约束问题的脚本
-- 处理 learning_records 表引用 assignments 表的外键约束

-- 1. 查看所有外键约束
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM
    information_schema.KEY_COLUMN_USAGE
WHERE
    TABLE_SCHEMA = 'english_learning'
    AND REFERENCED_TABLE_NAME IS NOT NULL
    AND REFERENCED_TABLE_NAME IN ('assignments', 'student_assignments', 'units')
ORDER BY
    TABLE_NAME, CONSTRAINT_NAME;

-- 2. 如果存在 learning_records 表的外键约束，先删除它
-- 找到约束名称后执行删除语句
-- ALTER TABLE learning_records DROP FOREIGN KEY learning_records_ibfk_3;

-- 3. 检查是否有其他表引用这些表
SELECT
    TABLE_NAME as '表名',
    COLUMN_NAME as '字段名',
    REFERENCED_TABLE_NAME as '引用表',
    REFERENCED_COLUMN_NAME as '引用字段',
    CONSTRAINT_NAME as '约束名称'
FROM
    information_schema.KEY_COLUMN_USAGE
WHERE
    TABLE_SCHEMA = 'english_learning'
    AND REFERENCED_TABLE_NAME IN ('assignments', 'student_assignments', 'units')
ORDER BY
    REFERENCED_TABLE_NAME, TABLE_NAME;