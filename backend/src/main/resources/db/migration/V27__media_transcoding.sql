-- ═══════════════════════════════════════════════════════════════
-- V27: 媒体转码状态字段
-- 为 media 表新增 transcode_status / transcode_error / original_codec
-- 用于异步 ffmpeg 转码流水线（移动端兼容性，AV1/HEVC/VP9 → H.264）
-- 默认 SKIPPED，所有存量行无需迁移即可兼容
-- ═══════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS _add_col_media_transcode;
CREATE PROCEDURE _add_col_media_transcode()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema=DATABASE() AND table_name='media'
                     AND column_name='transcode_status') THEN
        ALTER TABLE media ADD COLUMN transcode_status VARCHAR(20)
            NOT NULL DEFAULT 'SKIPPED'
            COMMENT '转码状态: SKIPPED|PENDING|PROCESSING|SUCCESS|FAILED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema=DATABASE() AND table_name='media'
                     AND column_name='transcode_error') THEN
        ALTER TABLE media ADD COLUMN transcode_error VARCHAR(500) NULL
            COMMENT '转码失败原因（最后 500 字 stderr 摘要）';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema=DATABASE() AND table_name='media'
                     AND column_name='original_codec') THEN
        ALTER TABLE media ADD COLUMN original_codec VARCHAR(50) NULL
            COMMENT '上传时探测到的原始视频编码（av1/hevc/h264/vp9 等）';
    END IF;
END;
CALL _add_col_media_transcode();
DROP PROCEDURE IF EXISTS _add_col_media_transcode;

DROP PROCEDURE IF EXISTS _add_idx_media_transcode;
CREATE PROCEDURE _add_idx_media_transcode()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE table_schema=DATABASE() AND table_name='media'
                     AND index_name='idx_transcode_status') THEN
        ALTER TABLE media ADD INDEX idx_transcode_status (transcode_status);
    END IF;
END;
CALL _add_idx_media_transcode();
DROP PROCEDURE IF EXISTS _add_idx_media_transcode;
