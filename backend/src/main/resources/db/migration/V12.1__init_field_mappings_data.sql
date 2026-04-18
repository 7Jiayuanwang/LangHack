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
('administrative_class', 'xzbh', 'classCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '行政班编号'),
('administrative_class', 'xzbmc', 'className', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '行政班名称'),
('administrative_class', 'xzbjc', 'classShortName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '行政班简称'),
('administrative_class', 'yxbh', 'collegeCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '学院编号'),
('administrative_class', 'yxmc', 'collegeName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '学院名称'),
('administrative_class', 'zybh', 'majorCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '专业编号'),
('administrative_class', 'zymc', 'majorName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '专业名称'),
('administrative_class', 'nj', 'grade', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8, 1, '年级'),
('administrative_class', 'bjlx', 'classType', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '班级类型'),
('administrative_class', 'bzrgh', 'headTeacherCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '班主任工号'),
('administrative_class', 'bzrxm', 'headTeacherName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '班主任姓名'),
('administrative_class', 'xsrs', 'studentCount', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 12, 1, '学生人数'),
('administrative_class', 'nsrs', 'maleCount', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 13, 1, '男生人数'),
('administrative_class', 'nsrs_nv', 'femaleCount', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 14, 1, '女生人数'),
('administrative_class', 'bjzt', 'classStatus', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 15, 1, '班级状态'),
('administrative_class', 'clrq', 'establishDate', 'STRING', 'DATE', 'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 16, 1, '成立日期'),
('administrative_class', 'byrq', 'graduateDate', 'STRING', 'DATE', 'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 17, 1, '毕业日期'),
('administrative_class', 'bz', 'remark', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 18, 1, '备注');

-- ============================================================================
-- 2. 学校课程 (school_course) 字段映射 — 接口: /api/kcxx
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('school_course', 'kcbh', 'courseCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程编号'),
('school_course', 'kcmc', 'courseName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称'),
('school_course', 'kcywmc', 'courseNameEn', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称'),
('school_course', 'kcjc', 'courseShortName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '课程简称'),
('school_course', 'kcxz', 'courseNature', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '课程性质'),
('school_course', 'kclb', 'courseCategory', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '课程类别'),
('school_course', 'xf', 'credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学分'),
('school_course', 'zxs', 'totalHours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 8, 1, '总学时'),
('school_course', 'llxs', 'theoryHours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 9, 1, '理论学时'),
('school_course', 'syxs', 'experimentHours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 10, 1, '实验学时'),
('school_course', 'sjxs', 'practiceHours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 11, 1, '实践学时'),
('school_course', 'sjxs_pc', 'computerHours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 12, 1, '上机学时'),
('school_course', 'kkyxbh', 'collegeCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 13, 1, '开课学院编号'),
('school_course', 'kkyxmc', 'collegeName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 14, 1, '开课学院名称'),
('school_course', 'kkxbbh', 'departmentCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 15, 1, '开课系部编号'),
('school_course', 'kkxbmc', 'departmentName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 16, 1, '开课系部名称'),
('school_course', 'kcfzrgh', 'leaderCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 17, 1, '课程负责人工号'),
('school_course', 'kcfzrxm', 'leaderName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 18, 1, '课程负责人姓名'),
('school_course', 'khfs', 'assessmentMethod', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 19, 1, '考核方式'),
('school_course', 'sfsyjx', 'isBilingual', 'STRING', 'STRING', 'BOOLEAN', '{"trueValues":["是","1","true"],"falseValues":["否","0","false"]}', NULL, 0, 20, 1, '是否双语教学'),
('school_course', 'sfwlkc', 'isOnline', 'STRING', 'STRING', 'BOOLEAN', '{"trueValues":["是","1","true"],"falseValues":["否","0","false"]}', NULL, 0, 21, 1, '是否网络课程'),
('school_course', 'syzy', 'applicableMajors', 'STRING', 'STRING', 'SPLIT', '{"delimiter":","}', NULL, 0, 22, 1, '适用专业'),
('school_course', 'xxkcbh', 'prerequisiteCodes', 'STRING', 'STRING', 'SPLIT', '{"delimiter":","}', NULL, 0, 23, 1, '先修课程编号'),
('school_course', 'xxkcmc', 'prerequisiteNames', 'STRING', 'STRING', 'SPLIT', '{"delimiter":","}', NULL, 0, 24, 1, '先修课程名称'),
('school_course', 'kcjj', 'courseIntro', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 25, 1, '课程简介'),
('school_course', 'jxmb', 'teachingObjectives', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 26, 1, '教学目标'),
('school_course', 'kczt', 'courseStatus', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 27, 1, '课程状态');

-- ============================================================================
-- 3. 教学班 (teaching_class) 字段映射 — 接口: /api/bzkcxx
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_class', 'jxbid', 'classId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '教学班号'),
('teaching_class', 'jxbmc', 'className', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2, 1, '教学班名称'),
('teaching_class', 'kcbh', 'courseCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '课程编号'),
('teaching_class', 'kcmc', 'courseName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '课程名称'),
('teaching_class', 'jgh', 'teacherId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '授课教师工号'),
('teaching_class', 'xm', 'teacherName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '授课教师姓名'),
('teaching_class', 'xn', 'academicYear', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学年'),
('teaching_class', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 8, 1, '学期'),
('teaching_class', 'bjrl', 'capacity', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 9, 1, '班级容量'),
('teaching_class', 'yxrs', 'enrolledCount', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 10, 1, '已选人数'),
('teaching_class', 'kczt', 'classStatus', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '课程状态');

-- ============================================================================
-- 4. 课程安排 (course_schedule) 字段映射 — 接口: /api/kcap
-- 注意: 官方API字段名为 skxqj(上课星期几) 和 skjc(上课节次)，非 XQJ/JCS
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_schedule', 'jxbid', 'classId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '教学班号'),
('course_schedule', 'skxqj', 'dayOfWeek', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '上课星期几 1-7'),
('course_schedule', 'skjc', 'period', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '上课节次'),
('course_schedule', 'zcd', 'weekRange', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '周次'),
('course_schedule', 'cdmc', 'classLocation', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '上课地点'),
('course_schedule', 'xn', 'academicYear', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年'),
('course_schedule', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学期');

-- ============================================================================
-- 5. 选课记录 (course_selection) 字段映射 — 接口: /api/xsxk
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_selection', 'xh', 'studentId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('course_selection', 'kcbh', 'courseCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程编号'),
('course_selection', 'kcmc', 'courseName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '课程名称'),
('course_selection', 'jhbh', 'planId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '计划编号'),
('course_selection', 'xn', 'academicYear', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '学年'),
('course_selection', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 6, 1, '学期'),
('course_selection', 'xf', 'credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学分'),
('course_selection', 'cjj', 'totalGrade', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 8, 1, '成绩'),
('course_selection', 'kcxz', 'courseNature', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '课程性质'),
('course_selection', 'jxbid', 'classId', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '教学班号'),
('course_selection', 'jgh', 'teacherId', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '教师工号'),
('course_selection', 'xm', 'teacherName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 12, 1, '教师姓名');

-- ============================================================================
-- 6. 等级考试成绩 (level_exam_scores) 字段映射 — 接口: /api/djcj
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', 'xh', 'studentId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('level_exam_scores', 'xm', 'studentName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '姓名'),
('level_exam_scores', 'ksbh', 'examId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '考试编号'),
('level_exam_scores', 'ksmc', 'examName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '考试名称'),
('level_exam_scores', 'ksrq', 'examDate', 'STRING', 'DATE', 'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 1, 5, 1, '考试日期'),
('level_exam_scores', 'cj', 'score', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 6, 1, '成绩'),
('level_exam_scores', 'dj', 'grade', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '等级'),
('level_exam_scores', 'zkzh', 'admissionTicketNo', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '准考证号'),
('level_exam_scores', 'bz', 'remark', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '备注');

-- ============================================================================
-- 7. 教学计划 (teaching_plans) 字段映射 — 接口: /api/jxjh
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_plans', 'jhbh', 'planId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '计划编号'),
('teaching_plans', 'jhmc', 'planName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '计划名称'),
('teaching_plans', 'zybh', 'majorCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '专业代码'),
('teaching_plans', 'zymc', 'majorName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '专业名称'),
('teaching_plans', 'nj', 'grade', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '年级'),
('teaching_plans', 'xz', 'schoolingLength', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 6, 1, '学制'),
('teaching_plans', 'xwlx', 'degreeType', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学位类型'),
('teaching_plans', 'pycc', 'educationLevel', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8, 1, '培养层次'),
('teaching_plans', 'yxbh', 'collegeCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 9, 1, '学院代码'),
('teaching_plans', 'yxmc', 'collegeName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 10, 1, '学院名称'),
('teaching_plans', 'zxf', 'totalCredits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 11, 1, '总学分'),
('teaching_plans', 'bxxf', 'requiredCredits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 12, 1, '必修学分'),
('teaching_plans', 'xxxf', 'electiveCredits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 13, 1, '选修学分'),
('teaching_plans', 'sjxf', 'practiceCredits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 14, 1, '实践学分'),
('teaching_plans', 'jhzt', 'planStatus', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 15, 1, '计划状态');
