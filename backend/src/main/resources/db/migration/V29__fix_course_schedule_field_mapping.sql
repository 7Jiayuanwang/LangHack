-- ============================================================================
-- Flyway Migration V29 — 修复 course_schedule 字段映射错误
-- ============================================================================
-- 问题：course_schedule 同步失败 903/1000 条，外键约束失败
-- 根因：字段映射错误
--   - 文档：course_schedule 接口返回 jxbid（教学班号）
--   - 实际：V12 映射使用 xkkch（选课课号）
--   - 结果：teaching_class 存储 kcbh（课程班号），course_schedule 存储 xkkch（选课课号）
--          两者不匹配，导致外键约束失败
--
-- 修复：更正 field_mappings 表中 course_schedule 的 classId 映射
--   - 从 xkkch（选课课号）改为 jxbid（教学班号）
-- ============================================================================

USE `english_learning`;

-- 更新 course_schedule 的 classId 字段映射
UPDATE field_mappings
SET source_field = 'jxbid',
    remark = '教学班号（作 class_id，与 teaching_class.class_id 对应）'
WHERE data_source = 'course_schedule'
  AND target_field = 'classId'
  AND source_field = 'xkkch';

-- 验证修复结果
SELECT data_source, source_field, target_field, remark
FROM field_mappings
WHERE data_source = 'course_schedule'
  AND target_field = 'classId';
