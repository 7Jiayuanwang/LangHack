-- V31: Add content_version column to text_learning_records
-- Tracks which content version (CET-4/CET-6/Academic/etc.) the student was reading

ALTER TABLE text_learning_records ADD COLUMN content_version VARCHAR(20) DEFAULT NULL
    COMMENT 'Content version: original/cet4/cet6/academic/chinese_parallel';

CREATE INDEX idx_tlr_student_version ON text_learning_records(student_id, content_version);
