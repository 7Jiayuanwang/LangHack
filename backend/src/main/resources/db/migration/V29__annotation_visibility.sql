-- V29: 批注可见性 — 1=公开（默认），0=仅自己可见
ALTER TABLE text_annotations
    ADD COLUMN visibility TINYINT NOT NULL DEFAULT 1 COMMENT '可见性: 1=公开, 0=仅自己';