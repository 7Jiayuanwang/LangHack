-- ═══════════════════════════════════════════════════════════════
-- V11: AI 对话持久化 + 反馈 + text_learning_records 版本追踪
-- 整合自:
--   V27__ai_conversation_persistence.sql           (ai_conversations/messages/shares)
--   V31__add_content_version_to_text_learning_records.sql
--     (text_learning_records.content_version
--      + vocabulary_books.source_type
--      — 已是与 V31 source_type 合并后的最终版本)
--   V43__ai_feedback.sql                          (ai_feedback 表)
-- ═══════════════════════════════════════════════════════════════

-- ── Part 1: ai_conversations 对话会话表（V27 整合） ───────────────────────────

CREATE TABLE IF NOT EXISTS ai_conversations (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(200) COMMENT '对话标题（自动生成或用户编辑）',
    message_count INT         DEFAULT 0,
    created_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      DEFAULT 0,
    INDEX idx_user_time (user_id, updated_time),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话会话';

-- ── Part 2: ai_conversations 幂等列补齐（V27 整合字段，外加 charset/collation） ─

DROP PROCEDURE IF EXISTS _v11_ai_conversations_charset;
CREATE PROCEDURE _v11_ai_conversations_charset()
BEGIN
    -- 表 charset/collation 幂等修正（生产历史手动建表可能缺 utf8mb4）
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ai_conversations'
    ) THEN
        ALTER TABLE ai_conversations
            CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    END IF;
END;
CALL _v11_ai_conversations_charset();
DROP PROCEDURE IF EXISTS _v11_ai_conversations_charset;

-- ── Part 3: ai_messages 对话消息表（V27 整合） ───────────────────────────────

CREATE TABLE IF NOT EXISTS ai_messages (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    role            ENUM('user','assistant') NOT NULL,
    content         TEXT         NOT NULL,
    selected_text   TEXT         COMMENT '用户选中的文本（仅 user 消息）',
    article_context TEXT         COMMENT '文章上下文（仅 user 消息）',
    created_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id, created_time),
    FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息';

-- ── Part 4: conversation_shares 对话分享表（V27 整合） ────────────────────────

CREATE TABLE IF NOT EXISTS conversation_shares (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    share_token     VARCHAR(64)  UNIQUE NOT NULL COMMENT '分享令牌',
    access_password VARCHAR(128) COMMENT '访问密码（BCrypt 加密，可选）',
    expires_at      DATETIME     COMMENT '过期时间（NULL = 永久）',
    view_count      INT          DEFAULT 0,
    created_by      BIGINT       NOT NULL,
    created_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (share_token),
    INDEX idx_conversation (conversation_id),
    FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话分享';

-- ── Part 5: text_learning_records.content_version（V31 Part 1 整合） ──────────
-- 记录学生阅读的内容版本（CET-4 / CET-6 / Academic / Chinese Parallel）

DROP PROCEDURE IF EXISTS _v11_tlr_content_version;
CREATE PROCEDURE _v11_tlr_content_version()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_learning_records'
          AND COLUMN_NAME = 'content_version'
    ) THEN
        ALTER TABLE text_learning_records ADD COLUMN content_version VARCHAR(20) DEFAULT NULL
            COMMENT 'Content version: original/cet4/cet6/academic/chinese_parallel'
            AFTER text_id;
    END IF;
END;
CALL _v11_tlr_content_version();
DROP PROCEDURE IF EXISTS _v11_tlr_content_version;

DROP PROCEDURE IF EXISTS _v11_tlr_version_idx;
CREATE PROCEDURE _v11_tlr_version_idx()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_learning_records'
          AND INDEX_NAME = 'idx_tlr_student_version'
    ) THEN
        CREATE INDEX idx_tlr_student_version ON text_learning_records(student_id, content_version);
    END IF;
END;
CALL _v11_tlr_version_idx();
DROP PROCEDURE IF EXISTS _v11_tlr_version_idx;

-- ── Part 6: vocabulary_books.source_type（V31 Part 2 整合） ───────────────────
-- 生词本来源标记：ARTICLE=文章阅读加入 / MANUAL=手动添加或其他来源

DROP PROCEDURE IF EXISTS _v11_vocab_source_type;
CREATE PROCEDURE _v11_vocab_source_type()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'vocabulary_books'
          AND COLUMN_NAME = 'source_type'
    ) THEN
        ALTER TABLE vocabulary_books
            ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
                COMMENT '来源类型：ARTICLE-文章阅读，MANUAL-手动添加或其他'
                AFTER next_review_time;
    END IF;
END;
CALL _v11_vocab_source_type();
DROP PROCEDURE IF EXISTS _v11_vocab_source_type;

UPDATE vocabulary_books SET source_type = 'MANUAL' WHERE source_type IS NULL OR source_type = '';

DROP PROCEDURE IF EXISTS _v11_vocab_source_idx;
CREATE PROCEDURE _v11_vocab_source_idx()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'vocabulary_books'
          AND INDEX_NAME = 'idx_vocabulary_source'
    ) THEN
        CREATE INDEX idx_vocabulary_source ON vocabulary_books(student_id, source_type);
    END IF;
END;
CALL _v11_vocab_source_idx();
DROP PROCEDURE IF EXISTS _v11_vocab_source_idx;

-- ── Part 7: ai_feedback 用户反馈表（V43 整合） ───────────────────────────────

CREATE TABLE IF NOT EXISTS ai_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    call_log_id BIGINT NOT NULL COMMENT 'AI调用记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    rating TINYINT COMMENT '1-5星评分',
    is_helpful BOOLEAN COMMENT '是否有帮助',
    feedback_text TEXT COMMENT '文字反馈',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_call_log (call_log_id),
    INDEX idx_user (user_id),
    FOREIGN KEY (call_log_id) REFERENCES ai_call_log(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI用户反馈表';

-- ═══════════════════════════════════════════════════════════════
-- texts.category 字段（整合自 V11__add_category_to_texts.sql）
-- ═══════════════════════════════════════════════════════════════
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