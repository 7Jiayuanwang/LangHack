-- ══════════════════════════════════════════════════════════════
-- V28: 全面修复所有数据源字段映射 - 支持中文字段名
--
-- 背景: CDSP API 文档声称返回英文缩写（xh/xkkh/kcmc），但实际可能返回中文字段名
--       （学号/选课课号/课程名称）。V27 修复了 student_schedule，本脚本预防性
--       为其他 8 个数据源也添加中文映射，避免未来出现相同问题。
--
-- 策略: 保留现有英文缩写映射，追加中文字段名映射（双格式兼容）
-- ══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────────────────────────
-- 1. administrative_class (UPPERCASE 字段)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', '行政班编号',   'classCode',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '行政班编号（中文）'),
('administrative_class', '班级名称',     'className',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '班级名称（中文）'),
('administrative_class', '学生类别',     'classType',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '学生类别（中文）'),
('administrative_class', '所属学院编码', 'collegeCode',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4,  1, '所属学院编码（中文）'),
('administrative_class', '所属学院名称', 'collegeName',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '所属学院名称（中文）'),
('administrative_class', '所属年级',     'grade',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '所属年级（中文）'),
('administrative_class', '班主任工号',   'headTeacherCode',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '班主任工号（中文）'),
('administrative_class', '班主任姓名',   'headTeacherName',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '班主任姓名（中文）'),
('administrative_class', '专业名称',     'majorName',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '专业名称（中文）'),
('administrative_class', '建班年月',     'establishDate',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 10, 1, '建班年月（中文）');

-- ────────────────────────────────────────────────────────────
-- 2. school_course (lowercase 字段)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('school_course', '课程代码',       'courseCode',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程代码（中文）'),
('school_course', '课程名称',       'courseName',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称（中文）'),
('school_course', '课程英文名称',   'courseNameEn',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称（中文）'),
('school_course', '课程学分',       'credits',          'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分（中文）'),
('school_course', '课程学时',       'totalHours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时（中文）'),
('school_course', '课程简介',       'courseIntro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介（中文）'),
('school_course', '考试类型',       'assessmentMethod', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型（中文）'),
('school_course', '开课院系',       'collegeName',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系（中文）');

-- ────────────────────────────────────────────────────────────
-- 3. teaching_class (UPPERCASE 字段)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_class', '课程号',         'courseCode',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程号（中文）'),
('teaching_class', '课程名称',       'courseName',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称（中文）'),
('teaching_class', '课程英文名称',   'courseNameEn',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称（中文）'),
('teaching_class', '课程学分',       'credits',          'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分（中文）'),
('teaching_class', '课程学时',       'totalHours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时（中文）'),
('teaching_class', '课程简介',       'courseIntro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介（中文）'),
('teaching_class', '考试类型',       'assessmentMethod', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型（中文）'),
('teaching_class', '开课院系',       'collegeName',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系（中文）');

-- ────────────────────────────────────────────────────────────
-- 4. course_schedule (lowercase 字段)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_schedule', '选课课号',     'classId',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '选课课号（中文）'),
('course_schedule', '上课星期几',   'dayOfWeek',     'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '上课星期几（中文）'),
('course_schedule', '星期几',       'dayOfWeek',     'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '星期几（中文）'),
('course_schedule', '上课节次',     'period',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '上课节次（中文）'),
('course_schedule', '第几节',       'period',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '第几节（中文）'),
('course_schedule', '课程周次',     'weekRange',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '课程周次（中文）'),
('course_schedule', '周次安排',     'weekRange',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '周次安排（中文）'),
('course_schedule', '上课地点',     'classLocation', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '上课地点（中文）'),
('course_schedule', '教室',         'classLocation', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '教室（中文）'),
('course_schedule', '学年',         'academicYear',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年（中文）'),
('course_schedule', '学期',         'semester',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学期（中文）');

-- ────────────────────────────────────────────────────────────
-- 5. course_selection (UPPERCASE 字段)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_selection', '学号',           'studentId',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号（中文）'),
('course_selection', '课程班号',       'classId',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程班号（中文）'),
('course_selection', '考试性质代码',   'selectionStatus', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '考试性质代码（中文）'),
('course_selection', '考试性质名称',   'classTime',       'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '考试性质名称（中文）');

-- ────────────────────────────────────────────────────────────
-- 6. level_exam_scores (lowercase 字段)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', '学号',       'studentId',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号（中文）'),
('level_exam_scores', '姓名',       'studentName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2, 1, '姓名（中文）'),
('level_exam_scores', '考试科目',   'examName',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '考试科目（中文）'),
('level_exam_scores', '考试日期',   'examDate',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 1, 4, 1, '考试日期（中文）'),
('level_exam_scores', '考试成绩',   'score',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 5, 1, '考试成绩（中文）');

-- ────────────────────────────────────────────────────────────
-- 7. teaching_plans (lowercase 字段)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_plans', '开课计划ID',       'planId',           'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '开课计划ID（中文）'),
('teaching_plans', '课程代码',         'courseCode',       'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2,  1, '课程代码（中文）'),
('teaching_plans', '参考书目',         'trainingObjective','STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '参考书目（中文）'),
('teaching_plans', '总学时',           'totalCredits',     'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4,  1, '总学时（中文）'),
('teaching_plans', '授课教师职工号',   'creator',          'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '授课教师职工号（中文）'),
('teaching_plans', '开课单位代码',     'collegeCode',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '开课单位代码（中文）'),
('teaching_plans', '开课单位名称',     'collegeName',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '开课单位名称（中文）'),
('teaching_plans', '开课学年',         'grade',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '开课学年（中文）'),
('teaching_plans', '开课学期名称',     'planName',         'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '开课学期名称（中文）'),
('teaching_plans', '考试方式代码',     'planStatus',       'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '考试方式代码（中文）'),
('teaching_plans', '考试方式名称',     'version',          'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '考试方式名称（中文）');

-- ────────────────────────────────────────────────────────────
-- 8. teacher_courses (lowercase 字段)
-- ────────────────────────────────────────────────────────────
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teacher_courses', '课程班号',       'classId',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程班号（中文）'),
('teacher_courses', '课程代码',       'courseCode',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程代码（中文）'),
('teacher_courses', '课程名称',       'courseName',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '课程名称（中文）'),
('teacher_courses', '任课教师工号',   'teacherId',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '任课教师工号（中文）'),
('teacher_courses', '任课教师姓名',   'teacherName',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '任课教师姓名（中文）'),
('teacher_courses', '学年',           'academicYear', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年（中文）'),
('teacher_courses', '学期',           'semester',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学期（中文）'),
('teacher_courses', '课程班别名',     'className',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '课程班别名（中文）');
