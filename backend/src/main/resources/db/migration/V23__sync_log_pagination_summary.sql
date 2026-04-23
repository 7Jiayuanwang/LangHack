ALTER TABLE sync_logs
    ADD COLUMN pagination_complete TINYINT(1) NULL COMMENT '分页是否完整结束' AFTER operator,
    ADD COLUMN pagination_stop_reason VARCHAR(64) NULL COMMENT '分页停止原因' AFTER pagination_complete,
    ADD COLUMN pagination_pages_fetched INT NULL COMMENT '已拉取页数' AFTER pagination_stop_reason,
    ADD COLUMN pagination_last_skip INT NULL COMMENT '最后一次skip值' AFTER pagination_pages_fetched,
    ADD COLUMN pagination_page_size INT NULL COMMENT '分页pageSize' AFTER pagination_last_skip,
    ADD COLUMN pagination_guard_hit TINYINT(1) NULL COMMENT '是否触发分页防护' AFTER pagination_page_size,
    ADD COLUMN fetched_row_count INT NULL COMMENT '上游已拉取条数' AFTER pagination_guard_hit;
