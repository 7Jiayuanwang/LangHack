package com.english.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI 生成的单词详细介绍
 */
@Data
public class WordAiExplanation {
    private Long id;
    private String word;
    private String phonetic;
    private String wordClass;
    private String basicMeaning;
    private String detailedExplanation;
    private String etymology;
    private String usageExamples;  // JSON 数组
    private String synonyms;       // JSON 数组
    private String antonyms;       // JSON 数组
    private String collocations;   // JSON 数组
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
