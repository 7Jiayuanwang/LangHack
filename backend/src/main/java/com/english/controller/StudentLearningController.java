package com.english.controller;

import com.english.constants.RoleConstants;
import com.english.dto.Result;
import com.english.service.*;
import com.english.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生学习控制器
 */
@RestController
@RequestMapping("/api/student/learning")
public class StudentLearningController {

    @Autowired
    private StudentClassService studentClassService;

    @Autowired
    private VocabularyBooksService vocabularyBooksService;

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private AssignmentService assignmentService;

    /**
     * 获取学生仪表板数据
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getDashboardData(Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            Map<String, Object> dashboard = new HashMap<>();

            // 统计课程数量 - 基于student_classes
            dashboard.put("enrolledCourses", studentClassService.countByStudentId(studentId));

            // 统计生词数量 - 基于vocabulary_books
            dashboard.put("vocabularyCount", vocabularyBooksService.countByStudentId(studentId));

            // 统计待完成任务 - 基于assignments和student_classes
            dashboard.put("pendingAssignments", getPendingAssignmentsCount(studentId));

            // 统计今日学习时长 - 基于learning_records
            dashboard.put("todayStudyTime", learningRecordsService.getTodayStudyTime(studentId));

            // 班级列表 - 基于student_classes关联classes和courses
            dashboard.put("myClasses", studentClassService.getStudentClassesWithDetails(studentId));

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
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getStudentCourses(Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            Map<String, Object> result = new HashMap<>();
            // 查询学生加入的班级及其关联的课程
            result.put("courses", studentClassService.getStudentCoursesWithProgress(studentId));

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取课程列表失败：" + e.getMessage());
        }
    }

    /**
     * 获取课程下的单元列表
     */
    @GetMapping("/courses/{courseId}/units")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getCourseUnits(
            @PathVariable Long courseId,
            Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

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
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getUnitContent(
            @PathVariable Long unitId,
            Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

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
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getTextDetail(
            @PathVariable Long textId,
            Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            Map<String, Object> detail = learningRecordsService.getTextDetailWithAnnotation(studentId, textId);

            // 记录课文学习记录
            learningRecordsService.recordTextView(studentId, textId);

            return Result.success(detail);
        } catch (Exception e) {
            return Result.error("获取课文详情失败：" + e.getMessage());
        }
    }

    /**
     * 获取待完成任务数量
     */
    private int getPendingAssignmentsCount(Long studentId) {
        // 获取学生所在的所有班级ID
        List<Long> classIds = studentClassService.getClassIdsByStudentId(studentId);

        if (classIds == null || classIds.isEmpty()) {
            return 0;
        }

        // 获取这些班级的所有任务（带状态）
        Map<String, Object> assignmentsData = assignmentService.getStudentAssignmentsWithStatus(classIds, studentId, null);

        if (assignmentsData == null || !assignmentsData.containsKey("assignments")) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) assignmentsData.get("assignments");

        // 筛选出待完成的任务（未完成且未过期）
        int pendingCount = 0;
        for (Map<String, Object> assignment : assignments) {
            String status = (String) assignment.get("status");
            // 统计未开始和进行中的任务（排除已完成和已过期）
            if ("not-started".equals(status) || "in-progress".equals(status)) {
                pendingCount++;
            }
        }

        return pendingCount;
    }

    /**
     * 获取待完成任务列表
     */
    private Object getPendingAssignments(Long studentId) {
        // 获取学生所在的所有班级ID
        List<Long> classIds = studentClassService.getClassIdsByStudentId(studentId);

        if (classIds == null || classIds.isEmpty()) {
            return new Object[0];
        }

        // 获取这些班级的所有任务（带状态）
        Map<String, Object> assignmentsData = assignmentService.getStudentAssignmentsWithStatus(classIds, studentId, null);

        if (assignmentsData == null || !assignmentsData.containsKey("assignments")) {
            return new Object[0];
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) assignmentsData.get("assignments");

        // 筛选出待完成的任务（未完成且未过期），并只返回必要的字段
        List<Map<String, Object>> pendingAssignments = new java.util.ArrayList<>();
        for (Map<String, Object> assignment : assignments) {
            String status = (String) assignment.get("status");
            // 只返回未开始和进行中的任务（排除已完成和已过期）
            if ("not-started".equals(status) || "in-progress".equals(status)) {
                Map<String, Object> pendingAssignment = new HashMap<>();
                pendingAssignment.put("id", assignment.get("id"));
                pendingAssignment.put("title", assignment.get("title"));
                pendingAssignment.put("deadline", assignment.get("deadline"));
                pendingAssignment.put("className", assignment.get("className"));
                pendingAssignment.put("unitName", assignment.get("unitName"));
                pendingAssignment.put("status", status);
                pendingAssignment.put("progress", assignment.get("progress"));
                pendingAssignments.add(pendingAssignment);
            }
        }

        // 按截止时间排序，最近截止的排在前面
        pendingAssignments.sort((a, b) -> {
            Object deadlineA = a.get("deadline");
            Object deadlineB = b.get("deadline");

            if (deadlineA == null && deadlineB == null) return 0;
            if (deadlineA == null) return 1;
            if (deadlineB == null) return -1;

            return ((java.time.LocalDateTime) deadlineA).compareTo((java.time.LocalDateTime) deadlineB);
        });

        return pendingAssignments;
    }
}