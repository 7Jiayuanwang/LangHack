package com.english.service;

import com.english.entity.Question;

import java.util.List;
import java.util.Map;

public interface QuestionService {
    Map<String, Object> getQuestionsByPage(Map<String, Object> params);
    Question getQuestionById(Long id);
    void createQuestion(Question question);
    void updateQuestion(Question question);
    void deleteQuestion(Long id);
    List<Question> getQuestionsByKnowledgePoint(Long knowledgePointId);
    List<Question> getRecommendedQuestions(Long studentId, Long knowledgePointId, int count);
}
