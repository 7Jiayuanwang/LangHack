package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 学生学习统计控制器
 */
@RestController
@RequestMapping("/api/student/stats")
public class StudentStatsController {

    private static final Logger log = LoggerFactory.getLogger(StudentStatsController.class);

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private VocabularyBooksService vocabularyBooksService;

    @Autowired
    private StudentClassesService studentClassesService;

    @Autowired
    private UserService userService;

    /**
     * 获取今日学习统计
     */
    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getTodayLearningStats(Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> stats = learningRecordsService.getTodayLearningStats(studentId);

            return Result.success(stats);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取今日统计失败", e);
            return Result.error("获取今日统计失败，请稍后重试");
        }
    }

    /**
     * 获取学习统计数据
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getLearningOverview(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> params = new HashMap<>();
            params.put("period", period);
            params.put("startDate", startDate);
            params.put("endDate", endDate);

            Map<String, Object> overview = learningRecordsService.getLearningOverview(studentId, params);

            return Result.success(overview);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取学习统计失败", e);
            return Result.error("获取学习统计失败，请稍后重试");
        }
    }

    /**
     * 获取详细学习记录
     */
    @GetMapping("/records")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getLearningRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String recordType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long wordId,
            Authentication authentication) {

        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> params = new HashMap<>();
            params.put("studentId", studentId);
            params.put("page", page);
            params.put("size", size);
            params.put("recordType", recordType);
            params.put("startDate", startDate);
            params.put("endDate", endDate);
            params.put("wordId", wordId);

            Map<String, Object> records = learningRecordsService.getLearningRecordsWithPagination(params);

            return Result.success(records);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取学习记录失败", e);
            return Result.error("获取学习记录失败，请稍后重试");
        }
    }

    /**
     * 获取单词学习统计
     */
    @GetMapping("/words")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getWordStudyStats(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long unitId,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> stats = vocabularyBooksService.getWordStudyStats(studentId, period, courseId, unitId);

            return Result.success(stats);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取单词统计失败", e);
            return Result.error("获取单词统计失败，请稍后重试");
        }
    }

    /**
     * 获取课程学习统计
     */
    @GetMapping("/courses")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getCourseLearningStats(
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> stats;

            if (courseId != null) {
                // 获取特定课程的统计
                stats = learningRecordsService.getCourseLearningStats(studentId, courseId);
            } else {
                // 获取所有课程的统计
                stats = learningRecordsService.getAllCoursesLearningStats(studentId);
            }

            return Result.success(stats);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取课程统计失败", e);
            return Result.error("获取课程统计失败，请稍后重试");
        }
    }

    /**
     * 获取学习时长统计
     */
    @GetMapping("/study-time")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getStudyTimeStats(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String granularity, // daily, weekly, monthly
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> params = new HashMap<>();
            params.put("period", period);
            params.put("granularity", granularity);

            Map<String, Object> stats = learningRecordsService.getStudyTimeStats(studentId, params);

            return Result.success(stats);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取学习时长统计失败", e);
            return Result.error("获取学习时长统计失败，请稍后重试");
        }
    }

    /**
     * 获取学习进度报告
     */
    @GetMapping("/progress-report")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getProgressReport(
            @RequestParam(required = false) String type, // weekly, monthly
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> report = learningRecordsService.generateProgressReport(studentId, type);

            return Result.success(report);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("生成进度报告失败", e);
            return Result.error("生成进度报告失败，请稍后重试");
        }
    }

    /**
     * 获取学习成就统计
     */
    @GetMapping("/achievements")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getAchievementStats(Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> achievements = learningRecordsService.getAchievementStats(studentId);

            return Result.success(achievements);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取成就统计失败", e);
            return Result.error("获取成就统计失败，请稍后重试");
        }
    }

    /**
     * 获取学习排名统计
     */
    @GetMapping("/ranking")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getLearningRanking(
            @RequestParam(required = false) String type, // class, course, global
            @RequestParam(required = false) Long targetId, // 班级ID或课程ID
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> ranking = learningRecordsService.getLearningRanking(studentId, type, targetId);

            return Result.success(ranking);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取学习排名失败", e);
            return Result.error("获取学习排名失败，请稍后重试");
        }
    }

    /**
     * 导出学习数据
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> exportLearningData(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String format, // excel, pdf, csv
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> params = new HashMap<>();
            params.put("startDate", startDate);
            params.put("endDate", endDate);
            params.put("format", format != null ? format : "excel");

            Map<String, Object> exportResult = learningRecordsService.exportLearningData(studentId, params);

            return Result.success(exportResult);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("导出学习数据失败", e);
            return Result.error("导出学习数据失败，请稍后重试");
        }
    }

    /**
     * 获取当前学生ID
     */
    private Long getCurrentStudentId(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user.getId();
    }
}