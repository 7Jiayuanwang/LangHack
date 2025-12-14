-- ========================================
-- LangHack 英语学习平台 - 数据库初始化脚本
-- ========================================

-- 设置字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ========================================
-- 1. 用户表
-- ========================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码（加密存储）',
    real_name VARCHAR(50) COMMENT '真实姓名',
    nickname VARCHAR(50) COMMENT '昵称',
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT' COMMENT '角色（ADMIN/TEACHER/STUDENT）',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    avatar VARCHAR(255) COMMENT '头像URL',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '用户状态（ACTIVE/INACTIVE/BANNED）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常, 1=已删除）',
    INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ========================================
-- 2. 课程表
-- ========================================
CREATE TABLE IF NOT EXISTS courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '课程ID',
    name VARCHAR(100) NOT NULL COMMENT '课程名称',
    description TEXT COMMENT '课程描述',
    cover_image VARCHAR(255) COMMENT '封面图片URL',
    teacher_id BIGINT COMMENT '教师ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- ========================================
-- 3. 单元表
-- ========================================
CREATE TABLE IF NOT EXISTS units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '单元ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    name VARCHAR(100) NOT NULL COMMENT '单元名称',
    description TEXT COMMENT '单元描述',
    order_num INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_course (course_id),
    INDEX idx_order (order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单元表';

-- ========================================
-- 4. 课文表
-- ========================================
CREATE TABLE IF NOT EXISTS texts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '课文ID',
    unit_id BIGINT NOT NULL COMMENT '单元ID',
    title VARCHAR(200) NOT NULL COMMENT '课文标题',
    content TEXT NOT NULL COMMENT '课文内容',
    translation TEXT COMMENT '翻译',
    audio_url VARCHAR(255) COMMENT '音频URL',
    order_num INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_unit (unit_id),
    INDEX idx_order (order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课文表';

-- ========================================
-- 5. 单词表
-- ========================================
CREATE TABLE IF NOT EXISTS words (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '单词ID',
    word VARCHAR(100) NOT NULL COMMENT '单词',
    phonetic VARCHAR(100) COMMENT '音标',
    meaning TEXT NOT NULL COMMENT '释义',
    example_sentence TEXT COMMENT '例句',
    example_translation TEXT COMMENT '例句翻译',
    audio_url VARCHAR(255) COMMENT '音频URL',
    difficulty INT NOT NULL DEFAULT 1 COMMENT '难度（1-5）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_word (word),
    INDEX idx_difficulty (difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词表';

-- ========================================
-- 6. 单元-单词关联表
-- ========================================
CREATE TABLE IF NOT EXISTS unit_words (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    unit_id BIGINT NOT NULL COMMENT '单元ID',
    word_id BIGINT NOT NULL COMMENT '单词ID',
    order_num INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_unit_word (unit_id, word_id),
    INDEX idx_unit (unit_id),
    INDEX idx_word (word_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单元-单词关联表';

-- ========================================
-- 7. 班级表
-- ========================================
CREATE TABLE IF NOT EXISTS classes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '班级ID',
    name VARCHAR(100) NOT NULL COMMENT '班级名称',
    description TEXT COMMENT '班级描述',
    teacher_id BIGINT NOT NULL COMMENT '教师ID',
    course_id BIGINT COMMENT '关联课程ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_teacher (teacher_id),
    INDEX idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级表';

-- ========================================
-- 8. 学生-班级关联表
-- ========================================
CREATE TABLE IF NOT EXISTS student_classes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    join_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY uk_student_class (student_id, class_id),
    INDEX idx_student (student_id),
    INDEX idx_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生-班级关联表';

-- ========================================
-- 9. 学习任务表
-- ========================================
CREATE TABLE IF NOT EXISTS assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    class_id BIGINT NOT NULL COMMENT '班级ID',
    title VARCHAR(200) NOT NULL COMMENT '任务标题',
    description TEXT COMMENT '任务描述',
    unit_id BIGINT COMMENT '关联单元ID',
    type VARCHAR(50) NOT NULL COMMENT '任务类型（TEXT_READING/WORD_LEARNING/PRACTICE）',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '截止时间',
    created_by BIGINT NOT NULL COMMENT '创建者ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_class (class_id),
    INDEX idx_unit (unit_id),
    INDEX idx_time (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习任务表';

-- ========================================
-- 10. 生词本表
-- ========================================
CREATE TABLE IF NOT EXISTS vocabulary_books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '生词本ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    word_id BIGINT NOT NULL COMMENT '单词ID',
    mastery_level INT NOT NULL DEFAULT 0 COMMENT '掌握程度（0-5）',
    review_count INT NOT NULL DEFAULT 0 COMMENT '复习次数',
    last_review_time DATETIME COMMENT '最后复习时间',
    next_review_time DATETIME COMMENT '下次复习时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    UNIQUE KEY uk_student_word (student_id, word_id),
    INDEX idx_student (student_id),
    INDEX idx_word (word_id),
    INDEX idx_next_review (next_review_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生词本表';

-- ========================================
-- 11. 学习记录表
-- ========================================
CREATE TABLE IF NOT EXISTS learning_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    content_type VARCHAR(50) NOT NULL COMMENT '内容类型（TEXT/WORD/UNIT）',
    content_id BIGINT NOT NULL COMMENT '内容ID',
    duration INT NOT NULL DEFAULT 0 COMMENT '学习时长（秒）',
    progress INT NOT NULL DEFAULT 0 COMMENT '学习进度（0-100）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    INDEX idx_student (student_id),
    INDEX idx_content (content_type, content_id),
    INDEX idx_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习记录表';

-- ========================================
-- 12. 知识点表
-- ========================================
CREATE TABLE IF NOT EXISTS knowledge_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识点ID',
    name VARCHAR(100) NOT NULL COMMENT '知识点名称',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '知识点编码',
    category VARCHAR(50) NOT NULL COMMENT '知识点分类（词汇、语法、听力、阅读、写作）',
    exam_type VARCHAR(20) NOT NULL DEFAULT 'CET' COMMENT '考试类型（CET=四六级, POSTGRADUATE=考研, IELTS=雅思）',
    level INT NOT NULL DEFAULT 1 COMMENT '层级（1=一级分类, 2=二级分类）',
    parent_id BIGINT DEFAULT NULL COMMENT '父级知识点ID',
    description TEXT COMMENT '知识点描述',
    importance INT NOT NULL DEFAULT 50 COMMENT '重要程度（1-100）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_exam_type (exam_type),
    INDEX idx_category (category),
    INDEX idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点表';

-- ========================================
-- 13. 题目表
-- ========================================
CREATE TABLE IF NOT EXISTS questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '题目ID',
    title TEXT NOT NULL COMMENT '题目标题/题干',
    question_type VARCHAR(50) NOT NULL COMMENT '题型（听力、阅读、完形填空、翻译、写作）',
    difficulty VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '难度（EASY, MEDIUM, HARD）',
    exam_type VARCHAR(20) NOT NULL DEFAULT 'CET' COMMENT '考试类型',
    exam_level VARCHAR(10) COMMENT '考试级别（CET4, CET6）',
    options JSON COMMENT '选项（ABCD选项，JSON格式）',
    correct_answer VARCHAR(500) NOT NULL COMMENT '正确答案',
    explanation TEXT COMMENT '答案解析',
    knowledge_point_ids VARCHAR(500) COMMENT '关联知识点ID列表（逗号分隔）',
    score INT NOT NULL DEFAULT 1 COMMENT '分值',
    created_by BIGINT COMMENT '创建者ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常, 1=已删除）',
    INDEX idx_type (question_type),
    INDEX idx_exam (exam_type, exam_level),
    INDEX idx_difficulty (difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目表';

-- ========================================
-- 14. 答题记录表
-- ========================================
CREATE TABLE IF NOT EXISTS answer_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '答题记录ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    student_answer VARCHAR(500) COMMENT '学生答案',
    is_correct TINYINT NOT NULL COMMENT '是否正确（0=错误, 1=正确）',
    time_spent INT COMMENT '答题耗时（秒）',
    answer_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
    error_type VARCHAR(50) COMMENT '错误类型（knowledge=知识性错误, method=方法性错误, careless=失误性错误）',
    INDEX idx_student (student_id),
    INDEX idx_question (question_id),
    INDEX idx_correct (is_correct),
    INDEX idx_time (answer_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录表';

-- ========================================
-- 15. 薄弱点分析结果表
-- ========================================
CREATE TABLE IF NOT EXISTS weakness_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分析记录ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    knowledge_point_id BIGINT NOT NULL COMMENT '知识点ID',
    total_questions INT NOT NULL DEFAULT 0 COMMENT '该知识点总题数',
    correct_count INT NOT NULL DEFAULT 0 COMMENT '答对题数',
    wrong_count INT NOT NULL DEFAULT 0 COMMENT '答错题数',
    accuracy_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '正确率（%）',
    weakness_level VARCHAR(20) NOT NULL COMMENT '薄弱程度（SEVERE=严重, MODERATE=中度, MILD=轻度, GOOD=良好）',
    last_practice_time DATETIME COMMENT '最后练习时间',
    analysis_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分析时间',
    INDEX idx_student_kp (student_id, knowledge_point_id),
    INDEX idx_weakness (weakness_level),
    UNIQUE KEY uk_student_kp (student_id, knowledge_point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='薄弱点分析结果表';

-- ========================================
-- 16. 学习建议表
-- ========================================
CREATE TABLE IF NOT EXISTS learning_suggestions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '建议ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    suggestion_type VARCHAR(50) NOT NULL COMMENT '建议类型（weakness_focus=薄弱点重点攻克, practice_plan=练习计划, study_method=学习方法）',
    content TEXT NOT NULL COMMENT '建议内容',
    knowledge_point_ids VARCHAR(500) COMMENT '关联知识点ID列表',
    priority INT NOT NULL DEFAULT 50 COMMENT '优先级（1-100）',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING=待执行, IN_PROGRESS=进行中, COMPLETED=已完成）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    completed_time DATETIME COMMENT '完成时间',
    INDEX idx_student (student_id),
    INDEX idx_status (status),
    INDEX idx_priority (priority DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习建议表';

SET FOREIGN_KEY_CHECKS = 1;
