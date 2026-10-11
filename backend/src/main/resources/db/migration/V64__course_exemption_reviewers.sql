-- Persist the teachers assigned to review each exemption application.
CREATE TABLE IF NOT EXISTS course_exemption_reviewer (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cer_application_teacher (application_id, teacher_id),
    KEY idx_cer_teacher_application (teacher_id, application_id),
    CONSTRAINT fk_cer_application FOREIGN KEY (application_id)
        REFERENCES course_exemption_application(id) ON DELETE CASCADE,
    CONSTRAINT fk_cer_teacher FOREIGN KEY (teacher_id)
        REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='assigned reviewers for course exemption applications';

-- Backfill from application students' active classes.
INSERT IGNORE INTO course_exemption_reviewer (application_id, teacher_id, created_time)
SELECT a.id, c.teacher_id, a.created_time
FROM course_exemption_application a
JOIN student_classes sc ON sc.student_id = a.student_id AND sc.status = 'ACTIVE'
JOIN classes c ON c.id = sc.class_id
JOIN users teacher ON teacher.id = c.teacher_id
WHERE c.teacher_id IS NOT NULL
  AND teacher.role = 'TEACHER'
  AND teacher.status = 'ACTIVE';

-- Backfill from school-data bindings. A user's login name is not always the school student number,
-- so prefer school_student.student_id when users.school_student_id is populated.
INSERT IGNORE INTO course_exemption_reviewer (application_id, teacher_id, created_time)
SELECT a.id, teacher.id, a.created_time
FROM course_exemption_application a
JOIN users student ON student.id = a.student_id
LEFT JOIN school_student ss ON ss.id = student.school_student_id
JOIN teacher_student_binding tsb
  ON tsb.student_id COLLATE utf8mb4_unicode_ci =
     COALESCE(ss.student_id, student.username) COLLATE utf8mb4_unicode_ci
JOIN users teacher
  ON teacher.username COLLATE utf8mb4_unicode_ci =
     tsb.teacher_id COLLATE utf8mb4_unicode_ci
WHERE teacher.role = 'TEACHER'
  AND teacher.status = 'ACTIVE';
