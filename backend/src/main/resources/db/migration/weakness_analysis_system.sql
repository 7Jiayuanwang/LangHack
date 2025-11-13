-- ========================================
-- 薄弱点分析系统数据库表
-- ========================================

-- 1. 知识点表
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

-- 2. 题目表
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

-- 3. 答题记录表
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
    INDEX idx_time (answer_time),
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录表';

-- 4. 薄弱点分析结果表
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
    UNIQUE KEY uk_student_kp (student_id, knowledge_point_id),
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='薄弱点分析结果表';

-- 5. 学习建议表
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

-- ========================================
-- 初始化四六级知识点体系
-- ========================================

-- 一级分类
INSERT INTO knowledge_points (name, code, category, exam_type, level, importance, description) VALUES
-- 词汇类
('词汇基础', 'CET_VOCAB', '词汇', 'CET', 1, 90, '四六级核心词汇与固定搭配'),
('语法基础', 'CET_GRAMMAR', '语法', 'CET', 1, 85, '时态、从句、非谓语等语法知识'),
('听力技巧', 'CET_LISTENING', '听力', 'CET', 1, 95, '听力场景词汇与解题技巧'),
('阅读理解', 'CET_READING', '阅读', 'CET', 1, 95, '阅读定位、理解与推断能力'),
('写作翻译', 'CET_WRITING', '写作', 'CET', 1, 80, '写作框架与翻译技巧');

-- 二级分类 - 词汇类
INSERT INTO knowledge_points (name, code, category, exam_type, level, parent_id, importance, description) VALUES
('核心词汇', 'CET_VOCAB_CORE', '词汇', 'CET', 2, 1, 95, '四六级高频核心词汇'),
('固定搭配', 'CET_VOCAB_COLLOCATION', '词汇', 'CET', 2, 1, 85, '常见固定搭配与短语'),
('熟词僻义', 'CET_VOCAB_POLYSEMY', '词汇', 'CET', 2, 1, 75, '熟悉单词的生僻含义'),
('同义近义词', 'CET_VOCAB_SYNONYM', '词汇', 'CET', 2, 1, 70, '同义词辨析与替换');

-- 二级分类 - 语法类
INSERT INTO knowledge_points (name, code, category, exam_type, level, parent_id, importance, description) VALUES
('时态语态', 'CET_GRAMMAR_TENSE', '语法', 'CET', 2, 2, 90, '时态和语态的正确使用'),
('从句结构', 'CET_GRAMMAR_CLAUSE', '语法', 'CET', 2, 2, 85, '名词性从句、定语从句、状语从句'),
('非谓语动词', 'CET_GRAMMAR_NON_FINITE', '语法', 'CET', 2, 2, 80, '不定式、动名词、分词的用法'),
('虚拟语气', 'CET_GRAMMAR_SUBJUNCTIVE', '语法', 'CET', 2, 2, 70, '虚拟语气的识别与运用'),
('倒装强调', 'CET_GRAMMAR_INVERSION', '语法', 'CET', 2, 2, 65, '倒装句与强调句型');

-- 二级分类 - 听力类
INSERT INTO knowledge_points (name, code, category, exam_type, level, parent_id, importance, description) VALUES
('数字信息', 'CET_LISTEN_NUMBER', '听力', 'CET', 2, 3, 85, '时间、日期、价格等数字信息捕捉'),
('场景词汇', 'CET_LISTEN_SCENE', '听力', 'CET', 2, 3, 95, '校园、旅游、工作等场景高频词汇'),
('语音现象', 'CET_LISTEN_PHONETICS', '听力', 'CET', 2, 3, 75, '连读、弱读、语音变化识别'),
('推理判断', 'CET_LISTEN_INFERENCE', '听力', 'CET', 2, 3, 70, '根据对话推断意图与态度'),
('笔记技巧', 'CET_LISTEN_NOTE', '听力', 'CET', 2, 3, 80, '听力笔记记录方法');

-- 二级分类 - 阅读类
INSERT INTO knowledge_points (name, code, category, exam_type, level, parent_id, importance, description) VALUES
('快速定位', 'CET_READ_LOCATION', '阅读', 'CET', 2, 4, 90, '题干关键词快速定位原文'),
('同义替换', 'CET_READ_PARAPHRASE', '阅读', 'CET', 2, 4, 95, '识别原文与选项的同义表达'),
('主旨大意', 'CET_READ_MAIN_IDEA', '阅读', 'CET', 2, 4, 85, '把握文章主旨与段落大意'),
('细节理解', 'CET_READ_DETAIL', '阅读', 'CET', 2, 4, 90, '准确理解文章细节信息'),
('推理判断', 'CET_READ_INFERENCE', '阅读', 'CET', 2, 4, 80, '根据文章内容合理推断'),
('词义猜测', 'CET_READ_WORD_GUESS', '阅读', 'CET', 2, 4, 70, '根据上下文猜测生词含义');

-- 二级分类 - 写作翻译类
INSERT INTO knowledge_points (name, code, category, exam_type, level, parent_id, importance, description) VALUES
('作文模板', 'CET_WRITE_TEMPLATE', '写作', 'CET', 2, 5, 85, '常用作文框架与模板'),
('连接词运用', 'CET_WRITE_CONNECTOR', '写作', 'CET', 2, 5, 90, '逻辑连接词的正确使用'),
('论证逻辑', 'CET_WRITE_ARGUMENT', '写作', 'CET', 2, 5, 80, '论点、论据的组织与展开'),
('翻译句型', 'CET_TRANS_PATTERN', '写作', 'CET', 2, 5, 85, '常见翻译句型与表达'),
('语法准确性', 'CET_WRITE_ACCURACY', '写作', 'CET', 2, 5, 75, '避免语法错误，提高准确性');

-- ========================================
-- 创建视图：学生薄弱点统计
-- ========================================
CREATE OR REPLACE VIEW v_student_weakness_stats AS
SELECT
    wa.student_id,
    u.username,
    u.real_name,
    kp.id AS knowledge_point_id,
    kp.name AS knowledge_point_name,
    kp.category,
    wa.total_questions,
    wa.correct_count,
    wa.wrong_count,
    wa.accuracy_rate,
    wa.weakness_level,
    wa.last_practice_time,
    CASE
        WHEN wa.weakness_level = 'SEVERE' THEN 4
        WHEN wa.weakness_level = 'MODERATE' THEN 3
        WHEN wa.weakness_level = 'MILD' THEN 2
        ELSE 1
    END AS weakness_priority
FROM weakness_analysis wa
JOIN users u ON wa.student_id = u.id
JOIN knowledge_points kp ON wa.knowledge_point_id = kp.id
WHERE u.role = 'STUDENT' AND u.deleted = 0;
