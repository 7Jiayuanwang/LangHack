-- V55__fix_duplicate_words.sql
-- 修复 words 表重复记录导致 getWordByWord selectOne() 报 TooManyResultsException

-- 1. 建映射表：重复 id -> 每个词保留的最小 id
CREATE TABLE IF NOT EXISTS _word_dup_map (
  old_id BIGINT NOT NULL,
  new_id BIGINT NOT NULL,
  PRIMARY KEY (old_id)
);

-- V55 may be interrupted while deleting a large vocabulary_books table.
-- Clear a partial mapping so a retry starts from a consistent snapshot.
TRUNCATE TABLE _word_dup_map;

INSERT INTO _word_dup_map (old_id, new_id)
SELECT w.id, keeper.min_id
FROM words w
INNER JOIN (SELECT word, MIN(id) AS min_id FROM words WHERE deleted=0 GROUP BY word HAVING COUNT(*)>1) keeper
  ON w.word = keeper.word
WHERE w.deleted=0 AND w.id != keeper.min_id;

-- 2. vocabulary_books：多个旧词可能同时映射到同一个新词。
--    先为每个 (student_id, new_word_id) 选一条记录，删除其余记录，
--    再迁移保留记录，避免 uk_student_word 冲突。
CREATE TEMPORARY TABLE _v55_vocab_keep (
  keep_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO _v55_vocab_keep (keep_id)
SELECT COALESCE(
         MIN(CASE WHEN m.old_id IS NULL THEN vb.id END),
         MIN(vb.id)
       ) AS keep_id
FROM vocabulary_books vb
LEFT JOIN _word_dup_map m ON m.old_id = vb.word_id
GROUP BY vb.student_id, COALESCE(m.new_id, vb.word_id);

DELETE vb
FROM vocabulary_books vb
LEFT JOIN _word_dup_map m ON m.old_id = vb.word_id
LEFT JOIN _v55_vocab_keep keep_row ON keep_row.keep_id = vb.id
WHERE keep_row.keep_id IS NULL;

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

-- 5. 清理映射表
DROP TEMPORARY TABLE _v55_vocab_keep;
DROP TABLE _word_dup_map;
