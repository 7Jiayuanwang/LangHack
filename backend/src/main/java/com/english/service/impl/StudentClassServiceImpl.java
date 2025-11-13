package com.english.service.impl;



import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.mapper.StudentClassMapper;
import com.english.service.ClassService;
import com.english.service.StudentClassService;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentClassServiceImpl extends ServiceImpl<StudentClassMapper, StudentClass> implements StudentClassService {

    @Autowired
    private ClassService classService;

    @Autowired
    private UserService userService;

    @Override
    public Map<String, Object> getStudentClassesByPage(Map<String, Object> params) {
        int page = (int) params.get("page");
        int size = (int) params.get("size");
        String keyword = (String) params.get("keyword");
        Long classId = params.get("classId") != null ? Long.valueOf(params.get("classId").toString()) : null;
        Long studentId = params.get("studentId") != null ? Long.valueOf(params.get("studentId").toString()) : null;

        Page<StudentClass> pageParam = new Page<>(page, size);
        IPage<StudentClass> pageResult = baseMapper.selectStudentClassesWithDetails(pageParam, classId, studentId, keyword);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public StudentClass getStudentClassById(Long id) {
        return getById(id);
    }

    @Override
    @Transactional
    public void joinClass(Long studentId, Long classId) {
        // 验证学生是否存在
        User student = userService.getUserById(studentId);
        if (student == null || !"STUDENT".equals(student.getRole())) {
            throw new RuntimeException("学生不存在或角色不正确");
        }

        // 验证班级是否存在
        com.english.entity.Class classInfo = classService.getClassById(classId);
        if (classInfo == null) {
            throw new RuntimeException("班级不存在");
        }

        // 检查学生是否已在班级中
        if (isStudentInClass(studentId, classId)) {
            throw new RuntimeException("学生已在此班级中");
        }

        StudentClass studentClass = new StudentClass();
        studentClass.setStudentId(studentId);
        studentClass.setClassId(classId);
        studentClass.setJoinTime(LocalDateTime.now());

        save(studentClass);
    }

    @Override
    @Transactional
    public void leaveClass(Long studentId, Long classId) {
        // 验证学生是否在班级中
        if (!isStudentInClass(studentId, classId)) {
            throw new RuntimeException("学生不在此班级中");
        }

        QueryWrapper<StudentClass> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_id", studentId);
        queryWrapper.eq("class_id", classId);
        remove(queryWrapper);
    }

    @Override
    public List<StudentClass> getStudentsByClassId(Long classId) {
        return baseMapper.selectStudentsByClassId(classId);
    }

    @Override
    public List<StudentClass> getClassesByStudentId(Long studentId) {
        return baseMapper.selectClassesByStudentId(studentId);
    }

    @Override
    public boolean isStudentInClass(Long studentId, Long classId) {
        Integer count = baseMapper.checkStudentInClass(studentId, classId);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void batchAddStudentsToClass(Long classId, List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return;
        }

        // 验证班级是否存在
        com.english.entity.Class classInfo = classService.getClassById(classId);
        if (classInfo == null) {
            throw new RuntimeException("班级不存在");
        }

        for (Long studentId : studentIds) {
            try {
                joinClass(studentId, classId);
            } catch (Exception e) {
                // 记录失败的学生，但不影响其他学生加入
                System.err.println("学生 " + studentId + " 加入班级失败: " + e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void batchRemoveStudentsFromClass(Long classId, List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return;
        }

        for (Long studentId : studentIds) {
            try {
                leaveClass(studentId, classId);
            } catch (Exception e) {
                // 记录失败的学生，但不影响其他学生移除
                System.err.println("学生 " + studentId + " 移除班级失败: " + e.getMessage());
            }
        }
    }

    @Override
    public Long getStudentCountByClassId(Long classId) {
        return baseMapper.selectStudentCountByClassId(classId);
    }

    @Override
    public Long getClassCountByStudentId(Long studentId) {
        return baseMapper.selectClassCountByStudentId(studentId);
    }

    @Override
    @Transactional
    public void addStudentToClass(Long teacherId, Long classId, Long studentId) {
        // 验证教师是否有权限管理此班级
        if (!classService.validateTeacherPermission(teacherId, classId)) {
            throw new RuntimeException("教师没有权限管理此班级");
        }

        joinClass(studentId, classId);
    }

    @Override
    @Transactional
    public void removeStudentFromClass(Long teacherId, Long classId, Long studentId) {
        // 验证教师是否有权限管理此班级
        if (!classService.validateTeacherPermission(teacherId, classId)) {
            throw new RuntimeException("教师没有权限管理此班级");
        }

        leaveClass(studentId, classId);
    }
}