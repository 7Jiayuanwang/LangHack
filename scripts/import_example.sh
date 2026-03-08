#!/bin/bash
# 批量导入多个班级的脚本示例

echo "=========================================="
echo "批量导入班级和学生"
echo "=========================================="
echo ""

# 配置数据库参数（如果需要修改）
DB_HOST="localhost"
DB_PORT="3306"
DB_USER="root"
DB_PASSWORD="Dao030916"
DB_NAME="english_learning"

# 配置课程和教师ID
COURSE_ID=1
TEACHER_ID=1

# Excel文件路径
EXCEL_FILE="25土管名单新.xls"

# 检查Excel文件是否存在
if [ ! -f "$EXCEL_FILE" ]; then
    echo "错误: Excel文件 '$EXCEL_FILE' 不存在"
    exit 1
fi

echo "第1步: 导入 25土管3班"
echo "----------------------------------------"
python3 scripts/import_class_students.py \
  --excel "$EXCEL_FILE" \
  --class-name "2025级土地资源管理3班" \
  --course-id $COURSE_ID \
  --teacher-id $TEACHER_ID \
  --description "2025级土地资源管理专业3班" \
  --filter-class "25土管3" \
  --db-host $DB_HOST \
  --db-port $DB_PORT \
  --db-user $DB_USER \
  --db-password $DB_PASSWORD \
  --db-name $DB_NAME

if [ $? -ne 0 ]; then
    echo "错误: 导入25土管3班失败"
    exit 1
fi

echo ""
echo "第2步: 导入 25土管4班"
echo "----------------------------------------"
python3 scripts/import_class_students.py \
  --excel "$EXCEL_FILE" \
  --class-name "2025级土地资源管理4班" \
  --course-id $COURSE_ID \
  --teacher-id $TEACHER_ID \
  --description "2025级土地资源管理专业4班" \
  --filter-class "25土管4" \
  --db-host $DB_HOST \
  --db-port $DB_PORT \
  --db-user $DB_USER \
  --db-password $DB_PASSWORD \
  --db-name $DB_NAME

if [ $? -ne 0 ]; then
    echo "错误: 导入25土管4班失败"
    exit 1
fi

echo ""
echo "=========================================="
echo "所有班级导入完成！"
echo "=========================================="
echo ""
echo "验证导入结果："
echo ""
echo "1. 查看创建的班级："
echo "   mysql -u$DB_USER -p$DB_PASSWORD $DB_NAME -e \"SELECT * FROM classes WHERE name LIKE '%土地资源管理%';\""
echo ""
echo "2. 查看班级学生数量："
echo "   mysql -u$DB_USER -p$DB_PASSWORD $DB_NAME -e \"SELECT c.name, COUNT(*) as student_count FROM student_classes sc JOIN classes c ON sc.class_id = c.id WHERE c.name LIKE '%土地资源管理%' GROUP BY c.name;\""
echo ""
echo "3. 查看所有学生："
echo "   mysql -u$DB_USER -p$DB_PASSWORD $DB_NAME -e \"SELECT c.name as class_name, u.username, u.real_name FROM users u JOIN student_classes sc ON u.id = sc.student_id JOIN classes c ON sc.class_id = c.id WHERE c.name LIKE '%土地资源管理%' ORDER BY c.name, u.real_name;\""
