#!/usr/bin/env bash
# 验证"用课程班号(KCBH)建班绑定师生"方案可行性
# 用法: bash verify_kcbh_binding.sh [教师工号]
# 例如: bash verify_kcbh_binding.sh 20160066
set -e

TEACHER_ID="${1:-20160066}"

MYSQL_PWD=$(docker inspect langhack-mysql-master --format '{{range .Config.Env}}{{println .}}{{end}}' | grep MYSQL_ROOT_PASSWORD | cut -d= -f2)
DB=english_learning
# 统一 collation 避免 JOIN 报错 (utf8mb4_unicode_ci vs 0900_ai_ci)
RUN() { docker exec -i langhack-mysql-master mysql -u root -p"${MYSQL_PWD}" "${DB}" -e "$1"; }

echo "############ 验证课程班号(KCBH)建班方案 ############"
echo "############ 教师工号: ${TEACHER_ID} ############"

echo -e "\n===== A. 两张表的 class_id 格式对比 ====="

echo -e "\n[A1] teaching_class.class_id 样例 + 长度分布"
RUN "SELECT LENGTH(class_id) AS len, COUNT(*) AS cnt, MIN(class_id) AS sample_min, MAX(class_id) AS sample_max
     FROM teaching_class GROUP BY LENGTH(class_id) ORDER BY len;"

echo -e "\n[A2] course_selection.class_id 样例 + 长度分布"
RUN "SELECT LENGTH(class_id) AS len, COUNT(*) AS cnt, MIN(class_id) AS sample_min, MAX(class_id) AS sample_max
     FROM course_selection GROUP BY LENGTH(class_id) ORDER BY len;"

echo -e "\n===== B. 核心验证：两表能否通过 class_id 直接 JOIN 上 ====="

echo -e "\n[B1] teaching_class ⋈ course_selection ON class_id 的交集数量"
RUN "SELECT COUNT(DISTINCT tc.class_id) AS matched_class_ids
     FROM teaching_class tc
     JOIN course_selection cs
       ON cs.class_id COLLATE utf8mb4_unicode_ci = tc.class_id COLLATE utf8mb4_unicode_ci;"

echo -e "\n[B2] teaching_class 里的 class_id 总数（去重）"
RUN "SELECT COUNT(DISTINCT class_id) AS total_tc_class_ids FROM teaching_class;"

echo -e "\n[B3] course_selection 里的 class_id 总数（去重）"
RUN "SELECT COUNT(DISTINCT class_id) AS total_cs_class_ids FROM course_selection;"

echo -e "\n===== C. 该教师维度验证 ====="

echo -e "\n[C1] 该教师当前学期(2026秋)的教学班 class_id"
RUN "SELECT class_id, course_code, course_name, academic_year, semester
     FROM teaching_class
     WHERE teacher_id COLLATE utf8mb4_unicode_ci = '${TEACHER_ID}'
       AND academic_year = '2026' AND semester = 1;"

echo -e "\n[C2] 上述 class_id 在 course_selection 能否找到学生"
RUN "SELECT cs.class_id, COUNT(*) AS student_cnt
     FROM course_selection cs
     WHERE cs.class_id COLLATE utf8mb4_unicode_ci IN (
       SELECT class_id COLLATE utf8mb4_unicode_ci FROM teaching_class
       WHERE teacher_id COLLATE utf8mb4_unicode_ci = '${TEACHER_ID}'
         AND academic_year = '2026' AND semester = 1
     )
     GROUP BY cs.class_id;"

echo -e "\n===== D. 课程班号唯一性验证（一个 class_id 是否唯一对应一教师一课程）====="

echo -e "\n[D1] teaching_class 里是否存在一个 class_id 对应多个教师/课程（若有则不唯一）"
RUN "SELECT class_id, COUNT(DISTINCT teacher_id) AS teacher_cnt, COUNT(DISTINCT course_code) AS course_cnt
     FROM teaching_class
     GROUP BY class_id
     HAVING teacher_cnt > 1 OR course_cnt > 1
     LIMIT 10;"

echo -e "\n[D2] course_selection 里一个 class_id 对应的教师/课程数（抽查10个）"
RUN "SELECT class_id, COUNT(DISTINCT teacher_id) AS teacher_cnt,
            COUNT(DISTINCT course_code) AS course_cnt, COUNT(DISTINCT student_id) AS student_cnt
     FROM course_selection
     GROUP BY class_id LIMIT 10;"

echo -e "\n===== E. 备选关联键验证（若 class_id 对不上，看这些能不能对上）====="

echo -e "\n[E1] course_code+teacher_id+academic_year+semester 四键关联（当前学期）"
RUN "SELECT tc.course_code, tc.teacher_id, tc.academic_year, tc.semester, COUNT(cs.id) AS matched
     FROM teaching_class tc
     LEFT JOIN course_selection cs
       ON cs.course_code COLLATE utf8mb4_unicode_ci = tc.course_code COLLATE utf8mb4_unicode_ci
      AND cs.teacher_id  COLLATE utf8mb4_unicode_ci = tc.teacher_id  COLLATE utf8mb4_unicode_ci
      AND cs.academic_year = tc.academic_year
      AND cs.semester = tc.semester
     WHERE tc.teacher_id COLLATE utf8mb4_unicode_ci = '${TEACHER_ID}'
       AND tc.academic_year = '2026' AND tc.semester = 1
     GROUP BY tc.course_code, tc.teacher_id, tc.academic_year, tc.semester;"

echo -e "\n[E2] course_selection 里该教师所有数据的学年学期分布（确认当前学期数据是否存在）"
RUN "SELECT academic_year, semester, COUNT(*) AS cnt, COUNT(DISTINCT class_id) AS class_id_cnt
     FROM course_selection
     WHERE teacher_id COLLATE utf8mb4_unicode_ci = '${TEACHER_ID}'
     GROUP BY academic_year, semester ORDER BY academic_year DESC, semester DESC;"

echo -e "\n############ 验证完成 ############"
echo "判读指南："
echo " - [B1] matched_class_ids > 0 且接近 [B2]/[B3] → class_id 两表能对上，你的方案成立"
echo " - [B1] = 0 → 两表 class_id 完全对不上，需用备选键(见 E1)"
echo " - [C2] 有数据 → 该教师当前学期能用 class_id 查到学生"
echo " - [C2] 空 → 当前学期选课数据没拉到（数据问题，非方案问题）"
echo " - [D1] 无行 → class_id 唯一，一个班号=一教师一课程，方案键成立"
echo " - [E2] 有 2026/1 的行 → 当前学期选课数据在库里；无 → 根本没拉当前学期"
