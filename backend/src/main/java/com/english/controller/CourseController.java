package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Course;
import com.english.service.CourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);

    @Autowired
    private CourseService courseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<Map<String, Object>> getCourseList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {

        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("keyword", keyword);
            params.put("status", status);

            Map<String, Object> result = courseService.getCoursesByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取课程列表失败", e);
            return Result.error("获取课程列表失败，请稍后重试");
        }
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<List<Course>> getActiveCourses() {
        try {
            List<Course> courses = courseService.getActiveCourses();
            return Result.success(courses);
        } catch (Exception e) {
            log.error("获取活跃课程失败", e);
            return Result.error("获取活跃课程失败，请稍后重试");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT', 'USER')")
    public Result<Course> getCourseById(@PathVariable Long id) {
        try {
            Course course = courseService.getCourseById(id);
            if (course == null) {
                return Result.error("课程不存在");
            }
            return Result.success(course);
        } catch (Exception e) {
            log.error("获取课程详情失败", e);
            return Result.error("获取课程详情失败，请稍后重试");
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> createCourse(@RequestBody Course course) {
        try {
            courseService.createCourse(course);
            return Result.success("课程创建成功");
        } catch (Exception e) {
            log.error("创建课程失败", e);
            return Result.error("创建课程失败，请稍后重试");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateCourse(@PathVariable Long id, @RequestBody Course course) {
        try {
            course.setId(id);
            courseService.updateCourse(course);
            return Result.success("课程更新成功");
        } catch (Exception e) {
            log.error("更新课程失败", e);
            return Result.error("更新课程失败，请稍后重试");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteCourse(@PathVariable Long id) {
        try {
            courseService.deleteCourse(id);
            return Result.success("课程删除成功");
        } catch (Exception e) {
            log.error("删除课程失败", e);
            return Result.error("删除课程失败，请稍后重试");
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getCourseStats() {
        try {
            Map<String, Object> stats = courseService.getCourseStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取课程统计失败", e);
            return Result.error("获取课程统计失败，请稍后重试");
        }
    }
}