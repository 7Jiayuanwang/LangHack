-- =============================================================
-- LangHack consolidated initial stable database migration
-- Generated from backend/src/main/resources/db/migration V0-V15
-- This file is used by scripts/auto-update.sh and scripts/hot-update.sh.
-- Last updated: 2026-05-05 (V15 added: fix schema issues)
-- =============================================================

USE english_learning;
SET NAMES utf8mb4;

-- Included Flyway migrations:
--   V0__base_tables.sql
--   V1__scoring_and_assignments.sql
--   V2__text_and_learning.sql
--   V3__media_and_annotations.sql
--   V4__backup_and_update_system.sql
--   V5__monitoring_and_access.sql
--   V6__system_config_and_ai_providers.sql
--   V7__feature_toggles.sql
--   V8__sso_integration.sql
--   V9__school_data_sync.sql
--   V10__user_and_text_updates.sql
--   V11__role_mapping_cleanup.sql
--   V12__school_data_and_videos.sql
--   V13__feature_toggles_and_ai_config.sql
--   V14__enhancements.sql
--   V15__fix_schema_issues.sql


-- =============================================================
-- V0__base_tables.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V0: 鏍稿績鍩虹琛紙瀹屾暣鐗堬級
-- Description: 鍒涘缓鎵€鏈夋牳蹇冭〃鐨勬渶缁堢姸鎬侊紝鍖呭惈鎵€鏈夊瓧娈?
--
-- 娉ㄦ剰: 姝ゆ枃浠朵笌 docker/mysql/init/00-base-tables.sql 淇濇寔鍚屾
--       鍚庣画杩佺Щ鑴氭湰锛圴1, V2, V10 绛夛級浣跨敤 IF NOT EXISTS 妫€鏌ヤ繚璇佸箓绛夋€?
--
-- 鍚屾鏃ユ湡: 2026-05-05
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 鐢ㄦ埛琛紙鏍稿績琛紝琚涓〃寮曠敤锛?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL COMMENT '鐢ㄦ埛鍚?,
    `password` VARCHAR(255) NOT NULL COMMENT '瀵嗙爜',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '閭',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '鎵嬫満鍙?,
    `role` ENUM('USER','STUDENT','TEACHER','ADMIN','OPS') NOT NULL DEFAULT 'USER',
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '鐪熷疄濮撳悕',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '鏄电О',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '澶村儚URL',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '鐘舵€?,
    `inner_uid` VARCHAR(100) DEFAULT NULL COMMENT '鐢ㄦ埛鍐呴儴鍞竴鏍囪瘑绗?,
    `hobby` VARCHAR(100) DEFAULT NULL COMMENT '鐢ㄦ埛鍏磋叮鐖卞ソ',
    `level` VARCHAR(50) DEFAULT NULL COMMENT '鑻辫姘村钩绛夌骇',
    `deleted` TINYINT DEFAULT 0 COMMENT '鏄惁鍒犻櫎 0-鏈垹闄?1-宸插垹闄?,
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    PRIMARY KEY (`id`),
    UNIQUE KEY `username` (`username`),
    KEY `idx_username` (`username`),
    KEY `idx_role` (`role`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_inner_uid` (`inner_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鐢ㄦ埛琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 璇剧▼琛?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `courses` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '璇剧▼鍚嶇О',
    `description` TEXT COMMENT '璇剧▼鎻忚堪',
    `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '灏侀潰鍥剧墖',
    `created_by` BIGINT DEFAULT NULL COMMENT '鍒涘缓鑰匢D',
    `status` ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE' COMMENT '鐘舵€?,
    `deleted` TINYINT DEFAULT 0 COMMENT '鏄惁鍒犻櫎 0-鏈垹闄?1-宸插垹闄?,
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_by` (`created_by`),
    KEY `idx_deleted` (`deleted`),
    CONSTRAINT `courses_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='璇剧▼琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 鍗曞厓琛?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `units` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL COMMENT '璇剧▼ID',
    `name` VARCHAR(100) NOT NULL COMMENT '鍗曞厓鍚嶇О',
    `description` TEXT COMMENT '鍗曞厓鎻忚堪',
    `order_num` INT DEFAULT 0 COMMENT '鎺掑簭鍙?,
    `deleted` TINYINT DEFAULT 0 COMMENT '鏄惁鍒犻櫎 0-鏈垹闄?1-宸插垹闄?,
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_order_num` (`order_num`),
    KEY `idx_deleted` (`deleted`),
    CONSTRAINT `units_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鍗曞厓琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 璇炬枃琛紙瀹屾暣鐗堬紝鍖呭惈鎵€鏈夊瓧娈碉級
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `texts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `unit_id` BIGINT DEFAULT NULL COMMENT '鍗曞厓ID锛堝彲涓虹┖锛岃〃绀烘湭鍒嗛厤鍒板崟鍏冿級',
    `title` VARCHAR(200) NOT NULL COMMENT '璇炬枃鏍囬',
    `content` TEXT NOT NULL COMMENT '璇炬枃鍐呭',
    `markdown_content` LONGTEXT COMMENT 'Markdown鏍煎紡鐨勮鏂囧唴瀹?,
    `content_type` ENUM('TEXT','MARKDOWN') DEFAULT 'TEXT' COMMENT '鍐呭绫诲瀷锛歍EXT-绾枃鏈紝MARKDOWN-Markdown鏍煎紡',
    `cet4_content` TEXT COMMENT 'CET-4鐗堟湰鍐呭',
    `cet6_content` TEXT COMMENT 'CET-6鐗堟湰鍐呭',
    `academic_content` TEXT COMMENT '瀛︽湳鐗堟湰鍐呭',
    `ielts_content` TEXT COMMENT '闆呮€濈増鏈唴瀹?,
    `audio_url` VARCHAR(255) DEFAULT NULL COMMENT '闊抽URL',
    `difficulty` ENUM('EASY','MEDIUM','HARD') DEFAULT 'MEDIUM' COMMENT '闅惧害',
    `order_num` INT DEFAULT 0 COMMENT '鎺掑簭鍙?,
    `uploaded_by` BIGINT DEFAULT NULL COMMENT '涓婁紶鑰匢D锛圢ULL琛ㄧず绯荤粺鍐呭锛?,
    `tags` VARCHAR(255) DEFAULT NULL COMMENT '鏍囩锛屽涓敤閫楀彿鍒嗛殧',
    `view_count` INT DEFAULT 0 COMMENT '鏌ョ湅娆℃暟',
    `like_count` INT DEFAULT 0 COMMENT '鐐硅禐娆℃暟',
    `is_public` TINYINT DEFAULT 1 COMMENT '鏄惁鍏紑 0-绉佹湁 1-鍏紑',
    `status` ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'APPROVED' COMMENT '瀹℃牳鐘舵€?,
    `deleted` TINYINT DEFAULT 0 COMMENT '鏄惁鍒犻櫎 0-鏈垹闄?1-宸插垹闄?,
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    `chinese_parallel_content` TEXT COMMENT '涓嫳瀵圭収鐗堟湰鍐呭',
    `cet4_markdown_content` LONGTEXT COMMENT 'CET-4闅惧害鐨凪arkdown鐗堟湰鍐呭',
    `cet6_markdown_content` LONGTEXT COMMENT 'CET-6闅惧害鐨凪arkdown鐗堟湰鍐呭',
    `academic_markdown_content` LONGTEXT COMMENT '瀛︽湳鐗堢殑Markdown鐗堟湰鍐呭',
    `chinese_parallel_markdown_content` LONGTEXT COMMENT '涓嫳瀵圭収鐨凪arkdown鐗堟湰鍐呭',
    `cover_url` VARCHAR(255) DEFAULT NULL COMMENT '灏侀潰鍥綰RL',
    `is_featured` TINYINT(1) DEFAULT 0 COMMENT '鏄惁鎺ㄨ崘',
    `featured_priority` INT DEFAULT 0 COMMENT '鎺ㄨ崘浼樺厛绾?,
    `content_format` VARCHAR(20) DEFAULT 'PLAIN_TEXT' COMMENT '鍐呭鏍煎紡锛歅LAIN_TEXT-绾枃鏈紝MARKDOWN-Markdown鏍煎紡',
    `cet4_content_format` VARCHAR(20) DEFAULT 'PLAIN_TEXT' COMMENT 'CET4鍐呭鏍煎紡',
    `cet6_content_format` VARCHAR(20) DEFAULT 'PLAIN_TEXT' COMMENT 'CET6鍐呭鏍煎紡',
    `academic_content_format` VARCHAR(20) DEFAULT 'PLAIN_TEXT' COMMENT '瀛︽湳鍐呭鏍煎紡',
    `chinese_parallel_content_format` VARCHAR(20) DEFAULT 'PLAIN_TEXT' COMMENT '涓嫳瀵圭収鍐呭鏍煎紡',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='璇炬枃琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 鐝骇琛?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `classes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '鐝骇鍚嶇О',
    `description` TEXT COMMENT '鐝骇鎻忚堪',
    `course_id` BIGINT DEFAULT NULL COMMENT '鍏宠仈璇剧▼ID',
    `teacher_id` BIGINT DEFAULT NULL COMMENT '鏁欏笀ID',
    `join_code` VARCHAR(10) DEFAULT NULL COMMENT '鍔犲叆鐮?鍙负绌?',
    `max_students` INT DEFAULT 50 COMMENT '鏈€澶у鐢熸暟',
    `status` ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE' COMMENT '鐘舵€?,
    `deleted` TINYINT DEFAULT 0 COMMENT '鏄惁鍒犻櫎 0-鏈垹闄?1-宸插垹闄?,
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    PRIMARY KEY (`id`),
    UNIQUE KEY `join_code` (`join_code`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_teacher_id` (`teacher_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`),
    CONSTRAINT `classes_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
    CONSTRAINT `classes_ibfk_2` FOREIGN KEY (`teacher_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鐝骇琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 浠诲姟琛紙瀹屾暣鐗堬紝鍖呭惈 weight 鍜?ideal_study_time_minutes锛?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `assignments` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(200) NOT NULL COMMENT '浠诲姟鏍囬',
    `description` TEXT COMMENT '浠诲姟鎻忚堪',
    `class_id` BIGINT DEFAULT NULL COMMENT '鐝骇ID',
    `unit_id` BIGINT DEFAULT NULL COMMENT '鍗曞厓ID',
    `text_id` BIGINT DEFAULT NULL COMMENT '璇炬枃ID',
    `type` ENUM('READING','VOCABULARY','COMPREHENSION') NOT NULL COMMENT '浠诲姟绫诲瀷',
    `difficulty` ENUM('EASY','MEDIUM','HARD') DEFAULT 'MEDIUM' COMMENT '闅惧害',
    `deadline` DATETIME DEFAULT NULL COMMENT '鎴鏃堕棿',
    `weight` DECIMAL(5,2) DEFAULT 1.00 COMMENT '浠诲姟鏉冮噸锛堢敤浜庤绠楀姞鏉冨钩鍧囧垎锛岄粯璁?.00锛?,
    `ideal_study_time_minutes` INT DEFAULT 60 COMMENT '鐞嗘兂瀛︿範鏃堕暱锛堝垎閽燂級锛岀敤浜庤绠楀垎鏁帮紝榛樿60鍒嗛挓',
    `status` ENUM('DRAFT','PUBLISHED','CLOSED') DEFAULT 'DRAFT' COMMENT '鐘舵€?,
    `created_by` BIGINT DEFAULT NULL COMMENT '鍒涘缓鑰匢D',
    `deleted` TINYINT DEFAULT 0 COMMENT '鏄惁鍒犻櫎 0-鏈垹闄?1-宸插垹闄?,
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浠诲姟琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 鍗曡瘝琛?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `words` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `word` VARCHAR(100) NOT NULL COMMENT '鍗曡瘝',
    `pronunciation` VARCHAR(200) DEFAULT NULL COMMENT '鍙戦煶',
    `definition` TEXT COMMENT '涓枃閲婁箟',
    `example_sentence` TEXT COMMENT '渚嬪彞',
    `audio_url` VARCHAR(255) DEFAULT NULL COMMENT '闊抽URL',
    `part_of_speech` VARCHAR(50) DEFAULT NULL COMMENT '璇嶆€?,
    `difficulty` ENUM('EASY','MEDIUM','HARD') DEFAULT 'MEDIUM' COMMENT '闅惧害',
    `synonyms` TEXT COMMENT '鍚屼箟璇?,
    `antonyms` TEXT COMMENT '鍙嶄箟璇?,
    `wordnet_id` VARCHAR(100) DEFAULT NULL COMMENT 'WordNet ID',
    `multiple_definitions` TEXT COMMENT '澶氫釜閲婁箟',
    `source` VARCHAR(100) DEFAULT NULL COMMENT '鏉ユ簮',
    `deleted` TINYINT DEFAULT 0 COMMENT '鏄惁鍒犻櫎 0-鏈垹闄?1-宸插垹闄?,
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    PRIMARY KEY (`id`),
    KEY `idx_word` (`word`),
    KEY `idx_difficulty` (`difficulty`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鍗曡瘝琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 鐢熻瘝鏈〃锛堝畬鏁寸増锛屽寘鍚?SM-2 绠楁硶瀛楁锛?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `vocabulary_books` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL COMMENT '瀛︾敓ID',
    `word_id` BIGINT NOT NULL COMMENT '鍗曡瘝ID',
    `added_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '娣诲姞鏃堕棿',
    `review_count` INT DEFAULT 0 COMMENT '澶嶄範娆℃暟',
    `last_reviewed` DATETIME DEFAULT NULL COMMENT '鏈€鍚庡涔犳椂闂?,
    `mastery_level` ENUM('NEW','LEARNING','FAMILIAR','MASTERED') DEFAULT 'NEW' COMMENT '鎺屾彙绋嬪害锛歂EW-鏂拌瘝锛孡EARNING-瀛︿範涓紝FAMILIAR-鐔熸倝锛孧ASTERED-宸叉帉鎻?,
    `repetition` INT DEFAULT 0 COMMENT 'SM2閲嶅娆℃暟',
    `interval_days` INT DEFAULT 1 COMMENT 'SM2闂撮殧澶╂暟',
    `easiness_factor` DOUBLE DEFAULT 2.5 COMMENT 'SM2闅惧害鍥犲瓙',
    `next_review_time` DATETIME DEFAULT NULL COMMENT '涓嬫澶嶄範鏃堕棿',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_word` (`student_id`, `word_id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_word_id` (`word_id`),
    KEY `idx_mastery_level` (`mastery_level`),
    CONSTRAINT `vocabulary_books_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `vocabulary_books_ibfk_2` FOREIGN KEY (`word_id`) REFERENCES `words` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鐢熻瘝鏈〃';

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 瀛︿範璁板綍琛紙瀹屾暣鐗堬紝鍖呭惈 text_id锛?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `learning_records` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL COMMENT '瀛︾敓ID',
    `word_id` BIGINT DEFAULT NULL COMMENT '鍗曡瘝ID',
    `text_id` BIGINT DEFAULT NULL COMMENT '鍏宠仈鐨勮鏂嘔D',
    `assignment_id` BIGINT DEFAULT NULL COMMENT '浠诲姟ID',
    `record_type` VARCHAR(50) DEFAULT NULL COMMENT '璁板綍绫诲瀷锛歏IEW-娴忚锛孯EVIEW-澶嶄範锛孴EST-娴嬭瘯',
    `duration_seconds` INT DEFAULT 0 COMMENT '瀛︿範鏃堕暱锛堢锛?,
    `correct_count` INT DEFAULT 0 COMMENT '姝ｇ‘娆℃暟',
    `total_count` INT DEFAULT 0 COMMENT '鎬绘鏁?,
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    PRIMARY KEY (`id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_word_id` (`word_id`),
    KEY `idx_assignment_id` (`assignment_id`),
    KEY `idx_record_type` (`record_type`),
    KEY `idx_created_time` (`created_time`),
    KEY `idx_text_id` (`text_id`),
    CONSTRAINT `learning_records_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='瀛︿範璁板綍琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 瀛︾敓-鐝骇鍏宠仈琛?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `student_classes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL COMMENT '瀛︾敓ID',
    `class_id` BIGINT NOT NULL COMMENT '鐝骇ID',
    `join_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍔犲叆鏃堕棿',
    `status` ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE' COMMENT '鐘舵€?,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_class` (`student_id`, `class_id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_class_id` (`class_id`),
    CONSTRAINT `student_classes_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `student_classes_ibfk_2` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='瀛︾敓-鐝骇鍏宠仈琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 榛樿娴嬭瘯璐﹀彿锛堝瘑鐮佸潎涓?admin123锛孊Crypt hash锛?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'admin', '$2a$10$ROg6F9MwwqmBGuuGMspwHula/rcizG318TtEVnYudxuLwTdocaNY.', 'ADMIN', '绯荤粺绠＄悊鍛?, 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'admin');

INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'ops', '$2a$10$ROg6F9MwwqmBGuuGMspwHula/rcizG318TtEVnYudxuLwTdocaNY.', 'OPS', '杩愮淮浜哄憳', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'ops');

INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'teacher', '$2a$10$ROg6F9MwwqmBGuuGMspwHula/rcizG318TtEVnYudxuLwTdocaNY.', 'TEACHER', '婕旂ず鏁欏笀', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'teacher');

INSERT INTO `users` (`username`, `password`, `role`, `real_name`, `status`, `deleted`, `created_time`, `updated_time`)
SELECT 'student', '$2a$10$ROg6F9MwwqmBGuuGMspwHula/rcizG318TtEVnYudxuLwTdocaNY.', 'STUDENT', '婕旂ず瀛︾敓', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'student');


-- =============================================================
-- V1__scoring_and_assignments.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V1: 鎴愮哗璁板綍琛?+ 浠诲姟鎵╁睍
-- 鏁村悎鑷? V5 (score_records), V6 (weight), V7 (ideal_study_time), V17 (unique constraint)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 1. 鍒涘缓鎴愮哗璁板綍琛?[鍘?V5]
--    鍞竴绾︽潫 uk_student_assignment_type 宸插唴鑱?[鍘?V17]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS score_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '瀛︾敓ID',
    assignment_id BIGINT COMMENT '浠诲姟ID',
    course_id BIGINT COMMENT '璇剧▼ID',
    unit_id BIGINT COMMENT '鍗曞厓ID',
    score_type VARCHAR(50) NOT NULL COMMENT '鍒嗘暟绫诲瀷: ASSIGNMENT/COURSE/UNIT/EXAM',
    score INT NOT NULL COMMENT '鍒嗘暟鍊?0-100)',
    full_score INT NOT NULL DEFAULT 100 COMMENT '婊″垎鍊?,
    score_details TEXT COMMENT 'JSON鏍煎紡鐨勫緱鍒嗚鎯?,
    remark VARCHAR(500) COMMENT '澶囨敞',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    CONSTRAINT fk_score_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    -- 闃叉鍚屼竴瀛︾敓鍚屼竴浠诲姟閲嶅璇勫垎 (assignment_id 涓?NULL 鏃朵笉鐢熸晥锛岃繖鏄鏈熻涓?
    UNIQUE KEY uk_student_assignment_type (student_id, assignment_id, score_type),
    INDEX idx_student_id (student_id),
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_course_id (course_id),
    INDEX idx_unit_id (unit_id),
    INDEX idx_score_type (score_type),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鎴愮哗璁板綍琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 2. 浠诲姟琛ㄦ墿灞?[鍘?V6 + V7]
--    浣跨敤瀛樺偍杩囩▼ IF NOT EXISTS 闃叉閲嶅娣诲姞鍒?
--    娉ㄦ剰: Flyway 涓嶆敮鎸?DELIMITER 鍛戒护锛岀洿鎺ュ畾涔夊瓨鍌ㄨ繃绋?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
DROP PROCEDURE IF EXISTS _add_col_assignments_weight;
CREATE PROCEDURE _add_col_assignments_weight()
BEGIN
    -- 浠诲姟鏉冮噸锛堝姞鏉冨钩鍧囧垎锛?
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='assignments' AND column_name='weight') THEN
        ALTER TABLE assignments ADD COLUMN weight DECIMAL(5,2) DEFAULT 1.00
            COMMENT '浠诲姟鏉冮噸锛堢敤浜庤绠楀姞鏉冨钩鍧囧垎锛岄粯璁?.00锛? AFTER deadline;
    END IF;
    -- 鐞嗘兂瀛︿範鏃堕暱
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='assignments' AND column_name='ideal_study_time_minutes') THEN
        ALTER TABLE assignments ADD COLUMN ideal_study_time_minutes INT DEFAULT 60
            COMMENT '鐞嗘兂瀛︿範鏃堕暱锛堝垎閽燂級锛岄粯璁?0鍒嗛挓' AFTER weight;
    END IF;
END;
CALL _add_col_assignments_weight();
DROP PROCEDURE IF EXISTS _add_col_assignments_weight;

-- 绱㈠紩锛堝箓绛夛細IF NOT EXISTS 妫€鏌ワ級
DROP PROCEDURE IF EXISTS _add_idx_assignments;
CREATE PROCEDURE _add_idx_assignments()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE table_schema=DATABASE() AND table_name='assignments' AND index_name='idx_weight') THEN
        CREATE INDEX idx_weight ON assignments (weight);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE table_schema=DATABASE() AND table_name='assignments' AND index_name='idx_ideal_study_time') THEN
        CREATE INDEX idx_ideal_study_time ON assignments (ideal_study_time_minutes);
    END IF;
END;
CALL _add_idx_assignments();
DROP PROCEDURE IF EXISTS _add_idx_assignments;


-- =============================================================
-- V2__text_and_learning.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V2: 璇炬枃澧炲己 + 瀛︿範璁板綍
-- 鏁村悎鑷? 鍘?V2 (text_id, chinese_parallel, markdown, markdown versions, unit_id nullable,
--         text_learning_records, user_text_reads, is_featured)
--         + V10 (featured_priority + index)
--         + V17 (cet4/6/academic_content, uploaded_by, tags, view/like_count, is_public, status)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 1. 瀛︿範璁板綍娣诲姞璇炬枃鍏宠仈 [鍘?V2]
--    娉ㄦ剰: Flyway 涓嶆敮鎸?DELIMITER 鍛戒护锛岀洿鎺ュ畾涔夊瓨鍌ㄨ繃绋?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
DROP PROCEDURE IF EXISTS _add_col_lr_text;
CREATE PROCEDURE _add_col_lr_text()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='learning_records' AND column_name='text_id') THEN
        ALTER TABLE learning_records ADD COLUMN text_id BIGINT COMMENT '鍏宠仈鐨勮鏂嘔D' AFTER word_id;
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

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 2. 璇炬枃琛ㄦ墿灞?鈥?缁熶竴鎵归噺娣诲姞鎵€鏈夊垪 [鍘?V2 + V10 + V17]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
DROP PROCEDURE IF EXISTS _add_col_texts_batch;
CREATE PROCEDURE _add_col_texts_batch()
BEGIN
    -- 涓嫳瀵圭収鍐呭 [鍘?V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='chinese_parallel_content') THEN
        ALTER TABLE texts ADD COLUMN chinese_parallel_content TEXT COMMENT '涓嫳瀵圭収鐗堟湰鍐呭';
    END IF;
    -- Markdown 鏀寔 [鍘?V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='markdown_content') THEN
        ALTER TABLE texts ADD COLUMN markdown_content LONGTEXT COMMENT 'Markdown鏍煎紡鐨勮鏂囧唴瀹? AFTER content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='content_type') THEN
        ALTER TABLE texts ADD COLUMN content_type ENUM('TEXT','MARKDOWN') DEFAULT 'TEXT' COMMENT '鍐呭绫诲瀷锛歍EXT-绾枃鏈紝MARKDOWN-Markdown鏍煎紡' AFTER markdown_content;
    END IF;
    -- 澶氶毦搴︾増鏈?Markdown [鍘?V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet4_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN cet4_markdown_content LONGTEXT COMMENT 'CET-4闅惧害鐨凪arkdown鐗堟湰' AFTER chinese_parallel_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet6_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN cet6_markdown_content LONGTEXT COMMENT 'CET-6闅惧害鐨凪arkdown鐗堟湰' AFTER cet4_markdown_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='academic_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN academic_markdown_content LONGTEXT COMMENT '瀛︽湳鐗堢殑Markdown鐗堟湰' AFTER cet6_markdown_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='chinese_parallel_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN chinese_parallel_markdown_content LONGTEXT COMMENT '涓嫳瀵圭収鐨凪arkdown鐗堟湰' AFTER academic_markdown_content;
    END IF;
    -- 鎺ㄨ崘鏂囩珷鏍囪瘑 [鍘?V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='is_featured') THEN
        ALTER TABLE texts ADD COLUMN is_featured TINYINT(1) DEFAULT 0 COMMENT '鏄惁鎺ㄨ崘';
    END IF;
    -- 鎺ㄨ崘浼樺厛绾?[鍘?V10]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='featured_priority') THEN
        ALTER TABLE texts ADD COLUMN featured_priority INT NOT NULL DEFAULT 0 COMMENT '鎺ㄨ崘浼樺厛绾э紝鍊艰秺澶ц秺闈犲墠鏄剧ず锛?=榛樿锛?;
    END IF;
    -- 澶氶毦搴︾増鏈唴瀹癸紙绾枃鏈級[鍘?V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet4_content') THEN
        ALTER TABLE texts ADD COLUMN cet4_content LONGTEXT COMMENT 'CET-4闅惧害鐗堟湰鍐呭' AFTER content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet6_content') THEN
        ALTER TABLE texts ADD COLUMN cet6_content LONGTEXT COMMENT 'CET-6闅惧害鐗堟湰鍐呭' AFTER cet4_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='academic_content') THEN
        ALTER TABLE texts ADD COLUMN academic_content LONGTEXT COMMENT '瀛︽湳鐗堢増鏈唴瀹? AFTER cet6_content;
    END IF;
    -- 涓婁紶鑰?[鍘?V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='uploaded_by') THEN
        ALTER TABLE texts ADD COLUMN uploaded_by BIGINT DEFAULT NULL COMMENT '涓婁紶鑰呯敤鎴稩D';
    END IF;
    -- 鏍囩 [鍘?V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='tags') THEN
        ALTER TABLE texts ADD COLUMN tags VARCHAR(500) DEFAULT NULL COMMENT '鏍囩锛岄€楀彿鍒嗛殧';
    END IF;
    -- 娴忚/鐐硅禐 [鍘?V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='view_count') THEN
        ALTER TABLE texts ADD COLUMN view_count INT DEFAULT 0 COMMENT '娴忚娆℃暟';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='like_count') THEN
        ALTER TABLE texts ADD COLUMN like_count INT DEFAULT 0 COMMENT '鐐硅禐娆℃暟';
    END IF;
    -- 鍏紑鐘舵€?[鍘?V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='is_public') THEN
        ALTER TABLE texts ADD COLUMN is_public TINYINT(1) DEFAULT 1 COMMENT '鏄惁鍏紑锛?=鍚︼紝1=鏄級';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='status') THEN
        ALTER TABLE texts ADD COLUMN status VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT '鐘舵€侊細DRAFT/PUBLISHED/ARCHIVED';
    END IF;
END;
CALL _add_col_texts_batch();
DROP PROCEDURE IF EXISTS _add_col_texts_batch;

-- 鎺ㄨ崘浼樺厛绾у鍚堢储寮?[鍘?V10]
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

-- unit_id 鏀逛负鍙┖锛堟敮鎸佽鏂囦粠鍗曞厓涓Щ闄わ級[鍘?V2]
ALTER TABLE texts MODIFY COLUMN unit_id BIGINT DEFAULT NULL
    COMMENT '鍗曞厓ID锛堝彲涓虹┖锛岃〃绀烘湭鍒嗛厤鍒板崟鍏冿級';

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 3. 鍒涘缓鏂囩珷瀛︿範璁板綍琛?[鍘?V2]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS text_learning_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '瀛︾敓ID',
    text_id BIGINT NOT NULL COMMENT '璇炬枃ID',
    assignment_id BIGINT COMMENT '鍏宠仈鐨勪换鍔D',
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '闃呰寮€濮嬫椂闂?,
    end_time DATETIME COMMENT '闃呰缁撴潫鏃堕棿',
    duration_seconds INT DEFAULT 0 COMMENT '闃呰鏃堕暱(绉?',
    is_completed TINYINT(1) DEFAULT 0 COMMENT '鏄惁宸插畬鎴愰槄璇?,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_student_text (student_id, text_id),
    INDEX idx_assignment (assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏂囩珷瀛︿範璁板綍琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 4. 鍒涘缓鐢ㄦ埛鏂囩珷宸茶璁板綍琛?[鍘?V2]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS user_text_reads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_id BIGINT NOT NULL,
    read_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_text (user_id, text_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛鏂囩珷宸茶璁板綍琛?;


-- =============================================================
-- V3__media_and_annotations.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V3: 濯掍綋琛?+ 璇嶆眹SM-2 + 鍒掔嚎鏍囨敞 + 濯掍綋璺緞鍒?
-- 鏁村悎鑷? 鍘?V3 (media, SM-2, underlines)
--         + V9 (media file_path/thumbnail_path)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 1. 鍒涘缓濯掍綋琛?[鍘?V3]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS media (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '濯掍綋ID',
    media_type ENUM('IMAGE','VIDEO') NOT NULL COMMENT '濯掍綋绫诲瀷',
    file_name VARCHAR(255) NOT NULL COMMENT '鍘熷鏂囦欢鍚?,
    file_content LONGBLOB NOT NULL COMMENT '鏂囦欢鍐呭',
    file_size BIGINT DEFAULT 0 COMMENT '鏂囦欢澶у皬锛堝瓧鑺傦級',
    mime_type VARCHAR(100) COMMENT 'MIME绫诲瀷',
    width INT COMMENT '瀹藉害锛堝儚绱狅紝浠呭浘鐗囷級',
    height INT COMMENT '楂樺害锛堝儚绱狅紝浠呭浘鐗囷級',
    duration INT COMMENT '鏃堕暱锛堢锛屼粎瑙嗛锛?,
    uploaded_by BIGINT COMMENT '涓婁紶鑰匢D',
    text_id BIGINT COMMENT '鍏宠仈鐨勮鏂嘔D',
    description VARCHAR(500) COMMENT '濯掍綋鎻忚堪',
    status ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'APPROVED' COMMENT '瀹℃牳鐘舵€?,
    deleted TINYINT DEFAULT 0 COMMENT '鏄惁鍒犻櫎 0-鏈垹闄?1-宸插垹闄?,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    INDEX idx_media_type (media_type),
    INDEX idx_uploaded_by (uploaded_by),
    INDEX idx_text_id (text_id),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='濯掍綋琛紙鍥剧墖鍜岃棰戯級';

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 2. 濯掍綋璺緞鍒?[鍘?V9]
--    娉ㄦ剰: Flyway 涓嶆敮鎸?DELIMITER 鍛戒护锛岀洿鎺ュ畾涔夊瓨鍌ㄨ繃绋?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
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

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 3. 璇嶆眹鏈坊鍔?SM-2 闂撮殧閲嶅瀛楁 [鍘?V3]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
DROP PROCEDURE IF EXISTS _add_col_sm2;
CREATE PROCEDURE _add_col_sm2()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='repetition') THEN
        ALTER TABLE vocabulary_books ADD COLUMN repetition INT DEFAULT 0 COMMENT 'SM-2 閲嶅娆℃暟';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='interval_days') THEN
        ALTER TABLE vocabulary_books ADD COLUMN interval_days INT DEFAULT 0 COMMENT 'SM-2 闂撮殧澶╂暟';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='easiness_factor') THEN
        ALTER TABLE vocabulary_books ADD COLUMN easiness_factor DOUBLE DEFAULT 2.5 COMMENT 'SM-2 绠€鏄撳洜瀛?;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='next_review_time') THEN
        ALTER TABLE vocabulary_books ADD COLUMN next_review_time DATETIME COMMENT 'SM-2 涓嬫澶嶄範鏃堕棿';
    END IF;
END;
CALL _add_col_sm2();
DROP PROCEDURE IF EXISTS _add_col_sm2;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 4. 鍒涘缓鍒掔嚎鏍囨敞琛?[鍘?V3]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS underlines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_id BIGINT NOT NULL,
    selected_text TEXT COMMENT '鐢ㄦ埛閫変腑鐨勬枃鏈唴瀹?,
    start_index INT COMMENT '璧峰浣嶇疆',
    end_index INT COMMENT '缁撴潫浣嶇疆',
    serialized_range TEXT COMMENT 'Serialized DOM range for robust highlighting',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_text_id (text_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛鍒掔嚎鏍囨敞琛?;


-- =============================================================
-- V4__backup_and_update_system.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V4: 鏁版嵁搴撳浠界郴缁?+ 鑷姩鏇存柊閰嶇疆
-- 鏁村悎鑷? 鍘?V4 (backup_records, backup_operation_logs, backup_config)
--         + V8 (update_config)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 1. 鍒涘缓澶囦唤璁板綍琛?[鍘?V4]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS backup_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL COMMENT '澶囦唤鏂囦欢鍚?,
    file_size BIGINT DEFAULT 0 COMMENT '鏂囦欢澶у皬(瀛楄妭)',
    backup_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '澶囦唤绫诲瀷: MANUAL/SCHEDULED',
    backup_mode VARCHAR(20) NOT NULL DEFAULT 'FULL' COMMENT '澶囦唤妯″紡: FULL/INCREMENTAL',
    base_backup_id BIGINT DEFAULT NULL COMMENT '澧為噺澶囦唤鐨勫熀鍑嗗叏閲忓浠絀D',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '鐘舵€? IN_PROGRESS/SUCCESS/FAILED',
    storage_path VARCHAR(500) NOT NULL COMMENT '瀛樺偍璺緞',
    created_by VARCHAR(100) COMMENT '鍒涘缓鑰?,
    error_message TEXT COMMENT '閿欒淇℃伅',
    database_size BIGINT DEFAULT 0 COMMENT '鏁版嵁搴撳ぇ灏?瀛楄妭)',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    completed_time DATETIME COMMENT '瀹屾垚鏃堕棿',
    INDEX idx_backup_type (backup_type),
    INDEX idx_backup_mode (backup_mode),
    INDEX idx_status (status),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏁版嵁搴撳浠借褰曡〃';

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 2. 鍒涘缓澶囦唤鎿嶄綔鏃ュ織琛?[鍘?V4]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS backup_operation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(100) NOT NULL COMMENT '鎿嶄綔浜?,
    operation_type VARCHAR(50) NOT NULL COMMENT '鎿嶄綔绫诲瀷: BACKUP_CREATE/DELETE/RESTORE/DOWNLOAD/CLEANUP/SCHEDULED',
    target_id BIGINT COMMENT '鐩爣澶囦唤璁板綍ID',
    target_name VARCHAR(255) COMMENT '鐩爣澶囦唤鏂囦欢鍚?,
    detail VARCHAR(1000) COMMENT '鎿嶄綔璇︽儏',
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '鎿嶄綔缁撴灉: SUCCESS/FAILED',
    ip_address VARCHAR(50) COMMENT '鎿嶄綔IP鍦板潃',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鎿嶄綔鏃堕棿',
    INDEX idx_operator (operator),
    INDEX idx_operation_type (operation_type),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='澶囦唤鎿嶄綔鏃ュ織琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 3. 鍒涘缓澶囦唤閰嶇疆琛紙鍗曡閰嶇疆妯″紡锛塠鍘?V4]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS backup_config (
    id INT NOT NULL DEFAULT 1,
    auto_backup_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '鑷姩澶囦唤寮€鍏?,
    backup_cron VARCHAR(100) NOT NULL DEFAULT '0 0 2 * * ?' COMMENT 'cron琛ㄨ揪寮?,
    backup_hour INT NOT NULL DEFAULT 2 COMMENT '澶囦唤灏忔椂(0-23)',
    backup_minute INT NOT NULL DEFAULT 0 COMMENT '澶囦唤鍒嗛挓(0-59)',
    retention_days INT NOT NULL DEFAULT 30 COMMENT '淇濈暀澶╂暟',
    max_count INT NOT NULL DEFAULT 50 COMMENT '鏈€澶у浠芥暟閲?,
    incremental_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '澧為噺澶囦唤寮€鍏?,
    full_backup_interval_days INT NOT NULL DEFAULT 7 COMMENT '鍏ㄩ噺澶囦唤闂撮殧澶╂暟',
    updated_by VARCHAR(50) DEFAULT NULL COMMENT '鏈€鍚庢洿鏂颁汉',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏈€鍚庢洿鏂版椂闂?,
    PRIMARY KEY (id),
    CONSTRAINT chk_single_row CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='澶囦唤閰嶇疆琛?鍗曡)';

INSERT IGNORE INTO backup_config (id, auto_backup_enabled, backup_cron, backup_hour, backup_minute,
    retention_days, max_count, incremental_enabled, full_backup_interval_days)
VALUES (1, 1, '0 0 2 * * ?', 2, 0, 30, 50, 0, 7);

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 4. 鑷姩鏇存柊閰嶇疆琛?[鍘?V8]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
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


-- =============================================================
-- V5__monitoring_and_access.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V23: 璁块棶鐩戞帶 + AI璋冪敤缁熻 + 瑙掕壊鎵╁睍 + 婕旂ず璐﹀彿
-- 鏁村悎鑷? V23 (monitoring tables), V24 (OPS role), V25 (demo accounts)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 1. 璁块棶鏃ュ織琛紙鍘熷鏁版嵁锛屼繚鐣?澶╋級[鍘?V23]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS access_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT DEFAULT NULL COMMENT '鐢ㄦ埛ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '鐢ㄦ埛鍚?,
    method VARCHAR(10) NOT NULL COMMENT 'HTTP鏂规硶',
    uri VARCHAR(500) NOT NULL COMMENT '璇锋眰URI',
    query_string VARCHAR(1000) DEFAULT NULL COMMENT '鏌ヨ鍙傛暟',
    status_code INT NOT NULL COMMENT 'HTTP鐘舵€佺爜',
    response_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT '鍝嶅簲鏃堕棿(姣)',
    ip_address VARCHAR(45) DEFAULT NULL COMMENT 'IP鍦板潃',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX idx_created_time (created_time),
    INDEX idx_username (username),
    INDEX idx_uri (uri(191)),
    INDEX idx_status_code (status_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='璁块棶鏃ュ織琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 2. 姣忓皬鏃惰仛鍚堢粺璁¤〃锛堜繚鐣?0澶╋級[鍘?V23]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS access_stats_hourly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_hour DATETIME NOT NULL COMMENT '缁熻灏忔椂(鏁寸偣)',
    total_requests INT NOT NULL DEFAULT 0 COMMENT '鎬昏姹傛暟',
    unique_users INT NOT NULL DEFAULT 0 COMMENT '鐙珛鐢ㄦ埛鏁?,
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '骞冲潎鍝嶅簲鏃堕棿(姣)',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '鏈€澶у搷搴旀椂闂?姣)',
    error_count INT NOT NULL DEFAULT 0 COMMENT '閿欒鏁?4xx+5xx)',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    UNIQUE KEY uk_stat_hour (stat_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='姣忓皬鏃惰闂粺璁?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 3. 姣忔棩鑱氬悎缁熻琛紙闀挎湡淇濈暀锛塠鍘?V23]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS access_stats_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL COMMENT '缁熻鏃ユ湡',
    total_requests INT NOT NULL DEFAULT 0 COMMENT '鎬昏姹傛暟',
    unique_users INT NOT NULL DEFAULT 0 COMMENT '鐙珛鐢ㄦ埛鏁?,
    peak_concurrent INT NOT NULL DEFAULT 0 COMMENT '宄板€煎苟鍙戞暟',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '骞冲潎鍝嶅簲鏃堕棿(姣)',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '鏈€澶у搷搴旀椂闂?姣)',
    error_count INT NOT NULL DEFAULT 0 COMMENT '閿欒鏁?,
    top_pages TEXT DEFAULT NULL COMMENT '鐑棬椤甸潰JSON',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    UNIQUE KEY uk_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='姣忔棩璁块棶缁熻';

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 4. AI/澶栭儴鏈嶅姟璋冪敤鏃ュ織琛紙淇濈暀30澶╋級[鍘?V23]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS ai_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(50) NOT NULL COMMENT '鏈嶅姟鍚嶇О: HIAGENT/OPENROUTER/TENCENT_TRANSLATE/TENCENT_ASR/BILIBILI',
    method_name VARCHAR(100) DEFAULT NULL COMMENT '璋冪敤鏂规硶鍚?,
    success TINYINT(1) NOT NULL DEFAULT 1 COMMENT '鏄惁鎴愬姛',
    response_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT '鍝嶅簲鏃堕棿(姣)',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '閿欒淇℃伅',
    input_tokens INT DEFAULT NULL COMMENT '杈撳叆token鏁?,
    output_tokens INT DEFAULT NULL COMMENT '杈撳嚭token鏁?,
    model_name VARCHAR(100) DEFAULT NULL COMMENT '妯″瀷鍚嶇О',
    triggered_by VARCHAR(100) DEFAULT NULL COMMENT '瑙﹀彂鐢ㄦ埛',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX idx_service_name (service_name),
    INDEX idx_created_time (created_time),
    INDEX idx_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI璋冪敤鏃ュ織琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 5. AI璋冪敤鏃ョ粺璁¤〃锛堥暱鏈熶繚鐣欙級[鍘?V23]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS ai_call_stats_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL COMMENT '缁熻鏃ユ湡',
    service_name VARCHAR(50) NOT NULL COMMENT '鏈嶅姟鍚嶇О',
    total_calls INT NOT NULL DEFAULT 0 COMMENT '鎬昏皟鐢ㄦ鏁?,
    success_calls INT NOT NULL DEFAULT 0 COMMENT '鎴愬姛娆℃暟',
    failed_calls INT NOT NULL DEFAULT 0 COMMENT '澶辫触娆℃暟',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '骞冲潎鍝嶅簲鏃堕棿',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '鏈€澶у搷搴旀椂闂?,
    total_input_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '鎬昏緭鍏oken',
    total_output_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '鎬昏緭鍑簍oken',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    UNIQUE KEY uk_date_service (stat_date, service_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI璋冪敤鏃ョ粺璁?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 6. 鐢ㄦ埛瑙掕壊鎵╁睍 鈥?鏂板 OPS 杩愮淮瑙掕壊 [鍘?V24]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
ALTER TABLE users MODIFY COLUMN role ENUM('USER','STUDENT','TEACHER','ADMIN','OPS') NOT NULL DEFAULT 'USER';

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 7. 纭繚婕旂ず璐﹀彿瀛樺湪涓斿彲姝ｅ父鐧诲綍 [鍘?V25]
--    瀵嗙爜: admin123 (澶嶇敤 admin 璐﹀彿鐨?BCrypt hash)
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 纭繚 teacher 璐﹀彿瀛樺湪
INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'teacher',
       (SELECT password FROM users WHERE username = 'admin' LIMIT 1),
       'TEACHER', '婕旂ず鏁欏笀', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'teacher');

-- 纭繚 student 璐﹀彿瀛樺湪
INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'student',
       (SELECT password FROM users WHERE username = 'admin' LIMIT 1),
       'STUDENT', '婕旂ず瀛︾敓', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'student');

-- 鍚屾瀵嗙爜 + 纭繚 ACTIVE 鐘舵€?+ 鏈杞垹闄?
UPDATE users SET
    password = (SELECT pw FROM (SELECT password AS pw FROM users WHERE username = 'admin' LIMIT 1) tmp),
    status = 'ACTIVE',
    deleted = 0
WHERE username IN ('teacher', 'student');


-- =============================================================
-- V6__system_config_and_ai_providers.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V6: 绯荤粺閰嶇疆 + AI鎻愪緵鍟?+ 澶氭ā鍨嬫敮鎸?
-- 鏁村悎鑷? 鍘?V6 (system_config + 榛樿閰嶇疆)
--         + V7 (ai_provider 琛?+ 鏁版嵁杩佺Щ)
--         + V11 (ai_provider.app_id)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 1. 鍒涘缓绯荤粺閰嶇疆琛?+ 榛樿閰嶇疆 [鍘?V6]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_group` VARCHAR(50) NOT NULL COMMENT '閰嶇疆鍒嗙粍: ai_translate, ai_article, ai_chat',
    `config_key` VARCHAR(100) NOT NULL COMMENT '閰嶇疆閿?,
    `config_value` TEXT COMMENT '閰嶇疆鍊?,
    `description` VARCHAR(255) COMMENT '閰嶇疆璇存槑锛堣繍缁撮潰鏉挎樉绀猴級',
    `is_secret` TINYINT DEFAULT 0 COMMENT '鏄惁鏁忔劅锛堝墠绔劚鏁忔樉绀猴級',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updated_by` VARCHAR(50),
    UNIQUE KEY `uk_group_key` (`config_group`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绯荤粺閰嶇疆琛?;

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('ai_translate', 'api_url', 'https://api.siliconflow.cn/v1/chat/completions', 'API 鍦板潃锛圤penAI 鍏煎锛?, 0),
('ai_translate', 'api_key', '', 'API 瀵嗛挜', 1),
('ai_translate', 'model', 'Qwen/Qwen2.5-7B-Instruct', '妯″瀷鍚嶇О', 0),
('ai_translate', 'enabled', 'true', '鏄惁鍚敤', 0),
('ai_translate', 'timeout', '30000', '瓒呮椂鏃堕棿(ms)', 0);

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 2. AI 鎻愪緵鍟嗚〃 + 鏁版嵁杩佺Щ [鍘?V7]
--    app_id 瀛楁宸插唴鑱?[鍘?V11]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS `ai_provider` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `service` VARCHAR(50) NOT NULL COMMENT 'translate / article / chat',
    `name` VARCHAR(100) NOT NULL COMMENT 'display name',
    `api_url` VARCHAR(500) NOT NULL COMMENT 'provider endpoint',
    `api_key` TEXT NOT NULL COMMENT 'provider api key',
    `app_id` VARCHAR(200) NULL COMMENT 'App ID锛圚iAgent 绛夋湇鍔″晢蹇呭～锛?,
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


-- =============================================================
-- V7__feature_toggles.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V7: 鏂囩珷鏀惰棌 + 鍔熻兘寮€鍏?+ 涓€у寲寮€鍏?
-- 鏁村悎鑷? 鍘?V12 (article_favorites + feature_toggle 閰嶇疆)
--         + V13 (淇 system_config description 涔辩爜)
--         + V14 (user_tags + feature_override)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 1. 鏂囩珷鏀惰棌琛?[鍘?V12]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
CREATE TABLE IF NOT EXISTS article_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '瀛︾敓ID',
    text_id BIGINT NOT NULL COMMENT '璇炬枃/鏂囩珷ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鏀惰棌鏃堕棿',
    UNIQUE KEY uk_student_text (student_id, text_id),
    INDEX idx_student_id (student_id),
    INDEX idx_text_id (text_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏂囩珷鏀惰棌琛?;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 2. 鍔熻兘寮€鍏抽厤缃?[鍘?V12 + V13]
--    V13 鐨?UPDATE 璇彞绱ц窡 INSERT IGNORE锛岀‘淇?description 姝ｇ‘
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'page_dashboard', 'true', '瀛︾敓绔?鍙戠幇椤?, 0),
('feature_toggle', 'page_courses', 'false', '瀛︾敓绔?璇剧▼椤碉紙寮€鍙戜腑锛?, 0),
('feature_toggle', 'page_classes', 'true', '瀛︾敓绔?鐝骇/绀惧尯椤?, 0),
('feature_toggle', 'page_assignments', 'true', '瀛︾敓绔?浠诲姟椤?, 0),
('feature_toggle', 'page_articles', 'true', '瀛︾敓绔?鏂囩珷椤?, 0),
('feature_toggle', 'page_vocabulary', 'true', '瀛︾敓绔?鐢熻瘝鏈〉', 0),
('feature_toggle', 'page_my_study', 'true', '瀛︾敓绔?鎴戠殑瀛︿範椤?, 0),
('feature_toggle', 'page_ai_video', 'false', '瀛︾敓绔?AI瑙嗛瀛楀箷椤碉紙鏈畬鍠勶級', 0),
('feature_toggle', 'page_ai_qa', 'true', '瀛︾敓绔?AI闂瓟椤?, 0),
('feature_toggle', 'page_user_texts', 'true', '瀛︾敓绔?鎴戠殑涓婁紶椤?, 0);

-- 淇 description 涔辩爜 [鍘?V13]
UPDATE `system_config` SET `description` = '瀛︾敓绔?鍙戠幇椤?              WHERE `config_key` = 'page_dashboard'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '瀛︾敓绔?璇剧▼椤碉紙寮€鍙戜腑锛?    WHERE `config_key` = 'page_courses'     AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '瀛︾敓绔?鐝骇/绀惧尯椤?         WHERE `config_key` = 'page_classes'     AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '瀛︾敓绔?浠诲姟椤?              WHERE `config_key` = 'page_assignments' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '瀛︾敓绔?鏂囩珷椤?              WHERE `config_key` = 'page_articles'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '瀛︾敓绔?鐢熻瘝鏈〉'            WHERE `config_key` = 'page_vocabulary' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '瀛︾敓绔?鎴戠殑瀛︿範椤?          WHERE `config_key` = 'page_my_study'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '瀛︾敓绔?AI瑙嗛瀛楀箷椤碉紙鏈畬鍠勶級' WHERE `config_key` = 'page_ai_video'  AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '瀛︾敓绔?AI闂瓟椤?            WHERE `config_key` = 'page_ai_qa'      AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '瀛︾敓绔?鎴戠殑涓婁紶椤?          WHERE `config_key` = 'page_user_texts' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '鏄惁浣跨敤 OpenRouter 浣滀负 AI 鏈嶅姟鍟? WHERE `config_key` = 'use_openrouter'     AND `config_group` = 'ai_config';
UPDATE `system_config` SET `description` = 'OpenRouter API Key'                   WHERE `config_key` = 'openrouter_api_key' AND `config_group` = 'ai_config';
UPDATE `system_config` SET `description` = 'OpenRouter 妯″瀷 ID'                  WHERE `config_key` = 'openrouter_model'   AND `config_group` = 'ai_config';

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 3. 涓€у寲鍔熻兘寮€鍏?[鍘?V14]
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
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
    scope        VARCHAR(16)  NOT NULL COMMENT 'tag 鎴?user',
    scope_value  VARCHAR(128) NOT NULL COMMENT 'tag鍚嶇О 鎴?userId瀛楃涓?,
    feature_key  VARCHAR(128) NOT NULL,
    enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    description  VARCHAR(255) DEFAULT NULL,
    updated_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_scope_key (scope, scope_value, feature_key),
    INDEX idx_scope_value (scope, scope_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =============================================================
-- V8__sso_integration.sql
-- =============================================================

-- V15: 缁熶竴韬唤璁よ瘉 (SSO) 闆嗘垚

-- SSO 鐢ㄦ埛缁戝畾琛?
CREATE TABLE IF NOT EXISTS sso_user_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '涓婚敭ID',
    user_id BIGINT NOT NULL COMMENT '鏈湴鐢ㄦ埛ID',
    sso_provider VARCHAR(50) NOT NULL COMMENT 'SSO鎻愪緵鍟? CAS, OAUTH2',
    sso_user_id VARCHAR(255) NOT NULL COMMENT 'SSO鐢ㄦ埛鍞竴鏍囪瘑',
    sso_username VARCHAR(255) NOT NULL COMMENT 'SSO鐢ㄦ埛鍚?,
    sso_email VARCHAR(255) COMMENT 'SSO閭',
    sso_real_name VARCHAR(255) COMMENT 'SSO鐪熷疄濮撳悕',
    sso_member_of TEXT COMMENT 'SSO缁勭粐鍗曚綅 (JSON鏁扮粍)',
    auto_approved TINYINT(1) DEFAULT 0 COMMENT '鏄惁鑷姩瀹℃壒: 0=寰呭鎵? 1=宸插鎵?,
    last_login_time DATETIME COMMENT '鏈€鍚庣櫥褰曟椂闂?,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    deleted TINYINT(1) DEFAULT 0 COMMENT '閫昏緫鍒犻櫎: 0=姝ｅ父, 1=宸插垹闄?,
    UNIQUE KEY uk_sso_provider_user (sso_provider, sso_user_id, deleted),
    INDEX idx_user_id (user_id),
    INDEX idx_sso_username (sso_username),
    INDEX idx_auto_approved (auto_approved),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO鐢ㄦ埛缁戝畾琛?;

-- SSO 鐧诲綍鏃ュ織琛?
CREATE TABLE IF NOT EXISTS sso_login_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '涓婚敭ID',
    sso_provider VARCHAR(50) NOT NULL COMMENT 'SSO鎻愪緵鍟?,
    sso_user_id VARCHAR(255) NOT NULL COMMENT 'SSO鐢ㄦ埛ID',
    sso_username VARCHAR(255) NOT NULL COMMENT 'SSO鐢ㄦ埛鍚?,
    login_result VARCHAR(50) NOT NULL COMMENT '鐧诲綍缁撴灉: SUCCESS, PENDING_APPROVAL, FAILED',
    failure_reason VARCHAR(500) COMMENT '澶辫触鍘熷洜',
    ip_address VARCHAR(50) COMMENT 'IP鍦板潃',
    user_agent VARCHAR(500) COMMENT '鐢ㄦ埛浠ｇ悊',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX idx_sso_user (sso_provider, sso_user_id),
    INDEX idx_login_time (created_time),
    INDEX idx_login_result (login_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO鐧诲綍鏃ュ織';

-- SSO 閰嶇疆琛?
CREATE TABLE IF NOT EXISTS sso_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '涓婚敭ID',
    config_key VARCHAR(100) NOT NULL COMMENT '閰嶇疆閿?,
    config_value TEXT COMMENT '閰嶇疆鍊?,
    config_type VARCHAR(50) NOT NULL COMMENT '閰嶇疆绫诲瀷: SYSTEM, CAS, OAUTH2, ROLE_MAPPING',
    description VARCHAR(500) COMMENT '閰嶇疆鎻忚堪',
    is_encrypted TINYINT(1) DEFAULT 0 COMMENT '鏄惁鍔犲瘑瀛樺偍',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY uk_config_key (config_key),
    INDEX idx_config_type (config_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO閰嶇疆琛?;

-- 鎵╁睍 users 琛ㄥ瓧娈?
SET @sso_bound_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sso_bound'
);
SET @sso_bound_sql = IF(
    @sso_bound_exists = 0,
    'ALTER TABLE users ADD COLUMN sso_bound TINYINT(1) DEFAULT 0 COMMENT ''鏄惁閫氳繃SSO缁戝畾: 0=鍚? 1=鏄?'',
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
    'ALTER TABLE users ADD COLUMN sso_provider VARCHAR(50) COMMENT ''SSO鎻愪緵鍟?'',
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
    'ALTER TABLE users ADD COLUMN sso_subject VARCHAR(255) COMMENT ''SSO鍞竴鏍囪瘑''',
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

-- 鎻掑叆榛樿 SSO 閰嶇疆
INSERT IGNORE INTO sso_config (config_key, config_value, config_type, description) VALUES
('sso.enabled', 'false', 'SYSTEM', 'SSO鍔熻兘鎬诲紑鍏?),
('sso.protocol', 'CAS', 'SYSTEM', 'SSO鍗忚绫诲瀷: CAS 鎴?OAUTH2'),
('sso.auto_approval.enabled', 'true', 'SYSTEM', '鏄惁鍚敤鑷姩瀹℃壒'),
('sso.role_mapping.ou=绠＄悊鍛?, 'ADMIN', 'ROLE_MAPPING', '绠＄悊鍛樿鑹叉槧灏?),
('sso.role_mapping.ou=杩愮淮', 'OPS', 'ROLE_MAPPING', '杩愮淮瑙掕壊鏄犲皠'),
('sso.role_mapping.ou=鏁欏笀', 'TEACHER', 'ROLE_MAPPING', '鏁欏笀瑙掕壊鏄犲皠'),
('sso.role_mapping.ou=瀛︾敓', 'STUDENT', 'ROLE_MAPPING', '瀛︾敓瑙掕壊鏄犲皠'),
('sso.role_mapping.ou=People', 'STUDENT', 'ROLE_MAPPING', 'CAS containerId 涓?People 鐨勭敤鎴锋槧灏勪负 STUDENT 瑙掕壊');


-- =============================================================
-- V9__school_data_sync.sql
-- =============================================================

-- ============================================================================
-- Flyway Migration Script: 瀛︽牎鏁版嵁鍚屾琛ㄧ粨鏋?
-- Version: V100
-- Description: 鍒涘缓瀛︽牎鏁版嵁鎺ュ叆鐩稿叧鐨勬墍鏈夎〃
-- Author: System
-- Date: 2026-04-09
-- ============================================================================

-- ============================================================================
-- 1. 鍩虹鏁版嵁灞?(Foundation Layer)
-- ============================================================================

-- 1.1 琛屾斂鐝〃
CREATE TABLE IF NOT EXISTS administrative_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    class_code VARCHAR(50) NOT NULL UNIQUE COMMENT '琛屾斂鐝紪鍙?(XZBH)',
    class_name VARCHAR(100) NOT NULL COMMENT '琛屾斂鐝悕绉?(XZBMC)',
    class_short_name VARCHAR(50) COMMENT '琛屾斂鐝畝绉?(XZBJC)',
    college_code VARCHAR(50) NOT NULL COMMENT '瀛﹂櫌缂栧彿 (YXBH)',
    college_name VARCHAR(100) NOT NULL COMMENT '瀛﹂櫌鍚嶇О (YXMC)',
    major_code VARCHAR(50) NOT NULL COMMENT '涓撲笟缂栧彿 (ZYBH)',
    major_name VARCHAR(100) NOT NULL COMMENT '涓撲笟鍚嶇О (ZYMC)',
    grade VARCHAR(10) NOT NULL COMMENT '骞寸骇 (NJ)',
    class_type VARCHAR(20) COMMENT '鐝骇绫诲瀷 (BJLX)',
    head_teacher_code VARCHAR(50) COMMENT '鐝富浠诲伐鍙?(BZRGH)',
    head_teacher_name VARCHAR(50) COMMENT '鐝富浠诲鍚?(BZRXM)',
    student_count INT DEFAULT 0 COMMENT '瀛︾敓浜烘暟 (XSRS)',
    male_count INT DEFAULT 0 COMMENT '鐢风敓浜烘暟 (NSRS)',
    female_count INT DEFAULT 0 COMMENT '濂崇敓浜烘暟 (NSRS_NV)',
    class_status VARCHAR(20) NOT NULL COMMENT '鐝骇鐘舵€?(BJZT)',
    establish_date DATE COMMENT '鎴愮珛鏃ユ湡 (CLRQ)',
    graduate_date DATE COMMENT '姣曚笟鏃ユ湡 (BYRQ)',
    remark VARCHAR(500) COMMENT '澶囨敞 (BZ)',
    sync_time DATETIME NOT NULL COMMENT '鍚屾鏃堕棿',
    sync_source VARCHAR(20) NOT NULL COMMENT '鍚屾鏉ユ簮 (api/etl)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    
    INDEX idx_college (college_code),
    INDEX idx_major (major_code),
    INDEX idx_grade (grade),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='琛屾斂鐝熀鏈俊鎭〃';

-- 1.2 瀛︽牎璇剧▼琛?
CREATE TABLE IF NOT EXISTS school_course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    course_code VARCHAR(50) NOT NULL UNIQUE COMMENT '璇剧▼缂栧彿 (KCBH)',
    course_name VARCHAR(200) NOT NULL COMMENT '璇剧▼鍚嶇О (KCMC)',
    course_name_en VARCHAR(200) COMMENT '璇剧▼鑻辨枃鍚嶇О (KCYWMC)',
    course_short_name VARCHAR(100) COMMENT '璇剧▼绠€绉?(KCJC)',
    course_nature VARCHAR(50) NOT NULL COMMENT '璇剧▼鎬ц川 (KCXZ)',
    course_category VARCHAR(50) NOT NULL COMMENT '璇剧▼绫诲埆 (KCLB)',
    credits DECIMAL(3,1) NOT NULL COMMENT '瀛﹀垎 (XF)',
    total_hours INT NOT NULL COMMENT '鎬诲鏃?(ZXS)',
    theory_hours INT COMMENT '鐞嗚瀛︽椂 (LLXS)',
    experiment_hours INT COMMENT '瀹為獙瀛︽椂 (SYXS)',
    practice_hours INT COMMENT '瀹炶返瀛︽椂 (SJXS)',
    computer_hours INT COMMENT '涓婃満瀛︽椂 (SJXS_PC)',
    college_code VARCHAR(50) NOT NULL COMMENT '寮€璇惧闄㈢紪鍙?(KKYXBH)',
    college_name VARCHAR(100) NOT NULL COMMENT '寮€璇惧闄㈠悕绉?(KKYXMC)',
    department_code VARCHAR(50) COMMENT '寮€璇剧郴閮ㄧ紪鍙?(KKXBBH)',
    department_name VARCHAR(100) COMMENT '寮€璇剧郴閮ㄥ悕绉?(KKXBMC)',
    leader_code VARCHAR(50) COMMENT '璇剧▼璐熻矗浜哄伐鍙?(KCFZRGH)',
    leader_name VARCHAR(50) COMMENT '璇剧▼璐熻矗浜哄鍚?(KCFZRXM)',
    assessment_method VARCHAR(50) NOT NULL COMMENT '鑰冩牳鏂瑰紡 (KHFS)',
    is_bilingual VARCHAR(10) COMMENT '鏄惁鍙岃鏁欏 (SFSYJX)',
    is_online VARCHAR(10) COMMENT '鏄惁缃戠粶璇剧▼ (SFWLKC)',
    applicable_majors VARCHAR(500) COMMENT '閫傜敤涓撲笟 (SYZY)',
    prerequisite_codes VARCHAR(500) COMMENT '鍏堜慨璇剧▼缂栧彿 (XXKCBH)',
    prerequisite_names VARCHAR(500) COMMENT '鍏堜慨璇剧▼鍚嶇О (XXKCMC)',
    course_intro TEXT COMMENT '璇剧▼绠€浠?(KCJJ)',
    teaching_objectives TEXT COMMENT '鏁欏鐩爣 (JXMB)',
    course_status VARCHAR(20) NOT NULL COMMENT '璇剧▼鐘舵€?(KCZT)',
    sync_time DATETIME NOT NULL COMMENT '鍚屾鏃堕棿',
    sync_source VARCHAR(20) NOT NULL COMMENT '鍚屾鏉ユ簮',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    
    INDEX idx_college (college_code),
    INDEX idx_nature (course_nature),
    INDEX idx_category (course_category),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='瀛︽牎璇剧▼鍩烘湰淇℃伅琛?;

-- ============================================================================
-- 2. 鏁欏缁勭粐灞?(Teaching Organization Layer)
-- ============================================================================

-- 2.1 鏁欏鐝〃
CREATE TABLE IF NOT EXISTS teaching_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    class_id VARCHAR(50) NOT NULL UNIQUE COMMENT '鏁欏鐝彿 (JXBID)',
    class_name VARCHAR(100) COMMENT '鏁欏鐝悕绉?(JXBMC)',
    course_code VARCHAR(50) NOT NULL COMMENT '璇剧▼缂栧彿 (KCBH)',
    course_name VARCHAR(200) NOT NULL COMMENT '璇剧▼鍚嶇О (KCMC)',
    teacher_id VARCHAR(50) NOT NULL COMMENT '鎺堣鏁欏笀宸ュ彿 (JGH)',
    teacher_name VARCHAR(50) NOT NULL COMMENT '鎺堣鏁欏笀濮撳悕 (XM)',
    academic_year VARCHAR(20) NOT NULL COMMENT '瀛﹀勾 (XNM)',
    semester INT NOT NULL COMMENT '瀛︽湡 (XQM)',
    capacity INT NOT NULL COMMENT '鐝骇瀹归噺',
    enrolled_count INT DEFAULT 0 COMMENT '宸查€変汉鏁?,
    class_status VARCHAR(20) NOT NULL COMMENT '璇剧▼鐘舵€?,
    sync_time DATETIME NOT NULL COMMENT '鍚屾鏃堕棿',
    sync_source VARCHAR(20) NOT NULL COMMENT '鍚屾鏉ユ簮',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    
    FOREIGN KEY (course_code) REFERENCES school_course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_course (course_code),
    INDEX idx_teacher (teacher_id),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏁欏鐝俊鎭〃';

-- 2.2 璇剧▼瀹夋帓琛?
CREATE TABLE IF NOT EXISTS course_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    class_id VARCHAR(50) NOT NULL COMMENT '鏁欏鐝彿 (JXBID)',
    day_of_week INT NOT NULL COMMENT '鏄熸湡鍑?(XQJ) 1-7',
    period VARCHAR(20) NOT NULL COMMENT '鑺傛 (JCS) 濡?1-2"',
    week_range VARCHAR(50) NOT NULL COMMENT '鍛ㄦ (ZCD) 濡?1-16鍛?',
    class_location VARCHAR(100) NOT NULL COMMENT '涓婅鍦扮偣 (CDMC)',
    academic_year VARCHAR(20) NOT NULL COMMENT '瀛﹀勾 (XNM)',
    semester INT NOT NULL COMMENT '瀛︽湡 (XQM)',
    sync_time DATETIME NOT NULL COMMENT '鍚屾鏃堕棿',
    sync_source VARCHAR(20) NOT NULL COMMENT '鍚屾鏉ユ簮',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    
    FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_class_id (class_id),
    INDEX idx_day_period (day_of_week, period),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='璇剧▼瀹夋帓淇℃伅琛?;

-- ============================================================================
-- 3. 閫夎涓庢垚缁╁眰 (Enrollment & Grade Layer)
-- ============================================================================

-- 3.1 閫夎璁板綍琛?
CREATE TABLE IF NOT EXISTS course_selection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    selection_id VARCHAR(50) NOT NULL UNIQUE COMMENT '閫夎璁板綍ID',
    student_id VARCHAR(50) NOT NULL COMMENT '瀛﹀彿',
    student_name VARCHAR(100) NOT NULL COMMENT '瀛︾敓濮撳悕',
    course_code VARCHAR(50) NOT NULL COMMENT '璇剧▼浠ｇ爜',
    course_name VARCHAR(200) COMMENT '璇剧▼鍚嶇О锛圕DSP API涓嶈繑鍥烇紝鍙负绌猴級',
    class_id VARCHAR(50) NOT NULL COMMENT '鏁欏鐝彿',
    teacher_id VARCHAR(50) NOT NULL COMMENT '鎺堣鏁欏笀宸ュ彿',
    teacher_name VARCHAR(50) NOT NULL COMMENT '鎺堣鏁欏笀濮撳悕',
    credits DECIMAL(3,1) COMMENT '瀛﹀垎锛圕DSP API涓嶈繑鍥烇紝鍙负绌猴級',
    academic_year VARCHAR(20) NOT NULL COMMENT '瀛﹀勾',
    semester INT NOT NULL COMMENT '瀛︽湡',
    class_time VARCHAR(100) COMMENT '涓婅鏃堕棿',
    class_location VARCHAR(100) COMMENT '涓婅鍦扮偣',
    selection_time DATETIME NOT NULL COMMENT '閫夎鏃堕棿',
    selection_status VARCHAR(20) NOT NULL COMMENT '閫夎鐘舵€?SELECTED/DROPPED/COMPLETED',
    regular_grade DECIMAL(5,2) COMMENT '骞虫椂鎴愮哗',
    final_grade DECIMAL(5,2) COMMENT '鏈熸湯鎴愮哗',
    total_grade DECIMAL(5,2) COMMENT '鎬昏瘎鎴愮哗',
    grade_point DECIMAL(3,2) COMMENT '缁╃偣',
    attendance_count INT DEFAULT 0 COMMENT '鍑哄嫟娆℃暟',
    absence_count INT DEFAULT 0 COMMENT '缂哄嫟娆℃暟',
    grade_remark VARCHAR(500) COMMENT '鎴愮哗澶囨敞',
    sync_time DATETIME NOT NULL COMMENT '鍚屾鏃堕棿',
    sync_source VARCHAR(20) NOT NULL COMMENT '鍚屾鏉ユ簮',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    
    FOREIGN KEY (course_code) REFERENCES school_course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_student (student_id),
    INDEX idx_course (course_code),
    INDEX idx_class (class_id),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_selection_status (selection_status),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='閫夎璁板綍琛?;

-- 3.2 绛夌骇鑰冭瘯鎴愮哗琛?
CREATE TABLE IF NOT EXISTS level_exam_scores (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    student_id VARCHAR(50) NOT NULL COMMENT '瀛﹀彿',
    student_name VARCHAR(100) COMMENT '瀛︾敓濮撳悕锛圕DSP 涓嶆彁渚涳紝鍙负绌猴級',
    exam_type VARCHAR(50) COMMENT '鑰冭瘯绫诲瀷锛圕DSP 涓嶆彁渚涳紝浠?examName 鎺ㄦ柇锛?,
    exam_name VARCHAR(100) NOT NULL COMMENT '鑰冭瘯鍚嶇О',
    admission_ticket_no VARCHAR(30) COMMENT '鍑嗚€冭瘉鍙?,
    exam_date DATE NOT NULL COMMENT '鑰冭瘯鏃堕棿',
    score DECIMAL(5,2) NOT NULL COMMENT '鎴愮哗',
    grade VARCHAR(20) COMMENT '绛夌骇',
    is_passed BOOLEAN COMMENT '鏄惁閫氳繃锛圕DSP 涓嶆彁渚涳紝浠?score 鎺ㄦ柇锛?,
    passing_score DECIMAL(5,2) COMMENT '鍚堟牸鍒嗘暟绾?,
    listening_score DECIMAL(5,2) COMMENT '鍚姏鎴愮哗',
    reading_score DECIMAL(5,2) COMMENT '闃呰鎴愮哗',
    writing_score DECIMAL(5,2) COMMENT '鍐欎綔鎴愮哗',
    translation_score DECIMAL(5,2) COMMENT '缈昏瘧鎴愮哗',
    certificate_no VARCHAR(50) COMMENT '璇佷功缂栧彿',
    certificate_date DATE COMMENT '鍙戣瘉鏃ユ湡',
    exam_location VARCHAR(100) COMMENT '鑰冭瘯鍦扮偣',
    exam_room_no VARCHAR(20) COMMENT '鑰冨満鍙?,
    seat_no VARCHAR(10) COMMENT '搴т綅鍙?,
    registration_date DATE COMMENT '鎶ュ悕鏃堕棿',
    score_release_date DATE COMMENT '鎴愮哗鍙戝竷鏃堕棿',
    data_source VARCHAR(50) COMMENT '鏁版嵁鏉ユ簮',
    remark VARCHAR(500) COMMENT '澶囨敞',
    sync_time DATETIME NOT NULL COMMENT '鍚屾鏃堕棿',
    sync_source VARCHAR(20) NOT NULL COMMENT '鍚屾鏉ユ簮',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    
    UNIQUE KEY uk_student_exam (student_id, exam_type, exam_date),
    INDEX idx_student_id (student_id),
    INDEX idx_exam_type (exam_type),
    INDEX idx_exam_date (exam_date),
    INDEX idx_is_passed (is_passed),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绛夌骇鑰冭瘯鎴愮哗琛?;

-- 4. 鏁欏璁″垝灞?
CREATE TABLE IF NOT EXISTS teaching_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    plan_id VARCHAR(30) NOT NULL UNIQUE COMMENT '璁″垝缂栧彿',
    plan_name VARCHAR(200) NOT NULL COMMENT '璁″垝鍚嶇О',
    major_code VARCHAR(20) NOT NULL COMMENT '涓撲笟浠ｇ爜',
    major_name VARCHAR(100) NOT NULL COMMENT '涓撲笟鍚嶇О',
    grade VARCHAR(10) NOT NULL COMMENT '骞寸骇',
    schooling_length INT NOT NULL COMMENT '瀛﹀埗',
    degree_type VARCHAR(20) NOT NULL COMMENT '瀛︿綅绫诲瀷',
    education_level VARCHAR(20) NOT NULL COMMENT '鍩瑰吇灞傛',
    college_code VARCHAR(20) NOT NULL COMMENT '瀛﹂櫌浠ｇ爜',
    college_name VARCHAR(100) NOT NULL COMMENT '瀛﹂櫌鍚嶇О',
    total_credits DECIMAL(5,2) NOT NULL COMMENT '鎬诲鍒嗚姹?,
    required_credits DECIMAL(5,2) NOT NULL COMMENT '蹇呬慨瀛﹀垎',
    elective_credits DECIMAL(5,2) NOT NULL COMMENT '閫変慨瀛﹀垎',
    practice_credits DECIMAL(5,2) COMMENT '瀹炶返瀛﹀垎',
    general_education_credits DECIMAL(5,2) COMMENT '閫氳瘑鏁欒偛瀛﹀垎',
    major_core_credits DECIMAL(5,2) COMMENT '涓撲笟鏍稿績瀛﹀垎',
    major_elective_credits DECIMAL(5,2) COMMENT '涓撲笟閫変慨瀛﹀垎',
    training_objective TEXT COMMENT '鍩瑰吇鐩爣',
    training_requirements TEXT COMMENT '鍩瑰吇瑕佹眰',
    main_disciplines VARCHAR(200) COMMENT '涓诲共瀛︾',
    core_courses TEXT COMMENT '鏍稿績璇剧▼',
    plan_status VARCHAR(20) NOT NULL COMMENT '璁″垝鐘舵€?,
    effective_date DATE NOT NULL COMMENT '鐢熸晥鏃ユ湡',
    expiry_date DATE COMMENT '澶辨晥鏃ユ湡',
    creator VARCHAR(50) COMMENT '鍒跺畾浜?,
    reviewer VARCHAR(50) COMMENT '瀹℃牳浜?,
    review_date DATE COMMENT '瀹℃牳鏃堕棿',
    version VARCHAR(20) COMMENT '鐗堟湰鍙?,
    revision_notes TEXT COMMENT '淇璇存槑',
    attachment_path VARCHAR(500) COMMENT '闄勪欢璺緞',
    sync_time DATETIME NOT NULL COMMENT '鍚屾鏃堕棿',
    sync_source VARCHAR(20) NOT NULL COMMENT '鍚屾鏉ユ簮',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    
    INDEX idx_major_grade (major_code, grade),
    INDEX idx_plan_status (plan_status),
    INDEX idx_effective_date (effective_date),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏁欏璁″垝涓昏〃';

CREATE TABLE IF NOT EXISTS teaching_plan_courses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    detail_id VARCHAR(30) NOT NULL UNIQUE COMMENT '鏄庣粏缂栧彿',
    plan_id VARCHAR(30) NOT NULL COMMENT '璁″垝缂栧彿',
    course_code VARCHAR(20) NOT NULL COMMENT '璇剧▼浠ｇ爜',
    course_name VARCHAR(100) NOT NULL COMMENT '璇剧▼鍚嶇О',
    course_type VARCHAR(20) NOT NULL COMMENT '璇剧▼绫诲瀷',
    course_category VARCHAR(50) COMMENT '璇剧▼绫诲埆',
    credits DECIMAL(4,2) NOT NULL COMMENT '瀛﹀垎',
    hours INT NOT NULL COMMENT '瀛︽椂',
    theory_hours INT COMMENT '鐞嗚瀛︽椂',
    lab_hours INT COMMENT '瀹為獙瀛︽椂',
    semester INT NOT NULL COMMENT '寮€璇惧鏈?,
    assessment_method VARCHAR(20) COMMENT '鑰冩牳鏂瑰紡',
    prerequisite_courses VARCHAR(200) COMMENT '鍏堜慨璇剧▼',
    sort_order INT COMMENT '鎺掑簭鍙?,
    sync_time DATETIME NOT NULL COMMENT '鍚屾鏃堕棿',
    sync_source VARCHAR(20) NOT NULL COMMENT '鍚屾鏉ユ簮',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',

    INDEX idx_plan_id (plan_id),
    INDEX idx_course_code (course_code),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏁欏璁″垝璇剧▼鏄庣粏琛?;

-- ============================================================================
-- 6. 杈呭姪琛?
-- ============================================================================

-- 6.1 鍚屾鏃ュ織琛?
CREATE TABLE IF NOT EXISTS sync_logs (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sync_type` VARCHAR(50) NOT NULL COMMENT '鍚屾绫诲瀷: FULL/INCREMENTAL',
    `data_source` VARCHAR(50) NOT NULL COMMENT '鏁版嵁婧?,
    `sync_method` VARCHAR(50) NOT NULL COMMENT '鍚屾鏂规硶: REPLACE_ALL/UPSERT',
    `status` VARCHAR(50) NOT NULL COMMENT '鐘舵€? RUNNING/SUCCESS/FAILED',
    `total_count` INT DEFAULT 0 COMMENT '鎬昏褰曟暟',
    `success_count` INT DEFAULT 0 COMMENT '鎴愬姛鏁?,
    `fail_count` INT DEFAULT 0 COMMENT '澶辫触鏁?,
    `skip_count` INT DEFAULT 0 COMMENT '璺宠繃鏁?,
    `start_time` DATETIME COMMENT '寮€濮嬫椂闂?,
    `end_time` DATETIME COMMENT '缁撴潫鏃堕棿',
    `duration_ms` BIGINT COMMENT '鑰楁椂锛堟绉掞級',
    `error_message` TEXT COMMENT '閿欒淇℃伅',
    `trigger_type` VARCHAR(50) COMMENT '瑙﹀彂绫诲瀷: MANUAL/SCHEDULED',
    `operator` VARCHAR(50) COMMENT '鎿嶄綔浜?,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    PRIMARY KEY (`id`),
    INDEX idx_data_source (`data_source`),
    INDEX idx_status (`status`),
    INDEX idx_sync_type (`sync_type`),
    INDEX idx_start_time (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍚屾鏃ュ織琛?;

-- 6.2 瀛楁鏄犲皠閰嶇疆琛?
CREATE TABLE IF NOT EXISTS field_mappings (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `data_source` VARCHAR(50) NOT NULL COMMENT '鏁版嵁婧愭爣璇?,
    `source_field` VARCHAR(100) NOT NULL COMMENT '婧愬瓧娈靛悕锛圓PI杩斿洖鐨凧SON閿悕锛?,
    `target_field` VARCHAR(100) NOT NULL COMMENT '鐩爣瀛楁鍚嶏紙Java瀹炰綋灞炴€у悕锛?,
    `source_type` VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '婧愬瓧娈电被鍨?,
    `target_type` VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '鐩爣瀛楁绫诲瀷',
    `converter` VARCHAR(50) NOT NULL DEFAULT 'NONE' COMMENT '杞崲鍣? NONE/SPLIT/BOOLEAN/DATE',
    `converter_config` JSON COMMENT '杞崲鍣ㄩ厤缃紙JSON鏍煎紡锛?,
    `default_value` VARCHAR(500) COMMENT '榛樿鍊硷紙婧愭暟鎹负绌烘椂浣跨敤锛?,
    `is_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '鏄惁蹇呭～',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '鎺掑簭椤哄簭',
    `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '鏄惁鍚敤',
    `remark` VARCHAR(500) COMMENT '瀛楁鏄犲皠璇存槑',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    PRIMARY KEY (`id`),
    INDEX idx_data_source (`data_source`),
    INDEX idx_target_field (`target_field`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='瀛楁鏄犲皠閰嶇疆琛?;


-- =============================================================
-- V10__user_and_text_updates.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V10: 鐢ㄦ埛 + 鏂囨湰 + 璇嶆眹琛ㄥ瓧娈佃ˉ涓侊紙鏁村悎锛?
-- 鏁村悎鑷? 鍘?V10 (users nickname+avatar) + 鍘?V11 (texts featured + vocabulary_books SM-2)
-- 娉ㄦ剰: Flyway 鍦ㄦ湰椤圭洰绂佺敤 (application.yml flyway.enabled=false)锛?
--       瀹為檯鐢熶骇閮ㄧ讲璧?docker/mysql/init/04-apply-all-migrations.sql
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈹€鈹€ Part 1: users 琛?nickname 鍒?+ avatar_url 閲嶅懡鍚?鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 鍘?V10: 娣诲姞 users.nickname 鍒楋紝閲嶅懡鍚?users.avatar_url 鈫?avatar
-- 鍘熷洜: User 瀹炰綋浣跨敤 nickname 鍜?avatar 瀛楁

SET @dbname = DATABASE();
SET @tablename = 'users';

-- nickname
SET @colname = 'nickname';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN nickname VARCHAR(50) DEFAULT NULL COMMENT ''鏄电О'' AFTER role'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 閲嶅懡鍚?avatar_url 鈫?avatar锛堝箓绛夛級
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

-- 鈹€鈹€ Part 2: texts 琛?featured 瀛楁 + vocabulary_books 琛?SM-2 瀛楁 鈹€鈹€
-- 鍘?V11: 鍘熷洜: Text 瀹炰綋鏈?cover_url/is_featured/featured_priority 瀛楁锛?
--        VocabularyBook 瀹炰綋鏈?repetition/interval_days/easiness_factor/next_review_time

-- texts.cover_url
SET @colname = 'cover_url';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'texts' AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE texts ADD COLUMN cover_url VARCHAR(255) DEFAULT NULL COMMENT ''灏侀潰鍥綰RL'' AFTER chinese_parallel_markdown_content'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;


-- =============================================================
-- V11__role_mapping_cleanup.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V11: 娓呯悊 sso_config 琛ㄤ腑鏃犳晥鐨勮鑹叉槧灏勮鍒欙紝淇琚鏄犲皠鐨勭敤鎴疯鑹?
-- 鍘熷悕: V12__fix_role_mapping_cleanup.sql锛堥噸鍛藉悕涓?V11锛屼富棰樼嫭绔嬶級
--
-- 闂锛歴so_config 琛ㄤ腑鍙兘瀛樺湪 config_key='sso.role_mapping.ou' 鐨勮剰瑙勫垯锛?
-- strip 鍓嶇紑鍚?key='ou'锛岀敱浜庢棫鐗?RoleMappingService 浣跨敤 contains() 鍖归厤锛?
-- 'ou' 鍖归厤鎵€鏈?LDAP DN锛堥兘鍖呭惈 'ou'锛夛紝瀵艰嚧鎵€鏈夌敤鎴疯鏄犲皠涓哄悓涓€瑙掕壊銆?
-- 鏂扮増鏀圭敤 DN 缁勪欢绮剧‘鍖归厤锛屽悓鏃舵杩佺Щ娓呯悊鏁版嵁搴撲腑鐨勮剰瑙勫垯銆?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 1. 鍒犻櫎鏃犳晥鐨勭煭 key 瑙勫垯锛坘ey strip 鍚庝笉鍚?'='锛屽 'ou'銆?dc' 绛夛級
DELETE FROM sso_config
WHERE config_type = 'ROLE_MAPPING'
  AND REPLACE(config_key, 'sso.role_mapping.', '') NOT LIKE '%=%';

-- 2. 淇琚鏄犲皠鐨勭敤鎴疯鑹?
--    鏃т唬鐮佸洜 'ou' 瑙勫垯鎶婃墍鏈?CAS 鐢ㄦ埛鏄犲皠涓?TEACHER
--    淇绛栫暐锛氬皢琚?SSO 閿欒鏄犲皠涓?TEACHER 浣嗗疄闄呮槸瀛︾敓鐨勭敤鎴锋仮澶嶄负 STUDENT
UPDATE users u
INNER JOIN sso_user_bindings b ON u.id = b.user_id AND b.deleted = 0
SET u.role = 'STUDENT', u.updated_time = NOW()
WHERE u.role = 'TEACHER'
  AND u.sso_bound = 1
  AND u.deleted = 0
  AND b.auto_approved = 1
  AND b.sso_member_of IS NOT NULL
  AND b.sso_member_of NOT LIKE '%ou=鏁欏笀%'
  AND b.sso_member_of NOT LIKE '%ou=teacher%';


-- =============================================================
-- V12__school_data_and_videos.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V12: 瀛︽牎鏁版嵁闆嗘垚 + 瑙嗛绠＄悊琛?
-- 鏁村悎鑷? V12__school_data_integration.sql + V12__add_videos_table.sql
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V12: 瀛︽牎鏁版嵁闆嗘垚鏁村悎
-- 鏁村悎鑷? V12.1 (瀛楁鏄犲皠) + V13 (system_config seed)
--       + V14 (student_schedule 琛? + V18 (鍔熻兘寮€鍏?seed)
--       + V19 (瀛楁鏄犲皠) + V20 (API 璁よ瘉 appId+appSecret)
--       + V20-align (2026-04-21 鎸?CDSP 瀹樻柟 PDF 瀵归綈 field_mappings)
--
-- 鍘嗗彶鍚堝苟璇存槑:
--   - 2026-04-19: 涔濅釜琛ヤ竵 (V12.1/V13/V14/V16/V18/V19/V20) 棣栨鍚堝苟鍏?V12
--   - 2026-04-21: 鎸夊畼鏂?PDF 鍘熸枃淇 field_mappings, 鍓旈櫎 V12.1/V19 閬楃暀
--     snake_case 鍐椾綑鏄犲皠锛團ieldMappingServiceImpl 浼氳嚜鍔ㄥ皢 target_field
--     杞?camelCase, snake_case/camelCase 涓ゅ鏄瓑浠烽噸澶嶏級, 鍚堝苟 V20-align
--   - 2026-05-05: 绉婚櫎 V16 (course鈫抯chool_course 閲嶅懡鍚? 鍐椾綑浠ｇ爜锛?
--     V9 宸叉纭垱寤?school_course 琛紝鏃犻渶鍏煎鎬у鐞?
--
-- 娉ㄦ剰: Flyway 鍦ㄦ湰椤圭洰鐢熶骇鐜绂佺敤 (application.yml flyway.enabled=false),
--       瀹為檯鐢熶骇閮ㄧ讲璧?docker/mysql/init/04-apply-all-migrations.sql
--       CI test profile 鍚敤 Flyway (application-test.yml)
--
-- 鎵ц椤哄簭:
--   Part 1: Schema 鍙樻洿
--   Part 2: system_config seed
--   Part 3: field_mappings 鏁版嵁锛堟寜 PDF 鍘熸枃锛?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- Part 1.1: 鍒涘缓 student_schedule 琛?(鍘?V14 Part 1)
-- 瀛樺偍瀛︾敓涓汉璇捐〃鏁版嵁 (/api/xskb)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
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

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- Part 2.1: 鏁版嵁婧愰厤缃瀛?(鍘?V13)
-- NOTE: api_app_id / api_app_secret 鍦?Part 2.3 娣诲姞锛圴20锛?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'source', 'api', '鏁版嵁婧愮被鍨? api 鎴?etl', 0),
('school_data', 'api_base_url', 'https://api.imau.edu.cn', 'API鍩虹URL', 0),
('school_data', 'api_connect_timeout', '5000', 'API杩炴帴瓒呮椂(ms)', 0),
('school_data', 'api_read_timeout', '30000', 'API璇诲彇瓒呮椂(ms)', 0),
('school_data', 'api_retry_attempts', '3', 'API閲嶈瘯娆℃暟', 0),
('school_data', 'etl_jdbc_url', '', 'ETL JDBC杩炴帴URL', 0),
('school_data', 'etl_username', '', 'ETL鏁版嵁搴撶敤鎴峰悕', 0),
('school_data', 'etl_password', '', 'ETL鏁版嵁搴撳瘑鐮?, 1),
('school_data', 'etl_connection_timeout', '30000', 'ETL杩炴帴瓒呮椂(ms)', 0),
('school_data', 'etl_query_timeout', '60000', 'ETL鏌ヨ瓒呮椂(ms)', 0);

-- 鍚屾璋冨害閰嶇疆
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'sync_enabled', 'false', '鏄惁鍚敤鍚屾璋冨害', 0),
('school_data', 'sync_full_cron', '0 0 2 * * ?', '鍏ㄩ噺鍚屾cron', 0),
('school_data', 'sync_incremental_cron', '0 0 * * * ?', '澧為噺鍚屾cron', 0),
('school_data', 'sync_validation_cron', '0 0 4 * * ?', '鏁版嵁鏍￠獙cron', 0),
('school_data', 'sync_batch_size', '500', '鎵归噺澶у皬', 0),
('school_data', 'sync_timeout_minutes', '60', '鍚屾瓒呮椂(鍒嗛挓)', 0);

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- Part 2.2: 鍔熻兘寮€鍏?seed (鍘?V18)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'ops.school_data_browser', 'false', '杩愮淮绔鏍℃暟鎹祻瑙堝姛鑳斤紙琛屾斂鐝€佽绋嬨€佹暀瀛︾彮绛夛級', 0),
('feature_toggle', 'ops.school_data_config', 'false', '杩愮淮绔鏍℃暟鎹簮閰嶇疆鍔熻兘锛圓PI/ETL鍒囨崲銆佽繛鎺ユ祴璇曪級', 0),
('feature_toggle', 'page_schedule', 'false', '瀛︾敓绔琛ㄦ煡鐪嬪姛鑳斤紙浠庢暀鍔＄郴缁熷悓姝ヨ琛ㄦ暟鎹級', 0);

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- Part 2.3: API 璁よ瘉 appId + appSecret 绛惧悕 (鍘?V20)
-- 瀹樻柟鎺ュ彛鏂囨。: sign = Base64( MD5( appId + appSecret + timestamp ) )
-- 璇锋眰澶? appId / timestamp / sign
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'api_app_id', '', 'API 搴旂敤 ID锛堢敱瀛︽牎鏁版嵁骞冲彴鍒嗛厤锛岀敤浜庣鍚嶈璇侊級', 1),
('school_data', 'api_app_secret', '', 'API 搴旂敤 Secret锛堢敱瀛︽牎鏁版嵁骞冲彴鍒嗛厤锛屼笌 appId + timestamp 缁勫悎鐢熸垚绛惧悕锛?, 1);

-- 娓呯悊鏃ч儴缃查仐鐣欑殑 api_token锛圔earer Token 璁よ瘉宸插簾寮冿紱鍏ㄦ柊閮ㄧ讲涓嶄細鏈夋璁板綍锛?
DELETE FROM `system_config`
WHERE `config_group` = 'school_data' AND `config_key` = 'api_token';

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- Part 3: field_mappings 鎸?CDSP 瀹樻柟 PDF 瀵归綈 (鍘?V20-align)
--
-- 鍩轰簬 9 浠?cdsp.imau.edu.cn data-api/v2 瀹樻柟鎺ュ彛鏂囨。鍘熸枃澶嶆牳锛?026-04-21锛夛細
--   琛屾斂鐝熀鏈俊鎭?/ 璇剧▼鍩烘湰淇℃伅 / 鏈笓绉戠敓璇剧▼淇℃伅 / 璇剧▼瀹夋帓淇℃伅 /
--   瀛︾敓閫夎 / 鏈笓绉戠敓绛夌骇鑰冭瘯鎴愮哗 / 鏁欏璁″垝淇℃伅 / 鏁欏笀鎺堣 / 瀛︾敓璇捐〃
--
-- 閰嶅浠ｇ爜:
--   - FieldMappingServiceImpl.applyMapping 澶у皬鍐欎笉鏁忔劅鏌ヨ〃
--     (PDF 杩斿洖瀛楁 3 涓鐐?UPPERCASE + 6 涓?lowercase)
--   - SchoolDataSyncServiceImpl.upsertEntity: teaching_class 鏁版嵁婧?
--     璺敱鍒?upsertSchoolCourse (bzkcxx 绔偣瀹為檯杩斿洖璇剧▼淇℃伅锛岄潪鏁欏鐝?
--   - target_field 缁熶竴 camelCase, 鍖归厤 Java 瀹炰綋灞炴€?
--
-- 宸茬煡闄愬埗锛堥潪鏈?part 鑱岃矗锛?
--   - course_selection PDF 浠呰繑鍥?XH/KSXZDM/KSXZMC/KCBH 4 涓瓧娈碉紝
--     杩滃皯浜?CourseSelection 瀹炰綋 NOT NULL 鍒楁暟锛寀psert 鍙兘澶辫触銆?
--   - teaching_plans 杩斿洖鐨勬槸鏁欏璁″垝涓殑璇剧▼鏉＄洰锛岄儴鍒嗗疄浣撳繀濉?
--     瀛楁 (planName/planStatus) PDF 涓嶆彁渚涖€?
--   - 涓婅堪闇€閫氳繃 DDL 鏀惧鎴栭粯璁ゅ€肩瓥鐣ヨВ鍐筹紝鏈?part 浠呯‘淇濆瓧娈垫槧灏勬湰韬纭€?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- Step 3.0: 娓呯┖ V12 鍘嗗彶閬楃暀鐨勯敊璇?mappings锛堝箓绛夛級
DELETE FROM field_mappings WHERE data_source IN (
    'administrative_class', 'school_course', 'teaching_class',
    'course_schedule', 'course_selection', 'level_exam_scores',
    'teaching_plans', 'teacher_courses', 'student_schedule'
);

-- 1. administrative_class (cdsp_1710949911983374368 / 琛屾斂鐝熀鏈俊鎭?
-- PDF fields (UPPERCASE): XZBH, BJMC, XSLB, SSXYBM, SSXYMC, SSNJ, BZRGH, BZRXM, ZY, JBNY
-- 娉ㄦ剰锛欳DSP API 鍙兘杩斿洖涓枃瀛楁鍚嶏紝闇€鍚屾椂鏀寔鑻辨枃缂╁啓鍜屼腑鏂囧瓧娈靛悕
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'XZBH',         'class_code',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '琛屾斂鐝紪鍙凤紙鑻辨枃缂╁啓锛?),
('administrative_class', '琛屾斂鐝紪鍙?,   'class_code',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '琛屾斂鐝紪鍙凤紙涓枃锛?),
('administrative_class', 'BJMC',         'class_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '鐝骇鍚嶇О锛堣嫳鏂囩缉鍐欙級'),
('administrative_class', '鐝骇鍚嶇О',     'class_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '鐝骇鍚嶇О锛堜腑鏂囷級'),
('administrative_class', 'XSLB',         'class_type',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '瀛︾敓绫诲埆锛堣嫳鏂囩缉鍐欙級'),
('administrative_class', '瀛︾敓绫诲埆',     'class_type',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '瀛︾敓绫诲埆锛堜腑鏂囷級'),
('administrative_class', 'SSXYBM',       'college_code',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4,  1, '鎵€灞炲闄㈢紪鐮侊紙鑻辨枃缂╁啓锛?),
('administrative_class', '鎵€灞炲闄㈢紪鐮?, 'college_code',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4,  1, '鎵€灞炲闄㈢紪鐮侊紙涓枃锛?),
('administrative_class', 'SSXYMC',       'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '鎵€灞炲闄㈠悕绉帮紙鑻辨枃缂╁啓锛?),
('administrative_class', '鎵€灞炲闄㈠悕绉?, 'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '鎵€灞炲闄㈠悕绉帮紙涓枃锛?),
('administrative_class', 'SSNJ',         'grade',             'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '鎵€灞炲勾绾э紙鑻辨枃缂╁啓锛?),
('administrative_class', '鎵€灞炲勾绾?,     'grade',             'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '鎵€灞炲勾绾э紙涓枃锛?),
('administrative_class', 'BZRGH',        'head_teacher_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '鐝富浠诲伐鍙凤紙鑻辨枃缂╁啓锛?),
('administrative_class', '鐝富浠诲伐鍙?,   'head_teacher_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '鐝富浠诲伐鍙凤紙涓枃锛?),
('administrative_class', 'BZRXM',        'head_teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '鐝富浠诲鍚嶏紙鑻辨枃缂╁啓锛?),
('administrative_class', '鐝富浠诲鍚?,   'head_teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '鐝富浠诲鍚嶏紙涓枃锛?),
('administrative_class', 'ZY',           'major_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '涓撲笟鍚嶇О锛堣嫳鏂囩缉鍐欙級'),
('administrative_class', '涓撲笟鍚嶇О',     'major_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '涓撲笟鍚嶇О锛堜腑鏂囷級'),
('administrative_class', 'JBNY',         'establish_date',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 10, 1, '寤虹彮骞存湀锛堣嫳鏂囩缉鍐欙級'),
('administrative_class', '寤虹彮骞存湀',     'establish_date',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 10, 1, '寤虹彮骞存湀锛堜腑鏂囷級');

-- 2. school_course (cdsp_1715290945064353805 / 璇剧▼鍩烘湰淇℃伅)
-- PDF fields (lowercase): kcxs, kcdm, kcxf, kcywmc, kcjj, kslx, kkyx, kcmc
-- 娉ㄦ剰锛欳DSP API 鍙兘杩斿洖涓枃瀛楁鍚嶏紝闇€鍚屾椂鏀寔鑻辨枃缂╁啓鍜屼腑鏂囧瓧娈靛悕
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('school_course', 'kcdm',       'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '璇剧▼浠ｇ爜锛堣嫳鏂囩缉鍐欙級'),
('school_course', '璇剧▼浠ｇ爜',   'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '璇剧▼浠ｇ爜锛堜腑鏂囷級'),
('school_course', 'kcmc',       'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '璇剧▼鍚嶇О锛堣嫳鏂囩缉鍐欙級'),
('school_course', '璇剧▼鍚嶇О',   'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '璇剧▼鍚嶇О锛堜腑鏂囷級'),
('school_course', 'kcywmc',     'course_name_en',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '璇剧▼鑻辨枃鍚嶇О锛堣嫳鏂囩缉鍐欙級'),
('school_course', '璇剧▼鑻辨枃鍚嶇О', 'course_name_en',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '璇剧▼鑻辨枃鍚嶇О锛堜腑鏂囷級'),
('school_course', 'kcxf',       'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '璇剧▼瀛﹀垎锛堣嫳鏂囩缉鍐欙級'),
('school_course', '璇剧▼瀛﹀垎',   'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '璇剧▼瀛﹀垎锛堜腑鏂囷級'),
('school_course', 'kcxs',       'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '璇剧▼瀛︽椂锛堣嫳鏂囩缉鍐欙級'),
('school_course', '璇剧▼瀛︽椂',   'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '璇剧▼瀛︽椂锛堜腑鏂囷級'),
('school_course', 'kcjj',       'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '璇剧▼绠€浠嬶紙鑻辨枃缂╁啓锛?),
('school_course', '璇剧▼绠€浠?,   'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '璇剧▼绠€浠嬶紙涓枃锛?),
('school_course', 'kslx',       'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '鑰冭瘯绫诲瀷锛堣嫳鏂囩缉鍐欙級'),
('school_course', '鑰冭瘯绫诲瀷',   'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '鑰冭瘯绫诲瀷锛堜腑鏂囷級'),
('school_course', 'kkyx',       'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '寮€璇鹃櫌绯伙紙鑻辨枃缂╁啓锛?),
('school_course', '寮€璇鹃櫌绯?,   'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '寮€璇鹃櫌绯伙紙涓枃锛?);

-- 3. teaching_class (cdsp_1721804030860152850 / 鏈笓绉戠敓璇剧▼淇℃伅)
-- 姝ょ鐐瑰疄闄呰繑鍥炶绋嬩俊鎭紙闈炴暀瀛︾彮锛夛紝浠ｇ爜宸茶矾鐢卞埌 upsertSchoolCourse
-- PDF fields (UPPERCASE): KCXS, KCXF, KKYX, KCJJ, KCYWMC, KCMC, KCH, KSLX
-- 娉ㄦ剰锛欳DSP API 鍙兘杩斿洖涓枃瀛楁鍚嶏紝闇€鍚屾椂鏀寔鑻辨枃缂╁啓鍜屼腑鏂囧瓧娈靛悕
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_class', 'KCH',          'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '璇剧▼鍙凤紙鑻辨枃缂╁啓锛?),
('teaching_class', '璇剧▼鍙?,       'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '璇剧▼鍙凤紙涓枃锛?),
('teaching_class', 'KCMC',         'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '璇剧▼鍚嶇О锛堣嫳鏂囩缉鍐欙級'),
('teaching_class', '璇剧▼鍚嶇О',     'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '璇剧▼鍚嶇О锛堜腑鏂囷級'),
('teaching_class', 'KCYWMC',       'course_name_en',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '璇剧▼鑻辨枃鍚嶇О锛堣嫳鏂囩缉鍐欙級'),
('teaching_class', '璇剧▼鑻辨枃鍚嶇О', 'course_name_en',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '璇剧▼鑻辨枃鍚嶇О锛堜腑鏂囷級'),
('teaching_class', 'KCXF',         'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '璇剧▼瀛﹀垎锛堣嫳鏂囩缉鍐欙級'),
('teaching_class', '璇剧▼瀛﹀垎',     'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '璇剧▼瀛﹀垎锛堜腑鏂囷級'),
('teaching_class', 'KCXS',         'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '璇剧▼瀛︽椂锛堣嫳鏂囩缉鍐欙級'),
('teaching_class', '璇剧▼瀛︽椂',     'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '璇剧▼瀛︽椂锛堜腑鏂囷級'),
('teaching_class', 'KCJJ',         'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '璇剧▼绠€浠嬶紙鑻辨枃缂╁啓锛?),
('teaching_class', '璇剧▼绠€浠?,     'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '璇剧▼绠€浠嬶紙涓枃锛?),
('teaching_class', 'KSLX',         'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '鑰冭瘯绫诲瀷锛堣嫳鏂囩缉鍐欙級'),
('teaching_class', '鑰冭瘯绫诲瀷',     'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '鑰冭瘯绫诲瀷锛堜腑鏂囷級'),
('teaching_class', 'KKYX',         'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '寮€璇鹃櫌绯伙紙鑻辨枃缂╁啓锛?),
('teaching_class', '寮€璇鹃櫌绯?,     'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '寮€璇鹃櫌绯伙紙涓枃锛?);

-- 4. course_schedule (cdsp_1715290377801515044 / 璇剧▼瀹夋帓淇℃伅)
-- PDF fields (lowercase): xkkch, xn, xq, skxqj, skjc, kczc, skdd
-- xq 杩斿洖涓枃 "绉?/"鏄? varchar锛宼arget_type=STRING锛宻ervice 灞傚仛涓枃鈫扞nteger 杞崲
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_schedule', 'xkkch',        'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '閫夎璇惧彿锛堜綔 class_id锛?),
('course_schedule', '閫夎璇惧彿',     'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '閫夎璇惧彿锛堜腑鏂囧瓧娈靛悕锛?),
('course_schedule', 'skxqj',        'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '涓婅鏄熸湡鍑?1-7锛堣嫳鏂囩缉鍐欙級'),
('course_schedule', '涓婅鏄熸湡鍑?,   'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '涓婅鏄熸湡鍑?1-7锛堜腑鏂囧瓧娈靛悕锛?),
('course_schedule', 'skjc',         'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '涓婅鑺傛锛堣嫳鏂囩缉鍐欙級'),
('course_schedule', '涓婅鑺傛',     'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '涓婅鑺傛锛堜腑鏂囧瓧娈靛悕锛?),
('course_schedule', 'kczc',         'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '璇剧▼鍛ㄦ锛堟弿杩版€э紝鑻辨枃缂╁啓锛?),
('course_schedule', '璇剧▼鍛ㄦ',     'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '璇剧▼鍛ㄦ锛堟弿杩版€э紝涓枃瀛楁鍚嶏級'),
('course_schedule', 'skdd',         'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '涓婅鍦扮偣锛堣嫳鏂囩缉鍐欙級'),
('course_schedule', '涓婅鍦扮偣',     'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '涓婅鍦扮偣锛堜腑鏂囧瓧娈靛悕锛?),
('course_schedule', 'xn',           'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '瀛﹀勾锛圕DSP 鍗曞勾濡?"2024"锛岃嫳鏂囩缉鍐欙級'),
('course_schedule', '瀛﹀勾',         'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '瀛﹀勾锛堜腑鏂囧瓧娈靛悕锛?),
('course_schedule', 'xq',           'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '瀛︽湡涓枃 "绉?/"鏄?锛宻ervice 灞傝浆 Integer锛堣嫳鏂囩缉鍐欙級'),
('course_schedule', '瀛︽湡',         'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '瀛︽湡锛堜腑鏂囧瓧娈靛悕锛?);

-- 5. course_selection (cdsp_1715294802871926794 / 瀛︾敓閫夎)
-- PDF fields (UPPERCASE): XH, KSXZDM, KSXZMC, KCBH
-- 娉ㄦ剰锛歅DF 鍙繑鍥?4 涓瓧娈碉紝杩滃皯浜?CourseSelection 瀹炰綋 NOT NULL 鍒楁暟銆?
-- KCBH 鍦ㄩ摼寮忔媺鍙栦笌鍘嗗彶鍏煎涓寜鈥濊绋嬬彮鍙封€濅娇鐢紝搴旂粺涓€钀藉埌 classId銆?
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_selection', 'XH',     'student_id',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '瀛﹀彿'),
('course_selection', 'KCBH',   'class_id',          'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '璇剧▼鐝彿锛堜綔 class_id锛?),
('course_selection', 'KSXZDM', 'selection_status',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '鑰冭瘯鎬ц川浠ｇ爜锛堜綔閫夎鐘舵€侊級'),
('course_selection', 'KSXZMC', 'class_time',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '鑰冭瘯鎬ц川鍚嶇О锛堟殏瀛?classTime锛?);

-- 6. level_exam_scores (DS0076 / 鏈笓绉戠敓绛夌骇鑰冭瘯鎴愮哗)
-- PDF fields (lowercase): xh, xhxm, xm, kskm, ksrq, fskscj
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', 'xh',     'student_id',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '瀛﹀彿'),
('level_exam_scores', 'xm',     'student_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2, 1, '濮撳悕'),
('level_exam_scores', 'kskm',   'exam_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '鑰冭瘯绉戠洰锛堜綔鑰冭瘯鍚嶇О锛?),
('level_exam_scores', 'ksrq',   'exam_date',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 1, 4, 1, '鑰冭瘯鏃ユ湡'),
('level_exam_scores', 'fskscj', 'score',        'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 5, 1, '鑰冭瘯鎴愮哗锛堝垎鏁帮級');

-- 7. teaching_plans (cdsp_1715289655026466836 / 鏁欏璁″垝淇℃伅)
-- PDF fields (lowercase): zxs, skjszgh, kkdwdm, kkxq, cksm, ksfsdm, kkjhid, kkxn,
--   kcdm, kkxq_name, kkdwdm_name, ksfsmc
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_plans', 'kkjhid',      'plan_id',            'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '寮€璇捐鍒?ID锛坧lan_id 涓婚敭锛?),
('teaching_plans', 'kcdm',        'course_code',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2,  1, '璇剧▼浠ｇ爜'),
('teaching_plans', 'cksm',        'training_objective', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '鍙傝€冧功鐩紙浣滃煿鍏荤洰鏍囷級'),
('teaching_plans', 'zxs',         'total_credits',      'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4,  1, '鎬诲鏃讹紙杩戜技鎬诲鍒嗭級'),
('teaching_plans', 'skjszgh',     'creator',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '鎺堣鏁欏笀鑱屽伐鍙凤紙浣滃垱寤鸿€咃級'),
('teaching_plans', 'kkdwdm',      'college_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '寮€璇惧崟浣嶄唬鐮?),
('teaching_plans', 'kkdwdm_name', 'college_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '寮€璇惧崟浣嶅悕绉?),
('teaching_plans', 'kkxn',        'grade',              'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '寮€璇惧骞达紙浣滃勾绾э級'),
('teaching_plans', 'kkxq_name',   'plan_name',          'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '寮€璇惧鏈熷悕绉帮紙浣滆鍒掑悕绉帮級'),
('teaching_plans', 'ksfsdm',      'plan_status',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '鑰冭瘯鏂瑰紡浠ｇ爜锛堜綔璁″垝鐘舵€侊級'),
('teaching_plans', 'ksfsmc',      'version',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '鑰冭瘯鏂瑰紡鍚嶇О锛堜綔鐗堟湰锛?);

-- 8. teacher_courses (cdsp_1715294645677801497 / 鏁欏笀鎺堣)
-- PDF fields (lowercase): rkjs, xn, xq, kcdm, kxh, rkjsh, kcmc, kcbh, sfzj, kcbbm, rkjsxm
-- 鏁欏笀鎺堣鏁版嵁 upsert 鍒?TeachingClass 瀹炰綋锛坰ervice 浠ｇ爜鏃㈡湁閫昏緫锛?
-- 璇箟锛堟寜 PDF 杩斿洖绀轰緥鏍稿 2026-04-21锛?
--   kcdm = 璇剧▼浠ｇ爜锛堝 "594625"锛岃绋嬫湰韬紪鐮侊級鈫?courseCode
--   kcbh = 璇剧▼鐝彿锛堝 "51425977"锛屾煇娆″紑鐝殑鍏ㄥ眬鍞竴 ID锛夆啋 classId (TeachingClass 涓婚敭)
--   kxh  = 璇惧簭鍙凤紙鐝唴搴忓彿锛岄€氬父涓?kcdm 鐩稿悓锛夆啋 涓嶅叆搴?
--   xq   = 瀛︽湡锛孉PI 杩斿洖涓枃 "绉?/"鏄? varchar 鈫?target_type=STRING 鍏堜繚鐣欏師鍊硷紝
--          SchoolDataSyncServiceImpl.upsertTeachingClass 璇诲彇鏃跺啀鍋氫腑鏂団啋Integer 杞崲
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teacher_courses', 'kcbh',   'class_id',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '璇剧▼鐝彿锛堜綔 class_id锛屽叏灞€鍞竴寮€鐝?ID锛?),
('teacher_courses', 'kcdm',   'course_code',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '璇剧▼浠ｇ爜'),
('teacher_courses', 'kcmc',   'course_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '璇剧▼鍚嶇О'),
('teacher_courses', 'rkjsh',  'teacher_id',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '浠昏鏁欏笀宸ュ彿'),
('teacher_courses', 'rkjsxm', 'teacher_name',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '浠昏鏁欏笀濮撳悕'),
('teacher_courses', 'xn',     'academic_year', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '瀛﹀勾锛圕DSP 鍗曞勾鏍煎紡濡?"2024"锛?),
('teacher_courses', 'xq',     'semester',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '瀛︽湡 varchar 涓枃 "绉?/"鏄?锛宻ervice 灞傝浆 Integer'),
('teacher_courses', 'kcbbm',  'class_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '璇剧▼鐝埆鍚嶏紙浣滅彮绾у睍绀哄悕锛?);

-- 9. student_schedule (cdsp_1715294397274341377 / 瀛︾敓璇捐〃)
-- PDF fields (lowercase): zcdm, skjszgh, jsmc, lsxykh, kcmc, js, skcd, djj, xqj,
--   xkkh, xh, xq, xn, zcap, kcbh
-- xq 杩斿洖涓枃 "绉?/"鏄? varchar锛宼arget_type=STRING锛宻ervice 灞傚仛涓枃鈫扞nteger 杞崲
-- 娉ㄦ剰锛欳DSP API 瀹為檯杩斿洖涓枃瀛楁鍚嶏紙闈炶嫳鏂囩缉鍐欙級锛岄渶鍚屾椂鏀寔涓ょ鏍煎紡
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('student_schedule', '瀛﹀彿',           'student_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '瀛﹀彿锛堜腑鏂囧瓧娈靛悕锛?),
('student_schedule', 'xh',            'student_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '瀛﹀彿锛堣嫳鏂囩缉鍐欙級'),
('student_schedule', '閫夎璇惧彿',       'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '閫夎璇惧彿锛堜綔 class_id锛屼腑鏂囧瓧娈靛悕锛?),
('student_schedule', 'xkkh',          'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '閫夎璇惧彿锛堜綔 class_id锛岃嫳鏂囩缉鍐欙級'),
('student_schedule', '璇剧▼鐝彿',       'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '璇剧▼鐝彿锛堜綔 class_id锛屼腑鏂囧瓧娈靛悕锛?),
('student_schedule', '璇剧▼缂栧彿',       'course_code',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '璇剧▼缂栧彿锛堜腑鏂囧瓧娈靛悕锛岄潪蹇呭～锛?),
('student_schedule', 'kcbh',          'course_code',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '璇剧▼缂栧彿锛堣嫳鏂囩缉鍐欙紝闈炲繀濉級'),
('student_schedule', '璇剧▼鍚嶇О',       'course_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4,  1, '璇剧▼鍚嶇О锛堜腑鏂囧瓧娈靛悕锛?),
('student_schedule', 'kcmc',          'course_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4,  1, '璇剧▼鍚嶇О锛堣嫳鏂囩缉鍐欙級'),
('student_schedule', '涓婅鏁欏笀鑱屽伐鍙?, 'teacher_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '涓婅鏁欏笀鑱屽伐鍙凤紙涓枃瀛楁鍚嶏級'),
('student_schedule', 'skjszgh',       'teacher_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '涓婅鏁欏笀鑱屽伐鍙凤紙鑻辨枃缂╁啓锛?),
('student_schedule', '鏁欏笀鍚嶇О',       'teacher_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '鏁欏笀鍚嶇О锛堜腑鏂囧瓧娈靛悕锛?),
('student_schedule', 'jsmc',          'teacher_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '鏁欏笀鍚嶇О锛堣嫳鏂囩缉鍐欙級'),
('student_schedule', '鏄熸湡鍑?,         'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7,  1, '鏄熸湡鍑?1-7锛堜腑鏂囧瓧娈靛悕锛?),
('student_schedule', 'xqj',           'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7,  1, '鏄熸湡鍑?1-7锛堣嫳鏂囩缉鍐欙級'),
('student_schedule', '绗嚑鑺?,         'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8,  1, '绗嚑鑺傦紙涓枃瀛楁鍚嶏級'),
('student_schedule', 'djj',           'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8,  1, '绗嚑鑺傦紙鑻辨枃缂╁啓锛?),
('student_schedule', '鍛ㄦ瀹夋帓',       'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '鍛ㄦ瀹夋帓锛堝 "1-9"锛屼腑鏂囧瓧娈靛悕锛?),
('student_schedule', 'zcap',          'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '鍛ㄦ瀹夋帓锛堝 "1-9"锛岃嫳鏂囩缉鍐欙級'),
('student_schedule', '鏁欏',           'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '鏁欏锛堜腑鏂囧瓧娈靛悕锛?),
('student_schedule', 'js',            'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '鏁欏锛堣嫳鏂囩缉鍐欙級'),
('student_schedule', '瀛﹀勾',           'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '瀛﹀勾锛圕DSP 鍗曞勾濡?"2024"锛屼腑鏂囧瓧娈靛悕锛?),
('student_schedule', 'xn',            'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '瀛﹀勾锛圕DSP 鍗曞勾濡?"2024"锛岃嫳鏂囩缉鍐欙級'),
('student_schedule', '瀛︽湡',           'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 12, 1, '瀛︽湡涓枃 "绉?/"鏄?锛宻ervice 灞傝浆 Integer锛堜腑鏂囧瓧娈靛悕锛?),
('student_schedule', 'xq',            'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 12, 1, '瀛︽湡涓枃 "绉?/"鏄?锛宻ervice 灞傝浆 Integer锛堣嫳鏂囩缉鍐欙級');

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- Part 4: 瑙嗛绠＄悊琛?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?


-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- Part 4: 瑙嗛绠＄悊琛?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

CREATE TABLE IF NOT EXISTS `videos` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(200) NOT NULL COMMENT '瑙嗛鏍囬',
    `description` TEXT COMMENT '瑙嗛绠€浠?,
    `media_id` BIGINT DEFAULT NULL COMMENT '鍏宠仈鐨勫獟浣撹褰旾D',
    `cover_media_id` BIGINT DEFAULT NULL COMMENT '灏侀潰濯掍綋ID',
    `video_url` VARCHAR(500) DEFAULT NULL COMMENT '瑙嗛URL锛堝閮ㄩ摼鎺ユ椂浣跨敤锛?,
    `cover_url` VARCHAR(500) DEFAULT NULL COMMENT '灏侀潰URL',
    `subtitle_content` LONGTEXT COMMENT '瀛楀箷鍐呭锛圫RT/VTT鏍煎紡锛?,
    `user_id` BIGINT DEFAULT NULL COMMENT '涓婁紶鑰呯敤鎴稩D',
    `is_public` TINYINT DEFAULT 1 COMMENT '鏄惁鍏紑 0-绉佹湁 1-鍏紑',
    `is_featured` TINYINT(1) DEFAULT 0 COMMENT '鏄惁鎺ㄨ崘',
    `featured_priority` INT DEFAULT 0 COMMENT '鎺ㄨ崘浼樺厛绾?,
    `tags` VARCHAR(500) DEFAULT NULL COMMENT '鏍囩锛岄€楀彿鍒嗛殧',
    `view_count` INT DEFAULT 0 COMMENT '娴忚娆℃暟',
    `like_count` INT DEFAULT 0 COMMENT '鏀惰棌/鐐硅禐娆℃暟',
    `status` VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT '鐘舵€侊細DRAFT/PUBLISHED/ARCHIVED',
    `associated_text_id` BIGINT DEFAULT NULL COMMENT '鍏宠仈鐨勮鏂嘔D锛堢敤浜庢敹钘忔槧灏勶級',
    `transcription_status` VARCHAR(20) DEFAULT NULL COMMENT '杞綍鐘舵€侊細PENDING/PROCESSING/COMPLETED/FAILED',
    `deleted` TINYINT DEFAULT 0 COMMENT '鏄惁鍒犻櫎 0-鏈垹闄?1-宸插垹闄?,
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_media_id` (`media_id`),
    KEY `idx_is_public` (`is_public`),
    KEY `idx_is_featured` (`is_featured`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_associated_text_id` (`associated_text_id`),
    KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='瑙嗛琛?;


UPDATE field_mappings
SET is_active = 0
WHERE data_source = 'administrative_class'
  AND source_field IN ('SSXYBM', 'SSXYMC', '鎵€灞炲闄㈢紪鐮?, '鎵€灞炲闄㈠悕绉?);

INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'YXDM',       'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '瀛﹂櫌浠ｇ爜锛堣嫳鏂囩缉鍐欙級'),
('administrative_class', '瀛﹂櫌浠ｇ爜',   'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '瀛﹂櫌浠ｇ爜锛堜腑鏂囷級'),
('administrative_class', 'YXMC',       'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '瀛﹂櫌鍚嶇О锛堣嫳鏂囩缉鍐欙級'),
('administrative_class', '瀛﹂櫌鍚嶇О',   'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '瀛﹂櫌鍚嶇О锛堜腑鏂囷級'),
('administrative_class', 'ZYDM',       'major_code',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '涓撲笟浠ｇ爜锛堣嫳鏂囩缉鍐欙級'),
('administrative_class', '涓撲笟浠ｇ爜',   'major_code',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '涓撲笟浠ｇ爜锛堜腑鏂囷級'),
('teaching_plans', 'zybh', 'major_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 12, 1, '涓撲笟缂栧彿'),
('teaching_plans', 'zymc', 'major_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 13, 1, '涓撲笟鍚嶇О');


-- =============================================================
-- V13__feature_toggles_and_ai_config.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V13: 鍔熻兘寮€鍏虫墿灞?+ AI 閰嶇疆澧炲己
-- 鏁村悎鑷? V14 (expand_feature_toggles) + V16 (ai_qa_recovery_and_hiagent_config)
--       + V17 (add_page_word_recite_toggle) + V18 (ai_provider_protocol)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- V13: 鎵╁睍 feature_toggle 瑕嗙洊瀛︾敓绔叏閲忛〉闈?+ 绠＄悊鍛樻ā鍧楀垎绾?
-- 鑳屾櫙锛?
--   1. 鍘?V7 鍙?seed 浜?11 涓鐢熼〉 toggle锛宲age_scores / page_course_selections 鍦ㄤ唬鐮佷腑寮曠敤
--      浣?DB 鏈?seed锛屽鑷?toggle 澶辨晥锛堥粯璁ゅ睍绀猴級
--   2. 鏂板 page_courses / page_text_learning 浠ユ敮鎸佸鐢熺 Layout 鍏ㄨ鐩?
--   3. 鏂板 9 涓?admin.* toggle 鏀寔绠＄悊鍛樻ā鍧楃骇绮掑害鎺у埗
-- 涓?V7/V12 鍏崇郴锛歏7 鍙鐩栭儴鍒嗗鐢熼〉锛沄12 娣诲姞瀛︽牎鏁版嵁鐩稿叧锛沄14 琛ラ綈鍓╀綑

SET NAMES utf8mb4;

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 瀛︾敓绔?4 涓己澶?toggle锛堣ˉ榻愬叏 15 椤佃鐩栵級
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_courses',           'false', '瀛︾敓绔?璇剧▼瀛︿範椤碉紙寮€鍙戜腑锛?,   0),
  ('feature_toggle', 'page_scores',            'true',  '瀛︾敓绔?绛夌骇鑰冭瘯鎴愮哗椤?,         0),
  ('feature_toggle', 'page_course_selections', 'true',  '瀛︾敓绔?鎴戠殑閫夎椤?,             0),
  ('feature_toggle', 'page_text_learning',     'true',  '瀛︾敓绔?璇炬枃瀛︿範瀛愰〉',           0),
  ('feature_toggle', 'page_schedule',          'true',  '瀛︾敓绔?鎴戠殑璇捐〃椤?,             0);

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 绠＄悊鍛?9 涓ā鍧?toggle锛堝垎绾ф帶鍒讹級
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'admin.dashboard',    'true', '绠＄悊鍛?浠〃鐩?,     0),
  ('feature_toggle', 'admin.courses',      'true', '绠＄悊鍛?璇剧▼绠＄悊',   0),
  ('feature_toggle', 'admin.classes',      'true', '绠＄悊鍛?鐝骇绠＄悊',   0),
  ('feature_toggle', 'admin.users',        'true', '绠＄悊鍛?鐢ㄦ埛绠＄悊',   0),
  ('feature_toggle', 'admin.units',        'true', '绠＄悊鍛?鍗曞厓绠＄悊',   0),
  ('feature_toggle', 'admin.words',        'true', '绠＄悊鍛?鍗曡瘝绠＄悊',   0),
  ('feature_toggle', 'admin.texts',        'true', '绠＄悊鍛?璇炬枃绠＄悊',   0),
  ('feature_toggle', 'admin.assignments',  'true', '绠＄悊鍛?浣滀笟绠＄悊',   0),
  ('feature_toggle', 'admin.sso_approval', 'true', '绠＄悊鍛?SSO 瀹℃壒',   0);

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 瀛︾敓绔ˉ鍏呴〉闈?toggle
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_videos',            'true',  '瀛︾敓绔?瑙嗛瀛︿範椤?,             0),
  ('feature_toggle', 'page_video_player',      'true',  '瀛︾敓绔?瑙嗛鎾斁鍣ㄩ〉',           0),
  ('feature_toggle', 'page_interactive_text',  'true',  '瀛︾敓绔?浜や簰寮忚鏂囬〉',           0),
  ('feature_toggle', 'page_vocabulary',        'true',  '瀛︾敓绔?璇嶆眹瀛︿範椤?,             0),
  ('feature_toggle', 'page_ai_qa',             'true',  '瀛︾敓绔?AI闂瓟椤?,               0),
  ('feature_toggle', 'page_articles',          'true',  '瀛︾敓绔?鏂囩珷闃呰椤?,             0),
  ('feature_toggle', 'page_user_texts',        'true',  '瀛︾敓绔?鎴戠殑璇炬枃椤?,             0);

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 鏁欏笀绔〉闈?toggle
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_teacher_dashboard',   'true', '鏁欏笀绔?浠〃鐩?,         0),
  ('feature_toggle', 'page_teacher_courses',     'true', '鏁欏笀绔?璇剧▼绠＄悊',       0),
  ('feature_toggle', 'page_teacher_my_courses',  'true', '鏁欏笀绔?鎴戠殑璇剧▼',       0),
  ('feature_toggle', 'page_teacher_classes',     'true', '鏁欏笀绔?鐝骇绠＄悊',       0),
  ('feature_toggle', 'page_teacher_assignments', 'true', '鏁欏笀绔?浣滀笟绠＄悊',       0);

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 绠＄悊鍛樼琛ュ厖椤甸潰 toggle
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_admin_videos',      'true', '绠＄悊鍛?瑙嗛绠＄悊',       0),
  ('feature_toggle', 'page_admin_backups',     'true', '绠＄悊鍛?澶囦唤绠＄悊',       0),
  ('feature_toggle', 'page_admin_monitoring',  'true', '绠＄悊鍛?绯荤粺鐩戞帶',       0);

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 杩愮淮绔〉闈?toggle
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_ops_monitoring',           'true', '杩愮淮绔?绯荤粺鐩戞帶',           0),
  ('feature_toggle', 'page_ops_ai_providers',         'true', '杩愮淮绔?AI鎻愪緵鍟嗙鐞?,       0),
  ('feature_toggle', 'page_ops_feature_toggles',      'true', '杩愮淮绔?鍔熻兘寮€鍏崇鐞?,       0),
  ('feature_toggle', 'page_ops_school_data_config',   'true', '杩愮淮绔?瀛︽牎鏁版嵁閰嶇疆',       0),
  ('feature_toggle', 'page_ops_school_data_browser',  'true', '杩愮淮绔?瀛︽牎鏁版嵁娴忚鍣?,     0),
  ('feature_toggle', 'page_ops_update',               'true', '杩愮淮绔?绯荤粺鏇存柊',           0);

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- Part 2: AI 闂瓟鎭㈠ + HiAgent 鍑瘉閰嶇疆
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

--      鎴?feature_override 閲屾湁瀵?page_ai_qa 鐨勭鐢ㄨ鍒?鈫?璺敱瀹堝崼鎷︽埅 鈫?"杩涗笉鍘?
--   2. HiAgent 鏀瑰啓/闂瓟鍑瘉鍘熸湰鍙€氳繃 application.yml + 鐜鍙橀噺娉ㄥ叆锛?
--      杩濆弽"鎵€鏈夐厤缃墠绔彲閰?鐨勮繍缁磋瘔姹?鈫?鎶?4+1 涓?HiAgent 鍑瘉杩佸埌 system_config.ai_config
--      鍚庣 HiAgentServiceImpl 璇诲€间紭鍏?DB锛屽洖閫€鍒?@Value锛堢幆澧冨彉閲忥級
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 1. 鎭㈠ page_ai_qa 寮€鍏充负 true锛堟棤璁轰箣鍓嶆槸浣曞€硷級
--    骞舵竻闄?feature_override 閲岀殑绂佺敤瑕嗙洊
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key`   = 'page_ai_qa';

-- 鑻ュ巻鍙叉湭 seed锛岃ˉ seed锛圛NSERT IGNORE 淇濇寔骞傜瓑锛?
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES ('feature_toggle', 'page_ai_qa', 'true', '瀛︾敓绔?AI闂瓟椤?, 0);

-- 娓呴櫎鎵€鏈夊 page_ai_qa 鐨勭鐢ㄨ鐩栵紙tag/user scope锛?
DELETE FROM `feature_override` WHERE `feature_key` = 'page_ai_qa';

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 2. HiAgent 鍑瘉 seed 鍒?ai_config group锛坕s_secret=1 for keys锛?
--    鍊奸粯璁や负绌哄瓧绗︿覆锛岃〃绀?灏氭湭浠庡墠绔厤缃紝浣跨敤 application.yml 鐜鍙橀噺"
--    鍓嶇闈㈡澘鍐欏叆 DB 鍚庯紝HiAgentServiceImpl 浼氫紭鍏堣 DB 鍊?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('ai_config', 'hiagent_api_url',    '', 'HiAgent API Base URL锛堢暀绌哄垯浣跨敤鐜鍙橀噺 HIAGENT_API_URL锛?,         0),
  ('ai_config', 'hiagent_api_key',    '', 'HiAgent 鏀瑰啓搴旂敤 API Key锛堢暀绌哄垯浣跨敤鐜鍙橀噺 HIAGENT_API_KEY锛?,      1),
  ('ai_config', 'hiagent_app_id',     '', 'HiAgent 鏀瑰啓搴旂敤 App ID锛堢暀绌哄垯浣跨敤鐜鍙橀噺 HIAGENT_APP_ID锛?,        0),
  ('ai_config', 'hiagent_qa_api_key', '', 'HiAgent 闂瓟搴旂敤 API Key锛堝繀椤讳笌鏀瑰啓鍑瘉涓嶅悓锛涚暀绌哄垯鐢?HIAGENT_QA_API_KEY锛?, 1),
  ('ai_config', 'hiagent_qa_app_id',  '', 'HiAgent 闂瓟搴旂敤 App ID锛堝繀椤讳笌鏀瑰啓鍑瘉涓嶅悓锛涚暀绌哄垯鐢?HIAGENT_QA_APP_ID锛?,  0);

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- Part 3: 琛ラ綈鑳屽崟璇嶉〉 toggle
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

--   toggleKey = 'page_word_recite'锛屼絾璇ラ敭浠庢湭 seed 鍒?
--   system_config.feature_toggle锛屽鑷磋繍缁撮潰鏉?鍏ㄥ眬寮€鍏?鍒楄〃缂哄皯
--   "鑳屽崟璇?鏉＄洰 鈥斺€?寮€鍏充笌椤甸潰鏃犳硶涓€涓€瀵瑰簲銆?
--
--   鍚屾椂 router/index.js 涓?/student/word-recite 璺敱瀹堝崼缂哄け
--   featureKey锛屾湰杩佺Щ閰嶅悎鍓嶇琛ラ綈鍚庯紝杈炬垚"寮€鍏冲叧闂?鈫?瀵艰埅闅愯棌 +
--   蹇嵎鍏ュ彛闅愯棌 + 鐩存帴璁块棶琚嫤鎴?涓夊眰鑱斿姩銆?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES ('feature_toggle', 'page_word_recite', 'true', '瀛︾敓绔?鑳屽崟璇嶉〉', 0);

-- 鑻ュ巻鍙蹭腑瀛樺湪浣嗘弿杩颁负绌猴紝缁熶竴瑙勮寖鍖栨弿杩?
UPDATE `system_config`
SET `description` = '瀛︾敓绔?鑳屽崟璇嶉〉'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` = 'page_word_recite'
  AND (`description` IS NULL OR `description` = '');

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- Part 4: AI 鏈嶅姟鍟嗗崗璁被鍨嬪瓧娈?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?


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
        COMMENT '鍗忚绫诲瀷: openai_compatible | hiagent';


INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'student.lazy_course_selections', 'true', '瀛︾敓绔?Lazy 閫夎璁板綍鍚屾锛堥檲鏃у害闃堝€?1 澶╋級', 0),
  ('feature_toggle', 'teacher.school_data_enabled', 'true', '鏁欏笀绔鏍℃暟鎹姛鑳斤紙璇捐〃/閫夎/鎴愮哗锛?, 0);


-- =============================================================
-- V14__enhancements.sql
-- =============================================================

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- V14: 澧炲己鍔熻兘
-- 鏁村悎鑷? V22 (chain_fetch_schema) + V24 (similarity_matrix)
--       + V25 (similar_id_indexes) + V26 (optimize_interaction_view)
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?

-- Part 1: 鎸夊闄㈤摼寮忔媺鍙栨暟鎹ā鍨?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 鐢ㄩ€? 鏀寔鎸夊闄㈤摼寮忔媺鍙栧鐢?鏁欏笀鏁版嵁锛岄厤鍚?SSO 鑷姩鎺堟潈銆?
--       鏁欏笀-瀛︾敓缁戝畾鏌ヨ绛変笅娓稿満鏅?
-- Author: System
-- Date: 2026-04-22
-- ============================================================================

-- ============================================================================
-- 1. 瀛︾敓涓绘暟鎹紙CDSP 閾惧紡鎷夊彇浜х墿锛?
-- ============================================================================
CREATE TABLE IF NOT EXISTS school_student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    student_id VARCHAR(50) NOT NULL UNIQUE COMMENT '瀛﹀彿锛堝搴?CDSP xh / XH锛?,
    student_name VARCHAR(100) COMMENT '瀛︾敓濮撳悕锛圫SO attributes 鎴栨墜宸ヨˉ鍏級',
    college_name VARCHAR(100) NOT NULL COMMENT '瀛﹂櫌鍚嶇О锛圕DSP kkyx锛?,
    college_code VARCHAR(50) COMMENT '瀛﹂櫌浠ｇ爜锛圕DSP 鏈彁渚涳紝棰勭暀锛?,
    class_code VARCHAR(50) COMMENT '琛屾斂鐝彿锛圕AS attributes 琛ュ叏锛岄娆?NULL锛?,
    class_name VARCHAR(100) COMMENT '鐝骇鍚嶇О锛圕AS attributes 琛ュ叏锛?,
    grade VARCHAR(20) COMMENT '骞寸骇锛堝彲鐢ㄥ鍙峰墠 4 浣嶆帹鏂級',
    major_name VARCHAR(100) COMMENT '涓撲笟锛圕AS attributes 琛ュ叏锛?,
    source_course_codes TEXT COMMENT '鍙戠幇璇ュ鐢熺殑璇剧▼浠ｇ爜鍒楄〃锛圝SON 鏁扮粍锛夛紝璇婃柇鐢?,
    sync_time DATETIME NOT NULL COMMENT '棣栨鍚屾鏃堕棿',
    last_exam_sync_time DATETIME COMMENT '涓婃鍥涘叚绾ф垚缁╁悓姝ユ椂闂?,
    last_schedule_sync_time DATETIME COMMENT '涓婃璇捐〃鍚屾鏃堕棿',
    sync_source VARCHAR(20) NOT NULL DEFAULT 'SCHOOL_API' COMMENT '鍚屾鏉ユ簮',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_college (college_name),
    INDEX idx_grade (grade),
    INDEX idx_class_code (class_code),
    INDEX idx_last_exam (last_exam_sync_time),
    INDEX idx_last_schedule (last_schedule_sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDSP 鍚屾鐨勫鐢熶富鏁版嵁锛堥摼寮忔媺鍙栦骇鐗╋級';

-- ============================================================================
-- 2. 鏁欏笀涓绘暟鎹紙CDSP 閾惧紡鎷夊彇浜х墿锛?
-- ============================================================================
CREATE TABLE IF NOT EXISTS school_teacher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    teacher_id VARCHAR(50) NOT NULL UNIQUE COMMENT '宸ュ彿锛圕DSP rkjsh / skjszgh锛?,
    teacher_name VARCHAR(100) COMMENT '鏁欏笀濮撳悕锛圕DSP rkjsxm锛?,
    college_name VARCHAR(100) NOT NULL COMMENT '瀛﹂櫌锛堟潵鑷摼寮忚Е鍙戝闄級',
    college_code VARCHAR(50) COMMENT '瀛﹂櫌浠ｇ爜锛堥鐣欙級',
    sync_time DATETIME NOT NULL COMMENT '鍚屾鏃堕棿',
    last_schedule_sync_time DATETIME COMMENT '涓婃璇捐〃鍚屾鏃堕棿',
    sync_source VARCHAR(20) NOT NULL DEFAULT 'SCHOOL_API' COMMENT '鍚屾鏉ユ簮',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_college (college_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDSP 鍚屾鐨勬暀甯堜富鏁版嵁';

-- ============================================================================
-- 3. 鏁欏笀-瀛︾敓缁戝畾锛堥摼寮忔媺鍙栬鐢熷叧鑱旓級
-- ============================================================================
CREATE TABLE IF NOT EXISTS teacher_student_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '涓婚敭',
    teacher_id VARCHAR(50) NOT NULL COMMENT '宸ュ彿',
    student_id VARCHAR(50) NOT NULL COMMENT '瀛﹀彿',
    course_code VARCHAR(50) NOT NULL COMMENT '璇剧▼浠ｇ爜 kcdm',
    course_class_code VARCHAR(50) COMMENT '璇剧▼鐝彿 kcbh',
    academic_year VARCHAR(20) NOT NULL DEFAULT '' COMMENT '瀛﹀勾锛圢ULL 鏇挎崲涓虹┖涓蹭互閰嶅悎 UNIQUE 绱㈠紩锛?,
    semester VARCHAR(10) NOT NULL DEFAULT '' COMMENT '瀛︽湡锛堢/鏄ワ級锛孨ULL 鏇挎崲涓虹┖涓?,
    source_type VARCHAR(20) NOT NULL DEFAULT 'CHAIN_SYNC' COMMENT '鏉ユ簮: CHAIN_SYNC / MANUAL',
    sync_time DATETIME NOT NULL COMMENT '鍚屾鏃堕棿',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_teacher_student_course (teacher_id, student_id, course_code, academic_year, semester),
    INDEX idx_teacher (teacher_id),
    INDEX idx_student (student_id),
    INDEX idx_course (course_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏁欏笀-瀛︾敓缁戝畾鍏崇郴琛?;

-- ============================================================================
-- 4. Feature Toggle / 瀛﹂櫌閾惧紡閰嶇疆 seed
-- ============================================================================
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'ops.school_data_chain_fetch', 'false', '杩愮淮绔寜瀛﹂櫌閾惧紡鎷夊彇鍔熻兘寮€鍏?, 0),
('feature_toggle', 'student.lazy_data_sync', 'true', '瀛︾敓绔寜闇€ Lazy 鍚屾鍥涘叚绾?璇捐〃', 0),
('feature_toggle', 'sso.auto_grant_student', 'false', 'SSO 瀛﹀彿鍖归厤 school_student 鑷姩鎺堟潈 STUDENT', 0),
('feature_toggle', 'sso.auto_grant_teacher', 'false', 'SSO 宸ュ彿鍖归厤 school_teacher 鑷姩鎺堟潈 TEACHER', 0),
('school_data', 'sync_college_chain_cron', '0 30 3 * * ?', '鎸夊闄㈤摼寮忓埛鏂?cron 琛ㄨ揪寮?, 0),
('school_data', 'sync_college_chain_colleges', '澶栧浗璇闄?, '閾惧紡鍒锋柊鐧藉悕鍗曪紙閫楀彿鍒嗛殧瀛﹂櫌鍚嶏級', 0),
('school_data', 'sync_college_chain_match_mode', 'eq', '瀛﹂櫌鍚嶅尮閰嶆ā寮忥紙eq=绮剧‘ / contains=鍖呭惈锛?, 0);

-- Part 4: 鐩镐技搴︾煩闃佃〃
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- 1. text_similarity_matrix 鈥?鏂囩珷鐩镐技搴︾煩闃?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
CREATE TABLE IF NOT EXISTS `text_similarity_matrix` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '涓婚敭',
    `text_id` BIGINT NOT NULL COMMENT '鏂囩珷ID',
    `similar_text_id` BIGINT NOT NULL COMMENT '鐩镐技鏂囩珷ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '鐩镐技搴?0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY `uk_text_pair` (`text_id`, `similar_text_id`),
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鏂囩珷鐩镐技搴︾煩闃?;

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- 2. text_similarity_matrix_tmp 鈥?鏂囩珷鐩镐技搴︾煩闃典复鏃惰〃
--    琛ㄧ粨鏋勪笌 text_similarity_matrix 瀹屽叏涓€鑷?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
CREATE TABLE IF NOT EXISTS `text_similarity_matrix_tmp` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '涓婚敭',
    `text_id` BIGINT NOT NULL COMMENT '鏂囩珷ID',
    `similar_text_id` BIGINT NOT NULL COMMENT '鐩镐技鏂囩珷ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '鐩镐技搴?0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY `uk_text_pair` (`text_id`, `similar_text_id`),
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鏂囩珷鐩镐技搴︾煩闃典复鏃惰〃';

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- 3. user_similarity_matrix 鈥?鐢ㄦ埛鐩镐技搴︾煩闃?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
CREATE TABLE IF NOT EXISTS `user_similarity_matrix` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '涓婚敭',
    `user_id` BIGINT NOT NULL COMMENT '鐢ㄦ埛ID',
    `similar_user_id` BIGINT NOT NULL COMMENT '鐩镐技鐢ㄦ埛ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '鐩镐技搴?0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY `uk_user_pair` (`user_id`, `similar_user_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鐢ㄦ埛鐩镐技搴︾煩闃?;

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- 4. user_similarity_matrix_tmp 鈥?鐢ㄦ埛鐩镐技搴︾煩闃典复鏃惰〃
--    琛ㄧ粨鏋勪笌 user_similarity_matrix 瀹屽叏涓€鑷?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
CREATE TABLE IF NOT EXISTS `user_similarity_matrix_tmp` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '涓婚敭',
    `user_id` BIGINT NOT NULL COMMENT '鐢ㄦ埛ID',
    `similar_user_id` BIGINT NOT NULL COMMENT '鐩镐技鐢ㄦ埛ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '鐩镐技搴?0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY `uk_user_pair` (`user_id`, `similar_user_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鐢ㄦ埛鐩镐技搴︾煩闃典复鏃惰〃';

-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
-- 5. v_user_text_interaction_scores 鈥?鐢ㄦ埛-鏂囩珷浜や簰璇勫垎瑙嗗浘
--
-- 鏍稿績鍏紡锛?
--   w_read(0.2)       鈥?鏄惁璇昏繃锛?/1锛?
--   w_complete(0.2)   鈥?鏄惁瀹屾垚闃呰锛?/1锛?
--   w_favorite(0.3)   鈥?鏄惁鏀惰棌锛?/1锛?
--   w_duration(0.1)   鈥?闃呰鏃堕暱寰楀垎锛堟弧鍒?00绉掞級
--   w_accuracy(0.1)    鈥?涔犻姝ｇ‘鐜?
--   w_underline(0.1)   鈥?鍒掔嚎娆℃暟寰楀垎锛堟弧鍒?0娆★級
--
--   鏈€缁堝垎鏁?= LEAST(1.0, 鎬诲垎)锛屽彧鑰冭檻 role 涓?'STUDENT' 鎴?'USER' 鐨勭敤鎴?
-- 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺?
CREATE OR REPLACE VIEW `v_user_text_interaction_scores` AS
SELECT
    u.id                                          AS user_id,
    t.id                                          AS text_id,

    -- w_read(0.2): 鏄惁宸茶
    COALESCE(utr.read_at IS NOT NULL, 0)          AS w_read,

    -- w_complete(0.2): 鏄惁瀹屾垚闃呰
    COALESCE(tlr.is_completed = 1, 0)             AS w_complete,

    -- w_favorite(0.3): 鏄惁鏀惰棌
    COALESCE(af.id IS NOT NULL, 0)                AS w_favorite,

    -- w_duration(0.1): 闃呰鏃堕暱寰楀垎锛堟弧鍒?00绉掞級
    LEAST(COALESCE(tlr.duration_seconds, 0) / 600.0, 1.0) AS w_duration,

    -- w_accuracy(0.1): 涔犻姝ｇ‘鐜?
    COALESCE(
        sr.correct_rate,
        0.0
    )                                             AS w_accuracy,

    -- w_underline(0.1): 鍒掔嚎娆℃暟寰楀垎锛堟弧鍒?0娆★級
    LEAST(COALESCE(ul.underline_count, 0) / 10.0, 1.0) AS w_underline,

    -- 鏈€缁堝姞鏉冩€诲垎锛堝皝椤?.0锛?
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
    -- 姣忕敤鎴锋瘡鏂囩珷鐨勫垝绾挎鏁?
    SELECT user_id, text_id, COUNT(*) AS underline_count
    FROM underlines
    GROUP BY user_id, text_id
) ul ON ul.user_id = u.id AND ul.text_id = t.id
WHERE u.role IN ('STUDENT', 'USER');

-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- Part 5: 鐩镐技搴︾煩闃佃ˉ鍏呯储寮?
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

-- Part 6: 浼樺寲鐢ㄦ埛鏂囨湰浜や簰瑙嗗浘
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

-- 婧愯〃锛?
--   - user_text_reads       (user_id,   text_id)
--   - text_learning_records (student_id,text_id)  student_id 瀵瑰簲 user_id
--   - article_favorites     (student_id,text_id)  鍚屼笂
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


-- =============================================================
-- V15__fix_schema_issues.sql
-- =============================================================

-- V15: Fix runtime schema mismatches found in production

-- 1. backup_records.category
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'backup_records'
      AND COLUMN_NAME = 'category'
);
SET @add_category_sql = IF(
    @col_exists = 0,
    'ALTER TABLE backup_records ADD COLUMN category VARCHAR(50) DEFAULT ''SYSTEM'' COMMENT ''Backup category: SYSTEM/USER/AUTO'' AFTER backup_mode',
    'SELECT 1'
);
PREPARE stmt FROM @add_category_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. videos.subtitle_content_zh
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'videos'
      AND COLUMN_NAME = 'subtitle_content_zh'
);
SET @add_subtitle_sql = IF(
    @col_exists = 0,
    'ALTER TABLE videos ADD COLUMN subtitle_content_zh LONGTEXT COMMENT ''Chinese subtitle content (SRT/VTT)'' AFTER subtitle_content',
    'SELECT 1'
);
PREPARE stmt FROM @add_subtitle_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Relax nullable constraints for school data tables.
ALTER TABLE course_selection
    MODIFY COLUMN credits DECIMAL(3,1) NULL COMMENT 'Upstream credits may be null',
    MODIFY COLUMN course_name VARCHAR(200) NULL COMMENT 'Upstream course name may be null',
    MODIFY COLUMN teacher_id VARCHAR(50) NULL COMMENT 'Upstream teacher id may be null',
    MODIFY COLUMN teacher_name VARCHAR(50) NULL COMMENT 'Upstream teacher name may be null',
    MODIFY COLUMN academic_year VARCHAR(20) NULL COMMENT 'Upstream academic year may be null',
    MODIFY COLUMN semester INT NULL COMMENT 'Upstream semester may be null',
    MODIFY COLUMN selection_time DATETIME NULL COMMENT 'Upstream selection time may be null',
    MODIFY COLUMN selection_status VARCHAR(20) NULL DEFAULT 'SELECTED' COMMENT 'Selection status';

ALTER TABLE course_schedule
    MODIFY COLUMN class_id VARCHAR(50) NULL COMMENT 'Teaching class id',
    MODIFY COLUMN day_of_week INT NULL COMMENT 'Day of week',
    MODIFY COLUMN period VARCHAR(20) NULL COMMENT 'Course period',
    MODIFY COLUMN week_range VARCHAR(50) NULL COMMENT 'Week range',
    MODIFY COLUMN class_location VARCHAR(100) NULL COMMENT 'Class location';

ALTER TABLE teaching_class
    MODIFY COLUMN class_name VARCHAR(100) NULL COMMENT 'Teaching class name',
    MODIFY COLUMN capacity INT NULL DEFAULT 0 COMMENT 'Class capacity',
    MODIFY COLUMN class_status VARCHAR(20) NULL DEFAULT 'ACTIVE' COMMENT 'Class status';

-- 4. Clean orphan rows before re-adding the FK.
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

-- 5. Rebuild course_schedule FK with ON DELETE SET NULL.
SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND CONSTRAINT_NAME = 'fk_course_schedule_teaching_class'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @drop_fk_sql = IF(
    @fk_exists > 0,
    'ALTER TABLE course_schedule DROP FOREIGN KEY fk_course_schedule_teaching_class',
    'SELECT 1'
);
PREPARE stmt FROM @drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND CONSTRAINT_NAME = 'course_schedule_ibfk_1'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @drop_fk_sql = IF(
    @fk_exists > 0,
    'ALTER TABLE course_schedule DROP FOREIGN KEY course_schedule_ibfk_1',
    'SELECT 1'
);
PREPARE stmt FROM @drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND CONSTRAINT_NAME = 'fk_course_schedule_teaching_class'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @add_fk_sql = IF(
    @fk_exists = 0,
    'ALTER TABLE course_schedule ADD CONSTRAINT fk_course_schedule_teaching_class FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE SET NULL ON UPDATE CASCADE',
    'SELECT 1'
);
PREPARE stmt FROM @add_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. Add missing field mappings.
INSERT IGNORE INTO field_mappings (
    data_source, source_field, target_field, source_type, target_type,
    converter, converter_config, default_value, is_required, sort_order, is_active, remark
) VALUES
    ('course_selection', 'XF', 'credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, 'Credits from upstream if present'),
    ('course_selection', '瀛﹀垎', 'credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, 'Credits from upstream if present');

INSERT IGNORE INTO field_mappings (
    data_source, source_field, target_field, source_type, target_type,
    converter, converter_config, default_value, is_required, sort_order, is_active, remark
) VALUES
    ('course_selection', 'XM', 'student_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, 'Student name if present'),
    ('course_selection', '濮撳悕', 'student_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, 'Student name if present');

-- 7. Add composite indexes used by list queries.
SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_selection'
      AND INDEX_NAME = 'idx_student_academic'
);
SET @add_idx_sql = IF(
    @idx_exists = 0,
    'CREATE INDEX idx_student_academic ON course_selection(student_id, academic_year, semester)',
    'SELECT 1'
);
PREPARE stmt FROM @add_idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND INDEX_NAME = 'idx_class_academic'
);
SET @add_idx_sql = IF(
    @idx_exists = 0,
    'CREATE INDEX idx_class_academic ON course_schedule(class_id, academic_year, semester)',
    'SELECT 1'
);
PREPARE stmt FROM @add_idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
