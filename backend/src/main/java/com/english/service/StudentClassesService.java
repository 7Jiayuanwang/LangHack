package com.english.service;

import java.util.List;
import java.util.Map;

/**
 * 学生班级服务接口
 */
public interface StudentClassesService {

    /**
     * 统计学生加入的班级数量
     */
    int countByStudentId(Long studentId);

    /**
     * 获取学生加入的班级列表（包含课程和教师信息）
     */
    List<Map<String, Object>> getStudentClassesWithDetails(Long studentId);

    /**
     * 获取学生的课程列表（包含学习进度）
     */
    List<Map<String, Object>> getStudentCoursesWithProgress(Long studentId);

    /**
     * 获取学生加入的班级ID列表
     */
    List<Long> getClassIdsByStudentId(Long studentId);

    /**
     * 检查学生是否已加入指定班级
     */
    boolean isStudentInClass(Long studentId, Long classId);

    /**
     * 获取学生班级关联信息
     */
    Map<String, Object> getStudentClassInfo(Long studentId, Long classId);
}