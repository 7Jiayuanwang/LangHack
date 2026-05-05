-- P3-2: inspect student account mapping status

SELECT
    config_key,
    config_value,
    description,
    updated_time
FROM system_config
WHERE config_group = 'feature_toggle'
  AND config_key IN ('sso.auto_grant_student', 'sso.auto_grant_teacher', 'student.lazy_data_sync');

SELECT
    COUNT(*) AS total_students,
    COUNT(DISTINCT college_name) AS colleges,
    MAX(sync_time) AS last_sync_time
FROM school_student;

SELECT
    COUNT(*) AS total_bindings,
    COUNT(DISTINCT teacher_id) AS teachers,
    COUNT(DISTINCT student_id) AS students,
    MAX(sync_time) AS last_sync_time
FROM teacher_student_binding;

SELECT
    sync_type,
    data_source,
    sync_method,
    status,
    total_count,
    success_count,
    fail_count,
    skip_count,
    error_message,
    trigger_type,
    operator,
    created_at
FROM sync_logs
ORDER BY created_at DESC
LIMIT 10;

SELECT
    student_id,
    student_name,
    college_name,
    grade,
    sync_time
FROM school_student
WHERE student_id = '2023122066164'
LIMIT 1;

SELECT
    teacher_id,
    student_id,
    course_code,
    course_class_code,
    academic_year,
    semester,
    sync_time
FROM teacher_student_binding
WHERE student_id = '2023122066164'
LIMIT 5;

SELECT
    id,
    username,
    role,
    status,
    sso_bound,
    sso_provider,
    real_name,
    created_time
FROM users
WHERE username = '2023122066164'
LIMIT 1;

SELECT
    b.id AS binding_id,
    b.sso_username,
    b.auto_approved,
    u.id AS user_id,
    u.username,
    u.role,
    u.status,
    u.real_name,
    b.created_time
FROM sso_user_bindings b
LEFT JOIN users u ON b.user_id = u.id
WHERE b.auto_approved = 0
  AND b.deleted = 0
  AND u.role IN ('USER', 'STUDENT')
ORDER BY b.created_time DESC
LIMIT 10;

SELECT
    role,
    COUNT(*) AS count,
    SUM(CASE WHEN sso_bound = 1 THEN 1 ELSE 0 END) AS sso_bound_count,
    SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_count
FROM users
WHERE deleted = 0
GROUP BY role
ORDER BY FIELD(role, 'ADMIN', 'OPS', 'TEACHER', 'STUDENT', 'USER');

-- Manual toggle enablement example:
-- INSERT INTO system_config (config_group, config_key, config_value, description, is_secret)
-- VALUES ('feature_toggle', 'sso.auto_grant_student', 'true', 'SSO自动授予学生权限', 0)
-- ON DUPLICATE KEY UPDATE config_value = 'true', updated_time = NOW();
