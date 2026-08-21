-- 文章分类字段：课内教材课文/课外读物
-- 使用存在性判断，兼容字段已存在的环境

SET @dbname = DATABASE();
SET @tablename = 'texts';

-- 添加 article_type 字段（如果不存在）
SET @columnname = 'article_type';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = @dbname AND table_name = @tablename AND column_name = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE texts ADD COLUMN article_type ENUM(''INNER'', ''OUTER'') DEFAULT ''OUTER'' COMMENT ''文章类型：课内教材课文/课外读物'' AFTER unit_id'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 添加 book_volume 字段（如果不存在）
SET @columnname = 'book_volume';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = @dbname AND table_name = @tablename AND column_name = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE texts ADD COLUMN book_volume VARCHAR(50) DEFAULT NULL COMMENT ''教材册号，例如综合教程1'' AFTER article_type'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 添加 unit_num 字段（如果不存在）
SET @columnname = 'unit_num';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = @dbname AND table_name = @tablename AND column_name = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE texts ADD COLUMN unit_num INT DEFAULT NULL COMMENT ''教材单元编号'' AFTER book_volume'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 创建索引（如果不存在）
SET @indexname = 'idx_texts_article_filter';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema = @dbname AND table_name = @tablename AND index_name = @indexname) > 0,
  'SELECT 1',
  'CREATE INDEX idx_texts_article_filter ON texts (article_type, book_volume, unit_num)'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
