package com.english.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class QuestionStatsDTO {
    private Map<String, Integer> byType;      // 按题型统计
    private Map<String, Integer> byCategory;   // 按知识点分类统计
    private Map<String, Integer> byDifficulty; // 按难度统计
    private Map<String, Integer> byExamType;   // 按考试类型统计
}
