-- ═══════════════════════════════════════════════════════════════════════
-- 从 school_student 批量补建 users 行
-- 目的：让还没走过 SSO 首登的学生也能在 “用户管理” 页面出现
--
-- 数据安全要点（对应 sync-users-from-school-student.sh 里的护栏）：
--   1. LEFT JOIN 不带 deleted=0：任何同名 users 行（含软删）都跳过，
--      避免撞 `UNIQUE KEY username` 触发整批回滚
--   2. ON DUPLICATE KEY UPDATE username = users.username：并发 SSO
--      首登在我们 SELECT 之后 INSERT 同学号时，转为 no-op，不回滚其他行
--   3. password 用 '!NOLOGIN_SSO_ONLY'：非 bcrypt 前缀，Spring 会直接
--      判为不匹配。SSO 启用后密码登录路径已被禁，这里只是防御
--   4. 只写最必要字段；sso_bound / sso_provider / sso_subject 留 NULL，
--      首登时由 SsoUserMappingService 自己填
--   5. JOIN 强制 COLLATE utf8mb4_unicode_ci：users(unicode_ci) 与
--      school_student(0900_ai_ci, MySQL 8 默认) 的 collation 不同，
--      不加会触发 ERROR 1267。COLLATE 只影响比较，不改表。
--
-- 过滤：学号前 4 位 >= 2021（毕业超过两年的老学号不再显示）
-- ═══════════════════════════════════════════════════════════════════════

INSERT INTO users (
    username,
    password,
    real_name,
    role,
    status,
    deleted,
    school_student_id,
    created_time,
    updated_time
)
SELECT
    ss.student_id      AS username,
    '!NOLOGIN_SSO_ONLY' AS password,
    ss.student_name    AS real_name,
    'STUDENT'          AS role,
    'ACTIVE'           AS status,
    0                  AS deleted,
    ss.id              AS school_student_id,
    NOW()              AS created_time,
    NOW()              AS updated_time
FROM school_student ss
LEFT JOIN users u
       ON u.username = ss.student_id COLLATE utf8mb4_unicode_ci
WHERE u.id IS NULL
  AND CHAR_LENGTH(ss.student_id) >= 4
  AND LEFT(ss.student_id, 4) >= '2021'
ON DUPLICATE KEY UPDATE username = users.username;
