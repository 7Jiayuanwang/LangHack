-- V23: AI 出题功能支持

-- 确保 text_exercises 表存在（生产环境由 complete_init.sql 创建，测试环境只跑 Flyway 需要兜底创建）
CREATE TABLE IF NOT EXISTS text_exercises (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    text_id BIGINT NOT NULL COMMENT '课文ID',
    question TEXT NOT NULL COMMENT '题目内容',
    exercise_type VARCHAR(32) DEFAULT 'MULTIPLE_CHOICE' COMMENT '习题类型',
    order_num INT DEFAULT 1 COMMENT '显示顺序',
    created_by BIGINT NOT NULL COMMENT '创建者ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_text_id (text_id),
    INDEX idx_exercise_type (exercise_type),
    INDEX idx_order_num (order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课文习题表';

-- 幂等添加新字段（MySQL 8.0 无 ADD COLUMN IF NOT EXISTS，使用 INFORMATION_SCHEMA 检查）
DROP PROCEDURE IF EXISTS _add_ai_quiz_columns;
CREATE PROCEDURE _add_ai_quiz_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_exercises'
          AND COLUMN_NAME = 'question_type'
    ) THEN
        ALTER TABLE text_exercises ADD COLUMN question_type VARCHAR(50) DEFAULT 'MULTIPLE_CHOICE' COMMENT '题目类型';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_exercises'
          AND COLUMN_NAME = 'analysis'
    ) THEN
        ALTER TABLE text_exercises ADD COLUMN analysis TEXT COMMENT '题目解析';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'text_exercises'
          AND COLUMN_NAME = 'distractor_analysis'
    ) THEN
        ALTER TABLE text_exercises ADD COLUMN distractor_analysis JSON COMMENT '干扰项分析';
    END IF;
END;
CALL _add_ai_quiz_columns();
DROP PROCEDURE IF EXISTS _add_ai_quiz_columns;

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
