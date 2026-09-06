-- Link texts to multiple units while keeping legacy texts.unit_id usable.
CREATE TABLE IF NOT EXISTS text_units (
    text_id BIGINT NOT NULL,
    unit_id BIGINT NOT NULL,
    order_num INT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (text_id, unit_id),
    INDEX idx_text_units_unit (unit_id, order_num),
    CONSTRAINT fk_text_units_text FOREIGN KEY (text_id) REFERENCES texts(id) ON DELETE CASCADE,
    CONSTRAINT fk_text_units_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Text-unit associations';

-- Backfill associations from the legacy texts.unit_id column.
INSERT IGNORE INTO text_units (text_id, unit_id, order_num)
SELECT id, unit_id, COALESCE(order_num, 0)
FROM texts
WHERE unit_id IS NOT NULL AND unit_id > 0;
