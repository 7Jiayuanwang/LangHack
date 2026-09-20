-- 学习伙伴智能体：用户主动目标、兴趣、偏好与可管理记忆。
-- 智能体分支原用 V49；合并后 V49 属于 sentence_favorites。
-- 这些表用 IF NOT EXISTS 保持幂等，以兼容已运行旧智能体分支的环境。

CREATE TABLE IF NOT EXISTS learner_agent_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    current_level VARCHAR(30) DEFAULT NULL COMMENT 'BEGINNER/ELEMENTARY/INTERMEDIATE/UPPER_INTERMEDIATE/ADVANCED',
    weekly_minutes INT DEFAULT NULL COMMENT '用户每周可投入的英语学习分钟数',
    preferred_formats_json VARCHAR(500) DEFAULT NULL COMMENT 'JSON array: READING/VOCABULARY/QUIZ/WRITING/CONVERSATION/VIDEO',
    encouragement_style VARCHAR(20) DEFAULT 'GENTLE' COMMENT 'GENTLE/DIRECT/CHALLENGE',
    memory_consent TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许从会话和行为中提取长期记忆',
    onboarding_completed TINYINT(1) NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_learner_agent_profile_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习伙伴智能体用户设置';

CREATE TABLE IF NOT EXISTS learner_goals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    goal_type VARCHAR(30) NOT NULL COMMENT 'CET4/CET6/POSTGRADUATE/ACADEMIC_READING/COURSE/GENERAL/OTHER',
    title VARCHAR(100) NOT NULL,
    priority INT NOT NULL DEFAULT 3 COMMENT '1-5，5为最高',
    target_date DATE DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/COMPLETED',
    source VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER/INFERRED',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_learner_goals_user_status (user_id, status),
    INDEX idx_learner_goals_target_date (target_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习伙伴用户多目标';

CREATE TABLE IF NOT EXISTS learner_interests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER/INFERRED',
    weight DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_learner_interests_user_name (user_id, name),
    INDEX idx_learner_interests_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习伙伴用户兴趣';

CREATE TABLE IF NOT EXISTS learner_memories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    memory_key VARCHAR(80) NOT NULL,
    memory_type VARCHAR(30) NOT NULL COMMENT 'PROFILE/PREFERENCE/HABIT/GOAL/DIFFICULTY',
    content VARCHAR(500) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    source VARCHAR(30) NOT NULL COMMENT 'ONBOARDING/USER_CONFIRMED/CONVERSATION/BEHAVIOR',
    evidence_type VARCHAR(30) DEFAULT NULL,
    evidence_id VARCHAR(100) DEFAULT NULL,
    is_sensitive TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'CANDIDATE/ACTIVE/REJECTED/EXPIRED',
    expires_time DATETIME DEFAULT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_learner_memories_user_key (user_id, memory_key),
    INDEX idx_learner_memories_user_status (user_id, status),
    INDEX idx_learner_memories_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户可查看和管理的学习伙伴长期记忆';

-- 若旧智能体分支已占用 Flyway V49，合并后的 V49__sentence_favorites.sql
-- 不会再次执行；在此补建该表，避免已部署环境遗漏例句收藏功能。
CREATE TABLE IF NOT EXISTS sentence_favorites (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  text_id BIGINT NOT NULL,
  sentence VARCHAR(2000) NOT NULL,
  sentence_hash BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(sentence, 256))) STORED,
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sentence_favorite (user_id, text_id, sentence_hash),
  KEY idx_sentence_favorite_user_created (user_id, created_time),
  KEY idx_sentence_favorite_text (text_id),
  CONSTRAINT fk_sentence_favorite_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_sentence_favorite_text FOREIGN KEY (text_id) REFERENCES texts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户例句收藏';
