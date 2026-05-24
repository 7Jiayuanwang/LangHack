-- V27: AI 对话持久化与分享
-- 对话会话表
CREATE TABLE IF NOT EXISTS ai_conversations (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(200) COMMENT '对话标题（自动生成或用户编辑）',
    message_count INT         DEFAULT 0,
    created_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      DEFAULT 0,
    INDEX idx_user_time (user_id, updated_time),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 对话消息表
CREATE TABLE IF NOT EXISTS ai_messages (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    role            ENUM('user','assistant') NOT NULL,
    content         TEXT         NOT NULL,
    selected_text   TEXT         COMMENT '用户选中的文本（仅 user 消息）',
    article_context TEXT         COMMENT '文章上下文（仅 user 消息）',
    created_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id, created_time),
    FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
);

-- 对话分享表
CREATE TABLE IF NOT EXISTS conversation_shares (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    share_token     VARCHAR(64)  UNIQUE NOT NULL COMMENT '分享令牌',
    access_password VARCHAR(128) COMMENT '访问密码（BCrypt 加密，可选）',
    expires_at      DATETIME     COMMENT '过期时间（NULL = 永久）',
    view_count      INT          DEFAULT 0,
    created_by      BIGINT       NOT NULL,
    created_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (share_token),
    INDEX idx_conversation (conversation_id),
    FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id)
);
