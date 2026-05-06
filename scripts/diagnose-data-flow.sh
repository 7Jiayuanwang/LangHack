#!/bin/bash
# 数据流诊断脚本：从 CDSP API 到前端显示的完整链路检查

set -e

MYSQL_CONTAINER="langhack-mysql-master"
MYSQL_USER="root"
MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-root}"
DB_NAME="english_learning"

echo "=========================================="
echo "数据流诊断脚本"
echo "=========================================="
echo ""

# 1. 检查数据库中的数据
echo "1. 检查数据库表数据量"
echo "----------------------------------------"
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT
    'administrative_class' as table_name, COUNT(*) as count FROM administrative_class
UNION ALL SELECT 'school_course', COUNT(*) FROM school_course
UNION ALL SELECT 'teaching_class', COUNT(*) FROM teaching_class
UNION ALL SELECT 'course_schedule', COUNT(*) FROM course_schedule
UNION ALL SELECT 'course_selection', COUNT(*) FROM course_selection
UNION ALL SELECT 'level_exam_scores', COUNT(*) FROM level_exam_scores
UNION ALL SELECT 'teaching_plans', COUNT(*) FROM teaching_plans
UNION ALL SELECT 'student_schedule', COUNT(*) FROM student_schedule;
"
echo ""

# 2. 检查选课记录样本数据
echo "2. 检查选课记录样本（前3条）"
echo "----------------------------------------"
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT
    student_id, student_name, course_code, course_name,
    class_id, teacher_name, credits, total_grade,
    academic_year, semester, sync_time
FROM course_selection
ORDER BY sync_time DESC
LIMIT 3;
" 2>/dev/null || echo "查询失败或无数据"
echo ""

# 3. 检查字段映射配置
echo "3. 检查 course_selection 字段映射"
echo "----------------------------------------"
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT
    source_field, target_field, converter, remark
FROM field_mappings
WHERE data_source = 'course_selection'
ORDER BY source_field;
"
echo ""

# 4. 检查最近的同步日志
echo "4. 检查最近的同步日志"
echo "----------------------------------------"
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT
    data_source, sync_type, status,
    total_count, success_count, fail_count,
    created_at
FROM sync_logs
WHERE data_source IN ('teacher_courses', 'course_selection')
ORDER BY created_at DESC
LIMIT 5;
"
echo ""

# 5. 检查外键约束问题
echo "5. 检查外键约束问题"
echo "----------------------------------------"
echo "5.1 检查 course_selection 中不存在的 class_id"
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT COUNT(DISTINCT cs.class_id) as orphaned_class_ids
FROM course_selection cs
LEFT JOIN teaching_class tc ON cs.class_id = tc.class_id
WHERE tc.class_id IS NULL;
" 2>/dev/null || echo "查询失败"

echo ""
echo "5.2 检查 course_selection 中不存在的 course_code"
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT COUNT(DISTINCT cs.course_code) as orphaned_course_codes
FROM course_selection cs
LEFT JOIN school_course sc ON cs.course_code = sc.course_code
WHERE sc.course_code IS NULL;
" 2>/dev/null || echo "查询失败"
echo ""

# 6. 检查学生选课记录
echo "6. 检查特定学生的选课记录"
echo "----------------------------------------"
echo "请输入学号（回车跳过）："
read -r STUDENT_ID
if [ -n "$STUDENT_ID" ]; then
    docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
    SELECT
        student_id, student_name, course_name, teacher_name,
        credits, total_grade, academic_year, semester
    FROM course_selection
    WHERE student_id = '$STUDENT_ID'
    ORDER BY academic_year DESC, semester DESC;
    "
else
    echo "已跳过"
fi
echo ""

# 7. 检查 teaching_class 数据
echo "7. 检查 teaching_class 表数据量"
echo "----------------------------------------"
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT COUNT(*) as total,
       COUNT(DISTINCT class_id) as unique_class_ids,
       COUNT(DISTINCT course_code) as unique_course_codes
FROM teaching_class;
"
echo ""

# 8. 生成诊断报告
echo "=========================================="
echo "诊断完成"
echo "=========================================="
echo ""
echo "常见问题排查："
echo "1. 如果 course_selection 表为空 → 需要同步 teacher_courses 和 course_selection"
echo "2. 如果 orphaned_class_ids > 0 → teaching_class 数据不完整，需要先同步 teacher_courses"
echo "3. 如果 orphaned_course_codes > 0 → school_course 数据不完整，需要先同步 school_course"
echo "4. 如果同步日志显示大量失败 → 检查 CDSP API 认证和字段映射"
echo ""
echo "修复建议："
echo "1. 按顺序同步：school_course → teacher_courses → course_selection"
echo "2. 使用运维端 → 学校数据同步 → 全量同步"
echo "3. 检查后端日志：docker logs langhack-backend 2>&1 | grep -i error"
echo ""
