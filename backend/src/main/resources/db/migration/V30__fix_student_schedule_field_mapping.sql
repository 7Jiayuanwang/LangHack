-- ============================================================================
-- Flyway Migration V30 — 修复 student_schedule 字段映射错误（全面修正）
-- ============================================================================
-- 问题：student_schedule 的字段映射几乎全部错误
-- 根因：V12 使用的字段名与 CDSP API 实际返回的字段名不匹配
--
-- 对比（文档 vs V12 实际）：
--   class_id:       jxbid  vs xkkh   ❌
--   student_name:   xm     vs 缺失    ❌
--   teacher_id:     jgh    vs skjszgh ❌
--   teacher_name:   jsxm   vs jsmc    ❌
--   day_of_week:    skxqj  vs xqj     ❌
--   period:         skjc   vs djj     ❌
--   week_range:     zcd    vs zcap    ❌
--   class_location: cdmc   vs js      ❌
--
-- 修复：更正所有错误的字段映射
-- ============================================================================

USE `english_learning`;

-- 1. 修复 class_id 映射（教学班号）
UPDATE field_mappings
SET source_field = 'jxbid',
    remark = '教学班号（作 class_id，与 teaching_class.class_id 对应）'
WHERE data_source = 'student_schedule'
  AND target_field = 'classId'
  AND source_field = 'xkkh';

-- 2. 添加缺失的 student_name 映射
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark)
VALUES ('student_schedule', 'xm', 'studentName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2, 1, '学生姓名')
ON DUPLICATE KEY UPDATE source_field = 'xm', remark = '学生姓名';

-- 3. 修复 teacher_id 映射（教师工号）
UPDATE field_mappings
SET source_field = 'jgh',
    remark = '教师工号'
WHERE data_source = 'student_schedule'
  AND target_field = 'teacherId'
  AND source_field = 'skjszgh';

-- 4. 修复 teacher_name 映射（教师姓名）
UPDATE field_mappings
SET source_field = 'jsxm',
    remark = '教师姓名'
WHERE data_source = 'student_schedule'
  AND target_field = 'teacherName'
  AND source_field = 'jsmc';

-- 5. 修复 day_of_week 映射（上课星期几）
UPDATE field_mappings
SET source_field = 'skxqj',
    remark = '上课星期几 (1-7)'
WHERE data_source = 'student_schedule'
  AND target_field = 'dayOfWeek'
  AND source_field = 'xqj';

-- 6. 修复 period 映射（上课节次）
UPDATE field_mappings
SET source_field = 'skjc',
    remark = '上课节次（如"1-2"）'
WHERE data_source = 'student_schedule'
  AND target_field = 'period'
  AND source_field = 'djj';

-- 7. 修复 week_range 映射（周次）
UPDATE field_mappings
SET source_field = 'zcd',
    remark = '周次（如"1-16周"）'
WHERE data_source = 'student_schedule'
  AND target_field = 'weekRange'
  AND source_field = 'zcap';

-- 8. 修复 class_location 映射（上课地点）
UPDATE field_mappings
SET source_field = 'cdmc',
    remark = '上课地点名称'
WHERE data_source = 'student_schedule'
  AND target_field = 'classLocation'
  AND source_field = 'js';

-- 验证修复结果
SELECT data_source, source_field, target_field, remark
FROM field_mappings
WHERE data_source = 'student_schedule'
ORDER BY sort_order;
