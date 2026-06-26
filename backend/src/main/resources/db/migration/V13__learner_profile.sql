-- V13: learner_profile — 学习者画像领域合并迁移
-- 源文件: V32 (recommendation_events) + V33 (learner_profile_snapshots)
--       + V35 (learner_profile_summaries) + V36 (learner_composite_tags)

-- ============================================================
-- Part 1: recommendation_events — 推荐事件追踪
-- ============================================================
CREATE TABLE IF NOT EXISTS recommendation_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_id BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL COMMENT 'IMPRESSION/CLICK/DISMISS/BOOKMARK',
    source VARCHAR(30) DEFAULT NULL COMMENT 'item_cf/user_cf/fallback/persona',
    position INT DEFAULT NULL COMMENT 'Recommendation list position (0-based)',
    persona_tags VARCHAR(200) DEFAULT NULL COMMENT 'Active persona tag codes at event time, comma-separated',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_re_user (user_id),
    INDEX idx_re_text (text_id),
    INDEX idx_re_user_type (user_id, event_type),
    INDEX idx_re_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Part 2: learner_profile_snapshots — 学习者画像每日快照
-- ============================================================
CREATE TABLE IF NOT EXISTS learner_profile_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    profile_status VARCHAR(10) DEFAULT NULL COMMENT 'ready/partial/empty',
    risk_level VARCHAR(10) DEFAULT NULL COMMENT 'low/medium/high',
    active_days_7d INT DEFAULT 0,
    active_days_14d INT DEFAULT 0,
    study_minutes_14d INT DEFAULT 0,
    inactive_days INT DEFAULT 0,
    new_count INT DEFAULT 0,
    learning_count INT DEFAULT 0,
    mastered_count INT DEFAULT 0,
    weak_ratio DOUBLE DEFAULT 0.0,
    overdue_review_count INT DEFAULT 0,
    overdue_review_ratio DOUBLE DEFAULT 0.0,
    primary_content_version VARCHAR(20) DEFAULT NULL,
    version_distribution_json VARCHAR(500) DEFAULT NULL COMMENT 'JSON: {"cet4":0.6,"cet6":0.3}',
    matched_tag_codes VARCHAR(200) DEFAULT NULL COMMENT 'Comma-separated tag codes',
    tag_scores_json VARCHAR(500) DEFAULT NULL COMMENT 'JSON: tag evaluation details',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, snapshot_date),
    INDEX idx_lps_user (user_id),
    INDEX idx_lps_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Part 3: learner_profile_summaries — LLM 生成的画像摘要
-- ============================================================
CREATE TABLE IF NOT EXISTS learner_profile_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    summary_type VARCHAR(20) NOT NULL COMMENT 'PERSONAL/TEACHER_FACED/CLASS_COHORT',
    language VARCHAR(10) DEFAULT 'zh' COMMENT 'zh/en',
    summary_text TEXT NOT NULL,
    model_id VARCHAR(100) DEFAULT NULL,
    input_snapshot_id BIGINT DEFAULT NULL,
    input_tags_json VARCHAR(500) DEFAULT NULL,
    generation_time_ms INT DEFAULT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_lps_user (user_id),
    INDEX idx_lps_type (summary_type),
    INDEX idx_lps_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Part 4: learner_composite_tags — 可配置的组合标签
-- ============================================================
CREATE TABLE IF NOT EXISTS learner_composite_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    tag_type VARCHAR(20) NOT NULL COMMENT 'risk/ability/habit/preference/positive',
    priority INT DEFAULT 50,
    rule_definition JSON NOT NULL COMMENT '{"conditions":[...],"combiner":"AND"}',
    action_hint VARCHAR(200) DEFAULT NULL,
    enabled TINYINT(1) DEFAULT 1,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
