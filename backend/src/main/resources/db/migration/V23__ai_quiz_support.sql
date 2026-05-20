-- V23: AI 出题功能支持

-- text_exercises 表添加新字段
ALTER TABLE text_exercises ADD COLUMN question_type VARCHAR(50) DEFAULT 'MULTIPLE_CHOICE' COMMENT '题目类型';
ALTER TABLE text_exercises ADD COLUMN analysis TEXT COMMENT '题目解析';
ALTER TABLE text_exercises ADD COLUMN distractor_analysis JSON COMMENT '干扰项分析';

-- AI 出题会话表
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
