package com.english.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.english.dto.PracticeSessionDTO;
import com.english.entity.Question;

import java.util.List;
import java.util.Map;

public interface QuestionService extends IService<Question> {
    Map<String, Object> getQuestionsByPage(Map<String, Object> params);
    Question getQuestionById(Long id);
    void createQuestion(Question question);
    void updateQuestion(Question question);
    void deleteQuestion(Long id);
    List<Question> getQuestionsByKnowledgePoint(Long knowledgePointId);
    List<Question> getRecommendedQuestions(Long studentId, Long knowledgePointId, int count);

    // Practice-specific methods
    PracticeSessionDTO.QuestionDetailDTO getQuestionDetailForPractice(Long questionId, Long studentId);
    PracticeSessionDTO.QuestionAnalysisDTO getQuestionAnalysis(Long questionId, Long studentId);
    List<Question> getWrongQuestions(Long studentId);
}
