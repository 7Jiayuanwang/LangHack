-- ═══════════════════════════════════════════════════════════════
-- V1: 成绩记录表 + 任务扩展
-- 整合自: V5 (score_records), V6 (weight), V7 (ideal_study_time), V17 (unique constraint)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 创建成绩记录表 [原 V5]
--    唯一约束 uk_student_assignment_type 已内联 [原 V17]
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

-- ────────────────────────────────────────
-- 2. 任务表扩展 [原 V6 + V7]
--    使用存储过程 IF NOT EXISTS 防止重复添加列
--    注意: Flyway 不支持 DELIMITER 命令，直接定义存储过程
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _add_col_assignments_weight;
CREATE PROCEDURE _add_col_assignments_weight()
BEGIN
    -- 任务权重（加权平均分）
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='assignments' AND column_name='weight') THEN
        ALTER TABLE assignments ADD COLUMN weight DECIMAL(5,2) DEFAULT 1.00
            COMMENT '任务权重（用于计算加权平均分，默认1.00）' AFTER deadline;
    END IF;
    -- 理想学习时长
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='assignments' AND column_name='ideal_study_time_minutes') THEN
        ALTER TABLE assignments ADD COLUMN ideal_study_time_minutes INT DEFAULT 60
            COMMENT '理想学习时长（分钟），默认60分钟' AFTER weight;
    END IF;
END;
CALL _add_col_assignments_weight();
DROP PROCEDURE IF EXISTS _add_col_assignments_weight;

-- 索引（幂等：IF NOT EXISTS 检查）
DROP PROCEDURE IF EXISTS _add_idx_assignments;
CREATE PROCEDURE _add_idx_assignments()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE table_schema=DATABASE() AND table_name='assignments' AND index_name='idx_weight') THEN
        CREATE INDEX idx_weight ON assignments (weight);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE table_schema=DATABASE() AND table_name='assignments' AND index_name='idx_ideal_study_time') THEN
        CREATE INDEX idx_ideal_study_time ON assignments (ideal_study_time_minutes);
    END IF;
END;
CALL _add_idx_assignments();
DROP PROCEDURE IF EXISTS _add_idx_assignments;
