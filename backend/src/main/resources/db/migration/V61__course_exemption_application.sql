-- 大学英语课程免修/免考申请审批（内农大教字〔2025〕18 号）
-- 申请条件：CET4 >= 497；CET6 >= 425；雅思 >= 6（两年内）；托福 >= 68（两年内）
-- 折算规则：四级 = 成绩*100/710+15；六级 = 成绩*100/710+25；雅思/托福按对照表；
--          折算后大于 100 按 100 计。免修不免考者最终成绩按期末卷面成绩计入。
CREATE TABLE IF NOT EXISTS course_exemption_application (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL COMMENT '申请人 users.id',
    course_name VARCHAR(50) NOT NULL COMMENT '申请课程，如 大学英语 2 / 大学英语 3',
    apply_type VARCHAR(20) NOT NULL COMMENT 'EXEMPT_FULL=免修免考；EXEMPT_WITH_EXAM=免修不免考',
    exam_type VARCHAR(10) NOT NULL COMMENT 'CET4 / CET6 / IELTS / TOEFL',
    exam_score VARCHAR(20) NOT NULL COMMENT '原始成绩，雅思可含 0.5 小数',
    exam_date DATE DEFAULT NULL COMMENT '成绩获得时间，雅思/托福须在两年内',
    certificate_url VARCHAR(500) DEFAULT NULL COMMENT '成绩证明材料地址',
    student_remark VARCHAR(500) DEFAULT NULL COMMENT '学生申请说明',
    converted_score INT DEFAULT NULL COMMENT '按细则折算的成绩，四舍五入、上限 100',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    review_comment VARCHAR(500) DEFAULT NULL COMMENT '教师审批意见',
    reviewed_by BIGINT DEFAULT NULL COMMENT '审批教师 users.id',
    reviewed_time DATETIME DEFAULT NULL COMMENT '审批时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_cea_status_time (status, created_time),
    KEY idx_cea_student (student_id),
    CONSTRAINT fk_cea_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='大学英语免修/免考申请审批';
