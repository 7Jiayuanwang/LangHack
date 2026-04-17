-- ============================================================================
-- Flyway Migration Script: 初始化字段映射配置数据
-- Version: V12
-- Description: 为7个数据源配置字段映射关系，source_field严格匹配官方API小写字段名
-- Author: System
-- Date: 2026-04-12
-- Updated: 2026-04-15 — 基于官方接口文档统一为小写拼音缩写
-- Reference: docs/SCHOOL_DATA_API_SPEC.md
-- ============================================================================

-- ============================================================================
-- 1. 行政班 (administrative_class) 字段映射 — 接口: /api/xzxx
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'xzbh', 'class_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '行政班编号'),
('administrative_class', 'xzbmc', 'class_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '行政班名称'),
('administrative_class', 'xzbjc', 'class_short_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '行政班简称'),
('administrative_class', 'yxbh', 'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '学院编号'),
('administrative_class', 'yxmc', 'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '学院名称'),
('administrative_class', 'zybh', 'major_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '专业编号'),
('administrative_class', 'zymc', 'major_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '专业名称'),
('administrative_class', 'nj', 'grade', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8, 1, '年级'),
('administrative_class', 'bjlx', 'class_type', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '班级类型'),
('administrative_class', 'bzrgh', 'head_teacher_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '班主任工号'),
('administrative_class', 'bzrxm', 'head_teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '班主任姓名'),
('administrative_class', 'xsrs', 'student_count', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 12, 1, '学生人数'),
('administrative_class', 'nsrs', 'male_count', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 13, 1, '男生人数'),
('administrative_class', 'nsrs_nv', 'female_count', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 14, 1, '女生人数'),
('administrative_class', 'bjzt', 'class_status', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 15, 1, '班级状态'),
('administrative_class', 'clrq', 'establish_date', 'STRING', 'DATE', 'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 16, 1, '成立日期'),
('administrative_class', 'byrq', 'graduate_date', 'STRING', 'DATE', 'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 17, 1, '毕业日期'),
('administrative_class', 'bz', 'remark', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 18, 1, '备注');

-- ============================================================================
-- 2. 学校课程 (school_course) 字段映射 — 接口: /api/kcxx
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('school_course', 'kcbh', 'course_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程编号'),
('school_course', 'kcmc', 'course_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称'),
('school_course', 'kcywmc', 'course_name_en', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称'),
('school_course', 'kcjc', 'course_short_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '课程简称'),
('school_course', 'kcxz', 'course_nature', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '课程性质'),
('school_course', 'kclb', 'course_category', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '课程类别'),
('school_course', 'xf', 'credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学分'),
('school_course', 'zxs', 'total_hours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 8, 1, '总学时'),
('school_course', 'llxs', 'theory_hours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 9, 1, '理论学时'),
('school_course', 'syxs', 'experiment_hours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 10, 1, '实验学时'),
('school_course', 'sjxs', 'practice_hours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 11, 1, '实践学时'),
('school_course', 'sjxs_pc', 'computer_hours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 12, 1, '上机学时'),
('school_course', 'kkyxbh', 'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 13, 1, '开课学院编号'),
('school_course', 'kkyxmc', 'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 14, 1, '开课学院名称'),
('school_course', 'kkxbbh', 'department_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 15, 1, '开课系部编号'),
('school_course', 'kkxbmc', 'department_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 16, 1, '开课系部名称'),
('school_course', 'kcfzrgh', 'leader_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 17, 1, '课程负责人工号'),
('school_course', 'kcfzrxm', 'leader_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 18, 1, '课程负责人姓名'),
('school_course', 'khfs', 'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 19, 1, '考核方式'),
('school_course', 'sfsyjx', 'is_bilingual', 'STRING', 'STRING', 'BOOLEAN', '{"trueValues":["是","1","true"],"falseValues":["否","0","false"]}', NULL, 0, 20, 1, '是否双语教学'),
('school_course', 'sfwlkc', 'is_online', 'STRING', 'STRING', 'BOOLEAN', '{"trueValues":["是","1","true"],"falseValues":["否","0","false"]}', NULL, 0, 21, 1, '是否网络课程'),
('school_course', 'syzy', 'applicable_majors', 'STRING', 'STRING', 'SPLIT', '{"delimiter":","}', NULL, 0, 22, 1, '适用专业'),
('school_course', 'xxkcbh', 'prerequisite_codes', 'STRING', 'STRING', 'SPLIT', '{"delimiter":","}', NULL, 0, 23, 1, '先修课程编号'),
('school_course', 'xxkcmc', 'prerequisite_names', 'STRING', 'STRING', 'SPLIT', '{"delimiter":","}', NULL, 0, 24, 1, '先修课程名称'),
('school_course', 'kcjj', 'course_intro', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 25, 1, '课程简介'),
('school_course', 'jxmb', 'teaching_objectives', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 26, 1, '教学目标'),
('school_course', 'kczt', 'course_status', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 27, 1, '课程状态');

-- ============================================================================
-- 3. 教学班 (teaching_class) 字段映射 — 接口: /api/bzkcxx
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_class', 'jxbid', 'class_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '教学班号'),
('teaching_class', 'jxbmc', 'class_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2, 1, '教学班名称'),
('teaching_class', 'kcbh', 'course_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '课程编号'),
('teaching_class', 'kcmc', 'course_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '课程名称'),
('teaching_class', 'jgh', 'teacher_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '授课教师工号'),
('teaching_class', 'xm', 'teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '授课教师姓名'),
('teaching_class', 'xn', 'academic_year', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学年'),
('teaching_class', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 8, 1, '学期'),
('teaching_class', 'bjrl', 'capacity', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 9, 1, '班级容量'),
('teaching_class', 'yxrs', 'enrolled_count', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 10, 1, '已选人数'),
('teaching_class', 'kczt', 'class_status', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '课程状态');

-- ============================================================================
-- 4. 课程安排 (course_schedule) 字段映射 — 接口: /api/kcap
-- 注意: 官方API字段名为 skxqj(上课星期几) 和 skjc(上课节次)，非 XQJ/JCS
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_schedule', 'jxbid', 'class_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '教学班号'),
('course_schedule', 'skxqj', 'day_of_week', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '上课星期几 1-7'),
('course_schedule', 'skjc', 'period', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '上课节次'),
('course_schedule', 'zcd', 'week_range', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '周次'),
('course_schedule', 'cdmc', 'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '上课地点'),
('course_schedule', 'xn', 'academic_year', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年'),
('course_schedule', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学期');

-- ============================================================================
-- 5. 选课记录 (course_selection) 字段映射 — 接口: /api/xsxk
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_selection', 'xh', 'student_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('course_selection', 'kcbh', 'course_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程编号'),
('course_selection', 'kcmc', 'course_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '课程名称'),
('course_selection', 'jhbh', 'plan_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '计划编号'),
('course_selection', 'xn', 'academic_year', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '学年'),
('course_selection', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 6, 1, '学期'),
('course_selection', 'xf', 'credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学分'),
('course_selection', 'cjj', 'total_grade', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 8, 1, '成绩'),
('course_selection', 'kcxz', 'course_nature', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '课程性质'),
('course_selection', 'jxbid', 'class_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '教学班号'),
('course_selection', 'jgh', 'teacher_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '教师工号'),
('course_selection', 'xm', 'teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 12, 1, '教师姓名');

-- ============================================================================
-- 6. 等级考试成绩 (level_exam_scores) 字段映射 — 接口: /api/djcj
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', 'xh', 'student_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('level_exam_scores', 'xm', 'student_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '姓名'),
('level_exam_scores', 'ksbh', 'exam_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '考试编号'),
('level_exam_scores', 'ksmc', 'exam_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '考试名称'),
('level_exam_scores', 'ksrq', 'exam_date', 'STRING', 'DATE', 'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 1, 5, 1, '考试日期'),
('level_exam_scores', 'cj', 'score', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 6, 1, '成绩'),
('level_exam_scores', 'dj', 'grade', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '等级'),
('level_exam_scores', 'zkzh', 'admission_ticket_no', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '准考证号'),
('level_exam_scores', 'bz', 'remark', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '备注');

-- ============================================================================
-- 7. 教学计划 (teaching_plans) 字段映射 — 接口: /api/jxjh
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_plans', 'jhbh', 'plan_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '计划编号'),
('teaching_plans', 'jhmc', 'plan_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '计划名称'),
('teaching_plans', 'zybh', 'major_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '专业代码'),
('teaching_plans', 'zymc', 'major_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '专业名称'),
('teaching_plans', 'nj', 'grade', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '年级'),
('teaching_plans', 'xz', 'schooling_length', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 6, 1, '学制'),
('teaching_plans', 'xwlx', 'degree_type', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学位类型'),
('teaching_plans', 'pycc', 'education_level', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8, 1, '培养层次'),
('teaching_plans', 'yxbh', 'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 9, 1, '学院代码'),
('teaching_plans', 'yxmc', 'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 10, 1, '学院名称'),
('teaching_plans', 'zxf', 'total_credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 11, 1, '总学分'),
('teaching_plans', 'bxxf', 'required_credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 12, 1, '必修学分'),
('teaching_plans', 'xxxf', 'elective_credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 13, 1, '选修学分'),
('teaching_plans', 'sjxf', 'practice_credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 14, 1, '实践学分'),
('teaching_plans', 'jhzt', 'plan_status', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 15, 1, '计划状态');
