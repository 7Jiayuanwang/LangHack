-- ══════════════════════════════════════════════════════════════
-- LangHack 合并迁移脚本（MySQL 8.0 兼容）
-- 将 Flyway V1 ~ V9 的所有迁移合并为单个幂等脚本
--
-- 对应 Flyway 迁移文件（整合后）:
--   V1__scoring_and_assignments.sql         (原 V1)
--   V2__text_and_learning.sql               (原 V2 + V10 + V17)
--   V3__media_and_annotations.sql           (原 V3 + V9)
--   V4__backup_and_update_system.sql        (原 V4 + V8)
--   V5__monitoring_and_access.sql           (原 V5)
--   V6__system_config_and_ai_providers.sql  (原 V6 + V7 + V11)
--   V7__feature_toggles.sql                 (原 V12 + V13 + V14)
--   V8__sso_integration.sql                 (原 V15)
--   V9__school_data_sync.sql                (原 V16)
--   V10__user_nickname_and_avatar_rename.sql (独立追加)
--   V11__text_featured_and_vocab_sm2.sql     (独立追加)
--   V12__fix_role_mapping_cleanup.sql         (独立追加)
--
-- 此脚本有两个用途：
--   1. 新环境初始化（docker-entrypoint-initdb.d，仅首次启动执行）
--   2. 存量升级（deploy.sh 在已有数据库上执行，每次部署自动应用）
-- 无论数据库处于何种状态，本脚本均可安全重复执行（全幂等）
-- ══════════════════════════════════════════════════════════════

USE `english_learning`;

-- ──────────────────────────────────────────────────────────────────
-- 强制设置会话字符集（init-connect 对 SUPER/root 用户不生效）
-- 必须在此显式声明，确保所有中文数据以 utf8mb4 正确存储
-- ──────────────────────────────────────────────────────────────────
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ═══════════════════════════════════════════════════════════════
-- V1: 成绩记录表 + 任务扩展
-- 整合自: V1 (score_records), V1 (weight), V1 (ideal_study_time), V2 (unique constraint)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 创建成绩记录表 [V1]
--    唯一约束 uk_student_assignment_type 已内联 [原 V1 + V17]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS score_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    assignment_id BIGINT COMMENT '任务ID',
    course_id BIGINT COMMENT '课程ID',
    unit_id BIGINT COMMENT '单元ID',
    score_type VARCHAR(50) NOT NULL COMMENT '分数类型: ASSIGNMENT/COURSE/UNIT/EXAM',
    score INT NOT NULL COMMENT '分数值(0-100)',
    full_score INT NOT NULL DEFAULT 100 COMMENT '满分值',
    score_details TEXT COMMENT 'JSON格式的得分详情',
    remark VARCHAR(500) COMMENT '备注',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT fk_score_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    -- 防止同一学生同一任务重复评分 (assignment_id 为 NULL 时不生效，这是预期行为)
    UNIQUE KEY uk_student_assignment_type (student_id, assignment_id, score_type),
    INDEX idx_student_id (student_id),
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_course_id (course_id),
    INDEX idx_unit_id (unit_id),
    INDEX idx_score_type (score_type),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩记录表';

-- ────────────────────────────────────────
-- 2. 任务表扩展 [V1]
-- ────────────────────────────────────────
-- 任务权重（加权平均分）
DROP PROCEDURE IF EXISTS _add_col_weight;
DELIMITER //
CREATE PROCEDURE _add_col_weight()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE table_schema = DATABASE() AND table_name = 'assignments' AND column_name = 'weight'
    ) THEN
        ALTER TABLE assignments ADD COLUMN weight DECIMAL(5,2) DEFAULT 1.00
            COMMENT '任务权重（用于计算加权平均分，默认1.00）' AFTER deadline;
    END IF;
END//
DELIMITER ;
CALL _add_col_weight();
DROP PROCEDURE IF EXISTS _add_col_weight;
DROP PROCEDURE IF EXISTS _add_index_weight;
DELIMITER //
CREATE PROCEDURE _add_index_weight()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE() AND table_name = 'assignments' AND index_name = 'idx_weight'
    ) THEN
        CREATE INDEX idx_weight ON assignments (weight);
    END IF;
END//
DELIMITER ;
CALL _add_index_weight();
DROP PROCEDURE IF EXISTS _add_index_weight;

-- 理想学习时长
DROP PROCEDURE IF EXISTS _add_col_study_time;
DELIMITER //
CREATE PROCEDURE _add_col_study_time()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE table_schema = DATABASE() AND table_name = 'assignments' AND column_name = 'ideal_study_time_minutes'
    ) THEN
        ALTER TABLE assignments ADD COLUMN ideal_study_time_minutes INT DEFAULT 60
            COMMENT '理想学习时长（分钟），默认60分钟' AFTER weight;
    END IF;
END//
DELIMITER ;
CALL _add_col_study_time();
DROP PROCEDURE IF EXISTS _add_col_study_time;
DROP PROCEDURE IF EXISTS _add_index_study_time;
DELIMITER //
CREATE PROCEDURE _add_index_study_time()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE() AND table_name = 'assignments' AND index_name = 'idx_ideal_study_time'
    ) THEN
        CREATE INDEX idx_ideal_study_time ON assignments (ideal_study_time_minutes);
    END IF;
END//
DELIMITER ;
CALL _add_index_study_time();
DROP PROCEDURE IF EXISTS _add_index_study_time;

-- ═══════════════════════════════════════════════════════════════
-- V2: 课文增强 + 学习记录
-- 整合自: 原 V2 (text_id, chinese_parallel, markdown, markdown versions, unit_id nullable,
--         text_learning_records, user_text_reads, is_featured)
--         + 原 V10 (featured_priority + index)
--         + 原 V17 (cet4/6/academic_content, uploaded_by, tags, view/like_count, is_public, status)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 学习记录添加课文关联 [V2]
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _add_col_lr_text;
DELIMITER //
CREATE PROCEDURE _add_col_lr_text()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='learning_records' AND column_name='text_id') THEN
        ALTER TABLE learning_records ADD COLUMN text_id BIGINT COMMENT '关联的课文ID' AFTER word_id;
    END IF;
END//
DELIMITER ;
CALL _add_col_lr_text();
DROP PROCEDURE IF EXISTS _add_col_lr_text;
DROP PROCEDURE IF EXISTS _add_fk_lr_text;
DELIMITER //
CREATE PROCEDURE _add_fk_lr_text()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE table_schema = DATABASE() AND table_name = 'learning_records' AND constraint_name = 'fk_learning_record_text'
    ) THEN
        ALTER TABLE learning_records ADD CONSTRAINT fk_learning_record_text
            FOREIGN KEY (text_id) REFERENCES texts(id) ON DELETE SET NULL;
    END IF;
END//
DELIMITER ;
CALL _add_fk_lr_text();
DROP PROCEDURE IF EXISTS _add_fk_lr_text;
DROP PROCEDURE IF EXISTS _add_index_lr_text;
DELIMITER //
CREATE PROCEDURE _add_index_lr_text()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE() AND table_name = 'learning_records' AND index_name = 'idx_text_id'
    ) THEN
        CREATE INDEX idx_text_id ON learning_records (text_id);
    END IF;
END//
DELIMITER ;
CALL _add_index_lr_text();
DROP PROCEDURE IF EXISTS _add_index_lr_text;

-- ────────────────────────────────────────
-- 2. 课文表扩展 [V2: 原 V2 + V10 + V17]
-- ────────────────────────────────────────
-- 中英对照内容 [V2: 原 V2]
DROP PROCEDURE IF EXISTS _add_col_texts_batch;
DELIMITER //
CREATE PROCEDURE _add_col_texts_batch()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='chinese_parallel_content') THEN
        ALTER TABLE texts ADD COLUMN chinese_parallel_content TEXT COMMENT '中英对照版本内容';
    END IF;
    -- Markdown 支持 [V2: 原 V10]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='markdown_content') THEN
        ALTER TABLE texts ADD COLUMN markdown_content LONGTEXT COMMENT 'Markdown格式的课文内容' AFTER content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='content_type') THEN
        ALTER TABLE texts ADD COLUMN content_type ENUM('TEXT','MARKDOWN') DEFAULT 'TEXT' COMMENT '内容类型：TEXT-纯文本，MARKDOWN-Markdown格式' AFTER markdown_content;
    END IF;
    -- 多难度版本 Markdown [V2: 原 V12]
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
    -- 推荐文章标识 [V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='is_featured') THEN
        ALTER TABLE texts ADD COLUMN is_featured TINYINT(1) DEFAULT 0 COMMENT '是否推荐';
    END IF;
    -- 推荐优先级 [V2: 原 V10]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='featured_priority') THEN
        ALTER TABLE texts ADD COLUMN featured_priority INT NOT NULL DEFAULT 0 COMMENT '推荐优先级，值越大越靠前显示（0=默认随机）';
    END IF;
    -- 文章封面图 URL [新增]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cover_url') THEN
        ALTER TABLE texts ADD COLUMN cover_url VARCHAR(500) DEFAULT NULL COMMENT '文章封面图 URL' AFTER audio_url;
    END IF;
    -- 多难度版本内容（纯文本）[V2: 原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet4_content') THEN
        ALTER TABLE texts ADD COLUMN cet4_content LONGTEXT COMMENT 'CET-4难度版本内容' AFTER content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet6_content') THEN
        ALTER TABLE texts ADD COLUMN cet6_content LONGTEXT COMMENT 'CET-6难度版本内容' AFTER cet4_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='academic_content') THEN
        ALTER TABLE texts ADD COLUMN academic_content LONGTEXT COMMENT '学术版版本内容' AFTER cet6_content;
    END IF;
    -- 上传者 [V2: 原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='uploaded_by') THEN
        ALTER TABLE texts ADD COLUMN uploaded_by BIGINT DEFAULT NULL COMMENT '上传者用户ID';
    END IF;
    -- 标签 [V2: 原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='tags') THEN
        ALTER TABLE texts ADD COLUMN tags VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔';
    END IF;
    -- 浏览/点赞 [V2: 原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='view_count') THEN
        ALTER TABLE texts ADD COLUMN view_count INT DEFAULT 0 COMMENT '浏览次数';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='like_count') THEN
        ALTER TABLE texts ADD COLUMN like_count INT DEFAULT 0 COMMENT '点赞次数';
    END IF;
    -- 公开状态 [V2: 原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='is_public') THEN
        ALTER TABLE texts ADD COLUMN is_public TINYINT(1) DEFAULT 1 COMMENT '是否公开（0=否，1=是）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='status') THEN
        ALTER TABLE texts ADD COLUMN status VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT '状态：DRAFT/PUBLISHED/ARCHIVED';
    END IF;
END//
DELIMITER ;
CALL _add_col_texts_batch();
DROP PROCEDURE IF EXISTS _add_col_texts_batch;

-- 推荐优先级复合索引 [V2: 原 V10]
DROP PROCEDURE IF EXISTS _add_idx_featured_priority;
DELIMITER //
CREATE PROCEDURE _add_idx_featured_priority()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE() AND table_name = 'texts' AND index_name = 'idx_texts_featured_priority'
    ) THEN
        CREATE INDEX idx_texts_featured_priority ON texts (is_featured, featured_priority DESC);
    END IF;
END//
DELIMITER ;
CALL _add_idx_featured_priority();
DROP PROCEDURE IF EXISTS _add_idx_featured_priority;

-- unit_id 改为可空（支持课文从单元中移除）[V2]
ALTER TABLE texts MODIFY COLUMN unit_id BIGINT DEFAULT NULL
    COMMENT '单元ID（可为空，表示未分配到单元）';

-- ────────────────────────────────────────
-- 3. 创建文章学习记录表 [V2]
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
-- 4. 创建用户文章已读记录表 [V2]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_text_reads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_id BIGINT NOT NULL,
    read_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_text (user_id, text_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户文章已读记录表';

-- ═══════════════════════════════════════════════════════════════
-- V3: 媒体表 + 词汇SM-2 + 划线标注
-- 整合自: 原 V3 (media, SM-2, underlines)
--         + 原 V9 (media file_path/thumbnail_path)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 创建媒体表 [V3]
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
-- 2. 词汇本添加 SM-2 间隔重复字段 [V3]
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _add_col_sm2;
DELIMITER //
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
END//
DELIMITER ;
CALL _add_col_sm2();
DROP PROCEDURE IF EXISTS _add_col_sm2;

-- ────────────────────────────────────────
-- 3. 创建划线标注表 [V3]
--    serialized_range 字段已内联
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

-- ═══════════════════════════════════════════════════════════════
-- V4: 数据库备份系统 + 自动更新配置
-- 整合自: 原 V4 (backup_records, backup_operation_logs, backup_config)
--         + 原 V8 (update_config)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 创建备份记录表 [V4]
--    backup_mode, base_backup_id 已内联 [V4]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS backup_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL COMMENT '备份文件名',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    backup_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '备份类型: MANUAL/SCHEDULED',
    backup_mode VARCHAR(20) NOT NULL DEFAULT 'FULL' COMMENT '备份模式: FULL/INCREMENTAL',
    base_backup_id BIGINT DEFAULT NULL COMMENT '增量备份的基准全量备份ID',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态: IN_PROGRESS/SUCCESS/FAILED',
    storage_path VARCHAR(500) NOT NULL COMMENT '存储路径',
    created_by VARCHAR(100) COMMENT '创建者',
    error_message TEXT COMMENT '错误信息',
    database_size BIGINT DEFAULT 0 COMMENT '数据库大小(字节)',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    completed_time DATETIME COMMENT '完成时间',
    INDEX idx_backup_type (backup_type),
    INDEX idx_backup_mode (backup_mode),
    INDEX idx_status (status),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库备份记录表';

-- ────────────────────────────────────────
-- 2. 创建备份操作日志表 [V4]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS backup_operation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(100) NOT NULL COMMENT '操作人',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型: BACKUP_CREATE/DELETE/RESTORE/DOWNLOAD/CLEANUP/SCHEDULED',
    target_id BIGINT COMMENT '目标备份记录ID',
    target_name VARCHAR(255) COMMENT '目标备份文件名',
    detail VARCHAR(1000) COMMENT '操作详情',
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果: SUCCESS/FAILED',
    ip_address VARCHAR(50) COMMENT '操作IP地址',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_operator (operator),
    INDEX idx_operation_type (operation_type),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备份操作日志表';

-- ────────────────────────────────────────
-- 3. 创建备份配置表（单行配置模式）[V4]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS backup_config (
    id INT NOT NULL DEFAULT 1,
    auto_backup_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '自动备份开关',
    backup_cron VARCHAR(100) NOT NULL DEFAULT '0 0 2 * * ?' COMMENT 'cron表达式',
    backup_hour INT NOT NULL DEFAULT 2 COMMENT '备份小时(0-23)',
    backup_minute INT NOT NULL DEFAULT 0 COMMENT '备份分钟(0-59)',
    retention_days INT NOT NULL DEFAULT 30 COMMENT '保留天数',
    max_count INT NOT NULL DEFAULT 50 COMMENT '最大备份数量',
    incremental_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '增量备份开关',
    full_backup_interval_days INT NOT NULL DEFAULT 7 COMMENT '全量备份间隔天数',
    updated_by VARCHAR(50) DEFAULT NULL COMMENT '最后更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT chk_single_row CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备份配置表(单行)';

-- 插入默认配置行（INSERT IGNORE 防止重复执行报错）
INSERT IGNORE INTO backup_config (id, auto_backup_enabled, backup_cron, backup_hour, backup_minute,
    retention_days, max_count, incremental_enabled, full_backup_interval_days)
VALUES (1, 1, '0 0 2 * * ?', 2, 0, 30, 50, 0, 7);

-- ═══════════════════════════════════════════════════════════════
-- V5: 访问监控 + AI调用统计 + 角色扩展 + 演示账号
-- 整合自: 原 V5 (monitoring tables), V5 (OPS role), V5 (demo accounts)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 访问日志表（原始数据，保留7天）[V5]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS access_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT DEFAULT NULL COMMENT '用户ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '用户名',
    method VARCHAR(10) NOT NULL COMMENT 'HTTP方法',
    uri VARCHAR(500) NOT NULL COMMENT '请求URI',
    query_string VARCHAR(1000) DEFAULT NULL COMMENT '查询参数',
    status_code INT NOT NULL COMMENT 'HTTP状态码',
    response_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT '响应时间(毫秒)',
    ip_address VARCHAR(45) DEFAULT NULL COMMENT 'IP地址',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_created_time (created_time),
    INDEX idx_username (username),
    INDEX idx_uri (uri(191)),
    INDEX idx_status_code (status_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访问日志表';

-- ────────────────────────────────────────
-- 2. 每小时聚合统计表（保留90天）[V5]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS access_stats_hourly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_hour DATETIME NOT NULL COMMENT '统计小时(整点)',
    total_requests INT NOT NULL DEFAULT 0 COMMENT '总请求数',
    unique_users INT NOT NULL DEFAULT 0 COMMENT '独立用户数',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '平均响应时间(毫秒)',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '最大响应时间(毫秒)',
    error_count INT NOT NULL DEFAULT 0 COMMENT '错误数(4xx+5xx)',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_stat_hour (stat_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每小时访问统计';

-- ────────────────────────────────────────
-- 3. 每日聚合统计表（长期保留）[V5]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS access_stats_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL COMMENT '统计日期',
    total_requests INT NOT NULL DEFAULT 0 COMMENT '总请求数',
    unique_users INT NOT NULL DEFAULT 0 COMMENT '独立用户数',
    peak_concurrent INT NOT NULL DEFAULT 0 COMMENT '峰值并发数',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '平均响应时间(毫秒)',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '最大响应时间(毫秒)',
    error_count INT NOT NULL DEFAULT 0 COMMENT '错误数',
    top_pages TEXT DEFAULT NULL COMMENT '热门页面JSON',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日访问统计';

-- ────────────────────────────────────────
-- 4. AI/外部服务调用日志表（保留30天）[V5]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(50) NOT NULL COMMENT '服务名称: HIAGENT/OPENROUTER/TENCENT_TRANSLATE/TENCENT_ASR/BILIBILI',
    method_name VARCHAR(100) DEFAULT NULL COMMENT '调用方法名',
    success TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否成功',
    response_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT '响应时间(毫秒)',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    input_tokens INT DEFAULT NULL COMMENT '输入token数',
    output_tokens INT DEFAULT NULL COMMENT '输出token数',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    triggered_by VARCHAR(100) DEFAULT NULL COMMENT '触发用户',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_service_name (service_name),
    INDEX idx_created_time (created_time),
    INDEX idx_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI调用日志表';

-- ────────────────────────────────────────
-- 5. AI调用日统计表（长期保留）[V5]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ai_call_stats_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL COMMENT '统计日期',
    service_name VARCHAR(50) NOT NULL COMMENT '服务名称',
    total_calls INT NOT NULL DEFAULT 0 COMMENT '总调用次数',
    success_calls INT NOT NULL DEFAULT 0 COMMENT '成功次数',
    failed_calls INT NOT NULL DEFAULT 0 COMMENT '失败次数',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '平均响应时间',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '最大响应时间',
    total_input_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总输入token',
    total_output_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总输出token',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_date_service (stat_date, service_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI调用日统计';

-- ────────────────────────────────────────
-- 6. 用户角色扩展 — 新增 OPS 运维角色 [V5]
-- ────────────────────────────────────────
ALTER TABLE users MODIFY COLUMN role ENUM('USER','STUDENT','TEACHER','ADMIN','OPS') NOT NULL DEFAULT 'USER';

-- ────────────────────────────────────────
-- 7. 确保演示账号存在且可正常登录 [V5]
--    密码: admin123 (复用 admin 账号的 BCrypt hash)
-- ────────────────────────────────────────
-- 确保 teacher 账号存在
INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'teacher',
       (SELECT password FROM users WHERE username = 'admin' LIMIT 1),
       'TEACHER', '演示教师', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'teacher');

-- 确保 student 账号存在
INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'student',
       (SELECT password FROM users WHERE username = 'admin' LIMIT 1),
       'STUDENT', '演示学生', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'student');

-- 同步密码 + 确保 ACTIVE 状态 + 未被软删除
UPDATE users SET
    password = (SELECT pw FROM (SELECT password AS pw FROM users WHERE username = 'admin' LIMIT 1) tmp),
    status = 'ACTIVE',
    deleted = 0
WHERE username IN ('teacher', 'student');

-- ─────────────────────────────────────────────────────────────
-- V6: 系统配置表 + AI 提供商 + 多模型支持
-- 整合自: 原 V6 (system_config), 原 V7 (ai_provider), 原 V11 (app_id)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_group` VARCHAR(50) NOT NULL COMMENT '配置分组: ai_translate, ai_article, ai_chat',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `description` VARCHAR(255) COMMENT '配置说明（运维面板显示）',
    `is_secret` TINYINT DEFAULT 0 COMMENT '是否敏感（前端脱敏显示）',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updated_by` VARCHAR(50),
    UNIQUE KEY `uk_group_key` (`config_group`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 翻译服务默认配置
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('ai_translate', 'api_url', 'https://api.siliconflow.cn/v1/chat/completions', 'API 地址（OpenAI 兼容）', 0),
('ai_translate', 'api_key', '', 'API 密钥', 1),
('ai_translate', 'model', 'Qwen/Qwen2.5-7B-Instruct', '模型名称', 0),
('ai_translate', 'enabled', 'true', '是否启用', 0),
('ai_translate', 'timeout', '30000', '超时时间(ms)', 0);

-- V6: AI 提供商表（多模型加权路由）
-- app_id 字段已内联 [原 V11], qa_app_id 为扩展字段

CREATE TABLE IF NOT EXISTS `ai_provider` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `service` VARCHAR(50) NOT NULL COMMENT 'translate / article / chat',
    `name` VARCHAR(100) NOT NULL COMMENT 'display name',
    `api_url` VARCHAR(500) NOT NULL COMMENT 'provider endpoint',
    `api_key` TEXT NOT NULL COMMENT 'provider api key',
    `app_id` VARCHAR(200) NULL COMMENT 'App ID (HiAgent 等服务商必填)',
    `qa_app_id` VARCHAR(200) NULL COMMENT 'QA App ID（留空则与 app_id 共用）',
    `model` VARCHAR(200) NOT NULL COMMENT 'default model',
    `weight` INT NOT NULL DEFAULT 1 COMMENT 'weighted routing priority',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT 'whether provider is enabled',
    `timeout_ms` INT NOT NULL DEFAULT 30000 COMMENT 'request timeout in ms',
    `remark` VARCHAR(255) COMMENT 'operator note',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updated_by` VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI provider configuration';

INSERT IGNORE INTO `ai_provider`
    (`id`, `service`, `name`, `api_url`, `api_key`, `model`, `weight`, `enabled`, `timeout_ms`, `remark`)
SELECT
    1,
    'translate',
    'SiliconFlow (default)',
    COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='api_url'),
             'https://api.siliconflow.cn/v1/chat/completions'),
    COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='api_key'), ''),
    COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='model'),
             'Qwen/Qwen2.5-7B-Instruct'),
    1,
    1,
    COALESCE(CAST((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='timeout') AS UNSIGNED),
             30000),
    'migrated from system_config'
WHERE NOT EXISTS (SELECT 1 FROM ai_provider WHERE service = 'translate');

-- V4: 自动更新配置表 [原 V8]
CREATE TABLE IF NOT EXISTS update_config (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    cron_expr            VARCHAR(64) NOT NULL DEFAULT '0 0 3 * * ?' COMMENT 'Spring cron expression',
    enabled              TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether scheduled self-update is enabled',
    git_branch           VARCHAR(64) NOT NULL DEFAULT 'master' COMMENT 'tracked git branch',
    backup_before_update TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'whether to back up the database before update',
    last_run_at          DATETIME DEFAULT NULL COMMENT 'last execution time',
    last_run_status      VARCHAR(16) DEFAULT 'NEVER' COMMENT 'NEVER/RUNNING/SUCCESS/FAILED'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='self-update configuration';

INSERT IGNORE INTO update_config (id, cron_expr, enabled, git_branch, backup_before_update, last_run_status)
VALUES (1, '0 0 3 * * ?', 0, 'master', 1, 'NEVER');

-- V3: 媒体路径列 [原 V9]
DROP PROCEDURE IF EXISTS _add_col_media_paths;
DELIMITER //
CREATE PROCEDURE _add_col_media_paths()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='media' AND column_name='file_path') THEN
        ALTER TABLE media ADD COLUMN file_path VARCHAR(500) COMMENT 'stored media file path';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='media' AND column_name='thumbnail_path') THEN
        ALTER TABLE media ADD COLUMN thumbnail_path VARCHAR(500) COMMENT 'stored thumbnail file path';
    END IF;
END//
DELIMITER ;
CALL _add_col_media_paths();
DROP PROCEDURE IF EXISTS _add_col_media_paths;

-- ══════════════════════════════════════════════════════════════
-- V7: 文章收藏 + 功能开关 + 个性化开关
-- 整合自: 原 V12 (article_favorites + feature_toggle)
--         + 原 V13 (修复 system_config description 乱码)
--         + 原 V14 (user_tags + feature_override)
-- ══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS article_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    text_id BIGINT NOT NULL COMMENT '课文/文章ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_student_text (student_id, text_id),
    INDEX idx_student_id (student_id),
    INDEX idx_text_id (text_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章收藏表';

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'page_dashboard', 'true', '学生端-发现页', 0),
('feature_toggle', 'page_courses', 'false', '学生端-课程页（开发中）', 0),
('feature_toggle', 'page_classes', 'true', '学生端-班级/社区页', 0),
('feature_toggle', 'page_assignments', 'true', '学生端-任务页', 0),
('feature_toggle', 'page_articles', 'true', '学生端-文章页', 0),
('feature_toggle', 'page_vocabulary', 'true', '学生端-生词本页', 0),
('feature_toggle', 'page_my_study', 'true', '学生端-我的学习页', 0),
('feature_toggle', 'page_ai_video', 'false', '学生端-AI视频字幕页（未完善）', 0),
('feature_toggle', 'page_ai_qa', 'true', '学生端-AI问答页', 0),
('feature_toggle', 'page_user_texts', 'true', '学生端-我的上传页', 0);

-- ─────────────────────────────────────────────
-- V7: 修复 system_config description 乱码
-- （SET NAMES utf8mb4 已在文件头部声明，此处 UPDATE 可正确写入中文）
-- ─────────────────────────────────────────────
UPDATE `system_config` SET `description` = '学生端-发现页'              WHERE `config_key` = 'page_dashboard'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-课程页（开发中）'    WHERE `config_key` = 'page_courses'     AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-班级/社区页'         WHERE `config_key` = 'page_classes'     AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-任务页'              WHERE `config_key` = 'page_assignments' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-文章页'              WHERE `config_key` = 'page_articles'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-生词本页'            WHERE `config_key` = 'page_vocabulary' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-我的学习页'          WHERE `config_key` = 'page_my_study'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-AI视频字幕页（未完善）' WHERE `config_key` = 'page_ai_video'  AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-AI问答页'            WHERE `config_key` = 'page_ai_qa'      AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-我的上传页'          WHERE `config_key` = 'page_user_texts' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '是否使用 OpenRouter 作为 AI 服务商' WHERE `config_key` = 'use_openrouter'     AND `config_group` = 'ai_config';
UPDATE `system_config` SET `description` = 'OpenRouter API Key'                   WHERE `config_key` = 'openrouter_api_key' AND `config_group` = 'ai_config';
UPDATE `system_config` SET `description` = 'OpenRouter 模型 ID'                  WHERE `config_key` = 'openrouter_model'   AND `config_group` = 'ai_config';

-- ─────────────────────────────────────────────
-- V7: 个性化功能开关 - 用户标签表 & 功能覆盖规则表
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_tags (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    tag_name     VARCHAR(64) NOT NULL,
    created_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_user_tag (user_id, tag_name),
    INDEX idx_tag_name (tag_name),
    INDEX idx_user_id  (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS feature_override (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope        VARCHAR(16)  NOT NULL COMMENT 'tag 或 user',
    scope_value  VARCHAR(128) NOT NULL COMMENT 'tag名称 或 userId字符串',
    feature_key  VARCHAR(128) NOT NULL,
    enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    description  VARCHAR(255) DEFAULT NULL,
    updated_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_scope_key (scope, scope_value, feature_key),
    INDEX idx_scope_value (scope, scope_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════
-- V8: 统一身份认证 (SSO) 集成
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. SSO 用户绑定表
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sso_user_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '本地用户ID',
    sso_provider VARCHAR(50) NOT NULL COMMENT 'SSO提供商: CAS, OAUTH2',
    sso_user_id VARCHAR(255) NOT NULL COMMENT 'SSO用户唯一标识',
    sso_username VARCHAR(255) NOT NULL COMMENT 'SSO用户名',
    sso_email VARCHAR(255) COMMENT 'SSO邮箱',
    sso_real_name VARCHAR(255) COMMENT 'SSO真实姓名',
    sso_member_of TEXT COMMENT 'SSO组织单位 (JSON数组)',
    auto_approved TINYINT(1) DEFAULT 0 COMMENT '是否自动审批: 0=待审批, 1=已审批',
    last_login_time DATETIME COMMENT '最后登录时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除: 0=正常, 1=已删除',
    UNIQUE KEY uk_sso_provider_user (sso_provider, sso_user_id, deleted),
    INDEX idx_user_id (user_id),
    INDEX idx_sso_username (sso_username),
    INDEX idx_auto_approved (auto_approved),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO用户绑定表';

-- ────────────────────────────────────────
-- 2. SSO 登录日志表
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sso_login_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sso_provider VARCHAR(50) NOT NULL COMMENT 'SSO提供商',
    sso_user_id VARCHAR(255) NOT NULL COMMENT 'SSO用户ID',
    sso_username VARCHAR(255) NOT NULL COMMENT 'SSO用户名',
    login_result VARCHAR(50) NOT NULL COMMENT '登录结果: SUCCESS, PENDING_APPROVAL, FAILED',
    failure_reason VARCHAR(500) COMMENT '失败原因',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    user_agent VARCHAR(500) COMMENT '用户代理',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_sso_user (sso_provider, sso_user_id),
    INDEX idx_login_time (created_time),
    INDEX idx_login_result (login_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO登录日志';

-- ────────────────────────────────────────
-- 3. SSO 配置表
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sso_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    config_type VARCHAR(50) NOT NULL COMMENT '配置类型: SYSTEM, CAS, OAUTH2, ROLE_MAPPING',
    description VARCHAR(500) COMMENT '配置描述',
    is_encrypted TINYINT(1) DEFAULT 0 COMMENT '是否加密存储',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_config_key (config_key),
    INDEX idx_config_type (config_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO配置表';

-- ────────────────────────────────────────
-- 4. 扩展 users 表字段（幂等）
-- ────────────────────────────────────────
-- 检查并添加 sso_bound 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sso_bound');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN sso_bound TINYINT(1) DEFAULT 0 COMMENT ''是否通过SSO绑定: 0=否, 1=是''',
    'SELECT ''Column sso_bound already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 sso_provider 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sso_provider');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN sso_provider VARCHAR(50) COMMENT ''SSO提供商''',
    'SELECT ''Column sso_provider already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 sso_subject 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sso_subject');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN sso_subject VARCHAR(255) COMMENT ''SSO唯一标识''',
    'SELECT ''Column sso_subject already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 sso_bound 索引
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_sso_bound');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE users ADD INDEX idx_sso_bound (sso_bound)',
    'SELECT ''Index idx_sso_bound already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 sso_subject 复合索引
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_sso_subject');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE users ADD INDEX idx_sso_subject (sso_provider, sso_subject)',
    'SELECT ''Index idx_sso_subject already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ────────────────────────────────────────
-- V9: 学校数据同步表结构 (原 V16)
-- ────────────────────────────────────────

-- 1.1 行政班表
CREATE TABLE IF NOT EXISTS administrative_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    class_code VARCHAR(50) NOT NULL UNIQUE,
    class_name VARCHAR(100) NOT NULL,
    class_short_name VARCHAR(50),
    college_code VARCHAR(50) NOT NULL,
    college_name VARCHAR(100) NOT NULL,
    major_code VARCHAR(50) NOT NULL,
    major_name VARCHAR(100) NOT NULL,
    grade VARCHAR(10) NOT NULL,
    class_type VARCHAR(20),
    head_teacher_code VARCHAR(50),
    head_teacher_name VARCHAR(50),
    student_count INT DEFAULT 0,
    male_count INT DEFAULT 0,
    female_count INT DEFAULT 0,
    class_status VARCHAR(20) NOT NULL,
    establish_date DATE,
    graduate_date DATE,
    remark VARCHAR(500),
    sync_time DATETIME NOT NULL,
    sync_source VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_college (college_code),
    INDEX idx_major (major_code),
    INDEX idx_grade (grade),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 1.2 课程表
CREATE TABLE IF NOT EXISTS course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_code VARCHAR(50) NOT NULL UNIQUE,
    course_name VARCHAR(200) NOT NULL,
    course_name_en VARCHAR(200),
    course_short_name VARCHAR(100),
    course_nature VARCHAR(50) NOT NULL,
    course_category VARCHAR(50) NOT NULL,
    credits DECIMAL(3,1) NOT NULL,
    total_hours INT NOT NULL,
    theory_hours INT,
    experiment_hours INT,
    practice_hours INT,
    computer_hours INT,
    college_code VARCHAR(50) NOT NULL,
    college_name VARCHAR(100) NOT NULL,
    department_code VARCHAR(50),
    department_name VARCHAR(100),
    leader_code VARCHAR(50),
    leader_name VARCHAR(50),
    assessment_method VARCHAR(50) NOT NULL,
    is_bilingual VARCHAR(10),
    is_online VARCHAR(10),
    applicable_majors VARCHAR(500),
    prerequisite_codes VARCHAR(500),
    prerequisite_names VARCHAR(500),
    course_intro TEXT,
    teaching_objectives TEXT,
    course_status VARCHAR(20) NOT NULL,
    sync_time DATETIME NOT NULL,
    sync_source VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_college (college_code),
    INDEX idx_nature (course_nature),
    INDEX idx_category (course_category),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2.1 教学班表
CREATE TABLE IF NOT EXISTS teaching_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    class_id VARCHAR(50) NOT NULL UNIQUE,
    class_name VARCHAR(100),
    course_code VARCHAR(50) NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    teacher_id VARCHAR(50) NOT NULL,
    teacher_name VARCHAR(50) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    semester INT NOT NULL,
    capacity INT NOT NULL,
    enrolled_count INT DEFAULT 0,
    class_status VARCHAR(20) NOT NULL,
    sync_time DATETIME NOT NULL,
    sync_source VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (course_code) REFERENCES course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_course (course_code),
    INDEX idx_teacher (teacher_id),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2.2 课程安排表
CREATE TABLE IF NOT EXISTS course_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    class_id VARCHAR(50) NOT NULL,
    day_of_week INT NOT NULL,
    period VARCHAR(20) NOT NULL,
    week_range VARCHAR(50) NOT NULL,
    class_location VARCHAR(100) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    semester INT NOT NULL,
    sync_time DATETIME NOT NULL,
    sync_source VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_class_id (class_id),
    INDEX idx_day_period (day_of_week, period),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3.1 选课记录表
CREATE TABLE IF NOT EXISTS course_selection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    selection_id VARCHAR(50) NOT NULL UNIQUE,
    student_id VARCHAR(50) NOT NULL,
    student_name VARCHAR(100) NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    class_id VARCHAR(50) NOT NULL,
    teacher_id VARCHAR(50) NOT NULL,
    teacher_name VARCHAR(50) NOT NULL,
    credits DECIMAL(3,1) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    semester INT NOT NULL,
    class_time VARCHAR(100),
    class_location VARCHAR(100),
    selection_time DATETIME NOT NULL,
    selection_status VARCHAR(20) NOT NULL,
    regular_grade DECIMAL(5,2),
    final_grade DECIMAL(5,2),
    total_grade DECIMAL(5,2),
    grade_point DECIMAL(3,2),
    attendance_count INT DEFAULT 0,
    absence_count INT DEFAULT 0,
    grade_remark VARCHAR(500),
    sync_time DATETIME NOT NULL,
    sync_source VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (course_code) REFERENCES course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_student (student_id),
    INDEX idx_course (course_code),
    INDEX idx_class (class_id),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_selection_status (selection_status),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3.2 等级考试成绩表
CREATE TABLE IF NOT EXISTS level_exam_scores (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id VARCHAR(50) NOT NULL,
    student_name VARCHAR(100) NOT NULL,
    exam_type VARCHAR(50) NOT NULL,
    exam_name VARCHAR(100) NOT NULL,
    admission_ticket_no VARCHAR(30),
    exam_date DATE NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    grade VARCHAR(20),
    is_passed BOOLEAN NOT NULL,
    passing_score DECIMAL(5,2),
    listening_score DECIMAL(5,2),
    reading_score DECIMAL(5,2),
    writing_score DECIMAL(5,2),
    translation_score DECIMAL(5,2),
    certificate_no VARCHAR(50),
    certificate_date DATE,
    exam_location VARCHAR(100),
    exam_room_no VARCHAR(20),
    seat_no VARCHAR(10),
    registration_date DATE,
    score_release_date DATE,
    data_source VARCHAR(50),
    remark VARCHAR(500),
    sync_time DATETIME NOT NULL,
    sync_source VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_student_exam (student_id, exam_type, exam_date),
    INDEX idx_student_id (student_id),
    INDEX idx_exam_type (exam_type),
    INDEX idx_exam_date (exam_date),
    INDEX idx_is_passed (is_passed),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 教学计划主表
CREATE TABLE IF NOT EXISTS teaching_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id VARCHAR(30) NOT NULL UNIQUE,
    plan_name VARCHAR(200) NOT NULL,
    major_code VARCHAR(20) NOT NULL,
    major_name VARCHAR(100) NOT NULL,
    grade VARCHAR(10) NOT NULL,
    schooling_length INT NOT NULL,
    degree_type VARCHAR(20) NOT NULL,
    education_level VARCHAR(20) NOT NULL,
    college_code VARCHAR(20) NOT NULL,
    college_name VARCHAR(100) NOT NULL,
    total_credits DECIMAL(5,2) NOT NULL,
    required_credits DECIMAL(5,2) NOT NULL,
    elective_credits DECIMAL(5,2) NOT NULL,
    practice_credits DECIMAL(5,2),
    general_education_credits DECIMAL(5,2),
    major_core_credits DECIMAL(5,2),
    major_elective_credits DECIMAL(5,2),
    training_objective TEXT,
    training_requirements TEXT,
    main_disciplines VARCHAR(200),
    core_courses TEXT,
    plan_status VARCHAR(20) NOT NULL,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    creator VARCHAR(50),
    reviewer VARCHAR(50),
    review_date DATE,
    version VARCHAR(20),
    revision_notes TEXT,
    attachment_path VARCHAR(500),
    sync_time DATETIME NOT NULL,
    sync_source VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_major_grade (major_code, grade),
    INDEX idx_plan_status (plan_status),
    INDEX idx_effective_date (effective_date),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4.1 教学计划课程明细表
CREATE TABLE IF NOT EXISTS teaching_plan_courses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    detail_id VARCHAR(30) NOT NULL UNIQUE,
    plan_id VARCHAR(30) NOT NULL,
    course_code VARCHAR(20) NOT NULL,
    course_name VARCHAR(100) NOT NULL,
    course_type VARCHAR(20) NOT NULL,
    course_category VARCHAR(50),
    credits DECIMAL(4,2) NOT NULL,
    hours INT NOT NULL,
    theory_hours INT,
    lab_hours INT,
    semester INT NOT NULL,
    assessment_method VARCHAR(20),
    prerequisite_courses VARCHAR(200),
    sort_order INT,
    sync_time DATETIME NOT NULL,
    sync_source VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_plan_id (plan_id),
    INDEX idx_course_code (course_code),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ────────────────────────────────────────
-- V10: 用户表 nickname + avatar 列修正
-- ────────────────────────────────────────

-- 检查并添加 nickname 列
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'nickname');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN nickname VARCHAR(50) DEFAULT NULL COMMENT ''昵称'' AFTER role',
    'SELECT ''Column nickname already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并重命名 avatar_url → avatar（仅在 avatar 不存在且 avatar_url 存在时执行）
SET @avatar_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'avatar');
SET @avatar_url_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'avatar_url');
SET @sql = IF(@avatar_exists = 0 AND @avatar_url_exists > 0,
    'ALTER TABLE users RENAME COLUMN avatar_url TO avatar',
    'SELECT ''Column avatar already exists or avatar_url does not exist'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ────────────────────────────────────────
-- V11: texts 推荐字段 + vocabulary_books SM-2 算法字段
-- ────────────────────────────────────────

-- texts: cover_url
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'cover_url');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE texts ADD COLUMN cover_url VARCHAR(255) DEFAULT NULL COMMENT ''封面图URL'' AFTER chinese_parallel_markdown_content',
    'SELECT ''Column cover_url already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- texts: is_featured
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'is_featured');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE texts ADD COLUMN is_featured TINYINT(1) DEFAULT 0 COMMENT ''是否推荐''',
    'SELECT ''Column is_featured already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- texts: featured_priority
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'featured_priority');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE texts ADD COLUMN featured_priority INT DEFAULT 0 COMMENT ''推荐优先级''',
    'SELECT ''Column featured_priority already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- texts: idx_is_featured
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'texts' AND INDEX_NAME = 'idx_is_featured');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE texts ADD INDEX idx_is_featured (is_featured)',
    'SELECT ''Index idx_is_featured already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- vocabulary_books: repetition
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'vocabulary_books' AND COLUMN_NAME = 'repetition');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE vocabulary_books ADD COLUMN repetition INT DEFAULT 0 COMMENT ''SM2重复次数'' AFTER mastery_level',
    'SELECT ''Column repetition already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- vocabulary_books: interval_days
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'vocabulary_books' AND COLUMN_NAME = 'interval_days');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE vocabulary_books ADD COLUMN interval_days INT DEFAULT 1 COMMENT ''SM2间隔天数'' AFTER repetition',
    'SELECT ''Column interval_days already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- vocabulary_books: easiness_factor
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'vocabulary_books' AND COLUMN_NAME = 'easiness_factor');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE vocabulary_books ADD COLUMN easiness_factor DOUBLE DEFAULT 2.5 COMMENT ''SM2难度因子'' AFTER interval_days',
    'SELECT ''Column easiness_factor already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- vocabulary_books: next_review_time（可能已存在于基础表，幂等处理）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning' AND TABLE_NAME = 'vocabulary_books' AND COLUMN_NAME = 'next_review_time');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE vocabulary_books ADD COLUMN next_review_time DATETIME DEFAULT NULL COMMENT ''下次复习时间'' AFTER easiness_factor',
    'SELECT ''Column next_review_time already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ────────────────────────────────────────
-- 4.5 创建 ecdict_words 表（如果不存在）
-- 06-import-ecdict.sh 需要 LOAD DATA INTO 此表，
-- 但全量备份中才包含 CREATE TABLE，全新部署时表不存在导致导入失败
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `ecdict_words` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `word` VARCHAR(255) NOT NULL COMMENT '单词',
    `phonetic` VARCHAR(255) DEFAULT NULL COMMENT '音标',
    `definition` TEXT COMMENT '英文定义',
    `translation` TEXT COMMENT '中文翻译',
    `pos` VARCHAR(100) DEFAULT NULL COMMENT '词性',
    `collins` VARCHAR(50) DEFAULT NULL COMMENT '柯林斯星级',
    `oxford` VARCHAR(50) DEFAULT NULL COMMENT '牛津3000核心词汇标记',
    `tag` VARCHAR(255) DEFAULT NULL COMMENT '标签',
    `bnc` VARCHAR(50) DEFAULT NULL COMMENT 'BNC词频',
    `frq` VARCHAR(50) DEFAULT NULL COMMENT '当代语料库词频',
    `exchange` TEXT COMMENT '时态复数等变换',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`),
    KEY `idx_phonetic` (`phonetic`),
    KEY `idx_pos` (`pos`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ECDICT词汇表';

-- ────────────────────────────────────────
-- 5. 插入默认 SSO 配置
-- ────────────────────────────────────────
INSERT IGNORE INTO sso_config (config_key, config_value, config_type, description) VALUES
('sso.enabled', 'false', 'SYSTEM', 'SSO功能总开关'),
('sso.protocol', 'CAS', 'SYSTEM', 'SSO协议类型: CAS 或 OAUTH2'),
('sso.auto_approval.enabled', 'true', 'SYSTEM', '是否启用自动审批'),
('sso.role_mapping.ou=管理员', 'ADMIN', 'ROLE_MAPPING', '管理员角色映射'),
('sso.role_mapping.ou=运维', 'OPS', 'ROLE_MAPPING', '运维角色映射'),
('sso.role_mapping.ou=教师', 'TEACHER', 'ROLE_MAPPING', '教师角色映射'),
('sso.role_mapping.ou=学生', 'STUDENT', 'ROLE_MAPPING', '学生角色映射');

-- ────────────────────────────────────────
-- V12: 清理无效角色映射规则 + 修复误映射用户
-- 整合自: V12__fix_role_mapping_cleanup.sql
--
-- 问题：sso_config 表中可能存在 config_key='sso.role_mapping.ou' 的脏规则，
-- strip 前缀后 key='ou'，旧版 RoleMappingService 使用 contains() 匹配，
-- 'ou' 匹配所有 LDAP DN（都包含 'ou'），导致所有用户被映射为同一角色。
-- 新版改用 DN 组件精确匹配，同时此迁移清理数据库中的脏规则。
-- ────────────────────────────────────────

-- 1. 删除无效的短 key 规则（key strip 后不含 '='，如 'ou'、'dc' 等）
DELETE FROM sso_config
WHERE config_type = 'ROLE_MAPPING'
  AND REPLACE(config_key, 'sso.role_mapping.', '') NOT LIKE '%=%';

-- 2. 修复被误映射的用户角色
--    旧代码因 'ou' 规则把所有 CAS 用户映射为 TEACHER
--    修复策略：将被 SSO 错误映射为 TEACHER 但实际是学生的用户恢复为 STUDENT
UPDATE users u
INNER JOIN sso_user_bindings b ON u.id = b.user_id AND b.deleted = 0
SET u.role = 'STUDENT', u.updated_time = NOW()
WHERE u.role = 'TEACHER'
  AND u.sso_bound = 1
  AND u.deleted = 0
  AND b.auto_approved = 1
  AND b.sso_member_of IS NOT NULL
  AND b.sso_member_of NOT LIKE '%ou=教师%'
  AND b.sso_member_of NOT LIKE '%ou=teacher%';

-- ════════════════════════════════════════════════════════════════
