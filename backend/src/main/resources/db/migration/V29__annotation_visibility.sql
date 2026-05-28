-- V29: 批注可见性 — 1=公开（默认），0=仅自己可见
DROP PROCEDURE IF EXISTS v29_add_visibility;
DELIMITER $$
CREATE PROCEDURE v29_add_visibility()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'text_annotations' AND COLUMN_NAME = 'visibility') THEN
        ALTER TABLE text_annotations ADD COLUMN visibility TINYINT NOT NULL DEFAULT 1 COMMENT '可见性: 1=公开, 0=仅自己';
    END IF;
END$$
DELIMITER ;
CALL v29_add_visibility();
DROP PROCEDURE IF EXISTS v29_add_visibility;
