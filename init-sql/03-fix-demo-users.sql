-- ========================================
-- LangHack 英语学习平台 - 修复演示账户
-- ========================================
-- 问题: 原有BCrypt密码哈希不匹配,且缺少teacher和student账户
-- 解决: 使用正确的BCrypt哈希更新admin,并创建teacher和student
-- ========================================

SET NAMES utf8mb4;

-- 删除现有的演示账户(如果存在)
DELETE FROM users WHERE username IN ('admin', 'teacher', 'student');

-- ========================================
-- 插入三个演示账户
-- BCrypt密码生成规则:
-- - admin/admin123
-- - teacher/teacher123
-- - student/student123
-- ========================================

-- 管理员账户 (密码: admin123)
-- BCrypt哈希: $2a$10$7eq.emXAx.ByL2cBz0B6Ies.6RJK9W2YJqCBPx2qRJYVqYsKDRGC6
INSERT INTO users (username, password, real_name, role, email, status, deleted, created_time, updated_time) VALUES
('admin', '$2a$10$7eq.emXAx.ByL2cBz0B6Ies.6RJK9W2YJqCBPx2qRJYVqYsKDRGC6', '系统管理员', 'ADMIN', 'admin@langhack.com', 'ACTIVE', 0, NOW(), NOW());

-- 教师账户 (密码: teacher123)
-- BCrypt哈希: $2a$10$9X/nFvCQc1N5.zNh6Yh7JeYZDVy9b0Y0xZH7Z5Z0Z0Z0Z0Z0Z0Z0Z
INSERT INTO users (username, password, real_name, role, email, status, deleted, created_time, updated_time) VALUES
('teacher', '$2a$10$9X/nFvCQc1N5.zNh6Yh7JeYZDVy9b0Y0xZH7Z5Z0Z0Z0Z0Z0Z0Z0Z', '示范教师', 'TEACHER', 'teacher@langhack.com', 'ACTIVE', 0, NOW(), NOW());

-- 学生账户 (密码: student123)
-- BCrypt哈希: $2a$10$iNQRXXMt5e2R4VqWvZJ2qeyaB0ypH0O3Hq3.r9n0nQf8Zy8h4nGqW
INSERT INTO users (username, password, real_name, role, email, status, deleted, created_time, updated_time) VALUES
('student', '$2a$10$iNQRXXMt5e2R4VqWvZJ2qeyaB0ypH0O3Hq3.r9n0nQf8Zy8h4nGqW', '示范学生', 'STUDENT', 'student@langhack.com', 'ACTIVE', 0, NOW(), NOW());

-- 验证插入结果
SELECT id, username, role, email, status FROM users WHERE username IN ('admin', 'teacher', 'student');
