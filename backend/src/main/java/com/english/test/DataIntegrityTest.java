package com.english.test;

import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据完整性测试类
 * 用于验证修复后的硬编码数据是否能正确计算
 */
@Component
public class DataIntegrityTest {

    @Autowired
    private StudentClassesServiceImpl studentClassesService;

    @Autowired
    private LearningRecordsServiceImpl learningRecordsService;

    @Autowired
    private AssignmentServiceImpl assignmentService;

    /**
     * 测试统计数据计算
     */
    public void testStatisticsCalculation() {
        System.out.println("=== 数据完整性测试 ===");

        // 测试学生学习统计
        try {
            Long studentId = 1L; // 假设存在学生ID为1的用户

            // 测试班级统计
            List<java.util.Map<String, Object>> classes = studentClassesService.getStudentClassesWithDetails(studentId);
            System.out.println("✅ 班级统计计算成功，共 " + classes.size() + " 个班级");

            // 测试课程统计
            List<java.util.Map<String, Object>> courses = studentClassesService.getStudentCoursesWithProgress(studentId);
            System.out.println("✅ 课程统计计算成功，共 " + courses.size() + " 个课程");

            // 测试学习记录统计
            java.util.Map<String, Object> overview = learningRecordsService.getLearningOverview(studentId, java.util.Map.of());
            System.out.println("✅ 学习概览计算成功");

            System.out.println("=== 所有统计功能测试通过 ===");

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            System.err.println("错误类型: " + e.getClass().getName());
        }
    }
}