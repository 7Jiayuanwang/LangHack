CREATE TABLE IF NOT EXISTS sentence_favorites (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  text_id BIGINT NOT NULL,
  sentence VARCHAR(2000) NOT NULL,
  sentence_hash BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(sentence, 256))) STORED,
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sentence_favorite (user_id, text_id, sentence_hash),
  KEY idx_sentence_favorite_user_created (user_id, created_time),
  KEY idx_sentence_favorite_text (text_id),
  CONSTRAINT fk_sentence_favorite_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_sentence_favorite_text FOREIGN KEY (text_id) REFERENCES texts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户例句收藏';
