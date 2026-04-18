-- ═══════════════════════════════════════════════════════════════
-- V3: 媒体表 + 词汇SM-2 + 划线标注 + 媒体路径列
-- 整合自: 原 V3 (media, SM-2, underlines)
--         + V9 (media file_path/thumbnail_path)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 创建媒体表 [原 V3]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS media (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '媒体ID',
    media_type ENUM('IMAGE','VIDEO') NOT NULL COMMENT '媒体类型',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_content LONGBLOB NOT NULL COMMENT '文件内容',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    mime_type VARCHAR(100) COMMENT 'MIME类型',
    width INT COMMENT '宽度（像素，仅图片）',
    height INT COMMENT '高度（像素，仅图片）',
    duration INT COMMENT '时长（秒，仅视频）',
    uploaded_by BIGINT COMMENT '上传者ID',
    text_id BIGINT COMMENT '关联的课文ID',
    description VARCHAR(500) COMMENT '媒体描述',
    status ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'APPROVED' COMMENT '审核状态',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_media_type (media_type),
    INDEX idx_uploaded_by (uploaded_by),
    INDEX idx_text_id (text_id),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体表（图片和视频）';

-- ────────────────────────────────────────
-- 2. 媒体路径列 [原 V9]
--    注意: Flyway 不支持 DELIMITER 命令，直接定义存储过程
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _add_col_media_paths;
CREATE PROCEDURE _add_col_media_paths()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='media' AND column_name='file_path') THEN
        ALTER TABLE media ADD COLUMN file_path VARCHAR(500) COMMENT 'stored media file path';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='media' AND column_name='thumbnail_path') THEN
        ALTER TABLE media ADD COLUMN thumbnail_path VARCHAR(500) COMMENT 'stored thumbnail file path';
    END IF;
END;
CALL _add_col_media_paths();
DROP PROCEDURE IF EXISTS _add_col_media_paths;

-- ────────────────────────────────────────
-- 3. 词汇本添加 SM-2 间隔重复字段 [原 V3]
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _add_col_sm2;
CREATE PROCEDURE _add_col_sm2()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='repetition') THEN
        ALTER TABLE vocabulary_books ADD COLUMN repetition INT DEFAULT 0 COMMENT 'SM-2 重复次数';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='interval_days') THEN
        ALTER TABLE vocabulary_books ADD COLUMN interval_days INT DEFAULT 0 COMMENT 'SM-2 间隔天数';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='easiness_factor') THEN
        ALTER TABLE vocabulary_books ADD COLUMN easiness_factor DOUBLE DEFAULT 2.5 COMMENT 'SM-2 简易因子';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='next_review_time') THEN
        ALTER TABLE vocabulary_books ADD COLUMN next_review_time DATETIME COMMENT 'SM-2 下次复习时间';
    END IF;
END;
CALL _add_col_sm2();
DROP PROCEDURE IF EXISTS _add_col_sm2;

-- ────────────────────────────────────────
-- 4. 创建划线标注表 [原 V3]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS underlines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_id BIGINT NOT NULL,
    selected_text TEXT COMMENT '用户选中的文本内容',
    start_index INT COMMENT '起始位置',
    end_index INT COMMENT '结束位置',
    serialized_range TEXT COMMENT 'Serialized DOM range for robust highlighting',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_text_id (text_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户划线标注表';
