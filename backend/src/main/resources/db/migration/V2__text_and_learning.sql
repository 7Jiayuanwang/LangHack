-- ═══════════════════════════════════════════════════════════════
-- V2: 课文增强 + 学习记录
-- 整合自: 原 V2 (text_id, chinese_parallel, markdown, markdown versions, unit_id nullable,
--         text_learning_records, user_text_reads, is_featured)
--         + V10 (featured_priority + index)
--         + V17 (cet4/6/academic_content, uploaded_by, tags, view/like_count, is_public, status)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 学习记录添加课文关联 [原 V2]
--    注意: Flyway 不支持 DELIMITER 命令，直接定义存储过程
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _add_col_lr_text;
CREATE PROCEDURE _add_col_lr_text()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='learning_records' AND column_name='text_id') THEN
        ALTER TABLE learning_records ADD COLUMN text_id BIGINT COMMENT '关联的课文ID' AFTER word_id;
    END IF;
END;
CALL _add_col_lr_text();
DROP PROCEDURE IF EXISTS _add_col_lr_text;

DROP PROCEDURE IF EXISTS _add_fk_lr_text;
CREATE PROCEDURE _add_fk_lr_text()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE table_schema = DATABASE() AND table_name = 'learning_records' AND constraint_name = 'fk_learning_record_text'
    ) THEN
        ALTER TABLE learning_records ADD CONSTRAINT fk_learning_record_text
            FOREIGN KEY (text_id) REFERENCES texts(id) ON DELETE SET NULL;
    END IF;
END;
CALL _add_fk_lr_text();
DROP PROCEDURE IF EXISTS _add_fk_lr_text;

DROP PROCEDURE IF EXISTS _add_index_lr_text;
CREATE PROCEDURE _add_index_lr_text()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE() AND table_name = 'learning_records' AND index_name = 'idx_text_id'
    ) THEN
        CREATE INDEX idx_text_id ON learning_records (text_id);
    END IF;
END;
CALL _add_index_lr_text();
DROP PROCEDURE IF EXISTS _add_index_lr_text;

-- ────────────────────────────────────────
-- 2. 课文表扩展 — 统一批量添加所有列 [原 V2 + V10 + V17]
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _add_col_texts_batch;
CREATE PROCEDURE _add_col_texts_batch()
BEGIN
    -- 中英对照内容 [原 V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='chinese_parallel_content') THEN
        ALTER TABLE texts ADD COLUMN chinese_parallel_content TEXT COMMENT '中英对照版本内容';
    END IF;
    -- Markdown 支持 [原 V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='markdown_content') THEN
        ALTER TABLE texts ADD COLUMN markdown_content LONGTEXT COMMENT 'Markdown格式的课文内容' AFTER content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='content_type') THEN
        ALTER TABLE texts ADD COLUMN content_type ENUM('TEXT','MARKDOWN') DEFAULT 'TEXT' COMMENT '内容类型：TEXT-纯文本，MARKDOWN-Markdown格式' AFTER markdown_content;
    END IF;
    -- 多难度版本 Markdown [原 V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet4_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN cet4_markdown_content LONGTEXT COMMENT 'CET-4难度的Markdown版本' AFTER chinese_parallel_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet6_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN cet6_markdown_content LONGTEXT COMMENT 'CET-6难度的Markdown版本' AFTER cet4_markdown_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='academic_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN academic_markdown_content LONGTEXT COMMENT '学术版的Markdown版本' AFTER cet6_markdown_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='chinese_parallel_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN chinese_parallel_markdown_content LONGTEXT COMMENT '中英对照的Markdown版本' AFTER academic_markdown_content;
    END IF;
    -- 推荐文章标识 [原 V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='is_featured') THEN
        ALTER TABLE texts ADD COLUMN is_featured TINYINT(1) DEFAULT 0 COMMENT '是否推荐';
    END IF;
    -- 推荐优先级 [原 V10]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='featured_priority') THEN
        ALTER TABLE texts ADD COLUMN featured_priority INT NOT NULL DEFAULT 0 COMMENT '推荐优先级，值越大越靠前显示（0=默认）';
    END IF;
    -- 多难度版本内容（纯文本）[原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet4_content') THEN
        ALTER TABLE texts ADD COLUMN cet4_content LONGTEXT COMMENT 'CET-4难度版本内容' AFTER content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet6_content') THEN
        ALTER TABLE texts ADD COLUMN cet6_content LONGTEXT COMMENT 'CET-6难度版本内容' AFTER cet4_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='academic_content') THEN
        ALTER TABLE texts ADD COLUMN academic_content LONGTEXT COMMENT '学术版版本内容' AFTER cet6_content;
    END IF;
    -- 上传者 [原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='uploaded_by') THEN
        ALTER TABLE texts ADD COLUMN uploaded_by BIGINT DEFAULT NULL COMMENT '上传者用户ID';
    END IF;
    -- 标签 [原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='tags') THEN
        ALTER TABLE texts ADD COLUMN tags VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔';
    END IF;
    -- 浏览/点赞 [原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='view_count') THEN
        ALTER TABLE texts ADD COLUMN view_count INT DEFAULT 0 COMMENT '浏览次数';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='like_count') THEN
        ALTER TABLE texts ADD COLUMN like_count INT DEFAULT 0 COMMENT '点赞次数';
    END IF;
    -- 公开状态 [原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='is_public') THEN
        ALTER TABLE texts ADD COLUMN is_public TINYINT(1) DEFAULT 1 COMMENT '是否公开（0=否，1=是）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='status') THEN
        ALTER TABLE texts ADD COLUMN status VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT '状态：DRAFT/PUBLISHED/ARCHIVED';
    END IF;
END;
CALL _add_col_texts_batch();
DROP PROCEDURE IF EXISTS _add_col_texts_batch;

-- 推荐优先级复合索引 [原 V10]
DROP PROCEDURE IF EXISTS _add_idx_featured_priority;
CREATE PROCEDURE _add_idx_featured_priority()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE() AND table_name = 'texts' AND index_name = 'idx_texts_featured_priority'
    ) THEN
        CREATE INDEX idx_texts_featured_priority ON texts (is_featured, featured_priority DESC);
    END IF;
END;
CALL _add_idx_featured_priority();
DROP PROCEDURE IF EXISTS _add_idx_featured_priority;

-- unit_id 改为可空（支持课文从单元中移除）[原 V2]
ALTER TABLE texts MODIFY COLUMN unit_id BIGINT DEFAULT NULL
    COMMENT '单元ID（可为空，表示未分配到单元）';

-- ────────────────────────────────────────
-- 3. 创建文章学习记录表 [原 V2]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS text_learning_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    text_id BIGINT NOT NULL COMMENT '课文ID',
    assignment_id BIGINT COMMENT '关联的任务ID',
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读开始时间',
    end_time DATETIME COMMENT '阅读结束时间',
    duration_seconds INT DEFAULT 0 COMMENT '阅读时长(秒)',
    is_completed TINYINT(1) DEFAULT 0 COMMENT '是否已完成阅读',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_student_text (student_id, text_id),
    INDEX idx_assignment (assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章学习记录表';

-- ────────────────────────────────────────
-- 4. 创建用户文章已读记录表 [原 V2]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_text_reads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_id BIGINT NOT NULL,
    read_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_text (user_id, text_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户文章已读记录表';
