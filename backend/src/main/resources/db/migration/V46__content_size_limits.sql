-- ═══════════════════════════════════════════════════════════════════════
-- V46: content_limits 分组 — 图片/视频/Excel 上传大小初始值
-- ═══════════════════════════════════════════════════════════════════════
INSERT IGNORE INTO system_config (config_group, config_key, config_value, description, is_secret) VALUES
  ('content_limits', 'upload.content_size.image',              '10MB',  '图片上传大小限制（全局默认）', 0),
  ('content_limits', 'upload.content_size.video',              '512MB', '视频上传大小限制（全局默认）', 0),
  ('content_limits', 'upload.content_size.excel_import',       '10MB',  'Excel批量导入大小限制',        0);
