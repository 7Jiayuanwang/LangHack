-- P3-2: fix student account mapping

INSERT INTO system_config (config_group, config_key, config_value, description, is_secret)
VALUES ('feature_toggle', 'sso.auto_grant_student', 'true', 'SSO自动授予学生权限', 0)
ON DUPLICATE KEY UPDATE
    config_value = 'true',
    updated_time = NOW();

INSERT INTO system_config (config_group, config_key, config_value, description, is_secret)
VALUES ('feature_toggle', 'sso.auto_grant_teacher', 'true', 'SSO自动授予教师权限', 0)
ON DUPLICATE KEY UPDATE
    config_value = 'true',
    updated_time = NOW();

INSERT INTO system_config (config_group, config_key, config_value, description, is_secret)
VALUES ('feature_toggle', 'student.lazy_data_sync', 'true', '学生端懒加载数据同步', 0)
ON DUPLICATE KEY UPDATE
    config_value = 'true',
    updated_time = NOW();

UPDATE users u
INNER JOIN school_student ss ON u.username = ss.student_id
SET
    u.role = 'STUDENT',
    u.status = 'ACTIVE',
    u.real_name = COALESCE(u.real_name, ss.student_name),
    u.updated_time = NOW()
WHERE u.role = 'USER'
  AND u.deleted = 0;

UPDATE sso_user_bindings b
INNER JOIN users u ON b.user_id = u.id
INNER JOIN school_student ss ON u.username = ss.student_id
SET
    b.auto_approved = 1,
    b.updated_time = NOW()
WHERE b.auto_approved = 0
  AND b.deleted = 0
  AND u.role = 'STUDENT'
  AND u.deleted = 0;

SELECT
    'Feature Toggles' AS check_type,
    config_key,
    config_value,
    description
FROM system_config
WHERE config_group = 'feature_toggle'
  AND config_key IN ('sso.auto_grant_student', 'sso.auto_grant_teacher', 'student.lazy_data_sync')
UNION ALL
SELECT
    'Upgraded Users' AS check_type,
    CONCAT('Total: ', COUNT(*)) AS config_key,
    '' AS config_value,
    '' AS description
FROM users u
INNER JOIN school_student ss ON u.username = ss.student_id
WHERE u.role = 'STUDENT'
  AND u.deleted = 0;
