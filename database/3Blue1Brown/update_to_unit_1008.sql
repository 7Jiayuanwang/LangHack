-- ============================================================
-- 将3Blue1Brown文章从unit_id=999更新到unit_id=1008
-- 目标单元: ISA reading 课程下的单元 1008
-- 执行时间: 2026-01-29
-- ============================================================

USE english_learning;

-- 首先确认目标单元存在
SELECT id, course_id, name, description 
FROM units 
WHERE id = 1008;

-- 显示将要更新的文章列表
SELECT id, title, unit_id, tags
FROM texts
WHERE tags LIKE '%3Blue1Brown%' 
  AND deleted = 0
ORDER BY id;

-- 更新所有3Blue1Brown标签的文章到单元1008
UPDATE texts
SET unit_id = 1008,
    updated_time = NOW()
WHERE tags LIKE '%3Blue1Brown%'
  AND deleted = 0;

-- 显示更新结果
SELECT 
    '更新完成!' AS message,
    COUNT(*) AS updated_count
FROM texts
WHERE unit_id = 1008 
  AND tags LIKE '%3Blue1Brown%'
  AND deleted = 0;

-- 显示更新后的文章列表
SELECT id, title, unit_id, tags, updated_time
FROM texts
WHERE unit_id = 1008 
  AND tags LIKE '%3Blue1Brown%'
  AND deleted = 0
ORDER BY id;
