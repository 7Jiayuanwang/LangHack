-- ============================================================================
-- Flyway Migration V13 — 优化 v_user_text_interaction_scores 视图
-- ============================================================================
-- 背景：V12 原视图使用 users CROSS JOIN texts（180 × 520 ≈ 9.3 万行全扫），
-- 无交互的 (user, text) 对也会参与计算后被 WHERE score > 0 滤掉，浪费 I/O。
-- 改为由四张交互源表 UNION 驱动，只对真实有过交互的 (user, text) 对生成行。
--
-- 源表：
--   - user_text_reads       (user_id,   text_id)
--   - text_learning_records (student_id,text_id)  student_id 对应 user_id
--   - article_favorites     (student_id,text_id)  同上
--   - underlines            (user_id,   text_id)
-- ============================================================================

USE `english_learning`;
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
