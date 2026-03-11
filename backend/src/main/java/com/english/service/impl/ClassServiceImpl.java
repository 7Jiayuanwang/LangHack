package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Class;
import com.english.entity.Course;
import com.english.entity.User;
import com.english.entity.StudentClass;
import com.english.mapper.ClassMapper;
import com.english.mapper.StudentClassMapper;
import com.english.service.ClassService;
import com.english.service.CourseService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import com.english.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClassServiceImpl extends ServiceImpl<ClassMapper, Class> implements ClassService {

    private static final Logger logger = LoggerFactory.getLogger(ClassServiceImpl.class);

    @Autowired
    @Lazy
    private CourseService courseService;

    @Autowired
    private UserService userService;

    @Autowired
    private StudentClassMapper studentClassMapper;

    @Override
    @Transactional
    public Map<String, Object> getClassesByPage(Map<String, Object> params) {
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int size = params.get("size") != null ? ((Number) params.get("size")).intValue() : 10;
        String keyword = (String) params.get("keyword");
        Long courseId = params.get("courseId") != null ? Long.valueOf(params.get("courseId").toString()) : null;
        Long teacherId = params.get("teacherId") != null ? Long.valueOf(params.get("teacherId").toString()) : null;

        Page<Class> pageParam = new Page<>(page, size);
        IPage<Class> pageResult = baseMapper.selectClassesWithDetails(pageParam, courseId, teacherId, keyword);

        // 为每个班级添加学生数量统计
        List<Map<String, Object>> recordsWithStudentCount = new ArrayList<>();
        for (Class classInfo : pageResult.getRecords()) {
            Map<String, Object> classMap = new HashMap<>();
            classMap.put("id", classInfo.getId());
            classMap.put("name", classInfo.getName());
            classMap.put("courseId", classInfo.getCourseId());
            classMap.put("teacherId", classInfo.getTeacherId());
            classMap.put("description", classInfo.getDescription());
            classMap.put("createdTime", classInfo.getCreatedTime());

            // 添加课程信息
            if (classInfo.getCourse() != null) {
                Map<String, Object> courseMap = new HashMap<>();
                courseMap.put("id", classInfo.getCourse().getId());
                courseMap.put("name", classInfo.getCourse().getName());
                courseMap.put("description", classInfo.getCourse().getDescription());
                classMap.put("course", courseMap);
            } else {
                // 如果course对象为null，但courseId存在，尝试从courseService获取
                if (classInfo.getCourseId() != null) {
                    try {
                        Course course = courseService.getCourseById(classInfo.getCourseId());
                        if (course != null) {
                            Map<String, Object> courseMap = new HashMap<>();
                            courseMap.put("id", course.getId());
                            courseMap.put("name", course.getName());
                            courseMap.put("description", course.getDescription());
                            classMap.put("course", courseMap);
                        } else {
                            logger.warn("课程 {} 不存在或已删除", classInfo.getCourseId());
                        }
                    } catch (Exception e) {
                        logger.warn("获取课程信息失败", e);
                    }
                }
            }

            // 添加教师信息
            if (classInfo.getTeacher() != null) {
                Map<String, Object> teacherMap = new HashMap<>();
                teacherMap.put("id", classInfo.getTeacher().getId());
                teacherMap.put("username", classInfo.getTeacher().getUsername());
                teacherMap.put("nickname", classInfo.getTeacher().getNickname());
                teacherMap.put("realName", classInfo.getTeacher().getRealName());
                teacherMap.put("email", classInfo.getTeacher().getEmail());
                classMap.put("teacher", teacherMap);
            } else {
                // 如果teacher对象为null，但teacherId存在，尝试从userService获取
                if (classInfo.getTeacherId() != null) {
                    try {
                        User teacher = userService.getUserById(classInfo.getTeacherId());
                        if (teacher != null) {
                            Map<String, Object> teacherMap = new HashMap<>();
                            teacherMap.put("id", teacher.getId());
                            teacherMap.put("username", teacher.getUsername());
                            teacherMap.put("nickname", teacher.getNickname());
                            teacherMap.put("realName", teacher.getRealName());
                            teacherMap.put("email", teacher.getEmail());
                            classMap.put("teacher", teacherMap);
                        }
                    } catch (Exception e) {
                        logger.warn("获取教师信息失败: {}", e.getMessage(), e);
                    }
                }
            }

            // 添加学生数量
            classMap.put("studentCount", getStudentCount(classInfo.getId()));

            recordsWithStudentCount.add(classMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", recordsWithStudentCount);
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
        // 验证班级名称
        if (classInfo.getName() == null || classInfo.getName().trim().isEmpty()) {
            throw new BusinessException("班级名称不能为空");
        }
        if (classInfo.getCourseId() == null) {
            throw new BusinessException("课程ID不能为空");
        }
        if (classInfo.getTeacherId() == null) {
            throw new BusinessException("教师ID不能为空");
        }

        // 验证课程是否存在
        Course course = courseService.getCourseById(classInfo.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }

        // 验证教师是否存在且角色为教师
        User teacher = userService.getUserById(classInfo.getTeacherId());
        if (teacher == null || !"TEACHER".equals(teacher.getRole())) {
            throw new BusinessException("指定的教师不存在或角色不正确");
        }

        // 检查班级名称是否已存在
        if (checkClassNameExists(classInfo.getName(), null)) {
            throw new BusinessException("班级名称已存在");
        }

        classInfo.setCreatedTime(LocalDateTime.now());
        save(classInfo);
    }

    @Override
    @Transactional
    public void updateClass(Class classInfo) {
        // 验证班级名称
        if (classInfo.getName() == null || classInfo.getName().trim().isEmpty()) {
            throw new BusinessException("班级名称不能为空");
        }

        Class existingClass = getClassById(classInfo.getId());
        if (existingClass == null) {
            throw new BusinessException("班级不存在");
        }

        // 如果修改了课程ID，验证课程是否存在
        if (!java.util.Objects.equals(existingClass.getCourseId(), classInfo.getCourseId())) {
            if (classInfo.getCourseId() != null) {
                Course course = courseService.getCourseById(classInfo.getCourseId());
                if (course == null) {
                    throw new BusinessException("课程不存在");
                }
            }
        }

        // 如果修改了教师ID，验证教师是否存在且角色为教师
        if (!java.util.Objects.equals(existingClass.getTeacherId(), classInfo.getTeacherId())) {
            if (classInfo.getTeacherId() != null) {
                User teacher = userService.getUserById(classInfo.getTeacherId());
                if (teacher == null || !"TEACHER".equals(teacher.getRole())) {
                    throw new BusinessException("指定的教师不存在或角色不正确");
                }
            }
        }

        // 检查班级名称是否已存在（排除自己）
        if (checkClassNameExists(classInfo.getName(), classInfo.getId())) {
            throw new BusinessException("班级名称已存在");
        }

        updateById(classInfo);
    }

    @Override
    @Transactional
    public void deleteClass(Long id) {
        Class classInfo = getClassById(id);
        if (classInfo == null) {
            throw new BusinessException("班级不存在");
        }

        // 检查班级是否还有学生，有则拒绝删除
        QueryWrapper<StudentClass> studentWrapper = new QueryWrapper<>();
        studentWrapper.eq("class_id", id);
        long studentCount = studentClassMapper.selectCount(studentWrapper);
        if (studentCount > 0) {
            throw new BusinessException("班级下还有 " + studentCount + " 名学生，请先移除学生后再删除班级");
        }

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
                new Page<>(1, 100), null, null, keyword).getRecords();
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
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        QueryWrapper<Class> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", name);
        if (excludeId != null) {
            queryWrapper.ne("id", excludeId);
        }
        return count(queryWrapper) > 0;
    }

    @Override
    public boolean validateTeacherPermission(Long teacherId, Long classId) {
        if (teacherId == null || classId == null) {
            return false;
        }
        Class classInfo = getClassById(classId);
        return classInfo != null && classInfo.getTeacherId() != null && teacherId.equals(classInfo.getTeacherId());
    }

    /**
     * 获取班级学生数量
     */
    private Integer getStudentCount(Long classId) {
        if (classId == null)
            return 0;
        try {
            QueryWrapper<StudentClass> wrapper = new QueryWrapper<>();
            wrapper.eq("class_id", classId);
            long count = studentClassMapper.selectCount(wrapper);
            return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
        } catch (Exception e) {
            logger.error("获取班级学生数量失败", e);
            return 0;
        }
    }

    @Override
    public Class getClassByName(String name) {
        QueryWrapper<Class> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", name);
        queryWrapper.last("LIMIT 1");
        return getOne(queryWrapper);
    }

    @Override
    @Transactional
    public void createClassForImport(Class classInfo) {
        // 检查班级名称是否已存在
        if (checkClassNameExists(classInfo.getName(), null)) {
            throw new BusinessException("班级名称已存在");
        }

        classInfo.setCreatedTime(LocalDateTime.now());

        if (classInfo.getDescription() == null) {
            classInfo.setDescription("通过批量导入自动创建");
        }

        save(classInfo);
    }
}