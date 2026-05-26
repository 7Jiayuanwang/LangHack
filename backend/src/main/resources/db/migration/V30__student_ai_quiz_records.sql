-- V30: 学生 AI 题做题记录（两表设计）
-- 设计目标：
--   1. 用户隔离：所有查询按 student_id 过滤
--   2. 包含文章ID：两张表都冗余 text_id，避免管理员查询 JOIN
--   3. 题目快照：regenerate 删原题后仍可追溯历史
--   4. 追加式：不设 unique，保留完整做题轨迹
--   5. 与现有 student_exercise_answers 共存（旧表服务 assignment/传统题，新表服务 AI 题）

-- 1. 做题尝试汇总表：每次提交一组 AI 题一行
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

-- 2. 答题明细表：每题一行，挂在 attempt 下
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
