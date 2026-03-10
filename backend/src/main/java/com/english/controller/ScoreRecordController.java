package com.english.controller;

import com.english.dto.Result;
import com.english.entity.ScoreRecord;
import com.english.service.ScoreRecordService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@RestController
@RequestMapping("/api/scores")
public class ScoreRecordController {

    private static final Logger logger = LoggerFactory.getLogger(ScoreRecordController.class);

    @Autowired
    private ScoreRecordService scoreRecordService;

    @Autowired
    private UserService userService;
    
    @Autowired
    private com.english.service.AssignmentService assignmentService;
    
    @Autowired
    private com.english.service.ClassService classService;

    @Autowired
    private com.english.service.StudentClassesService studentClassService;

    /**
     * 记录分数（教师/管理员）
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> recordScore(@RequestBody ScoreRecord scoreRecord, Authentication authentication) {
        try {
            String userRole = getCurrentRole(authentication);

            // 教师只能为自己班级的学生记录分数
            if ("ROLE_TEACHER".equals(userRole)) {
                Long teacherId = getCurrentUserId(authentication);

                // 如果有关联的任务，验证教师是否有权限
                if (scoreRecord.getAssignmentId() != null) {
                    var assignment = assignmentService.getById(scoreRecord.getAssignmentId());
                    if (assignment != null && assignment.getClassId() != null) {
                        var classInfo = classService.getClassById(assignment.getClassId());
                        if (classInfo == null || !teacherId.equals(classInfo.getTeacherId())) {
                            return Result.error("没有权限为该学生记录分数");
                        }
                    }
                } else {
                    // 没有关联任务时，教师必须通过班级关系验证
                    return Result.error("教师记录分数必须关联一个任务");
                }
            }

            // 管理员可以记录任何学生的分数

            scoreRecordService.recordScore(scoreRecord);
            return Result.success("分数记录成功");
        } catch (com.english.exception.BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("记录分数失败", e);

            return Result.error("记录分数失败，请稍后重试");
        }
    }

    /**
     * 获取学生分数记录列表
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<List<Map<String, Object>>> getStudentScores(
            @PathVariable Long studentId,
            @RequestParam(required = false) String scoreType,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Long assignmentId,
            Authentication authentication) {
        try {
            String userRole = getCurrentRole(authentication);

            // 教师需要验证学生是否属于自己的班级
            if ("ROLE_TEACHER".equals(userRole)) {
                Long teacherId = getCurrentUserId(authentication);

                // 获取学生所属班级
                List<Long> studentClassIds = studentClassService.getClassIdsByStudentId(studentId);
                if (studentClassIds == null || studentClassIds.isEmpty()) {
                    return Result.error("学生不属于任何班级");
                }

                // 检查班级是否被教师管理
                boolean hasAccess = false;
                for (Long classId : studentClassIds) {
                    var classInfo = classService.getClassById(classId);
                    if (classInfo != null && teacherId.equals(classInfo.getTeacherId())) {
                        hasAccess = true;
                        break;
                    }
                }

                if (!hasAccess) {
                    return Result.error("没有权限查看该学生的分数记录");
                }
            }
            // 管理员可以查看任何学生的分数记录

            List<Map<String, Object>> scores = scoreRecordService.getStudentScores(
                    studentId, scoreType, courseId, unitId, assignmentId);
            return Result.success(scores);
        } catch (Exception e) {
            logger.error("获取分数记录失败", e);

            return Result.error("获取分数记录失败，请稍后重试");
        }
    }

    /**
     * 获取自己的分数记录（学生端）
     */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<List<Map<String, Object>>> getMyScores(
            @RequestParam(required = false) String scoreType,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Long assignmentId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            List<Map<String, Object>> scores = scoreRecordService.getStudentScores(
                    studentId, scoreType, courseId, unitId, assignmentId);
            return Result.success(scores);
        } catch (Exception e) {
            logger.error("获取分数记录失败", e);

            return Result.error("获取分数记录失败，请稍后重试");
        }
    }

    /**
     * 获取学生平均分数
     */
    @GetMapping("/student/{studentId}/average")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getStudentAverageScore(
            @PathVariable Long studentId,
            @RequestParam(required = false) String scoreType,
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {
        try {
            String userRole = getCurrentRole(authentication);

            // 教师需要验证学生是否属于自己的班级
            if ("ROLE_TEACHER".equals(userRole)) {
                Long teacherId = getCurrentUserId(authentication);

                // 获取学生所属班级
                List<Long> studentClassIds = studentClassService.getClassIdsByStudentId(studentId);
                if (studentClassIds == null || studentClassIds.isEmpty()) {
                    return Result.error("学生不属于任何班级");
                }

                // 检查班级是否被教师管理
                boolean hasAccess = false;
                for (Long classId : studentClassIds) {
                    var classInfo = classService.getClassById(classId);
                    if (classInfo != null && teacherId.equals(classInfo.getTeacherId())) {
                        hasAccess = true;
                        break;
                    }
                }

                if (!hasAccess) {
                    return Result.error("没有权限查看该学生的分数记录");
                }
            }
            // 管理员可以查看任何学生的平均分数

            Map<String, Object> stats = scoreRecordService.getStudentAverageScore(studentId, scoreType, courseId);
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取平均分数失败", e);

            return Result.error("获取平均分数失败，请稍后重试");
        }
    }

    /**
     * 获取分数统计
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getScoreStatistics(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Long assignmentId,
            Authentication authentication) {
        try {
            var authorities = authentication.getAuthorities();
            String userRole = authorities.isEmpty() ? "" : authorities.iterator().next().getAuthority();

            // 教师只能查看自己班级相关的分数统计
            if ("ROLE_TEACHER".equals(userRole)) {
                Long teacherId = getCurrentUserId(authentication);

                // 如果指定了任务，验证教师是否有权限
                if (assignmentId != null) {
                    var assignment = assignmentService.getById(assignmentId);
                    if (assignment == null || assignment.getClassId() == null) {
                        return Result.error("任务不存在或无效");
                    }
                    var classInfo = classService.getClassById(assignment.getClassId());
                    if (classInfo == null || !teacherId.equals(classInfo.getTeacherId())) {
                        return Result.error("没有权限查看该任务的统计信息");
                    }
                }
                // 如果指定了课程，暂时允许（统计是只读的），但更严格的设计应该验证班级
            }
            // 管理员可以查看全局统计

            Map<String, Object> stats = scoreRecordService.getScoreStatistics(courseId, unitId, assignmentId);
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取分数统计失败", e);

            return Result.error("获取分数统计失败，请稍后重试");
        }
    }

    /**
     * 更新任务分数（基于任务完成情况自动计算）
     */
    @PostMapping("/assignment/{assignmentId}/update")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    public Result<String> updateAssignmentScore(
            @PathVariable Long assignmentId,
            Authentication authentication) {
        try {
            Long studentId = getCurrentStudentId(authentication);
            scoreRecordService.updateAssignmentScore(assignmentId, studentId);
            return Result.success("分数更新成功");
        } catch (Exception e) {
            logger.error("更新分数失败", e);

            return Result.error("更新分数失败，请稍后重试");
        }
    }

    /**
     * 导出班级成绩（Excel格式）
     */
    @GetMapping("/class/{classId}/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public org.springframework.http.ResponseEntity<?> exportClassScores(
            @PathVariable Long classId,
            Authentication authentication) {
        try {
            String userRole = getCurrentRole(authentication);

            // 教师只能导出自己管理的班级成绩
            if ("ROLE_TEACHER".equals(userRole)) {
                Long teacherId = getCurrentUserId(authentication);
                var classInfo = classService.getClassById(classId);
                if (classInfo == null || !teacherId.equals(classInfo.getTeacherId())) {
                    return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body("没有权限导出该班级的成绩");
                }
            }
            
            // 获取班级所有学生的成绩数据
            List<Map<String, Object>> exportData = scoreRecordService.exportClassScores(classId);
            
            // 生成Excel文件
            byte[] excelBytes = scoreRecordService.generateClassScoresExcel(exportData, classId);
            
            // 获取班级名称
            var classInfo = classService.getClassById(classId);
            String className = classInfo != null ? classInfo.getName() : "班级";
            String fileName = className + "_成绩_" + java.time.LocalDate.now() + ".xlsx";
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", 
                new String(fileName.getBytes("UTF-8"), "ISO-8859-1"));
            
            return org.springframework.http.ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
        } catch (Exception e) {
            logger.error("导出成绩失败", e);
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body("导出失败，请稍后重试");
        }
    }

    private Long getCurrentStudentId(Authentication authentication) {
        com.english.entity.User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            throw new com.english.exception.BusinessException("用户不存在");
        }
        return user.getId();
    }

    private Long getCurrentUserId(Authentication authentication) {
        return getCurrentStudentId(authentication);
    }

    private String getCurrentRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            return "";
        }
        return authentication.getAuthorities().iterator().next().getAuthority();
    }
}

