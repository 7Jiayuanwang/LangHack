-- =============================================
-- 添加合并项目需要的新表
-- 在现有 english_learning 数据库中执行
-- =============================================

USE english_learning;

-- 1. 学习记录表
CREATE TABLE IF NOT EXISTS `learning_records` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `unit_id` bigint DEFAULT NULL COMMENT '单元ID',
  `word_id` bigint DEFAULT NULL COMMENT '单词ID',
  `text_id` bigint DEFAULT NULL COMMENT '课文ID',
  `assignment_id` bigint DEFAULT NULL COMMENT '任务ID',
  `learning_type` varchar(50) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '学习类型：WORD-单词学习, TEXT-课文学习, ASSIGNMENT-任务',
  `progress` int DEFAULT 0 COMMENT '学习进度百分比',
  `time_spent` int DEFAULT 0 COMMENT '学习时长（分钟）',
  `status` varchar(20) COLLATE utf8mb4_0900_ai_ci DEFAULT 'IN_PROGRESS' COMMENT '状态：IN_PROGRESS-进行中, COMPLETED-已完成, PAUSED-已暂停',
  `score` int DEFAULT NULL COMMENT '得分',
  `notes` text COLLATE utf8mb4_0900_ai_ci COMMENT '学习笔记',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_unit_id` (`unit_id`),
  KEY `idx_word_id` (`word_id`),
  KEY `idx_text_id` (`text_id`),
  KEY `idx_assignment_id` (`assignment_id`),
  KEY `idx_learning_type` (`learning_type`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习记录表';

-- 2. 生词本表
CREATE TABLE IF NOT EXISTS `vocabulary_books` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `word_id` bigint NOT NULL COMMENT '单词ID',
  `source_type` varchar(50) COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '来源类型：TEXT-课文, ASSIGNMENT-任务',
  `source_id` bigint DEFAULT NULL COMMENT '来源ID',
  `mastery_level` int DEFAULT 0 COMMENT '掌握程度：0-未掌握, 1-认识, 2-熟悉, 3-掌握',
  `review_count` int DEFAULT 0 COMMENT '复习次数',
  `last_review_time` datetime DEFAULT NULL COMMENT '上次复习时间',
  `notes` text COLLATE utf8mb4_0900_ai_ci COMMENT '笔记',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_word` (`student_id`,`word_id`,`deleted`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_word_id` (`word_id`),
  KEY `idx_mastery_level` (`mastery_level`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生词本表';

-- 3. 单元单词关联表
CREATE TABLE IF NOT EXISTS `unit_words` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `unit_id` bigint NOT NULL COMMENT '单元ID',
  `word_id` bigint NOT NULL COMMENT '单词ID',
  `order_num` int DEFAULT 0 COMMENT '排序号',
  `is_key_word` tinyint DEFAULT 0 COMMENT '是否为重点单词：0-否，1-是',
  `deleted` tinyint DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_unit_word` (`unit_id`,`word_id`,`deleted`),
  KEY `idx_unit_id` (`unit_id`),
  KEY `idx_word_id` (`word_id`),
  KEY `idx_order_num` (`order_num`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单元单词关联表';

-- 验证表是否创建成功
SELECT
    'learning_records' as table_name, COUNT(*) as exists_check
FROM information_schema.tables
WHERE table_schema = 'english_learning' AND table_name = 'learning_records'
UNION ALL
SELECT
    'vocabulary_books', COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'english_learning' AND table_name = 'vocabulary_books'
UNION ALL
SELECT
    'unit_words', COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'english_learning' AND table_name = 'unit_words';

-- 如果以上查询返回的 exists_check 都是 1，说明表创建成功
