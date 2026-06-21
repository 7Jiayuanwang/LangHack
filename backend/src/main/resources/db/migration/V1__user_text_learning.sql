-- ═══════════════════════════════════════════════════════════════
-- V1: 用户/课文/学习 (User / Text / Learning Records)
-- 整合自: V1, V2, V10, V11, V17, V21, V22
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- Part 1: 成绩记录表 + 任务扩展 [原 V1]
--   - score_records 表 (含内联唯一约束)
--   - assignments.weight + ideal_study_time_minutes + idx_weight + idx_ideal_study_time
-- ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS score_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    assignment_id BIGINT COMMENT '任务ID',
    course_id BIGINT COMMENT '课程ID',
    unit_id BIGINT COMMENT '单元ID',
    score_type VARCHAR(50) NOT NULL COMMENT '分数类型: ASSIGNMENT/COURSE/UNIT/EXAM',
    score INT NOT NULL COMMENT '分数值(0-100)',
    full_score INT NOT NULL DEFAULT 100 COMMENT '满分值',
    score_details TEXT COMMENT 'JSON格式的得分详情',
    remark VARCHAR(500) COMMENT '备注',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT fk_score_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    -- 防止同一学生同一任务重复评分 (assignment_id 为 NULL 时不生效，这是预期行为)
    UNIQUE KEY uk_student_assignment_type (student_id, assignment_id, score_type),
    INDEX idx_student_id (student_id),
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_course_id (course_id),
    INDEX idx_unit_id (unit_id),
    INDEX idx_score_type (score_type),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩记录表';

DROP PROCEDURE IF EXISTS _v1_assignments_cols;
CREATE PROCEDURE _v1_assignments_cols()
BEGIN
    -- 任务权重（加权平均分）[原 V6]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'assignments' AND COLUMN_NAME = 'weight') THEN
        ALTER TABLE assignments ADD COLUMN weight DECIMAL(5,2) DEFAULT 1.00
            COMMENT '任务权重（用于计算加权平均分，默认1.00）' AFTER deadline;
    END IF;
    -- 理想学习时长 [原 V7]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'assignments' AND COLUMN_NAME = 'ideal_study_time_minutes') THEN
        ALTER TABLE assignments ADD COLUMN ideal_study_time_minutes INT DEFAULT 60
            COMMENT '理想学习时长（分钟），默认60分钟' AFTER weight;
    END IF;
END;
CALL _v1_assignments_cols();
DROP PROCEDURE IF EXISTS _v1_assignments_cols;

DROP PROCEDURE IF EXISTS _v1_assignments_idx;
CREATE PROCEDURE _v1_assignments_idx()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'assignments' AND INDEX_NAME = 'idx_weight') THEN
        CREATE INDEX idx_weight ON assignments (weight);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'assignments' AND INDEX_NAME = 'idx_ideal_study_time') THEN
        CREATE INDEX idx_ideal_study_time ON assignments (ideal_study_time_minutes);
    END IF;
END;
CALL _v1_assignments_idx();
DROP PROCEDURE IF EXISTS _v1_assignments_idx;

-- ────────────────────────────────────────
-- Part 2: 学习记录添加课文关联 [原 V2]
--   - learning_records.text_id + FK + idx
-- ────────────────────────────────────────

DROP PROCEDURE IF EXISTS _v1_lr_text_col;
CREATE PROCEDURE _v1_lr_text_col()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'learning_records' AND COLUMN_NAME = 'text_id') THEN
        ALTER TABLE learning_records ADD COLUMN text_id BIGINT COMMENT '关联的课文ID' AFTER word_id;
    END IF;
END;
CALL _v1_lr_text_col();
DROP PROCEDURE IF EXISTS _v1_lr_text_col;

DROP PROCEDURE IF EXISTS _v1_lr_text_fk;
CREATE PROCEDURE _v1_lr_text_fk()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'learning_records' AND CONSTRAINT_NAME = 'fk_learning_record_text'
    ) THEN
        ALTER TABLE learning_records ADD CONSTRAINT fk_learning_record_text
            FOREIGN KEY (text_id) REFERENCES texts(id) ON DELETE SET NULL;
    END IF;
END;
CALL _v1_lr_text_fk();
DROP PROCEDURE IF EXISTS _v1_lr_text_fk;

DROP PROCEDURE IF EXISTS _v1_lr_text_idx;
CREATE PROCEDURE _v1_lr_text_idx()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'learning_records' AND INDEX_NAME = 'idx_text_id'
    ) THEN
        CREATE INDEX idx_text_id ON learning_records (text_id);
    END IF;
END;
CALL _v1_lr_text_idx();
DROP PROCEDURE IF EXISTS _v1_lr_text_idx;

-- ────────────────────────────────────────
-- Part 3: 课文表扩展 — 按源迁移顺序链式追加列 [原 V2 + V10 + V17 + V22]
--   列顺序链:
--     V2  : chinese_parallel_content, markdown_content (AFTER content),
--           content_type, cet4_markdown_content, cet6_markdown_content,
--           academic_markdown_content, chinese_parallel_markdown_content,
--           is_featured, featured_priority, cet4_content (AFTER content),
--           cet6_content, academic_content, uploaded_by, tags,
--           view_count, like_count, is_public, status
--     V10 : cover_url (AFTER chinese_parallel_markdown_content)
--     V17 : read_count  ← 注意: V17 漏了 texts.read_count (V22 才补)
--     V22 : texts.read_count (最终补齐)
-- ────────────────────────────────────────

DROP PROCEDURE IF EXISTS _v1_texts_cols;
CREATE PROCEDURE _v1_texts_cols()
BEGIN
    -- 中英对照内容 [原 V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'chinese_parallel_content') THEN
        ALTER TABLE texts ADD COLUMN chinese_parallel_content TEXT COMMENT '中英对照版本内容';
    END IF;
    -- Markdown 支持 [原 V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'markdown_content') THEN
        ALTER TABLE texts ADD COLUMN markdown_content LONGTEXT COMMENT 'Markdown格式的课文内容' AFTER content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'content_type') THEN
        ALTER TABLE texts ADD COLUMN content_type ENUM('TEXT','MARKDOWN') DEFAULT 'TEXT'
            COMMENT '内容类型：TEXT-纯文本，MARKDOWN-Markdown格式' AFTER markdown_content;
    END IF;
    -- 多难度版本 Markdown [原 V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'cet4_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN cet4_markdown_content LONGTEXT
            COMMENT 'CET-4难度的Markdown版本' AFTER chinese_parallel_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'cet6_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN cet6_markdown_content LONGTEXT
            COMMENT 'CET-6难度的Markdown版本' AFTER cet4_markdown_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'academic_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN academic_markdown_content LONGTEXT
            COMMENT '学术版的Markdown版本' AFTER cet6_markdown_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'chinese_parallel_markdown_content') THEN
        ALTER TABLE texts ADD COLUMN chinese_parallel_markdown_content LONGTEXT
            COMMENT '中英对照的Markdown版本' AFTER academic_markdown_content;
    END IF;
    -- 推荐文章标识 [原 V2]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'is_featured') THEN
        ALTER TABLE texts ADD COLUMN is_featured TINYINT(1) DEFAULT 0 COMMENT '是否推荐';
    END IF;
    -- 推荐优先级 [原 V10]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'featured_priority') THEN
        ALTER TABLE texts ADD COLUMN featured_priority INT NOT NULL DEFAULT 0
            COMMENT '推荐优先级，值越大越靠前显示（0=默认）';
    END IF;
    -- 多难度版本内容（纯文本）[原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'cet4_content') THEN
        ALTER TABLE texts ADD COLUMN cet4_content LONGTEXT COMMENT 'CET-4难度版本内容' AFTER content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'cet6_content') THEN
        ALTER TABLE texts ADD COLUMN cet6_content LONGTEXT COMMENT 'CET-6难度版本内容' AFTER cet4_content;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'academic_content') THEN
        ALTER TABLE texts ADD COLUMN academic_content LONGTEXT COMMENT '学术版版本内容' AFTER cet6_content;
    END IF;
    -- 上传者 [原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'uploaded_by') THEN
        ALTER TABLE texts ADD COLUMN uploaded_by BIGINT DEFAULT NULL COMMENT '上传者用户ID';
    END IF;
    -- 标签 [原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'tags') THEN
        ALTER TABLE texts ADD COLUMN tags VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔';
    END IF;
    -- 浏览/点赞 [原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'view_count') THEN
        ALTER TABLE texts ADD COLUMN view_count INT DEFAULT 0 COMMENT '浏览次数';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'like_count') THEN
        ALTER TABLE texts ADD COLUMN like_count INT DEFAULT 0 COMMENT '点赞次数';
    END IF;
    -- 公开状态 [原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'is_public') THEN
        ALTER TABLE texts ADD COLUMN is_public TINYINT(1) DEFAULT 1 COMMENT '是否公开（0=否，1=是）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'status') THEN
        ALTER TABLE texts ADD COLUMN status VARCHAR(20) DEFAULT 'PUBLISHED'
            COMMENT '状态：DRAFT/PUBLISHED/ARCHIVED';
    END IF;
    -- 封面图 URL [原 V10]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'cover_url') THEN
        ALTER TABLE texts ADD COLUMN cover_url VARCHAR(255) DEFAULT NULL
            COMMENT '封面图URL' AFTER chinese_parallel_markdown_content;
    END IF;
    -- 阅读次数 [原 V17 遗漏 / V22 补齐]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND COLUMN_NAME = 'read_count') THEN
        ALTER TABLE texts ADD COLUMN read_count INT NOT NULL DEFAULT 0 COMMENT '阅读次数';
    END IF;
END;
CALL _v1_texts_cols();
DROP PROCEDURE IF EXISTS _v1_texts_cols;

-- 推荐优先级复合索引 [原 V10]
DROP PROCEDURE IF EXISTS _v1_idx_featured_priority;
CREATE PROCEDURE _v1_idx_featured_priority()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'texts' AND INDEX_NAME = 'idx_texts_featured_priority'
    ) THEN
        CREATE INDEX idx_texts_featured_priority ON texts (is_featured, featured_priority DESC);
    END IF;
END;
CALL _v1_idx_featured_priority();
DROP PROCEDURE IF EXISTS _v1_idx_featured_priority;

-- unit_id 改为可空（支持课文从单元中移除）[原 V2]
ALTER TABLE texts MODIFY COLUMN unit_id BIGINT DEFAULT NULL
    COMMENT '单元ID（可为空，表示未分配到单元）';

-- ────────────────────────────────────────
-- Part 4: 文章学习记录表 + 用户已读记录表 [原 V2]
-- ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS text_learning_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    text_id BIGINT NOT NULL COMMENT '课文ID',
    assignment_id BIGINT COMMENT '关联的任务ID',
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读开始时间',
    end_time DATETIME COMMENT '阅读结束时间',
    duration_seconds INT DEFAULT 0 COMMENT '阅读时长(秒)',
    is_completed TINYINT(1) DEFAULT 0 COMMENT '是否已完成阅读',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_student_text (student_id, text_id),
    INDEX idx_assignment (assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章学习记录表';

CREATE TABLE IF NOT EXISTS user_text_reads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_id BIGINT NOT NULL,
    read_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_text (user_id, text_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户文章已读记录表';

-- ────────────────────────────────────────
-- Part 5: 用户表扩展 [原 V10 + V17 + V22]
--   列顺序链:
--     V10 : nickname (AFTER role) + rename avatar_url → avatar
--     V17 : read_article_count, read_total_times
--     V22 : 幂等补齐 (同一列)
-- ────────────────────────────────────────

DROP PROCEDURE IF EXISTS _v1_users_cols;
CREATE PROCEDURE _v1_users_cols()
BEGIN
    -- nickname [原 V10]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'nickname') THEN
        ALTER TABLE users ADD COLUMN nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称' AFTER role;
    END IF;
    -- 已读文章数 [原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'read_article_count') THEN
        ALTER TABLE users ADD COLUMN read_article_count INT NOT NULL DEFAULT 0 COMMENT '已读文章数';
    END IF;
    -- 累计阅读次数 [原 V17]
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'read_total_times') THEN
        ALTER TABLE users ADD COLUMN read_total_times INT NOT NULL DEFAULT 0 COMMENT '累计阅读次数';
    END IF;
END;
CALL _v1_users_cols();
DROP PROCEDURE IF EXISTS _v1_users_cols;

-- 重命名 avatar_url → avatar（幂等）[原 V10]
-- 必须放在 users 加列之后：MySQL 不允许在一条 ALTER 中同时 ADD 和 RENAME 同一表
DROP PROCEDURE IF EXISTS _v1_users_rename_avatar;
CREATE PROCEDURE _v1_users_rename_avatar()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'avatar_url')
       AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'avatar') THEN
        ALTER TABLE users RENAME COLUMN avatar_url TO avatar;
    END IF;
END;
CALL _v1_users_rename_avatar();
DROP PROCEDURE IF EXISTS _v1_users_rename_avatar;

-- ────────────────────────────────────────
-- Part 6: user_text_reads 阅读次数统计 [原 V17 + V22 防御性补齐]
--   V17 首次引入，V22 在历史 DB 未应用时防御性重试
-- ────────────────────────────────────────

DROP PROCEDURE IF EXISTS _v1_user_text_reads_cols;
CREATE PROCEDURE _v1_user_text_reads_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_text_reads' AND COLUMN_NAME = 'read_count') THEN
        ALTER TABLE user_text_reads ADD COLUMN read_count INT NOT NULL DEFAULT 1 COMMENT '阅读次数';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_text_reads' AND COLUMN_NAME = 'last_read_at') THEN
        ALTER TABLE user_text_reads ADD COLUMN last_read_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后阅读时间';
    END IF;
END;
CALL _v1_user_text_reads_cols();
DROP PROCEDURE IF EXISTS _v1_user_text_reads_cols;

-- ────────────────────────────────────────
-- Part 7: 清理 sso_config 脏规则 + 修复误映射角色 [原 V11]
--   - DELETE sso_config WHERE config_key strip 后不含 '='
--   - UPDATE users 把被 SSO 错误映射为 TEACHER 的 CAS 学生恢复为 STUDENT
-- ────────────────────────────────────────

DELETE FROM sso_config
WHERE config_type = 'ROLE_MAPPING'
  AND REPLACE(config_key, 'sso.role_mapping.', '') NOT LIKE '%=%';

UPDATE users u
INNER JOIN sso_user_bindings b ON u.id = b.user_id AND b.deleted = 0
SET u.role = 'STUDENT', u.updated_time = NOW()
WHERE u.role = 'TEACHER'
  AND u.sso_bound = 1
  AND u.deleted = 0
  AND b.auto_approved = 1
  AND b.sso_member_of IS NOT NULL
  AND b.sso_member_of NOT LIKE '%ou=教师%'
  AND b.sso_member_of NOT LIKE '%ou=teacher%';