#!/bin/bash
# 一键数据流诊断脚本 - 自动完成所有检查并生成报告

set -e

MYSQL_CONTAINER="langhack-mysql-master"
MYSQL_USER="root"
MYSQL_PASS="${MYSQL_ROOT_PASSWORD:-root}"
DB_NAME="english_learning"
REPORT_FILE="/tmp/data-flow-diagnosis-$(date +%Y%m%d-%H%M%S).txt"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 输出函数
log_info() { echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$REPORT_FILE"; }
log_success() { echo -e "${GREEN}[✓]${NC} $1" | tee -a "$REPORT_FILE"; }
log_warning() { echo -e "${YELLOW}[!]${NC} $1" | tee -a "$REPORT_FILE"; }
log_error() { echo -e "${RED}[✗]${NC} $1" | tee -a "$REPORT_FILE"; }
log_section() { echo -e "\n${BLUE}========================================${NC}" | tee -a "$REPORT_FILE"; echo -e "${BLUE}$1${NC}" | tee -a "$REPORT_FILE"; echo -e "${BLUE}========================================${NC}" | tee -a "$REPORT_FILE"; }

# 初始化报告
echo "数据流诊断报告 - $(date)" > "$REPORT_FILE"
echo "========================================" >> "$REPORT_FILE"

log_section "开始自动诊断"

# 检查 Docker 容器状态
log_info "检查 Docker 容器状态..."
if ! docker ps | grep -q "$MYSQL_CONTAINER"; then
    log_error "MySQL 容器未运行"
    log_info "尝试启动容器..."
    if [ -f "docker-compose.yml" ]; then
        log_info "执行: docker-compose up -d"
        docker-compose up -d 2>&1 | tee -a "$REPORT_FILE"
        sleep 5
        if docker ps | grep -q "$MYSQL_CONTAINER"; then
            log_success "容器启动成功"
        else
            log_error "容器启动失败，请手动检查"
            log_info "手动启动命令: docker-compose up -d"
            exit 1
        fi
    else
        log_error "未找到 docker-compose.yml，请手动启动容器"
        log_info "手动启动命令: docker-compose up -d"
        exit 1
    fi
else
    log_success "MySQL 容器运行正常"
fi

# 1. 数据库表数据量检查
log_section "1. 数据库表数据量"
TABLE_COUNTS=$(docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -N -e "
SELECT CONCAT(table_name, ':', cnt)
FROM (
    SELECT 'administrative_class' as table_name, COUNT(*) as cnt FROM administrative_class
    UNION ALL SELECT 'school_course', COUNT(*) FROM school_course
    UNION ALL SELECT 'teaching_class', COUNT(*) FROM teaching_class
    UNION ALL SELECT 'course_schedule', COUNT(*) FROM course_schedule
    UNION ALL SELECT 'course_selection', COUNT(*) FROM course_selection
    UNION ALL SELECT 'level_exam_scores', COUNT(*) FROM level_exam_scores
    UNION ALL SELECT 'teaching_plans', COUNT(*) FROM teaching_plans
    UNION ALL SELECT 'student_schedule', COUNT(*) FROM student_schedule
) t;
" 2>/dev/null)

ISSUES_FOUND=0
CRITICAL_ISSUES=0

while IFS=: read -r table count; do
    if [ "$count" -eq 0 ]; then
        log_error "$table: $count 条记录 (表为空)"
        CRITICAL_ISSUES=$((CRITICAL_ISSUES + 1))
    elif [ "$count" -lt 10 ]; then
        log_warning "$table: $count 条记录 (数据较少)"
        ISSUES_FOUND=$((ISSUES_FOUND + 1))
    else
        log_success "$table: $count 条记录"
    fi
done <<< "$TABLE_COUNTS"

# 2. 选课记录详细检查
log_section "2. 选课记录详细检查"
SELECTION_COUNT=$(docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -N -e "SELECT COUNT(*) FROM course_selection;" 2>/dev/null)

if [ "$SELECTION_COUNT" -eq 0 ]; then
    log_error "course_selection 表为空，无法显示数据"
    log_warning "需要执行同步：teacher_courses → course_selection"
    CRITICAL_ISSUES=$((CRITICAL_ISSUES + 1))
else
    log_success "course_selection 表有 $SELECTION_COUNT 条记录"

    log_info "显示前 3 条记录样本："
    docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
    SELECT
        student_id, student_name, course_name, teacher_name,
        credits, total_grade, academic_year, semester
    FROM course_selection
    ORDER BY sync_time DESC
    LIMIT 3;
    " 2>/dev/null | tee -a "$REPORT_FILE"
fi

# 3. 外键约束检查
log_section "3. 外键约束完整性检查"

log_info "检查 course_selection 中孤立的 class_id..."
ORPHANED_CLASS_IDS=$(docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -N -e "
SELECT COUNT(DISTINCT cs.class_id)
FROM course_selection cs
LEFT JOIN teaching_class tc ON cs.class_id = tc.class_id
WHERE tc.class_id IS NULL;
" 2>/dev/null)

if [ "$ORPHANED_CLASS_IDS" -gt 0 ]; then
    log_error "发现 $ORPHANED_CLASS_IDS 个孤立的 class_id (teaching_class 表中不存在)"
    log_warning "需要先同步 teacher_courses 填充 teaching_class 表"
    CRITICAL_ISSUES=$((CRITICAL_ISSUES + 1))
else
    log_success "所有 class_id 都在 teaching_class 表中存在"
fi

log_info "检查 course_selection 中孤立的 course_code..."
ORPHANED_COURSE_CODES=$(docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -N -e "
SELECT COUNT(DISTINCT cs.course_code)
FROM course_selection cs
LEFT JOIN school_course sc ON cs.course_code = sc.course_code
WHERE sc.course_code IS NULL;
" 2>/dev/null)

if [ "$ORPHANED_COURSE_CODES" -gt 0 ]; then
    log_error "发现 $ORPHANED_COURSE_CODES 个孤立的 course_code (school_course 表中不存在)"
    log_warning "需要先同步 school_course 表"
    CRITICAL_ISSUES=$((CRITICAL_ISSUES + 1))
else
    log_success "所有 course_code 都在 school_course 表中存在"
fi

# 4. 字段映射检查
log_section "4. 字段映射配置检查"
MAPPING_COUNT=$(docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -N -e "
SELECT COUNT(*) FROM field_mappings WHERE data_source = 'course_selection';
" 2>/dev/null)

if [ "$MAPPING_COUNT" -lt 10 ]; then
    log_error "course_selection 字段映射不完整 (只有 $MAPPING_COUNT 条)"
    log_warning "需要检查 field_mappings 表配置"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
else
    log_success "course_selection 有 $MAPPING_COUNT 条字段映射"
fi

log_info "显示字段映射配置："
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT source_field, target_field, converter, remark
FROM field_mappings
WHERE data_source = 'course_selection'
ORDER BY source_field;
" 2>/dev/null | tee -a "$REPORT_FILE"

# 5. 同步日志检查
log_section "5. 同步日志检查"
log_info "检查最近的同步记录..."
docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "
SELECT
    data_source, sync_type, status,
    total_count, success_count, fail_count,
    DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') as sync_time
FROM sync_logs
WHERE data_source IN ('teacher_courses', 'course_selection', 'school_course')
ORDER BY created_at DESC
LIMIT 10;
" 2>/dev/null | tee -a "$REPORT_FILE"

# 检查是否有失败的同步
FAILED_SYNCS=$(docker exec $MYSQL_CONTAINER mysql -u$MYSQL_USER -p$MYSQL_PASS $DB_NAME -N -e "
SELECT COUNT(*) FROM sync_logs
WHERE data_source IN ('teacher_courses', 'course_selection')
AND status = 'FAILED'
AND created_at > DATE_SUB(NOW(), INTERVAL 1 DAY);
" 2>/dev/null)

if [ "$FAILED_SYNCS" -gt 0 ]; then
    log_error "最近 24 小时内有 $FAILED_SYNCS 次同步失败"
    ISSUES_FOUND=$((ISSUES_FOUND + 1))
else
    log_success "最近 24 小时内没有同步失败记录"
fi

# 6. 前端字段绑定检查
log_section "6. 前端字段绑定检查"
log_info "检查学生端选课记录页面字段绑定..."

FRONTEND_FIELDS=$(grep -o "item\.[a-zA-Z]*" frontend/src/views/student/CourseSelections.vue 2>/dev/null | sort -u | sed 's/item\.//')
if [ -n "$FRONTEND_FIELDS" ]; then
    log_success "前端使用的字段："
    echo "$FRONTEND_FIELDS" | while read -r field; do
        echo "  - $field" | tee -a "$REPORT_FILE"
    done
else
    log_warning "无法读取前端字段绑定"
fi

# 7. Entity 字段检查
log_section "7. Entity 字段定义检查"
log_info "检查 CourseSelection Entity 字段..."
ENTITY_FIELDS=$(grep "private.*;" backend/src/main/java/com/english/entity/CourseSelection.java 2>/dev/null | grep -v "//" | awk '{print $3}' | sed 's/;//' | head -20)
if [ -n "$ENTITY_FIELDS" ]; then
    log_success "Entity 定义的字段："
    echo "$ENTITY_FIELDS" | while read -r field; do
        echo "  - $field" | tee -a "$REPORT_FILE"
    done
else
    log_warning "无法读取 Entity 字段定义"
fi

# 8. 生成诊断总结
log_section "诊断总结"

if [ "$CRITICAL_ISSUES" -gt 0 ]; then
    log_error "发现 $CRITICAL_ISSUES 个严重问题，需要立即修复"
elif [ "$ISSUES_FOUND" -gt 0 ]; then
    log_warning "发现 $ISSUES_FOUND 个警告，建议检查"
else
    log_success "所有检查通过，数据流正常"
fi

# 9. 生成修复建议
log_section "修复建议"

if [ "$SELECTION_COUNT" -eq 0 ]; then
    echo "" | tee -a "$REPORT_FILE"
    log_error "问题：course_selection 表为空"
    log_info "解决方案："
    echo "  1. 运维端 → 学校数据同步" | tee -a "$REPORT_FILE"
    echo "  2. 按顺序同步：school_course → teacher_courses → course_selection" | tee -a "$REPORT_FILE"
    echo "  3. 使用全量同步模式" | tee -a "$REPORT_FILE"
fi

if [ "$ORPHANED_CLASS_IDS" -gt 0 ]; then
    echo "" | tee -a "$REPORT_FILE"
    log_error "问题：course_selection 中有孤立的 class_id"
    log_info "解决方案："
    echo "  1. 先同步 teacher_courses (填充 teaching_class 表)" | tee -a "$REPORT_FILE"
    echo "  2. 再重新同步 course_selection" | tee -a "$REPORT_FILE"
fi

if [ "$ORPHANED_COURSE_CODES" -gt 0 ]; then
    echo "" | tee -a "$REPORT_FILE"
    log_error "问题：course_selection 中有孤立的 course_code"
    log_info "解决方案："
    echo "  1. 先同步 school_course 表" | tee -a "$REPORT_FILE"
    echo "  2. 再重新同步 course_selection" | tee -a "$REPORT_FILE"
fi

# 10. 字段名对照表
log_section "字段名对照表"
cat <<'EOF' | tee -a "$REPORT_FILE"
数据库列名 (snake_case) → Entity/JSON (camelCase) → 前端绑定
--------------------------------------------------------------
student_id              → studentId              → item.studentId
student_name            → studentName            → item.studentName
course_code             → courseCode             → item.courseCode
course_name             → courseName             → item.courseName
class_id                → classId                → item.classId
teacher_name            → teacherName            → item.teacherName
credits                 → credits                → item.credits
class_time              → classTime              → item.classTime
class_location          → classLocation          → item.classLocation
total_grade             → totalGrade             → item.totalGrade

规则：
- 数据库使用 snake_case
- Java/JSON/前端统一使用 camelCase
- MyBatis Plus 自动转换
EOF

# 完成
log_section "诊断完成"
log_info "完整报告已保存到: $REPORT_FILE"
echo ""
log_info "快速查看报告: cat $REPORT_FILE"
log_info "如需帮助，请查看: docs/DATA_FLOW_TROUBLESHOOTING.md"

exit 0
