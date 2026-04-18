-- ═══════════════════════════════════════════════════════════════
-- V19: 学校数据字段映射规则（基于官方API文档）
-- ═══════════════════════════════════════════════════════════════
-- 说明：将学校API返回的拼音缩写字段映射到数据库的英文全称字段
-- 参考文档：E:\项目\兴趣小组\智能与安全社团\服务器使用\数据资源\接口文档\

-- 学生课表字段映射（/api/xskb）
INSERT INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`)
VALUES
-- 基础信息
('student_schedule', 'zcdm', 'week_code', 'NONE', NULL, '周次代码'),
('student_schedule', 'skjszgh', 'teacher_id', 'NONE', NULL, '上课教师职工号'),
('student_schedule', 'jsmc', 'teacher_name', 'NONE', NULL, '教师名称'),
('student_schedule', 'lsxykh', 'teacher_school_id', 'NONE', NULL, '老师校园卡号'),
('student_schedule', 'kcmc', 'course_name', 'NONE', NULL, '课程名称'),
('student_schedule', 'js', 'classroom', 'NONE', NULL, '教室'),
('student_schedule', 'skcd', 'duration', 'NONE', NULL, '上课长度（节数）'),
('student_schedule', 'djj', 'period', 'NONE', NULL, '第几节'),
('student_schedule', 'xqj', 'day_of_week', 'NONE', NULL, '星期几（1-7）'),
('student_schedule', 'xkkh', 'course_selection_code', 'NONE', NULL, '选课课号'),
('student_schedule', 'xh', 'student_id', 'NONE', NULL, '学号'),
('student_schedule', 'xq', 'semester', 'NONE', NULL, '学期（1/2）'),
('student_schedule', 'xn', 'academic_year', 'NONE', NULL, '学年（如2024）'),
('student_schedule', 'zcap', 'week_schedule', 'NONE', NULL, '周次安排（如"1-9"）'),
('student_schedule', 'kcbh', 'class_code', 'NONE', NULL, '课程班号');

-- 行政班基本信息字段映射（/api/xzxx）
INSERT INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`)
VALUES
('administrative_class', 'xzbh', 'class_code', 'NONE', NULL, '行政班编号'),
('administrative_class', 'xzmc', 'class_name', 'NONE', NULL, '行政班名称'),
('administrative_class', 'xzbjc', 'class_short_name', 'NONE', NULL, '行政班简称'),
('administrative_class', 'yxbh', 'college_code', 'NONE', NULL, '学院编号'),
('administrative_class', 'yxmc', 'college_name', 'NONE', NULL, '学院名称'),
('administrative_class', 'zybh', 'major_code', 'NONE', NULL, '专业编号'),
('administrative_class', 'zymc', 'major_name', 'NONE', NULL, '专业名称'),
('administrative_class', 'nj', 'grade', 'NONE', NULL, '年级'),
('administrative_class', 'bjlx', 'class_type', 'NONE', NULL, '班级类型'),
('administrative_class', 'bzrgh', 'head_teacher_code', 'NONE', NULL, '班主任工号'),
('administrative_class', 'bzrxm', 'head_teacher_name', 'NONE', NULL, '班主任姓名'),
('administrative_class', 'xsrs', 'student_count', 'NONE', NULL, '学生人数'),
('administrative_class', 'nsrs', 'male_count', 'NONE', NULL, '男生人数'),
('administrative_class', 'nsrs_nv', 'female_count', 'NONE', NULL, '女生人数'),
('administrative_class', 'bjzt', 'class_status', 'NONE', NULL, '班级状态'),
('administrative_class', 'clrq', 'establish_date', 'DATE', NULL, '成立日期'),
('administrative_class', 'byrq', 'graduate_date', 'DATE', NULL, '毕业日期'),
('administrative_class', 'bz', 'remark', 'NONE', NULL, '备注');

-- 课程基本信息字段映射（/api/kcxx）
INSERT INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`)
VALUES
('school_course', 'kcbh', 'course_code', 'NONE', NULL, '课程编号'),
('school_course', 'kcmc', 'course_name', 'NONE', NULL, '课程名称'),
('school_course', 'kcywmc', 'course_name_en', 'NONE', NULL, '课程英文名称'),
('school_course', 'kcjc', 'course_short_name', 'NONE', NULL, '课程简称'),
('school_course', 'kcxz', 'course_nature', 'NONE', NULL, '课程性质'),
('school_course', 'kclb', 'course_category', 'NONE', NULL, '课程类别'),
('school_course', 'xf', 'credits', 'NONE', NULL, '学分'),
('school_course', 'zxs', 'total_hours', 'NONE', NULL, '总学时'),
('school_course', 'llxs', 'theory_hours', 'NONE', NULL, '理论学时'),
('school_course', 'syxs', 'experiment_hours', 'NONE', NULL, '实验学时'),
('school_course', 'sjxs', 'practice_hours', 'NONE', NULL, '实践学时'),
('school_course', 'sjxs_pc', 'computer_hours', 'NONE', NULL, '上机学时'),
('school_course', 'kkyxbh', 'college_code', 'NONE', NULL, '开课学院编号'),
('school_course', 'kkyxmc', 'college_name', 'NONE', NULL, '开课学院名称'),
('school_course', 'kkxbbh', 'department_code', 'NONE', NULL, '开课系部编号'),
('school_course', 'kkxbmc', 'department_name', 'NONE', NULL, '开课系部名称'),
('school_course', 'kcfzrgh', 'leader_code', 'NONE', NULL, '课程负责人工号'),
('school_course', 'kcfzrxm', 'leader_name', 'NONE', NULL, '课程负责人姓名'),
('school_course', 'khfs', 'assessment_method', 'NONE', NULL, '考核方式'),
('school_course', 'sfsyjx', 'is_bilingual', 'BOOLEAN', NULL, '是否双语教学'),
('school_course', 'sfwlkc', 'is_online', 'BOOLEAN', NULL, '是否网络课程'),
('school_course', 'syzy', 'applicable_majors', 'NONE', NULL, '适用专业'),
('school_course', 'xxkcbh', 'prerequisite_codes', 'NONE', NULL, '先修课程编号'),
('school_course', 'xxkcmc', 'prerequisite_names', 'NONE', NULL, '先修课程名称'),
('school_course', 'kcjj', 'course_intro', 'NONE', NULL, '课程简介'),
('school_course', 'jxmb', 'teaching_objectives', 'NONE', NULL, '教学目标'),
('school_course', 'kczt', 'course_status', 'NONE', NULL, '课程状态');

-- 教学班信息字段映射（/api/bzkcxx）
INSERT INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`)
VALUES
('teaching_class', 'jxbid', 'class_id', 'NONE', NULL, '教学班号'),
('teaching_class', 'jxbmc', 'class_name', 'NONE', NULL, '教学班名称'),
('teaching_class', 'kcbh', 'course_code', 'NONE', NULL, '课程编号'),
('teaching_class', 'kcmc', 'course_name', 'NONE', NULL, '课程名称'),
('teaching_class', 'jgh', 'teacher_id', 'NONE', NULL, '授课教师工号'),
('teaching_class', 'xm', 'teacher_name', 'NONE', NULL, '教师姓名'),
('teaching_class', 'xnm', 'academic_year', 'NONE', NULL, '学年'),
('teaching_class', 'xqm', 'semester', 'NONE', NULL, '学期'),
('teaching_class', 'bjrl', 'capacity', 'NONE', NULL, '班级容量'),
('teaching_class', 'yxrs', 'enrolled_count', 'NONE', NULL, '已选人数'),
('teaching_class', 'kczt', 'class_status', 'NONE', NULL, '课程状态');

-- 课程安排字段映射（/api/kcap）
INSERT INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`)
VALUES
('course_schedule', 'jxbid', 'class_id', 'NONE', NULL, '教学班号'),
('course_schedule', 'xqj', 'day_of_week', 'NONE', NULL, '星期几'),
('course_schedule', 'jcs', 'period', 'NONE', NULL, '节次'),
('course_schedule', 'zcd', 'week_range', 'NONE', NULL, '周次'),
('course_schedule', 'cdmc', 'class_location', 'NONE', NULL, '上课地点'),
('course_schedule', 'xnm', 'academic_year', 'NONE', NULL, '学年'),
('course_schedule', 'xqm', 'semester', 'NONE', NULL, '学期');

-- 学生选课字段映射（/api/xsxk）
INSERT INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`)
VALUES
('course_selection', 'xkjlid', 'selection_id', 'NONE', NULL, '选课记录ID'),
('course_selection', 'xh', 'student_id', 'NONE', NULL, '学号'),
('course_selection', 'xm', 'student_name', 'NONE', NULL, '学生姓名'),
('course_selection', 'kcbh', 'course_code', 'NONE', NULL, '课程编号'),
('course_selection', 'kcmc', 'course_name', 'NONE', NULL, '课程名称'),
('course_selection', 'jxbid', 'class_id', 'NONE', NULL, '教学班号'),
('course_selection', 'jgh', 'teacher_id', 'NONE', NULL, '授课教师工号'),
('course_selection', 'jsxm', 'teacher_name', 'NONE', NULL, '授课教师姓名'),
('course_selection', 'xf', 'credits', 'NONE', NULL, '学分'),
('course_selection', 'xnm', 'academic_year', 'NONE', NULL, '学年'),
('course_selection', 'xqm', 'semester', 'NONE', NULL, '学期'),
('course_selection', 'sksj', 'class_time', 'NONE', NULL, '上课时间'),
('course_selection', 'skdd', 'class_location', 'NONE', NULL, '上课地点'),
('course_selection', 'xksj', 'selection_time', 'DATE', NULL, '选课时间'),
('course_selection', 'xkzt', 'selection_status', 'NONE', NULL, '选课状态'),
('course_selection', 'pscj', 'regular_grade', 'NONE', NULL, '平时成绩'),
('course_selection', 'qmcj', 'final_grade', 'NONE', NULL, '期末成绩'),
('course_selection', 'zpcj', 'total_grade', 'NONE', NULL, '总评成绩'),
('course_selection', 'jd', 'grade_point', 'NONE', NULL, '绩点'),
('course_selection', 'cqcs', 'attendance_count', 'NONE', NULL, '出勤次数'),
('course_selection', 'qqcs', 'absence_count', 'NONE', NULL, '缺勤次数'),
('course_selection', 'cjbz', 'grade_remark', 'NONE', NULL, '成绩备注');

-- 等级考试成绩字段映射（/api/djcj）
INSERT INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`)
VALUES
('level_exam_scores', 'xh', 'student_id', 'NONE', NULL, '学号'),
('level_exam_scores', 'xm', 'student_name', 'NONE', NULL, '学生姓名'),
('level_exam_scores', 'kslx', 'exam_type', 'NONE', NULL, '考试类型'),
('level_exam_scores', 'ksmc', 'exam_name', 'NONE', NULL, '考试名称'),
('level_exam_scores', 'zkzh', 'admission_ticket_no', 'NONE', NULL, '准考证号'),
('level_exam_scores', 'kssj', 'exam_date', 'DATE', NULL, '考试时间'),
('level_exam_scores', 'cj', 'score', 'NONE', NULL, '成绩'),
('level_exam_scores', 'dj', 'grade', 'NONE', NULL, '等级'),
('level_exam_scores', 'sftg', 'is_passed', 'BOOLEAN', NULL, '是否通过'),
('level_exam_scores', 'hgfsx', 'passing_score', 'NONE', NULL, '合格分数线'),
('level_exam_scores', 'tlcj', 'listening_score', 'NONE', NULL, '听力成绩'),
('level_exam_scores', 'ydcj', 'reading_score', 'NONE', NULL, '阅读成绩'),
('level_exam_scores', 'xzcj', 'writing_score', 'NONE', NULL, '写作成绩'),
('level_exam_scores', 'fycj', 'translation_score', 'NONE', NULL, '翻译成绩'),
('level_exam_scores', 'zsbh', 'certificate_no', 'NONE', NULL, '证书编号'),
('level_exam_scores', 'fzrq', 'certificate_date', 'DATE', NULL, '发证日期'),
('level_exam_scores', 'ksdd', 'exam_location', 'NONE', NULL, '考试地点'),
('level_exam_scores', 'kch', 'exam_room_no', 'NONE', NULL, '考场号'),
('level_exam_scores', 'zwh', 'seat_no', 'NONE', NULL, '座位号'),
('level_exam_scores', 'bmsj', 'registration_date', 'DATE', NULL, '报名时间'),
('level_exam_scores', 'cjfbsj', 'score_release_date', 'DATE', NULL, '成绩发布时间'),
('level_exam_scores', 'sjly', 'data_source', 'NONE', NULL, '数据来源'),
('level_exam_scores', 'bz', 'remark', 'NONE', NULL, '备注');

-- 教学计划字段映射（/api/jxjh）
INSERT INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`)
VALUES
('teaching_plans', 'jhbh', 'plan_id', 'NONE', NULL, '教学计划编号'),
('teaching_plans', 'jhmc', 'plan_name', 'NONE', NULL, '教学计划名称'),
('teaching_plans', 'zybh', 'major_code', 'NONE', NULL, '专业代码'),
('teaching_plans', 'zymc', 'major_name', 'NONE', NULL, '专业名称'),
('teaching_plans', 'nj', 'grade', 'NONE', NULL, '年级'),
('teaching_plans', 'xz', 'schooling_length', 'NONE', NULL, '学制（年）');

-- 教师授课字段映射（/api/jssk）
INSERT INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`)
VALUES
('teacher_courses', 'jgh', 'teacher_id', 'NONE', NULL, '教师职工号'),
('teacher_courses', 'jsxm', 'teacher_name', 'NONE', NULL, '教师姓名'),
('teacher_courses', 'jxbid', 'class_id', 'NONE', NULL, '教学班号'),
('teacher_courses', 'kcbh', 'course_code', 'NONE', NULL, '课程编号'),
('teacher_courses', 'kcmc', 'course_name', 'NONE', NULL, '课程名称'),
('teacher_courses', 'xnm', 'academic_year', 'NONE', NULL, '学年'),
('teacher_courses', 'xqm', 'semester', 'NONE', NULL, '学期'),
('teacher_courses', 'skrs', 'student_count', 'NONE', NULL, '上课人数');
