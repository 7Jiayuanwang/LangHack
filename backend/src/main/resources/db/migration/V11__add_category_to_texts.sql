-- 添加课文类别字段（幂等）
-- 使用 prepared statement 检查字段是否存在
SET @db = DATABASE();
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = @db AND table_name = 'texts' AND column_name = 'category';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE texts ADD COLUMN category VARCHAR(50) DEFAULT NULL COMMENT ''课文类别'' AFTER tags',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
