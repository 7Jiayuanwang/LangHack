package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Course;
import com.english.mapper.CourseMapper;
import com.english.service.CourseService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    @Override
    public Map<String, Object> getCoursesByPage(Map<String, Object> params) {
        int page = (int) params.get("page");
        int size = (int) params.get("size");
        String keyword = (String) params.get("keyword");
        String status = (String) params.get("status");

        Page<Course> pageParam = new Page<>(page, size);
        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                .like("name", keyword)
                .or()
                .like("description", keyword)
            );
        }

        if (status != null && !status.trim().isEmpty()) {
            queryWrapper.eq("status", status);
        }

        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");

        IPage<Course> pageResult = page(pageParam, queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public Course getCourseById(Long id) {
        return getById(id);
    }

    @Override
    public void createCourse(Course course) {
        if (course.getStatus() == null) {
            course.setStatus("ACTIVE");
        }
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        course.setDeleted(0);
        save(course);
    }

    @Override
    public void updateCourse(Course course) {
        course.setUpdatedAt(LocalDateTime.now());
        updateById(course);
    }

    @Override
    public void deleteCourse(Long id) {
        Course course = new Course();
        course.setId(id);
        course.setDeleted(1);
        course.setUpdatedAt(LocalDateTime.now());
        updateById(course);
    }

    @Override
    public List<Course> getActiveCourses() {
        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", "ACTIVE");
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public List<Course> getCoursesByKeyword(String keyword) {
        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                .like("name", keyword)
                .or()
                .like("description", keyword)
            );
        }
        queryWrapper.eq("status", "ACTIVE");
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public Map<String, Object> getCourseStats() {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<Course> activeWrapper = new QueryWrapper<>();
        activeWrapper.eq("status", "ACTIVE");
        activeWrapper.eq("deleted", 0);
        Long activeCount = count(activeWrapper);

        QueryWrapper<Course> inactiveWrapper = new QueryWrapper<>();
        inactiveWrapper.eq("status", "INACTIVE");
        inactiveWrapper.eq("deleted", 0);
        Long inactiveCount = count(inactiveWrapper);

        stats.put("totalActive", activeCount);
        stats.put("totalInactive", inactiveCount);
        stats.put("totalCourses", activeCount + inactiveCount);

        return stats;
    }
}