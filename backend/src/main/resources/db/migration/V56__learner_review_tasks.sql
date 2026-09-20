-- 学习伙伴复习计划：每位学生、每篇平台文章保留一条下一次复习任务。
-- 只保存答题汇总和关联 ID，不复制题目、作文或对话正文。

CREATE TABLE IF NOT EXISTS learner_review_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '复习任务 ID',
    user_id BIGINT NOT NULL COMMENT '学生用户 ID，关联 users.id',
    text_id BIGINT NOT NULL COMMENT '平台文章 ID，关联 texts.id',
    last_attempt_id BIGINT DEFAULT NULL COMMENT '最近一次答题 ID，关联 student_ai_quiz_attempts.id',
    repeat_count INT NOT NULL DEFAULT 0 COMMENT '该文章累计答题次数',
    last_correct INT NOT NULL DEFAULT 0 COMMENT '最近一次答对题数',
    last_total INT NOT NULL DEFAULT 0 COMMENT '最近一次总题数',
    last_attempt_time DATETIME DEFAULT NULL COMMENT '最近一次答题时间',
    due_at DATETIME NOT NULL COMMENT '下次建议复习时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learner_review_user_text (user_id, text_id),
    KEY idx_learner_review_user_due (user_id, deleted, due_at),
    KEY idx_learner_review_last_attempt (last_attempt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习伙伴平台文章复习计划';

-- 为已有答题记录建立第一版计划；按提交时间和 ID 选最近一次，保留累计练习次数。
INSERT INTO learner_review_tasks
    (user_id, text_id, last_attempt_id, repeat_count, last_correct, last_total,
     last_attempt_time, due_at)
SELECT recent.student_id, recent.text_id, recent.id, recent.attempt_count,
       recent.correct_count, recent.total_questions, recent.submitted_time,
       DATE_ADD(
           DATE_ADD(DATE(recent.submitted_time),
               INTERVAL (CASE
                   WHEN recent.correct_count * 100 < recent.total_questions * 80 THEN 1
                   WHEN recent.attempt_count = 1 THEN 3
                   ELSE 7
               END) DAY),
           INTERVAL 7 HOUR)
FROM (
    SELECT attempts.*,
           ROW_NUMBER() OVER (
               PARTITION BY attempts.student_id, attempts.text_id
               ORDER BY attempts.submitted_time DESC, attempts.id DESC
           ) AS rn,
           COUNT(*) OVER (
               PARTITION BY attempts.student_id, attempts.text_id
           ) AS attempt_count
    FROM student_ai_quiz_attempts attempts
    WHERE attempts.total_questions > 0
) recent
WHERE recent.rn = 1
ON DUPLICATE KEY UPDATE updated_time = updated_time;
