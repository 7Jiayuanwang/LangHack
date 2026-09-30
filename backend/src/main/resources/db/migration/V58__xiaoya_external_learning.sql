-- 小芽的站外英语学习记录仅属于学生本人；点击链接和浏览行为不写入此表。
-- 同一天可以记录多条英文收获，但成长养分最多按一天计算一次。
CREATE TABLE IF NOT EXISTS xiaoya_external_learning (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    learning_day DATE NOT NULL,
    source_type VARCHAR(12) NOT NULL,
    source_title VARCHAR(120) DEFAULT NULL,
    takeaway_en VARCHAR(500) NOT NULL,
    takeaway_hash CHAR(64) NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_xiaoya_external_day_takeaway (user_id, learning_day, takeaway_hash),
    KEY idx_xiaoya_external_user_day (user_id, learning_day, created_time),
    CONSTRAINT fk_xiaoya_external_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
