package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.KnowledgePoint;
import com.english.entity.LearningSuggestion;
import com.english.entity.WeaknessAnalysis;
import com.english.mapper.KnowledgePointMapper;
import com.english.mapper.LearningSuggestionMapper;
import com.english.mapper.WeaknessAnalysisMapper;
import com.english.service.LearningSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LearningSuggestionServiceImpl extends ServiceImpl<LearningSuggestionMapper, LearningSuggestion> implements LearningSuggestionService {

    @Autowired
    private WeaknessAnalysisMapper weaknessAnalysisMapper;

    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

    @Override
    @Transactional
    public void generateSuggestions(Long studentId) {
        // Clear old pending suggestions
        QueryWrapper<LearningSuggestion> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("student_id", studentId);
        deleteWrapper.eq("status", "PENDING");
        remove(deleteWrapper);

        // Get student's weakness analysis
        QueryWrapper<WeaknessAnalysis> weaknessWrapper = new QueryWrapper<>();
        weaknessWrapper.eq("student_id", studentId);
        weaknessWrapper.orderByAsc("accuracy_rate");
        List<WeaknessAnalysis> weaknessList = weaknessAnalysisMapper.selectList(weaknessWrapper);

        if (weaknessList.isEmpty()) {
            // Generate general suggestion for new students
            generateGeneralSuggestion(studentId);
            return;
        }

        // Generate suggestions based on weakness analysis
        List<LearningSuggestion> suggestions = new ArrayList<>();

        // 1. Generate suggestions for severe weaknesses (High Priority)
        List<WeaknessAnalysis> severeWeaknesses = weaknessList.stream()
            .filter(w -> "SEVERE".equals(w.getWeaknessLevel()))
            .limit(3)
            .collect(Collectors.toList());

        for (WeaknessAnalysis weakness : severeWeaknesses) {
            LearningSuggestion suggestion = createWeaknessSuggestion(
                studentId,
                weakness,
                "HIGH",
                "WEAKNESS_PRACTICE"
            );
            suggestions.add(suggestion);
        }

        // 2. Generate suggestions for moderate weaknesses (Medium Priority)
        List<WeaknessAnalysis> moderateWeaknesses = weaknessList.stream()
            .filter(w -> "MODERATE".equals(w.getWeaknessLevel()))
            .limit(2)
            .collect(Collectors.toList());

        for (WeaknessAnalysis weakness : moderateWeaknesses) {
            LearningSuggestion suggestion = createWeaknessSuggestion(
                studentId,
                weakness,
                "MEDIUM",
                "WEAKNESS_PRACTICE"
            );
            suggestions.add(suggestion);
        }

        // 3. Generate review suggestion for old knowledge points
        generateReviewSuggestions(studentId, weaknessList, suggestions);

        // 4. Generate reinforcement suggestion for good performance
        generateReinforcementSuggestions(studentId, weaknessList, suggestions);

        // 5. Generate balanced practice suggestion
        if (suggestions.size() < 3) {
            generateBalancedPracticeSuggestion(studentId, weaknessList, suggestions);
        }

        // Save all suggestions
        saveBatch(suggestions);
    }

    @Override
    public List<LearningSuggestion> getStudentSuggestions(Long studentId) {
        QueryWrapper<LearningSuggestion> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId);
        queryWrapper.eq("status", "PENDING");
        queryWrapper.orderByDesc("priority", "created_time");
        return list(queryWrapper);
    }

    @Override
    public void completeSuggestion(Long suggestionId, Long studentId) {
        LearningSuggestion suggestion = getById(suggestionId);
        if (suggestion == null) {
            throw new RuntimeException("学习建议不存在");
        }

        // 验证建议是否属于当前学生
        if (!suggestion.getStudentId().equals(studentId)) {
            throw new RuntimeException("您没有权限操作此学习建议");
        }

        suggestion.setStatus("COMPLETED");
        suggestion.setCompletedTime(LocalDateTime.now());
        updateById(suggestion);
    }

    /**
     * Create a weakness-based suggestion
     */
    private LearningSuggestion createWeaknessSuggestion(
        Long studentId,
        WeaknessAnalysis weakness,
        String priority,
        String type
    ) {
        KnowledgePoint kp = knowledgePointMapper.selectById(weakness.getKnowledgePointId());
        String kpName = kp != null ? kp.getName() : "该知识点";
        String category = kp != null ? kp.getCategory() : "";

        LearningSuggestion suggestion = new LearningSuggestion();
        suggestion.setStudentId(studentId);
        suggestion.setSuggestionType(type);
        suggestion.setKnowledgePointIds(weakness.getKnowledgePointId().toString());
        suggestion.setPriority(priority);
        suggestion.setStatus("PENDING");
        suggestion.setCreatedTime(LocalDateTime.now());

        // Generate AI-like suggestion content
        double accuracy = weakness.getAccuracyRate().doubleValue() * 100;
        String content = generateSuggestionContent(
            kpName,
            category,
            accuracy,
            weakness.getTotalQuestions(),
            weakness.getWeaknessLevel()
        );

        suggestion.setContent(content);

        return suggestion;
    }

    /**
     * Generate AI-like suggestion content based on analysis
     */
    private String generateSuggestionContent(
        String knowledgePoint,
        String category,
        double accuracy,
        int totalQuestions,
        String weaknessLevel
    ) {
        StringBuilder content = new StringBuilder();

        // Title
        if ("SEVERE".equals(weaknessLevel)) {
            content.append("【重点关注】");
        } else if ("MODERATE".equals(weaknessLevel)) {
            content.append("【建议加强】");
        } else {
            content.append("【巩固提高】");
        }

        content.append(knowledgePoint).append("\n\n");

        // Analysis
        content.append("📊 学习分析：\n");
        content.append(String.format("在%d道题目中，您的正确率为%.1f%%", totalQuestions, accuracy));

        if ("SEVERE".equals(weaknessLevel)) {
            content.append("，这表明您在该知识点上还存在较大困难。\n\n");
        } else if ("MODERATE".equals(weaknessLevel)) {
            content.append("，这表明您对该知识点的掌握还不够牢固。\n\n");
        } else {
            content.append("，您已经有了一定的基础。\n\n");
        }

        // Suggestions
        content.append("💡 学习建议：\n");

        if ("SEVERE".equals(weaknessLevel)) {
            content.append("1. 建议系统学习").append(knowledgePoint).append("的基础概念和规则\n");
            content.append("2. 每天练习5-10道相关题目，重点理解错题\n");
            content.append("3. 寻找该知识点的相关例句和应用场景\n");
            content.append("4. 可以观看相关视频教程，加深理解\n");
        } else if ("MODERATE".equals(weaknessLevel)) {
            content.append("1. 复习").append(knowledgePoint).append("的核心要点\n");
            content.append("2. 每天练习3-5道题目，注意总结规律\n");
            content.append("3. 对比易混淆的概念，加强辨析能力\n");
            content.append("4. 多做实际应用题，提高熟练度\n");
        } else {
            content.append("1. 继续保持对").append(knowledgePoint).append("的练习\n");
            content.append("2. 尝试更有难度的题目，突破自己\n");
            content.append("3. 总结该知识点的高级用法和技巧\n");
            content.append("4. 帮助他人理解，巩固自己的掌握\n");
        }

        // Practice recommendation
        content.append("\n🎯 推荐练习量：");
        if ("SEVERE".equals(weaknessLevel)) {
            content.append("每天15-20分钟，持续1-2周");
        } else if ("MODERATE".equals(weaknessLevel)) {
            content.append("每天10-15分钟，持续1周");
        } else {
            content.append("每天5-10分钟，持续3-5天");
        }

        return content.toString();
    }

    /**
     * Generate review suggestions for knowledge points not practiced recently
     */
    private void generateReviewSuggestions(
        Long studentId,
        List<WeaknessAnalysis> weaknessList,
        List<LearningSuggestion> suggestions
    ) {
        LocalDateTime twoWeeksAgo = LocalDateTime.now().minusWeeks(2);

        List<WeaknessAnalysis> needReview = weaknessList.stream()
            .filter(w -> w.getLastPracticeTime() != null &&
                        w.getLastPracticeTime().isBefore(twoWeeksAgo))
            .filter(w -> w.getAccuracyRate().compareTo(BigDecimal.valueOf(0.85)) < 0)
            .limit(2)
            .collect(Collectors.toList());

        for (WeaknessAnalysis weakness : needReview) {
            KnowledgePoint kp = knowledgePointMapper.selectById(weakness.getKnowledgePointId());
            String kpName = kp != null ? kp.getName() : "该知识点";

            LearningSuggestion suggestion = new LearningSuggestion();
            suggestion.setStudentId(studentId);
            suggestion.setSuggestionType("REVIEW");
            suggestion.setKnowledgePointIds(weakness.getKnowledgePointId().toString());
            suggestion.setPriority("MEDIUM");
            suggestion.setStatus("PENDING");
            suggestion.setCreatedTime(LocalDateTime.now());

            String content = "🔄 复习提醒：" + kpName + "\n\n" +
                "您已经有一段时间没有练习这个知识点了。建议花10-15分钟复习一下，" +
                "避免遗忘。温故而知新，可以巩固之前的学习成果！";

            suggestion.setContent(content);
            suggestions.add(suggestion);
        }
    }

    /**
     * Generate reinforcement suggestions for well-mastered knowledge points
     */
    private void generateReinforcementSuggestions(
        Long studentId,
        List<WeaknessAnalysis> weaknessList,
        List<LearningSuggestion> suggestions
    ) {
        List<WeaknessAnalysis> strongPoints = weaknessList.stream()
            .filter(w -> w.getAccuracyRate().compareTo(BigDecimal.valueOf(0.85)) >= 0)
            .filter(w -> w.getTotalQuestions() >= 5)
            .limit(1)
            .collect(Collectors.toList());

        for (WeaknessAnalysis strength : strongPoints) {
            KnowledgePoint kp = knowledgePointMapper.selectById(strength.getKnowledgePointId());
            String kpName = kp != null ? kp.getName() : "该知识点";

            LearningSuggestion suggestion = new LearningSuggestion();
            suggestion.setStudentId(studentId);
            suggestion.setSuggestionType("REINFORCEMENT");
            suggestion.setKnowledgePointIds(strength.getKnowledgePointId().toString());
            suggestion.setPriority("LOW");
            suggestion.setStatus("PENDING");
            suggestion.setCreatedTime(LocalDateTime.now());

            double accuracy = strength.getAccuracyRate().doubleValue() * 100;
            String content = "⭐ 表现优秀：" + kpName + "\n\n" +
                String.format("您在该知识点的正确率达到%.1f%%，表现优秀！", accuracy) +
                "建议尝试更高难度的题目，或者学习该知识点的高级应用，" +
                "进一步提升您的英语水平。";

            suggestion.setContent(content);
            suggestions.add(suggestion);
        }
    }

    /**
     * Generate balanced practice suggestion
     */
    private void generateBalancedPracticeSuggestion(
        Long studentId,
        List<WeaknessAnalysis> weaknessList,
        List<LearningSuggestion> suggestions
    ) {
        // Get multiple knowledge points for balanced practice
        List<Long> kpIds = weaknessList.stream()
            .filter(w -> w.getAccuracyRate().compareTo(BigDecimal.valueOf(0.7)) < 0)
            .limit(5)
            .map(WeaknessAnalysis::getKnowledgePointId)
            .collect(Collectors.toList());

        if (kpIds.isEmpty()) {
            return;
        }

        LearningSuggestion suggestion = new LearningSuggestion();
        suggestion.setStudentId(studentId);
        suggestion.setSuggestionType("BALANCED_PRACTICE");
        suggestion.setKnowledgePointIds(kpIds.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(",")));
        suggestion.setPriority("MEDIUM");
        suggestion.setStatus("PENDING");
        suggestion.setCreatedTime(LocalDateTime.now());

        String content = "📚 综合练习建议\n\n" +
            "为了全面提升您的英语水平，建议进行综合练习。" +
            "系统已为您精选了多个需要加强的知识点，" +
            "建议每天花20-30分钟进行混合练习，" +
            "这样可以帮助您在实际应用中灵活运用各项知识。";

        suggestion.setContent(content);
        suggestions.add(suggestion);
    }

    /**
     * Generate general suggestion for new students
     */
    private void generateGeneralSuggestion(Long studentId) {
        LearningSuggestion suggestion = new LearningSuggestion();
        suggestion.setStudentId(studentId);
        suggestion.setSuggestionType("GENERAL");
        suggestion.setPriority("HIGH");
        suggestion.setStatus("PENDING");
        suggestion.setCreatedTime(LocalDateTime.now());

        String content = "👋 欢迎开始学习！\n\n" +
            "您还没有任何练习记录。建议您：\n\n" +
            "1. 先进行一些基础练习，让系统了解您的水平\n" +
            "2. 不要担心答错，错误是学习的最好机会\n" +
            "3. 每天坚持练习15-30分钟，效果最佳\n" +
            "4. 系统会根据您的表现，为您推荐最适合的学习内容\n\n" +
            "加油！期待看到您的进步！";

        suggestion.setContent(content);
        save(suggestion);
    }
}
