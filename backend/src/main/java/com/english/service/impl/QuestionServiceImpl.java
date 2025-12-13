package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.dto.PracticeSessionDTO;
import com.english.entity.AnswerRecord;
import com.english.entity.Question;
import com.english.entity.WeaknessAnalysis;
import com.english.mapper.AnswerRecordMapper;
import com.english.mapper.QuestionMapper;
import com.english.mapper.WeaknessAnalysisMapper;
import com.english.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Autowired
    private WeaknessAnalysisMapper weaknessAnalysisMapper;

    @Autowired
    private AnswerRecordMapper answerRecordMapper;

    @Override
    public Map<String, Object> getQuestionsByPage(Map<String, Object> params) {
        int page = (int) params.get("page");
        int size = (int) params.get("size");
        String keyword = (String) params.get("keyword");
        String questionType = (String) params.get("questionType");
        String difficulty = (String) params.get("difficulty");
        String examType = (String) params.get("examType");
        Long knowledgePointId = (Long) params.get("knowledgePointId");

        Page<Question> pageParam = new Page<>(page, size);
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                .like("title", keyword)
                .or()
                .like("explanation", keyword)
            );
        }

        if (questionType != null && !questionType.trim().isEmpty()) {
            queryWrapper.eq("question_type", questionType);
        }

        if (difficulty != null && !difficulty.trim().isEmpty()) {
            queryWrapper.eq("difficulty", difficulty);
        }

        if (examType != null && !examType.trim().isEmpty()) {
            queryWrapper.eq("exam_type", examType);
        }

        if (knowledgePointId != null) {
            queryWrapper.like("knowledge_point_ids", knowledgePointId.toString());
        }

        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");

        IPage<Question> pageResult = page(pageParam, queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public Question getQuestionById(Long id) {
        return getById(id);
    }

    @Override
    public void createQuestion(Question question) {
        if (question.getDifficulty() == null) {
            question.setDifficulty("MEDIUM");
        }
        if (question.getScore() == null) {
            question.setScore(10);
        }
        question.setCreatedTime(LocalDateTime.now());
        question.setUpdatedTime(LocalDateTime.now());
        question.setDeleted(0);
        save(question);
    }

    @Override
    public void updateQuestion(Question question) {
        question.setUpdatedTime(LocalDateTime.now());
        updateById(question);
    }

    @Override
    public void deleteQuestion(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setDeleted(1);
        question.setUpdatedTime(LocalDateTime.now());
        updateById(question);
    }

    @Override
    public List<Question> getQuestionsByKnowledgePoint(Long knowledgePointId) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("knowledge_point_ids", knowledgePointId.toString());
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public List<Question> getRecommendedQuestions(Long studentId, Long knowledgePointId, int count) {
        // Get student's weakness analysis for this knowledge point
        QueryWrapper<WeaknessAnalysis> weaknessWrapper = new QueryWrapper<>();
        weaknessWrapper.eq("student_id", studentId);
        weaknessWrapper.eq("knowledge_point_id", knowledgePointId);
        WeaknessAnalysis weakness = weaknessAnalysisMapper.selectOne(weaknessWrapper);

        // Build query for questions
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("knowledge_point_ids", knowledgePointId.toString());
        queryWrapper.eq("deleted", 0);

        // Recommend harder questions if student is doing well, easier if struggling
        if (weakness != null && weakness.getAccuracyRate() != null) {
            double accuracyRate = weakness.getAccuracyRate().doubleValue();
            if (accuracyRate < 0.5) {
                // Student is struggling, recommend easy questions
                queryWrapper.eq("difficulty", "EASY");
            } else if (accuracyRate < 0.8) {
                // Student is doing okay, recommend medium questions
                queryWrapper.eq("difficulty", "MEDIUM");
            } else {
                // Student is doing well, recommend hard questions
                queryWrapper.eq("difficulty", "HARD");
            }
        }

        // Use random ordering to provide variety
        queryWrapper.last("ORDER BY RAND() LIMIT " + count);

        return list(queryWrapper);
    }

    @Override
    public PracticeSessionDTO.QuestionDetailDTO getQuestionDetailForPractice(Long questionId, Long studentId) {
        Question question = getById(questionId);
        if (question == null) {
            throw new RuntimeException("题目不存在");
        }

        PracticeSessionDTO.QuestionDetailDTO dto = new PracticeSessionDTO.QuestionDetailDTO();
        dto.setId(question.getId());
        dto.setTitle(question.getTitle());
        dto.setQuestionType(question.getQuestionType());
        dto.setDifficulty(question.getDifficulty());
        dto.setExamType(question.getExamType());
        dto.setExamLevel(question.getExamLevel());
        dto.setOptions(question.getOptions());
        dto.setScore(question.getScore());
        dto.setKnowledgePointIds(question.getKnowledgePointIds());

        // Check if student has answered this question
        QueryWrapper<AnswerRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", studentId);
        recordWrapper.eq("question_id", questionId);
        recordWrapper.orderByDesc("answer_time");
        recordWrapper.last("LIMIT 1");

        AnswerRecord latestRecord = answerRecordMapper.selectOne(recordWrapper);
        if (latestRecord != null) {
            dto.setHasAnswered(true);
            dto.setStudentAnswer(latestRecord.getStudentAnswer());
            dto.setIsCorrect(latestRecord.getIsCorrect() != null && latestRecord.getIsCorrect() == 1);
        } else {
            dto.setHasAnswered(false);
        }

        return dto;
    }

    @Override
    public PracticeSessionDTO.QuestionAnalysisDTO getQuestionAnalysis(Long questionId, Long studentId) {
        Question question = getById(questionId);
        if (question == null) {
            throw new RuntimeException("题目不存在");
        }

        PracticeSessionDTO.QuestionAnalysisDTO dto = new PracticeSessionDTO.QuestionAnalysisDTO();
        dto.setQuestionId(question.getId());
        dto.setCorrectAnswer(question.getCorrectAnswer());
        dto.setExplanation(question.getExplanation());
        dto.setKnowledgePointIds(question.getKnowledgePointIds());

        // Get student's statistics for this question
        QueryWrapper<AnswerRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", studentId);
        recordWrapper.eq("question_id", questionId);

        List<AnswerRecord> records = answerRecordMapper.selectList(recordWrapper);
        int totalAttempts = records.size();
        long correctAttempts = records.stream()
            .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1)
            .count();

        dto.setTotalAttempts(totalAttempts);
        dto.setCorrectAttempts((int) correctAttempts);
        dto.setAccuracyRate(totalAttempts > 0 ? (double) correctAttempts / totalAttempts * 100 : 0.0);

        return dto;
    }

    @Override
    public List<Question> getWrongQuestions(Long studentId) {
        // Get all wrong answer records for this student
        QueryWrapper<AnswerRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", studentId);
        recordWrapper.eq("is_correct", 0);
        recordWrapper.orderByDesc("answer_time");

        List<AnswerRecord> wrongRecords = answerRecordMapper.selectList(recordWrapper);

        // Get unique question IDs
        Set<Long> wrongQuestionIds = wrongRecords.stream()
            .map(AnswerRecord::getQuestionId)
            .collect(Collectors.toSet());

        if (wrongQuestionIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Get questions
        QueryWrapper<Question> questionWrapper = new QueryWrapper<>();
        questionWrapper.in("id", wrongQuestionIds);
        questionWrapper.eq("deleted", 0);
        questionWrapper.orderByDesc("created_time");

        return list(questionWrapper);
    }
}

