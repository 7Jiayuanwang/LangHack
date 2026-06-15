-- DS0104 易辅导系统学生基本信息接口 (2026-06-15)
-- 1) school_student 新增 gender / phone / counselor_name 三列
-- 2) field_mappings seed：xh/xm/yx/zy/nj/bj/xb/lxdh/fdyxm 共 9 条
-- 未来扩展：rxrq(入学日期)/xjzt(学籍状态)/xslbmc(学生类别)/xzlx(学制类型)/szxy(所在学院)/sfbys/sfzx/mqzk

-- DDL：幂等添加三列
DROP PROCEDURE IF EXISTS _v45_add_student_extra_cols;
CREATE PROCEDURE _v45_add_student_extra_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'school_student'
                     AND COLUMN_NAME = 'gender') THEN
        ALTER TABLE school_student
            ADD COLUMN gender VARCHAR(10) NULL COMMENT '性别（男/女）' AFTER major_name;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'school_student'
                     AND COLUMN_NAME = 'phone') THEN
        ALTER TABLE school_student
            ADD COLUMN phone VARCHAR(30) NULL COMMENT '联系电话' AFTER gender;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'school_student'
                     AND COLUMN_NAME = 'counselor_name') THEN
        ALTER TABLE school_student
            ADD COLUMN counselor_name VARCHAR(50) NULL COMMENT '辅导员姓名' AFTER phone;
    END IF;
END;
CALL _v45_add_student_extra_cols();
DROP PROCEDURE IF EXISTS _v45_add_student_extra_cols;

-- field_mappings seed
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, is_required, remark)
VALUES
    ('student_basic_info', 'xh',    'student_id',     'STRING', 'STRING', 'NONE', 1, '学号（主键）'),
    ('student_basic_info', 'xm',    'student_name',   'STRING', 'STRING', 'NONE', 1, '姓名'),
    ('student_basic_info', 'yx',    'college_name',   'STRING', 'STRING', 'NONE', 0, '院系（学院名称）'),
    ('student_basic_info', 'zy',    'major_name',     'STRING', 'STRING', 'NONE', 0, '专业'),
    ('student_basic_info', 'nj',    'grade',          'STRING', 'STRING', 'NONE', 0, '年级'),
    ('student_basic_info', 'bj',    'class_name',     'STRING', 'STRING', 'NONE', 0, '班级'),
    ('student_basic_info', 'xb',    'gender',         'STRING', 'STRING', 'NONE', 0, '性别'),
    ('student_basic_info', 'lxdh',  'phone',          'STRING', 'STRING', 'NONE', 0, '联系电话'),
    ('student_basic_info', 'fdyxm', 'counselor_name', 'STRING', 'STRING', 'NONE', 0, '辅导员姓名');
