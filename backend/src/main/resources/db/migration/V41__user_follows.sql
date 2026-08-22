-- V38: User follow system
-- User follow relationship table
CREATE TABLE IF NOT EXISTS user_follows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id BIGINT NOT NULL COMMENT 'Follower user ID',
    following_id BIGINT NOT NULL COMMENT 'Following user ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Follow time',
    UNIQUE KEY uk_follower_following (follower_id, following_id),
    KEY idx_following_id (following_id),
    KEY idx_follower_id (follower_id),
    CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES users(id),
    CONSTRAINT fk_follow_following FOREIGN KEY (following_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User follow relationship table';

-- Add follow count fields to users table for query performance
ALTER TABLE users
    ADD COLUMN following_count INT NOT NULL DEFAULT 0 COMMENT 'Following count',
    ADD COLUMN followers_count INT NOT NULL DEFAULT 0 COMMENT 'Followers count';
