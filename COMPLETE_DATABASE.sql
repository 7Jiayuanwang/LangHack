-- 英语学习平台数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS english_learning DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE english_learning;

-- 用户表
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    email VARCHAR(100) COMMENT '邮箱',
    role ENUM('STUDENT', 'TEACHER', 'ADMIN') NOT NULL COMMENT '角色',
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
CREATE TABLE courses (
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
CREATE TABLE units (
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
CREATE TABLE texts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    unit_id BIGINT NOT NULL COMMENT '单元ID',
    title VARCHAR(200) NOT NULL COMMENT '课文标题',
    content TEXT NOT NULL COMMENT '课文内容',
    audio_url VARCHAR(255) COMMENT '音频URL',
    difficulty ENUM('EASY', 'MEDIUM', 'HARD') DEFAULT 'MEDIUM' COMMENT '难度',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    INDEX idx_unit_id (unit_id),
    INDEX idx_difficulty (difficulty),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课文表';

-- 单词表
CREATE TABLE words (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    word VARCHAR(100) NOT NULL COMMENT '单词',
    pronunciation VARCHAR(100) COMMENT '音标',
    definition TEXT NOT NULL COMMENT '词义',
    example_sentence TEXT COMMENT '例句',
    audio_url VARCHAR(255) COMMENT '音频URL',
    part_of_speech VARCHAR(50) COMMENT '词性',
    difficulty ENUM('EASY', 'MEDIUM', 'HARD') DEFAULT 'MEDIUM' COMMENT '难度',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_word (word),
    INDEX idx_difficulty (difficulty),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词表';

-- 单元单词关联表
CREATE TABLE unit_words (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    unit_id BIGINT NOT NULL COMMENT '单元ID',
    word_id BIGINT NOT NULL COMMENT '单词ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE,
    UNIQUE KEY uk_unit_word (unit_id, word_id),
    INDEX idx_unit_id (unit_id),
    INDEX idx_word_id (word_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单元单词关联表';

-- 班级表
CREATE TABLE classes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '班级名称',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    description TEXT COMMENT '班级描述',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (course_id) REFERENCES courses(id),
    FOREIGN KEY (teacher_id) REFERENCES users(id),
    INDEX idx_course_id (course_id),
    INDEX idx_teacher_id (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级表';

-- 学生班级关联表
CREATE TABLE student_classes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE,
    UNIQUE KEY uk_student_class (student_id, class_id),
    INDEX idx_student_id (student_id),
    INDEX idx_class_id (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生班级关联表';

-- 学习任务表
CREATE TABLE assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    class_id BIGINT NOT NULL COMMENT '班级ID',
    unit_id BIGINT NOT NULL COMMENT '单元ID',
    title VARCHAR(200) NOT NULL COMMENT '任务标题',
    description TEXT COMMENT '任务描述',
    deadline DATETIME COMMENT '截止时间',
    created_by BIGINT NOT NULL COMMENT '创建者ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE,
    FOREIGN KEY (unit_id) REFERENCES units(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_class_id (class_id),
    INDEX idx_unit_id (unit_id),
    INDEX idx_deadline (deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习任务表';

-- 生词本表
CREATE TABLE vocabulary_books (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    word_id BIGINT NOT NULL COMMENT '单词ID',
    added_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    review_count INT DEFAULT 0 COMMENT '复习次数',
    last_reviewed DATETIME COMMENT '最后复习时间',
    mastery_level ENUM('NEW', 'LEARNING', 'MASTERED') DEFAULT 'NEW' COMMENT '掌握程度',
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE,
    UNIQUE KEY uk_student_word (student_id, word_id),
    INDEX idx_student_id (student_id),
    INDEX idx_mastery_level (mastery_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生词本表';

-- 学习记录表
CREATE TABLE learning_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '学生ID',
    word_id BIGINT NOT NULL COMMENT '单词ID',
    assignment_id BIGINT COMMENT '任务ID',
    record_type ENUM('VIEW', 'REVIEW', 'TEST') NOT NULL COMMENT '记录类型',
    duration_seconds INT DEFAULT 0 COMMENT '学习时长(秒)',
    correct_count INT DEFAULT 0 COMMENT '正确次数',
    total_count INT DEFAULT 0 COMMENT '总次数',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE,
    FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE SET NULL,
    INDEX idx_student_id (student_id),
    INDEX idx_word_id (word_id),
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_record_type (record_type),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习记录表';

-- 插入默认管理员用户 (密码: admin123)
INSERT INTO users (username, password, email, role, real_name) VALUES
('admin', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'admin@example.com', 'ADMIN', '系统管理员');

-- 插入示例数据
INSERT INTO courses (name, description, created_by) VALUES
('新概念英语第一册', '适合英语初学者的经典教材', 1),
('大学英语四级', '大学英语四级考试备考课程', 1);

INSERT INTO units (course_id, name, description, order_num) VALUES
(1, '第一单元', '基础问候语', 1),
(1, '第二单元', '基本句型', 2),
(2, '第一单元', '听力理解', 1),
(2, '第二单元', '阅读理解', 2);

-- 插入示例单词
INSERT INTO words (word, pronunciation, definition, example_sentence, part_of_speech, difficulty) VALUES
('hello', '/həˈləʊ/', '你好；喂', 'Hello, how are you?', 'interjection', 'EASY'),
('world', '/wɜːld/', '世界；地球', 'Welcome to the world of English.', 'noun', 'EASY'),
('beautiful', '/ˈbjuːtɪfl/', '美丽的；漂亮的', 'She is a beautiful girl.', 'adjective', 'MEDIUM'),
('difficult', '/ˈdɪfɪkəlt/', '困难的；艰难的', 'This question is difficult.', 'adjective', 'MEDIUM'),
('achievement', '/əˈtʃiːvmənt/', '成就；功绩', 'Graduating is a great achievement.', 'noun', 'HARD');

-- 为单元添加单词
INSERT INTO unit_words (unit_id, word_id) VALUES
(1, 1), (1, 2),
(2, 3), (2, 4), (2, 5);

-- 插入示例课文
INSERT INTO texts (unit_id, title, content, difficulty) VALUES
(1, 'Greetings', 'Hello! My name is Tom. What is your name? Nice to meet you!', 'EASY'),
(2, 'Basic Sentences', 'This is a book. That is a pen. I am a student. You are a teacher.', 'EASY'),
(3, 'Listening Practice', 'Listen carefully and answer the questions about the conversation.', 'MEDIUM'),
(4, 'Reading Comprehension', 'Read the passage and choose the best answer for each question.', 'MEDIUM');