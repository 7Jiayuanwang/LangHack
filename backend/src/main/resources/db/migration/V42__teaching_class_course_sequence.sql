-- V42: teaching_class 增加「课序号」，供教师与教务处按 课程号+课序号 核对班级
-- 来源 CDSP teacher_courses 字段：课序号(kxh)，同一教师同一课程下唯一
-- 采用存储过程守卫，列已存在则跳过，兼容手动/重复执行环境

DROP PROCEDURE IF EXISTS add_teaching_class_seq_col;

DELIMITER $$
CREATE PROCEDURE add_teaching_class_seq_col()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'teaching_class'
          AND column_name = 'course_sequence'
    ) THEN
        ALTER TABLE teaching_class
            ADD COLUMN course_sequence VARCHAR(20) NULL COMMENT '课序号(CDSP kxh)，同一教师同一课程下唯一' AFTER class_name;
    END IF;
END$$
DELIMITER ;

CALL add_teaching_class_seq_col();
DROP PROCEDURE IF EXISTS add_teaching_class_seq_col;
