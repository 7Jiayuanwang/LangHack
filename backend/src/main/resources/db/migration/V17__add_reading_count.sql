-- V17: 为 user_text_reads 表添加阅读次数字段，为 users 表添加阅读统计字段

DROP PROCEDURE IF EXISTS _add_reading_count_cols;
CREATE PROCEDURE _add_reading_count_cols()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user_text_reads'
          AND COLUMN_NAME = 'read_count'
    ) THEN
        ALTER TABLE user_text_reads ADD COLUMN read_count INT NOT NULL DEFAULT 1 COMMENT '阅读次数';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user_text_reads'
          AND COLUMN_NAME = 'last_read_at'
    ) THEN
        ALTER TABLE user_text_reads ADD COLUMN last_read_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后阅读时间';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'read_article_count'
    ) THEN
        ALTER TABLE users ADD COLUMN read_article_count INT NOT NULL DEFAULT 0 COMMENT '已读文章数';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'read_total_times'
    ) THEN
        ALTER TABLE users ADD COLUMN read_total_times INT NOT NULL DEFAULT 0 COMMENT '累计阅读次数';
    END IF;
END;
CALL _add_reading_count_cols();
DROP PROCEDURE IF EXISTS _add_reading_count_cols;
