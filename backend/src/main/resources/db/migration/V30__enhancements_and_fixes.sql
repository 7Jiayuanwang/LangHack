-- ═══════════════════════════════════════════════════════════════
-- V15: 功能增强与问题修复
-- 整合自: V21 (sso_binding_admin_remark) + V22 (chain_fetch_schema)
--       + V23 (sync_log_pagination_summary) + V24 (similarity_matrix)
--       + V25 (similar_id_indexes) + V26 (optimize_interaction_view)
--       + V28 (fix_student_schedule_nullable_fields)
--       + V29 (fix_field_mappings_administrative_class_teaching_plans)
-- ═══════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────
-- Part 1: SSO 绑定表管理员备注字段
-- ─────────────────────────────────────────────
ALTER TABLE sso_user_bindings
ADD COLUMN admin_remark TEXT COMMENT '管理员备注：记录特殊情况（如用户被删除后重新登录）' AFTER sso_member_of;

-- ─────────────────────────────────────────────
-- Part 2: 按学院链式拉取数据模型
-- ─────────────────────────────────────────────

--              与 SSO 自动授权、教师-学生绑定查询等下游场景
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

-- ============================================================================
-- 5. SSO 角色映射补充：ou=People（IMAU CAS 回退 containerId 格式）
--   CAS 不返回 memberOf 时回退使用 containerId，值如 ou=2000001,ou=People
--   ou=People 是全校师生通用组，默认映射为 STUDENT（校园环境大多数人是学生）
--   英文 key 含 '=' 无法写入 YAML default-rules（Spring Boot 吞 '='），故用数据库
-- ============================================================================
INSERT IGNORE INTO sso_config (config_key, config_value, config_type, description) VALUES
('sso.role_mapping.ou=People', 'STUDENT', 'ROLE_MAPPING', 'CAS containerId 通用组 → STUDENT（大多数人是学生）');

-- ============================================================================
-- 6. level_exam_scores 修复：student_name 可为空 + 字段映射补全
--   CDSP 等级考试成绩端点不返回学生姓名，仅返回 学号/考试科目/考试日期/分数
--   原 student_id NOT NULL + student_name NOT NULL 导致 100% INSERT 失败
-- ============================================================================
ALTER TABLE level_exam_scores MODIFY COLUMN student_name VARCHAR(100) COMMENT '学生姓名（CDSP 不提供，可为空）';
ALTER TABLE level_exam_scores MODIFY COLUMN exam_type VARCHAR(50) COMMENT '考试类型（CDSP 不提供，从 examName 推断）';
ALTER TABLE level_exam_scores MODIFY COLUMN is_passed BOOLEAN COMMENT '是否通过（CDSP 不提供，从 score 推断）';

-- 补充 xm → studentName 字段映射（CDSP 未来可能返回姓名字段时自动生效）
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', 'xm', 'studentName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '学生姓名（CDSP 可能不返回）');

-- ─────────────────────────────────────────────
-- Part 3: 同步日志分页字段（V9 已包含，此处保留版本连续性）
-- ─────────────────────────────────────────────
-- V23 的列已在 V9 的 CREATE TABLE sync_logs 中定义

-- ─────────────────────────────────────────────
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


USE `english_learning`;

-- 幂等添加索引（如果索引已存在则跳过）
DELIMITER $$

CREATE PROCEDURE add_index_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_column_name VARCHAR(64)
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO index_exists
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND index_name = p_index_name;

    IF index_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD INDEX `', p_index_name, '` (`', p_column_name, '`)');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_index_if_not_exists('text_similarity_matrix', 'idx_similar_text_id', 'similar_text_id');
CALL add_index_if_not_exists('text_similarity_matrix_tmp', 'idx_similar_text_id', 'similar_text_id');
CALL add_index_if_not_exists('user_similarity_matrix', 'idx_similar_user_id', 'similar_user_id');
CALL add_index_if_not_exists('user_similarity_matrix_tmp', 'idx_similar_user_id', 'similar_user_id');

DROP PROCEDURE IF EXISTS add_index_if_not_exists;

-- ─────────────────────────────────────────────
-- Part 6: 优化用户文本交互视图
-- ─────────────────────────────────────────────

-- 源表：
--   - user_text_reads       (user_id,   text_id)
--   - text_learning_records (student_id,text_id)  student_id 对应 user_id
--   - article_favorites     (student_id,text_id)  同上
--   - underlines            (user_id,   text_id)
-- ============================================================================

USE `english_learning`;
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

-- ─────────────────────────────────────────────
-- Part 7: 修复学生课表必填字段问题
-- ─────────────────────────────────────────────

ALTER TABLE student_schedule
    MODIFY COLUMN day_of_week TINYINT NULL COMMENT '星期几 1-7，NULL 表示无固定时间',
    MODIFY COLUMN period VARCHAR(20) NULL COMMENT '第几节，NULL 表示无固定时间';

-- 更新字段映射配置：将这两个字段标记为非必填
UPDATE field_mappings
SET is_required = 0
WHERE data_source = 'student_schedule'
  AND target_field IN ('day_of_week', 'period');

-- ─────────────────────────────────────────────
-- Part 8: 修复学校数据字段映射问题
-- ─────────────────────────────────────────────

-- ════════════════════════════════════════════════════════════════
-- Part 1: 修复行政班学院字段映射
-- ════════════════════════════════════════════════════════════════

-- 禁用错误的学院字段映射
UPDATE field_mappings
SET is_active = 0
WHERE data_source = 'administrative_class'
  AND source_field IN ('SSXYBM', 'SSXYMC', '所属学院编码', '所属学院名称');

-- 添加正确的学院字段映射（YXDM/YXMC）
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'YXDM',     'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '院系代码（英文缩写）'),
('administrative_class', '院系代码', 'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '院系代码（中文）'),
('administrative_class', 'YXMC',     'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '院系名称（英文缩写）'),
('administrative_class', '院系名称', 'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '院系名称（中文）');

-- 添加专业代码字段映射（ZYDM）
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'ZYDM',     'major_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '专业代码（英文缩写）'),
('administrative_class', '专业代码', 'major_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '专业代码（中文）');

-- ════════════════════════════════════════════════════════════════
-- Part 2: 添加教学计划专业字段映射
-- ════════════════════════════════════════════════════════════════

INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_plans', 'zybh', 'major_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 12, 1, '专业编号'),
('teaching_plans', 'zymc', 'major_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 13, 1, '专业名称');
