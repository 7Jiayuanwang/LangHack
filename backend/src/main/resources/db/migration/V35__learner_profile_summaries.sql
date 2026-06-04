-- V35: learner_profile_summaries — LLM 生成的画像摘要
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
