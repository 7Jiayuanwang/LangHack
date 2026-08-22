#!/usr/bin/env bash
# 教师同步班级诊断脚本
# 用法: bash diag_class_sync.sh <教师工号>
# 例如: bash diag_class_sync.sh 20101001
set -e

TEACHER_ID="${1:-}"
if [ -z "$TEACHER_ID" ]; then
  echo "用法: bash diag_class_sync.sh <教师工号>"
  echo "教师工号 = 教师登录用户名 (users.username)"
  exit 1
fi

MYSQL_PWD=$(docker inspect langhack-mysql-master --format '{{range .Config.Env}}{{println .}}{{end}}' | grep MYSQL_ROOT_PASSWORD | cut -d= -f2)
DB=english_learning
RUN() { docker exec -i langhack-mysql-master mysql -u root -p"${MYSQL_PWD}" "${DB}" -e "$1"; }

echo "========== 教师工号: ${TEACHER_ID} =========="

echo -e "\n【1】该教师在 teaching_class 里的教学班（含学年学期）"
RUN "SELECT class_id, course_code, course_name, teacher_id, teacher_name, academic_year, semester
     FROM teaching_class WHERE teacher_id = '${TEACHER_ID}';"

echo -e "\n【2】teaching_class 里该教师各学年学期的教学班数量"
RUN "SELECT academic_year, semester, COUNT(*) AS cnt
     FROM teaching_class WHERE teacher_id = '${TEACHER_ID}'
     GROUP BY academic_year, semester ORDER BY academic_year DESC, semester DESC;"

echo -e "\n【3】course_selection 里该教师的选课记录数量（按学年学期）"
RUN "SELECT academic_year, semester, COUNT(*) AS cnt
     FROM course_selection WHERE teacher_id = '${TEACHER_ID}'
     GROUP BY academic_year, semester ORDER BY academic_year DESC, semester DESC;"

echo -e "\n【4】course_selection 里该教师选课记录样例（前5条）"
RUN "SELECT student_id, student_name, course_code, teacher_id, academic_year, semester
     FROM course_selection WHERE teacher_id = '${TEACHER_ID}' LIMIT 5;"

echo -e "\n【5】用 course_code+teacher_id 关联（不加学年学期）能否匹配到选课记录"
RUN "SELECT tc.course_code, tc.academic_year AS tc_year, tc.semester AS tc_sem,
            COUNT(cs.id) AS matched_selections
     FROM teaching_class tc
     LEFT JOIN course_selection cs
       ON cs.course_code = tc.course_code AND cs.teacher_id = tc.teacher_id
     WHERE tc.teacher_id = '${TEACHER_ID}'
     GROUP BY tc.course_code, tc.academic_year, tc.semester;"

echo -e "\n【6】学号在 users 表能否匹配（取该教师选课的学号 join users）"
RUN "SELECT COUNT(DISTINCT cs.student_id) AS total_students,
            COUNT(DISTINCT u.id) AS matched_users
     FROM course_selection cs
     LEFT JOIN users u ON u.username = cs.student_id AND u.deleted = 0
     WHERE cs.teacher_id = '${TEACHER_ID}';"

echo -e "\n【7】系统当前算出的学期（对比【2】【3】看是否一致）"
RUN "SELECT
       CASE WHEN MONTH(NOW()) >= 8 THEN YEAR(NOW())
            WHEN MONTH(NOW()) >= 2 THEN YEAR(NOW())-1
            ELSE YEAR(NOW())-1 END AS calc_academic_year,
       CASE WHEN MONTH(NOW()) >= 8 THEN 1
            WHEN MONTH(NOW()) >= 2 THEN 2
            ELSE 1 END AS calc_semester;"

echo -e "\n========== 诊断完成 =========="
echo "重点看："
echo " - 【2】和【3】的学年学期是否与【7】算出的一致？不一致 → 同步查不到（这是最可能的原因）"
echo " - 【5】matched_selections 是否 > 0？为 0 → course_code+teacher_id 关联不上"
echo " - 【6】matched_users 是否接近 total_students？相差大 → 学生没在平台注册/学号格式不符"
