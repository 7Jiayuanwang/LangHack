package com.english.service;

import com.english.entity.LearningSuggestion;

import java.util.List;

public interface LearningSuggestionService {
    void generateSuggestions(Long studentId);
    List<LearningSuggestion> getStudentSuggestions(Long studentId);
    void completeSuggestion(Long suggestionId, Long studentId);
}
