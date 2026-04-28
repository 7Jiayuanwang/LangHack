-- V27: 修改 course_selection.course_name 为可为 NULL
-- 原因: CDSP API 不返回 course_name 字段，导致插入时 NULL 约束违规

ALTER TABLE course_selection
MODIFY COLUMN course_name VARCHAR(200) COMMENT '课程名称（CDSP API不返回，可为空）';
