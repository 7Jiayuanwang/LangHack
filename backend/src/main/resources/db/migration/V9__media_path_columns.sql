-- V9: add media storage path columns
ALTER TABLE media
    ADD COLUMN IF NOT EXISTS file_path VARCHAR(500) COMMENT 'stored media file path';

ALTER TABLE media
    ADD COLUMN IF NOT EXISTS thumbnail_path VARCHAR(500) COMMENT 'stored thumbnail file path';
