#!/bin/bash
# ============================================================================
# 字段映射对比脚本 - 对比 SCHOOL_DATA_API_SPEC.md 与 V12 field_mappings
# ============================================================================

echo "=========================================="
echo "字段映射对比分析"
echo "=========================================="
echo ""

# 定义文档中的正确映射（根据 SCHOOL_DATA_API_SPEC.md v2.2）
cat > /tmp/expected_mappings.txt << 'EOF'
# 4. course_schedule (lowercase)
course_schedule|jxbid|class_id|教学班号
course_schedule|skxqj|day_of_week|上课星期几 (1-7)
course_schedule|skjc|period|上课节次
course_schedule|zcd|week_range|周次
course_schedule|cdmc|class_location|上课地点名称
course_schedule|xn|academic_year|学年
course_schedule|xq|semester|学期

# 8. teacher_courses (lowercase)
teacher_courses|rkjsh|teacher_id|教师工号
teacher_courses|rkjsxm|teacher_name|教师姓名
teacher_courses|kcbh|class_id|课程班号/教学班号
teacher_courses|kcdm|course_code|课程代码
teacher_courses|kcmc|course_name|课程名称
teacher_courses|xn|academic_year|学年
teacher_courses|xq|semester|学期
teacher_courses|kcbbm|class_name|课程班别名

# 9. student_schedule (lowercase)
student_schedule|xh|student_id|学号
student_schedule|xm|student_name|学生姓名
student_schedule|jxbid|class_id|教学班号
student_schedule|kcbh|course_code|课程编号
student_schedule|kcmc|course_name|课程名称
student_schedule|jgh|teacher_id|教师工号
student_schedule|jsxm|teacher_name|教师姓名
student_schedule|skxqj|day_of_week|上课星期几 (1-7)
student_schedule|skjc|period|上课节次
student_schedule|zcd|week_range|周次
student_schedule|cdmc|class_location|上课地点名称
student_schedule|xn|academic_year|学年
student_schedule|xq|semester|学期
EOF

echo "1. 检查 course_schedule 字段映射"
echo "=========================================="
echo "预期: jxbid → class_id (教学班号)"
echo "实际:"
grep "course_schedule.*classId" "G:\GitHub_local\project\英语学习平台\LangHack\backend\src\main\resources\db\migration\V12__school_data_integration.sql" | head -1
echo ""

echo "2. 检查 student_schedule 字段映射"
echo "=========================================="
echo "预期: jxbid → class_id (教学班号), kcbh → course_code (课程编号)"
echo "实际:"
grep "student_schedule.*'classId'\|student_schedule.*'courseCode'" "G:\GitHub_local\project\英语学习平台\LangHack\backend\src\main\resources\db\migration\V12__school_data_integration.sql"
echo ""

echo "3. 检查 teacher_courses 字段映射"
echo "=========================================="
echo "预期: kcbh → class_id (课程班号), kcdm → course_code (课程代码)"
echo "实际:"
grep "teacher_courses.*'classId'\|teacher_courses.*'courseCode'" "G:\GitHub_local\project\英语学习平台\LangHack\backend\src\main\resources\db\migration\V12__school_data_integration.sql"
echo ""

echo "=========================================="
echo "对比完成"
echo "=========================================="
