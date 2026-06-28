package com.english.dto;

import lombok.Data;
import java.util.List;

/**
 * 单词详细介绍响应 DTO
 */
@Data
public class WordExplanationDTO {
    private String word;
    private String phonetic;
    private String wordClass;
    private String basicMeaning;
    private String detailedExplanation;
    private String etymology;
    private List<String> usageExamples;
    private List<String> synonyms;
    private List<String> antonyms;
    private List<String> collocations;
    private boolean fromCache;  // 是否来自缓存
}
