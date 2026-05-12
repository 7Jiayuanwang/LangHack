-- V22: 补齐 read_count 相关字段（V17 遗漏 texts.read_count，且历史 DB 未应用 V17 的 user_text_reads 部分）

DROP PROCEDURE IF EXISTS _fix_reading_count_cols;
CREATE PROCEDURE _fix_reading_count_cols()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'texts'
          AND COLUMN_NAME = 'read_count'
    ) THEN
        ALTER TABLE texts ADD COLUMN read_count INT NOT NULL DEFAULT 0 COMMENT '阅读次数';
    END IF;

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
CALL _fix_reading_count_cols();
DROP PROCEDURE IF EXISTS _fix_reading_count_cols;
