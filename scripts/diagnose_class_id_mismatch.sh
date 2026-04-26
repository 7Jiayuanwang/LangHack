#!/bin/bash
# ============================================================================
# 诊断 course_schedule 外键约束失败的根因
# ============================================================================
# 问题：course_schedule 有 903/1000 条记录因外键约束失败
# 原因：class_id 在 teaching_class 表中不存在
#
# 本脚本检查：
# 1. teaching_class 表中有哪些 class_id
# 2. course_schedule 失败记录的 class_id 是什么
# 3. 两者的交集和差集
# ============================================================================

MYSQL_CONTAINER="langhack-mysql-master"
MYSQL_USER="root"
MYSQL_PASS="123456"
DB_NAME="english_learning"

echo "=========================================="
echo "1. 检查 teaching_class 表的 class_id 样本"
echo "=========================================="
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT class_id, course_code, course_name, teacher_id, academic_year, semester
FROM teaching_class
LIMIT 10;
" 2>/dev/null

echo ""
echo "=========================================="
echo "2. 检查 teaching_class 表的 class_id 数量"
echo "=========================================="
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT COUNT(DISTINCT class_id) as total_class_ids
FROM teaching_class;
" 2>/dev/null

echo ""
echo "=========================================="
echo "3. 检查 course_schedule 表的 class_id 样本"
echo "=========================================="
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT class_id, day_of_week, period, week_range, class_location, academic_year, semester
FROM course_schedule
LIMIT 10;
" 2>/dev/null

echo ""
echo "=========================================="
echo "4. 检查 course_schedule 中不存在于 teaching_class 的 class_id"
echo "=========================================="
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT cs.class_id, COUNT(*) as count
FROM course_schedule cs
LEFT JOIN teaching_class tc ON cs.class_id = tc.class_id
WHERE tc.class_id IS NULL
GROUP BY cs.class_id
LIMIT 20;
" 2>/dev/null

echo ""
echo "=========================================="
echo "5. 检查字段映射配置"
echo "=========================================="
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT data_source, source_field, target_field, remark
FROM field_mappings
WHERE data_source IN ('teacher_courses', 'course_schedule')
  AND target_field = 'classId'
ORDER BY data_source;
" 2>/dev/null

echo ""
echo "=========================================="
echo "6. 统计分析"
echo "=========================================="
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT
    'teaching_class' as table_name,
    COUNT(*) as total_records,
    COUNT(DISTINCT class_id) as unique_class_ids
FROM teaching_class
UNION ALL
SELECT
    'course_schedule' as table_name,
    COUNT(*) as total_records,
    COUNT(DISTINCT class_id) as unique_class_ids
FROM course_schedule;
" 2>/dev/null

echo ""
echo "=========================================="
echo "诊断完成"
echo "=========================================="
