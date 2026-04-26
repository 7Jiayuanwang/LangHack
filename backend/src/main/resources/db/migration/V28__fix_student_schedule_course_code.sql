-- ============================================================================
-- Flyway Migration V28 — 修复 course_code 字段约束
-- ============================================================================
-- 背景：CDSP API 不稳定提供 kcbh（课程编号）字段
-- 导致 student_schedule 和 course_selection 同步 100% 失败（各 1000/1000 条）
--
-- 错误信息：
--   - student_schedule: Field 'course_code' doesn't have a default value
--   - course_selection: Column 'course_code' cannot be null
--
-- 修复：将两个表的 course_code 改为可空，允许 CDSP API 不提供该字段时仍能插入记录
-- ============================================================================

USE `english_learning`;

-- ════════════════════════════════════════════════════════════════
-- 1. student_schedule 表 course_code 字段修复
-- ════════════════════════════════════════════════════════════════

ALTER TABLE student_schedule
MODIFY COLUMN course_code VARCHAR(50) COMMENT '课程编号（CDSP 可能不提供，可为空）';

-- ════════════════════════════════════════════════════════════════
-- 2. course_selection 表 course_code 字段修复
-- ════════════════════════════════════════════════════════════════
-- 注意：需要先删除外键约束，修改字段后重新添加

-- 删除外键约束
ALTER TABLE course_selection DROP FOREIGN KEY course_selection_ibfk_1;

-- 修改字段为可空
ALTER TABLE course_selection
MODIFY COLUMN course_code VARCHAR(50) COMMENT '课程编号（CDSP 可能不提供，可为空）';

-- 重新添加外键约束（允许 NULL 值）
ALTER TABLE course_selection
ADD CONSTRAINT course_selection_ibfk_1
FOREIGN KEY (course_code) REFERENCES school_course(course_code)
ON DELETE RESTRICT ON UPDATE CASCADE;
