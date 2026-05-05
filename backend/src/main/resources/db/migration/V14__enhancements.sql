-- ═══════════════════════════════════════════════════════════════
-- V14: 增强功能
-- 整合自: V22 (chain_fetch_schema) + V24 (similarity_matrix)
--       + V25 (similar_id_indexes) + V26 (optimize_interaction_view)
-- ═══════════════════════════════════════════════════════════════

-- Part 1: 按学院链式拉取数据模型
-- ─────────────────────────────────────────────
-- 用途: 支持按学院链式拉取学生/教师数据，配合 SSO 自动授权、
--       教师-学生绑定查询等下游场景
-- Author: System
-- Date: 2026-04-22
-- ============================================================================

-- ============================================================================
-- 1. 学生主数据（CDSP 链式拉取产物）
-- ============================================================================
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

-- ============================================================================
-- 2. 教师主数据（CDSP 链式拉取产物）
-- ============================================================================
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

-- ============================================================================
-- 3. 教师-学生绑定（链式拉取衍生关联）
-- ============================================================================
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

-- ============================================================================
-- 4. Feature Toggle / 学院链式配置 seed
-- ============================================================================
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'ops.school_data_chain_fetch', 'false', '运维端按学院链式拉取功能开关', 0),
('feature_toggle', 'student.lazy_data_sync', 'true', '学生端按需 Lazy 同步四六级/课表', 0),
('feature_toggle', 'sso.auto_grant_student', 'false', 'SSO 学号匹配 school_student 自动授权 STUDENT', 0),
('feature_toggle', 'sso.auto_grant_teacher', 'false', 'SSO 工号匹配 school_teacher 自动授权 TEACHER', 0),
('school_data', 'sync_college_chain_cron', '0 30 3 * * ?', '按学院链式刷新 cron 表达式', 0),
('school_data', 'sync_college_chain_colleges', '外国语学院', '链式刷新白名单（逗号分隔学院名）', 0),
('school_data', 'sync_college_chain_match_mode', 'eq', '学院名匹配模式（eq=精确 / contains=包含）', 0);

-- Part 4: 相似度矩阵表
-- ─────────────────────────────────────────────

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ═══════════════════════════════════════════════════════════════
-- 1. text_similarity_matrix — 文章相似度矩阵
-- ═══════════════════════════════════════════════════════════════
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

-- ═══════════════════════════════════════════════════════════════
-- 2. text_similarity_matrix_tmp — 文章相似度矩阵临时表
--    表结构与 text_similarity_matrix 完全一致
-- ═══════════════════════════════════════════════════════════════
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

-- ═══════════════════════════════════════════════════════════════
-- 3. user_similarity_matrix — 用户相似度矩阵
-- ═══════════════════════════════════════════════════════════════
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

-- ═══════════════════════════════════════════════════════════════
-- 4. user_similarity_matrix_tmp — 用户相似度矩阵临时表
--    表结构与 user_similarity_matrix 完全一致
-- ═══════════════════════════════════════════════════════════════
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

-- ═══════════════════════════════════════════════════════════════
-- 5. v_user_text_interaction_scores — 用户-文章交互评分视图
--
-- 核心公式：
--   w_read(0.2)       — 是否读过（0/1）
--   w_complete(0.2)   — 是否完成阅读（0/1）
--   w_favorite(0.3)   — 是否收藏（0/1）
--   w_duration(0.1)   — 阅读时长得分（满分600秒）
--   w_accuracy(0.1)    — 习题正确率
--   w_underline(0.1)   — 划线次数得分（满分10次）
--
--   最终分数 = LEAST(1.0, 总分)，只考虑 role 为 'STUDENT' 或 'USER' 的用户
-- ═══════════════════════════════════════════════════════════════
CREATE OR REPLACE VIEW `v_user_text_interaction_scores` AS
SELECT
    u.id                                          AS user_id,
    t.id                                          AS text_id,

    -- w_read(0.2): 是否已读
    COALESCE(utr.read_at IS NOT NULL, 0)          AS w_read,

    -- w_complete(0.2): 是否完成阅读
    COALESCE(tlr.is_completed = 1, 0)             AS w_complete,

    -- w_favorite(0.3): 是否收藏
    COALESCE(af.id IS NOT NULL, 0)                AS w_favorite,

    -- w_duration(0.1): 阅读时长得分（满分600秒）
    LEAST(COALESCE(tlr.duration_seconds, 0) / 600.0, 1.0) AS w_duration,

    -- w_accuracy(0.1): 习题正确率
    COALESCE(
        sr.correct_rate,
        0.0
    )                                             AS w_accuracy,

    -- w_underline(0.1): 划线次数得分（满分10次）
    LEAST(COALESCE(ul.underline_count, 0) / 10.0, 1.0) AS w_underline,

    -- 最终加权总分（封顶1.0）
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
    -- 每用户每文章的划线次数
    SELECT user_id, text_id, COUNT(*) AS underline_count
    FROM underlines
    GROUP BY user_id, text_id
) ul ON ul.user_id = u.id AND ul.text_id = t.id
WHERE u.role IN ('STUDENT', 'USER');

-- ─────────────────────────────────────────────
-- Part 5: 相似度矩阵补充索引
-- ─────────────────────────────────────────────

SET @idx_text_similarity_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'text_similarity_matrix' AND INDEX_NAME = 'idx_similar_text_id'
);
SET @idx_text_similarity_sql = IF(
    @idx_text_similarity_exists = 0,
    'ALTER TABLE `text_similarity_matrix` ADD INDEX `idx_similar_text_id` (`similar_text_id`)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_text_similarity_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_text_similarity_tmp_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'text_similarity_matrix_tmp' AND INDEX_NAME = 'idx_similar_text_id'
);
SET @idx_text_similarity_tmp_sql = IF(
    @idx_text_similarity_tmp_exists = 0,
    'ALTER TABLE `text_similarity_matrix_tmp` ADD INDEX `idx_similar_text_id` (`similar_text_id`)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_text_similarity_tmp_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_user_similarity_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_similarity_matrix' AND INDEX_NAME = 'idx_similar_user_id'
);
SET @idx_user_similarity_sql = IF(
    @idx_user_similarity_exists = 0,
    'ALTER TABLE `user_similarity_matrix` ADD INDEX `idx_similar_user_id` (`similar_user_id`)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_user_similarity_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_user_similarity_tmp_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_similarity_matrix_tmp' AND INDEX_NAME = 'idx_similar_user_id'
);
SET @idx_user_similarity_tmp_sql = IF(
    @idx_user_similarity_tmp_exists = 0,
    'ALTER TABLE `user_similarity_matrix_tmp` ADD INDEX `idx_similar_user_id` (`similar_user_id`)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_user_similarity_tmp_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Part 6: 优化用户文本交互视图
-- ─────────────────────────────────────────────

-- 源表：
--   - user_text_reads       (user_id,   text_id)
--   - text_learning_records (student_id,text_id)  student_id 对应 user_id
--   - article_favorites     (student_id,text_id)  同上
--   - underlines            (user_id,   text_id)
-- ============================================================================

SET NAMES utf8mb4;

CREATE OR REPLACE VIEW `v_user_text_interaction_scores` AS
WITH user_text_pairs AS (
    SELECT user_id,    text_id FROM user_text_reads
    UNION
    SELECT student_id, text_id FROM text_learning_records
    UNION
    SELECT student_id, text_id FROM article_favorites
    UNION
    SELECT user_id,    text_id FROM underlines
)
SELECT
    utp.user_id                                    AS user_id,
    utp.text_id                                    AS text_id,

    COALESCE(utr.read_at IS NOT NULL, 0)           AS w_read,
    COALESCE(tlr.is_completed = 1, 0)              AS w_complete,
    COALESCE(af.id IS NOT NULL, 0)                 AS w_favorite,
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
    )                                              AS interaction_score

FROM user_text_pairs utp
INNER JOIN `users` u ON u.id = utp.user_id AND u.role IN ('STUDENT', 'USER')
INNER JOIN `texts` t ON t.id = utp.text_id AND t.deleted = 0
LEFT JOIN `user_text_reads` utr
    ON utr.user_id = utp.user_id AND utr.text_id = utp.text_id
LEFT JOIN `text_learning_records` tlr
    ON tlr.student_id = utp.user_id AND tlr.text_id = utp.text_id
LEFT JOIN `article_favorites` af
    ON af.student_id = utp.user_id AND af.text_id = utp.text_id
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
) sr ON sr.student_id = utp.user_id AND sr.text_id = utp.text_id
LEFT JOIN (
    SELECT user_id, text_id, COUNT(*) AS underline_count
    FROM underlines
    GROUP BY user_id, text_id
) ul ON ul.user_id = utp.user_id AND ul.text_id = utp.text_id;
