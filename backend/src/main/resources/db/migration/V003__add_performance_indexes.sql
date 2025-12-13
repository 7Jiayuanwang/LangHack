-- 性能优化索引
-- 添加索引以优化查询性能

-- 答题记录表索引
-- 优化学生答题统计查询
CREATE INDEX IF NOT EXISTS idx_answer_record_student ON answer_records(student_id, is_correct);

-- 优化按时间查询答题记录
CREATE INDEX IF NOT EXISTS idx_answer_record_time ON answer_records(answer_time);

-- 优化学生答题历史查询
CREATE INDEX IF NOT EXISTS idx_answer_record_student_time ON answer_records(student_id, answer_time DESC);

-- 题目表索引
-- 优化按难度查询
CREATE INDEX IF NOT EXISTS idx_question_difficulty ON questions(difficulty, deleted);

-- 优化按题型查询
CREATE INDEX IF NOT EXISTS idx_question_type ON questions(question_type, deleted);

-- 优化按考试类型查询
CREATE INDEX IF NOT EXISTS idx_question_exam_type ON questions(exam_type, deleted);

-- 单词表索引
-- 优化单词搜索
CREATE INDEX IF NOT EXISTS idx_word_word ON words(word, deleted);

-- 优化按难度查询
CREATE INDEX IF NOT EXISTS idx_word_difficulty ON words(difficulty, deleted);

-- 用户表索引
-- 优化按角色查询
CREATE INDEX IF NOT EXISTS idx_user_role ON users(role, deleted);

-- 优化按注册时间查询
CREATE INDEX IF NOT EXISTS idx_user_created_time ON users(created_time DESC, deleted);
