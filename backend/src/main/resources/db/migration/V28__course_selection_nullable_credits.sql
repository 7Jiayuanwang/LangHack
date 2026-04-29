-- V28: 修改 course_selection.credits 为可为 NULL
-- 原因: CDSP API 不返回 credits 字段，导致插入时 NULL 约束违规

ALTER TABLE course_selection
MODIFY COLUMN credits DECIMAL(3,1) COMMENT '学分（CDSP API不返回，可为空）';
