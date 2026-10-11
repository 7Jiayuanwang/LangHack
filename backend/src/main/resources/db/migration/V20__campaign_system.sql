-- V21__campaign_system.sql
-- 活动发布与统计系统
-- 创建日期: 2026-06-21

-- 1. 活动主表
CREATE TABLE IF NOT EXISTS campaign_activities (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL COMMENT '活动标题',
    description TEXT COMMENT '活动描述',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '结束时间',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT/ACTIVE/ENDED/ARCHIVED',
    completion_rule VARCHAR(50) DEFAULT 'THRESHOLD' COMMENT '完成规则: ANY/ALL/THRESHOLD',
    completion_threshold INT DEFAULT 1 COMMENT '完成阈值（THRESHOLD模式）',

    -- 报名设置
    enrollment_mode VARCHAR(20) DEFAULT 'AUTO' COMMENT '报名模式: AUTO/MANUAL/INVITE_ONLY',
    enrollment_scope VARCHAR(20) DEFAULT 'ALL' COMMENT '报名范围: ALL/COLLEGE/GRADE/CLASS/CUSTOM',
    enrollment_target_ids TEXT COMMENT '允许报名的ID列表（JSON数组）',
    max_participants INT COMMENT '最大参与人数（NULL=不限制）',

    -- 隐私设置
    show_scores TINYINT(1) DEFAULT 1 COMMENT '是否显示具体分数',
    show_ranking TINYINT(1) DEFAULT 1 COMMENT '是否显示排名',

    created_by BIGINT COMMENT '创建者用户ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT(1) DEFAULT 0,

    INDEX idx_status (status),
    INDEX idx_time_range (start_time, end_time),
    INDEX idx_enrollment_mode (enrollment_mode),
    INDEX idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动主表';

-- 2. 活动任务表
CREATE TABLE IF NOT EXISTS campaign_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL COMMENT '活动ID',
    task_name VARCHAR(100) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(50) NOT NULL COMMENT '任务类型: READING_TIME/WORD_LEARNING/TEXT_UPLOAD/FEEDBACK/AI_QA/MANUAL',
    task_unit VARCHAR(20) COMMENT '任务单位: MINUTES/COUNT/TIMES',
    target_value INT NOT NULL COMMENT '目标值（如阅读60分钟、学习50个单词）',
    points INT DEFAULT 1 COMMENT '完成得分',
    is_required TINYINT(1) DEFAULT 0 COMMENT '是否必做',
    auto_collect TINYINT(1) DEFAULT 1 COMMENT '是否自动采集数据',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT(1) DEFAULT 0,

    INDEX idx_campaign (campaign_id),
    INDEX idx_task_type (task_type),
    FOREIGN KEY (campaign_id) REFERENCES campaign_activities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动任务表';

-- 3. 参与记录表
CREATE TABLE IF NOT EXISTS campaign_participations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    enrollment_status VARCHAR(20) DEFAULT 'ENROLLED' COMMENT '报名状态: PENDING/ENROLLED/REJECTED/WITHDRAWN',
    enrollment_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    enrollment_source VARCHAR(20) DEFAULT 'MANUAL' COMMENT '报名来源: MANUAL/AUTO/ADMIN',

    total_score INT DEFAULT 0 COMMENT '总得分',
    completed_tasks INT DEFAULT 0 COMMENT '已完成任务数',
    completion_status VARCHAR(20) DEFAULT 'IN_PROGRESS' COMMENT '状态: NOT_STARTED/IN_PROGRESS/COMPLETED',

    join_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_campaign_user (campaign_id, user_id),
    INDEX idx_campaign (campaign_id),
    INDEX idx_user (user_id),
    INDEX idx_enrollment_status (enrollment_status),
    INDEX idx_score (total_score DESC),
    FOREIGN KEY (campaign_id) REFERENCES campaign_activities(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动参与记录';

-- 4. 任务完成记录表
CREATE TABLE IF NOT EXISTS campaign_task_completions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    completion_count INT DEFAULT 0 COMMENT '完成次数',
    current_value DECIMAL(10,2) DEFAULT 0 COMMENT '当前值（如已读45分钟）',
    is_completed TINYINT(1) DEFAULT 0 COMMENT '是否达标',
    last_update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_campaign_task_user (campaign_id, task_id, user_id),
    INDEX idx_campaign (campaign_id),
    INDEX idx_task (task_id),
    INDEX idx_user (user_id),
    FOREIGN KEY (campaign_id) REFERENCES campaign_activities(id) ON DELETE CASCADE,
    FOREIGN KEY (task_id) REFERENCES campaign_tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务完成明细';

-- 5. 手动数据录入表
CREATE TABLE IF NOT EXISTS campaign_manual_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    record_value DECIMAL(10,2) NOT NULL COMMENT '录入值',
    record_type VARCHAR(20) DEFAULT 'MANUAL' COMMENT 'MANUAL/IMPORT',
    import_batch_id VARCHAR(50) COMMENT '批量导入批次ID',
    remark TEXT COMMENT '备注',
    created_by BIGINT COMMENT '录入人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT(1) DEFAULT 0,

    INDEX idx_campaign (campaign_id),
    INDEX idx_task (task_id),
    INDEX idx_user (user_id),
    INDEX idx_import_batch (import_batch_id),
    FOREIGN KEY (campaign_id) REFERENCES campaign_activities(id) ON DELETE CASCADE,
    FOREIGN KEY (task_id) REFERENCES campaign_tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手动数据录入';

-- ═══════════════════════════════════════════════════════════════
-- 文章相似度矩阵（整合自 V20__text_similarity_matrix.sql）
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `text_similarity_matrix` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `text_id` BIGINT NOT NULL COMMENT '文章ID',
    `similar_text_id` BIGINT NOT NULL COMMENT '相似文章ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_text_pair` (`text_id`, `similar_text_id`),
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章相似度矩阵';

CREATE TABLE IF NOT EXISTS `text_similarity_matrix_tmp` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `text_id` BIGINT NOT NULL COMMENT '文章ID',
    `similar_text_id` BIGINT NOT NULL COMMENT '相似文章ID',
    `similarity` DECIMAL(5,4) NOT NULL COMMENT '相似度(0.0000~0.9999)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_text_pair` (`text_id`, `similar_text_id`),
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_similarity` (`similarity` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章相似度矩阵临时表';
