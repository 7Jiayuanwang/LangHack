package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.User;
import com.english.entity.Class;
import com.english.entity.StudentClass;
import com.english.mapper.ClassMapper;
import com.english.mapper.StudentClassMapper;
import com.english.mapper.UserMapper;
import com.english.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * 数据清理服务 - 用于清理不一致的数据
 * 注意：在生产环境中应该谨慎使用
 */
@Service
public class DataCleanupServiceImpl {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private StudentClassMapper studentClassMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void autoCleanupInconsistentData() {
        // 只在开发环境运行数据清理
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isDevProfile = activeProfiles.length == 0 ||
                              java.util.Arrays.asList(activeProfiles).contains("dev");

        if (!isDevProfile) {
            System.out.println("=== 生产环境，跳过自动数据清理 ===");
            return;
        }

        System.out.println("=== 开发环境，开始自动数据一致性检查和清理 ===");

        // 1. 检查和清理不一致的学生班级关联
        cleanupInconsistentStudentClasses();

        // 2. 确保教师没有默认班级（除非自己创建）
        ensureTeachersNoDefaultClasses();

        // 3. 确保学生没有默认班级（除非主动加入）
        ensureStudentsNoDefaultClasses();

        // 4. 打印详细统计信息用于调试
        printDataStatistics();

        System.out.println("=== 自动数据一致性检查和清理完成 ===");
    }

    /**
     * 手动触发数据清理
     */
    public void cleanupInconsistentData() {
        System.out.println("=== 手动触发数据一致性检查和清理 ===");

        // 1. 检查和清理不一致的学生班级关联
        cleanupInconsistentStudentClasses();

        // 2. 确保教师没有默认班级（除非自己创建）
        ensureTeachersNoDefaultClasses();

        // 3. 确保学生没有默认班级（除非主动加入）
        ensureStudentsNoDefaultClasses();

        // 4. 打印详细统计信息用于调试
        printDataStatistics();

        System.out.println("=== 手动数据一致性检查和清理完成 ===");
    }

    private void cleanupInconsistentStudentClasses() {
        System.out.println("检查学生班级关联一致性...");

        // 获取所有学生班级关联
        QueryWrapper<StudentClass> scWrapper = new QueryWrapper<>();
        List<StudentClass> studentClasses = studentClassMapper.selectList(scWrapper);

        System.out.println("发现 " + studentClasses.size() + " 条学生班级关联记录");

        // 检查每个关联的有效性
        int invalidCount = 0;
        for (StudentClass sc : studentClasses) {
            // 检查学生是否存在
            User student = userMapper.selectById(sc.getStudentId());
            if (student == null || !"STUDENT".equals(student.getRole())) {
                System.out.println("删除无效关联：学生ID " + sc.getStudentId() + " 不存在或不是学生角色");
                studentClassMapper.deleteById(sc.getId());
                invalidCount++;
                continue;
            }

            // 检查班级是否存在
            Class classInfo = classMapper.selectById(sc.getClassId());
            if (classInfo == null) {
                System.out.println("删除无效关联：班级ID " + sc.getClassId() + " 不存在");
                studentClassMapper.deleteById(sc.getId());
                invalidCount++;
                continue;
            }
        }

        System.out.println("清理了 " + invalidCount + " 条无效的班级关联记录");
    }

    private void ensureTeachersNoDefaultClasses() {
        System.out.println("检查教师默认班级状态...");

        QueryWrapper<User> teacherWrapper = new QueryWrapper<>();
        teacherWrapper.eq("role", "TEACHER");
        teacherWrapper.eq("deleted", 0);
        List<User> teachers = userMapper.selectList(teacherWrapper);

        System.out.println("检查 " + teachers.size() + " 个教师账户");

        for (User teacher : teachers) {
            QueryWrapper<Class> classWrapper = new QueryWrapper<>();
            classWrapper.eq("teacher_id", teacher.getId());
            List<Class> teacherClasses = classMapper.selectList(classWrapper);

            System.out.println("教师 " + teacher.getUsername() + " 创建了 " + teacherClasses.size() + " 个班级");

            // 这里不删除教师创建的班级，因为这是正常的
            // 但是会报告每个教师的班级数量
        }
    }

    private void ensureStudentsNoDefaultClasses() {
        System.out.println("检查学生默认班级状态...");

        QueryWrapper<User> studentWrapper = new QueryWrapper<>();
        studentWrapper.eq("role", "STUDENT");
        studentWrapper.eq("deleted", 0);
        List<User> students = userMapper.selectList(studentWrapper);

        System.out.println("检查 " + students.size() + " 个学生账户");

        for (User student : students) {
            QueryWrapper<StudentClass> scWrapper = new QueryWrapper<>();
            scWrapper.eq("student_id", student.getId());
            List<StudentClass> studentClasses = studentClassMapper.selectList(scWrapper);

            System.out.println("学生 " + student.getUsername() + " 加入了 " + studentClasses.size() + " 个班级");

            // 学生加入班级是正常的，不删除
            // 但是这里会显示每个学生的班级数量，方便调试
        }
    }

    /**
     * 获取数据统计信息，用于调试
     */
    public void printDataStatistics() {
        System.out.println("\n=== 数据统计信息 ===");

        // 用户统计
        QueryWrapper<User> adminWrapper = new QueryWrapper<>();
        adminWrapper.eq("role", "ADMIN");
        adminWrapper.eq("deleted", 0);
        Long adminCount = userMapper.selectCount(adminWrapper);

        QueryWrapper<User> teacherWrapper = new QueryWrapper<>();
        teacherWrapper.eq("role", "TEACHER");
        teacherWrapper.eq("deleted", 0);
        Long teacherCount = userMapper.selectCount(teacherWrapper);

        QueryWrapper<User> studentWrapper = new QueryWrapper<>();
        studentWrapper.eq("role", "STUDENT");
        studentWrapper.eq("deleted", 0);
        Long studentCount = userMapper.selectCount(studentWrapper);

        // 班级统计
        Long classCount = classMapper.selectCount(null);

        // 学生班级关联统计
        QueryWrapper<StudentClass> scWrapper = new QueryWrapper<>();
        Long studentClassCount = studentClassMapper.selectCount(scWrapper);

        System.out.println("管理员数量: " + adminCount);
        System.out.println("教师数量: " + teacherCount);
        System.out.println("学生数量: " + studentCount);
        System.out.println("班级数量: " + classCount);
        System.out.println("学生班级关联数量: " + studentClassCount);

        // 详细的学生班级信息
        System.out.println("\n=== 学生班级关联详情 ===");
        List<StudentClass> allStudentClasses = studentClassMapper.selectList(null);
        for (StudentClass sc : allStudentClasses) {
            User student = userMapper.selectById(sc.getStudentId());
            Class classInfo = classMapper.selectById(sc.getClassId());
            System.out.println("学生: " + (student != null ? student.getUsername() : "未知") +
                             " -> 班级: " + (classInfo != null ? classInfo.getName() : "未知") +
                             " (加入时间: " + sc.getJoinTime() + ")");
        }
        System.out.println("====================");
    }
}