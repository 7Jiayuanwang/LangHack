-- Student submission and teacher approval share this table.
CREATE TABLE IF NOT EXISTS course_exemption_application (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    course_name VARCHAR(100) NOT NULL,
    apply_type VARCHAR(20) NOT NULL,
    exam_type VARCHAR(10) NOT NULL,
    exam_score VARCHAR(20) NOT NULL,
    exam_date DATE DEFAULT NULL,
    certificate_url VARCHAR(500) NOT NULL,
    student_remark VARCHAR(500) DEFAULT NULL,
    converted_score INT DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_comment VARCHAR(500) DEFAULT NULL,
    reviewed_by BIGINT DEFAULT NULL,
    reviewed_time DATETIME DEFAULT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_cea_status_time (status, created_time),
    KEY idx_cea_student (student_id),
    CONSTRAINT fk_cea_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='course exemption applications';
