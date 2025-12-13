-- ========================================
-- LangHack 英语学习平台 - 初始化数据
-- ========================================

SET NAMES utf8mb4;

-- ========================================
-- 初始化管理员账户 (密码: admin123)
-- 密码使用BCrypt加密
-- ========================================
INSERT INTO users (username, password, real_name, role, email) VALUES
('admin', '$2a$10$N.ZOn9G6/YLFixAOPMg/h.z7pCu6v2XyFDtC4q.jeeGm/TEZyj15C', '系统管理员', 'ADMIN', 'admin@langhack.com')
ON DUPLICATE KEY UPDATE username = username;

-- ========================================
-- 初始化四六级知识点体系
-- ========================================

-- 一级分类
INSERT INTO knowledge_points (name, code, category, exam_type, level, importance, description) VALUES
('词汇基础', 'CET_VOCAB', '词汇', 'CET', 1, 90, '四六级核心词汇与固定搭配'),
('语法基础', 'CET_GRAMMAR', '语法', 'CET', 1, 85, '时态、从句、非谓语等语法知识'),
('听力技巧', 'CET_LISTENING', '听力', 'CET', 1, 95, '听力场景词汇与解题技巧'),
('阅读理解', 'CET_READING', '阅读', 'CET', 1, 95, '阅读定位、理解与推断能力'),
('写作翻译', 'CET_WRITING', '写作', 'CET', 1, 80, '写作框架与翻译技巧')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 二级分类 - 词汇类
INSERT INTO knowledge_points (name, code, category, exam_type, level, parent_id, importance, description) VALUES
('核心词汇', 'CET_VOCAB_CORE', '词汇', 'CET', 2, 1, 95, '四六级高频核心词汇'),
('固定搭配', 'CET_VOCAB_COLLOCATION', '词汇', 'CET', 2, 1, 85, '常见固定搭配与短语'),
('熟词僻义', 'CET_VOCAB_POLYSEMY', '词汇', 'CET', 2, 1, 75, '熟悉单词的生僻含义'),
('同义近义词', 'CET_VOCAB_SYNONYM', '词汇', 'CET', 2, 1, 70, '同义词辨析与替换')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 二级分类 - 语法类
INSERT INTO knowledge_points (name, code, category, exam_type, level, parent_id, importance, description) VALUES
('时态语态', 'CET_GRAMMAR_TENSE', '语法', 'CET', 2, 2, 90, '时态和语态的正确使用'),
('从句结构', 'CET_GRAMMAR_CLAUSE', '语法', 'CET', 2, 2, 85, '名词性从句、定语从句、状语从句'),
('非谓语动词', 'CET_GRAMMAR_NON_FINITE', '语法', 'CET', 2, 2, 80, '不定式、动名词、分词的用法'),
('虚拟语气', 'CET_GRAMMAR_SUBJUNCTIVE', '语法', 'CET', 2, 2, 70, '虚拟语气的识别与运用'),
('倒装强调', 'CET_GRAMMAR_INVERSION', '语法', 'CET', 2, 2, 65, '倒装句与强调句型')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 二级分类 - 听力类
INSERT INTO knowledge_points (name, code, category, exam_type, level, parent_id, importance, description) VALUES
('数字信息', 'CET_LISTEN_NUMBER', '听力', 'CET', 2, 3, 85, '时间、日期、价格等数字信息捕捉'),
('场景词汇', 'CET_LISTEN_SCENE', '听力', 'CET', 2, 3, 95, '校园、旅游、工作等场景高频词汇'),
('语音现象', 'CET_LISTEN_PHONETICS', '听力', 'CET', 2, 3, 75, '连读、弱读、语音变化识别'),
('推理判断', 'CET_LISTEN_INFERENCE', '听力', 'CET', 2, 3, 70, '根据对话推断意图与态度'),
('笔记技巧', 'CET_LISTEN_NOTE', '听力', 'CET', 2, 3, 80, '听力笔记记录方法')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 二级分类 - 阅读类
INSERT INTO knowledge_points (name, code, category, exam_type, level, parent_id, importance, description) VALUES
('快速定位', 'CET_READ_LOCATION', '阅读', 'CET', 2, 4, 90, '题干关键词快速定位原文'),
('同义替换', 'CET_READ_PARAPHRASE', '阅读', 'CET', 2, 4, 95, '识别原文与选项的同义表达'),
('主旨大意', 'CET_READ_MAIN_IDEA', '阅读', 'CET', 2, 4, 85, '把握文章主旨与段落大意'),
('细节理解', 'CET_READ_DETAIL', '阅读', 'CET', 2, 4, 90, '准确理解文章细节信息'),
('推理判断', 'CET_READ_INFERENCE', '阅读', 'CET', 2, 4, 80, '根据文章内容合理推断'),
('词义猜测', 'CET_READ_WORD_GUESS', '阅读', 'CET', 2, 4, 70, '根据上下文猜测生词含义')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 二级分类 - 写作翻译类
INSERT INTO knowledge_points (name, code, category, exam_type, level, parent_id, importance, description) VALUES
('作文模板', 'CET_WRITE_TEMPLATE', '写作', 'CET', 2, 5, 85, '常用作文框架与模板'),
('连接词运用', 'CET_WRITE_CONNECTOR', '写作', 'CET', 2, 5, 90, '逻辑连接词的正确使用'),
('论证逻辑', 'CET_WRITE_ARGUMENT', '写作', 'CET', 2, 5, 80, '论点、论据的组织与展开'),
('翻译句型', 'CET_TRANS_PATTERN', '写作', 'CET', 2, 5, 85, '常见翻译句型与表达'),
('语法准确性', 'CET_WRITE_ACCURACY', '写作', 'CET', 2, 5, 75, '避免语法错误，提高准确性')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ========================================
-- 初始化示例课程
-- ========================================
INSERT INTO courses (name, description) VALUES
('大学英语四级精讲', '针对CET-4考试的系统性课程，涵盖听力、阅读、写作和翻译四个模块'),
('大学英语六级突破', '针对CET-6考试的强化课程，深入讲解高难度题型技巧')
ON DUPLICATE KEY UPDATE name = name;
