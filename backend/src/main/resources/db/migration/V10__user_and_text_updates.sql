-- ═══════════════════════════════════════════════════════════════
-- V10: 用户 + 文本 + 词汇表字段补丁（整合）
-- 整合自: 原 V10 (users nickname+avatar) + 原 V11 (texts featured + vocabulary_books SM-2)
-- 注意: Flyway 在本项目禁用 (application.yml flyway.enabled=false)，
--       实际生产部署走 docker/mysql/init/04-apply-all-migrations.sql
-- ═══════════════════════════════════════════════════════════════

-- ── Part 1: users 表 nickname 列 + avatar_url 重命名 ────────────
-- 原 V10: 添加 users.nickname 列，重命名 users.avatar_url → avatar
-- 原因: User 实体使用 nickname 和 avatar 字段

SET @dbname = DATABASE();
SET @tablename = 'users';

-- nickname
SET @colname = 'nickname';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN nickname VARCHAR(50) DEFAULT NULL COMMENT ''昵称'' AFTER role'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 重命名 avatar_url → avatar（幂等）
SET @colname = 'avatar';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE users RENAME COLUMN avatar_url TO avatar'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ── Part 2: texts 表 featured 字段 + vocabulary_books 表 SM-2 字段 ──
-- 原 V11: 原因: Text 实体有 cover_url/is_featured/featured_priority 字段，
--        VocabularyBook 实体有 repetition/interval_days/easiness_factor/next_review_time

-- texts.cover_url
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

-- texts.is_featured
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

-- texts.featured_priority
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

-- texts.idx_is_featured
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'texts' AND INDEX_NAME = 'idx_is_featured');
SET @preparedStatement = (SELECT IF(@idx_exists > 0,
    'SELECT 1',
    'ALTER TABLE texts ADD INDEX idx_is_featured (is_featured)'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- vocabulary_books.repetition
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

-- vocabulary_books.interval_days
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

-- vocabulary_books.easiness_factor
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

-- vocabulary_books.next_review_time
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
