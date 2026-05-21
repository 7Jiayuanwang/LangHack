-- V26: NO-OP（已废弃）
-- 历史背景：本迁移原用于补建 text_exercises 表，但 V23 引用 text_exercises
--           在版本顺序上先于 V26，导致 fresh DB 测试环境跑 V23 失败。
-- 修复方案：text_exercises 建表逻辑已合并到 V23 顶部（CREATE IF NOT EXISTS）。
-- 保留本文件以避免 Flyway 报 "missing migration" 错误（已记录的环境）。
SELECT 'V26 deprecated; logic moved to V23' AS notice;
