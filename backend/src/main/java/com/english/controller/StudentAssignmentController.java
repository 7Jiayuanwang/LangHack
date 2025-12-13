package com.english.controller;

import com.english.constants.RoleConstants;
import com.english.dto.Result;
import com.english.service.*;
import com.english.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学生任务管理控制器
 */
@RestController
@RequestMapping("/api/student/assignments")
public class StudentAssignmentController {

    @Autowired
    private AssignmentService assignmentsService;

    @Autowired
    private StudentClassService studentClassService;

    @Autowired
    private LearningRecordsService learningRecordsService;

    /**
     * 获取学生任务列表
     */
    @GetMapping
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getStudentAssignments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String courseId,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            // 获取学生加入的班级
            java.util.List<Long> classIds = studentClassService.getClassIdsByStudentId(studentId);

            // 查询这些班级的任务
            Map<String, Object> result = assignmentsService.getStudentAssignmentsWithStatus(classIds, studentId, status);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取任务列表失败：" + e.getMessage());
        }
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{assignmentId}")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getAssignmentDetail(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> detail = assignmentsService.getAssignmentDetailWithProgress(assignmentId, studentId);

            return Result.success(detail);
        } catch (Exception e) {
            return Result.error("获取任务详情失败：" + e.getMessage());
        }
    }

    /**
     * 开始执行任务
     */
    @PostMapping("/{assignmentId}/start")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> startAssignment(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> startInfo = assignmentsService.startAssignment(assignmentId, studentId);

            return Result.success(startInfo);
        } catch (Exception e) {
            return Result.error("开始任务失败：" + e.getMessage());
        }
    }

    /**
     * 获取任务学习内容
     */
    @GetMapping("/{assignmentId}/content")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getAssignmentContent(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> content = assignmentsService.getAssignmentContent(assignmentId, studentId);

            return Result.success(content);
        } catch (Exception e) {
            return Result.error("获取任务内容失败：" + e.getMessage());
        }
    }

    /**
     * 提交任务完成情况
     */
    @PostMapping("/{assignmentId}/complete")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<String> completeAssignment(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Object> completionData,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            assignmentsService.completeAssignment(assignmentId, studentId, completionData);

            return Result.success("任务提交成功");
        } catch (Exception e) {
            return Result.error("提交任务失败：" + e.getMessage());
        }
    }

    /**
     * 获取任务完成统计
     */
    @GetMapping("/{assignmentId}/progress")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getAssignmentProgress(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> progress = assignmentsService.getAssignmentProgress(assignmentId, studentId);

            return Result.success(progress);
        } catch (Exception e) {
            return Result.error("获取任务进度失败：" + e.getMessage());
        }
    }

    /**
     * 获取任务学习记录
     */
    @GetMapping("/{assignmentId}/records")
    @PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
    public Result<Map<String, Object>> getAssignmentRecords(
            @PathVariable Long assignmentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> records = learningRecordsService.getAssignmentLearningRecords(
                assignmentId, studentId, page, size);

            return Result.success(records);
        } catch (Exception e) {
            return Result.error("获取学习记录失败：" + e.getMessage());
        }
    }

    /**
     * 检查学生是否可以访问任务
     */
    private boolean canAccessAssignment(Long studentId, Long assignmentId) {
        try {
            // 获取学生加入的班级ID列表
            java.util.List<Long> studentClassIds = studentClassService.getClassIdsByStudentId(studentId);

            // 检查任务是否属于学生加入的班级
            return assignmentsService.isAssignmentInClasses(assignmentId, studentClassIds);
        } catch (Exception e) {
            return false;
        }
    }
}
