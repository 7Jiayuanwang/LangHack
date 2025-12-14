package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Course;
import com.english.entity.Unit;
import com.english.entity.Text;
import com.english.entity.UnitWord;
import com.english.mapper.CourseMapper;
import com.english.mapper.UnitMapper;
import com.english.mapper.TextMapper;
import com.english.mapper.UnitWordMapper;
import com.english.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private UnitWordMapper unitWordMapper;

    @Override
    public Map<String, Object> getCoursesByPage(Map<String, Object> params) {
        int page = (int) params.get("page");
        int size = (int) params.get("size");
        String keyword = (String) params.get("keyword");

        Page<Course> pageParam = new Page<>(page, size);
        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                .like("name", keyword)
                .or()
                .like("description", keyword)
            );
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
        course.setCreatedTime(LocalDateTime.now());
        course.setUpdatedTime(LocalDateTime.now());
        course.setDeleted(0);
        save(course);
    }

    @Override
    public void updateCourse(Course course) {
        course.setUpdatedTime(LocalDateTime.now());
        updateById(course);
    }

    @Override
    @Transactional
    public void deleteCourse(Long id) {
        Course course = getById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }

        // 获取该课程下的所有单元
        QueryWrapper<Unit> unitWrapper = new QueryWrapper<>();
        unitWrapper.eq("course_id", id);
        List<Unit> units = unitMapper.selectList(unitWrapper);

        if (!units.isEmpty()) {
            List<Long> unitIds = units.stream().map(Unit::getId).collect(java.util.stream.Collectors.toList());

            // 删除单元下的所有课文（软删除）
            for (Long unitId : unitIds) {
                QueryWrapper<Text> textWrapper = new QueryWrapper<>();
                textWrapper.eq("unit_id", unitId);
                textWrapper.eq("deleted", 0);
                List<Text> texts = textMapper.selectList(textWrapper);

                for (Text text : texts) {
                    text.setDeleted(1);
                    text.setUpdatedTime(LocalDateTime.now());
                    textMapper.updateById(text);
                }
            }

            // 删除单元-单词关联
            QueryWrapper<UnitWord> unitWordWrapper = new QueryWrapper<>();
            unitWordWrapper.in("unit_id", unitIds);
            unitWordMapper.delete(unitWordWrapper);

            // 删除所有单元
            unitMapper.delete(unitWrapper);
        }

        // 软删除课程
        Course deleteCourse = new Course();
        deleteCourse.setId(id);
        deleteCourse.setDeleted(1);
        deleteCourse.setUpdatedTime(LocalDateTime.now());
        updateById(deleteCourse);
    }

    @Override
    public List<Course> getActiveCourses() {
        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();
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
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public Map<String, Object> getCourseStats() {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<Course> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("deleted", 0);
        Long totalCount = count(totalWrapper);

        stats.put("totalCourses", totalCount);

        return stats;
    }
}