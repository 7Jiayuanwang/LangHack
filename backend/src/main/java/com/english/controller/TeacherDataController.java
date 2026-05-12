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
    
    @Autowired
    private com.english.mapper.StudentClassMapper studentClassMapper;

    @Autowired
    private com.english.service.VocabularyBooksService vocabularyBooksService;

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

    /**
     * 教师查看班级学生的单词学习进度
     * GET /api/teacher/word-progress?classId=123
     */
    @GetMapping("/word-progress")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<java.util.List<java.util.Map<String, Object>>> getClassWordProgress(
            @RequestParam Long classId,
            Authentication authentication) {
        try {
            if (classId == null) return Result.error("classId 不能为空");

            // 查询班级内学生
            java.util.List<com.english.entity.StudentClass> students = studentClassMapper.selectStudentsByClassId(classId);
            java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();

            if (students == null || students.isEmpty()) {
                return Result.success(result);
            }

            for (com.english.entity.StudentClass sc : students) {
                Long studentId = sc.getStudentId();
                com.english.entity.User student = userService.getUserById(studentId);
                if (student == null) continue;

                java.util.Map<String, Object> stats = vocabularyBooksService.getWordStudyStats(studentId, null);

                java.util.Map<String, Object> entry = new java.util.HashMap<>();
                entry.put("studentId", studentId);
                entry.put("username", student.getUsername());
                entry.put("realName", student.getRealName());
                entry.put("classJoinTime", sc.getJoinTime());
                entry.put("wordStats", stats != null ? stats : new java.util.HashMap<>());

                result.add(entry);
            }

            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取班级单词进度失败", e);
            return Result.error("获取班级单词进度失败，请稍后重试");
        }
    }
}
