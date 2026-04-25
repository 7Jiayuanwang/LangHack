-- ══════════════════════════════════════════════════════════════
-- LangHack 合并迁移脚本（MySQL 8.0 兼容）
-- 将 Flyway V0 ~ V26 的所有迁移合并为单个幂等脚本
--
-- 对应 Flyway 迁移文件（整合后 - 2026-04-21 V12 瘦身 + V20 合入 + V17-V19 重编号）:
--   V0__base_tables.sql                     (基础表)
--   V1__scoring_and_assignments.sql         (评分+作业)
--   V2__text_and_learning.sql               (文本+学习)
--   V3__media_and_annotations.sql           (媒体+批注)
--   V4__backup_and_update_system.sql        (备份+更新)
--   V5__monitoring_and_access.sql           (监控+访问)
--   V6__system_config_and_ai_providers.sql  (系统配置+AI)
--   V7__feature_toggles.sql                 (功能开关)
--   V8__sso_integration.sql                 (CAS SSO)
--   V9__school_data_sync.sql                (学校数据基础表)
--   V10__user_and_text_updates.sql          (合并: 旧 V10 + V11)
--   V11__role_mapping_cleanup.sql           (合并: 旧 V12 重命名)
--   V12__school_data_integration.sql        (合并: 旧 V12.1/V13/V14/V16/V18/V19/V20 + V20-align
--                                              2026-04-21 按 CDSP 官方 PDF 重建 9 数据源字段映射)
--   V13__access_log_covering_index.sql     (监控热点页查询索引优化)
--   V14__expand_feature_toggles.sql         (学生页全覆盖 + admin.* 模块 toggle)
--   V15__ImportECDICTData.java              (JDBC 迁移 - 导入 ECDICT 词典数据)
--   V16__ai_qa_recovery_and_hiagent_config.sql (AI 问答开关恢复 + HiAgent 凭证迁 DB, 旧 V17)
--   V17__add_page_word_recite_toggle.sql    (学生端-背单词页 feature_toggle seed, 旧 V18)
--   V18__ai_provider_protocol.sql           (AI 服务商协议类型字段, 旧 V19)
--   V21__add_sso_binding_admin_remark.sql  (SSO 绑定表添加管理员备注字段)
--   V22__chain_fetch_schema.sql            (按学院链式拉取: school_student / school_teacher / teacher_student_binding)
--   V23__sync_log_pagination_summary.sql   (sync_logs 分页扩展字段)
--   V24__similarity_matrix.sql              (相似度矩阵表 Item-CF / User-CF)
--   V25__add_similar_id_indexes.sql        (相似度矩阵补 similar_*_id 索引)
--   V26__optimize_interaction_view.sql      (v_user_text_interaction_scores 视图)
--
-- 注意：旧 V20__align_field_mappings_with_pdf.sql 已于 2026-04-21 合并进 V12，
--       V12 Part 3.1/3.2/3.3 的 camelCase/snake_case 冗余 field_mappings 已删除
--       （FieldMappingServiceImpl 自动将 target_field 转 camelCase, 两套等价重复）。
--       V19 列名修正 — 使用 `converter`/`remark`（原 V19 误写为
--       `converter_type`/`description`/`created_by` 导致 INSERT IGNORE 静默失败）
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
-- V14: 扩展 feature_toggle 覆盖学生端全量 + 管理员分级
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_scores',            'true',  '学生端-等级考试成绩页',         0),
  ('feature_toggle', 'page_course_selections', 'true',  '学生端-我的选课页',             0),
  ('feature_toggle', 'page_text_learning',     'true',  '学生端-课文学习子页',           0),
  ('feature_toggle', 'page_schedule',          'true',  '学生端-我的课表页',             0),
  ('feature_toggle', 'admin.dashboard',    'true', '管理员-仪表盘',     0),
  ('feature_toggle', 'admin.courses',      'true', '管理员-课程管理',   0),
  ('feature_toggle', 'admin.classes',      'true', '管理员-班级管理',   0),
  ('feature_toggle', 'admin.users',        'true', '管理员-用户管理',   0),
  ('feature_toggle', 'admin.units',        'true', '管理员-单元管理',   0),
  ('feature_toggle', 'admin.words',        'true', '管理员-单词管理',   0),
  ('feature_toggle', 'admin.texts',        'true', '管理员-课文管理',   0),
  ('feature_toggle', 'admin.assignments',  'true', '管理员-作业管理',   0),
  ('feature_toggle', 'admin.sso_approval', 'true', '管理员-SSO 审批',   0);

-- ─────────────────────────────────────────────
-- V18: 补齐学生端"背单词"页 toggle
-- Layout.vue 底部导航 + Dashboard.vue 快捷入口均引用此键
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES ('feature_toggle', 'page_word_recite', 'true', '学生端-背单词页', 0);

UPDATE `system_config`
SET `description` = '学生端-背单词页'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` = 'page_word_recite'
  AND (`description` IS NULL OR `description` = '');

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
CREATE TABLE IF NOT EXISTS school_course (
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
    FOREIGN KEY (course_code) REFERENCES school_course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
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
    FOREIGN KEY (course_code) REFERENCES school_course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
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
    student_name VARCHAR(100) COMMENT '学生姓名（CDSP 不提供，可为空）',
    exam_type VARCHAR(50) COMMENT '考试类型（CDSP 不提供，从 examName 推断）',
    exam_name VARCHAR(100) NOT NULL,
    admission_ticket_no VARCHAR(30),
    exam_date DATE NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    grade VARCHAR(20),
    is_passed BOOLEAN COMMENT '是否通过（CDSP 不提供，从 score 推断）',
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
-- V9 补充: sync_logs 和 field_mappings 建表
-- 04 文件中 V9 仅创建了学校数据同步业务表，遗漏了辅助表
-- ════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS sync_logs (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sync_type` VARCHAR(50) NOT NULL COMMENT '同步类型: FULL/INCREMENTAL',
    `data_source` VARCHAR(50) NOT NULL COMMENT '数据源',
    `sync_method` VARCHAR(50) NOT NULL COMMENT '同步方法: REPLACE_ALL/UPSERT',
    `status` VARCHAR(50) NOT NULL COMMENT '状态: RUNNING/SUCCESS/FAILED',
    `total_count` INT DEFAULT 0 COMMENT '总记录数',
    `success_count` INT DEFAULT 0 COMMENT '成功数',
    `fail_count` INT DEFAULT 0 COMMENT '失败数',
    `skip_count` INT DEFAULT 0 COMMENT '跳过数',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `duration_ms` BIGINT COMMENT '耗时（毫秒）',
    `error_message` TEXT COMMENT '错误信息',
    `trigger_type` VARCHAR(50) COMMENT '触发类型: MANUAL/SCHEDULED',
    `operator` VARCHAR(50) COMMENT '操作人',
    `pagination_complete` TINYINT(1) NULL COMMENT '分页是否完整结束',
    `pagination_stop_reason` VARCHAR(64) NULL COMMENT '分页停止原因',
    `pagination_pages_fetched` INT NULL COMMENT '已拉取页数',
    `pagination_last_skip` INT NULL COMMENT '最后一次skip值',
    `pagination_page_size` INT NULL COMMENT '分页pageSize',
    `pagination_guard_hit` TINYINT(1) NULL COMMENT '是否触发分页防护',
    `fetched_row_count` INT NULL COMMENT '上游已拉取条数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX idx_data_source (`data_source`),
    INDEX idx_status (`status`),
    INDEX idx_sync_type (`sync_type`),
    INDEX idx_start_time (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步日志表';

CREATE TABLE IF NOT EXISTS field_mappings (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `data_source` VARCHAR(50) NOT NULL COMMENT '数据源标识',
    `source_field` VARCHAR(100) NOT NULL COMMENT '源字段名（API返回的JSON键名）',
    `target_field` VARCHAR(100) NOT NULL COMMENT '目标字段名（Java实体属性名）',
    `source_type` VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '源字段类型',
    `target_type` VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '目标字段类型',
    `converter` VARCHAR(50) NOT NULL DEFAULT 'NONE' COMMENT '转换器: NONE/SPLIT/BOOLEAN/DATE',
    `converter_config` JSON COMMENT '转换器配置（JSON格式）',
    `default_value` VARCHAR(500) COMMENT '默认值（源数据为空时使用）',
    `is_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否必填',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序顺序',
    `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `remark` VARCHAR(500) COMMENT '字段映射说明',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX idx_data_source (`data_source`),
    INDEX idx_target_field (`target_field`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段映射配置表';

-- （field_mappings seed 见文件末尾 V12 Part 3，按 CDSP 官方 PDF 对齐）

-- ════════════════════════════════════════════════════════════════
-- V13: 学校数据接入配置种子数据（system_config school_data 组）
-- 整合自: V13__school_data_system_config.sql
-- ════════════════════════════════════════════════════════════════

-- 数据源配置
-- NOTE: api_app_id / api_app_secret 在 V20 section 添加（appId + appSecret 签名认证）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'source', 'api', '数据源类型: api 或 etl', 0),
('school_data', 'api_base_url', 'https://api.imau.edu.cn', 'API基础URL', 0),
('school_data', 'api_connect_timeout', '5000', 'API连接超时(ms)', 0),
('school_data', 'api_read_timeout', '30000', 'API读取超时(ms)', 0),
('school_data', 'api_retry_attempts', '3', 'API重试次数', 0),
('school_data', 'etl_jdbc_url', '', 'ETL JDBC连接URL', 0),
('school_data', 'etl_username', '', 'ETL数据库用户名', 0),
('school_data', 'etl_password', '', 'ETL数据库密码', 1),
('school_data', 'etl_connection_timeout', '30000', 'ETL连接超时(ms)', 0),
('school_data', 'etl_query_timeout', '60000', 'ETL查询超时(ms)', 0);

-- 同步调度配置
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'sync_enabled', 'false', '是否启用同步调度', 0),
('school_data', 'sync_full_cron', '0 0 2 * * ?', '全量同步cron', 0),
('school_data', 'sync_incremental_cron', '0 0 * * * ?', '增量同步cron', 0),
('school_data', 'sync_validation_cron', '0 0 4 * * ?', '数据校验cron', 0),
('school_data', 'sync_batch_size', '500', '批量大小', 0),
('school_data', 'sync_timeout_minutes', '60', '同步超时(分钟)', 0);

-- ════════════════════════════════════════════════════════════════
-- V14: 学生课表表结构
-- 整合自: V14__student_schedule_and_teacher_courses_mapping.sql (表结构)
--        field_mappings 见文件末尾 V12 Part 3
-- ════════════════════════════════════════════════════════════════

-- 1. 学生课表 (student_schedule) 表结构 — 接口: /api/xskb
CREATE TABLE IF NOT EXISTS student_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id VARCHAR(50) NOT NULL,
    student_name VARCHAR(100) NOT NULL,
    class_id VARCHAR(50) NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    teacher_id VARCHAR(50) NOT NULL,
    teacher_name VARCHAR(50) NOT NULL,
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

    INDEX idx_student (student_id),
    INDEX idx_class_id (class_id),
    INDEX idx_course (course_code),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Student schedule info';

-- （teacher_courses / student_schedule 字段映射见文件末尾 V12 Part 3）

-- ════════════════════════════════════════════════════════════════
-- V16: 重命名 course → school_course (存量数据库升级)
-- 新环境初始化时上方已直接创建 school_course 表，此段仅处理已有数据库
-- ════════════════════════════════════════════════════════════════
SET @course_exists = (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course');
SET @school_course_exists = (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'school_course');
-- 仅当 course 存在且 school_course 不存在时执行重命名
SET @sql = IF(@course_exists = 1 AND @school_course_exists = 0, 'RENAME TABLE `course` TO `school_course`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ════════════════════════════════════════════════════════════════
-- V12 (原 V18): 学校数据功能开关
-- 整合自: V18__school_data_feature_toggle.sql → 现属 V12 合并文件
-- ════════════════════════════════════════════════════════════════

-- 运维端：学校数据浏览和配置功能开关（默认关闭，仅运维和管理员可见）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES
('feature_toggle', 'ops.school_data_browser', 'false', '运维端学校数据浏览功能（行政班、课程、教学班等）', 0),
('feature_toggle', 'ops.school_data_config', 'false', '运维端学校数据源配置功能（API/ETL切换、连接测试）', 0);

-- 学生端：课表功能开关（默认关闭，需要学校数据源配置完成后启用）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES
('feature_toggle', 'page_schedule', 'false', '学生端课表查看功能（从教务系统同步课表数据）', 0);

-- ════════════════════════════════════════════════════════════════
-- V12 (原 V20): 学校数据 API 认证方式迁移 — token → appId + appSecret 签名
-- 整合自: V20__school_data_api_auth_appid.sql
--
-- 背景: 官方接口文档要求 appId + appSecret + timestamp 签名认证
--       sign = Base64( MD5( appId + appSecret + timestamp ) )
--       请求头: appId / timestamp / sign
-- ════════════════════════════════════════════════════════════════

-- 新增 appId / appSecret 配置项（签名认证）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'api_app_id', '', 'API 应用 ID（由学校数据平台分配，用于签名认证）', 1),
('school_data', 'api_app_secret', '', 'API 应用 Secret（由学校数据平台分配，与 appId + timestamp 组合生成签名）', 1);

-- 清理旧部署遗留的 api_token（Bearer Token 认证已废弃；全新部署不会有此记录）
DELETE FROM `system_config`
WHERE `config_group` = 'school_data' AND `config_key` = 'api_token';

-- ════════════════════════════════════════════════════════════════
-- V13: access_log 覆盖索引 — 加速 top_pages 查询
-- 整合自: V13__access_log_covering_index.sql
--
-- 背景: MonitoringController.getTopPages → AccessLogMapper.topPagesByDateRange
--       SQL: SELECT uri, COUNT(*) FROM access_log WHERE created_time BETWEEN ?
--            GROUP BY uri ORDER BY count DESC LIMIT ?
--       原表仅 (created_time) 索引 → GROUP BY uri 走文件排序，大日志量下延迟显著。
-- 方案: 增加 (created_time, uri(191)) 复合索引 → 走索引范围扫描 + 索引内 GROUP BY。
-- ════════════════════════════════════════════════════════════════

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'access_log'
      AND INDEX_NAME = 'idx_created_time_uri');

SET @stmt = IF(@idx_exists > 0,
    'SELECT 1',
    'ALTER TABLE access_log ADD INDEX idx_created_time_uri (created_time, uri(191))'
);
PREPARE alterIfNotExists FROM @stmt;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ════════════════════════════════════════════════════════════════
-- V16 (原 V17): AI 问答恢复 + HiAgent 凭证前端可配置化
-- 整合自: V17__ai_qa_recovery_and_hiagent_config.sql → 现 V16
--
-- 背景:
--   1. 运维操作 / 缓存漂移 / 历史覆盖可能导致 page_ai_qa 被置为 'false'
--      或 feature_override 里有对 page_ai_qa 的禁用规则 → 路由守卫拦截
--   2. HiAgent 改写/问答凭证原本只通过 application.yml + 环境变量注入，
--      把 4+1 个 HiAgent 凭证迁到 system_config.ai_config 使前端可配
-- ════════════════════════════════════════════════════════════════

-- 恢复 page_ai_qa 开关为 true
UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key`   = 'page_ai_qa';

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES ('feature_toggle', 'page_ai_qa', 'true', '学生端-AI问答页', 0);

-- 清除所有对 page_ai_qa 的禁用覆盖
DELETE FROM `feature_override` WHERE `feature_key` = 'page_ai_qa';

-- HiAgent 凭证 seed 到 ai_config group
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('ai_config', 'hiagent_api_url',    '', 'HiAgent API Base URL（留空则使用环境变量 HIAGENT_API_URL）',         0),
  ('ai_config', 'hiagent_api_key',    '', 'HiAgent 改写应用 API Key（留空则使用环境变量 HIAGENT_API_KEY）',      1),
  ('ai_config', 'hiagent_app_id',     '', 'HiAgent 改写应用 App ID（留空则使用环境变量 HIAGENT_APP_ID）',        0),
  ('ai_config', 'hiagent_qa_api_key', '', 'HiAgent 问答应用 API Key（必须与改写凭证不同；留空则用 HIAGENT_QA_API_KEY）', 1),
  ('ai_config', 'hiagent_qa_app_id',  '', 'HiAgent 问答应用 App ID（必须与改写凭证不同；留空则用 HIAGENT_QA_APP_ID）',  0);

-- ════════════════════════════════════════════════════════════════
-- V18 (原 V19): AI 服务商协议类型字段
-- ════════════════════════════════════════════════════════════════

-- 使用存储过程检查并添加列（兼容 MySQL 5.7+）
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS AddProtocolColumn()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'ai_provider'
        AND COLUMN_NAME = 'protocol'
    ) THEN
        ALTER TABLE `ai_provider`
            ADD COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible'
            COMMENT '协议类型: openai_compatible | hiagent'
            AFTER `service`;
    END IF;
END //
DELIMITER ;
CALL AddProtocolColumn();
DROP PROCEDURE IF EXISTS AddProtocolColumn;

-- ════════════════════════════════════════════════════════════════
-- V12 Part 3 (原 V20-align): 按 CDSP 官方 PDF 原文对齐 field_mappings
-- 整合自: V20__align_field_mappings_with_pdf.sql → 现属 V12 合并文件
--
-- 背景:
--   V12.1 seed 的 source_field 与 9 份官方 PDF 实际返回字段命名不匹配
--   （不仅大小写差异，还有字段完全不同，如 V12 'xzbmc' vs PDF 'BJMC'、
--   V12 'yxbh' vs PDF 'SSXYBM'）。
--   FieldMappingServiceImpl.applyMapping 的 HashMap 查表是大小写敏感的，
--   API 返回的 UPPERCASE 字段会直接 miss 被静默跳过。
--
-- 配套代码修改:
--   (a) FieldMappingServiceImpl.applyMapping 改为 case-insensitive 查表
--   (b) SchoolDataSyncServiceImpl.upsertEntity: teaching_class 数据源
--       路由到 upsertSchoolCourse (PDF 2026-04-21 复核 bzkcxx 端点
--       实际返回课程信息，非教学班信息)
--
-- 已知限制:
--   - course_selection PDF 仅返回 XH/KSXZDM/KSXZMC/KCBH 4 个字段，
--     远少于 CourseSelection 实体 NOT NULL 列数；需后续 DDL 放宽。
--   - teaching_plans 返回的是教学计划中的课程条目，部分实体必填
--     字段 (planName/planStatus) PDF 不提供。
-- ════════════════════════════════════════════════════════════════

-- Step 1: 清空 V12.1 遗留的错误 mappings
DELETE FROM field_mappings WHERE data_source IN (
    'administrative_class', 'school_course', 'teaching_class',
    'course_schedule', 'course_selection', 'level_exam_scores',
    'teaching_plans', 'teacher_courses', 'student_schedule'
);

-- 1. administrative_class (cdsp_1710949911983374368 / 行政班基本信息)
-- PDF fields (UPPERCASE): XZBH, BJMC, XSLB, SSXYBM, SSXYMC, SSNJ,
--                         BZRGH, BZRXM, ZY, JBNY
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'XZBH',   'classCode',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '行政班编号'),
('administrative_class', 'BJMC',   'className',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '班级名称'),
('administrative_class', 'XSLB',   'classType',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '学生类别'),
('administrative_class', 'SSXYBM', 'collegeCode',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4,  1, '所属学院编码'),
('administrative_class', 'SSXYMC', 'collegeName',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '所属学院名称'),
('administrative_class', 'SSNJ',   'grade',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '所属年级'),
('administrative_class', 'BZRGH',  'headTeacherCode',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '班主任工号'),
('administrative_class', 'BZRXM',  'headTeacherName',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '班主任姓名'),
('administrative_class', 'ZY',     'majorName',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '专业名称'),
('administrative_class', 'JBNY',   'establishDate',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 10, 1, '建班年月');

-- 2. school_course (cdsp_1715290945064353805 / 课程基本信息)
-- PDF fields (lowercase): kcxs, kcdm, kcxf, kcywmc, kcjj, kslx, kkyx, kcmc
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('school_course', 'kcdm',   'courseCode',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程代码'),
('school_course', 'kcmc',   'courseName',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称'),
('school_course', 'kcywmc', 'courseNameEn',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称'),
('school_course', 'kcxf',   'credits',          'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分'),
('school_course', 'kcxs',   'totalHours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时'),
('school_course', 'kcjj',   'courseIntro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介'),
('school_course', 'kslx',   'assessmentMethod', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型'),
('school_course', 'kkyx',   'collegeName',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系');

-- 3. teaching_class (cdsp_1721804030860152850 / 本专科生课程信息)
-- 此端点实际返回课程信息（非教学班），代码已路由到 upsertSchoolCourse
-- PDF fields (UPPERCASE): KCXS, KCXF, KKYX, KCJJ, KCYWMC, KCMC, KCH, KSLX
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_class', 'KCH',    'courseCode',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程号（作课程代码）'),
('teaching_class', 'KCMC',   'courseName',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称'),
('teaching_class', 'KCYWMC', 'courseNameEn',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称'),
('teaching_class', 'KCXF',   'credits',          'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分'),
('teaching_class', 'KCXS',   'totalHours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时'),
('teaching_class', 'KCJJ',   'courseIntro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介'),
('teaching_class', 'KSLX',   'assessmentMethod', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型'),
('teaching_class', 'KKYX',   'collegeName',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系');

-- 4. course_schedule (cdsp_1715290377801515044 / 课程安排信息)
-- PDF fields (lowercase): xkkch, xn, xq, skxqj, skjc, kczc, skdd
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_schedule', 'xkkch', 'classId',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '选课课号'),
('course_schedule', 'skxqj', 'dayOfWeek',     'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '上课星期几 1-7'),
('course_schedule', 'skjc',  'period',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '上课节次'),
('course_schedule', 'kczc',  'weekRange',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '课程周次'),
('course_schedule', 'skdd',  'classLocation', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '上课地点'),
('course_schedule', 'xn',    'academicYear',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年（CDSP 单年如 "2024"）'),
('course_schedule', 'xq',    'semester',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学期中文 "秋"/"春"');

-- 5. course_selection (cdsp_1715294802871926794 / 学生选课)
-- PDF fields (UPPERCASE): XH, KSXZDM, KSXZMC, KCBH
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_selection', 'XH',     'studentId',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('course_selection', 'KCBH',   'classId',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程班号（作 class_id）'),
('course_selection', 'KSXZDM', 'selectionStatus', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '考试性质代码'),
('course_selection', 'KSXZMC', 'classTime',       'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '考试性质名称');

-- 6. level_exam_scores (DS0076 / 本专科生等级考试成绩)
-- PDF fields (lowercase): xh, xhxm, xm, kskm, ksrq, fskscj
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', 'xh',     'studentId',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('level_exam_scores', 'xm',     'studentName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2, 1, '姓名'),
('level_exam_scores', 'kskm',   'examName',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '考试科目'),
('level_exam_scores', 'ksrq',   'examDate',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 1, 4, 1, '考试日期'),
('level_exam_scores', 'fskscj', 'score',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 5, 1, '考试成绩');

-- 7. teaching_plans (cdsp_1715289655026466836 / 教学计划信息)
-- PDF fields (lowercase): kkjhid, kcdm, cksm, zxs, skjszgh, kkdwdm, kkdwdm_name,
--                         kkxn, kkxq_name, ksfsdm, ksfsmc
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_plans', 'kkjhid',      'planId',             'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '开课计划 ID'),
('teaching_plans', 'kcdm',        'courseCode',         'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2,  1, '课程代码'),
('teaching_plans', 'cksm',        'trainingObjective',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '参考书目'),
('teaching_plans', 'zxs',         'totalCredits',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4,  1, '总学时'),
('teaching_plans', 'skjszgh',     'creator',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '授课教师职工号'),
('teaching_plans', 'kkdwdm',      'collegeCode',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '开课单位代码'),
('teaching_plans', 'kkdwdm_name', 'collegeName',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '开课单位名称'),
('teaching_plans', 'kkxn',        'grade',              'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '开课学年'),
('teaching_plans', 'kkxq_name',   'planName',           'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '开课学期名称'),
('teaching_plans', 'ksfsdm',      'planStatus',         'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '考试方式代码'),
('teaching_plans', 'ksfsmc',      'version',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '考试方式名称');

-- 8. teacher_courses (cdsp_1715294645677801497 / 教师授课)
-- PDF fields (lowercase): kxh, kcbh, kcmc, rkjsh, rkjsxm, xn, xq, kcbbm, kcdm
-- 数据落到 TeachingClass 表（service 逻辑一致）
-- 语义对齐 CDSP 后台 API 返回示例（2026-04-21 截图）:
--   kcdm = 课程代码 (如 "594625") → courseCode
--   kcbh = 课程班号 (如 "51425977" 开班唯一 ID) → classId
--   kxh  = 课序号（通常与 kcdm 同值）→ 不入库
--   xq   = 学期 varchar 中文 "秋"/"春" → STRING，service 层 parseSchoolSemester 转 Integer
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teacher_courses', 'kcbh',   'classId',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程班号（作 class_id）'),
('teacher_courses', 'kcdm',   'courseCode',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程代码'),
('teacher_courses', 'kcmc',   'courseName',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '课程名称'),
('teacher_courses', 'rkjsh',  'teacherId',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '任课教师工号'),
('teacher_courses', 'rkjsxm', 'teacherName',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '任课教师姓名'),
('teacher_courses', 'xn',     'academicYear', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年（CDSP 单年如 "2024"）'),
('teacher_courses', 'xq',     'semester',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学期中文 "秋"/"春"'),
('teacher_courses', 'kcbbm',  'className',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '课程班别名（作班级展示名）');

-- 9. student_schedule (cdsp_1715294397274341377 / 学生课表)
-- PDF fields (lowercase): xh, xkkh, kcbh, kcmc, skjszgh, jsmc, xqj, djj,
--                         zcap, js, xn, xq
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('student_schedule', 'xh',      'studentId',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '学号'),
('student_schedule', 'xkkh',    'classId',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '选课课号'),
('student_schedule', 'kcbh',    'courseCode',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3,  1, '课程编号'),
('student_schedule', 'kcmc',    'courseName',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4,  1, '课程名称'),
('student_schedule', 'skjszgh', 'teacherId',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '上课教师职工号'),
('student_schedule', 'jsmc',    'teacherName',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '教师名称'),
('student_schedule', 'xqj',     'dayOfWeek',     'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7,  1, '星期几 1-7'),
('student_schedule', 'djj',     'period',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8,  1, '第几节'),
('student_schedule', 'zcap',    'weekRange',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '周次安排'),
('student_schedule', 'js',      'classLocation', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '教室'),
('student_schedule', 'xn',      'academicYear',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '学年（CDSP 单年如 "2024"）'),
('student_schedule', 'xq',      'semester',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 12, 1, '学期中文 "秋"/"春"');

-- ════════════════════════════════════════════════════════════════


-- ═══════════════════════════════════════════════════════════════
-- V21: SSO 绑定表添加管理员备注字段
-- 用于记录用户被删除后重新登录等需要管理员审核的情况
-- ═══════════════════════════════════════════════════════════════

-- 检查列是否存在的存储过程
DELIMITER $$
DROP PROCEDURE IF EXISTS add_column_if_not_exists_v21$$
CREATE PROCEDURE add_column_if_not_exists_v21()
BEGIN
    DECLARE col_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO col_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'english_learning'
      AND TABLE_NAME = 'sso_user_bindings'
      AND COLUMN_NAME = 'admin_remark';

    IF col_exists = 0 THEN
        ALTER TABLE sso_user_bindings
        ADD COLUMN admin_remark TEXT COMMENT '管理员备注：记录特殊情况（如用户被删除后重新登录）' AFTER sso_member_of;
    END IF;
END$$
DELIMITER ;

CALL add_column_if_not_exists_v21();
DROP PROCEDURE IF EXISTS add_column_if_not_exists_v21;


-- ═══════════════════════════════════════════════════════════════
-- V22: 按学院链式拉取数据模型
-- 新建 school_student / school_teacher / teacher_student_binding
-- 以支撑外院链式拉取、SSO 自动授权、教师-学生绑定查询
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS school_student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    student_id VARCHAR(50) NOT NULL UNIQUE COMMENT '学号（对应 CDSP xh / XH）',
    student_name VARCHAR(100) COMMENT '学生姓名（SSO attributes 或手工补全）',
    college_name VARCHAR(100) NOT NULL COMMENT '学院名称（CDSP kkyx）',
    college_code VARCHAR(50) COMMENT '学院代码（CDSP 未提供，预留）',
    class_code VARCHAR(50) COMMENT '行政班号（CAS attributes 补全，首次 NULL）',
    class_name VARCHAR(100) COMMENT '班级名称（CAS attributes 补全）',
    grade VARCHAR(20) COMMENT '年级（可用学号前 4 位推断）',
    major_name VARCHAR(100) COMMENT '专业（CAS attributes 补全）',
    source_course_codes TEXT COMMENT '发现该学生的课程代码列表（JSON 数组），诊断用',
    sync_time DATETIME NOT NULL COMMENT '首次同步时间',
    last_exam_sync_time DATETIME COMMENT '上次四六级成绩同步时间',
    last_schedule_sync_time DATETIME COMMENT '上次课表同步时间',
    sync_source VARCHAR(20) NOT NULL DEFAULT 'SCHOOL_API' COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_college (college_name),
    INDEX idx_grade (grade),
    INDEX idx_class_code (class_code),
    INDEX idx_last_exam (last_exam_sync_time),
    INDEX idx_last_schedule (last_schedule_sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDSP 同步的学生主数据（链式拉取产物）';

CREATE TABLE IF NOT EXISTS school_teacher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    teacher_id VARCHAR(50) NOT NULL UNIQUE COMMENT '工号（CDSP rkjsh / skjszgh）',
    teacher_name VARCHAR(100) COMMENT '教师姓名（CDSP rkjsxm）',
    college_name VARCHAR(100) NOT NULL COMMENT '学院（来自链式触发学院）',
    college_code VARCHAR(50) COMMENT '学院代码（预留）',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    last_schedule_sync_time DATETIME COMMENT '上次课表同步时间',
    sync_source VARCHAR(20) NOT NULL DEFAULT 'SCHOOL_API' COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_college (college_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDSP 同步的教师主数据';

CREATE TABLE IF NOT EXISTS teacher_student_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    teacher_id VARCHAR(50) NOT NULL COMMENT '工号',
    student_id VARCHAR(50) NOT NULL COMMENT '学号',
    course_code VARCHAR(50) NOT NULL COMMENT '课程代码 kcdm',
    course_class_code VARCHAR(50) COMMENT '课程班号 kcbh',
    academic_year VARCHAR(20) NOT NULL DEFAULT '' COMMENT '学年（NULL 替换为空串以配合 UNIQUE 索引）',
    semester VARCHAR(10) NOT NULL DEFAULT '' COMMENT '学期（秋/春），NULL 替换为空串',
    source_type VARCHAR(20) NOT NULL DEFAULT 'CHAIN_SYNC' COMMENT '来源: CHAIN_SYNC / MANUAL',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_teacher_student_course (teacher_id, student_id, course_code, academic_year, semester),
    INDEX idx_teacher (teacher_id),
    INDEX idx_student (student_id),
    INDEX idx_course (course_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师-学生绑定关系表';

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'ops.school_data_chain_fetch', 'false', '运维端按学院链式拉取功能开关', 0),
('feature_toggle', 'student.lazy_data_sync',     'true',  '学生端按需 Lazy 同步四六级/课表', 0),
('feature_toggle', 'sso.auto_grant_student',     'false', 'SSO 学号匹配 school_student 自动授权 STUDENT', 0),
('feature_toggle', 'sso.auto_grant_teacher',     'false', 'SSO 工号匹配 school_teacher 自动授权 TEACHER', 0),
('school_data',    'sync_college_chain_cron',       '0 30 3 * * ?', '按学院链式刷新 cron 表达式', 0),
('school_data',    'sync_college_chain_colleges',   '外国语学院',   '链式刷新白名单（逗号分隔学院名）', 0),
('school_data',    'sync_college_chain_match_mode', 'eq',           '学院名匹配模式（eq=精确 / contains=包含）', 0);

-- ────────────────────────────────────────
-- V22 补充: SSO 角色映射 ou=People + level_exam_scores 修复
-- ────────────────────────────────────────

-- SSO: CAS containerId 通用组 → STUDENT
INSERT IGNORE INTO sso_config (config_key, config_value, config_type, description) VALUES
('sso.role_mapping.ou=People', 'STUDENT', 'ROLE_MAPPING', 'CAS containerId 通用组 → STUDENT（大多数人是学生）');

-- level_exam_scores: student_name / exam_type / is_passed 改为可空（CDSP 不提供）
ALTER TABLE level_exam_scores MODIFY COLUMN student_name VARCHAR(100) COMMENT '学生姓名（CDSP 不提供，可为空）';
ALTER TABLE level_exam_scores MODIFY COLUMN exam_type VARCHAR(50) COMMENT '考试类型（CDSP 不提供，从 examName 推断）';
ALTER TABLE level_exam_scores MODIFY COLUMN is_passed BOOLEAN COMMENT '是否通过（CDSP 不提供，从 score 推断）';

-- ════════════════════════════════════════════════════════════════
-- V23: sync_logs 分页扩展字段
-- ════════════════════════════════════════════════════════════════

ALTER TABLE sync_logs
    ADD COLUMN pagination_complete TINYINT(1) NULL COMMENT '分页是否完整结束' AFTER operator,
    ADD COLUMN pagination_stop_reason VARCHAR(64) NULL COMMENT '分页停止原因' AFTER pagination_complete,
    ADD COLUMN pagination_pages_fetched INT NULL COMMENT '已拉取页数' AFTER pagination_stop_reason,
    ADD COLUMN pagination_last_skip INT NULL COMMENT '最后一次skip值' AFTER pagination_pages_fetched,
    ADD COLUMN pagination_page_size INT NULL COMMENT '分页pageSize' AFTER pagination_last_skip,
    ADD COLUMN pagination_guard_hit TINYINT(1) NULL COMMENT '是否触发分页防护' AFTER pagination_page_size,
    ADD COLUMN fetched_row_count INT NULL COMMENT '上游已拉取条数' AFTER pagination_guard_hit;

-- ════════════════════════════════════════════════════════════════
-- V24: 相似度矩阵表（Item-CF / User-CF 推荐算法核心数据）
-- ════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS `text_similarity_matrix` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `text_id` BIGINT NOT NULL COMMENT '文章ID',
    `similar_text_id` BIGINT NOT NULL COMMENT '相似文章ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_text_pair` (`text_id`, `similar_text_id`),
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章相似度矩阵';

CREATE TABLE IF NOT EXISTS `text_similarity_matrix_tmp` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `text_id` BIGINT NOT NULL COMMENT '文章ID',
    `similar_text_id` BIGINT NOT NULL COMMENT '相似文章ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_text_pair` (`text_id`, `similar_text_id`),
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章相似度矩阵临时表';

CREATE TABLE IF NOT EXISTS `user_similarity_matrix` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `similar_user_id` BIGINT NOT NULL COMMENT '相似用户ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_pair` (`user_id`, `similar_user_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户相似度矩阵';

CREATE TABLE IF NOT EXISTS `user_similarity_matrix_tmp` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `similar_user_id` BIGINT NOT NULL COMMENT '相似用户ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_pair` (`user_id`, `similar_user_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户相似度矩阵临时表';

-- ════════════════════════════════════════════════════════════════
-- V25: 相似度矩阵补 similar_*_id 索引（Item-CF / User-CF 双向查询优化）
-- ════════════════════════════════════════════════════════════════

ALTER TABLE `text_similarity_matrix`      ADD INDEX `idx_similar_text_id` (`similar_text_id`);
ALTER TABLE `text_similarity_matrix_tmp`  ADD INDEX `idx_similar_text_id` (`similar_text_id`);
ALTER TABLE `user_similarity_matrix`      ADD INDEX `idx_similar_user_id` (`similar_user_id`);
ALTER TABLE `user_similarity_matrix_tmp`  ADD INDEX `idx_similar_user_id` (`similar_user_id`);

-- ════════════════════════════════════════════════════════════════
-- V26: v_user_text_interaction_scores 视图（原 CROSS JOIN 版本，保持不变）
-- ════════════════════════════════════════════════════════════════

CREATE OR REPLACE VIEW `v_user_text_interaction_scores` AS
SELECT
    u.id                                          AS user_id,
    t.id                                          AS text_id,
    COALESCE(utr.read_at IS NOT NULL, 0)          AS w_read,
    COALESCE(tlr.is_completed = 1, 0)             AS w_complete,
    COALESCE(af.id IS NOT NULL, 0)                AS w_favorite,
    LEAST(COALESCE(tlr.duration_seconds, 0) / 600.0, 1.0) AS w_duration,
    COALESCE(sr.correct_rate, 0.0)                 AS w_accuracy,
    LEAST(COALESCE(ul.underline_count, 0) / 10.0, 1.0) AS w_underline,
    LEAST(
        COALESCE(utr.read_at IS NOT NULL, 0) * 0.2
        + COALESCE(tlr.is_completed = 1, 0) * 0.2
        + COALESCE(af.id IS NOT NULL, 0) * 0.3
        + LEAST(COALESCE(tlr.duration_seconds, 0) / 600.0, 1.0) * 0.1
        + COALESCE(sr.correct_rate, 0.0) * 0.1
        + LEAST(COALESCE(ul.underline_count, 0) / 10.0, 1.0) * 0.1,
        1.0
    )                                             AS interaction_score
FROM `users` u
CROSS JOIN `texts` t
LEFT JOIN `user_text_reads` utr
    ON utr.user_id = u.id AND utr.text_id = t.id
LEFT JOIN `text_learning_records` tlr
    ON tlr.student_id = u.id AND tlr.text_id = t.id
LEFT JOIN `article_favorites` af
    ON af.student_id = u.id AND af.text_id = t.id
LEFT JOIN (
    SELECT
        sr.student_id,
        lr.text_id,
        CAST(SUM(CASE WHEN sr.score_type = 'ASSIGNMENT'
            THEN COALESCE(JSON_EXTRACT(sr.score_details, '$.correct_count'), 0) ELSE 0 END) AS DECIMAL(5,2))
        / NULLIF(SUM(CASE WHEN sr.score_type = 'ASSIGNMENT'
            THEN COALESCE(JSON_EXTRACT(sr.score_details, '$.total_count'), 0) ELSE 0 END), 0) AS correct_rate
    FROM score_records sr
    LEFT JOIN learning_records lr ON lr.assignment_id = sr.assignment_id
    WHERE sr.score_type = 'ASSIGNMENT'
    GROUP BY sr.student_id, lr.text_id
) sr ON sr.student_id = u.id AND sr.text_id = t.id
LEFT JOIN (
    SELECT user_id, text_id, COUNT(*) AS underline_count
    FROM underlines
    GROUP BY user_id, text_id
) ul ON ul.user_id = u.id AND ul.text_id = t.id
WHERE u.role IN ('STUDENT', 'USER');

-- ════════════════════════════════════════════════════════════════
