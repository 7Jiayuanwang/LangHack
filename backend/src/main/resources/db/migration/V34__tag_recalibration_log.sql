-- V34: tag_recalibration_log — 标签阈值校准审计日志
CREATE TABLE IF NOT EXISTS tag_recalibration_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_code VARCHAR(50) NOT NULL,
    old_threshold VARCHAR(50) NOT NULL,
    new_threshold VARCHAR(50) NOT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    affected_user_count INT DEFAULT 0,
    operator VARCHAR(50) DEFAULT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trl_tag (tag_code),
    INDEX idx_trl_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
