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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 学生任务管理控制器
 */
@RestController
@RequestMapping("/api/student/assignments")
public class StudentAssignmentController {

    private static final Logger logger = LoggerFactory.getLogger(StudentAssignmentController.class);

    @Autowired
    private AssignmentService assignmentsService;

    @Autowired
    private StudentClassesService studentClassesService;

    @Autowired
    private LearningRecordsService learningRecordsService;

    @Autowired
    private UserService userService;

    /**
     * 获取学生任务列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getStudentAssignments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            // 如果有关键词搜索，搜索所有已发布任务（不限班级）
            if (keyword != null && !keyword.trim().isEmpty()) {
                Map<String, Object> result = assignmentsService.searchAllPublishedAssignments(keyword.trim(), studentId, status);
                return Result.success(result);
            }

            // 如果指定了单元ID，获取该单元的任务
            if (unitId != null) {
                java.util.List<Map<String, Object>> assignments = assignmentsService.getAssignmentsByUnitId(unitId, studentId);
                Map<String, Object> result = new HashMap<>();
                result.put("assignments", assignments != null ? assignments : java.util.Collections.emptyList());
                result.put("total", assignments != null ? assignments.size() : 0);
                return Result.success(result);
            }

            // 如果指定了课程ID，获取该课程的任务
            if (courseId != null) {
                java.util.List<Map<String, Object>> assignments = assignmentsService.getAssignmentsByCourseId(courseId, studentId);
                Map<String, Object> result = new HashMap<>();
                result.put("assignments", assignments != null ? assignments : java.util.Collections.emptyList());
                result.put("total", assignments != null ? assignments.size() : 0);
                return Result.success(result);
            }

            // 获取学生加入的班级
            java.util.List<Long> classIds = studentClassesService.getClassIdsByStudentId(studentId);

            // 查询这些班级的任务
            Map<String, Object> result = assignmentsService.getStudentAssignmentsWithStatus(classIds, studentId, status);

            return Result.success(result);
        } catch (com.english.exception.BusinessException e) {
            logger.error("获取任务列表失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取任务列表失败", e);
            return Result.error("获取任务列表失败，请稍后重试");
        }
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{assignmentId}")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getAssignmentDetail(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> detail = assignmentsService.getAssignmentDetailWithProgress(assignmentId, studentId);

            return Result.success(detail);
        } catch (com.english.exception.BusinessException e) {
            logger.error("获取任务详情失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取任务详情失败", e);
            return Result.error("获取任务详情失败，请稍后重试");
        }
    }

    /**
     * 开始执行任务
     */
    @PostMapping("/{assignmentId}/start")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> startAssignment(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> startInfo = assignmentsService.startAssignment(assignmentId, studentId);

            return Result.success(startInfo);
        } catch (com.english.exception.BusinessException e) {
            logger.error("开始任务失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("开始任务失败", e);
            return Result.error("开始任务失败，请稍后重试");
        }
    }

    /**
     * 获取任务学习内容
     */
    @GetMapping("/{assignmentId}/content")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getAssignmentContent(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> content = assignmentsService.getAssignmentContent(assignmentId, studentId);

            return Result.success(content);
        } catch (com.english.exception.BusinessException e) {
            logger.error("获取任务内容失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取任务内容失败", e);
            return Result.error("获取任务内容失败，请稍后重试");
        }
    }

    /**
     * 提交任务完成情况
     */
    @PostMapping("/{assignmentId}/complete")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> completeAssignment(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Object> completionData,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            // 验证任务 deadline
            com.english.entity.Assignment assignment = assignmentsService.getAssignmentById(assignmentId);
            if (assignment == null) {
                return Result.error("任务不存在");
            }
            if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
                return Result.error("任务已截止，无法再提交");
            }

            assignmentsService.completeAssignment(assignmentId, studentId, completionData);

            return Result.success("任务提交成功");
        } catch (com.english.exception.BusinessException e) {
            logger.error("提交任务失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("提交任务失败", e);
            return Result.error("提交任务失败，请稍后重试");
        }
    }

    /**
     * 获取任务完成统计
     */
    @GetMapping("/{assignmentId}/progress")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getAssignmentProgress(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        try {
            Long studentId = getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> progress = assignmentsService.getAssignmentProgress(assignmentId, studentId);
            if (progress == null) {
                progress = new HashMap<>();
            }

            return Result.success(progress);
        } catch (com.english.exception.BusinessException e) {
            logger.error("获取任务进度失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取任务进度失败", e);
            return Result.error("获取任务进度失败，请稍后重试");
        }
    }

    /**
     * 获取任务学习记录
     */
    @GetMapping("/{assignmentId}/records")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<Map<String, Object>> getAssignmentRecords(
            @PathVariable Long assignmentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            Long studentId = getCurrentStudentId(authentication);

            // 检查权限
            if (!canAccessAssignment(studentId, assignmentId)) {
                return Result.error("无权访问此任务");
            }

            Map<String, Object> records = learningRecordsService.getAssignmentLearningRecords(
                assignmentId, studentId, page, size);

            return Result.success(records);
        } catch (com.english.exception.BusinessException e) {
            logger.error("获取学习记录失败", e);
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取学习记录失败", e);
            return Result.error("获取学习记录失败，请稍后重试");
        }
    }


    /**
     * 获取当前学生ID
     */
    private Long getCurrentStudentId(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new com.english.exception.BusinessException("用户不存在");
        }
        return user.getId();
    }

    /**
     * 检查学生是否可以访问任务
     * IDOR防护: 验证任务是否属于学生所在的班级
     */
    private boolean canAccessAssignment(Long studentId, Long assignmentId) {
        try {
            // 验证参数
            if (studentId == null || studentId <= 0 || assignmentId == null || assignmentId <= 0) {
                return false;
            }

            // 获取学生加入的班级ID列表
            java.util.List<Long> studentClassIds = studentClassesService.getClassIdsByStudentId(studentId);

            // 检查任务是否属于学生加入的班级
            return assignmentsService.isAssignmentInClasses(assignmentId, studentClassIds);
        } catch (com.english.exception.BusinessException e) {
            // 业务异常：用户不存在、班级不存在等，安全地拒绝访问
            logger.debug("权限检查业务异常: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            // 系统异常：数据库错误等，记录日志并拒绝访问
            logger.error("权限检查异常，assignmentId={}, studentId={}", assignmentId, studentId, e);
            return false;
        }
    }
}
