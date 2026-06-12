-- Auto-generated from backend/src/main/resources/db/migration/V*.sql
-- Do not edit by hand; update Flyway migrations and regenerate.
-- NOTE: 此文件使用 $$ 作为存储过程分隔符，必须通过 04-apply-all-migrations.sh 执行
--       不可直接 source，否则 DELIMITER 命令报错

-- V0__base_tables.sql

-- ═══════════════════════════════════════════════════════════════════════
-- V0: 核心基础表（完整版）
-- Description: 创建所有核心表的最终状态，包含所有字段
--
-- 注意: 此文件与 docker/mysql/init/00-base-tables.sql 保持同步
--       后续迁移脚本（V1, V2, V10 等）使用 IF NOT EXISTS 检查保证幂等性
--
-- 同步日期: 2026-05-05
-- ═══════════════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 用户表（核心表，被多个表引用）
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `role` ENUM('USER','STUDENT','TEACHER','ADMIN','OPS') NOT NULL DEFAULT 'USER',
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `inner_uid` VARCHAR(100) DEFAULT NULL COMMENT '用户内部唯一标识符',
    `school_student_id` BIGINT DEFAULT NULL COMMENT '关联 school_student.id',
    `school_teacher_id` BIGINT DEFAULT NULL COMMENT '关联 school_teacher.id',
    `hobby` VARCHAR(100) DEFAULT NULL COMMENT '用户兴趣爱好',
    `level` VARCHAR(50) DEFAULT NULL COMMENT '英语水平等级',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `username` (`username`),
    KEY `idx_username` (`username`),
    KEY `idx_role` (`role`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_inner_uid` (`inner_uid`),
    KEY `idx_users_school_student_id` (`school_student_id`),
    KEY `idx_users_school_teacher_id` (`school_teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- V25: 确保 school_student_id / school_teacher_id 列存在（种子备份可能用旧结构建表）
DROP PROCEDURE IF EXISTS _add_school_cols$$
CREATE PROCEDURE _add_school_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema=DATABASE() AND table_name='users'
                     AND column_name='school_student_id') THEN
        ALTER TABLE users ADD COLUMN school_student_id BIGINT DEFAULT NULL COMMENT '关联 school_student.id' AFTER inner_uid;
        ALTER TABLE users ADD INDEX idx_users_school_student_id (school_student_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema=DATABASE() AND table_name='users'
                     AND column_name='school_teacher_id') THEN
        ALTER TABLE users ADD COLUMN school_teacher_id BIGINT DEFAULT NULL COMMENT '关联 school_teacher.id' AFTER school_student_id;
        ALTER TABLE users ADD INDEX idx_users_school_teacher_id (school_teacher_id);
    END IF;
END$$
CALL _add_school_cols()$$
DROP PROCEDURE IF EXISTS _add_school_cols$$

-- ────────────────────────────────────────
-- 课程表
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `courses` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '课程名称',
    `description` TEXT COMMENT '课程描述',
    `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图片',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `status` ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_by` (`created_by`),
    KEY `idx_deleted` (`deleted`),
    CONSTRAINT `courses_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

-- ────────────────────────────────────────
-- 单元表
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `units` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `name` VARCHAR(100) NOT NULL COMMENT '单元名称',
    `description` TEXT COMMENT '单元描述',
    `order_num` INT DEFAULT 0 COMMENT '排序号',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_order_num` (`order_num`),
    KEY `idx_deleted` (`deleted`),
    CONSTRAINT `units_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单元表';

-- ────────────────────────────────────────
-- 课文表（完整版，包含所有字段）
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `texts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `unit_id` BIGINT DEFAULT NULL COMMENT '单元ID（可为空，表示未分配到单元）',
    `title` VARCHAR(200) NOT NULL COMMENT '课文标题',
    `content` TEXT NOT NULL COMMENT '课文内容',
    `markdown_content` LONGTEXT COMMENT 'Markdown格式的课文内容',
    `content_type` ENUM('TEXT','MARKDOWN') DEFAULT 'TEXT' COMMENT '内容类型：TEXT-纯文本，MARKDOWN-Markdown格式',
    `cet4_content` TEXT COMMENT 'CET-4版本内容',
    `cet6_content` TEXT COMMENT 'CET-6版本内容',
    `academic_content` TEXT COMMENT '学术版本内容',
    `ielts_content` TEXT COMMENT '雅思版本内容',
    `audio_url` VARCHAR(255) DEFAULT NULL COMMENT '音频URL',
    `difficulty` ENUM('EASY','MEDIUM','HARD') DEFAULT 'MEDIUM' COMMENT '难度',
    `order_num` INT DEFAULT 0 COMMENT '排序号',
    `uploaded_by` BIGINT DEFAULT NULL COMMENT '上传者ID（NULL表示系统内容）',
    `tags` VARCHAR(255) DEFAULT NULL COMMENT '标签，多个用逗号分隔',
    `view_count` INT DEFAULT 0 COMMENT '查看次数',
    `like_count` INT DEFAULT 0 COMMENT '点赞次数',
    `read_count` INT DEFAULT 0 COMMENT '阅读次数',
    `is_public` TINYINT DEFAULT 1 COMMENT '是否公开 0-私有 1-公开',
    `status` ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'APPROVED' COMMENT '审核状态',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `chinese_parallel_content` TEXT COMMENT '中英对照版本内容',
    `cet4_markdown_content` LONGTEXT COMMENT 'CET-4难度的Markdown版本内容',
    `cet6_markdown_content` LONGTEXT COMMENT 'CET-6难度的Markdown版本内容',
    `academic_markdown_content` LONGTEXT COMMENT '学术版的Markdown版本内容',
    `chinese_parallel_markdown_content` LONGTEXT COMMENT '中英对照的Markdown版本内容',
    `cover_url` VARCHAR(255) DEFAULT NULL COMMENT '封面图URL',
    `is_featured` TINYINT(1) DEFAULT 0 COMMENT '是否推荐',
    `featured_priority` INT DEFAULT 0 COMMENT '推荐优先级',
    `content_format` VARCHAR(20) DEFAULT 'PLAIN_TEXT' COMMENT '内容格式：PLAIN_TEXT-纯文本，MARKDOWN-Markdown格式',
    `cet4_content_format` VARCHAR(20) DEFAULT 'PLAIN_TEXT' COMMENT 'CET4内容格式',
    `cet6_content_format` VARCHAR(20) DEFAULT 'PLAIN_TEXT' COMMENT 'CET6内容格式',
    `academic_content_format` VARCHAR(20) DEFAULT 'PLAIN_TEXT' COMMENT '学术内容格式',
    `chinese_parallel_content_format` VARCHAR(20) DEFAULT 'PLAIN_TEXT' COMMENT '中英对照内容格式',
    PRIMARY KEY (`id`),
    KEY `idx_unit_id` (`unit_id`),
    KEY `idx_uploaded_by` (`uploaded_by`),
    KEY `idx_difficulty` (`difficulty`),
    KEY `idx_order_num` (`order_num`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_status` (`status`),
    KEY `idx_is_public` (`is_public`),
    KEY `idx_content_format` (`content_format`),
    KEY `idx_is_featured` (`is_featured`),
    CONSTRAINT `texts_ibfk_1` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`) ON DELETE CASCADE,
    CONSTRAINT `texts_ibfk_2` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课文表';

-- ────────────────────────────────────────
-- 班级表
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `classes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '班级名称',
    `description` TEXT COMMENT '班级描述',
    `course_id` BIGINT DEFAULT NULL COMMENT '关联课程ID',
    `teacher_id` BIGINT DEFAULT NULL COMMENT '教师ID',
    `join_code` VARCHAR(10) DEFAULT NULL COMMENT '加入码(可为空)',
    `max_students` INT DEFAULT 50 COMMENT '最大学生数',
    `status` ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `join_code` (`join_code`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_teacher_id` (`teacher_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`),
    CONSTRAINT `classes_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
    CONSTRAINT `classes_ibfk_2` FOREIGN KEY (`teacher_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级表';

-- ────────────────────────────────────────
-- 任务表（完整版，包含 weight 和 ideal_study_time_minutes）
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `assignments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(200) NOT NULL COMMENT '任务标题',
    `description` TEXT COMMENT '任务描述',
    `class_id` BIGINT DEFAULT NULL COMMENT '班级ID',
    `unit_id` BIGINT DEFAULT NULL COMMENT '单元ID',
    `text_id` BIGINT DEFAULT NULL COMMENT '课文ID',
    `type` ENUM('READING','VOCABULARY','COMPREHENSION') NOT NULL COMMENT '任务类型',
    `difficulty` ENUM('EASY','MEDIUM','HARD') DEFAULT 'MEDIUM' COMMENT '难度',
    `deadline` DATETIME DEFAULT NULL COMMENT '截止时间',
    `weight` DECIMAL(5,2) DEFAULT 1.00 COMMENT '任务权重（用于计算加权平均分，默认1.00）',
    `ideal_study_time_minutes` INT DEFAULT 60 COMMENT '理想学习时长（分钟），用于计算分数，默认60分钟',
    `status` ENUM('DRAFT','PUBLISHED','CLOSED') DEFAULT 'DRAFT' COMMENT '状态',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_class_id` (`class_id`),
    KEY `idx_unit_id` (`unit_id`),
    KEY `idx_text_id` (`text_id`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`),
    KEY `idx_created_by` (`created_by`),
    KEY `idx_deadline` (`deadline`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_weight` (`weight`),
    KEY `idx_ideal_study_time` (`ideal_study_time_minutes`),
    CONSTRAINT `assignments_ibfk_1` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`),
    CONSTRAINT `assignments_ibfk_2` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`),
    CONSTRAINT `assignments_ibfk_3` FOREIGN KEY (`text_id`) REFERENCES `texts` (`id`),
    CONSTRAINT `assignments_ibfk_4` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- ────────────────────────────────────────
-- 单词表
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `words` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `word` VARCHAR(100) NOT NULL COMMENT '单词',
    `pronunciation` VARCHAR(200) DEFAULT NULL COMMENT '发音',
    `definition` TEXT COMMENT '中文释义',
    `example_sentence` TEXT COMMENT '例句',
    `audio_url` VARCHAR(255) DEFAULT NULL COMMENT '音频URL',
    `part_of_speech` VARCHAR(50) DEFAULT NULL COMMENT '词性',
    `difficulty` ENUM('EASY','MEDIUM','HARD') DEFAULT 'MEDIUM' COMMENT '难度',
    `synonyms` TEXT COMMENT '同义词',
    `antonyms` TEXT COMMENT '反义词',
    `wordnet_id` VARCHAR(100) DEFAULT NULL COMMENT 'WordNet ID',
    `multiple_definitions` TEXT COMMENT '多个释义',
    `source` VARCHAR(100) DEFAULT NULL COMMENT '来源',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_word` (`word`),
    KEY `idx_difficulty` (`difficulty`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单词表';

-- ────────────────────────────────────────
-- 生词本表（完整版，包含 SM-2 算法字段）
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `vocabulary_books` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `word_id` BIGINT NOT NULL COMMENT '单词ID',
    `added_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    `review_count` INT DEFAULT 0 COMMENT '复习次数',
    `last_reviewed` DATETIME DEFAULT NULL COMMENT '最后复习时间',
    `mastery_level` ENUM('NEW','LEARNING','FAMILIAR','MASTERED') DEFAULT 'NEW' COMMENT '掌握程度：NEW-新词，LEARNING-学习中，FAMILIAR-熟悉，MASTERED-已掌握',
    `repetition` INT DEFAULT 0 COMMENT 'SM2重复次数',
    `interval_days` INT DEFAULT 1 COMMENT 'SM2间隔天数',
    `easiness_factor` DOUBLE DEFAULT 2.5 COMMENT 'SM2难度因子',
    `next_review_time` DATETIME DEFAULT NULL COMMENT '下次复习时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_word` (`student_id`, `word_id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_word_id` (`word_id`),
    KEY `idx_mastery_level` (`mastery_level`),
    CONSTRAINT `vocabulary_books_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `vocabulary_books_ibfk_2` FOREIGN KEY (`word_id`) REFERENCES `words` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生词本表';

-- ────────────────────────────────────────
-- 学习记录表（完整版，包含 text_id）
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `learning_records` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `word_id` BIGINT DEFAULT NULL COMMENT '单词ID',
    `text_id` BIGINT DEFAULT NULL COMMENT '关联的课文ID',
    `assignment_id` BIGINT DEFAULT NULL COMMENT '任务ID',
    `record_type` VARCHAR(50) DEFAULT NULL COMMENT '记录类型：VIEW-浏览，REVIEW-复习，TEST-测试',
    `duration_seconds` INT DEFAULT 0 COMMENT '学习时长（秒）',
    `correct_count` INT DEFAULT 0 COMMENT '正确次数',
    `total_count` INT DEFAULT 0 COMMENT '总次数',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_word_id` (`word_id`),
    KEY `idx_assignment_id` (`assignment_id`),
    KEY `idx_record_type` (`record_type`),
    KEY `idx_created_time` (`created_time`),
    KEY `idx_text_id` (`text_id`),
    CONSTRAINT `learning_records_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习记录表';

-- ────────────────────────────────────────
-- 学生-班级关联表
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `student_classes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `class_id` BIGINT NOT NULL COMMENT '班级ID',
    `join_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `status` ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_class` (`student_id`, `class_id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_class_id` (`class_id`),
    CONSTRAINT `student_classes_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `student_classes_ibfk_2` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生-班级关联表';

-- ────────────────────────────────────────
-- 默认测试账号（密码均为 admin123，BCrypt hash）
-- ────────────────────────────────────────
INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'admin', '$2a$10$ROg6F9MwwqmBGuuGMspwHula/rcizG318TtEVnYudxuLwTdocaNY.', 'ADMIN', '系统管理员', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'admin');

INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'ops', '$2a$10$ROg6F9MwwqmBGuuGMspwHula/rcizG318TtEVnYudxuLwTdocaNY.', 'OPS', '运维人员', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'ops');

INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'teacher', '$2a$10$ROg6F9MwwqmBGuuGMspwHula/rcizG318TtEVnYudxuLwTdocaNY.', 'TEACHER', '演示教师', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'teacher');

INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'student', '$2a$10$ROg6F9MwwqmBGuuGMspwHula/rcizG318TtEVnYudxuLwTdocaNY.', 'STUDENT', '演示学生', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'student');

-- V1__scoring_and_assignments.sql

-- ═══════════════════════════════════════════════════════════════
-- V1: 成绩记录表 + 任务扩展
-- 整合自: V5 (score_records), V6 (weight), V7 (ideal_study_time), V17 (unique constraint)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 创建成绩记录表 [原 V5]
--    唯一约束 uk_student_assignment_type 已内联 [原 V17]
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
-- 2. 任务表扩展 [原 V6 + V7]
--    使用存储过程 IF NOT EXISTS 防止重复添加列
--    注意: Flyway 不支持 DELIMITER 命令，直接定义存储过程
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _add_col_assignments_weight$$
CREATE PROCEDURE _add_col_assignments_weight()
BEGIN
    -- 任务权重（加权平均分）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='assignments' AND column_name='weight') THEN
        ALTER TABLE assignments ADD COLUMN weight DECIMAL(5,2) DEFAULT 1.00
            COMMENT '任务权重（用于计算加权平均分，默认1.00）' AFTER deadline;
    END IF;
    -- 理想学习时长
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='assignments' AND column_name='ideal_study_time_minutes') THEN
        ALTER TABLE assignments ADD COLUMN ideal_study_time_minutes INT DEFAULT 60
            COMMENT '理想学习时长（分钟），默认60分钟' AFTER weight;
    END IF;
END$$
CALL _add_col_assignments_weight()$$
DROP PROCEDURE IF EXISTS _add_col_assignments_weight$$

-- 索引（幂等：IF NOT EXISTS 检查）
DROP PROCEDURE IF EXISTS _add_idx_assignments$$
CREATE PROCEDURE _add_idx_assignments()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE table_schema=DATABASE() AND table_name='assignments' AND index_name='idx_weight') THEN
        CREATE INDEX idx_weight ON assignments (weight);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE table_schema=DATABASE() AND table_name='assignments' AND index_name='idx_ideal_study_time') THEN
        CREATE INDEX idx_ideal_study_time ON assignments (ideal_study_time_minutes);
    END IF;
END$$
CALL _add_idx_assignments()$$
DROP PROCEDURE IF EXISTS _add_idx_assignments$$

-- V2__text_and_learning.sql

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
DROP PROCEDURE IF EXISTS _add_col_lr_text$$
CREATE PROCEDURE _add_col_lr_text()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='learning_records' AND column_name='text_id') THEN
        ALTER TABLE learning_records ADD COLUMN text_id BIGINT COMMENT '关联的课文ID' AFTER word_id;
    END IF;
END$$
CALL _add_col_lr_text()$$
DROP PROCEDURE IF EXISTS _add_col_lr_text$$

DROP PROCEDURE IF EXISTS _add_fk_lr_text$$
CREATE PROCEDURE _add_fk_lr_text()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE table_schema = DATABASE() AND table_name = 'learning_records' AND constraint_name = 'fk_learning_record_text'
    ) THEN
        ALTER TABLE learning_records ADD CONSTRAINT fk_learning_record_text
            FOREIGN KEY (text_id) REFERENCES texts(id) ON DELETE SET NULL;
    END IF;
END$$
CALL _add_fk_lr_text()$$
DROP PROCEDURE IF EXISTS _add_fk_lr_text$$

DROP PROCEDURE IF EXISTS _add_index_lr_text$$
CREATE PROCEDURE _add_index_lr_text()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE() AND table_name = 'learning_records' AND index_name = 'idx_text_id'
    ) THEN
        CREATE INDEX idx_text_id ON learning_records (text_id);
    END IF;
END$$
CALL _add_index_lr_text()$$
DROP PROCEDURE IF EXISTS _add_index_lr_text$$

-- ────────────────────────────────────────
-- 2. 课文表扩展 — 统一批量添加所有列 [原 V2 + V10 + V17]
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _add_col_texts_batch$$
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
END$$
CALL _add_col_texts_batch()$$
DROP PROCEDURE IF EXISTS _add_col_texts_batch$$

-- 推荐优先级复合索引 [原 V10]
DROP PROCEDURE IF EXISTS _add_idx_featured_priority$$
CREATE PROCEDURE _add_idx_featured_priority()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE() AND table_name = 'texts' AND index_name = 'idx_texts_featured_priority'
    ) THEN
        CREATE INDEX idx_texts_featured_priority ON texts (is_featured, featured_priority DESC);
    END IF;
END$$
CALL _add_idx_featured_priority()$$
DROP PROCEDURE IF EXISTS _add_idx_featured_priority$$

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
    content_version VARCHAR(20) DEFAULT NULL COMMENT 'Content version: original/cet4/cet6/academic/chinese_parallel',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_student_text (student_id, text_id),
    INDEX idx_assignment (assignment_id),
    INDEX idx_tlr_student_version (student_id, content_version)
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

-- V3__media_and_annotations.sql

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
DROP PROCEDURE IF EXISTS _add_col_media_paths$$
CREATE PROCEDURE _add_col_media_paths()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='media' AND column_name='file_path') THEN
        ALTER TABLE media ADD COLUMN file_path VARCHAR(500) COMMENT 'stored media file path';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='media' AND column_name='thumbnail_path') THEN
        ALTER TABLE media ADD COLUMN thumbnail_path VARCHAR(500) COMMENT 'stored thumbnail file path';
    END IF;
END$$
CALL _add_col_media_paths()$$
DROP PROCEDURE IF EXISTS _add_col_media_paths$$

-- ────────────────────────────────────────
-- 3. 词汇本添加 SM-2 间隔重复字段 [原 V3]
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _add_col_sm2$$
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
END$$
CALL _add_col_sm2()$$
DROP PROCEDURE IF EXISTS _add_col_sm2$$

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

-- V4__backup_and_update_system.sql

-- ═══════════════════════════════════════════════════════════════
-- V4: 数据库备份系统 + 自动更新配置
-- 整合自: 原 V4 (backup_records, backup_operation_logs, backup_config)
--         + V8 (update_config)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 创建备份记录表 [原 V4]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS backup_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL COMMENT '备份文件名',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    backup_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '备份类型: MANUAL/SCHEDULED',
    backup_mode VARCHAR(20) NOT NULL DEFAULT 'FULL' COMMENT '备份模式: FULL/INCREMENTAL',
    category VARCHAR(50) DEFAULT 'SYSTEM' COMMENT '备份分类: system_config/user_data/business_data/school_data',
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

SET @backup_category_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'backup_records'
      AND COLUMN_NAME = 'category'
);
SET @backup_category_sql = IF(
    @backup_category_exists = 0,
    'ALTER TABLE backup_records ADD COLUMN category VARCHAR(50) DEFAULT ''SYSTEM'' COMMENT ''备份分类: system_config/user_data/business_data/school_data'' AFTER backup_mode',
    'SELECT 1'
);
PREPARE stmt FROM @backup_category_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ────────────────────────────────────────
-- 2. 创建备份操作日志表 [原 V4]
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
-- 3. 创建备份配置表（单行配置模式）[原 V4]
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

INSERT IGNORE INTO backup_config (id, auto_backup_enabled, backup_cron, backup_hour, backup_minute,
    retention_days, max_count, incremental_enabled, full_backup_interval_days)
VALUES (1, 1, '0 0 2 * * ?', 2, 0, 30, 50, 0, 7);

-- ────────────────────────────────────────
-- 4. 自动更新配置表 [原 V8]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS update_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    enabled         TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether scheduled hot deploy is enabled',
    cron_expr       VARCHAR(64) NOT NULL DEFAULT '0 0 2 ? * SUN' COMMENT 'Spring cron expression',
    git_branch      VARCHAR(64) NOT NULL DEFAULT 'master' COMMENT 'tracked git branch',
    last_run_at     DATETIME DEFAULT NULL COMMENT 'last execution time',
    last_run_status VARCHAR(16) DEFAULT 'NEVER' COMMENT 'NEVER/RUNNING/SUCCESS/FAILED'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='hot deploy configuration';

INSERT IGNORE INTO update_config (id, enabled, cron_expr, git_branch, last_run_status)
VALUES (1, 0, '0 0 2 ? * SUN', 'master', 'NEVER');

-- V5__monitoring_and_access.sql

-- ═══════════════════════════════════════════════════════════════
-- V23: 访问监控 + AI调用统计 + 角色扩展 + 演示账号
-- 整合自: V23 (monitoring tables), V24 (OPS role), V25 (demo accounts)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 访问日志表（原始数据，保留7天）[原 V23]
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
-- 2. 每小时聚合统计表（保留90天）[原 V23]
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
-- 3. 每日聚合统计表（长期保留）[原 V23]
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
-- 4. AI/外部服务调用日志表（保留30天）[原 V23]
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
-- 5. AI调用日统计表（长期保留）[原 V23]
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
-- 6. 用户角色扩展 — 新增 OPS 运维角色 [原 V24]
-- ────────────────────────────────────────
ALTER TABLE users MODIFY COLUMN role ENUM('USER','STUDENT','TEACHER','ADMIN','OPS') NOT NULL DEFAULT 'USER';

-- ────────────────────────────────────────
-- 7. 确保演示账号存在且可正常登录 [原 V25]
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

-- V6__system_config_and_ai_providers.sql

-- ═══════════════════════════════════════════════════════════════
-- V6: 系统配置 + AI提供商 + 多模型支持
-- 整合自: 原 V6 (system_config + 默认配置)
--         + V7 (ai_provider 表 + 数据迁移)
--         + V11 (ai_provider.app_id)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 创建系统配置表 + 默认配置 [原 V6]
-- ────────────────────────────────────────
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

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('ai_translate', 'api_url', 'https://api.siliconflow.cn/v1/chat/completions', 'API 地址（OpenAI 兼容）', 0),
('ai_translate', 'api_key', '', 'API 密钥', 1),
('ai_translate', 'model', 'Qwen/Qwen2.5-7B-Instruct', '模型名称', 0),
('ai_translate', 'enabled', 'true', '是否启用', 0),
('ai_translate', 'timeout', '30000', '超时时间(ms)', 0);

-- ────────────────────────────────────────
-- 2. AI 提供商表 + 数据迁移 [原 V7]
--    app_id 字段已内联 [原 V11]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `ai_provider` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `service` VARCHAR(50) NOT NULL COMMENT 'translate / article / chat',
    `name` VARCHAR(100) NOT NULL COMMENT 'display name',
    `api_url` VARCHAR(500) NOT NULL COMMENT 'provider endpoint',
    `api_key` TEXT NOT NULL COMMENT 'provider api key',
    `app_id` VARCHAR(200) NULL COMMENT 'App ID（HiAgent 等服务商必填）',
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

-- V7__feature_toggles.sql

-- ═══════════════════════════════════════════════════════════════
-- V7: 文章收藏 + 功能开关 + 个性化开关
-- 整合自: 原 V12 (article_favorites + feature_toggle 配置)
--         + V13 (修复 system_config description 乱码)
--         + V14 (user_tags + feature_override)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 文章收藏表 [原 V12]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS article_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    text_id BIGINT NOT NULL COMMENT '课文/文章ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_student_text (student_id, text_id),
    INDEX idx_student_id (student_id),
    INDEX idx_text_id (text_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章收藏表';

-- ────────────────────────────────────────
-- 2. 功能开关配置 [原 V12 + V13]
--    V13 的 UPDATE 语句紧跟 INSERT IGNORE，确保 description 正确
-- ────────────────────────────────────────
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

-- 修复 description 乱码 [原 V13]
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

-- ────────────────────────────────────────
-- 3. 个性化功能开关 [原 V14]
-- ────────────────────────────────────────
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

-- V8__sso_integration.sql

-- V15: 统一身份认证 (SSO) 集成

-- SSO 用户绑定表
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

-- V45: 补偿 admin_remark 与新增 admin_role_locked（幂等）
DROP PROCEDURE IF EXISTS _add_sso_binding_cols;
CREATE PROCEDURE _add_sso_binding_cols()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'sso_user_bindings'
          AND COLUMN_NAME  = 'admin_remark'
    ) THEN
        ALTER TABLE sso_user_bindings
            ADD COLUMN admin_remark TEXT COMMENT '管理员备注：记录特殊情况（如用户被删除后重新登录）'
            AFTER sso_member_of;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'sso_user_bindings'
          AND COLUMN_NAME  = 'admin_role_locked'
    ) THEN
        ALTER TABLE sso_user_bindings
            ADD COLUMN admin_role_locked TINYINT(1) DEFAULT 0
                COMMENT '管理员角色锁定: 1=管理员手动审批指定角色，SSO/学校目录不得覆盖'
            AFTER admin_remark;
    END IF;
END;
CALL _add_sso_binding_cols();
DROP PROCEDURE IF EXISTS _add_sso_binding_cols;

-- SSO 登录日志表
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

-- SSO 配置表
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

-- 扩展 users 表字段
SET @sso_bound_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sso_bound'
);
SET @sso_bound_sql = IF(
    @sso_bound_exists = 0,
    'ALTER TABLE users ADD COLUMN sso_bound TINYINT(1) DEFAULT 0 COMMENT ''是否通过SSO绑定: 0=否, 1=是''',
    'SELECT 1'
);
PREPARE stmt FROM @sso_bound_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sso_provider_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sso_provider'
);
SET @sso_provider_sql = IF(
    @sso_provider_exists = 0,
    'ALTER TABLE users ADD COLUMN sso_provider VARCHAR(50) COMMENT ''SSO提供商''',
    'SELECT 1'
);
PREPARE stmt FROM @sso_provider_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sso_subject_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sso_subject'
);
SET @sso_subject_sql = IF(
    @sso_subject_exists = 0,
    'ALTER TABLE users ADD COLUMN sso_subject VARCHAR(255) COMMENT ''SSO唯一标识''',
    'SELECT 1'
);
PREPARE stmt FROM @sso_subject_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_sso_bound_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_sso_bound'
);
SET @idx_sso_bound_sql = IF(
    @idx_sso_bound_exists = 0,
    'CREATE INDEX idx_sso_bound ON users (sso_bound)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_sso_bound_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_sso_subject_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_sso_subject'
);
SET @idx_sso_subject_sql = IF(
    @idx_sso_subject_exists = 0,
    'CREATE INDEX idx_sso_subject ON users (sso_provider, sso_subject)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_sso_subject_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 插入默认 SSO 配置
INSERT IGNORE INTO sso_config (config_key, config_value, config_type, description) VALUES
('sso.enabled', 'false', 'SYSTEM', 'SSO功能总开关'),
('sso.protocol', 'CAS', 'SYSTEM', 'SSO协议类型: CAS 或 OAUTH2'),
('sso.auto_approval.enabled', 'true', 'SYSTEM', '是否启用自动审批'),
('sso.role_mapping.ou=管理员', 'ADMIN', 'ROLE_MAPPING', '管理员角色映射'),
('sso.role_mapping.ou=运维', 'OPS', 'ROLE_MAPPING', '运维角色映射'),
('sso.role_mapping.ou=教师', 'TEACHER', 'ROLE_MAPPING', '教师角色映射'),
('sso.role_mapping.ou=学生', 'STUDENT', 'ROLE_MAPPING', '学生角色映射'),
('sso.role_mapping.ou=People', 'STUDENT', 'ROLE_MAPPING', 'CAS containerId 为 People 的用户映射为 STUDENT 角色');

-- V9__school_data_sync.sql

-- ============================================================================
-- Flyway Migration Script: 学校数据同步表结构
-- Version: V100
-- Description: 创建学校数据接入相关的所有表
-- Author: System
-- Date: 2026-04-09
-- ============================================================================

-- ============================================================================
-- 1. 基础数据层 (Foundation Layer)
-- ============================================================================

-- 1.1 行政班表
CREATE TABLE IF NOT EXISTS administrative_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    class_code VARCHAR(50) NOT NULL UNIQUE COMMENT '行政班编号 (XZBH)',
    class_name VARCHAR(100) NOT NULL COMMENT '行政班名称 (XZBMC)',
    class_short_name VARCHAR(50) COMMENT '行政班简称 (XZBJC)',
    college_code VARCHAR(50) NOT NULL COMMENT '学院编号 (YXBH)',
    college_name VARCHAR(100) NULL COMMENT '学院名称 (YXMC)',
    major_code VARCHAR(50) NOT NULL COMMENT '专业编号 (ZYBH)',
    major_name VARCHAR(100) NULL COMMENT '专业名称 (ZYMC)',
    grade VARCHAR(10) NOT NULL COMMENT '年级 (NJ)',
    class_type VARCHAR(20) COMMENT '班级类型 (BJLX)',
    head_teacher_code VARCHAR(50) COMMENT '班主任工号 (BZRGH)',
    head_teacher_name VARCHAR(50) COMMENT '班主任姓名 (BZRXM)',
    student_count INT DEFAULT 0 COMMENT '学生人数 (XSRS)',
    male_count INT DEFAULT 0 COMMENT '男生人数 (NSRS)',
    female_count INT DEFAULT 0 COMMENT '女生人数 (NSRS_NV)',
    class_status VARCHAR(20) NOT NULL COMMENT '班级状态 (BJZT)',
    establish_date DATE COMMENT '成立日期 (CLRQ)',
    graduate_date DATE COMMENT '毕业日期 (BYRQ)',
    remark VARCHAR(500) COMMENT '备注 (BZ)',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源 (api/etl)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_college (college_code),
    INDEX idx_major (major_code),
    INDEX idx_grade (grade),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政班基本信息表';

-- 1.2 学校课程表
CREATE TABLE IF NOT EXISTS school_course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    course_code VARCHAR(50) NOT NULL UNIQUE COMMENT '课程编号 (KCBH)',
    course_name VARCHAR(200) NOT NULL COMMENT '课程名称 (KCMC)',
    course_name_en VARCHAR(200) COMMENT '课程英文名称 (KCYWMC)',
    course_short_name VARCHAR(100) COMMENT '课程简称 (KCJC)',
    course_nature VARCHAR(50) NOT NULL COMMENT '课程性质 (KCXZ)',
    course_category VARCHAR(50) NOT NULL COMMENT '课程类别 (KCLB)',
    credits DECIMAL(3,1) NOT NULL COMMENT '学分 (XF)',
    total_hours INT NOT NULL COMMENT '总学时 (ZXS)',
    theory_hours INT COMMENT '理论学时 (LLXS)',
    experiment_hours INT COMMENT '实验学时 (SYXS)',
    practice_hours INT COMMENT '实践学时 (SJXS)',
    computer_hours INT COMMENT '上机学时 (SJXS_PC)',
    college_code VARCHAR(50) NOT NULL COMMENT '开课学院编号 (KKYXBH)',
    college_name VARCHAR(100) NOT NULL COMMENT '开课学院名称 (KKYXMC)',
    department_code VARCHAR(50) COMMENT '开课系部编号 (KKXBBH)',
    department_name VARCHAR(100) COMMENT '开课系部名称 (KKXBMC)',
    leader_code VARCHAR(50) COMMENT '课程负责人工号 (KCFZRGH)',
    leader_name VARCHAR(50) COMMENT '课程负责人姓名 (KCFZRXM)',
    assessment_method VARCHAR(50) NOT NULL COMMENT '考核方式 (KHFS)',
    is_bilingual VARCHAR(10) COMMENT '是否双语教学 (SFSYJX)',
    is_online VARCHAR(10) COMMENT '是否网络课程 (SFWLKC)',
    applicable_majors VARCHAR(500) COMMENT '适用专业 (SYZY)',
    prerequisite_codes VARCHAR(500) COMMENT '先修课程编号 (XXKCBH)',
    prerequisite_names VARCHAR(500) COMMENT '先修课程名称 (XXKCMC)',
    course_intro TEXT COMMENT '课程简介 (KCJJ)',
    teaching_objectives TEXT COMMENT '教学目标 (JXMB)',
    course_status VARCHAR(20) NOT NULL COMMENT '课程状态 (KCZT)',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_college (college_code),
    INDEX idx_nature (course_nature),
    INDEX idx_category (course_category),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学校课程基本信息表';

-- ============================================================================
-- 2. 教学组织层 (Teaching Organization Layer)
-- ============================================================================

-- 2.1 教学班表
CREATE TABLE IF NOT EXISTS teaching_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    class_id VARCHAR(50) NOT NULL UNIQUE COMMENT '教学班号 (JXBID)',
    class_name VARCHAR(100) COMMENT '教学班名称 (JXBMC)',
    course_code VARCHAR(50) NOT NULL COMMENT '课程编号 (KCBH)',
    course_name VARCHAR(200) NOT NULL COMMENT '课程名称 (KCMC)',
    teacher_id VARCHAR(50) NOT NULL COMMENT '授课教师工号 (JGH)',
    teacher_name VARCHAR(50) NOT NULL COMMENT '授课教师姓名 (XM)',
    academic_year VARCHAR(20) NOT NULL COMMENT '学年 (XNM)',
    semester INT NOT NULL COMMENT '学期 (XQM)',
    capacity INT NULL DEFAULT 0 COMMENT '班级容量',
    enrolled_count INT DEFAULT 0 COMMENT '已选人数',
    class_status VARCHAR(20) NULL DEFAULT 'ACTIVE' COMMENT '课程状态',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (course_code) REFERENCES school_course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_course (course_code),
    INDEX idx_teacher (teacher_id),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学班信息表';

-- 2.2 课程安排表
CREATE TABLE IF NOT EXISTS course_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    class_id VARCHAR(50) NULL COMMENT '教学班号 (JXBID)',
    day_of_week INT NULL COMMENT '星期几 (XQJ) 1-7',
    period VARCHAR(20) NULL COMMENT '节次 (JCS) 如"1-2"',
    week_range VARCHAR(50) NULL COMMENT '周次 (ZCD) 如"1-16周"',
    class_location VARCHAR(100) NULL COMMENT '上课地点 (CDMC)',
    academic_year VARCHAR(20) NOT NULL COMMENT '学年 (XNM)',
    semester INT NOT NULL COMMENT '学期 (XQM)',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_class_id (class_id),
    INDEX idx_day_period (day_of_week, period),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_class_academic (class_id, academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程安排信息表';

-- ============================================================================
-- 3. 选课与成绩层 (Enrollment & Grade Layer)
-- ============================================================================

-- 3.1 选课记录表
CREATE TABLE IF NOT EXISTS course_selection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    selection_id VARCHAR(50) NOT NULL UNIQUE COMMENT '选课记录ID',
    student_id VARCHAR(50) NOT NULL COMMENT '学号',
    student_name VARCHAR(100) NULL COMMENT '学生姓名',
    course_code VARCHAR(50) NOT NULL COMMENT '课程代码',
    course_name VARCHAR(200) COMMENT '课程名称（CDSP API不返回，可为空）',
    class_id VARCHAR(50) NOT NULL COMMENT '教学班号',
    teacher_id VARCHAR(50) NULL COMMENT '授课教师工号',
    teacher_name VARCHAR(50) NULL COMMENT '授课教师姓名',
    credits DECIMAL(3,1) COMMENT '学分（CDSP API不返回，可为空）',
    academic_year VARCHAR(20) NULL COMMENT '学年',
    semester INT NULL COMMENT '学期',
    class_time VARCHAR(100) COMMENT '上课时间',
    class_location VARCHAR(100) COMMENT '上课地点',
    selection_time DATETIME NULL COMMENT '选课时间',
    selection_status VARCHAR(20) NULL DEFAULT 'SELECTED' COMMENT '选课状态 SELECTED/DROPPED/COMPLETED',
    regular_grade DECIMAL(5,2) COMMENT '平时成绩',
    final_grade DECIMAL(5,2) COMMENT '期末成绩',
    total_grade DECIMAL(5,2) COMMENT '总评成绩',
    grade_point DECIMAL(3,2) COMMENT '绩点',
    attendance_count INT DEFAULT 0 COMMENT '出勤次数',
    absence_count INT DEFAULT 0 COMMENT '缺勤次数',
    grade_remark VARCHAR(500) COMMENT '成绩备注',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (course_code) REFERENCES school_course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_student (student_id),
    INDEX idx_course (course_code),
    INDEX idx_class (class_id),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_student_academic (student_id, academic_year, semester),
    INDEX idx_selection_status (selection_status),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课记录表';

-- 3.2 等级考试成绩表
CREATE TABLE IF NOT EXISTS level_exam_scores (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    student_id VARCHAR(50) NOT NULL COMMENT '学号',
    student_name VARCHAR(100) COMMENT '学生姓名（CDSP 不提供，可为空）',
    exam_type VARCHAR(50) COMMENT '考试类型（CDSP 不提供，从 examName 推断）',
    exam_name VARCHAR(100) NOT NULL COMMENT '考试名称',
    admission_ticket_no VARCHAR(30) COMMENT '准考证号',
    exam_date DATE NOT NULL COMMENT '考试时间',
    score DECIMAL(5,2) NOT NULL COMMENT '成绩',
    grade VARCHAR(20) COMMENT '等级',
    is_passed BOOLEAN COMMENT '是否通过（CDSP 不提供，从 score 推断）',
    passing_score DECIMAL(5,2) COMMENT '合格分数线',
    listening_score DECIMAL(5,2) COMMENT '听力成绩',
    reading_score DECIMAL(5,2) COMMENT '阅读成绩',
    writing_score DECIMAL(5,2) COMMENT '写作成绩',
    translation_score DECIMAL(5,2) COMMENT '翻译成绩',
    certificate_no VARCHAR(50) COMMENT '证书编号',
    certificate_date DATE COMMENT '发证日期',
    exam_location VARCHAR(100) COMMENT '考试地点',
    exam_room_no VARCHAR(20) COMMENT '考场号',
    seat_no VARCHAR(10) COMMENT '座位号',
    registration_date DATE COMMENT '报名时间',
    score_release_date DATE COMMENT '成绩发布时间',
    data_source VARCHAR(50) COMMENT '数据来源',
    remark VARCHAR(500) COMMENT '备注',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_student_exam (student_id, exam_type, exam_date),
    INDEX idx_student_id (student_id),
    INDEX idx_exam_type (exam_type),
    INDEX idx_exam_date (exam_date),
    INDEX idx_is_passed (is_passed),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等级考试成绩表';

-- 4. 教学计划层
CREATE TABLE IF NOT EXISTS teaching_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plan_id VARCHAR(30) NOT NULL UNIQUE COMMENT '计划编号',
    plan_name VARCHAR(200) NOT NULL COMMENT '计划名称',
    major_code VARCHAR(20) NOT NULL COMMENT '专业代码',
    major_name VARCHAR(100) NULL COMMENT '专业名称',
    grade VARCHAR(10) NOT NULL COMMENT '年级',
    schooling_length INT NOT NULL COMMENT '学制',
    degree_type VARCHAR(20) NOT NULL COMMENT '学位类型',
    education_level VARCHAR(20) NOT NULL COMMENT '培养层次',
    college_code VARCHAR(20) NOT NULL COMMENT '学院代码',
    college_name VARCHAR(100) NULL COMMENT '学院名称',
    total_credits DECIMAL(5,2) NOT NULL COMMENT '总学分要求',
    required_credits DECIMAL(5,2) NOT NULL COMMENT '必修学分',
    elective_credits DECIMAL(5,2) NOT NULL COMMENT '选修学分',
    practice_credits DECIMAL(5,2) COMMENT '实践学分',
    general_education_credits DECIMAL(5,2) COMMENT '通识教育学分',
    major_core_credits DECIMAL(5,2) COMMENT '专业核心学分',
    major_elective_credits DECIMAL(5,2) COMMENT '专业选修学分',
    training_objective TEXT COMMENT '培养目标',
    training_requirements TEXT COMMENT '培养要求',
    main_disciplines VARCHAR(200) COMMENT '主干学科',
    core_courses TEXT COMMENT '核心课程',
    plan_status VARCHAR(20) NOT NULL COMMENT '计划状态',
    effective_date DATE NOT NULL COMMENT '生效日期',
    expiry_date DATE COMMENT '失效日期',
    creator VARCHAR(50) COMMENT '制定人',
    reviewer VARCHAR(50) COMMENT '审核人',
    review_date DATE COMMENT '审核时间',
    version VARCHAR(20) COMMENT '版本号',
    revision_notes TEXT COMMENT '修订说明',
    attachment_path VARCHAR(500) COMMENT '附件路径',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_major_grade (major_code, grade),
    INDEX idx_plan_status (plan_status),
    INDEX idx_effective_date (effective_date),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学计划主表';

CREATE TABLE IF NOT EXISTS teaching_plan_courses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    detail_id VARCHAR(30) NOT NULL UNIQUE COMMENT '明细编号',
    plan_id VARCHAR(30) NOT NULL COMMENT '计划编号',
    course_code VARCHAR(20) NOT NULL COMMENT '课程代码',
    course_name VARCHAR(100) NOT NULL COMMENT '课程名称',
    course_type VARCHAR(20) NOT NULL COMMENT '课程类型',
    course_category VARCHAR(50) COMMENT '课程类别',
    credits DECIMAL(4,2) NOT NULL COMMENT '学分',
    hours INT NOT NULL COMMENT '学时',
    theory_hours INT COMMENT '理论学时',
    lab_hours INT COMMENT '实验学时',
    semester INT NOT NULL COMMENT '开课学期',
    assessment_method VARCHAR(20) COMMENT '考核方式',
    prerequisite_courses VARCHAR(200) COMMENT '先修课程',
    sort_order INT COMMENT '排序号',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_plan_id (plan_id),
    INDEX idx_course_code (course_code),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学计划课程明细表';

-- ============================================================================
-- 6. 辅助表
-- ============================================================================

-- 6.1 同步日志表
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
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX idx_data_source (`data_source`),
    INDEX idx_status (`status`),
    INDEX idx_sync_type (`sync_type`),
    INDEX idx_start_time (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步日志表';

-- 6.2 字段映射配置表
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

-- V10__user_and_text_updates.sql

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

-- V11__role_mapping_cleanup.sql

-- ═══════════════════════════════════════════════════════════════
-- V11: 清理 sso_config 表中无效的角色映射规则，修复被误映射的用户角色
-- 原先曾放在更后的版本号中，现前移并并入正式迁移链
--
-- 问题：sso_config 表中可能存在 config_key='sso.role_mapping.ou' 的脏规则，
-- strip 前缀后 key='ou'，由于旧版 RoleMappingService 使用 contains() 匹配，
-- 'ou' 匹配所有 LDAP DN（都包含 'ou'），导致所有用户被映射为同一角色。
-- 新版改用 DN 组件精确匹配，同时此迁移清理数据库中的脏规则。
-- ═══════════════════════════════════════════════════════════════

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

-- V12__school_data_and_videos.sql

-- ═══════════════════════════════════════════════════════════════
-- V12: 学校数据集成 + 视频管理表
-- 整合自: V12__school_data_integration.sql + V12__add_videos_table.sql
-- ═══════════════════════════════════════════════════════════════
-- ═══════════════════════════════════════════════════════════════
-- V12: 学校数据集成整合
-- 整合自: V12.1 (字段映射) + V13 (system_config seed)
--       + V14 (student_schedule 表) + V18 (功能开关 seed)
--       + V19 (字段映射) + V20 (API 认证 appId+appSecret)
--       + V20-align (2026-04-21 按 CDSP 官方 PDF 对齐 field_mappings)
--
-- 历史合并说明:
--   - 2026-04-19: 九个补丁 (V12.1/V13/V14/V16/V18/V19/V20) 首次合并入 V12
--   - 2026-04-21: 按官方 PDF 原文修正 field_mappings, 剔除 V12.1/V19 遗留
--     snake_case 冗余映射（FieldMappingServiceImpl 会自动将 target_field
--     转 camelCase, snake_case/camelCase 两套是等价重复）, 合并 V20-align
--   - 2026-05-05: 移除 V16 (course→school_course 重命名) 冗余代码，
--     V9 已正确创建 school_course 表，无需兼容性处理
--   - 2026-05-06: 吸收原 V15/V16 的学校数据 schema 修复，移除零散 fix 版本
--
-- 注意: Flyway 在本项目生产环境禁用 (application.yml flyway.enabled=false),
--       实际生产部署走 docker/mysql/init/04-apply-all-migrations.sql
--       CI test profile 启用 Flyway (application-test.yml)
--
-- 执行顺序:
--   Part 1: Schema 变更
--   Part 2: system_config seed
--   Part 3: field_mappings 数据（按 PDF 原文）
-- ═══════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════
-- Part 1.1: 创建 student_schedule 表 (原 V14 Part 1)
-- 存储学生个人课表数据 (/api/xskb)
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS student_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id VARCHAR(50) NOT NULL,
    student_name VARCHAR(100),
    class_id VARCHAR(50) NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    teacher_id VARCHAR(50),
    teacher_name VARCHAR(100),
    day_of_week TINYINT NULL,
    period VARCHAR(20) NULL,
    week_range VARCHAR(50),
    class_location VARCHAR(200),
    academic_year VARCHAR(20) NOT NULL,
    semester TINYINT NOT NULL,
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

-- ═══════════════════════════════════════════════════════════════
-- Part 2.1: 数据源配置种子 (原 V13)
-- NOTE: api_app_id / api_app_secret 在 Part 2.3 添加（V20）
-- ═══════════════════════════════════════════════════════════════
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

-- ═══════════════════════════════════════════════════════════════
-- Part 2.2: 功能开关 seed (原 V18)
-- ═══════════════════════════════════════════════════════════════
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'ops.school_data_browser', 'true', '运维端学校数据浏览功能（行政班、课程、教学班等）', 0),
('feature_toggle', 'ops.school_data_config', 'true', '运维端学校数据源配置功能（API/ETL切换、连接测试）', 0),
('feature_toggle', 'page_schedule', 'true', '学生端课表查看功能（从教务系统同步课表数据）', 0);

-- ═══════════════════════════════════════════════════════════════
-- Part 2.3: API 认证 appId + appSecret 签名 (原 V20)
-- 官方接口文档: sign = Base64( MD5( appId + appSecret + timestamp ) )
-- 请求头: appId / timestamp / sign
-- ═══════════════════════════════════════════════════════════════
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'api_app_id', '', 'API 应用 ID（由学校数据平台分配，用于签名认证）', 1),
('school_data', 'api_app_secret', '', 'API 应用 Secret（由学校数据平台分配，与 appId + timestamp 组合生成签名）', 1);

-- 清理旧部署遗留的 api_token（Bearer Token 认证已废弃；全新部署不会有此记录）
DELETE FROM `system_config`
WHERE `config_group` = 'school_data' AND `config_key` = 'api_token';

-- ═══════════════════════════════════════════════════════════════
-- Part 3: field_mappings 按 CDSP 官方 PDF 对齐 (原 V20-align)
--
-- 基于 9 份 cdsp.imau.edu.cn data-api/v2 官方接口文档原文复核（2026-04-21）：
--   行政班基本信息 / 课程基本信息 / 本专科生课程信息 / 课程安排信息 /
--   学生选课 / 本专科生等级考试成绩 / 教学计划信息 / 教师授课 / 学生课表
--
-- 配套代码:
--   - FieldMappingServiceImpl.applyMapping 大小写不敏感查表
--     (PDF 返回字段 3 个端点 UPPERCASE + 6 个 lowercase)
--   - SchoolDataSyncServiceImpl.upsertEntity: teaching_class 数据源
--     路由到 upsertSchoolCourse (bzkcxx 端点实际返回课程信息，非教学班)
--   - target_field 统一 camelCase, 匹配 Java 实体属性
--
-- 已知限制（非本 part 职责）:
--   - course_selection / teaching_plans 的上游字段仍可能不完整，当前基线
--     通过 DDL 放宽 + service 默认值兜底保证同步不因空值整批失败。
-- ═══════════════════════════════════════════════════════════════

-- Step 3.0: 清空 V12 历史遗留的错误 mappings（幂等）
DELETE FROM field_mappings WHERE data_source IN (
    'administrative_class', 'school_course', 'teaching_class',
    'course_schedule', 'course_selection', 'level_exam_scores',
    'teaching_plans', 'teacher_courses', 'student_schedule'
);

-- 1. administrative_class (cdsp_1710949911983374368 / 行政班基本信息)
-- PDF fields (UPPERCASE): XZBH, BJMC, XSLB, SSXYBM, SSXYMC, SSNJ, BZRGH, BZRXM, ZY, JBNY
-- 注意：CDSP API 可能返回中文字段名，需同时支持英文缩写和中文字段名
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'XZBH',         'class_code',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '行政班编号（英文缩写）'),
('administrative_class', '行政班编号',   'class_code',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '行政班编号（中文）'),
('administrative_class', 'BJMC',         'class_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '班级名称（英文缩写）'),
('administrative_class', '班级名称',     'class_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '班级名称（中文）'),
('administrative_class', 'XSLB',         'class_type',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '学生类别（英文缩写）'),
('administrative_class', '学生类别',     'class_type',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '学生类别（中文）'),
('administrative_class', 'SSXYBM',       'college_code',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4,  1, '所属学院编码（英文缩写）'),
('administrative_class', '所属学院编码', 'college_code',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4,  1, '所属学院编码（中文）'),
('administrative_class', 'SSXYMC',       'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '所属学院名称（英文缩写）'),
('administrative_class', '所属学院名称', 'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '所属学院名称（中文）'),
('administrative_class', 'SSNJ',         'grade',             'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '所属年级（英文缩写）'),
('administrative_class', '所属年级',     'grade',             'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '所属年级（中文）'),
('administrative_class', 'BZRGH',        'head_teacher_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '班主任工号（英文缩写）'),
('administrative_class', '班主任工号',   'head_teacher_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '班主任工号（中文）'),
('administrative_class', 'BZRXM',        'head_teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '班主任姓名（英文缩写）'),
('administrative_class', '班主任姓名',   'head_teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '班主任姓名（中文）'),
('administrative_class', 'ZY',           'major_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '专业名称（英文缩写）'),
('administrative_class', '专业名称',     'major_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '专业名称（中文）'),
('administrative_class', 'JBNY',         'establish_date',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 10, 1, '建班年月（英文缩写）'),
('administrative_class', '建班年月',     'establish_date',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 10, 1, '建班年月（中文）');

-- 2. school_course (cdsp_1715290945064353805 / 课程基本信息)
-- PDF fields (lowercase): kcxs, kcdm, kcxf, kcywmc, kcjj, kslx, kkyx, kcmc
-- 注意：CDSP API 可能返回中文字段名，需同时支持英文缩写和中文字段名
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('school_course', 'kcdm',       'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程代码（英文缩写）'),
('school_course', '课程代码',   'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程代码（中文）'),
('school_course', 'kcmc',       'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称（英文缩写）'),
('school_course', '课程名称',   'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称（中文）'),
('school_course', 'kcywmc',     'course_name_en',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称（英文缩写）'),
('school_course', '课程英文名称', 'course_name_en',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称（中文）'),
('school_course', 'kcxf',       'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分（英文缩写）'),
('school_course', '课程学分',   'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分（中文）'),
('school_course', 'kcxs',       'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时（英文缩写）'),
('school_course', '课程学时',   'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时（中文）'),
('school_course', 'kcjj',       'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介（英文缩写）'),
('school_course', '课程简介',   'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介（中文）'),
('school_course', 'kslx',       'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型（英文缩写）'),
('school_course', '考试类型',   'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型（中文）'),
('school_course', 'kkyx',       'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系（英文缩写）'),
('school_course', '开课院系',   'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系（中文）');

-- 3. teaching_class (cdsp_1721804030860152850 / 本专科生课程信息)
-- 此端点实际返回课程信息（非教学班），代码已路由到 upsertSchoolCourse
-- PDF fields (UPPERCASE): KCXS, KCXF, KKYX, KCJJ, KCYWMC, KCMC, KCH, KSLX
-- 注意：CDSP API 可能返回中文字段名，需同时支持英文缩写和中文字段名
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_class', 'KCH',          'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程号（英文缩写）'),
('teaching_class', '课程号',       'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程号（中文）'),
('teaching_class', 'KCMC',         'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称（英文缩写）'),
('teaching_class', '课程名称',     'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称（中文）'),
('teaching_class', 'KCYWMC',       'course_name_en',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称（英文缩写）'),
('teaching_class', '课程英文名称', 'course_name_en',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称（中文）'),
('teaching_class', 'KCXF',         'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分（英文缩写）'),
('teaching_class', '课程学分',     'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分（中文）'),
('teaching_class', 'KCXS',         'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时（英文缩写）'),
('teaching_class', '课程学时',     'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时（中文）'),
('teaching_class', 'KCJJ',         'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介（英文缩写）'),
('teaching_class', '课程简介',     'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介（中文）'),
('teaching_class', 'KSLX',         'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型（英文缩写）'),
('teaching_class', '考试类型',     'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型（中文）'),
('teaching_class', 'KKYX',         'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系（英文缩写）'),
('teaching_class', '开课院系',     'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系（中文）');

-- 4. course_schedule (cdsp_1715290377801515044 / 课程安排信息)
-- PDF fields (lowercase): xkkch, xn, xq, skxqj, skjc, kczc, skdd
-- xq 返回中文 "秋"/"春" varchar，target_type=STRING，service 层做中文→Integer 转换
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_schedule', 'xkkch',        'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '选课课号（作 class_id）'),
('course_schedule', '选课课号',     'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '选课课号（中文字段名）'),
('course_schedule', 'kcdm',         'course_code',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '课程代码（英文缩写）'),
('course_schedule', '课程代码',     'course_code',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '课程代码（中文字段名）'),
('course_schedule', 'kcmc',         'course_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '课程名称（英文缩写）'),
('course_schedule', '课程名称',     'course_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '课程名称（中文字段名）'),
('course_schedule', 'jsgh',         'teacher_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '教师工号（英文缩写）'),
('course_schedule', '教师工号',     'teacher_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '教师工号（中文字段名）'),
('course_schedule', 'jsxm',         'teacher_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '教师姓名（英文缩写）'),
('course_schedule', '教师姓名',     'teacher_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '教师姓名（中文字段名）'),
('course_schedule', 'kkyx',         'college_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '开课院系（英文缩写）'),
('course_schedule', '开课院系',     'college_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '开课院系（中文字段名）'),
('course_schedule', 'skxqj',        'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '上课星期几 1-7（英文缩写）'),
('course_schedule', '上课星期几',   'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '上课星期几 1-7（中文字段名）'),
('course_schedule', 'skjc',         'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '上课节次（英文缩写）'),
('course_schedule', '上课节次',     'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '上课节次（中文字段名）'),
('course_schedule', 'kczc',         'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '课程周次（描述性，英文缩写）'),
('course_schedule', '课程周次',     'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '课程周次（描述性，中文字段名）'),
('course_schedule', 'skdd',         'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '上课地点（英文缩写）'),
('course_schedule', '上课地点',     'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '上课地点（中文字段名）'),
('course_schedule', 'xn',           'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年（CDSP 单年如 "2024"，英文缩写）'),
('course_schedule', '学年',         'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年（中文字段名）'),
('course_schedule', 'xq',           'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学期中文 "秋"/"春"，service 层转 Integer（英文缩写）'),
('course_schedule', '学期',         'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学期（中文字段名）');

-- 5. course_selection (cdsp_1715294802871926794 / 学生选课)
-- PDF fields (UPPERCASE): XH, KSXZDM, KSXZMC, KCBH
-- 注意：PDF 只返回 4 个字段，远少于 CourseSelection 实体 NOT NULL 列数。
-- KCBH 在链式拉取与历史兼容中按”课程班号”使用，应统一落到 classId。
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_selection', 'XH',     'student_id',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('course_selection', 'KCBH',   'class_id',          'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程班号（作 class_id）'),
('course_selection', 'KSXZDM', 'selection_status',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '考试性质代码（作选课状态）'),
('course_selection', 'KSXZMC', 'class_time',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '考试性质名称（暂存 classTime）'),
('course_selection', 'XF',     'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '学分（兼容扩展返回）'),
('course_selection', '学分',   'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '学分（中文字段名）'),
('course_selection', 'XM',     'student_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '姓名（兼容扩展返回）'),
('course_selection', '姓名',   'student_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '姓名（中文字段名）');

-- 6. level_exam_scores (DS0076 / 本专科生等级考试成绩)
-- PDF fields (lowercase): xh, xhxm, xm, kskm, ksrq, fskscj
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', 'xh',     'student_id',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('level_exam_scores', 'xm',     'student_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2, 1, '姓名'),
('level_exam_scores', 'kskm',   'exam_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '考试科目（作考试名称）'),
('level_exam_scores', 'ksrq',   'exam_date',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 1, 4, 1, '考试日期'),
('level_exam_scores', 'fskscj', 'score',        'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 5, 1, '考试成绩（分数）');

-- 7. teaching_plans (cdsp_1715289655026466836 / 教学计划信息)
-- PDF fields (lowercase): zxs, skjszgh, kkdwdm, kkxq, cksm, ksfsdm, kkjhid, kkxn,
--   kcdm, kkxq_name, kkdwdm_name, ksfsmc
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_plans', 'kkjhid',      'plan_id',            'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '开课计划 ID（plan_id 主键）'),
('teaching_plans', 'kcdm',        'course_code',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2,  1, '课程代码'),
('teaching_plans', 'cksm',        'training_objective', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '参考书目（作培养目标）'),
('teaching_plans', 'zxs',         'total_credits',      'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4,  1, '总学时（近似总学分）'),
('teaching_plans', 'skjszgh',     'creator',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '授课教师职工号（作创建者）'),
('teaching_plans', 'kkdwdm',      'college_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '开课单位代码'),
('teaching_plans', 'kkdwdm_name', 'college_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '开课单位名称'),
('teaching_plans', 'kkxn',        'grade',              'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '开课学年（作年级）'),
('teaching_plans', 'kkxq_name',   'plan_name',          'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '开课学期名称（作计划名称）'),
('teaching_plans', 'ksfsdm',      'plan_status',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '考试方式代码（作计划状态）'),
('teaching_plans', 'ksfsmc',      'version',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '考试方式名称（作版本）');

-- 8. teacher_courses (cdsp_1715294645677801497 / 教师授课)
-- PDF fields (lowercase): rkjs, xn, xq, kcdm, kxh, rkjsh, kcmc, kcbh, sfzj, kcbbm, rkjsxm
-- 教师授课数据 upsert 到 TeachingClass 实体（service 代码既有逻辑）
-- 语义（按 PDF 返回示例核对 2026-04-21）:
--   kcdm = 课程代码（如 "594625"，课程本身编码）→ courseCode
--   kcbh = 课程班号（如 "51425977"，某次开班的全局唯一 ID）→ classId (TeachingClass 主键)
--   kxh  = 课序号（班内序号，通常与 kcdm 相同）→ 不入库
--   xq   = 学期，API 返回中文 "秋"/"春" varchar → target_type=STRING 先保留原值，
--          SchoolDataSyncServiceImpl.upsertTeachingClass 读取时再做中文→Integer 转换
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teacher_courses', 'kcbh',   'class_id',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程班号（作 class_id，全局唯一开班 ID）'),
('teacher_courses', 'kcdm',   'course_code',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程代码'),
('teacher_courses', 'kcmc',   'course_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '课程名称'),
('teacher_courses', 'rkjsh',  'teacher_id',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '任课教师工号'),
('teacher_courses', 'rkjsxm', 'teacher_name',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '任课教师姓名'),
('teacher_courses', 'xn',     'academic_year', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年（CDSP 单年格式如 "2024"）'),
('teacher_courses', 'xq',     'semester',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学期 varchar 中文 "秋"/"春"，service 层转 Integer'),
('teacher_courses', 'kcbbm',  'class_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '课程班别名（作班级展示名）');

-- 9. student_schedule (cdsp_1715294397274341377 / 学生课表)
-- PDF fields (lowercase): zcdm, skjszgh, jsmc, lsxykh, kcmc, js, skcd, djj, xqj,
--   xkkh, xh, xq, xn, zcap, kcbh
-- xq 返回中文 "秋"/"春" varchar，target_type=STRING，service 层做中文→Integer 转换
-- 注意：CDSP API 实际返回中文字段名（非英文缩写），需同时支持两种格式
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('student_schedule', '学号',           'student_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '学号（中文字段名）'),
('student_schedule', 'xh',            'student_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '学号（英文缩写）'),
('student_schedule', '选课课号',       'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '选课课号（作 class_id，中文字段名）'),
('student_schedule', 'xkkh',          'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '选课课号（作 class_id，英文缩写）'),
('student_schedule', '课程班号',       'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '课程班号（作 class_id，中文字段名）'),
('student_schedule', '课程编号',       'course_code',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '课程编号（中文字段名，非必填）'),
('student_schedule', 'kcbh',          'course_code',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '课程编号（英文缩写，非必填）'),
('student_schedule', '课程名称',       'course_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4,  1, '课程名称（中文字段名）'),
('student_schedule', 'kcmc',          'course_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4,  1, '课程名称（英文缩写）'),
('student_schedule', '上课教师职工号', 'teacher_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '上课教师职工号（中文字段名）'),
('student_schedule', 'skjszgh',       'teacher_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '上课教师职工号（英文缩写）'),
('student_schedule', '教师名称',       'teacher_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '教师名称（中文字段名）'),
('student_schedule', 'jsmc',          'teacher_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '教师名称（英文缩写）'),
('student_schedule', '星期几',         'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7,  1, '星期几 1-7（中文字段名）'),
('student_schedule', 'xqj',           'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7,  1, '星期几 1-7（英文缩写）'),
('student_schedule', '第几节',         'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8,  1, '第几节（中文字段名）'),
('student_schedule', 'djj',           'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8,  1, '第几节（英文缩写）'),
('student_schedule', '周次安排',       'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '周次安排（如 "1-9"，中文字段名）'),
('student_schedule', 'zcap',          'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '周次安排（如 "1-9"，英文缩写）'),
('student_schedule', '教室',           'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '教室（中文字段名）'),
('student_schedule', 'js',            'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '教室（英文缩写）'),
('student_schedule', '学年',           'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '学年（CDSP 单年如 "2024"，中文字段名）'),
('student_schedule', 'xn',            'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '学年（CDSP 单年如 "2024"，英文缩写）'),
('student_schedule', '学期',           'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 12, 1, '学期中文 "秋"/"春"，service 层转 Integer（中文字段名）'),
('student_schedule', 'xq',            'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 12, 1, '学期中文 "秋"/"春"，service 层转 Integer（英文缩写）');

-- ═══════════════════════════════════════════════════════════════
-- Part 4: 视频管理表
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS `videos` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(200) NOT NULL COMMENT '视频标题',
    `description` TEXT COMMENT '视频简介',
    `media_id` BIGINT DEFAULT NULL COMMENT '关联的媒体记录ID',
    `cover_media_id` BIGINT DEFAULT NULL COMMENT '封面媒体ID',
    `video_url` VARCHAR(500) DEFAULT NULL COMMENT '视频URL（外部链接时使用）',
    `cover_url` VARCHAR(500) DEFAULT NULL COMMENT '封面URL',
    `subtitle_content` LONGTEXT COMMENT '字幕内容（SRT/VTT格式）',
    `subtitle_content_zh` LONGTEXT COMMENT '中文字幕内容（SRT/VTT格式）',
    `user_id` BIGINT DEFAULT NULL COMMENT '上传者用户ID',
    `is_public` TINYINT DEFAULT 1 COMMENT '是否公开 0-私有 1-公开',
    `is_featured` TINYINT(1) DEFAULT 0 COMMENT '是否推荐',
    `featured_priority` INT DEFAULT 0 COMMENT '推荐优先级',
    `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔',
    `view_count` INT DEFAULT 0 COMMENT '浏览次数',
    `like_count` INT DEFAULT 0 COMMENT '收藏/点赞次数',
    `status` VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT '状态：DRAFT/PUBLISHED/ARCHIVED',
    `associated_text_id` BIGINT DEFAULT NULL COMMENT '关联的课文ID（用于收藏映射）',
    `transcription_status` VARCHAR(20) DEFAULT NULL COMMENT '转录状态：PENDING/PROCESSING/COMPLETED/FAILED',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_media_id` (`media_id`),
    KEY `idx_is_public` (`is_public`),
    KEY `idx_is_featured` (`is_featured`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_associated_text_id` (`associated_text_id`),
    KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频表';

SET @videos_subtitle_zh_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'videos'
      AND COLUMN_NAME = 'subtitle_content_zh'
);
SET @videos_subtitle_zh_sql = IF(
    @videos_subtitle_zh_exists = 0,
    'ALTER TABLE videos ADD COLUMN subtitle_content_zh LONGTEXT COMMENT ''中文字幕内容（SRT/VTT格式）'' AFTER subtitle_content',
    'SELECT 1'
);
PREPARE stmt FROM @videos_subtitle_zh_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE administrative_class
    MODIFY COLUMN college_name VARCHAR(100) NULL COMMENT '学院名称 (YXMC)',
    MODIFY COLUMN major_name VARCHAR(100) NULL COMMENT '专业名称 (ZYMC)';

ALTER TABLE teaching_plans
    MODIFY COLUMN college_name VARCHAR(100) NULL COMMENT '学院名称',
    MODIFY COLUMN major_name VARCHAR(100) NULL COMMENT '专业名称';

UPDATE administrative_class SET college_name = NULL WHERE college_name = '';
UPDATE administrative_class SET major_name = NULL WHERE major_name = '';
UPDATE teaching_plans SET college_name = NULL WHERE college_name = '';
UPDATE teaching_plans SET major_name = NULL WHERE major_name = '';

ALTER TABLE course_selection
    MODIFY COLUMN student_name VARCHAR(100) NULL COMMENT '学生姓名',
    MODIFY COLUMN credits DECIMAL(3,1) NULL COMMENT '学分（CDSP API不返回，可为空）',
    MODIFY COLUMN teacher_id VARCHAR(50) NULL COMMENT '授课教师工号',
    MODIFY COLUMN teacher_name VARCHAR(50) NULL COMMENT '授课教师姓名',
    MODIFY COLUMN academic_year VARCHAR(20) NULL COMMENT '学年',
    MODIFY COLUMN semester INT NULL COMMENT '学期',
    MODIFY COLUMN selection_time DATETIME NULL COMMENT '选课时间',
    MODIFY COLUMN selection_status VARCHAR(20) NULL DEFAULT 'SELECTED' COMMENT '选课状态 SELECTED/DROPPED/COMPLETED';

ALTER TABLE course_schedule
    MODIFY COLUMN class_id VARCHAR(50) NULL COMMENT '教学班号 (JXBID)',
    MODIFY COLUMN day_of_week INT NULL COMMENT '星期几 (XQJ) 1-7',
    MODIFY COLUMN period VARCHAR(20) NULL COMMENT '节次 (JCS) 如"1-2"',
    MODIFY COLUMN week_range VARCHAR(50) NULL COMMENT '周次 (ZCD) 如"1-16周"',
    MODIFY COLUMN class_location VARCHAR(100) NULL COMMENT '上课地点 (CDMC)';

ALTER TABLE teaching_class
    MODIFY COLUMN class_name VARCHAR(100) NULL COMMENT '教学班名称 (JXBMC)',
    MODIFY COLUMN capacity INT NULL DEFAULT 0 COMMENT '班级容量',
    MODIFY COLUMN class_status VARCHAR(20) NULL DEFAULT 'ACTIVE' COMMENT '课程状态';

DELETE cs
FROM course_schedule cs
LEFT JOIN teaching_class tc ON cs.class_id = tc.class_id
WHERE cs.class_id IS NOT NULL
  AND tc.class_id IS NULL;

DELETE csel
FROM course_selection csel
LEFT JOIN school_course sc ON csel.course_code = sc.course_code
WHERE csel.course_code IS NOT NULL
  AND sc.course_code IS NULL;

SET @course_schedule_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND CONSTRAINT_NAME = 'fk_course_schedule_teaching_class'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @course_schedule_drop_fk_sql = IF(
    @course_schedule_fk_exists > 0,
    'ALTER TABLE course_schedule DROP FOREIGN KEY fk_course_schedule_teaching_class',
    'SELECT 1'
);
PREPARE stmt FROM @course_schedule_drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @course_schedule_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND CONSTRAINT_NAME = 'course_schedule_ibfk_1'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @course_schedule_drop_fk_sql = IF(
    @course_schedule_fk_exists > 0,
    'ALTER TABLE course_schedule DROP FOREIGN KEY course_schedule_ibfk_1',
    'SELECT 1'
);
PREPARE stmt FROM @course_schedule_drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @course_schedule_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND CONSTRAINT_NAME = 'fk_course_schedule_teaching_class'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @course_schedule_add_fk_sql = IF(
    @course_schedule_fk_exists = 0,
    'ALTER TABLE course_schedule ADD CONSTRAINT fk_course_schedule_teaching_class FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE SET NULL ON UPDATE CASCADE',
    'SELECT 1'
);
PREPARE stmt FROM @course_schedule_add_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @course_selection_student_academic_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_selection'
      AND INDEX_NAME = 'idx_student_academic'
);
SET @course_selection_student_academic_sql = IF(
    @course_selection_student_academic_exists = 0,
    'CREATE INDEX idx_student_academic ON course_selection(student_id, academic_year, semester)',
    'SELECT 1'
);
PREPARE stmt FROM @course_selection_student_academic_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @course_schedule_class_academic_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND INDEX_NAME = 'idx_class_academic'
);
SET @course_schedule_class_academic_sql = IF(
    @course_schedule_class_academic_exists = 0,
    'CREATE INDEX idx_class_academic ON course_schedule(class_id, academic_year, semester)',
    'SELECT 1'
);
PREPARE stmt FROM @course_schedule_class_academic_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


UPDATE field_mappings
SET is_active = 0
WHERE data_source = 'administrative_class'
  AND source_field IN ('SSXYBM', 'SSXYMC', '所属学院编码', '所属学院名称');

INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'YXDM',       'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '学院代码（英文缩写）'),
('administrative_class', '学院代码',   'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '学院代码（中文）'),
('administrative_class', 'YXMC',       'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '学院名称（英文缩写）'),
('administrative_class', '学院名称',   'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '学院名称（中文）'),
('administrative_class', 'ZYDM',       'major_code',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '专业代码（英文缩写）'),
('administrative_class', '专业代码',   'major_code',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '专业代码（中文）'),
('teaching_plans', 'zybh', 'major_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 12, 1, '专业编号'),
('teaching_plans', 'zymc', 'major_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 13, 1, '专业名称');

-- V13__feature_toggles_and_ai_config.sql

-- ═══════════════════════════════════════════════════════════════
-- V13: 功能开关扩展 + AI 配置增强
-- 整合自: V14 (expand_feature_toggles) + V16 (ai_qa_recovery_and_hiagent_config)
--       + V17 (add_page_word_recite_toggle) + V18 (ai_provider_protocol)
-- ═══════════════════════════════════════════════════════════════

-- V13: 扩展 feature_toggle 覆盖学生端全量页面 + 管理员模块分级
-- 背景：
--   1. 原 V7 只 seed 了 11 个学生页 toggle，page_scores / page_course_selections 在代码中引用
--      但 DB 未 seed，导致 toggle 失效（默认展示）
--   2. 新增 page_courses / page_text_learning 以支持学生端 Layout 全覆盖
--   3. 新增 9 个 admin.* toggle 支持管理员模块级粒度控制
-- 与 V7/V12 关系：V7 只覆盖部分学生页；V12 添加学校数据相关；V14 补齐剩余

SET NAMES utf8mb4;

-- ─────────────────────────────────────────────
-- 学生端 4 个缺失 toggle（补齐全 15 页覆盖）
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_courses',           'false', '学生端-课程学习页（开发中）',   0),
  ('feature_toggle', 'page_scores',            'true',  '学生端-等级考试成绩页',         0),
  ('feature_toggle', 'page_course_selections', 'true',  '学生端-我的选课页',             0),
  ('feature_toggle', 'page_text_learning',     'true',  '学生端-课文学习子页',           0),
  ('feature_toggle', 'page_schedule',          'true',  '学生端-我的课表页',             0);

-- ─────────────────────────────────────────────
-- 管理员 9 个模块 toggle（分级控制）
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
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
-- 学生端补充页面 toggle
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_videos',            'true',  '学生端-视频学习页',             0),
  ('feature_toggle', 'page_video_player',      'true',  '学生端-视频播放器页',           0),
  ('feature_toggle', 'page_interactive_text',  'true',  '学生端-交互式课文页',           0),
  ('feature_toggle', 'page_vocabulary',        'true',  '学生端-词汇学习页',             0),
  ('feature_toggle', 'page_ai_qa',             'true',  '学生端-AI问答页',               0),
  ('feature_toggle', 'page_articles',          'true',  '学生端-文章阅读页',             0),
  ('feature_toggle', 'page_user_texts',        'true',  '学生端-我的课文页',             0);

-- ─────────────────────────────────────────────
-- 教师端页面 toggle
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_teacher_dashboard',   'true', '教师端-仪表盘',         0),
  ('feature_toggle', 'page_teacher_courses',     'true', '教师端-课程管理',       0),
  ('feature_toggle', 'page_teacher_my_courses',  'true', '教师端-我的课程',       0),
  ('feature_toggle', 'page_teacher_classes',     'true', '教师端-班级管理',       0),
  ('feature_toggle', 'page_teacher_assignments', 'true', '教师端-作业管理',       0);

-- ─────────────────────────────────────────────
-- 管理员端补充页面 toggle
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_admin_videos',      'true', '管理员-视频管理',       0),
  ('feature_toggle', 'page_admin_backups',     'true', '管理员-备份管理',       0),
  ('feature_toggle', 'page_admin_monitoring',  'true', '管理员-系统监控',       0);

-- ─────────────────────────────────────────────
-- 运维端页面 toggle
-- ─────────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_ops_monitoring',           'true', '运维端-系统监控',           0),
  ('feature_toggle', 'page_ops_ai_providers',         'true', '运维端-AI提供商管理',       0),
  ('feature_toggle', 'page_ops_feature_toggles',      'true', '运维端-功能开关管理',       0),
  ('feature_toggle', 'page_ops_school_data_config',   'true', '运维端-学校数据配置',       0),
  ('feature_toggle', 'page_ops_school_data_browser',  'true', '运维端-学校数据浏览器',     0),
  ('feature_toggle', 'page_ops_update',               'true', '运维端-系统更新',           0);

-- ═══════════════════════════════════════════════════════════════
-- Part 2: AI 问答恢复 + HiAgent 凭证配置
-- ═══════════════════════════════════════════════════════════════

--      或 feature_override 里有对 page_ai_qa 的禁用规则 → 路由守卫拦截 → "进不去"
--   2. HiAgent 改写/问答凭证原本只通过 application.yml + 环境变量注入，
--      违反"所有配置前端可配"的运维诉求 → 把 4+1 个 HiAgent 凭证迁到 system_config.ai_config
--      后端 HiAgentServiceImpl 读值优先 DB，回退到 @Value（环境变量）
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 恢复 page_ai_qa 开关为 true（无论之前是何值）
--    并清除 feature_override 里的禁用覆盖
-- ────────────────────────────────────────
UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key`   = 'page_ai_qa';

-- 若历史未 seed，补 seed（INSERT IGNORE 保持幂等）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES ('feature_toggle', 'page_ai_qa', 'true', '学生端-AI问答页', 0);

-- 清除所有对 page_ai_qa 的禁用覆盖（tag/user scope）
DELETE FROM `feature_override` WHERE `feature_key` = 'page_ai_qa';

-- ────────────────────────────────────────
-- 2. HiAgent 凭证 seed 到 ai_config group（is_secret=1 for keys）
--    值默认为空字符串，表示"尚未从前端配置，使用 application.yml 环境变量"
--    前端面板写入 DB 后，HiAgentServiceImpl 会优先读 DB 值
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('ai_config', 'hiagent_api_url',    '', 'HiAgent API Base URL（留空则使用环境变量 HIAGENT_API_URL）',         0),
  ('ai_config', 'hiagent_api_key',    '', 'HiAgent 改写应用 API Key（留空则使用环境变量 HIAGENT_API_KEY）',      1),
  ('ai_config', 'hiagent_app_id',     '', 'HiAgent 改写应用 App ID（留空则使用环境变量 HIAGENT_APP_ID）',        0),
  ('ai_config', 'hiagent_qa_api_key', '', 'HiAgent 问答应用 API Key（必须与改写凭证不同；留空则用 HIAGENT_QA_API_KEY）', 1),
  ('ai_config', 'hiagent_qa_app_id',  '', 'HiAgent 问答应用 App ID（必须与改写凭证不同；留空则用 HIAGENT_QA_APP_ID）',  0);

-- ═══════════════════════════════════════════════════════════════
-- Part 3: 补齐背单词页 toggle
-- ═══════════════════════════════════════════════════════════════

--   toggleKey = 'page_word_recite'，但该键从未 seed 到
--   system_config.feature_toggle，导致运维面板"全局开关"列表缺少
--   "背单词"条目 —— 开关与页面无法一一对应。
--
--   同时 router/index.js 中 /student/word-recite 路由守卫缺失
--   featureKey，本迁移配合前端补齐后，达成"开关关闭 → 导航隐藏 +
--   快捷入口隐藏 + 直接访问被拦截"三层联动。
-- ═══════════════════════════════════════════════════════════════

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES ('feature_toggle', 'page_word_recite', 'true', '学生端-背单词页', 0);

-- 若历史中存在但描述为空，统一规范化描述
UPDATE `system_config`
SET `description` = '学生端-背单词页'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` = 'page_word_recite'
  AND (`description` IS NULL OR `description` = '');

-- ═══════════════════════════════════════════════════════════════
-- Part 4: AI 服务商协议类型字段
-- ═══════════════════════════════════════════════════════════════


SET @ai_provider_protocol_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_provider' AND COLUMN_NAME = 'protocol'
);
SET @ai_provider_protocol_sql = IF(
    @ai_provider_protocol_exists = 0,
    'ALTER TABLE `ai_provider` ADD COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT ''openai_compatible'' AFTER `service`',
    'SELECT 1'
);
PREPARE stmt FROM @ai_provider_protocol_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `ai_provider`
    MODIFY COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible'
        COMMENT '协议类型: openai_compatible | hiagent';


INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'student.lazy_course_selections', 'true', '学生端 Lazy 选课记录同步（陈旧度阈值 1 天）', 0),
  ('feature_toggle', 'teacher.school_data_enabled', 'true', '教师端学校数据功能（课表/选课/成绩）', 0);

-- V14__enhancements.sql

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
    college_name VARCHAR(100) NULL COMMENT '学院名称（CDSP kkyx）',
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
    college_name VARCHAR(100) NULL COMMENT '学院（来自链式触发学院）',
    college_code VARCHAR(50) COMMENT '学院代码（预留）',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    last_schedule_sync_time DATETIME COMMENT '上次课表同步时间',
    sync_source VARCHAR(20) NOT NULL DEFAULT 'SCHOOL_API' COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_college (college_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDSP 同步的教师主数据';

ALTER TABLE school_student
    MODIFY COLUMN college_name VARCHAR(100) NULL COMMENT '学院名称（CDSP kkyx）';

ALTER TABLE school_teacher
    MODIFY COLUMN college_name VARCHAR(100) NULL COMMENT '学院（来自链式触发学院）';

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
('feature_toggle', 'ops.school_data_chain_fetch', 'true', '运维端按学院链式拉取功能开关', 0),
('feature_toggle', 'student.lazy_data_sync', 'true', '学生端按需 Lazy 同步四六级/课表', 0),
('feature_toggle', 'sso.auto_grant_student', 'false', 'SSO 学号匹配 school_student 自动授权 STUDENT', 0),
('feature_toggle', 'sso.auto_grant_teacher', 'false', 'SSO 工号匹配 school_teacher 自动授权 TEACHER', 0),
('school_data', 'sync_college_chain_cron', '0 30 3 * * ?', '按学院链式刷新 cron 表达式', 0),
('school_data', 'sync_college_chain_colleges', '外国语学院', '链式刷新白名单（逗号分隔学院名）', 0),
('school_data', 'sync_college_chain_match_mode', 'eq', '学院名匹配模式（eq=精确 / contains=包含）', 0);

DELETE FROM `system_config`
WHERE `config_group` = 'feature_toggle'
  AND `config_key` IN (
      'ops_school_data_chain_fetch',
      'sso_auto_grant_student',
      'sso_auto_grant_teacher',
      'student_lazy_data_sync',
      'page_user_tests'
  );

UPDATE `system_config`
SET `description` = CASE `config_key`
        WHEN 'ops.school_data_browser' THEN '运维端学校数据浏览功能（行政班、课程、教学班等）'
        WHEN 'ops.school_data_config' THEN '运维端学校数据源配置功能（API/ETL切换、连接测试）'
        WHEN 'page_schedule' THEN '学生端-我的课表页'
        WHEN 'teacher.school_data_enabled' THEN '教师端学校数据功能（课表/选课/成绩）'
        WHEN 'ops.school_data_chain_fetch' THEN '运维端按学院链式拉取功能开关'
        WHEN 'student.lazy_data_sync' THEN '学生端按需 Lazy 同步四六级/课表'
        WHEN 'sso.auto_grant_student' THEN 'SSO 学号匹配 school_student 自动授权 STUDENT'
        WHEN 'sso.auto_grant_teacher' THEN 'SSO 工号匹配 school_teacher 自动授权 TEACHER'
        ELSE `description`
    END,
    `config_value` = CASE `config_key`
        WHEN 'ops.school_data_browser' THEN 'true'
        WHEN 'ops.school_data_config' THEN 'true'
        WHEN 'page_schedule' THEN 'true'
        WHEN 'ops.school_data_chain_fetch' THEN 'true'
        ELSE `config_value`
    END
WHERE `config_group` = 'feature_toggle'
  AND `config_key` IN (
      'ops.school_data_browser',
      'ops.school_data_config',
      'page_schedule',
      'teacher.school_data_enabled',
      'ops.school_data_chain_fetch',
      'student.lazy_data_sync',
      'sso.auto_grant_student',
      'sso.auto_grant_teacher'
  );

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

-- ═══════════════════════════════════════════════════════════════
-- V23: 内容标签表（管理员预设）
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS `content_tags` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `color` VARCHAR(20) DEFAULT NULL COMMENT '标签颜色（如 #409EFF）',
    `sort_order` INT DEFAULT 0 COMMENT '排序号，值越小越靠前',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_sort_order` (`sort_order`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容标签表（管理员预设）';

-- 预置常用标签
INSERT IGNORE INTO `content_tags` (`name`, `color`, `sort_order`) VALUES
    ('日常对话', '#67C23A', 1),
    ('商务英语', '#409EFF', 2),
    ('学术英语', '#E6A23C', 3),
    ('科技文章', '#909399', 4),
    ('新闻资讯', '#F56C6C', 5),
    ('文学小说', '#9B59B6', 6);

-- ════════════════════════════════════════════════════════════════

-- V21__add_display_fields_to_teacher_student_binding.sql
-- 为 teacher_student_binding 表添加显示字段（教师姓名、学生姓名、学院名称、课程名称）

DROP PROCEDURE IF EXISTS add_teacher_student_binding_display_fields$$
CREATE PROCEDURE add_teacher_student_binding_display_fields()
BEGIN
    -- Add teacher_name column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'teacher_student_binding'
          AND COLUMN_NAME = 'teacher_name'
    ) THEN
        ALTER TABLE teacher_student_binding
            ADD COLUMN teacher_name VARCHAR(100) NULL COMMENT '教师姓名' AFTER teacher_id;
    END IF;

    -- Add student_name column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'teacher_student_binding'
          AND COLUMN_NAME = 'student_name'
    ) THEN
        ALTER TABLE teacher_student_binding
            ADD COLUMN student_name VARCHAR(100) NULL COMMENT '学生姓名' AFTER student_id;
    END IF;

    -- Add college_name column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'teacher_student_binding'
          AND COLUMN_NAME = 'college_name'
    ) THEN
        ALTER TABLE teacher_student_binding
            ADD COLUMN college_name VARCHAR(100) NULL COMMENT '学院名称' AFTER student_id;
    END IF;

    -- Add course_name column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'teacher_student_binding'
          AND COLUMN_NAME = 'course_name'
    ) THEN
        ALTER TABLE teacher_student_binding
            ADD COLUMN course_name VARCHAR(200) NULL COMMENT '课程名称' AFTER course_code;
    END IF;

    -- Add index on college_name
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'teacher_student_binding'
          AND INDEX_NAME = 'idx_college_name'
    ) THEN
        CREATE INDEX idx_college_name ON teacher_student_binding(college_name);
    END IF;
END$$

CALL add_teacher_student_binding_display_fields()$$
DROP PROCEDURE IF EXISTS add_teacher_student_binding_display_fields$$

-- ════════════════════════════════════════════════════════════════

-- V16__enable_sso_auto_grant.sql
-- 默认开启 SSO 自动授权功能开关

UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` IN ('sso.auto_grant_student', 'sso.auto_grant_teacher');

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'sso.auto_grant_student', 'true', 'SSO 学号匹配 school_student 自动授权 STUDENT', 0),
('feature_toggle', 'sso.auto_grant_teacher', 'true', 'SSO 工号匹配 school_teacher 自动授权 TEACHER', 0);

-- ════════════════════════════════════════════════════════════════

-- V17__add_reading_count.sql
-- 为 user_text_reads 表添加阅读次数字段，为 users 表添加阅读统计字段

DROP PROCEDURE IF EXISTS _add_reading_count_cols$$
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

CALL _add_reading_count_cols()$$
DROP PROCEDURE IF EXISTS _add_reading_count_cols$$

-- ════════════════════════════════════════════════════════════════

-- V18__add_texts_read_count.sql
-- 为 texts 表添加 read_count 字段（修复 Entity 与 DB Schema 不一致）

DROP PROCEDURE IF EXISTS _add_texts_read_count$$
CREATE PROCEDURE _add_texts_read_count()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'texts'
          AND COLUMN_NAME = 'read_count'
    ) THEN
        ALTER TABLE texts ADD COLUMN read_count INT NOT NULL DEFAULT 0 COMMENT '阅读次数' AFTER like_count;
    END IF;
END$$

CALL _add_texts_read_count()$$
DROP PROCEDURE IF EXISTS _add_texts_read_count$$

-- ════════════════════════════════════════════════════════════════


-- V22__fix_reading_count_columns.sql
-- 补齐 read_count 相关字段（V17 遗漏 texts.read_count，且历史 DB 未应用 V17 的 user_text_reads 部分）

DROP PROCEDURE IF EXISTS _fix_reading_count_cols$$
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
END$$

CALL _fix_reading_count_cols()$$
DROP PROCEDURE IF EXISTS _fix_reading_count_cols$$

-- ════════════════════════════════════════════════════════════════


-- V26__text_exercises_table.sql
-- 补建 text_exercises 表（历史手动建表，从未纳入迁移脚本）
CREATE TABLE IF NOT EXISTS `text_exercises` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `text_id` BIGINT NOT NULL COMMENT '课文ID',
    `question` TEXT NOT NULL COMMENT '题目内容',
    `exercise_type` ENUM('MULTIPLE_CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'SHORT_ANSWER') DEFAULT 'MULTIPLE_CHOICE' COMMENT '习题类型',
    `order_num` INT DEFAULT 1 COMMENT '显示顺序',
    `created_by` BIGINT NOT NULL COMMENT '创建者ID',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (`text_id`) REFERENCES `texts`(`id`) ON DELETE CASCADE,
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_exercise_type` (`exercise_type`),
    INDEX `idx_order_num` (`order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课文习题表';

-- V23__ai_quiz_support.sql
-- AI 出题功能支持：text_exercises 加字段 + 新建 ai_quiz_session 表

DROP PROCEDURE IF EXISTS _ai_quiz_support$$
CREATE PROCEDURE _ai_quiz_support()
BEGIN
    -- text_exercises 表添加新字段
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_exercises'
          AND COLUMN_NAME = 'question_type'
    ) THEN
        ALTER TABLE text_exercises ADD COLUMN question_type VARCHAR(50) DEFAULT 'MULTIPLE_CHOICE' COMMENT '题目类型';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_exercises'
          AND COLUMN_NAME = 'analysis'
    ) THEN
        ALTER TABLE text_exercises ADD COLUMN analysis TEXT COMMENT '题目解析';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_exercises'
          AND COLUMN_NAME = 'distractor_analysis'
    ) THEN
        ALTER TABLE text_exercises ADD COLUMN distractor_analysis JSON COMMENT '干扰项分析';
    END IF;

    -- AI 出题会话表
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'ai_quiz_session'
    ) THEN
        CREATE TABLE ai_quiz_session (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            text_id BIGINT NOT NULL COMMENT '文章ID',
            difficulty VARCHAR(50) COMMENT '难度级别',
            passage_analysis JSON COMMENT '文章分析',
            quality_metrics JSON COMMENT '质量指标',
            question_count INT DEFAULT 0 COMMENT '生成题目数量',
            ai_service VARCHAR(50) COMMENT 'AI服务类型',
            ai_model VARCHAR(100) COMMENT 'AI模型名称',
            created_by BIGINT COMMENT '创建者ID',
            created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            INDEX idx_text_id (text_id),
            INDEX idx_created_by (created_by)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI出题会话';
    END IF;
END$$

CALL _ai_quiz_support()$$
DROP PROCEDURE IF EXISTS _ai_quiz_support$$

-- V24__hiagent_quiz_credentials.sql
-- HiAgent 出题凭证配置项（独立于改写/问答凭证）

INSERT INTO system_config (config_group, config_key, config_value, description, is_secret)
VALUES
  ('ai_config', 'hiagent_quiz_api_key', '', 'HiAgent 出题应用 API Key（独立于改写/问答凭证）', 1),
  ('ai_config', 'hiagent_quiz_app_id', '', 'HiAgent 出题应用 App ID（独立于改写/问答凭证）', 0)
ON DUPLICATE KEY UPDATE description = VALUES(description);


-- V25__exercise_options_table.sql
-- 补建 exercise_options 表（AI 出题选项存储，V23 遗漏）

CREATE TABLE IF NOT EXISTS `exercise_options` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `exercise_id` BIGINT NOT NULL COMMENT '习题ID',
  `option_content` VARCHAR(500) NOT NULL COMMENT '选项内容',
  `option_key` VARCHAR(10) NOT NULL COMMENT '选项标识(A, B, C, D等)',
  `is_correct` TINYINT(1) DEFAULT '0' COMMENT '是否正确答案',
  `order_num` INT DEFAULT '1' COMMENT '显示顺序',
  `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exercise_option` (`exercise_id`, `option_key`),
  KEY `idx_exercise_id` (`exercise_id`),
  CONSTRAINT `exercise_options_ibfk_1` FOREIGN KEY (`exercise_id`) REFERENCES `text_exercises` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='习题选项表';

-- V27__ai_conversation_persistence.sql
-- AI 对话持久化与分享

CREATE TABLE IF NOT EXISTS ai_conversations (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(200) COMMENT '对话标题',
    message_count INT         DEFAULT 0,
    created_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      DEFAULT 0,
    INDEX idx_user_time (user_id, updated_time),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS ai_messages (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    role            ENUM('user','assistant') NOT NULL,
    content         TEXT         NOT NULL,
    selected_text   TEXT,
    article_context TEXT,
    created_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id, created_time),
    FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS conversation_shares (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    share_token     VARCHAR(64)  UNIQUE NOT NULL,
    access_password VARCHAR(128),
    expires_at      DATETIME,
    view_count      INT          DEFAULT 0,
    created_by      BIGINT       NOT NULL,
    created_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (share_token),
    INDEX idx_conversation (conversation_id),
    FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- ═══════════════════════════════════════════════════════════════
-- V28__collaborative_annotations.sql
-- 协作式互动阅读 — 公共批注 + 点赞
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS text_annotations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    text_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    selected_text TEXT NOT NULL,
    note TEXT NOT NULL,
    upvote_count INT DEFAULT 0,
    deleted TINYINT DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_text_id (text_id),
    INDEX idx_user_id (user_id),
    INDEX idx_text_deleted (text_id, deleted),
    CONSTRAINT fk_ann_text FOREIGN KEY (text_id) REFERENCES texts(id) ON DELETE CASCADE,
    CONSTRAINT fk_ann_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS annotation_upvotes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    annotation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ann_user (annotation_id, user_id),
    CONSTRAINT fk_upvote_ann FOREIGN KEY (annotation_id) REFERENCES text_annotations(id) ON DELETE CASCADE,
    CONSTRAINT fk_upvote_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════
-- V29__annotation_visibility.sql
-- 批注可见性 — 1=公开（默认），0=仅自己可见
-- 注: MySQL 8 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS（仅 MariaDB），
--     故改用 information_schema + 动态 SQL 的幂等模式。
-- ═══════════════════════════════════════════════════════════════
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'text_annotations'
                     AND COLUMN_NAME = 'visibility');
SET @col_sql = IF(@col_exists = 0,
    'ALTER TABLE text_annotations ADD COLUMN visibility TINYINT NOT NULL DEFAULT 1 COMMENT ''可见性: 1=公开, 0=仅自己''',
    'SELECT 1');
PREPARE stmt FROM @col_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- V34__media_transcoding.sql
-- media 表新增异步转码字段（移动端 AV1/HEVC/VP9 → H.264 流水线）

DROP PROCEDURE IF EXISTS _add_col_media_transcode$$
CREATE PROCEDURE _add_col_media_transcode()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema=DATABASE() AND table_name='media'
                     AND column_name='transcode_status') THEN
        ALTER TABLE media ADD COLUMN transcode_status VARCHAR(20)
            NOT NULL DEFAULT 'SKIPPED'
            COMMENT '转码状态: SKIPPED|PENDING|PROCESSING|SUCCESS|FAILED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema=DATABASE() AND table_name='media'
                     AND column_name='transcode_error') THEN
        ALTER TABLE media ADD COLUMN transcode_error VARCHAR(500) NULL
            COMMENT '转码失败原因（最后 500 字 stderr 摘要）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema=DATABASE() AND table_name='media'
                     AND column_name='original_codec') THEN
        ALTER TABLE media ADD COLUMN original_codec VARCHAR(50) NULL
            COMMENT '上传时探测到的原始视频编码（av1/hevc/h264/vp9 等）';
    END IF;
END$$
CALL _add_col_media_transcode()$$
DROP PROCEDURE IF EXISTS _add_col_media_transcode$$

DROP PROCEDURE IF EXISTS _add_idx_media_transcode$$
CREATE PROCEDURE _add_idx_media_transcode()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE table_schema=DATABASE() AND table_name='media'
                     AND index_name='idx_transcode_status') THEN
        ALTER TABLE media ADD INDEX idx_transcode_status (transcode_status);
    END IF;
END$$
CALL _add_idx_media_transcode()$$
DROP PROCEDURE IF EXISTS _add_idx_media_transcode$$

-- V33__ai_feedback.sql
-- AI 用户反馈表

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI用户反馈表';


-- ────────────────────────────────────────
-- student_exercise_answers（旧答题记录表，V26 text_exercises 的配套表）
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `student_exercise_answers` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `exercise_id` BIGINT NOT NULL,
    `student_id` BIGINT NOT NULL,
    `assignment_id` BIGINT NULL,
    `answer_content` TEXT NULL,
    `is_correct` TINYINT(1) NULL,
    `answer_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
    `time_spent_seconds` INT NULL,
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_sea_student` (`student_id`),
    INDEX `idx_sea_exercise` (`exercise_id`),
    INDEX `idx_sea_assignment` (`assignment_id`),
    CONSTRAINT `fk_sea_student` FOREIGN KEY (`student_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_sea_exercise` FOREIGN KEY (`exercise_id`) REFERENCES `text_exercises`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_sea_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignments`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V30__student_ai_quiz_records.sql
-- 学生 AI 题做题记录（两表设计）

CREATE TABLE IF NOT EXISTS `student_ai_quiz_attempts` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '尝试ID',
    `student_id`    BIGINT NOT NULL COMMENT '学生ID',
    `text_id`       BIGINT NOT NULL COMMENT '文章ID',
    `session_id`    BIGINT NULL     COMMENT 'AI 出题会话ID',
    `assignment_id` BIGINT NULL     COMMENT '任务ID',
    `total_questions`    INT          NOT NULL DEFAULT 0    COMMENT '题目总数',
    `correct_count`      INT          NOT NULL DEFAULT 0    COMMENT '答对数',
    `score`              DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '得分百分比',
    `total_time_seconds` INT          NOT NULL DEFAULT 0    COMMENT '总用时(秒)',
    `submitted_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `created_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_aiqa_student`     (`student_id`),
    INDEX `idx_aiqa_text`        (`text_id`),
    INDEX `idx_aiqa_student_text`(`student_id`, `text_id`),
    INDEX `idx_aiqa_submitted`   (`submitted_time`),
    INDEX `idx_aiqa_session`     (`session_id`),
    CONSTRAINT `fk_aiqa_student` FOREIGN KEY (`student_id`)    REFERENCES `users`(`id`)            ON DELETE CASCADE,
    CONSTRAINT `fk_aiqa_text`    FOREIGN KEY (`text_id`)       REFERENCES `texts`(`id`)            ON DELETE CASCADE,
    CONSTRAINT `fk_aiqa_session` FOREIGN KEY (`session_id`)    REFERENCES `ai_quiz_session`(`id`)  ON DELETE SET NULL,
    CONSTRAINT `fk_aiqa_asn`     FOREIGN KEY (`assignment_id`) REFERENCES `assignments`(`id`)      ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `student_ai_quiz_records` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '明细ID',
    `attempt_id` BIGINT NOT NULL COMMENT '做题尝试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `text_id`    BIGINT NOT NULL COMMENT '文章ID',
    `exercise_id` BIGINT NULL COMMENT '题目ID',
    `question_snapshot`  TEXT        NOT NULL COMMENT '题干快照',
    `options_snapshot`   JSON        NULL     COMMENT '选项快照',
    `correct_answer`     VARCHAR(50) NULL     COMMENT '正确答案',
    `analysis_snapshot`  TEXT        NULL     COMMENT '解析快照',
    `user_answer`        VARCHAR(50) NOT NULL COMMENT '学生答案',
    `is_correct`         TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否正确',
    `time_spent_seconds` INT         NULL     COMMENT '本题用时(秒)',
    `question_order`     INT         NULL     COMMENT '题号',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_aiqr_attempt`     (`attempt_id`),
    INDEX `idx_aiqr_student`     (`student_id`),
    INDEX `idx_aiqr_text`        (`text_id`),
    INDEX `idx_aiqr_student_text`(`student_id`, `text_id`),
    INDEX `idx_aiqr_exercise`    (`exercise_id`),
    CONSTRAINT `fk_aiqr_attempt` FOREIGN KEY (`attempt_id`)  REFERENCES `student_ai_quiz_attempts`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_aiqr_student` FOREIGN KEY (`student_id`)  REFERENCES `users`(`id`)                   ON DELETE CASCADE,
    CONSTRAINT `fk_aiqr_text`    FOREIGN KEY (`text_id`)     REFERENCES `texts`(`id`)                   ON DELETE CASCADE,
    CONSTRAINT `fk_aiqr_ex`      FOREIGN KEY (`exercise_id`) REFERENCES `text_exercises`(`id`)          ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ═══════════════════════════════════════════════════════════════════════
-- V32__link_users_to_school_directory.sql
-- 将 users 与 school_student / school_teacher 主数据建立显式联系
-- 注：与 V25__exercise_options_table.sql 同 V25 版本号；本块为后续补录，
--     全部使用 IF NOT EXISTS / WHERE NULL 包装，保证幂等可重复执行
-- ═══════════════════════════════════════════════════════════════════════

-- 1) 规范化 username：student_<digits> -> <digits>（已规范化的不会再匹配）
UPDATE users
SET username = SUBSTRING(username, 9)
WHERE username REGEXP '^student_[0-9]+$';

-- 2) users 新增 school_student_id（幂等）
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'school_student_id'
);
SET @col_sql = IF(
    @col_exists = 0,
    'ALTER TABLE users ADD COLUMN school_student_id BIGINT NULL COMMENT ''关联 school_student.id（学号匹配）'' AFTER inner_uid',
    'SELECT 1'
);
PREPARE stmt FROM @col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) users 新增 school_teacher_id（幂等）
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'school_teacher_id'
);
SET @col_sql = IF(
    @col_exists = 0,
    'ALTER TABLE users ADD COLUMN school_teacher_id BIGINT NULL COMMENT ''关联 school_teacher.id（工号匹配）'' AFTER school_student_id',
    'SELECT 1'
);
PREPARE stmt FROM @col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 索引（幂等）
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_school_student_id'
);
SET @idx_sql = IF(
    @idx_exists = 0,
    'CREATE INDEX idx_users_school_student_id ON users(school_student_id)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_school_teacher_id'
);
SET @idx_sql = IF(
    @idx_exists = 0,
    'CREATE INDEX idx_users_school_teacher_id ON users(school_teacher_id)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 回填外键（幂等：WHERE IS NULL）
--    注：users.username 与 school_student/teacher 的 student_id/teacher_id 可能存在
--    collation 差异（utf8mb4_0900_ai_ci vs utf8mb4_unicode_ci），显式 COLLATE 避免
--    "Illegal mix of collations" 错误。
UPDATE users u
JOIN school_student s
  ON s.student_id COLLATE utf8mb4_unicode_ci = u.username COLLATE utf8mb4_unicode_ci
SET u.school_student_id = s.id
WHERE u.school_student_id IS NULL;

UPDATE users u
JOIN school_teacher t
  ON t.teacher_id COLLATE utf8mb4_unicode_ci = u.username COLLATE utf8mb4_unicode_ci
SET u.school_teacher_id = t.id
WHERE u.school_teacher_id IS NULL;

-- 6) 学生学校画像视图（DROP + CREATE 幂等）
DROP VIEW IF EXISTS v_user_school_profile;
CREATE VIEW v_user_school_profile AS
SELECT
    u.id                          AS user_id,
    u.username                    AS username,
    u.role                        AS role,
    u.real_name                   AS real_name,
    s.id                          AS school_student_id,
    s.student_id                  AS student_id,
    s.student_name                AS student_name,
    s.college_name                AS college_name,
    s.class_code                  AS class_code,
    s.class_name                  AS class_name,
    s.grade                       AS grade,
    s.major_name                  AS major_name,
    (SELECT COUNT(*) FROM course_selection cs            WHERE cs.student_id = s.student_id) AS course_selection_count,
    (SELECT COUNT(*) FROM teacher_student_binding tsb    WHERE tsb.student_id = s.student_id) AS teacher_binding_count,
    (SELECT MAX(les.exam_date) FROM level_exam_scores les WHERE les.student_id = s.student_id) AS last_exam_date,
    (SELECT MAX(les.score)     FROM level_exam_scores les WHERE les.student_id = s.student_id) AS best_exam_score
FROM users u
JOIN school_student s ON s.id = u.school_student_id
WHERE u.deleted = 0;

-- V26__add_vocabulary_source_type.sql
-- 生词本来源标记：区分文章阅读加入的词和其他来源的词

DROP PROCEDURE IF EXISTS _add_vocabulary_source_type$$
CREATE PROCEDURE _add_vocabulary_source_type()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE table_schema = DATABASE()
          AND table_name = 'vocabulary_books'
          AND column_name = 'source_type'
    ) THEN
        ALTER TABLE vocabulary_books
        ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        COMMENT '来源类型：ARTICLE-文章阅读，MANUAL-手动添加或其他'
        AFTER next_review_time;
    END IF;
END$$
CALL _add_vocabulary_source_type()$$
DROP PROCEDURE IF EXISTS _add_vocabulary_source_type$$

-- 历史数据全部标记为 MANUAL（旧数据无法确定来源）
UPDATE vocabulary_books SET source_type = 'MANUAL'
WHERE source_type IS NULL OR source_type = '';

-- 给 student_id + source_type 加索引，提升按来源查询的性能
DROP PROCEDURE IF EXISTS _idx_vocabulary_source$$
CREATE PROCEDURE _idx_vocabulary_source()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE table_schema = DATABASE()
          AND table_name = 'vocabulary_books'
          AND index_name = 'idx_vocabulary_source'
    ) THEN
        CREATE INDEX idx_vocabulary_source ON vocabulary_books(student_id, source_type);
    END IF;
END$$
CALL _idx_vocabulary_source()$$
DROP PROCEDURE IF EXISTS _idx_vocabulary_source$$

-- ============================================================
-- V31__add_content_version_to_text_learning_records.sql
-- ============================================================

DROP PROCEDURE IF EXISTS _add_col_tlr_content_version$$
CREATE PROCEDURE _add_col_tlr_content_version()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema=DATABASE() AND table_name='text_learning_records'
                     AND column_name='content_version') THEN
        ALTER TABLE text_learning_records ADD COLUMN content_version VARCHAR(20) DEFAULT NULL
            COMMENT 'Content version: original/cet4/cet6/academic/chinese_parallel';
        CREATE INDEX idx_tlr_student_version ON text_learning_records(student_id, content_version);
    END IF;
END$$
CALL _add_col_tlr_content_version()$$
DROP PROCEDURE IF EXISTS _add_col_tlr_content_version$$

-- ============================================================
-- V32__recommendation_events.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS recommendation_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_id BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL COMMENT 'IMPRESSION/CLICK/DISMISS/BOOKMARK',
    source VARCHAR(30) DEFAULT NULL COMMENT 'item_cf/user_cf/fallback/persona',
    position INT DEFAULT NULL COMMENT 'Recommendation list position (0-based)',
    persona_tags VARCHAR(200) DEFAULT NULL COMMENT 'Active persona tag codes at event time',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_re_user (user_id),
    INDEX idx_re_text (text_id),
    INDEX idx_re_user_type (user_id, event_type),
    INDEX idx_re_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- V33__learner_profile_snapshots.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS learner_profile_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    profile_status VARCHAR(10) DEFAULT NULL,
    risk_level VARCHAR(10) DEFAULT NULL,
    active_days_7d INT DEFAULT 0,
    active_days_14d INT DEFAULT 0,
    study_minutes_14d INT DEFAULT 0,
    inactive_days INT DEFAULT 0,
    new_count INT DEFAULT 0,
    learning_count INT DEFAULT 0,
    mastered_count INT DEFAULT 0,
    weak_ratio DOUBLE DEFAULT 0.0,
    overdue_review_count INT DEFAULT 0,
    overdue_review_ratio DOUBLE DEFAULT 0.0,
    primary_content_version VARCHAR(20) DEFAULT NULL,
    version_distribution_json VARCHAR(500) DEFAULT NULL,
    matched_tag_codes VARCHAR(200) DEFAULT NULL,
    tag_scores_json VARCHAR(500) DEFAULT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, snapshot_date),
    INDEX idx_lps_user (user_id),
    INDEX idx_lps_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- V34__tag_recalibration_log.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS tag_recalibration_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_code VARCHAR(50) NOT NULL,
    old_threshold VARCHAR(50) NOT NULL,
    new_threshold VARCHAR(50) NOT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    affected_user_count INT DEFAULT 0,
    operator VARCHAR(50) DEFAULT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trl_tag (tag_code),
    INDEX idx_trl_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- V35__learner_profile_summaries.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS learner_profile_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    summary_type VARCHAR(20) NOT NULL COMMENT 'PERSONAL/TEACHER_FACED/CLASS_COHORT',
    language VARCHAR(10) DEFAULT 'zh',
    summary_text TEXT NOT NULL,
    model_id VARCHAR(100) DEFAULT NULL,
    input_snapshot_id BIGINT DEFAULT NULL,
    input_tags_json VARCHAR(500) DEFAULT NULL,
    generation_time_ms INT DEFAULT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_lps2_user (user_id),
    INDEX idx_lps2_type (summary_type),
    INDEX idx_lps2_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- V36__learner_composite_tags.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS learner_composite_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    tag_type VARCHAR(20) NOT NULL,
    priority INT DEFAULT 50,
    rule_definition JSON NOT NULL,
    action_hint VARCHAR(200) DEFAULT NULL,
    enabled TINYINT(1) DEFAULT 1,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- V37__learner_profile_feature_toggles.sql
-- ============================================================
INSERT IGNORE INTO system_config (config_group, config_key, config_value, description)
VALUES
  ('feature_toggle', 'admin.persona_analytics',         'true', '管理员-画像分析'),
  ('feature_toggle', 'admin.class_cohort',              'true', '管理员-班级画像分析'),
  ('feature_toggle', 'student.learner_profile_enabled', 'true', '学生端-我的画像');

-- ============================================================
-- V38__seed_base_tag_definitions.sql
-- ============================================================
INSERT IGNORE INTO learner_composite_tags (code, display_name, tag_type, priority, rule_definition, action_hint, enabled) VALUES
('INACTIVE_RISK', '流失风险', 'risk', 10,
 '{"conditions":[{"source":"metric","field":"activeDays14d","operator":"LT","value":3},{"source":"metric","field":"studyMinutes14d","operator":"GT","value":0}],"combiner":"AND"}',
 '建议增加学习提醒频率', 1),
('VOCAB_WEAK', '词汇弱势', 'risk', 20,
 '{"conditions":[{"source":"metric","field":"totalWords","operator":"GTE","value":30},{"source":"metric","field":"weakRatio","operator":"GTE","value":0.6}],"combiner":"AND"}',
 '建议加强词汇复习', 1),
('REVIEW_OVERDUE', '复习逾期', 'risk', 30,
 '{"conditions":[{"source":"metric","field":"dueReviewCount","operator":"GT","value":0},{"source":"metric","field":"overdueReviewRatio","operator":"GTE","value":0.5}],"combiner":"AND"}',
 '请尽快完成待复习词汇', 1),
('CONSISTENT_LEARNER', '学习稳定', 'positive', 40,
 '{"conditions":[{"source":"metric","field":"activeDays7d","operator":"GTE","value":5}],"combiner":"AND"}',
 '保持良好的学习节奏', 1),
('CONTENT_LEVEL_STUCK', '版本停滞', 'ability', 50,
 '{"conditions":[{"source":"metric","field":"textsRead","operator":"GTE","value":40},{"source":"metric","field":"versionDistSize","operator":"EQ","value":1}],"combiner":"AND"}',
 '建议尝试更高难度版本', 1),
('CONTENT_LEVEL_JUMP', '版本跳跃', 'ability', 60,
 '{"conditions":[{"source":"metric","field":"activeDays7d","operator":"GTE","value":0}],"combiner":"AND"}',
 '注意难度跨度是否过大', 0),
('IMPROVING_TREND', '进步趋势', 'positive', 70,
 '{"conditions":[{"source":"metric","field":"oldActiveDays14d","operator":"GT","value":0},{"source":"metric","field":"trendRatio","operator":"GTE","value":1.3}],"combiner":"AND"}',
 '学习活跃度明显提升', 1),
('DECLINING_TREND', '下滑趋势', 'risk', 80,
 '{"conditions":[{"source":"metric","field":"oldActiveDays14d","operator":"GT","value":0},{"source":"metric","field":"trendRatio","operator":"LTE","value":0.6}],"combiner":"AND"}',
 '学习活跃度出现下降', 1),
('REVIEW_CHAMPION', '复习达人', 'positive', 90,
 '{"conditions":[{"source":"metric","field":"snap0OverdueRatio","operator":"LT","value":0.05},{"source":"metric","field":"snap1OverdueRatio","operator":"LT","value":0.05},{"source":"metric","field":"snap2OverdueRatio","operator":"LT","value":0.05}],"combiner":"AND"}',
 '复习习惯非常好', 1);

INSERT IGNORE INTO system_config (config_group, config_key, config_value, description)
VALUES ('feature_toggle', 'admin.tag_management', 'true', '管理员-标签管理');

-- ═══════════════════════════════════════════════════════════════════════
-- V39__update_config_hot_deploy_fields.sql
-- update_config 表新增零停机热部署调度字段（幂等）
-- ═══════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS v39_add_hot_deploy_fields$$
CREATE PROCEDURE v39_add_hot_deploy_fields()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'update_config'
                     AND COLUMN_NAME = 'hot_deploy_enabled') THEN
        ALTER TABLE update_config
            ADD COLUMN hot_deploy_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用定时零停机热部署',
            ADD COLUMN hot_deploy_cron_expr VARCHAR(100) NULL COMMENT '热部署 cron 表达式',
            ADD COLUMN hot_deploy_last_run_at DATETIME NULL COMMENT '最近一次热部署执行时间',
            ADD COLUMN hot_deploy_last_run_status VARCHAR(20) NULL DEFAULT 'NEVER' COMMENT '最近一次热部署状态';
    END IF;
END$$
CALL v39_add_hot_deploy_fields()$$
DROP PROCEDURE IF EXISTS v39_add_hot_deploy_fields$$

-- ═══════════════════════════════════════════════════════════════════════
-- V40__cleanup_update_config.sql
-- 清理 update_config 表 — 移除普通更新字段，热部署字段升为主字段（幂等）
-- ═══════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS v40_cleanup_update_config$$
CREATE PROCEDURE v40_cleanup_update_config()
BEGIN
    -- 将 hot_deploy_* 列合并到主字段
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'update_config'
                 AND COLUMN_NAME = 'hot_deploy_enabled') THEN
        UPDATE update_config
        SET enabled         = COALESCE(hot_deploy_enabled, 0),
            cron_expr       = COALESCE(hot_deploy_cron_expr, '0 0 2 ? * SUN'),
            last_run_at     = hot_deploy_last_run_at,
            last_run_status = COALESCE(hot_deploy_last_run_status, 'NEVER');
        ALTER TABLE update_config
            DROP COLUMN hot_deploy_enabled,
            DROP COLUMN hot_deploy_cron_expr,
            DROP COLUMN hot_deploy_last_run_at,
            DROP COLUMN hot_deploy_last_run_status;
    END IF;

    -- 删除普通更新专用列
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'update_config'
                 AND COLUMN_NAME = 'backup_before_update') THEN
        ALTER TABLE update_config DROP COLUMN backup_before_update;
    END IF;
END$$
CALL v40_cleanup_update_config()$$
DROP PROCEDURE IF EXISTS v40_cleanup_update_config$$
