-- ═══════════════════════════════════════════════════════════════
-- V13: access_log 覆盖索引 — 加速 top_pages 查询
--
-- 背景: MonitoringController.getTopPages 调用 AccessLogMapper.topPagesByDateRange
--       SQL: SELECT uri, COUNT(*) FROM access_log
--            WHERE created_time >= ? AND created_time < ?
--            GROUP BY uri ORDER BY count DESC LIMIT ?
--       原表仅有 (created_time) 索引，GROUP BY uri 需要文件排序，
--       在每日日志量大时响应延迟显著。
--
-- 方案: 增加 (created_time, uri(191)) 复合索引 → 查询走索引范围扫描 + 索引内 GROUP BY
-- ═══════════════════════════════════════════════════════════════

SET @dbname = DATABASE();

-- 幂等检查：索引已存在则跳过
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = 'access_log'
      AND INDEX_NAME = 'idx_created_time_uri');

SET @stmt = IF(@idx_exists > 0,
    'SELECT 1',
    'ALTER TABLE access_log ADD INDEX idx_created_time_uri (created_time, uri(191))'
);
PREPARE alterIfNotExists FROM @stmt;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
