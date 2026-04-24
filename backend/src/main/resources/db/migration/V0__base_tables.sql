-- ═══════════════════════════════════════════════════════════════════════
-- V0: 核心基础表
-- Description: 创建所有被后续迁移脚本引用的核心表
-- 这些表之前只存在于 Docker init 脚本 (00-base-tables.sql) 中，
-- 导致 CI 环境（空数据库 + Flyway）无法运行迁移链。
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
-- 注意: 不包含 V2+ 后续迁移添加的列（chinese_parallel_content, markdown_content 等）
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `texts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `unit_id` BIGINT NOT NULL COMMENT '单元ID',
    `title` VARCHAR(200) NOT NULL COMMENT '课文标题',
    `content` TEXT NOT NULL COMMENT '课文内容',
    `difficulty` ENUM('EASY','MEDIUM','HARD') DEFAULT 'MEDIUM' COMMENT '难度',
    `order_num` INT DEFAULT 0 COMMENT '排序号',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_unit_id` (`unit_id`),
    KEY `idx_difficulty` (`difficulty`),
    KEY `idx_order_num` (`order_num`),
    KEY `idx_deleted` (`deleted`),
    CONSTRAINT `texts_ibfk_1` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`) ON DELETE CASCADE
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
-- 注意: 不包含 V1 添加的 weight 和 ideal_study_time_minutes 列
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
-- 注意: 不包含 V3 添加的 SM-2 字段（repetition, interval_days, easiness_factor, next_review_time）
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `vocabulary_books` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `word_id` BIGINT NOT NULL COMMENT '单词ID',
    `added_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    `review_count` INT DEFAULT 0 COMMENT '复习次数',
    `last_reviewed` DATETIME DEFAULT NULL COMMENT '最后复习时间',
    `mastery_level` ENUM('NEW','LEARNING','FAMILIAR','MASTERED') DEFAULT 'NEW' COMMENT '掌握程度',
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
-- 注意: 不包含 V2 添加的 text_id 列
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `learning_records` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `word_id` BIGINT DEFAULT NULL COMMENT '单词ID',
    `assignment_id` BIGINT DEFAULT NULL COMMENT '任务ID',
    `record_type` VARCHAR(50) DEFAULT NULL COMMENT '记录类型',
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
