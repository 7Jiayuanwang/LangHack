mysqldump: [Warning] Using a password on the command line interface can be insecure.
-- MySQL dump 10.13  Distrib 8.0.44, for Linux (x86_64)
--
-- Host: localhost    Database: english_learning
-- ------------------------------------------------------
-- Server version	8.0.44-0ubuntu0.24.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `assignments`
--

DROP TABLE IF EXISTS `assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '任务标题',
  `description` text COMMENT '任务描述',
  `class_id` bigint DEFAULT NULL COMMENT '班级ID',
  `unit_id` bigint DEFAULT NULL COMMENT '单元ID',
  `text_id` bigint DEFAULT NULL COMMENT '课文ID',
  `type` enum('READING','VOCABULARY','COMPREHENSION') NOT NULL COMMENT '任务类型',
  `difficulty` enum('EASY','MEDIUM','HARD') DEFAULT 'MEDIUM' COMMENT '难度',
  `deadline` datetime DEFAULT NULL COMMENT '截止时间',
  `weight` decimal(5,2) DEFAULT '1.00' COMMENT '任务权重（用于计算加权平均分，默认1.00）',
  `ideal_study_time_minutes` int DEFAULT '60' COMMENT '理想学习时长（分钟），用于计算分数，默认60分钟',
  `status` enum('DRAFT','PUBLISHED','CLOSED') DEFAULT 'DRAFT' COMMENT '状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建者ID',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `classes`
--

DROP TABLE IF EXISTS `classes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `classes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '班级名称',
  `description` text COMMENT '班级描述',
  `course_id` bigint DEFAULT NULL COMMENT '关联课程ID',
  `teacher_id` bigint DEFAULT NULL COMMENT '教师ID',
  `join_code` varchar(10) DEFAULT NULL COMMENT '加入码(可为空)',
  `max_students` int DEFAULT '50' COMMENT '最大学生数',
  `status` enum('ACTIVE','INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `join_code` (`join_code`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `classes_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `classes_ibfk_2` FOREIGN KEY (`teacher_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班级表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `courses`
--

DROP TABLE IF EXISTS `courses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `courses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '课程名称',
  `description` text COMMENT '课程描述',
  `cover_image` varchar(255) DEFAULT NULL COMMENT '封面图片',
  `created_by` bigint DEFAULT NULL COMMENT '创建者ID',
  `status` enum('ACTIVE','INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `courses_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ecdict_words`
--

DROP TABLE IF EXISTS `ecdict_words`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ecdict_words` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `word` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '单词',
  `phonetic` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '音标',
  `definition` text COLLATE utf8mb4_unicode_ci COMMENT '英文定义',
  `translation` text COLLATE utf8mb4_unicode_ci COMMENT '中文翻译',
  `pos` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '词性',
  `collins` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '柯林斯星级',
  `oxford` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '牛津3000核心词汇标记',
  `tag` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签',
  `bnc` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BNC词频',
  `frq` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当代语料库词频',
  `exchange` text COLLATE utf8mb4_unicode_ci COMMENT '时态复数等变换',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`),
  KEY `idx_phonetic` (`phonetic`),
  KEY `idx_pos` (`pos`)
) ENGINE=InnoDB AUTO_INCREMENT=770611 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ECDICT词汇表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exercise_options`
--

DROP TABLE IF EXISTS `exercise_options`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exercise_options` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `exercise_id` bigint NOT NULL COMMENT '习题ID',
  `option_content` varchar(500) NOT NULL COMMENT '选项内容',
  `option_key` varchar(10) NOT NULL COMMENT '选项标识(A, B, C, D等)',
  `is_correct` tinyint(1) DEFAULT '0' COMMENT '是否正确答案',
  `order_num` int DEFAULT '1' COMMENT '显示顺序',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exercise_option` (`exercise_id`,`option_key`),
  KEY `idx_exercise_id` (`exercise_id`),
  CONSTRAINT `exercise_options_ibfk_1` FOREIGN KEY (`exercise_id`) REFERENCES `text_exercises` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='习题选项表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `learning_records`
--

DROP TABLE IF EXISTS `learning_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `word_id` bigint DEFAULT NULL COMMENT '单词ID',
  `text_id` bigint DEFAULT NULL COMMENT '关联的课文ID',
  `assignment_id` bigint DEFAULT NULL COMMENT '任务ID',
  `record_type` varchar(50) DEFAULT NULL COMMENT '记录类型：VIEW-浏览，REVIEW-复习，TEST-测试',
  `duration_seconds` int DEFAULT '0' COMMENT '学习时长（秒）',
  `correct_count` int DEFAULT '0' COMMENT '正确次数',
  `total_count` int DEFAULT '0' COMMENT '总次数',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_word_id` (`word_id`),
  KEY `idx_assignment_id` (`assignment_id`),
  KEY `idx_record_type` (`record_type`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_text_id` (`text_id`),
  CONSTRAINT `learning_records_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `score_records`
--

DROP TABLE IF EXISTS `score_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `assignment_id` bigint DEFAULT NULL COMMENT '任务ID（可选，如果是任务相关的分数）',
  `course_id` bigint DEFAULT NULL COMMENT '课程ID（可选，如果是课程相关的分数）',
  `unit_id` bigint DEFAULT NULL COMMENT '单元ID（可选，如果是单元相关的分数）',
  `score_type` varchar(50) NOT NULL COMMENT '分数类型：ASSIGNMENT-任务分数, COURSE-课程分数, UNIT-单元分数, EXAM-考试分数',
  `score` int NOT NULL COMMENT '分数值（0-100）',
  `full_score` int NOT NULL DEFAULT '100' COMMENT '满分',
  `score_details` text COMMENT '得分详情（JSON格式，存储详细得分项）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_assignment_id` (`assignment_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_unit_id` (`unit_id`),
  KEY `idx_score_type` (`score_type`),
  KEY `idx_created_time` (`created_time`),
  CONSTRAINT `score_records_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分数记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_assignments`
--

DROP TABLE IF EXISTS `student_assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint NOT NULL COMMENT '任务ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `status` enum('NOT_STARTED','IN_PROGRESS','COMPLETED') DEFAULT 'NOT_STARTED' COMMENT '完成状态',
  `score` int DEFAULT NULL COMMENT '得分',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_assignment_id` (`assignment_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `student_assignments_ibfk_1` FOREIGN KEY (`assignment_id`) REFERENCES `assignments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `student_assignments_ibfk_2` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生任务关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_classes`
--

DROP TABLE IF EXISTS `student_classes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_classes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `class_id` bigint NOT NULL COMMENT '班级ID',
  `join_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `status` enum('ACTIVE','INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_class` (`student_id`,`class_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_class_id` (`class_id`),
  CONSTRAINT `student_classes_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `student_classes_ibfk_2` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=168 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生班级关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_exercise_answers`
--

DROP TABLE IF EXISTS `student_exercise_answers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_exercise_answers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `exercise_id` bigint NOT NULL COMMENT '习题ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `assignment_id` bigint DEFAULT NULL COMMENT '任务ID(可选)',
  `answer_content` text COMMENT '学生答案',
  `is_correct` tinyint(1) DEFAULT NULL COMMENT '是否正确',
  `answer_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
  `time_spent_seconds` int DEFAULT NULL COMMENT '答题用时(秒)',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_exercise` (`student_id`,`exercise_id`,`assignment_id`),
  KEY `idx_exercise_id` (`exercise_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_assignment_id` (`assignment_id`),
  CONSTRAINT `student_exercise_answers_ibfk_1` FOREIGN KEY (`exercise_id`) REFERENCES `text_exercises` (`id`) ON DELETE CASCADE,
  CONSTRAINT `student_exercise_answers_ibfk_2` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `student_exercise_answers_ibfk_3` FOREIGN KEY (`assignment_id`) REFERENCES `assignments` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生习题答题记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `text_exercises`
--

DROP TABLE IF EXISTS `text_exercises`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `text_exercises` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `text_id` bigint NOT NULL COMMENT '课文ID',
  `question` text NOT NULL COMMENT '题目内容',
  `exercise_type` enum('MULTIPLE_CHOICE','TRUE_FALSE','FILL_BLANK','SHORT_ANSWER') DEFAULT 'MULTIPLE_CHOICE' COMMENT '习题类型',
  `order_num` int DEFAULT '1' COMMENT '显示顺序',
  `created_by` bigint NOT NULL COMMENT '创建者ID',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `created_by` (`created_by`),
  KEY `idx_text_id` (`text_id`),
  KEY `idx_exercise_type` (`exercise_type`),
  KEY `idx_order_num` (`order_num`),
  CONSTRAINT `text_exercises_ibfk_1` FOREIGN KEY (`text_id`) REFERENCES `texts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `text_exercises_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课文习题表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `texts`
--

DROP TABLE IF EXISTS `texts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `texts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `unit_id` bigint NOT NULL COMMENT '单元ID',
  `title` varchar(200) NOT NULL COMMENT '课文标题',
  `content` text NOT NULL COMMENT '课文内容',
  `cet4_content` text COMMENT 'CET-4版本内容',
  `cet6_content` text COMMENT 'CET-6版本内容',
  `academic_content` text COMMENT '学术版本内容',
  `ielts_content` text COMMENT '雅思版本内容',
  `audio_url` varchar(255) DEFAULT NULL COMMENT '音频URL',
  `difficulty` enum('EASY','MEDIUM','HARD') DEFAULT 'MEDIUM' COMMENT '难度',
  `order_num` int DEFAULT '0' COMMENT '排序号',
  `uploaded_by` bigint DEFAULT NULL COMMENT '上传者ID（NULL表示系统内容）',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签，多个用逗号分隔',
  `view_count` int DEFAULT '0' COMMENT '查看次数',
  `like_count` int DEFAULT '0' COMMENT '点赞次数',
  `is_public` tinyint DEFAULT '1' COMMENT '是否公开 0-私有 1-公开',
  `status` enum('PENDING','APPROVED','REJECTED') DEFAULT 'APPROVED' COMMENT '审核状态',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `chinese_parallel_content` text COMMENT '中英对照版本内容',
  PRIMARY KEY (`id`),
  KEY `idx_unit_id` (`unit_id`),
  KEY `idx_uploaded_by` (`uploaded_by`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_order_num` (`order_num`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_status` (`status`),
  KEY `idx_is_public` (`is_public`),
  CONSTRAINT `texts_ibfk_1` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`) ON DELETE CASCADE,
  CONSTRAINT `texts_ibfk_2` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=520 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课文表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `unit_words`
--

DROP TABLE IF EXISTS `unit_words`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `unit_words` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `unit_id` bigint NOT NULL COMMENT '单元ID',
  `word_id` bigint NOT NULL COMMENT '单词ID',
  `order_num` int DEFAULT '0' COMMENT '排序号',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_unit_id` (`unit_id`),
  KEY `idx_word_id` (`word_id`),
  KEY `idx_order_num` (`order_num`),
  CONSTRAINT `unit_words_ibfk_1` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`) ON DELETE CASCADE,
  CONSTRAINT `unit_words_ibfk_2` FOREIGN KEY (`word_id`) REFERENCES `words` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=664 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单元单词关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `units`
--

DROP TABLE IF EXISTS `units`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `units` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `name` varchar(100) NOT NULL COMMENT '单元名称',
  `description` text COMMENT '单元描述',
  `order_num` int DEFAULT '0' COMMENT '排序号',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_order_num` (`order_num`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `units_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1008 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单元表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `role` enum('USER','STUDENT','TEACHER','ADMIN') NOT NULL COMMENT '角色',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态',
  `inner_uid` varchar(100) DEFAULT NULL COMMENT '用户内部唯一标识符',
  `hobby` varchar(100) DEFAULT NULL COMMENT '用户兴趣爱好',
  `level` varchar(50) DEFAULT NULL COMMENT '英语水平等级',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `idx_username` (`username`),
  KEY `idx_role` (`role`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_inner_uid` (`inner_uid`)
) ENGINE=InnoDB AUTO_INCREMENT=180 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `vocabulary_books`
--

DROP TABLE IF EXISTS `vocabulary_books`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vocabulary_books` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `word_id` bigint NOT NULL COMMENT '单词ID',
  `added_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  `review_count` int DEFAULT '0' COMMENT '复习次数',
  `last_reviewed` datetime DEFAULT NULL COMMENT '最后复习时间',
  `mastery_level` enum('NEW','LEARNING','FAMILIAR','MASTERED') DEFAULT 'NEW' COMMENT '掌握程度：NEW-新词，LEARNING-学习中，FAMILIAR-熟悉，MASTERED-已掌握',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_word` (`student_id`,`word_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_word_id` (`word_id`),
  KEY `idx_mastery_level` (`mastery_level`),
  CONSTRAINT `vocabulary_books_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `vocabulary_books_ibfk_2` FOREIGN KEY (`word_id`) REFERENCES `words` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生词本表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `words`
--

DROP TABLE IF EXISTS `words`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `words` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `word` varchar(100) NOT NULL COMMENT '单词',
  `pronunciation` varchar(200) DEFAULT NULL COMMENT '发音',
  `definition` text COMMENT '中文释义',
  `example_sentence` text COMMENT '例句',
  `audio_url` varchar(255) DEFAULT NULL COMMENT '音频URL',
  `part_of_speech` varchar(50) DEFAULT NULL COMMENT '词性',
  `difficulty` enum('EASY','MEDIUM','HARD') DEFAULT 'MEDIUM' COMMENT '难度',
  `synonyms` text COMMENT '同义词',
  `antonyms` text COMMENT '反义词',
  `wordnet_id` varchar(100) DEFAULT NULL COMMENT 'WordNet ID',
  `multiple_definitions` text COMMENT '多个释义',
  `source` varchar(100) DEFAULT NULL COMMENT '来源',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_word` (`word`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=655 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单词表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-01-28 21:29:30
