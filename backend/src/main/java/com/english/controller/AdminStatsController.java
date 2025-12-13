package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.*;
import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/stats")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private WordService wordService;

    @Autowired
    private ClassService classService;

    @Autowired
    private TextService textService;

    @Autowired
    private AnswerRecordService answerRecordService;

    @Autowired
    private KnowledgePointService knowledgePointService;

    /**
     * 获取平台统计数据
     */
    @GetMapping("/platform")
    public Result<PlatformStatsDTO> getPlatformStats() {
        PlatformStatsDTO stats = new PlatformStatsDTO();

        // 统计用户数
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("deleted", 0);
        stats.setTotalUsers((int) userService.count(userQuery));

        // 统计课程数
        QueryWrapper<Course> courseQuery = new QueryWrapper<>();
        courseQuery.eq("deleted", 0);
        stats.setTotalCourses((int) courseService.count(courseQuery));

        // 统计题目数
        QueryWrapper<Question> questionQuery = new QueryWrapper<>();
        questionQuery.eq("deleted", 0);
        stats.setTotalQuestions((int) questionService.count(questionQuery));

        // 统计单词数
        QueryWrapper<Word> wordQuery = new QueryWrapper<>();
        wordQuery.eq("deleted", 0);
        stats.setTotalWords((int) wordService.count(wordQuery));

        // 统计班级数
        QueryWrapper<Class> classQuery = new QueryWrapper<>();
        classQuery.eq("deleted", 0);
        stats.setTotalClasses((int) classService.count(classQuery));

        // 统计课文数
        QueryWrapper<Text> textQuery = new QueryWrapper<>();
        textQuery.eq("deleted", 0);
        stats.setTotalTexts((int) textService.count(textQuery));

        return Result.success(stats);
    }

    /**
     * 获取用户统计数据
     */
    @GetMapping("/users")
    public Result<UserStatsDTO> getUserStats() {
        UserStatsDTO stats = new UserStatsDTO();

        // 统计各角色用户数
        QueryWrapper<User> baseQuery = new QueryWrapper<>();
        baseQuery.eq("deleted", 0);

        QueryWrapper<User> adminQuery = new QueryWrapper<>(baseQuery);
        adminQuery.eq("role", "ADMIN");
        stats.setAdminCount((int) userService.count(adminQuery));

        QueryWrapper<User> teacherQuery = new QueryWrapper<>(baseQuery);
        teacherQuery.eq("role", "TEACHER");
        stats.setTeacherCount((int) userService.count(teacherQuery));

        QueryWrapper<User> studentQuery = new QueryWrapper<>(baseQuery);
        studentQuery.eq("role", "STUDENT");
        stats.setStudentCount((int) userService.count(studentQuery));

        // 获取最近注册的10个用户
        QueryWrapper<User> recentQuery = new QueryWrapper<>();
        recentQuery.eq("deleted", 0)
                .orderByDesc("created_time")
                .last("LIMIT 10");
        stats.setRecentUsers(userService.list(recentQuery));

        return Result.success(stats);
    }

    /**
     * 获取学习统计数据
     */
    @GetMapping("/learning")
    public Result<LearningStatsDTO> getLearningStats(@RequestParam(defaultValue = "7") Integer days) {
        LearningStatsDTO stats = new LearningStatsDTO();

        // 最近N天的每日答题统计
        List<LearningStatsDTO.DailyStatDTO> dailyStats = new ArrayList<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

            QueryWrapper<AnswerRecord> dayQuery = new QueryWrapper<>();
            dayQuery.ge("answer_time", dayStart)
                    .lt("answer_time", dayEnd);

            LearningStatsDTO.DailyStatDTO dayStat = new LearningStatsDTO.DailyStatDTO();
            dayStat.setDate(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            dayStat.setCount((int) answerRecordService.count(dayQuery));
            dailyStats.add(dayStat);
        }
        stats.setDailyStats(dailyStats);

        // 活跃学生排行（按答题数）
        List<LearningStatsDTO.ActiveStudentDTO> activeStudents = answerRecordService.getActiveStudentRanking(10);
        stats.setActiveStudents(activeStudents);

        return Result.success(stats);
    }

    /**
     * 获取题库统计数据
     * 添加分页限制，防止一次性加载过多数据
     */
    @GetMapping("/questions")
    public Result<QuestionStatsDTO> getQuestionStats(
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(defaultValue = "1000") int maxSize) {

        // 限制分页大小
        pageSize = Math.min(pageSize, maxSize);
        pageSize = Math.max(pageSize, 1);

        QuestionStatsDTO stats = new QuestionStatsDTO();

        QueryWrapper<Question> baseQuery = new QueryWrapper<>();
        baseQuery.eq("deleted", 0)
                 .last("LIMIT " + pageSize);
        List<Question> allQuestions = questionService.list(baseQuery);

        // 按题型统计
        Map<String, Integer> byType = allQuestions.stream()
                .collect(Collectors.groupingBy(
                        Question::getQuestionType,
                        Collectors.summingInt(e -> 1)
                ));
        stats.setByType(byType);

        // 按难度统计
        Map<String, Integer> byDifficulty = allQuestions.stream()
                .collect(Collectors.groupingBy(
                        Question::getDifficulty,
                        Collectors.summingInt(e -> 1)
                ));
        stats.setByDifficulty(byDifficulty);

        // 按考试类型统计
        Map<String, Integer> byExamType = allQuestions.stream()
                .collect(Collectors.groupingBy(
                        Question::getExamType,
                        Collectors.summingInt(e -> 1)
                ));
        stats.setByExamType(byExamType);

        // 按知识点分类统计
        QueryWrapper<KnowledgePoint> kpQuery = new QueryWrapper<>();
        kpQuery.eq("level", 1); // 只统计一级分类
        List<KnowledgePoint> categories = knowledgePointService.list(kpQuery);

        Map<String, Integer> byCategory = new HashMap<>();
        for (KnowledgePoint kp : categories) {
            // 统计关联到该知识点的题目数
            long count = allQuestions.stream()
                    .filter(q -> q.getKnowledgePointIds() != null &&
                            q.getKnowledgePointIds().contains(kp.getId().toString()))
                    .count();
            byCategory.put(kp.getCategory(), (int) count);
        }
        stats.setByCategory(byCategory);

        return Result.success(stats);
    }
}
