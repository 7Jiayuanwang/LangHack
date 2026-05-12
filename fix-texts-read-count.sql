-- 修复 texts 表缺少 read_count 字段的问题
-- 此字段在 Text 实体中定义，但从未在迁移脚本中创建

USE english_learning;

-- 检查并添加 read_count 字段
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'texts'
      AND COLUMN_NAME = 'read_count'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE texts ADD COLUMN read_count INT NOT NULL DEFAULT 0 COMMENT ''阅读次数'' AFTER like_count',
    'SELECT ''Column read_count already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证
SELECT
    COLUMN_NAME,
    COLUMN_TYPE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'texts'
  AND COLUMN_NAME IN ('view_count', 'like_count', 'read_count')
ORDER BY ORDINAL_POSITION;
