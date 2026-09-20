package com.english.controller;

import com.english.dto.Result;
import com.english.dto.teacher.TeacherClassLearningOverviewDto;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.TeacherClassLearningService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherClassLearningController {
    private static final Logger log = LoggerFactory.getLogger(TeacherClassLearningController.class);

    private final TeacherClassLearningService learningService;
    private final UserService userService;

    public TeacherClassLearningController(TeacherClassLearningService learningService, UserService userService) {
        this.learningService = learningService;
        this.userService = userService;
    }

    @GetMapping("/{classId}/learning-overview")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<TeacherClassLearningOverviewDto> getOverview(@PathVariable Long classId,
                                                                Authentication authentication) {
        if (classId == null || classId <= 0) return Result.error(400, "无效的班级ID");
        if (authentication == null || authentication.getName() == null) {
            return Result.error(401, "用户未登录");
        }
        User teacher = userService.getUserByUsername(authentication.getName());
        if (teacher == null || teacher.getId() == null) return Result.error(401, "用户未登录");
        if (!"TEACHER".equals(teacher.getRole())) return Result.error(403, "无权查看班级学情");
        try {
            return Result.success(learningService.getOverview(teacher.getId(), classId));
        } catch (BusinessException e) {
            return Result.error(403, e.getMessage());
        } catch (Exception e) {
            log.error("获取班级学习概览失败 classId={}", classId, e);
            return Result.error("获取班级学习概览失败，请稍后重试");
        }
    }
}
