-- ============================================================
-- V60__fix_past_paper_data.sql
--
-- 用途：修正真题（past_papers）相关历史数据。纯数据修正，不改表结构，幂等，可重复执行。
--
-- 背景（为什么必须同时改列和 content_json）：
--   1. past_papers 的 category / duration_minutes 存在「两份真相源」——
--      表列 + content_json 里的同名字段。后端 PastPaperController.toPayload()
--      是把 content_json 整份返回给前端的，前端读取的是 JSON 里的值，
--      只改列界面不会有任何变化，因此两者必须一起修正。
--   2. past_papers.paper_key 是导入时生成的随机 UUID，各环境不同；
--      本脚本一律按 title / 内容特征匹配，不依赖 id、不依赖 paper_key。
--   3. 试卷数据本身来自 scripts/past-papers/convert-exam.mjs 的导入，
--      导入侧已同步修正（缺听力卷自动扣减时长），本脚本负责让已导入的环境对齐。
--
-- 修正内容：
--   A. 分类归一：标题写明的考试等级与 category 不一致时以标题为准（历史导入错标）。
--   B. 时长归一：14 题的 CET4/CET6 卷不含听力（源资料未提供），原按整套时长
--      导入（CET4 125 / CET6 130），应为扣掉听力后的 100 分钟；39 题整卷
--      按 CET4 125 / CET6 130。
--   C. content_json 内的 category / duration 与列对齐；无听力卷补 scope 说明。
--   D. 重复导入的试卷去重：title 相同且内容一致（仅 id/category 值不同）时保留
--      id 最小的一条，且跳过已被学生作答引用的行。
--   E. 学生作答记录（past_paper_attempts）的 category 与试卷保持一致。
-- ============================================================

-- ------------------------------------------------------------
-- A. 分类归一（以标题里的考试名称为准）
-- ------------------------------------------------------------
UPDATE past_papers
SET category = 'cet4'
WHERE category <> 'cet4'
  AND title LIKE '%四级%';

UPDATE past_papers
SET category = 'cet6'
WHERE category <> 'cet6'
  AND title LIKE '%六级%';

UPDATE past_papers
SET category = 'postgraduate'
WHERE category <> 'postgraduate'
  AND title LIKE '%考研%';

-- ------------------------------------------------------------
-- B. 时长归一
--    14 题 = 写作 + 选词 + 匹配 + 10 题仔细阅读 + 翻译，没有听力，
--    所以时长应为扣掉听力时段后的 100 分钟（CET4 125-25 / CET6 130-30）。
-- ------------------------------------------------------------
UPDATE past_papers
SET duration_minutes = 100,
    content_json = JSON_SET(content_json, '$.duration', 100)
WHERE category IN ('cet4', 'cet6')
  AND question_count = 14
  AND (duration_minutes <> 100
       OR JSON_UNQUOTE(JSON_EXTRACT(content_json, '$.duration')) <> '100');

UPDATE past_papers
SET duration_minutes = 125,
    content_json = JSON_SET(content_json, '$.duration', 125)
WHERE category = 'cet4'
  AND question_count = 39
  AND duration_minutes <> 125;

UPDATE past_papers
SET duration_minutes = 130,
    content_json = JSON_SET(content_json, '$.duration', 130)
WHERE category = 'cet6'
  AND question_count = 39
  AND duration_minutes <> 130;

-- ------------------------------------------------------------
-- C. content_json 与列对齐
-- ------------------------------------------------------------
-- C1. JSON 里的 duration 与列一致
UPDATE past_papers
SET content_json = JSON_SET(content_json, '$.duration', duration_minutes)
WHERE JSON_UNQUOTE(JSON_EXTRACT(content_json, '$.duration')) <> CAST(duration_minutes AS CHAR);

-- C2. JSON 里的 category 与列一致
UPDATE past_papers
SET content_json = JSON_SET(content_json, '$.category', category)
WHERE JSON_UNQUOTE(JSON_EXTRACT(content_json, '$.category')) <> category;

-- C3. 无听力卷补 scope 说明（卡片上用于解释时长为何更短；未填写的才补，不覆盖已有文案）
UPDATE past_papers
SET content_json = JSON_SET(content_json, '$.scope', '整套试卷（本套资料未提供听力题）')
WHERE category IN ('cet4', 'cet6')
  AND question_count = 14
  AND (JSON_EXTRACT(content_json, '$.scope') IS NULL
       OR JSON_UNQUOTE(JSON_EXTRACT(content_json, '$.scope')) = '');

-- ------------------------------------------------------------
-- D. 重复导入去重
--    同一份卷被重复导入时，只有 content_json 里的 id（卷 id + 每道题的派生 id，
--    形如 custom-<时间戳>-<随机串>-q-N）和 category 不同，正文完全一致；
--    因此先抹掉所有 id / category 的值再取哈希作为「内容签名」。
--    注：必须先 CAST(... AS JSON) 让 MySQL 归一化 JSON 文本（不同批次导入的
--    原始文本缩进/键顺序可能不同），再套 COLLATE 统一排序规则——
--    CAST(... AS JSON) 的结果排序规则是 utf8mb4_0900_ai_ci，与表的
--    utf8mb4_unicode_ci 直接比较会报 ERROR 1267。
--    - 长度预筛：重复行正文一致，长度必然接近（只差 id 串），用来避免对所有行做
--      代价高的正则+哈希比对；容差取 1%，因为列/JSON 被 JSON_SET 规范化过的行
--      与原始文本行会相差几千字节；
--    - 保留 id 最小的一条；
--    - 被 past_paper_attempts 引用（按 past_paper_id 或 paper_key）的行一律不删。
-- ------------------------------------------------------------
DELETE p
FROM past_papers p
JOIN past_papers k
  ON k.title = p.title
 AND k.id < p.id
 AND k.question_count = p.question_count
 AND ABS(LENGTH(k.content_json) - LENGTH(p.content_json)) <= 1 + LENGTH(p.content_json) DIV 100
 AND MD5(REGEXP_REPLACE(REGEXP_REPLACE(CAST(k.content_json AS JSON) COLLATE utf8mb4_unicode_ci,
        '"id"[[:space:]]*:[[:space:]]*"[^"]*"', '"id":""'),
        '"category"[[:space:]]*:[[:space:]]*"[^"]*"', '"category":""'))
   = MD5(REGEXP_REPLACE(REGEXP_REPLACE(CAST(p.content_json AS JSON) COLLATE utf8mb4_unicode_ci,
        '"id"[[:space:]]*:[[:space:]]*"[^"]*"', '"id":""'),
        '"category"[[:space:]]*:[[:space:]]*"[^"]*"', '"category":""'))
WHERE NOT EXISTS (
    SELECT 1 FROM past_paper_attempts a
    WHERE a.past_paper_id = p.id
       -- past_paper_attempts 的建表排序规则是 utf8mb4_0900_ai_ci，与 past_papers 的
       -- utf8mb4_unicode_ci 不同，跨表比较字符串必须显式统一，否则 ERROR 1267
       OR a.paper_key COLLATE utf8mb4_unicode_ci = p.paper_key
);

-- ------------------------------------------------------------
-- E. 学生作答记录的分类与试卷对齐（category 是作答时冗余写入的，会跟着试卷变更）
--    同样因为两张表排序规则不同，跨表字符串比较显式统一排序规则。
-- ------------------------------------------------------------
UPDATE past_paper_attempts a
JOIN past_papers p
  ON (a.past_paper_id = p.id
      OR a.paper_key COLLATE utf8mb4_unicode_ci = p.paper_key
      OR a.paper_key COLLATE utf8mb4_unicode_ci = CAST(p.id AS CHAR))
SET a.category = p.category
WHERE a.category COLLATE utf8mb4_unicode_ci <> p.category;
