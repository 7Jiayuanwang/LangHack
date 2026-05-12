#!/bin/bash
# 前端字段绑定检查脚本

echo "=========================================="
echo "前端字段绑定检查"
echo "=========================================="
echo ""

echo "1. 检查学生端选课记录页面字段绑定"
echo "----------------------------------------"
grep -n "item\." frontend/src/views/student/CourseSelections.vue | grep -v "//\|<!--" | head -20
echo ""

echo "2. 检查运维端学校数据浏览页面字段绑定"
echo "----------------------------------------"
echo "2.1 选课记录表格列定义："
grep -A 10 "el-tab-pane.*course_selection" frontend/src/views/ops/SchoolDataBrowser.vue | grep "el-table-column"
echo ""

echo "3. 检查后端 Entity 字段定义"
echo "----------------------------------------"
echo "3.1 CourseSelection Entity 字段："
grep "@TableField\|private.*;" backend/src/main/java/com/english/entity/CourseSelection.java | head -30
echo ""

echo "4. 字段名对照表"
echo "----------------------------------------"
cat <<'EOF'
| 数据库列名 (snake_case) | Entity 字段 (camelCase) | JSON 字段 (camelCase) | 前端绑定 |
|------------------------|------------------------|----------------------|---------|
| student_id             | studentId              | studentId            | item.studentId ✓ |
| student_name           | studentName            | studentName          | item.studentName ✓ |
| course_code            | courseCode             | courseCode           | item.courseCode ✓ |
| course_name            | courseName             | courseName           | item.courseName ✓ |
| class_id               | classId                | classId              | item.classId ✓ |
| teacher_id             | teacherId              | teacherId            | item.teacherId ✓ |
| teacher_name           | teacherName            | teacherName          | item.teacherName ✓ |
| credits                | credits                | credits              | item.credits ✓ |
| academic_year          | academicYear           | academicYear         | - |
| semester               | semester               | semester             | - |
| class_time             | classTime              | classTime            | item.classTime ✓ |
| class_location         | classLocation          | classLocation        | item.classLocation ✓ |
| selection_time         | selectionTime          | selectionTime        | - |
| selection_status       | selectionStatus        | selectionStatus      | - |
| regular_grade          | regularGrade           | regularGrade         | - |
| final_grade            | finalGrade             | finalGrade           | - |
| total_grade            | totalGrade             | totalGrade           | item.totalGrade ✓ |
| grade_point            | gradePoint             | gradePoint           | - |

说明：
- MyBatis Plus 自动处理 snake_case ↔ camelCase 转换
- Jackson 序列化使用 Java 字段名（camelCase）
- 前端应使用 camelCase 访问字段
EOF
echo ""

echo "5. 常见问题"
echo "----------------------------------------"
cat <<'EOF'
问题1：前端显示 undefined 或空白
原因：字段名不匹配（如前端用 student_id，后端返回 studentId）
解决：统一使用 camelCase

问题2：数据库有数据但前端看不到
原因：
  a) 后端查询条件不匹配（如 academicYear/semester 参数错误）
  b) 外键约束导致数据被跳过（teaching_class 不存在）
  c) 前端 API 调用失败（网络错误、认证失败）
解决：
  a) 检查查询参数格式（学年=单年如"2026"，学期=1或2）
  b) 运行 diagnose-data-flow.sh 检查外键问题
  c) 浏览器 DevTools → Network 查看 API 响应

问题3：部分字段显示，部分字段不显示
原因：字段映射不完整或字段值为 NULL
解决：检查 field_mappings 表配置
EOF
echo ""

echo "=========================================="
echo "检查完成"
echo "=========================================="
