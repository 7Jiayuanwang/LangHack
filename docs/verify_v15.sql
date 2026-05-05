-- Verify the V15 schema fix.
USE english_learning;

SELECT
    '1. backup_records.category' AS test_name,
    CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS result,
    COUNT(*) AS actual,
    1 AS expected
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'backup_records'
  AND COLUMN_NAME = 'category';

SELECT
    '2. videos.subtitle_content_zh' AS test_name,
    CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS result,
    COUNT(*) AS actual,
    1 AS expected
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'videos'
  AND COLUMN_NAME = 'subtitle_content_zh';

SELECT
    '3. course_selection.credits nullable' AS test_name,
    CASE WHEN IS_NULLABLE = 'YES' THEN 'PASS' ELSE 'FAIL' END AS result,
    IS_NULLABLE AS actual,
    'YES' AS expected
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'course_selection'
  AND COLUMN_NAME = 'credits';

SELECT
    '4. course_selection.teacher_id nullable' AS test_name,
    CASE WHEN IS_NULLABLE = 'YES' THEN 'PASS' ELSE 'FAIL' END AS result,
    IS_NULLABLE AS actual,
    'YES' AS expected
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'course_selection'
  AND COLUMN_NAME = 'teacher_id';

SELECT
    '5. course_schedule.class_id nullable' AS test_name,
    CASE WHEN IS_NULLABLE = 'YES' THEN 'PASS' ELSE 'FAIL' END AS result,
    IS_NULLABLE AS actual,
    'YES' AS expected
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'course_schedule'
  AND COLUMN_NAME = 'class_id';

SELECT
    '6. course_schedule.day_of_week nullable' AS test_name,
    CASE WHEN IS_NULLABLE = 'YES' THEN 'PASS' ELSE 'FAIL' END AS result,
    IS_NULLABLE AS actual,
    'YES' AS expected
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'course_schedule'
  AND COLUMN_NAME = 'day_of_week';

SELECT
    '7. course_schedule.period nullable' AS test_name,
    CASE WHEN IS_NULLABLE = 'YES' THEN 'PASS' ELSE 'FAIL' END AS result,
    IS_NULLABLE AS actual,
    'YES' AS expected
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'course_schedule'
  AND COLUMN_NAME = 'period';

SELECT
    '8. course_schedule foreign key' AS test_name,
    CASE
        WHEN COUNT(*) = 1 AND MAX(DELETE_RULE) = 'SET NULL' THEN 'PASS'
        WHEN COUNT(*) = 0 THEN 'WARN'
        ELSE 'FAIL'
    END AS result,
    CONCAT('DELETE_RULE=', COALESCE(MAX(DELETE_RULE), 'NULL')) AS actual,
    'DELETE_RULE=SET NULL' AS expected
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE TABLE_NAME = 'course_schedule'
  AND CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'fk_course_schedule_teaching_class';

SELECT
    '9. course_selection field mappings' AS test_name,
    CASE WHEN COUNT(*) >= 4 THEN 'PASS' ELSE 'FAIL' END AS result,
    COUNT(*) AS actual,
    '>=4' AS expected
FROM field_mappings
WHERE data_source = 'course_selection'
  AND target_field IN ('credits', 'student_name');

SELECT
    '10. course_selection idx_student_academic' AS test_name,
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS result,
    COUNT(*) AS actual,
    '>0' AS expected
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'course_selection'
  AND INDEX_NAME = 'idx_student_academic';

SELECT
    '11. course_schedule idx_class_academic' AS test_name,
    CASE WHEN COUNT(*) > 0 THEN 'PASS' ELSE 'FAIL' END AS result,
    COUNT(*) AS actual,
    '>0' AS expected
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'course_schedule'
  AND INDEX_NAME = 'idx_class_academic';

SELECT
    '12. course_schedule orphan records' AS test_name,
    CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE CONCAT('WARN: ', COUNT(*), ' orphan records') END AS result,
    COUNT(*) AS actual,
    0 AS expected
FROM course_schedule cs
LEFT JOIN teaching_class tc ON cs.class_id = tc.class_id
WHERE cs.class_id IS NOT NULL
  AND tc.class_id IS NULL;

SELECT
    '13. course_selection orphan records' AS test_name,
    CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE CONCAT('WARN: ', COUNT(*), ' orphan records') END AS result,
    COUNT(*) AS actual,
    0 AS expected
FROM course_selection csel
LEFT JOIN school_course sc ON csel.course_code = sc.course_code
WHERE csel.course_code IS NOT NULL
  AND sc.course_code IS NULL;

SELECT
    'V15 Verification Summary' AS title,
    13 AS total_tests;
