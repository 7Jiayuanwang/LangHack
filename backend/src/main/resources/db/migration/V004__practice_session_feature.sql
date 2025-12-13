-- Migration script for Practice Session feature
-- Creates tables: practice_sessions, favorite_questions

-- Table: practice_sessions
-- Stores practice session information for students
CREATE TABLE IF NOT EXISTS practice_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    session_name VARCHAR(200) COMMENT '会话名称',
    question_type VARCHAR(50) COMMENT '题目类型',
    difficulty VARCHAR(20) COMMENT '难度等级',
    question_count INT DEFAULT 0 COMMENT '题目总数',
    correct_count INT DEFAULT 0 COMMENT '正确数量',
    wrong_count INT DEFAULT 0 COMMENT '错误数量',
    total_time_spent INT DEFAULT 0 COMMENT '总用时(秒)',
    status VARCHAR(20) DEFAULT 'IN_PROGRESS' COMMENT '状态: IN_PROGRESS, COMPLETED',
    start_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    question_ids TEXT COMMENT '题目ID列表(JSON数组)',
    INDEX idx_student_id (student_id),
    INDEX idx_status (status),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='练习会话表';

-- Table: favorite_questions
-- Stores students' favorite/bookmarked questions
CREATE TABLE IF NOT EXISTS favorite_questions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_student_question (student_id, question_id),
    INDEX idx_student_id (student_id),
    INDEX idx_question_id (question_id),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏题目表';

-- Add indexes to answer_records table for better query performance
-- (Only if they don't already exist)
CREATE INDEX IF NOT EXISTS idx_answer_record_student_correct
    ON answer_records(student_id, is_correct);

CREATE INDEX IF NOT EXISTS idx_answer_record_question
    ON answer_records(question_id, answer_time);

CREATE INDEX IF NOT EXISTS idx_answer_record_time
    ON answer_records(answer_time);
