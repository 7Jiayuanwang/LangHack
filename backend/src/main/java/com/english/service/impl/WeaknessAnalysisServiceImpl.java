package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.AnswerRecord;
import com.english.entity.KnowledgePoint;
import com.english.entity.Question;
import com.english.entity.WeaknessAnalysis;
import com.english.mapper.AnswerRecordMapper;
import com.english.mapper.KnowledgePointMapper;
import com.english.mapper.QuestionMapper;
import com.english.mapper.WeaknessAnalysisMapper;
import com.english.service.WeaknessAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WeaknessAnalysisServiceImpl extends ServiceImpl<WeaknessAnalysisMapper, WeaknessAnalysis> implements WeaknessAnalysisService {

    @Autowired
    private AnswerRecordMapper answerRecordMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

    @Override
    @Transactional
    public void analyzeStudentWeakness(Long studentId) {
        // Get all answer records for the student
        QueryWrapper<AnswerRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.eq("student_id", studentId);
        List<AnswerRecord> records = answerRecordMapper.selectList(recordWrapper);

        if (records.isEmpty()) {
            return;
        }

        // Group records by knowledge point
        Map<Long, List<AnswerRecord>> recordsByKnowledgePoint = new HashMap<>();

        for (AnswerRecord record : records) {
            Question question = questionMapper.selectById(record.getQuestionId());
            if (question == null || question.getKnowledgePointIds() == null) {
                continue;
            }

            // Parse knowledge point IDs (stored as comma-separated string)
            String[] kpIds = question.getKnowledgePointIds().split(",");
            for (String kpIdStr : kpIds) {
                try {
                    Long kpId = Long.parseLong(kpIdStr.trim());
                    recordsByKnowledgePoint
                        .computeIfAbsent(kpId, k -> new ArrayList<>())
                        .add(record);
                } catch (NumberFormatException e) {
                    // Skip invalid IDs
                }
            }
        }

        // Analyze each knowledge point
        for (Map.Entry<Long, List<AnswerRecord>> entry : recordsByKnowledgePoint.entrySet()) {
            Long knowledgePointId = entry.getKey();
            List<AnswerRecord> kpRecords = entry.getValue();

            // Calculate statistics
            int totalQuestions = kpRecords.size();
            int correctCount = (int) kpRecords.stream()
                .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1)
                .count();
            int wrongCount = totalQuestions - correctCount;

            // Calculate accuracy rate
            BigDecimal accuracyRate = totalQuestions > 0
                ? BigDecimal.valueOf((double) correctCount / totalQuestions)
                    .setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            // Determine weakness level based on accuracy rate and question count
            String weaknessLevel = determineWeaknessLevel(accuracyRate, totalQuestions);

            // Get last practice time
            LocalDateTime lastPracticeTime = kpRecords.stream()
                .map(AnswerRecord::getAnswerTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

            // Check if analysis already exists
            QueryWrapper<WeaknessAnalysis> analysisWrapper = new QueryWrapper<>();
            analysisWrapper.eq("student_id", studentId);
            analysisWrapper.eq("knowledge_point_id", knowledgePointId);
            WeaknessAnalysis existingAnalysis = getOne(analysisWrapper);

            if (existingAnalysis != null) {
                // Update existing analysis
                existingAnalysis.setTotalQuestions(totalQuestions);
                existingAnalysis.setCorrectCount(correctCount);
                existingAnalysis.setWrongCount(wrongCount);
                existingAnalysis.setAccuracyRate(accuracyRate);
                existingAnalysis.setWeaknessLevel(weaknessLevel);
                existingAnalysis.setLastPracticeTime(lastPracticeTime);
                existingAnalysis.setAnalysisTime(LocalDateTime.now());
                updateById(existingAnalysis);
            } else {
                // Create new analysis
                WeaknessAnalysis newAnalysis = new WeaknessAnalysis();
                newAnalysis.setStudentId(studentId);
                newAnalysis.setKnowledgePointId(knowledgePointId);
                newAnalysis.setTotalQuestions(totalQuestions);
                newAnalysis.setCorrectCount(correctCount);
                newAnalysis.setWrongCount(wrongCount);
                newAnalysis.setAccuracyRate(accuracyRate);
                newAnalysis.setWeaknessLevel(weaknessLevel);
                newAnalysis.setLastPracticeTime(lastPracticeTime);
                newAnalysis.setAnalysisTime(LocalDateTime.now());
                save(newAnalysis);
            }
        }
    }

    @Override
    public List<WeaknessAnalysis> getStudentWeaknessList(Long studentId) {
        QueryWrapper<WeaknessAnalysis> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId);
        queryWrapper.orderByDesc("weakness_level", "analysis_time");
        return list(queryWrapper);
    }

    @Override
    public Map<String, Object> getWeaknessReport(Long studentId) {
        Map<String, Object> report = new HashMap<>();

        List<WeaknessAnalysis> weaknessList = getStudentWeaknessList(studentId);

        // Count weaknesses by level
        Map<String, Long> weaknessByLevel = weaknessList.stream()
            .collect(Collectors.groupingBy(
                WeaknessAnalysis::getWeaknessLevel,
                Collectors.counting()
            ));

        // Get top 5 weakest knowledge points
        List<WeaknessAnalysis> topWeaknesses = weaknessList.stream()
            .filter(w -> "SEVERE".equals(w.getWeaknessLevel()) ||
                        "MODERATE".equals(w.getWeaknessLevel()))
            .limit(5)
            .collect(Collectors.toList());

        // Enrich with knowledge point names
        List<Map<String, Object>> enrichedWeaknesses = topWeaknesses.stream()
            .map(w -> {
                Map<String, Object> item = new HashMap<>();
                KnowledgePoint kp = knowledgePointMapper.selectById(w.getKnowledgePointId());
                item.put("knowledgePointId", w.getKnowledgePointId());
                item.put("knowledgePointName", kp != null ? kp.getName() : "Unknown");
                item.put("category", kp != null ? kp.getCategory() : "Unknown");
                item.put("accuracyRate", w.getAccuracyRate());
                item.put("totalQuestions", w.getTotalQuestions());
                item.put("correctCount", w.getCorrectCount());
                item.put("wrongCount", w.getWrongCount());
                item.put("weaknessLevel", w.getWeaknessLevel());
                item.put("lastPracticeTime", w.getLastPracticeTime());
                return item;
            })
            .collect(Collectors.toList());

        // Calculate overall statistics
        double avgAccuracy = weaknessList.stream()
            .map(WeaknessAnalysis::getAccuracyRate)
            .filter(Objects::nonNull)
            .mapToDouble(BigDecimal::doubleValue)
            .average()
            .orElse(0.0);

        int totalKnowledgePoints = weaknessList.size();
        int severeCount = weaknessByLevel.getOrDefault("SEVERE", 0L).intValue();
        int moderateCount = weaknessByLevel.getOrDefault("MODERATE", 0L).intValue();
        int mildCount = weaknessByLevel.getOrDefault("MILD", 0L).intValue();

        report.put("totalKnowledgePoints", totalKnowledgePoints);
        report.put("avgAccuracyRate", Math.round(avgAccuracy * 10000) / 100.0);
        report.put("severeWeaknesses", severeCount);
        report.put("moderateWeaknesses", moderateCount);
        report.put("mildWeaknesses", mildCount);
        report.put("topWeaknesses", enrichedWeaknesses);
        report.put("reportTime", LocalDateTime.now());

        return report;
    }

    @Override
    public List<WeaknessAnalysis> getTopWeaknesses(Long studentId, int limit) {
        QueryWrapper<WeaknessAnalysis> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId);
        queryWrapper.in("weakness_level", "SEVERE", "MODERATE");
        queryWrapper.orderByAsc("accuracy_rate");
        queryWrapper.last("LIMIT " + limit);
        return list(queryWrapper);
    }

    /**
     * Determine weakness level based on accuracy rate and question count
     *
     * Algorithm:
     * - SEVERE: Accuracy < 50% and at least 5 questions attempted
     * - MODERATE: Accuracy 50-70% and at least 3 questions attempted
     * - MILD: Accuracy 70-85% or fewer than required questions
     * - NONE: Accuracy > 85%
     */
    private String determineWeaknessLevel(BigDecimal accuracyRate, int totalQuestions) {
        double accuracy = accuracyRate.doubleValue();

        if (accuracy < 0.5 && totalQuestions >= 5) {
            return "SEVERE";
        } else if (accuracy < 0.7 && totalQuestions >= 3) {
            return "MODERATE";
        } else if (accuracy < 0.85) {
            return "MILD";
        } else {
            return "NONE";
        }
    }
}
