-- 英语学习平台数据库完整初始化脚本
-- 包含所有表结构、索引、初始数据和普通用户功能支持

-- 创建数据库
CREATE DATABASE IF NOT EXISTS english_learning DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE english_learning;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    email VARCHAR(100) COMMENT '邮箱',
    role ENUM('USER', 'STUDENT', 'TEACHER', 'ADMIN') NOT NULL COMMENT '角色',
    real_name VARCHAR(50) COMMENT '真实姓名',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar VARCHAR(255) COMMENT '头像URL',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_role (role),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 课程表
CREATE TABLE IF NOT EXISTS courses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '课程名称',
    description TEXT COMMENT '课程描述',
    cover_image VARCHAR(255) COMMENT '封面图片',
    created_by BIGINT COMMENT '创建者ID',
    status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_status (status),
    INDEX idx_created_by (created_by),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- 单元表
CREATE TABLE IF NOT EXISTS units (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL COMMENT '课程ID',
    name VARCHAR(100) NOT NULL COMMENT '单元名称',
    description TEXT COMMENT '单元描述',
    order_num INT DEFAULT 0 COMMENT '排序号',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    INDEX idx_course_id (course_id),
    INDEX idx_order_num (order_num),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单元表';

-- 课文表
CREATE TABLE IF NOT EXISTS texts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    unit_id BIGINT NOT NULL COMMENT '单元ID',
    title VARCHAR(200) NOT NULL COMMENT '课文标题',
    content TEXT NOT NULL COMMENT '课文内容',
    audio_url VARCHAR(255) COMMENT '音频URL',
    difficulty ENUM('EASY', 'MEDIUM', 'HARD') DEFAULT 'MEDIUM' COMMENT '难度',
    order_num INT DEFAULT 0 COMMENT '排序号',
    uploaded_by BIGINT COMMENT '上传者ID（NULL表示系统内容）',
    tags VARCHAR(255) COMMENT '标签，多个用逗号分隔',
    view_count INT DEFAULT 0 COMMENT '查看次数',
    like_count INT DEFAULT 0 COMMENT '点赞次数',
    is_public TINYINT DEFAULT 1 COMMENT '是否公开 0-私有 1-公开',
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'APPROVED' COMMENT '审核状态',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_unit_id (unit_id),
    INDEX idx_uploaded_by (uploaded_by),
    INDEX idx_difficulty (difficulty),
    INDEX idx_order_num (order_num),
    INDEX idx_deleted (deleted),
    INDEX idx_status (status),
    INDEX idx_is_public (is_public)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课文表';

-- 单词表
CREATE TABLE IF NOT EXISTS words (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    word VARCHAR(100) NOT NULL COMMENT '单词',
    pronunciation VARCHAR(200) COMMENT '发音',
    definition TEXT COMMENT '中文释义',
    example_sentence TEXT COMMENT '例句',
    audio_url VARCHAR(255) COMMENT '音频URL',
    part_of_speech VARCHAR(50) COMMENT '词性',
    difficulty ENUM('EASY', 'MEDIUM', 'HARD') DEFAULT 'MEDIUM' COMMENT '难度',
    synonyms TEXT COMMENT '同义词',
    antonyms TEXT COMMENT '反义词',
    wordnet_id VARCHAR(100) COMMENT 'WordNet ID',
    multiple_definitions TEXT COMMENT '多个释义',
    source VARCHAR(100) COMMENT '来源',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_word (word),
    INDEX idx_difficulty (difficulty),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词表';

-- 单元单词关联表
CREATE TABLE IF NOT EXISTS unit_words (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    unit_id BIGINT NOT NULL COMMENT '单元ID',
    word_id BIGINT NOT NULL COMMENT '单词ID',
    order_num INT DEFAULT 0 COMMENT '排序号',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE,
    INDEX idx_unit_id (unit_id),
    INDEX idx_word_id (word_id),
    INDEX idx_order_num (order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单元单词关联表';

-- 班级表
CREATE TABLE IF NOT EXISTS classes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '班级名称',
    description TEXT COMMENT '班级描述',
    course_id BIGINT COMMENT '关联课程ID',
    teacher_id BIGINT COMMENT '教师ID',
    join_code VARCHAR(10) UNIQUE NULL COMMENT '加入码(可为空)',
    max_students INT DEFAULT 50 COMMENT '最大学生数',
    status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (course_id) REFERENCES courses(id),
    FOREIGN KEY (teacher_id) REFERENCES users(id),
    INDEX idx_course_id (course_id),
    INDEX idx_teacher_id (teacher_id),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级表';

-- 学生班级关联表
CREATE TABLE IF NOT EXISTS student_classes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE,
    INDEX idx_student_id (student_id),
    INDEX idx_class_id (class_id),
    UNIQUE KEY uk_student_class (student_id, class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生班级关联表';

-- 任务表
CREATE TABLE IF NOT EXISTS assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL COMMENT '任务标题',
    description TEXT COMMENT '任务描述',
    class_id BIGINT COMMENT '班级ID',
    unit_id BIGINT COMMENT '单元ID',
    text_id BIGINT COMMENT '课文ID',
    type ENUM('READING', 'VOCABULARY', 'COMPREHENSION') NOT NULL COMMENT '任务类型',
    difficulty ENUM('EASY', 'MEDIUM', 'HARD') DEFAULT 'MEDIUM' COMMENT '难度',
    deadline DATETIME COMMENT '截止时间',
    status ENUM('DRAFT', 'PUBLISHED', 'CLOSED') DEFAULT 'DRAFT' COMMENT '状态',
    created_by BIGINT COMMENT '创建者ID',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (class_id) REFERENCES classes(id),
    FOREIGN KEY (unit_id) REFERENCES units(id),
    FOREIGN KEY (text_id) REFERENCES texts(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_class_id (class_id),
    INDEX idx_unit_id (unit_id),
    INDEX idx_text_id (text_id),
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_created_by (created_by),
    INDEX idx_deadline (deadline),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- 学生任务关联表
CREATE TABLE IF NOT EXISTS student_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL COMMENT '任务ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    status ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED') DEFAULT 'NOT_STARTED' COMMENT '完成状态',
    score INT COMMENT '得分',
    start_time DATETIME COMMENT '开始时间',
    complete_time DATETIME COMMENT '完成时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_student_id (student_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生任务关联表';

-- 学习记录表
CREATE TABLE IF NOT EXISTS learning_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    word_id BIGINT COMMENT '单词ID',
    assignment_id BIGINT COMMENT '任务ID',
    record_type VARCHAR(50) COMMENT '记录类型：VIEW-浏览，REVIEW-复习，TEST-测试',
    duration_seconds INT DEFAULT 0 COMMENT '学习时长（秒）',
    correct_count INT DEFAULT 0 COMMENT '正确次数',
    total_count INT DEFAULT 0 COMMENT '总次数',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_student_id (student_id),
    INDEX idx_word_id (word_id),
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_record_type (record_type),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习记录表';

-- 生词本表已被vocabulary_books表替代，删除重复定义

-- 词汇书表
CREATE TABLE IF NOT EXISTS vocabulary_books (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    word_id BIGINT NOT NULL COMMENT '单词ID',
    added_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    review_count INT DEFAULT 0 COMMENT '复习次数',
    last_reviewed DATETIME COMMENT '最后复习时间',
    mastery_level ENUM('NEW', 'LEARNING', 'FAMILIAR', 'MASTERED') DEFAULT 'NEW' COMMENT '掌握程度：NEW-新词，LEARNING-学习中，FAMILIAR-熟悉，MASTERED-已掌握',
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE,
    INDEX idx_student_id (student_id),
    INDEX idx_word_id (word_id),
    INDEX idx_mastery_level (mastery_level),
    UNIQUE KEY uk_student_word (student_id, word_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生词本表';

-- 旧的词汇书关联表已不再需要，删除
-- vocabulary_books表现在直接存储学生的生词本条目

-- 插入初始数据

-- 插入管理员用户
INSERT IGNORE INTO users (id, username, password, role, real_name, status) VALUES
(1, 'admin', '$2a$10$vP1rOcRQQLvkHifk/1csgekyaO92j15kY6Tibmlv3qhN.I32rt636', 'ADMIN', '系统管理员', 'ACTIVE');

-- 插入教师用户
INSERT IGNORE INTO users (id, username, password, role, real_name, status) VALUES
(2, 'teacher', '$2a$10$G6rXW5MoyCo49ffuospIJ.1Dh8yWM5IAVRLjl.2aNk5gJEsE3.lRS', 'TEACHER', '张老师', 'ACTIVE');

-- 插入学生用户
INSERT IGNORE INTO users (id, username, password, role, real_name, status) VALUES
(3, 'student', '$2a$10$2gWgcibNYpi2WC26J1OvaOgnZDvoFEWXiUJ9vJROZN2HC8ALtNMkS', 'STUDENT', '李同学', 'ACTIVE');

-- 插入基础课程
INSERT IGNORE INTO courses (id, name, description, status) VALUES
(1, '英语入门', '适合英语初学者的基础课程', 'ACTIVE'),
(2, '日常英语', '日常生活英语对话和表达', 'ACTIVE'),
(3, '商务英语', '商务场景中的英语应用', 'ACTIVE');

-- 插入课程单元
INSERT IGNORE INTO units (id, course_id, name, description, order_num) VALUES
-- 英语入门课程的单元
(1, 1, 'Unit 1: Greetings', '基础问候语', 1),
(2, 1, 'Unit 2: Numbers', '数字和时间', 2),
(3, 1, 'Unit 3: Family', '家庭成员', 3),

-- 日常英语课程的单元
(4, 2, 'Unit 1: Daily Routine', '日常作息', 1),
(5, 2, 'Unit 2: Food', '食物和饮料', 2),
(6, 2, 'Unit 3: Shopping', '购物场景', 3),

-- 商务英语课程的单元
(7, 3, 'Unit 1: Meetings', '会议英语', 1),
(8, 3, 'Unit 2: Presentations', '演讲技巧', 2),
(9, 3, 'Unit 3: Negotiations', '商务谈判', 3);

-- 插入基础单词
INSERT IGNORE INTO words (id, word, pronunciation, definition, example_sentence, part_of_speech, difficulty, source) VALUES
(1, 'hello', '/həˈləʊ/', '你好，喂', 'Hello! How are you?', 'interjection', 'EASY', 'basic'),
(2, 'good', '/ɡʊd/', '好的，优良的', 'This is a good book.', 'adjective', 'EASY', 'basic'),
(3, 'morning', '/ˈmɔːnɪŋ/', '早晨，上午', 'Good morning, everyone.', 'noun', 'EASY', 'basic'),
(4, 'thank', '/θæŋk/', '感谢，谢谢', 'Thank you for your help.', 'verb', 'EASY', 'basic'),
(5, 'please', '/pliːz/', '请，请问', 'Please help me.', 'adverb', 'EASY', 'basic'),
(6, 'beautiful', '/ˈbjuːtɪfl/', '美丽的，漂亮的', 'The flower is beautiful.', 'adjective', 'MEDIUM', 'common'),
(7, 'important', '/ɪmˈpɔːtnt/', '重要的，重大的', 'This is an important meeting.', 'adjective', 'MEDIUM', 'common'),
(8, 'necessary', '/ˈnesəsəri/', '必要的，必需的', 'It is necessary to study hard.', 'adjective', 'MEDIUM', 'common'),
(9, 'professional', '/prəˈfeʃənl/', '专业的，职业的', 'She has professional skills.', 'adjective', 'HARD', 'advanced'),
(10, 'communication', '/kəˌmjuːnɪˈkeɪʃn/', '交流，沟通', 'Communication is important in business.', 'noun', 'HARD', 'advanced');

-- 插入课文内容
INSERT IGNORE INTO texts (id, unit_id, title, content, difficulty, order_num) VALUES
-- Unit 1: Greetings
(1, 1, 'Hello World', 'Hello! My name is Tom. What is your name? I am from China. I am a student. Nice to meet you!', 'EASY', 1),
(2, 1, 'Good Morning', 'Good morning, everyone! This is our English class. How are you today? I am fine, thank you. And you?', 'EASY', 2),

-- Unit 2: Numbers
(3, 2, 'Counting Numbers', 'One, two, three, four, five. Six, seven, eight, nine, ten. I can count from one to ten. Can you?', 'EASY', 1),
(4, 2, 'What Time Is It?', 'Excuse me, what time is it? It is nine o''clock. Thank you. You are welcome.', 'EASY', 2),

-- Unit 3: Family
(5, 3, 'My Family', 'This is my family. My father is a doctor. My mother is a teacher. I have a brother and a sister. We are a happy family.', 'EASY', 1),
(6, 3, 'Family Members', 'Hello, I am Mary. This is my grandfather. That is my grandmother. They are very kind. I love my family.', 'EASY', 2),

-- 日常英语课程课文
(7, 4, 'My Daily Routine', 'I get up at seven o''clock every morning. I brush my teeth and wash my face. Then I have breakfast at eight o''clock. I go to school at nine o''clock.', 'MEDIUM', 1),
(8, 5, 'At the Restaurant', 'Waiter: Good evening. Welcome to our restaurant. Customer: Good evening. I would like to see the menu, please. Waiter: Here is the menu. What would you like to order?', 'MEDIUM', 1),

-- 商务英语课程课文
(9, 7, 'Business Meeting', 'Good morning, everyone. Thank you for coming to today''s meeting. Let''s start with our agenda. First, we will discuss the quarterly report. Then we will talk about our new project.', 'HARD', 1),
(10, 8, 'Presentation Skills', 'Good morning, ladies and gentlemen. Today I would like to talk about effective presentation skills. A good presentation should have three main parts: an introduction, a body, and a conclusion.', 'HARD', 1);

-- 插入单元单词关联
INSERT IGNORE INTO unit_words (unit_id, word_id, order_num) VALUES
-- Unit 1: Greetings 单词
(1, 1, 1), (1, 2, 2), (1, 3, 3),
-- Unit 2: Numbers 单词
(2, 1, 1), (2, 4, 2), (2, 5, 3),
-- Unit 3: Family 单词
(3, 1, 1), (3, 6, 2), (3, 2, 3),
-- 其他单元的单词关联...
(4, 6, 1), (4, 7, 2), (4, 2, 3),
(5, 7, 1), (5, 8, 2), (5, 2, 3),
(7, 9, 1), (7, 10, 2), (7, 6, 3),
(8, 9, 1), (8, 7, 2), (8, 10, 3);

-- 旧的词汇书数据已不再需要，因为vocabulary_books表现在是生词本条目表

-- 创建用户上传内容专用课程和单元
INSERT IGNORE INTO courses (id, name, description, status, created_time, updated_time)
VALUES (999, '用户上传内容', '存放所有普通用户上传的学习材料', 'ACTIVE', NOW(), NOW());

INSERT IGNORE INTO units (id, course_id, name, description, order_num, created_time, updated_time)
VALUES (999, 999, '用户上传单元', '存放所有普通用户上传的课文', 1, NOW(), NOW());

-- 创建示例班级
INSERT IGNORE INTO classes (id, name, description, course_id, teacher_id, status) VALUES
(1, '英语入门一班', '适合英语初学者的小班教学', 1, 2, 'ACTIVE'),
(2, '日常英语对话班', '提升日常英语交流能力', 2, 2, 'ACTIVE'),
(3, '商务英语提升班', '职场英语专业培训', 3, 2, 'ACTIVE');

-- 创建示例任务
INSERT IGNORE INTO assignments (id, title, description, class_id, unit_id, type, difficulty, status, created_by) VALUES
(1, 'Unit 1 Reading Practice', 'Please read the text "Hello World" and answer the questions.', 1, 1, 'READING', 'EASY', 'PUBLISHED', 2),
(2, 'Vocabulary Quiz', 'Learn the first 10 words from Unit 1.', 1, 1, 'VOCABULARY', 'EASY', 'PUBLISHED', 2),
(3, 'Daily Conversation', 'Practice the dialogues about daily routine.', 2, 4, 'COMPREHENSION', 'MEDIUM', 'PUBLISHED', 2);

-- 插入生词本示例数据
INSERT IGNORE INTO vocabulary_books (student_id, word_id, mastery_level, review_count) VALUES
(3, 1, 'LEARNING', 3),
(3, 2, 'FAMILIAR', 5),
(3, 3, 'LEARNING', 2);

-- 插入学习记录示例数据
INSERT IGNORE INTO learning_records (student_id, word_id, assignment_id, record_type, duration_seconds, created_time) VALUES
(3, 1, NULL, 'VIEW', 60, NOW()),
(3, NULL, 1, 'VIEW', 0, NOW()),
(3, 2, NULL, 'REVIEW', 120, NOW());

-- 创建课文习题表
CREATE TABLE IF NOT EXISTS text_exercises (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    text_id BIGINT NOT NULL COMMENT '课文ID',
    question TEXT NOT NULL COMMENT '题目内容',
    exercise_type ENUM('MULTIPLE_CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'SHORT_ANSWER') DEFAULT 'MULTIPLE_CHOICE' COMMENT '习题类型',
    order_num INT DEFAULT 1 COMMENT '显示顺序',
    created_by BIGINT NOT NULL COMMENT '创建者ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (text_id) REFERENCES texts(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_text_id (text_id),
    INDEX idx_exercise_type (exercise_type),
    INDEX idx_order_num (order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课文习题表';

-- 创建习题选项表（用于选择题）
CREATE TABLE IF NOT EXISTS exercise_options (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exercise_id BIGINT NOT NULL COMMENT '习题ID',
    option_content VARCHAR(500) NOT NULL COMMENT '选项内容',
    option_key VARCHAR(10) NOT NULL COMMENT '选项标识(A, B, C, D等)',
    is_correct BOOLEAN DEFAULT FALSE COMMENT '是否正确答案',
    order_num INT DEFAULT 1 COMMENT '显示顺序',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (exercise_id) REFERENCES text_exercises(id) ON DELETE CASCADE,
    INDEX idx_exercise_id (exercise_id),
    UNIQUE KEY uk_exercise_option (exercise_id, option_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='习题选项表';

-- 创建学生习题答题记录表
CREATE TABLE IF NOT EXISTS student_exercise_answers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exercise_id BIGINT NOT NULL COMMENT '习题ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    assignment_id BIGINT COMMENT '任务ID(可选)',
    answer_content TEXT COMMENT '学生答案',
    is_correct BOOLEAN COMMENT '是否正确',
    answer_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
    time_spent_seconds INT COMMENT '答题用时(秒)',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (exercise_id) REFERENCES text_exercises(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE SET NULL,
    INDEX idx_exercise_id (exercise_id),
    INDEX idx_student_id (student_id),
    INDEX idx_assignment_id (assignment_id),
    UNIQUE KEY uk_student_exercise (student_id, exercise_id, assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生习题答题记录表';

-- 添加外键约束检查
SET FOREIGN_KEY_CHECKS = 0;
SET FOREIGN_KEY_CHECKS = 1;

-- 输出初始化完成信息
SELECT 'Database initialization completed successfully!' as status;
SELECT 'Tables created:' as info;
SELECT COUNT(*) as table_count FROM information_schema.tables WHERE table_schema = 'english_learning';
SELECT 'Sample data inserted:' as info;
SELECT
  (SELECT COUNT(*) FROM users) as users,
  (SELECT COUNT(*) FROM courses) as courses,
  (SELECT COUNT(*) FROM units) as units,
  (SELECT COUNT(*) FROM texts) as texts,
  (SELECT COUNT(*) FROM words) as words;