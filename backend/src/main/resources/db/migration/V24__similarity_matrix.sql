-- ============================================================================
-- Flyway Migration Script: V12 相似度矩阵表
-- ============================================================================

USE `english_learning`;
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ═══════════════════════════════════════════════════════════════
-- 1. text_similarity_matrix — 文章相似度矩阵
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `text_similarity_matrix` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `text_id` BIGINT NOT NULL COMMENT '文章ID',
    `similar_text_id` BIGINT NOT NULL COMMENT '相似文章ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_text_pair` (`text_id`, `similar_text_id`),
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章相似度矩阵';

-- ═══════════════════════════════════════════════════════════════
-- 2. text_similarity_matrix_tmp — 文章相似度矩阵临时表
--    表结构与 text_similarity_matrix 完全一致
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `text_similarity_matrix_tmp` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `text_id` BIGINT NOT NULL COMMENT '文章ID',
    `similar_text_id` BIGINT NOT NULL COMMENT '相似文章ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_text_pair` (`text_id`, `similar_text_id`),
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章相似度矩阵临时表';

-- ═══════════════════════════════════════════════════════════════
-- 3. user_similarity_matrix — 用户相似度矩阵
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `user_similarity_matrix` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `similar_user_id` BIGINT NOT NULL COMMENT '相似用户ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_pair` (`user_id`, `similar_user_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户相似度矩阵';

-- ═══════════════════════════════════════════════════════════════
-- 4. user_similarity_matrix_tmp — 用户相似度矩阵临时表
--    表结构与 user_similarity_matrix 完全一致
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `user_similarity_matrix_tmp` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `similar_user_id` BIGINT NOT NULL COMMENT '相似用户ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_pair` (`user_id`, `similar_user_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户相似度矩阵临时表';

-- ═══════════════════════════════════════════════════════════════
-- 5. v_user_text_interaction_scores — 用户-文章交互评分视图
--
-- 核心公式：
--   w_read(0.2)       — 是否读过（0/1）
--   w_complete(0.2)   — 是否完成阅读（0/1）
--   w_favorite(0.3)   — 是否收藏（0/1）
--   w_duration(0.1)   — 阅读时长得分（满分600秒）
--   w_accuracy(0.1)    — 习题正确率
--   w_underline(0.1)   — 划线次数得分（满分10次）
--
--   最终分数 = LEAST(1.0, 总分)，只考虑 role 为 'STUDENT' 或 'USER' 的用户
-- ═══════════════════════════════════════════════════════════════
CREATE OR REPLACE VIEW `v_user_text_interaction_scores` AS
SELECT
    u.id                                          AS user_id,
    t.id                                          AS text_id,

    -- w_read(0.2): 是否已读
    COALESCE(utr.read_at IS NOT NULL, 0)          AS w_read,

    -- w_complete(0.2): 是否完成阅读
    COALESCE(tlr.is_completed = 1, 0)             AS w_complete,

    -- w_favorite(0.3): 是否收藏
    COALESCE(af.id IS NOT NULL, 0)                AS w_favorite,

    -- w_duration(0.1): 阅读时长得分（满分600秒）
    LEAST(COALESCE(tlr.duration_seconds, 0) / 600.0, 1.0) AS w_duration,

    -- w_accuracy(0.1): 习题正确率
    COALESCE(
        sr.correct_rate,
        0.0
    )                                             AS w_accuracy,

    -- w_underline(0.1): 划线次数得分（满分10次）
    LEAST(COALESCE(ul.underline_count, 0) / 10.0, 1.0) AS w_underline,

    -- 最终加权总分（封顶1.0）
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
    -- 每用户每文章的划线次数
    SELECT user_id, text_id, COUNT(*) AS underline_count
    FROM underlines
    GROUP BY user_id, text_id
) ul ON ul.user_id = u.id AND ul.text_id = t.id
WHERE u.role IN ('STUDENT', 'USER');
