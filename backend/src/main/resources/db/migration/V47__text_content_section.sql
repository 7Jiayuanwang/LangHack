-- Add the second-level content classification used by in-class textbooks.
SET @dbname = DATABASE();
SET @tablename = 'texts';
SET @columnname = 'content_section';

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE table_schema = @dbname AND table_name = @tablename AND column_name = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE texts ADD COLUMN content_section VARCHAR(50) DEFAULT NULL COMMENT ''教材内容分类'' AFTER book_volume'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- The fourth option was renamed in the product taxonomy.
UPDATE texts
SET book_volume = '理解当代中国'
WHERE book_volume = '综合教程4';
