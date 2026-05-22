package com.english.integration;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.impl.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据完整性集成测试类
 * 用于验证修复后的硬编码数据是否能正确计算
 * 仅在 dev 环境执行
 */
@SpringBootTest
@ActiveProfiles("dev")
@Disabled("Requires database connection - run locally only")
public class DataIntegrityTest {

    @Autowired
    private StudentClassesServiceImpl studentClassesService;

    @Autowired
    private LearningRecordsServiceImpl learningRecordsService;

    @Autowired
    private AssignmentServiceImpl assignmentService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 测试统计数据计算
     */
    @Test
    public void testStatisticsCalculation() {
        System.out.println("=== 数据完整性测试 ===");

        // 测试学生学习统计 - 使用测试数据库中的真实学生ID
        try {
            // NOTE: 使用测试数据库中的有效学生ID
            // 从测试数据中查询或使用配置的测试ID
            Long studentId = getTestStudentId();
            assertNotNull(studentId, "测试学生ID不应为null");

            // 测试班级统计
            List<Map<String, Object>> classes = studentClassesService.getStudentClassesWithDetails(studentId);
            assertNotNull(classes, "班级列表不应为null");
            System.out.println("✅ 班级统计计算成功，共 " + classes.size() + " 个班级");

            // 测试课程统计
            List<Map<String, Object>> courses = studentClassesService.getStudentCoursesWithProgress(studentId);
            assertNotNull(courses, "课程列表不应为null");
            System.out.println("✅ 课程统计计算成功，共 " + courses.size() + " 个课程");

            // 测试学习记录统计
            Map<String, Object> overview = learningRecordsService.getLearningOverview(studentId, Map.of());
            assertNotNull(overview, "学习概览不应为null");
            System.out.println("✅ 学习概览计算成功");

            System.out.println("=== 所有统计功能测试通过 ===");

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            System.err.println("错误类型: " + e.getClass().getName());
            fail("数据完整性测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取测试学生ID — 查询数据库中第一个 STUDENT 角色用户
     */
    private Long getTestStudentId() {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("role", "STUDENT").last("LIMIT 1");
        User student = userMapper.selectOne(query);
        return student != null ? student.getId() : null;
    }
}
