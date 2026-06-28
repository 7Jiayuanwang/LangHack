-- V22: word_ai_explanations - AI 生成的单词详细介绍

CREATE TABLE IF NOT EXISTS word_ai_explanations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word VARCHAR(100) NOT NULL COMMENT '单词',
    phonetic VARCHAR(100) DEFAULT NULL COMMENT '音标',
    word_class VARCHAR(50) DEFAULT NULL COMMENT '词性',
    basic_meaning TEXT DEFAULT NULL COMMENT '基本释义',
    detailed_explanation TEXT DEFAULT NULL COMMENT 'AI 生成的详细解释',
    etymology TEXT DEFAULT NULL COMMENT '词源',
    usage_examples TEXT DEFAULT NULL COMMENT '使用例句（JSON 数组）',
    synonyms TEXT DEFAULT NULL COMMENT '同义词（JSON 数组）',
    antonyms TEXT DEFAULT NULL COMMENT '反义词（JSON 数组）',
    collocations TEXT DEFAULT NULL COMMENT '常见搭配（JSON 数组）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_word (word),
    KEY idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 生成的单词详细介绍';
