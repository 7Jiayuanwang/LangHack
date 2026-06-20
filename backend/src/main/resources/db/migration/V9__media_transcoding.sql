-- ═══════════════════════════════════════════════════════════════════════
-- V9: 媒体转码与内容大小限制
-- 合并自:
--   - V44 (media_transcoding)    media 表转码状态字段 (transcode_status/error/codec)
--   - V46 (content_size_limits)  content_limits 配置初始值 (image/video/excel/backup_import)
--
-- 历史说明:
--   - V44 为 media 表新增转码字段, 用于异步 ffmpeg 转码流水线
--     (移动端兼容性, AV1/HEVC/VP9 → H.264), 默认 SKIPPED
--   - V46 引入 content_limits 分组配置: 图片/视频/Excel 上传大小初始值
-- ═══════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════
-- Part 1: media 表转码状态字段 (原 V44)
--   - transcode_status  VARCHAR(20)  NOT NULL DEFAULT 'SKIPPED'
--   - transcode_error   VARCHAR(500) NULL
--   - original_codec    VARCHAR(50)  NULL
-- 索引: idx_transcode_status
-- ═══════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS _v9_add_col_media_transcode;
CREATE PROCEDURE _v9_add_col_media_transcode()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media'
                     AND COLUMN_NAME = 'transcode_status') THEN
        ALTER TABLE media ADD COLUMN transcode_status VARCHAR(20)
            NOT NULL DEFAULT 'SKIPPED'
            COMMENT '转码状态: SKIPPED|PENDING|PROCESSING|SUCCESS|FAILED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media'
                     AND COLUMN_NAME = 'transcode_error') THEN
        ALTER TABLE media ADD COLUMN transcode_error VARCHAR(500) NULL
            COMMENT '转码失败原因（最后 500 字 stderr 摘要）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media'
                     AND COLUMN_NAME = 'original_codec') THEN
        ALTER TABLE media ADD COLUMN original_codec VARCHAR(50) NULL
            COMMENT '上传时探测到的原始视频编码（av1/hevc/h264/vp9 等）';
    END IF;
END;
CALL _v9_add_col_media_transcode();
DROP PROCEDURE IF EXISTS _v9_add_col_media_transcode;

DROP PROCEDURE IF EXISTS _v9_add_idx_media_transcode;
CREATE PROCEDURE _v9_add_idx_media_transcode()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'media'
                     AND INDEX_NAME = 'idx_transcode_status') THEN
        ALTER TABLE media ADD INDEX idx_transcode_status (transcode_status);
    END IF;
END;
CALL _v9_add_idx_media_transcode();
DROP PROCEDURE IF EXISTS _v9_add_idx_media_transcode;

-- ═══════════════════════════════════════════════════════════════════════
-- Part 2: content_limits 分组 seed (原 V46)
--   - 图片上传大小限制 (全局默认)
--   - 视频上传大小限制 (全局默认)
--   - Excel 批量导入大小限制
--   - 备份文件导入大小限制 (2GB，与 nginx client_max_body_size 2048M 对齐)
-- ═══════════════════════════════════════════════════════════════════════

INSERT IGNORE INTO system_config (config_group, config_key, config_value, description, is_secret) VALUES
  ('content_limits', 'upload.content_size.image',              '10MB',   '图片上传大小限制（全局默认）', 0),
  ('content_limits', 'upload.content_size.video',              '512MB',  '视频上传大小限制（全局默认）', 0),
  ('content_limits', 'upload.content_size.excel_import',       '10MB',   'Excel批量导入大小限制',        0),
  ('content_limits', 'upload.content_size.backup_import',      '2048MB', '备份文件导入大小限制',         0);