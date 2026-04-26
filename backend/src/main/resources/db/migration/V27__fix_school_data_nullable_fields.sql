-- ============================================================================
-- Flyway Migration V27 — 修复学校数据表字段约束
-- ============================================================================
-- 背景：CDSP API 不提供 student_name 等字段，但多个表定义为 NOT NULL
-- 导致 student_schedule 和 course_selection 同步 100% 失败（各 1000 条）
--
-- 修复范围：
--   - student_schedule: student_name, teacher_id, teacher_name, week_range, class_location
--   - course_selection: student_name, teacher_id, teacher_name
-- ============================================================================

USE `english_learning`;

-- ════════════════════════════════════════════════════════════════
-- 1. student_schedule 表字段约束修复
-- ════════════════════════════════════════════════════════════════

ALTER TABLE student_schedule MODIFY COLUMN student_name VARCHAR(100) COMMENT '学生姓名（CDSP 不提供，可为空）';
ALTER TABLE student_schedule MODIFY COLUMN teacher_id VARCHAR(50) COMMENT '教师工号（可为空）';
ALTER TABLE student_schedule MODIFY COLUMN teacher_name VARCHAR(100) COMMENT '教师姓名（可为空）';
ALTER TABLE student_schedule MODIFY COLUMN week_range VARCHAR(50) COMMENT '周次范围（可为空）';
ALTER TABLE student_schedule MODIFY COLUMN class_location VARCHAR(200) COMMENT '上课地点（可为空）';

-- ════════════════════════════════════════════════════════════════
-- 2. course_selection 表字段约束修复
-- ════════════════════════════════════════════════════════════════

ALTER TABLE course_selection MODIFY COLUMN student_name VARCHAR(100) COMMENT '学生姓名（CDSP 不提供，可为空）';
ALTER TABLE course_selection MODIFY COLUMN teacher_id VARCHAR(50) COMMENT '教师工号（可为空）';
ALTER TABLE course_selection MODIFY COLUMN teacher_name VARCHAR(50) COMMENT '教师姓名（可为空）';
