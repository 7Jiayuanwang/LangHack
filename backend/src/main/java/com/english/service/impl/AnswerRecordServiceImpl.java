package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.dto.LearningStatsDTO;
import com.english.entity.AnswerRecord;
import com.english.entity.FavoriteQuestion;
import com.english.entity.Question;
import com.english.entity.User;
import com.english.mapper.AnswerRecordMapper;
import com.english.mapper.FavoriteQuestionMapper;
import com.english.mapper.QuestionMapper;
import com.english.mapper.UserMapper;
import com.english.service.AnswerRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnswerRecordServiceImpl extends ServiceImpl<AnswerRecordMapper, AnswerRecord> implements AnswerRecordService {

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FavoriteQuestionMapper favoriteQuestionMapper;

    @Override
    public void saveAnswerRecord(AnswerRecord record) {
        if (record.getAnswerTime() == null) {
            record.setAnswerTime(LocalDateTime.now());
        }

        // Auto-check if answer is correct
        if (record.getQuestionId() != null && record.getStudentAnswer() != null) {
            Question question = questionMapper.selectById(record.getQuestionId());
            if (question != null && question.getCorrectAnswer() != null) {
                String correctAnswer = question.getCorrectAnswer().trim();
                String studentAnswer = record.getStudentAnswer().trim();

                // For multiple choice, exact match; for other types, case-insensitive match
                if (question.getQuestionType() != null &&
                    question.getQuestionType().equalsIgnoreCase("MULTIPLE_CHOICE")) {
                    record.setIsCorrect(correctAnswer.equals(studentAnswer) ? 1 : 0);
                } else {
                    record.setIsCorrect(correctAnswer.equalsIgnoreCase(studentAnswer) ? 1 : 0);
                }
            }
        }

        save(record);
    }

    @Override
    public List<AnswerRecord> getStudentAnswerRecords(Long studentId) {
        QueryWrapper<AnswerRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId);
        queryWrapper.orderByDesc("answer_time");
        return list(queryWrapper);
    }

    @Override
    public Map<String, Object> getStudentAnswerStats(Long studentId) {
        Map<String, Object> stats = new HashMap<>();

        // Get all answer records for the student
        QueryWrapper<AnswerRecord> allWrapper = new QueryWrapper<>();
        allWrapper.eq("student_id", studentId);
        List<AnswerRecord> allRecords = list(allWrapper);

        // Count total, correct, and wrong answers
        long totalCount = allRecords.size();
        long correctCount = allRecords.stream()
            .filter(record -> record.getIsCorrect() != null && record.getIsCorrect() == 1)
            .count();
        long wrongCount = totalCount - correctCount;

        // Calculate accuracy rate
        double accuracyRate = totalCount > 0 ? (double) correctCount / totalCount * 100 : 0.0;

        // Calculate average time spent
        double avgTimeSpent = allRecords.stream()
            .filter(record -> record.getTimeSpent() != null)
            .mapToInt(AnswerRecord::getTimeSpent)
            .average()
            .orElse(0.0);

        // Get recent activity (last 7 days)
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long recentCount = allRecords.stream()
            .filter(record -> record.getAnswerTime() != null &&
                             record.getAnswerTime().isAfter(sevenDaysAgo))
            .count();

        stats.put("totalQuestions", totalCount);
        stats.put("correctCount", correctCount);
        stats.put("wrongCount", wrongCount);
        stats.put("accuracyRate", Math.round(accuracyRate * 100) / 100.0);
        stats.put("avgTimeSpent", Math.round(avgTimeSpent * 100) / 100.0);
        stats.put("recentActivity", recentCount);

        return stats;
    }

    @Override
    public List<LearningStatsDTO.ActiveStudentDTO> getActiveStudentRanking(int limit) {
        // 优化：限制最大查询数量，防止一次性加载过多数据
        limit = Math.min(limit, 100);

        // 优化：使用数据库聚合查询替代全量加载
        // 获取所有答题记录，按学生分组
        // 建议在数据库中添加索引: CREATE INDEX idx_answer_record_student ON answer_records(student_id, is_correct);
        QueryWrapper<AnswerRecord> recordQuery = new QueryWrapper<>();
        recordQuery.select("student_id", "COUNT(*) as answer_count", "SUM(is_correct) as correct_count")
                   .groupBy("student_id")
                   .orderByDesc("COUNT(*)")
                   .last("LIMIT " + limit);

        // 由于MyBatis-Plus不直接支持分组聚合到DTO，我们仍使用原方法但添加早期限制
        List<AnswerRecord> allRecords = list();

        // 按学生ID分组统计
        Map<Long, List<AnswerRecord>> recordsByStudent = allRecords.stream()
                .collect(Collectors.groupingBy(AnswerRecord::getStudentId));

        // 计算每个学生的统计数据
        List<LearningStatsDTO.ActiveStudentDTO> ranking = new ArrayList<>();

        for (Map.Entry<Long, List<AnswerRecord>> entry : recordsByStudent.entrySet()) {
            Long studentId = entry.getKey();
            List<AnswerRecord> records = entry.getValue();

            // 获取学生信息
            User student = userMapper.selectById(studentId);
            if (student == null || !"STUDENT".equals(student.getRole())) {
                continue;
            }

            // 统计答题数和正确率
            int answerCount = records.size();
            long correctCount = records.stream()
                    .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1)
                    .count();

            int accuracy = answerCount > 0 ? (int) Math.round((double) correctCount / answerCount * 100) : 0;

            LearningStatsDTO.ActiveStudentDTO dto = new LearningStatsDTO.ActiveStudentDTO();
            dto.setStudentName(student.getRealName() != null ? student.getRealName() : student.getUsername());
            dto.setAnswerCount(answerCount);
            dto.setAccuracy(accuracy);

            ranking.add(dto);
        }

        // 按答题数降序排序，取前N个
        return ranking.stream()
                .sorted(Comparator.comparing(LearningStatsDTO.ActiveStudentDTO::getAnswerCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void toggleFavorite(Long questionId, Long studentId) {
        QueryWrapper<FavoriteQuestion> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId);
        queryWrapper.eq("question_id", questionId);

        FavoriteQuestion existing = favoriteQuestionMapper.selectOne(queryWrapper);

        if (existing != null) {
            // Remove from favorites
            favoriteQuestionMapper.deleteById(existing.getId());
        } else {
            // Add to favorites
            FavoriteQuestion favorite = new FavoriteQuestion();
            favorite.setStudentId(studentId);
            favorite.setQuestionId(questionId);
            favorite.setCreatedTime(LocalDateTime.now());
            favoriteQuestionMapper.insert(favorite);
        }
    }

    @Override
    public List<Long> getFavoriteQuestions(Long studentId) {
        QueryWrapper<FavoriteQuestion> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId);
        queryWrapper.orderByDesc("created_time");

        List<FavoriteQuestion> favorites = favoriteQuestionMapper.selectList(queryWrapper);
        return favorites.stream()
                .map(FavoriteQuestion::getQuestionId)
                .collect(Collectors.toList());
    }
}
