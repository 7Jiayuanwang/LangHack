-- 清理并重置数据脚本（改进版）
-- 使用 TRUNCATE 重置自增ID

USE english_learning;

-- 禁用外键检查
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 清空所有表（使用TRUNCATE重置自增ID）
TRUNCATE TABLE texts;
TRUNCATE TABLE units;
TRUNCATE TABLE courses;
TRUNCATE TABLE words;

-- 启用外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- 2. 重新插入2个课程（ID会从1开始）
INSERT INTO courses (name, description, cover_image, created_by, status, deleted, created_time, updated_time)
VALUES
('新一代大学英语(第二版)综合教程1(智慧版)', '适合大学英语基础阶段学习', NULL, 1, 'ACTIVE', 0, NOW(), NOW()),
('新一代大学英语(第二版)综合教程2(智慧版)', '适合大学英语提高阶段学习', NULL, 1, 'ACTIVE', 0, NOW(), NOW());

-- 3. 插入单元（课程ID现在是1和2）
INSERT INTO units (course_id, name, description, order_num, created_time, updated_time)
VALUES
-- 综合教程1的单元
(1, 'Unit 1', 'Fresh Start', 1, NOW(), NOW()),
(1, 'Unit 2', 'Loving Parents, Loving Children', 2, NOW(), NOW()),
(1, 'Unit 3', 'Digital Campus', 3, NOW(), NOW()),
(1, 'Unit 4', 'Heroes of Our Time', 4, NOW(), NOW()),
(1, 'Unit 5', 'Winning Is Not Everything', 5, NOW(), NOW()),

-- 综合教程2的单元
(2, 'Unit 1', 'Ways of Learning', 1, NOW(), NOW()),
(2, 'Unit 2', 'Values', 2, NOW(), NOW()),
(2, 'Unit 3', 'The Generation Gap', 3, NOW(), NOW()),
(2, 'Unit 4', 'The Virtual World', 4, NOW(), NOW()),
(2, 'Unit 5', 'Overcoming Obstacles', 5, NOW(), NOW());

-- 4. 插入测试单词（用于点击查词功能）
INSERT INTO words (word, pronunciation, definition, part_of_speech, example_sentence, difficulty, deleted, created_time, updated_time)
VALUES
('study', '/ˈstʌdi/', '学习；研究', 'v./n.', 'I study English every day.', 'EASY', 0, NOW(), NOW()),
('learn', '/lɜːn/', '学习；得知', 'v.', 'We learn new things every day.', 'EASY', 0, NOW(), NOW()),
('fresh', '/freʃ/', '新鲜的；清新的', 'adj.', 'Fresh start means a new beginning.', 'EASY', 0, NOW(), NOW()),
('start', '/stɑːt/', '开始；出发', 'v./n.', 'Let us start the lesson.', 'EASY', 0, NOW(), NOW()),
('parent', '/ˈpeərənt/', '父母；家长', 'n.', 'Loving parents care for their children.', 'EASY', 0, NOW(), NOW()),
('love', '/lʌv/', '爱；热爱', 'v./n.', 'We love our parents.', 'EASY', 0, NOW(), NOW()),
('child', '/tʃaɪld/', '孩子；儿童', 'n.', 'Every child needs education.', 'EASY', 0, NOW(), NOW()),
('children', '/ˈtʃɪldrən/', '孩子们', 'n.', 'The children are playing.', 'EASY', 0, NOW(), NOW()),
('digital', '/ˈdɪdʒɪtl/', '数字的；数码的', 'adj.', 'We live in a digital age.', 'MEDIUM', 0, NOW(), NOW()),
('campus', '/ˈkæmpəs/', '校园', 'n.', 'The digital campus is very modern.', 'EASY', 0, NOW(), NOW()),
('have', '/hæv/', '有；拥有', 'v.', 'I have a book.', 'EASY', 0, NOW(), NOW()),
('make', '/meɪk/', '制作；使得', 'v.', 'Make a plan.', 'EASY', 0, NOW(), NOW()),
('time', '/taɪm/', '时间；时代', 'n.', 'Time is precious.', 'EASY', 0, NOW(), NOW()),
('people', '/ˈpiːpl/', '人；人们', 'n.', 'Many people study English.', 'EASY', 0, NOW(), NOW()),
('way', '/weɪ/', '方式；道路', 'n.', 'Different ways of learning.', 'EASY', 0, NOW(), NOW()),
('work', '/wɜːk/', '工作', 'n./v.', 'Hard work pays off.', 'EASY', 0, NOW(), NOW()),
('world', '/wɜːld/', '世界', 'n.', 'The virtual world.', 'EASY', 0, NOW(), NOW()),
('life', '/laɪf/', '生活；生命', 'n.', 'Campus life is colorful.', 'EASY', 0, NOW(), NOW()),
('good', '/ɡʊd/', '好的；优秀的', 'adj.', 'This is a good book.', 'EASY', 0, NOW(), NOW()),
('new', '/njuː/', '新的', 'adj.', 'A new semester.', 'EASY', 0, NOW(), NOW());

-- 验证数据
SELECT '=== 课程列表（应该只有2条）===' as Info;
SELECT id, name, status FROM courses WHERE deleted = 0;

SELECT '=== 单元列表（应该有10条）===' as Info;
SELECT id, course_id, name, order_num FROM units;

SELECT '=== 单词总数（应该有20个）===' as Info;
SELECT COUNT(*) as total_words FROM words WHERE deleted = 0;
