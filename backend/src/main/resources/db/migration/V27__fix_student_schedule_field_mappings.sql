-- ══════════════════════════════════════════════════════════════
-- V27: 修复 student_schedule 字段映射 - 支持中文字段名
--
-- 问题: CDSP API 实际返回中文字段名（如"学号"、"选课课号"），
--       但 field_mappings 只配置了英文缩写（xh、xkkh），
--       导致字段映射失败，classId 为 NULL，触发 NOT NULL 约束错误
--
-- 修复: 添加中文字段名映射，同时保留英文缩写以兼容未来可能的格式变化
-- ══════════════════════════════════════════════════════════════

-- 删除旧的 student_schedule 映射
DELETE FROM field_mappings WHERE data_source = 'student_schedule';

-- 插入新的双格式映射（中文 + 英文缩写）
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('student_schedule', '学号',           'studentId',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '学号（中文字段名）'),
('student_schedule', 'xh',            'studentId',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '学号（英文缩写）'),
('student_schedule', '选课课号',       'classId',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '选课课号（作 class_id，中文字段名）'),
('student_schedule', 'xkkh',          'classId',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '选课课号（作 class_id，英文缩写）'),
('student_schedule', '课程班号',       'classId',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '课程班号（作 class_id，中文字段名）'),
('student_schedule', 'jxbid',         'classId',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '教学班号（作 class_id，英文缩写）'),
('student_schedule', '课程编号',       'courseCode',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '课程编号（中文字段名，非必填）'),
('student_schedule', 'kcbh',          'courseCode',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '课程编号（英文缩写，非必填）'),
('student_schedule', '课程名称',       'courseName',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4,  1, '课程名称（中文字段名）'),
('student_schedule', 'kcmc',          'courseName',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4,  1, '课程名称（英文缩写）'),
('student_schedule', '上课教师职工号', 'teacherId',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '上课教师职工号（中文字段名）'),
('student_schedule', 'skjszgh',       'teacherId',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '上课教师职工号（英文缩写）'),
('student_schedule', 'jgh',           'teacherId',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '教师工号（英文缩写）'),
('student_schedule', '教师名称',       'teacherName',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '教师名称（中文字段名）'),
('student_schedule', 'jsmc',          'teacherName',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '教师名称（英文缩写）'),
('student_schedule', 'jsxm',          'teacherName',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '教师姓名（英文缩写）'),
('student_schedule', '星期几',         'dayOfWeek',     'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7,  1, '星期几 1-7（中文字段名）'),
('student_schedule', 'xqj',           'dayOfWeek',     'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7,  1, '星期几 1-7（英文缩写）'),
('student_schedule', 'skxqj',         'dayOfWeek',     'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7,  1, '上课星期几（英文缩写）'),
('student_schedule', '第几节',         'period',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8,  1, '第几节（中文字段名）'),
('student_schedule', 'djj',           'period',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8,  1, '第几节（英文缩写）'),
('student_schedule', 'skjc',          'period',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8,  1, '上课节次（英文缩写）'),
('student_schedule', '周次安排',       'weekRange',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '周次安排（如 "1-9"，中文字段名）'),
('student_schedule', 'zcap',          'weekRange',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '周次安排（如 "1-9"，英文缩写）'),
('student_schedule', 'zcd',           'weekRange',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '周次（英文缩写）'),
('student_schedule', '教室',           'classLocation', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '教室（中文字段名）'),
('student_schedule', 'js',            'classLocation', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '教室（英文缩写）'),
('student_schedule', 'cdmc',          'classLocation', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '上课地点名称（英文缩写）'),
('student_schedule', '学年',           'academicYear',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '学年（CDSP 单年如 "2024"，中文字段名）'),
('student_schedule', 'xn',            'academicYear',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '学年（CDSP 单年如 "2024"，英文缩写）'),
('student_schedule', '学期',           'semester',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 12, 1, '学期中文 "秋"/"春"，service 层转 Integer（中文字段名）'),
('student_schedule', 'xq',            'semester',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 12, 1, '学期中文 "秋"/"春"，service 层转 Integer（英文缩写）');
