-- 课文管理功能测试数据脚本
-- 请在运行此脚本前确保已经创建了 english_learning 数据库

USE english_learning;

-- 1. 插入测试课程
INSERT INTO courses (name, description, cover_image, created_by, status, deleted, created_time, updated_time)
VALUES
('新一代大学英语(第二版)综合教程1(智慧版)', '适合大学英语基础阶段学习', NULL, 1, 'ACTIVE', 0, NOW(), NOW()),
('新一代大学英语(第二版)综合教程2(智慧版)', '适合大学英语提高阶段学习', NULL, 1, 'ACTIVE', 0, NOW(), NOW());

-- 2. 插入测试单元（假设课程ID从1开始）
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

-- 3. 插入测试单词（用于点击查词功能）
INSERT INTO words (word, pronunciation, definition, part_of_speech, example_sentence, difficulty, deleted, created_time, updated_time)
VALUES
-- 常用基础单词
('study', '/ˈstʌdi/', '学习；研究', 'v./n.', 'I study English every day.', 'EASY', 0, NOW(), NOW()),
('learn', '/lɜːn/', '学习；得知', 'v.', 'We learn new things every day.', 'EASY', 0, NOW(), NOW()),
('fresh', '/freʃ/', '新鲜的；清新的', 'adj.', 'Fresh start means a new beginning.', 'EASY', 0, NOW(), NOW()),
('start', '/stɑːt/', '开始；出发', 'v./n.', 'Let us start the lesson.', 'EASY', 0, NOW(), NOW()),
('parent', '/ˈpeərənt/', '父母；家长', 'n.', 'Loving parents care for their children.', 'EASY', 0, NOW(), NOW()),
('love', '/lʌv/', '爱；热爱', 'v./n.', 'We love our parents.', 'EASY', 0, NOW(), NOW()),
('child', '/tʃaɪld/', '孩子；儿童', 'n.', 'Every child needs education.', 'EASY', 0, NOW(), NOW()),
('children', '/ˈtʃɪldrən/', '孩子们（child的复数）', 'n.', 'The children are playing.', 'EASY', 0, NOW(), NOW()),
('digital', '/ˈdɪdʒɪtl/', '数字的；数码的', 'adj.', 'We live in a digital age.', 'MEDIUM', 0, NOW(), NOW()),
('campus', '/ˈkæmpəs/', '校园；（大学）校区', 'n.', 'The digital campus is very modern.', 'EASY', 0, NOW(), NOW()),

-- 常用动词
('have', '/hæv/', '有；拥有', 'v.', 'I have a book.', 'EASY', 0, NOW(), NOW()),
('make', '/meɪk/', '制作；使得', 'v.', 'Make a plan.', 'EASY', 0, NOW(), NOW()),
('do', '/duː/', '做；干', 'v.', 'Do your homework.', 'EASY', 0, NOW(), NOW()),
('go', '/ɡəʊ/', '去；走', 'v.', 'Go to school.', 'EASY', 0, NOW(), NOW()),
('get', '/ɡet/', '得到；获得', 'v.', 'Get good grades.', 'EASY', 0, NOW(), NOW()),
('know', '/nəʊ/', '知道；了解', 'v.', 'I know the answer.', 'EASY', 0, NOW(), NOW()),
('think', '/θɪŋk/', '想；认为', 'v.', 'I think you are right.', 'EASY', 0, NOW(), NOW()),
('see', '/siː/', '看见；明白', 'v.', 'I see what you mean.', 'EASY', 0, NOW(), NOW()),

-- 常用形容词
('good', '/ɡʊd/', '好的；优秀的', 'adj.', 'This is a good book.', 'EASY', 0, NOW(), NOW()),
('new', '/njuː/', '新的；新鲜的', 'adj.', 'A new semester.', 'EASY', 0, NOW(), NOW()),
('important', '/ɪmˈpɔːtnt/', '重要的', 'adj.', 'Education is important.', 'MEDIUM', 0, NOW(), NOW()),
('different', '/ˈdɪfrənt/', '不同的', 'adj.', 'Different cultures.', 'EASY', 0, NOW(), NOW()),

-- 常用名词
('time', '/taɪm/', '时间；时代', 'n.', 'Time is precious.', 'EASY', 0, NOW(), NOW()),
('people', '/ˈpiːpl/', '人；人们', 'n.', 'Many people study English.', 'EASY', 0, NOW(), NOW()),
('way', '/weɪ/', '方式；道路', 'n.', 'Different ways of learning.', 'EASY', 0, NOW(), NOW()),
('work', '/wɜːk/', '工作；著作', 'n./v.', 'Hard work pays off.', 'EASY', 0, NOW(), NOW()),
('world', '/wɜːld/', '世界；领域', 'n.', 'The virtual world.', 'EASY', 0, NOW(), NOW()),
('life', '/laɪf/', '生活；生命', 'n.', 'Campus life is colorful.', 'EASY', 0, NOW(), NOW()),

-- 其他常用词
('very', '/ˈveri/', '非常；很', 'adv.', 'Very good.', 'EASY', 0, NOW(), NOW()),
('can', '/kæn/', '能够；可以', 'v.', 'I can speak English.', 'EASY', 0, NOW(), NOW()),
('will', '/wɪl/', '将；会', 'v.', 'I will study hard.', 'EASY', 0, NOW(), NOW()),
('because', '/bɪˈkɒz/', '因为', 'conj.', 'I study because I want to learn.', 'EASY', 0, NOW(), NOW()),
('when', '/wen/', '当...时候', 'conj.', 'When I was young.', 'EASY', 0, NOW(), NOW()),
('how', '/haʊ/', '如何；怎样', 'adv.', 'How are you?', 'EASY', 0, NOW(), NOW()),
('what', '/wɒt/', '什么', 'pron.', 'What is your name?', 'EASY', 0, NOW(), NOW()),
('which', '/wɪtʃ/', '哪一个', 'pron.', 'Which book do you like?', 'EASY', 0, NOW(), NOW());

-- 查看插入的数据
SELECT '=== 课程列表 ===' as Info;
SELECT id, name, description, status FROM courses;

SELECT '=== 单元列表 ===' as Info;
SELECT id, course_id, name, description, order_num FROM units;

SELECT '=== 单词总数 ===' as Info;
SELECT COUNT(*) as total_words FROM words;

-- 验证数据
SELECT '=== 课程和单元统计 ===' as Info;
SELECT
    c.id as course_id,
    c.name as course_name,
    COUNT(u.id) as unit_count
FROM courses c
LEFT JOIN units u ON c.id = u.course_id
WHERE c.deleted = 0
GROUP BY c.id, c.name;
