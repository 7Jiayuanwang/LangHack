-- ═══════════════════════════════════════════════════════════════
-- V28: 协作式互动阅读 — 公共批注 + 点赞
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS text_annotations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    text_id BIGINT NOT NULL COMMENT '关联课文ID',
    user_id BIGINT NOT NULL COMMENT '批注者ID',
    selected_text TEXT NOT NULL COMMENT '被批注的原文片段',
    note TEXT NOT NULL COMMENT '批注内容',
    upvote_count INT DEFAULT 0 COMMENT '点赞数',
    deleted TINYINT DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_text_id (text_id),
    INDEX idx_user_id (user_id),
    INDEX idx_text_deleted (text_id, deleted),
    CONSTRAINT fk_ann_text FOREIGN KEY (text_id) REFERENCES texts(id) ON DELETE CASCADE,
    CONSTRAINT fk_ann_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协作批注表';

CREATE TABLE IF NOT EXISTS annotation_upvotes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    annotation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ann_user (annotation_id, user_id),
    CONSTRAINT fk_upvote_ann FOREIGN KEY (annotation_id) REFERENCES text_annotations(id) ON DELETE CASCADE,
    CONSTRAINT fk_upvote_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批注点赞表';
