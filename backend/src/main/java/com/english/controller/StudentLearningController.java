package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 学生学习控制器
 */
@RestController
@RequestMapping("/api/student/learning")
public class StudentLearningController {

    @Autowired
    private StudentClassesService studentClassesService;

    @Autowired
    private VocabularyBooksService vocabularyBooksService;

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private UserService userService;

    /**
     * 获取学生仪表板数据
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Map<String, Object>> getDashboardData(Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> dashboard = new HashMap<>();

            // 统计课程数量 - 基于student_classes
            dashboard.put("enrolledCourses", studentClassesService.countByStudentId(studentId));

            // 统计生词数量 - 基于vocabulary_books
            dashboard.put("vocabularyCount", vocabularyBooksService.countByStudentId(studentId));

            // 统计待完成任务 - 基于assignments和student_classes
            dashboard.put("pendingAssignments", getPendingAssignmentsCount(studentId));

            // 统计今日学习时长 - 基于learning_records
            dashboard.put("todayStudyTime", learningRecordsService.getTodayStudyTime(studentId));

            // 班级列表 - 基于student_classes关联classes和courses
            dashboard.put("myClasses", studentClassesService.getStudentClassesWithDetails(studentId));

            // 待办任务 - 基于assignments
            dashboard.put("pendingAssignmentsList", getPendingAssignments(studentId));

            // 最近生词 - 基于vocabulary_books按added_time排序
            dashboard.put("recentWords", vocabularyBooksService.getRecentWords(studentId, 10));

            return Result.success(dashboard);
        } catch (Exception e) {
            return Result.error("获取仪表板数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取学生的课程列表
     */
    @GetMapping("/courses")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Map<String, Object>> getStudentCourses(Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> result = new HashMap<>();
            // 查询学生加入的班级及其关联的课程
            result.put("courses", studentClassesService.getStudentCoursesWithProgress(studentId));

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取课程列表失败：" + e.getMessage());
        }
    }

    /**
     * 获取课程下的单元列表
     */
    @GetMapping("/courses/{courseId}/units")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Map<String, Object>> getCourseUnits(
            @PathVariable Long courseId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> result = new HashMap<>();
            // 查询课程下的所有单元
            result.put("units", learningRecordsService.getUnitsWithLearningStats(studentId, courseId));

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取单元列表失败：" + e.getMessage());
        }
    }

    /**
     * 获取单元详细内容（课文和单词）
     */
    @GetMapping("/units/{unitId}/content")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Map<String, Object>> getUnitContent(
            @PathVariable Long unitId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> content = learningRecordsService.getUnitContentWithLearningStates(studentId, unitId);

            return Result.success(content);
        } catch (Exception e) {
            return Result.error("获取单元内容失败：" + e.getMessage());
        }
    }

    /**
     * 获取课文详细内容
     */
    @GetMapping("/texts/{textId}")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Map<String, Object>> getTextDetail(
            @PathVariable Long textId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);

            Map<String, Object> detail = learningRecordsService.getTextDetailWithAnnotation(studentId, textId);

            // 记录课文学习记录
            learningRecordsService.recordTextView(studentId, textId);

            return Result.success(detail);
        } catch (Exception e) {
            return Result.error("获取课文详情失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前学生ID
     */
    private Long getCurrentStudentId(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.getUserByUsername(username);
        return user != null ? user.getId() : null;
    }

    /**
     * 获取待完成任务数量
     */
    private int getPendingAssignmentsCount(Long studentId) {
        // 这里需要实现获取待完成任务数量的逻辑
        // 基于assignments表和student_classes关联查询
        return 0; // 暂时返回0
    }

    /**
     * 获取待完成任务列表
     */
    private Object getPendingAssignments(Long studentId) {
        // 这里需要实现获取待完成任务列表的逻辑
        return new Object[0]; // 暂时返回空数组
    }
}