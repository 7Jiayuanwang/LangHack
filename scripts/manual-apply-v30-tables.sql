-- ============================================================================
-- 手动补表脚本: V30 + student_exercise_answers
-- ============================================================================
-- 适用场景:
--   服务器现有数据库已经初始化过 (跑过 04-apply-all-migrations.sql),
--   但缺少 PR #31 引入的新表 (student_exercise_answers / student_ai_quiz_attempts /
--   student_ai_quiz_records),且当前 Flyway 被禁用 (docker-compose 中 SPRING_FLYWAY_ENABLED=false)。
--
-- 安全性:
--   - 全部使用 CREATE TABLE IF NOT EXISTS,可重复执行,不会破坏已有数据。
--
-- 使用方法 (在服务器上执行):
--   1) 把本文件 scp / 拷贝到服务器: /data/langhack/scripts/manual-apply-v30-tables.sql
--   2) 拷贝到容器: docker cp /data/langhack/scripts/manual-apply-v30-tables.sql langhack-mysql-master:/tmp/
--   3) 执行: docker exec -i langhack-mysql-master sh -c \
--               'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" english_learning < /tmp/manual-apply-v30-tables.sql'
--   4) 验证: docker exec langhack-mysql-master mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
--             "USE english_learning; SHOW TABLES LIKE 'student_%';"
--      预期看到 3 张表 (student_exercise_answers / student_ai_quiz_attempts / student_ai_quiz_records)
--   5) 重启后端使其重新连接: docker-compose restart backend
-- ============================================================================

USE english_learning;

-- ────────────────────────────────────────
-- student_exercise_answers (V26 text_exercises 的配套答题表)
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

-- ────────────────────────────────────────
-- V30__student_ai_quiz_records.sql
-- 学生 AI 题做题记录 (两表设计)
-- ────────────────────────────────────────
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

-- 完成后输出确认
SELECT 'student_exercise_answers' AS table_name, COUNT(*) AS exists_count FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'student_exercise_answers'
UNION ALL
SELECT 'student_ai_quiz_attempts', COUNT(*) FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'student_ai_quiz_attempts'
UNION ALL
SELECT 'student_ai_quiz_records', COUNT(*) FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'student_ai_quiz_records';
