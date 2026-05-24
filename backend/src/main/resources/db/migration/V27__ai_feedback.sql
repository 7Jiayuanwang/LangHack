-- V27: AI 用户反馈表
CREATE TABLE IF NOT EXISTS ai_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    call_log_id BIGINT NOT NULL COMMENT 'AI调用记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    rating TINYINT COMMENT '1-5星评分',
    is_helpful BOOLEAN COMMENT '是否有帮助',
    feedback_text TEXT COMMENT '文字反馈',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_call_log (call_log_id),
    INDEX idx_user (user_id),
    FOREIGN KEY (call_log_id) REFERENCES ai_call_log(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI用户反馈表';
