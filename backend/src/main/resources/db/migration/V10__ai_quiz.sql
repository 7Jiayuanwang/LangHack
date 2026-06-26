-- ═══════════════════════════════════════════════════════════════
-- V10: AI 出题功能整合
-- 整合自:
--   V23__ai_quiz_support.sql        (text_exercises + ai_quiz_session)
--   V24__hiagent_quiz_credentials.sql (system_config seed)
--   V25__exercise_options_table.sql (exercise_options)
--   V26__text_exercises_table.sql   (NO-OP — logic moved to V23, skipped)
--   V30__student_ai_quiz_records.sql (student_ai_quiz_attempts/records)
-- ═══════════════════════════════════════════════════════════════

-- ── Part 1: text_exercises 自包含建表（V23 整合） ─────────────────────────────
-- 生产已有则跳过；fresh DB 测试环境则创建

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

-- ── Part 2: text_exercises AI 字段幂等添加（V23 整合） ─────────────────────────

DROP PROCEDURE IF EXISTS _v10_ai_quiz_text_exercise_cols;
CREATE PROCEDURE _v10_ai_quiz_text_exercise_cols()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_exercises'
          AND COLUMN_NAME = 'question_type'
    ) THEN
        ALTER TABLE text_exercises ADD COLUMN question_type VARCHAR(50) DEFAULT 'MULTIPLE_CHOICE' COMMENT '题目类型' AFTER exercise_type;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_exercises'
          AND COLUMN_NAME = 'analysis'
    ) THEN
        ALTER TABLE text_exercises ADD COLUMN analysis TEXT COMMENT '题目解析' AFTER question_type;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_exercises'
          AND COLUMN_NAME = 'distractor_analysis'
    ) THEN
        ALTER TABLE text_exercises ADD COLUMN distractor_analysis JSON COMMENT '干扰项分析' AFTER analysis;
    END IF;
END;
CALL _v10_ai_quiz_text_exercise_cols();
DROP PROCEDURE IF EXISTS _v10_ai_quiz_text_exercise_cols;

-- ── Part 3: ai_quiz_session 表（V23 整合） ────────────────────────────────────

CREATE TABLE IF NOT EXISTS ai_quiz_session (
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

-- ── Part 4: HiAgent 出题凭证 seed（V24 整合） ────────────────────────────────

INSERT INTO system_config (config_group, config_key, config_value, description, is_secret)
VALUES
  ('ai_config', 'hiagent_quiz_api_key', '', 'HiAgent 出题应用 API Key（独立于改写/问答凭证）', 1),
  ('ai_config', 'hiagent_quiz_app_id', '', 'HiAgent 出题应用 App ID（独立于改写/问答凭证）', 0)
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- ── Part 5: exercise_options 表（V25 整合） ───────────────────────────────────

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

-- 注：V26 已 NO-OP（text_exercises 建表逻辑已合并到 Part 1），此处不再单独处理

-- ── Part 6: student_ai_quiz_attempts 做题尝试汇总表（V30 整合） ──────────────

CREATE TABLE IF NOT EXISTS `student_ai_quiz_attempts` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '尝试ID',

    `student_id`    BIGINT NOT NULL COMMENT '学生ID（用户隔离）',
    `text_id`       BIGINT NOT NULL COMMENT '文章ID',
    `session_id`    BIGINT NULL     COMMENT 'AI 出题会话ID (ai_quiz_session.id)',
    `assignment_id` BIGINT NULL     COMMENT '任务ID（可选）',

    `total_questions`    INT          NOT NULL DEFAULT 0    COMMENT '题目总数',
    `correct_count`      INT          NOT NULL DEFAULT 0    COMMENT '答对数',
    `score`              DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '得分百分比(0-100)',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生 AI 题做题尝试汇总';

-- ── Part 7: student_ai_quiz_records 答题明细表（V30 整合） ────────────────────

CREATE TABLE IF NOT EXISTS `student_ai_quiz_records` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '明细ID',

    `attempt_id` BIGINT NOT NULL COMMENT '做题尝试ID',

    -- 冗余字段（避免管理员"按学生/按文章"查询时强制 JOIN attempts）
    `student_id` BIGINT NOT NULL COMMENT '学生ID（用户隔离）',
    `text_id`    BIGINT NOT NULL COMMENT '文章ID',

    `exercise_id` BIGINT NULL COMMENT '题目ID（text_exercises.id，regenerate 时可能被删）',

    -- 题目快照（regenerate 删原题后历史仍可读）
    `question_snapshot`  TEXT        NOT NULL COMMENT '题干快照',
    `options_snapshot`   JSON        NULL     COMMENT '选项快照 [{"key":"A","content":"...","isCorrect":1},...]',
    `correct_answer`     VARCHAR(50) NULL     COMMENT '正确答案 key (A/B/C/D)',
    `analysis_snapshot`  TEXT        NULL     COMMENT '解析快照',

    -- 学生答题
    `user_answer`        VARCHAR(50) NOT NULL COMMENT '学生答案 key',
    `is_correct`         TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否正确(0/1)',
    `time_spent_seconds` INT         NULL     COMMENT '本题用时(秒)',
    `question_order`     INT         NULL     COMMENT '题号（展示顺序）',

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生 AI 题答题明细';