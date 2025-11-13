package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Class;
import com.english.entity.Course;
import com.english.entity.User;
import com.english.mapper.ClassMapper;
import com.english.service.ClassService;
import com.english.service.CourseService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClassServiceImpl extends ServiceImpl<ClassMapper, Class> implements ClassService {

    @Autowired
    private CourseService courseService;

    @Autowired
    private UserService userService;

    @Override
    public Map<String, Object> getClassesByPage(Map<String, Object> params) {
        int page = (int) params.get("page");
        int size = (int) params.get("size");
        String keyword = (String) params.get("keyword");
        Long courseId = params.get("courseId") != null ? Long.valueOf(params.get("courseId").toString()) : null;
        Long teacherId = params.get("teacherId") != null ? Long.valueOf(params.get("teacherId").toString()) : null;

        Page<Class> pageParam = new Page<>(page, size);
        IPage<Class> pageResult = baseMapper.selectClassesWithDetails(pageParam, courseId, teacherId, keyword);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public Class getClassById(Long id) {
        return baseMapper.selectClassWithDetails(id);
    }

    @Override
    @Transactional
    public void createClass(Class classInfo) {
        // 验证课程是否存在
        Course course = courseService.getCourseById(classInfo.getCourseId());
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }

        // 验证教师是否存在且角色为教师
        User teacher = userService.getUserById(classInfo.getTeacherId());
        if (teacher == null || !"TEACHER".equals(teacher.getRole())) {
            throw new RuntimeException("指定的教师不存在或角色不正确");
        }

        // 检查班级名称是否已存在
        if (checkClassNameExists(classInfo.getName(), null)) {
            throw new RuntimeException("班级名称已存在");
        }

        classInfo.setCreatedTime(LocalDateTime.now());
        save(classInfo);
    }

    @Override
    @Transactional
    public void updateClass(Class classInfo) {
        Class existingClass = getClassById(classInfo.getId());
        if (existingClass == null) {
            throw new RuntimeException("班级不存在");
        }

        // 如果修改了课程ID，验证课程是否存在
        if (!existingClass.getCourseId().equals(classInfo.getCourseId())) {
            Course course = courseService.getCourseById(classInfo.getCourseId());
            if (course == null) {
                throw new RuntimeException("课程不存在");
            }
        }

        // 如果修改了教师ID，验证教师是否存在且角色为教师
        if (!existingClass.getTeacherId().equals(classInfo.getTeacherId())) {
            User teacher = userService.getUserById(classInfo.getTeacherId());
            if (teacher == null || !"TEACHER".equals(teacher.getRole())) {
                throw new RuntimeException("指定的教师不存在或角色不正确");
            }
        }

        // 检查班级名称是否已存在（排除自己）
        if (checkClassNameExists(classInfo.getName(), classInfo.getId())) {
            throw new RuntimeException("班级名称已存在");
        }

        updateById(classInfo);
    }

    @Override
    @Transactional
    public void deleteClass(Long id) {
        Class classInfo = getClassById(id);
        if (classInfo == null) {
            throw new RuntimeException("班级不存在");
        }

        // 检查班级是否还有学生
        // TODO: 添加学生班级关联检查
        removeById(id);
    }

    @Override
    public List<Class> getClassesByTeacherId(Long teacherId) {
        return baseMapper.selectClassesByTeacherId(teacherId);
    }

    @Override
    public List<Class> getClassesByStudentId(Long studentId) {
        // 使用新的查询方法，包含关联信息和加入时间
        return baseMapper.selectClassesByStudentIdWithJoinTime(studentId);
    }

    @Override
    public List<Class> getClassesByCourseId(Long courseId) {
        QueryWrapper<Class> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId);
        return list(queryWrapper);
    }

    @Override
    public List<Class> searchClasses(String keyword) {
        return baseMapper.selectClassesWithDetails(
            new Page<>(1, 100), null, null, keyword
        ).getRecords();
    }

    @Override
    public Map<String, Object> getClassStats() {
        Map<String, Object> stats = new HashMap<>();

        Long totalCount = baseMapper.selectClassCount();
        stats.put("totalClasses", totalCount);

        // 可以添加更多统计信息
        // 如：按课程分类的班级数量、按教师分类的班级数量等

        return stats;
    }

    @Override
    public boolean checkClassNameExists(String name, Long excludeId) {
        QueryWrapper<Class> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", name);
        if (excludeId != null) {
            queryWrapper.ne("id", excludeId);
        }
        return count(queryWrapper) > 0;
    }

    @Override
    public boolean validateTeacherPermission(Long teacherId, Long classId) {
        Class classInfo = getClassById(classId);
        return classInfo != null && classInfo.getTeacherId().equals(teacherId);
    }
}