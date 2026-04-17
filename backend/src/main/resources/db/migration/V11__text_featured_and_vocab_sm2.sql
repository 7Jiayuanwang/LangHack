-- V11: 添加 texts 表推荐字段 + vocabulary_books 表 SM-2 算法字段
-- 原因: Text 实体有 cover_url/is_featured/featured_priority 字段，
--        VocabularyBook 实体有 repetition/interval_days/easiness_factor/next_review_time 字段，
--        但数据库缺少这些列，导致 MyBatis Plus SELECT 报 "Unknown column" 错误

-- ── texts 表 ──────────────────────────────────────

SET @dbname = DATABASE();

-- cover_url
SET @colname = 'cover_url';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'texts' AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE texts ADD COLUMN cover_url VARCHAR(255) DEFAULT NULL COMMENT ''封面图URL'' AFTER chinese_parallel_markdown_content'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- is_featured
SET @colname = 'is_featured';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'texts' AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE texts ADD COLUMN is_featured TINYINT(1) DEFAULT 0 COMMENT ''是否推荐'''
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- featured_priority
SET @colname = 'featured_priority';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'texts' AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE texts ADD COLUMN featured_priority INT DEFAULT 0 COMMENT ''推荐优先级'''
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- is_featured 索引
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'texts' AND INDEX_NAME = 'idx_is_featured');
SET @preparedStatement = (SELECT IF(@idx_exists > 0,
    'SELECT 1',
    'ALTER TABLE texts ADD INDEX idx_is_featured (is_featured)'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ── vocabulary_books 表 ──────────────────────────────

-- repetition
SET @colname = 'repetition';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'vocabulary_books' AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE vocabulary_books ADD COLUMN repetition INT DEFAULT 0 COMMENT ''SM2重复次数'' AFTER mastery_level'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- interval_days
SET @colname = 'interval_days';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'vocabulary_books' AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE vocabulary_books ADD COLUMN interval_days INT DEFAULT 1 COMMENT ''SM2间隔天数'' AFTER repetition'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- easiness_factor
SET @colname = 'easiness_factor';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'vocabulary_books' AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE vocabulary_books ADD COLUMN easiness_factor DOUBLE DEFAULT 2.5 COMMENT ''SM2难度因子'' AFTER interval_days'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- next_review_time
SET @colname = 'next_review_time';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'vocabulary_books' AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE vocabulary_books ADD COLUMN next_review_time DATETIME DEFAULT NULL COMMENT ''下次复习时间'' AFTER easiness_factor'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
