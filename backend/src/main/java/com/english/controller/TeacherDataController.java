package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.Result;
import com.english.entity.TeachingClass;
import com.english.mapper.TeachingClassMapper;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师个人数据查询控制器
 * <p>
 * 教师登录后查看自己的授课信息
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherDataController {

    private static final Logger logger = LoggerFactory.getLogger(TeacherDataController.class);

    @Autowired
    private TeachingClassMapper teachingClassMapper;

    @Autowired
    private UserService userService;

    /**
     * 获取当前教师的授课班级列表
     * GET /api/teacher/my-courses?academicYear=2025-2026&semester=1
     */
    @GetMapping("/my-courses")
    @PreAuthorize("hasAnyRole('TEACHER')")
    public Result<List<TeachingClass>> getMyCourses(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            Authentication authentication) {
        try {
            String teacherId = getTeacherId(authentication);
            QueryWrapper<TeachingClass> wrapper = new QueryWrapper<>();
            wrapper.eq("teacher_id", teacherId);
            if (academicYear != null && !academicYear.isEmpty()) {
                wrapper.eq("academic_year", academicYear);
            }
            if (semester != null) {
                wrapper.eq("semester", semester);
            }
            wrapper.orderByDesc("academic_year", "semester");
            return Result.success(teachingClassMapper.selectList(wrapper));
        } catch (Exception e) {
            logger.error("获取教师授课信息失败", e);
            return Result.error("获取授课信息失败，请稍后重试");
        }
    }

    private String getTeacherId(Authentication authentication) {
        com.english.entity.User user = userService.getUserByUsername(authentication.getName());
        if (user == null) {
            throw new RuntimeException("用户信息不存在");
        }
        return user.getUsername();
    }
}
