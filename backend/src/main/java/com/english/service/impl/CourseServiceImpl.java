package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Course;
import com.english.entity.Unit;
import com.english.exception.BusinessException;
import com.english.mapper.CourseMapper;
import com.english.mapper.UnitMapper;
import com.english.service.CourseService;
import com.english.service.UnitService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    private final UnitMapper unitMapper;
    private final UnitService unitService;

    public CourseServiceImpl(UnitMapper unitMapper, UnitService unitService) {
        this.unitMapper = unitMapper;
        this.unitService = unitService;
    }

    @Override
    @Cacheable(value = "courses", key = "'page:' + #params.get('page') + ':' + #params.get('size') + ':' + (#params.get('keyword') ?: 'null') + ':' + (#params.get('status') ?: 'null')", unless = "#result == null")
    public Map<String, Object> getCoursesByPage(Map<String, Object> params) {
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int size = params.get("size") != null ? ((Number) params.get("size")).intValue() : 10;
        String keyword = (String) params.getOrDefault("keyword", null);
        String status = (String) params.getOrDefault("status", null);

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
    @Cacheable(value = "courses", key = "'id:' + #id", unless = "#result == null")
    public Course getCourseById(Long id) {
        QueryWrapper<Course> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("deleted", 0);
        return getOne(wrapper);
    }

    @Override
    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
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
    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
    public void updateCourse(Course course) {
        course.setUpdatedAt(LocalDateTime.now());
        updateById(course);
    }

    @Override
    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
    public void deleteCourse(Long id) {
        // 1. 验证课程是否存在
        Course course = getCourseById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }

        // 2. 查询该课程下所有单元
        QueryWrapper<Unit> unitQuery = new QueryWrapper<>();
        unitQuery.eq("course_id", id);
        List<Unit> units = unitMapper.selectList(unitQuery);

        // 3. 对每个单元调用 unitService.deleteUnit() 执行级联删除
        if (units != null && !units.isEmpty()) {
            for (Unit unit : units) {
                unitService.deleteUnit(unit.getId());
            }
        }

        // 4. 软删除课程本身
        Course courseUpdate = new Course();
        courseUpdate.setId(id);
        courseUpdate.setDeleted(1);
        courseUpdate.setUpdatedAt(LocalDateTime.now());
        updateById(courseUpdate);
    }

    @Override
    @Cacheable(value = "courses", key = "'active'", unless = "#result == null || #result.isEmpty()")
    public List<Course> getActiveCourses() {
        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", "ACTIVE");
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    @Cacheable(value = "courses", key = "'keyword:' + (#keyword ?: 'null')", unless = "#result == null || #result.isEmpty()")
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
    @Cacheable(value = "stats", key = "'courseStats'", unless = "#result == null")
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