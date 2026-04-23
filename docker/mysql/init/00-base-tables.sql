-- ══════════════════════════════════════════════════════════════
-- LangHack 基础表结构
-- 在 docker-entrypoint-initdb.d 中第 0 步执行（在所有其他脚本之前）
--
-- 包含核心基础表，这些表被 Flyway 迁移脚本引用
-- ══════════════════════════════════════════════════════════════

USE `english_learning`;

SET NAMES utf8mb4;
SET character_set_client = utf8mb4;

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
    `hobby` VARCHAR(100) DEFAULT NULL COMMENT '用户兴趣爱好',
    `level` VARCHAR(50) DEFAULT NULL COMMENT '英语水平等级',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `sso_bound` TINYINT(1) DEFAULT 0 COMMENT '是否通过SSO绑定',
    `sso_provider` VARCHAR(50) DEFAULT NULL COMMENT 'SSO提供商',
    `sso_subject` VARCHAR(255) DEFAULT NULL COMMENT 'SSO唯一标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `username` (`username`),
    KEY `idx_username` (`username`),
    KEY `idx_role` (`role`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_inner_uid` (`inner_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

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
-- 课文表
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
-- 任务表
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
-- 生词本表
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
-- 学习记录表
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
-- 全量备份导入会覆盖这些账号，此 INSERT 仅用于无备份的全新部署
-- ────────────────────────────────────────
INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'ADMIN', '系统管理员', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'admin');

INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'ops', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'OPS', '运维人员', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'ops');

INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'teacher', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'TEACHER', '演示教师', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'teacher');

INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'student', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'STUDENT', '演示学生', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'student');
