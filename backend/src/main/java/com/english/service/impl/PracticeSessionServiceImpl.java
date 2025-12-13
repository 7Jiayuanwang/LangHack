package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.dto.PracticeSessionDTO;
import com.english.entity.AnswerRecord;
import com.english.entity.PracticeSession;
import com.english.entity.Question;
import com.english.mapper.AnswerRecordMapper;
import com.english.mapper.PracticeSessionMapper;
import com.english.mapper.QuestionMapper;
import com.english.service.PracticeSessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PracticeSessionServiceImpl extends ServiceImpl<PracticeSessionMapper, PracticeSession> implements PracticeSessionService {

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private AnswerRecordMapper answerRecordMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public PracticeSessionDTO.SessionResponse startSession(PracticeSessionDTO.StartSessionRequest request) {
        // Build query for questions based on criteria
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("deleted", 0);

        if (request.getQuestionType() != null && !request.getQuestionType().isEmpty()) {
            queryWrapper.eq("question_type", request.getQuestionType());
        }

        if (request.getDifficulty() != null && !request.getDifficulty().isEmpty()) {
            queryWrapper.eq("difficulty", request.getDifficulty());
        }

        if (request.getExamType() != null && !request.getExamType().isEmpty()) {
            queryWrapper.eq("exam_type", request.getExamType());
        }

        if (request.getKnowledgePointId() != null) {
            queryWrapper.like("knowledge_point_ids", request.getKnowledgePointId().toString());
        }

        // Get random questions
        int count = request.getQuestionCount() != null ? request.getQuestionCount() : 10;
        queryWrapper.last("ORDER BY RAND() LIMIT " + count);

        List<Question> questions = questionMapper.selectList(queryWrapper);

        if (questions.isEmpty()) {
            throw new RuntimeException("没有找到符合条件的题目");
        }

        // Create practice session
        PracticeSession session = new PracticeSession();
        session.setStudentId(request.getStudentId());
        session.setSessionName(request.getSessionName() != null ? request.getSessionName() : "练习会话");
        session.setQuestionType(request.getQuestionType());
        session.setDifficulty(request.getDifficulty());
        session.setQuestionCount(questions.size());
        session.setCorrectCount(0);
        session.setWrongCount(0);
        session.setTotalTimeSpent(0);
        session.setStatus("IN_PROGRESS");
        session.setStartTime(LocalDateTime.now());

        // Store question IDs as JSON array
        List<Long> questionIds = questions.stream().map(Question::getId).collect(Collectors.toList());
        try {
            session.setQuestionIds(objectMapper.writeValueAsString(questionIds));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize question IDs", e);
        }

        save(session);

        return new PracticeSessionDTO.SessionResponse(
            session.getId(),
            questionIds,
            session.getStartTime(),
            "练习会话已创建，共" + questions.size() + "道题"
        );
    }

    @Override
    @Transactional
    public void completeSession(Long sessionId, PracticeSessionDTO.CompleteSessionRequest request) {
        PracticeSession session = getById(sessionId);
        if (session == null) {
            throw new RuntimeException("练习会话不存在");
        }

        if ("COMPLETED".equals(session.getStatus())) {
            throw new RuntimeException("练习会话已完成");
        }

        // Get question IDs from session
        List<Long> questionIds;
        try {
            questionIds = objectMapper.readValue(session.getQuestionIds(), new TypeReference<List<Long>>(){});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse question IDs", e);
        }

        // Calculate statistics based on answer records
        QueryWrapper<AnswerRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", session.getStudentId());
        recordWrapper.in("question_id", questionIds);
        recordWrapper.ge("answer_time", session.getStartTime());

        List<AnswerRecord> records = answerRecordMapper.selectList(recordWrapper);

        long correctCount = records.stream()
            .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1)
            .count();
        long wrongCount = records.size() - correctCount;

        // Update session
        session.setCorrectCount((int) correctCount);
        session.setWrongCount((int) wrongCount);
        session.setTotalTimeSpent(request.getTotalTimeSpent() != null ? request.getTotalTimeSpent() : 0);
        session.setStatus("COMPLETED");
        session.setEndTime(LocalDateTime.now());

        updateById(session);
    }

    @Override
    public Map<String, Object> getPracticeHistory(Long studentId, Integer page, Integer size) {
        page = page != null ? page : 1;
        size = size != null ? size : 10;

        Page<PracticeSession> pageParam = new Page<>(page, size);
        QueryWrapper<PracticeSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId);
        queryWrapper.orderByDesc("start_time");

        IPage<PracticeSession> pageResult = page(pageParam, queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public Map<String, Object> getPracticeStats(Long studentId) {
        Map<String, Object> stats = new HashMap<>();

        // Get all completed sessions
        QueryWrapper<PracticeSession> sessionWrapper = new QueryWrapper<>();
        sessionWrapper.eq("student_id", studentId);
        sessionWrapper.eq("status", "COMPLETED");
        List<PracticeSession> sessions = list(sessionWrapper);

        // Calculate totals
        int totalSessions = sessions.size();
        int totalQuestions = sessions.stream()
            .mapToInt(s -> s.getQuestionCount() != null ? s.getQuestionCount() : 0)
            .sum();
        int totalCorrect = sessions.stream()
            .mapToInt(s -> s.getCorrectCount() != null ? s.getCorrectCount() : 0)
            .sum();
        int totalWrong = sessions.stream()
            .mapToInt(s -> s.getWrongCount() != null ? s.getWrongCount() : 0)
            .sum();
        int totalTimeSpent = sessions.stream()
            .mapToInt(s -> s.getTotalTimeSpent() != null ? s.getTotalTimeSpent() : 0)
            .sum();

        double accuracyRate = totalQuestions > 0 ? (double) totalCorrect / totalQuestions * 100 : 0.0;
        double avgTimePerQuestion = totalQuestions > 0 ? (double) totalTimeSpent / totalQuestions : 0.0;

        // Get recent activity (last 7 days)
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long recentSessions = sessions.stream()
            .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(sevenDaysAgo))
            .count();

        // Get statistics by difficulty
        Map<String, Integer> byDifficulty = new HashMap<>();
        byDifficulty.put("EASY", 0);
        byDifficulty.put("MEDIUM", 0);
        byDifficulty.put("HARD", 0);

        for (PracticeSession session : sessions) {
            if (session.getDifficulty() != null) {
                byDifficulty.put(session.getDifficulty(),
                    byDifficulty.getOrDefault(session.getDifficulty(), 0) + session.getQuestionCount());
            }
        }

        stats.put("totalSessions", totalSessions);
        stats.put("totalQuestions", totalQuestions);
        stats.put("totalCorrect", totalCorrect);
        stats.put("totalWrong", totalWrong);
        stats.put("accuracyRate", Math.round(accuracyRate * 100) / 100.0);
        stats.put("totalTimeSpent", totalTimeSpent);
        stats.put("avgTimePerQuestion", Math.round(avgTimePerQuestion * 100) / 100.0);
        stats.put("recentSessions", recentSessions);
        stats.put("byDifficulty", byDifficulty);

        return stats;
    }
}
