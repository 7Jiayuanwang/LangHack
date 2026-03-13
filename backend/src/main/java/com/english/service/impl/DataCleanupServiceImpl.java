package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.User;
import com.english.entity.Class;
import com.english.entity.StudentClass;
import com.english.mapper.ClassMapper;
import com.english.mapper.StudentClassMapper;
import com.english.mapper.UserMapper;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 数据清理服务 - 用于清理不一致的数据
 * 注意：在生产环境中应该谨慎使用
 */
@Service
public class DataCleanupServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(DataCleanupServiceImpl.class);

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
        // 只在明确设置了 dev profile 时才运行数据清理，默认不执行
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isDevProfile = Arrays.asList(activeProfiles).contains("dev");

        if (!isDevProfile) {
            return;
        }

        // 1. 检查和清理不一致的学生班级关联
        cleanupInconsistentStudentClasses();

        // 2. 确保教师没有默认班级（除非自己创建）
        ensureTeachersNoDefaultClasses();

        // 3. 确保学生没有默认班级（除非主动加入）
        ensureStudentsNoDefaultClasses();

        // 4. 打印详细统计信息用于调试
        printDataStatistics();

        logger.info("=== 自动数据一致性检查和清理完成 ===");
    }

    /**
     * 手动触发数据清理
     */
    @Transactional
    public void cleanupInconsistentData() {
        logger.info("=== 手动触发数据一致性检查和清理 ===");

        // 1. 检查和清理不一致的学生班级关联
        cleanupInconsistentStudentClasses();

        // 2. 确保教师没有默认班级（除非自己创建）
        ensureTeachersNoDefaultClasses();

        // 3. 确保学生没有默认班级（除非主动加入）
        ensureStudentsNoDefaultClasses();

        // 4. 打印详细统计信息用于调试
        printDataStatistics();

        logger.info("=== 手动数据一致性检查和清理完成 ===");
    }

    @Transactional
    public void cleanupInconsistentStudentClasses() {
        logger.info("检查学生班级关联一致性...");

        // 获取所有学生班级关联
        QueryWrapper<StudentClass> scWrapper = new QueryWrapper<>();
        List<StudentClass> studentClasses = studentClassMapper.selectList(scWrapper);

        if (studentClasses == null || studentClasses.isEmpty()) {
            logger.info("没有学生班级关联记录");
            return;
        }

        logger.info("发现 {} 条学生班级关联记录", studentClasses.size());

        // 先收集要删除的 ID，再批量删除（避免迭代中逐条删除+无事务问题）
        int invalidCount = 0;
        List<Long> toDelete = new ArrayList<>();
        for (StudentClass sc : studentClasses) {
            // 检查学生是否存在
            User student = userMapper.selectById(sc.getStudentId());
            if (student == null || (!"STUDENT".equals(student.getRole()) && !"USER".equals(student.getRole()))) {
                logger.info("标记删除无效关联：学生ID {} 不存在或不是学生角色", sc.getStudentId());
                toDelete.add(sc.getId());
                invalidCount++;
                continue;
            }

            // 检查班级是否存在
            Class classInfo = classMapper.selectById(sc.getClassId());
            if (classInfo == null) {
                logger.info("标记删除无效关联：班级ID {} 不存在", sc.getClassId());
                toDelete.add(sc.getId());
                invalidCount++;
            }
        }

        if (!toDelete.isEmpty()) {
            studentClassMapper.deleteBatchIds(toDelete);
        }
        logger.info("清理了 {} 条无效的班级关联记录", invalidCount);
    }

    @Transactional
    public void ensureTeachersNoDefaultClasses() {
        logger.info("检查教师默认班级状态...");

        QueryWrapper<User> teacherWrapper = new QueryWrapper<>();
        teacherWrapper.eq("role", "TEACHER");
        teacherWrapper.eq("deleted", 0);
        List<User> teachers = userMapper.selectList(teacherWrapper);

        if (teachers == null || teachers.isEmpty()) {
            logger.info("没有教师账户");
            return;
        }

        logger.info("检查 {} 个教师账户", teachers.size());

        for (User teacher : teachers) {
            QueryWrapper<Class> classWrapper = new QueryWrapper<>();
            classWrapper.eq("teacher_id", teacher.getId());
            classWrapper.eq("deleted", 0);
            List<Class> teacherClasses = classMapper.selectList(classWrapper);

            int classCount = (teacherClasses != null) ? teacherClasses.size() : 0;
            logger.info("教师 {} 创建了 {} 个班级", teacher.getUsername(), classCount);

            // 这里不删除教师创建的班级，因为这是正常的
            // 但是会报告每个教师的班级数量
        }
    }

    @Transactional
    public void ensureStudentsNoDefaultClasses() {
        logger.info("检查学生默认班级状态...");

        QueryWrapper<User> studentWrapper = new QueryWrapper<>();
        studentWrapper.in("role", java.util.Arrays.asList("STUDENT", "USER"));
        studentWrapper.eq("deleted", 0);
        List<User> students = userMapper.selectList(studentWrapper);

        if (students == null || students.isEmpty()) {
            logger.info("没有学生账户");
            return;
        }

        logger.info("检查 {} 个学生账户", students.size());

        for (User student : students) {
            QueryWrapper<StudentClass> scWrapper = new QueryWrapper<>();
            scWrapper.eq("student_id", student.getId());
            List<StudentClass> studentClasses = studentClassMapper.selectList(scWrapper);

            int classCount = (studentClasses != null) ? studentClasses.size() : 0;
            logger.info("学生 {} 加入了 {} 个班级", student.getUsername(), classCount);

            // 学生加入班级是正常的，不删除
            // 但是这里会显示每个学生的班级数量，方便调试
        }
    }

    /**
     * 获取数据统计信息，用于调试
     */
    @Transactional(readOnly = true)
    public void printDataStatistics() {
        logger.info("\n=== 数据统计信息 ===");

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
        studentWrapper.in("role", java.util.Arrays.asList("STUDENT", "USER"));
        studentWrapper.eq("deleted", 0);
        Long studentCount = userMapper.selectCount(studentWrapper);

        // 班级统计
        QueryWrapper<Class> classCountWrapper = new QueryWrapper<>();
        classCountWrapper.eq("deleted", 0);
        Long classCount = classMapper.selectCount(classCountWrapper);

        // 学生班级关联统计
        QueryWrapper<StudentClass> scWrapper = new QueryWrapper<>();
        Long studentClassCount = studentClassMapper.selectCount(scWrapper);

        logger.info("管理员数量: {}", adminCount);
        logger.info("教师数量: {}", teacherCount);
        logger.info("学生数量: {}", studentCount);
        logger.info("班级数量: {}", classCount);
        logger.info("学生班级关联数量: {}", studentClassCount);

        // 详细的学生班级信息
        logger.info("\n=== 学生班级关联详情 ===");
        List<StudentClass> allStudentClasses = studentClassMapper.selectList(null);
        if (allStudentClasses != null && !allStudentClasses.isEmpty()) {
            for (StudentClass sc : allStudentClasses) {
                User student = userMapper.selectById(sc.getStudentId());
                Class classInfo = classMapper.selectById(sc.getClassId());
                logger.info("学生: {} -> 班级: {} (加入时间: {})",
                                 (student != null ? student.getUsername() : "未知"),
                                 (classInfo != null ? classInfo.getName() : "未知"),
                                 sc.getJoinTime());
            }
        } else {
            logger.info("没有学生班级关联记录");
        }
        logger.info("====================");
    }
}