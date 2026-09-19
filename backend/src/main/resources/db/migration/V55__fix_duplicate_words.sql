-- V55__fix_duplicate_words.sql
-- 修复 words 表重复记录导致 getWordByWord selectOne() 报 TooManyResultsException

-- 1. 建临时映射表：重复 id -> 每个词保留的最小 id
DROP TEMPORARY TABLE IF EXISTS _word_dup_map;
CREATE TEMPORARY TABLE _word_dup_map AS
SELECT w.id AS old_id, keeper.min_id AS new_id
FROM words w
INNER JOIN (SELECT word, MIN(id) AS min_id FROM words WHERE deleted=0 GROUP BY word) keeper
  ON w.word = keeper.word
WHERE w.deleted=0 AND w.id != keeper.min_id;

-- 2. vocabulary_books：先删冲突记录（学生已有正确 word_id），再迁移剩余
DELETE vb FROM vocabulary_books vb
INNER JOIN _word_dup_map m ON vb.word_id = m.old_id
WHERE EXISTS (
  SELECT 1 FROM (SELECT student_id, word_id FROM vocabulary_books) vb2
  WHERE vb2.student_id = vb.student_id AND vb2.word_id = m.new_id
);

UPDATE vocabulary_books vb
INNER JOIN _word_dup_map m ON vb.word_id = m.old_id
SET vb.word_id = m.new_id;

-- 3. learning_records：迁移引用
UPDATE learning_records lr
INNER JOIN _word_dup_map m ON lr.word_id = m.old_id
SET lr.word_id = m.new_id;

-- 4. 软删除重复的 word 记录
UPDATE words w
INNER JOIN _word_dup_map m ON w.id = m.old_id
SET w.deleted = 1;

DROP TEMPORARY TABLE _word_dup_map;

-- 5. 防止再次出现重复：用虚拟生成列实现条件唯一索引
--    active_word: deleted=0 时为 word 值，deleted!=0 时为 NULL（NULL 不参与唯一约束）
ALTER TABLE words ADD COLUMN active_word VARCHAR(100) AS (IF(deleted=0, word, NULL)) STORED;
ALTER TABLE words ADD UNIQUE INDEX uk_active_word (active_word);
