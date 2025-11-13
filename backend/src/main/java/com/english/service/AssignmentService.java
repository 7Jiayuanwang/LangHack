package com.english.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.english.entity.Assignment;

import java.util.List;
import java.util.Map;

public interface AssignmentService extends IService<Assignment> {

    /**
     * 分页查询任务列表
     */
    Map<String, Object> getAssignmentsByPage(Map<String, Object> params);

    /**
     * 根据ID获取任务详情
     */
    Assignment getAssignmentById(Long id);

    /**
     * 创建任务
     */
    void createAssignment(Assignment assignment);

    /**
     * 更新任务
     */
    void updateAssignment(Assignment assignment);

    /**
     * 删除任务
     */
    void deleteAssignment(Long id);

    /**
     * 获取教师统计信息
     */
    Map<String, Object> getTeacherStats(Long teacherId);

    /**
     * 获取教师的班级列表（仪表板用）
     */
    List<Map<String, Object>> getTeacherClassesWithStats(Long teacherId);

    /**
     * 获取教师的最近任务列表
     */
    List<Assignment> getRecentAssignments(Long teacherId, Integer limit);

    /**
     * 验证教师权限
     */
    boolean validateTeacherPermission(Long teacherId, Long assignmentId);

    // ========== 学生端相关方法 ==========

    /**
     * 获取学生任务列表（带状态）
     */
    Map<String, Object> getStudentAssignmentsWithStatus(List<Long> classIds, Long studentId, String status);

    /**
     * 获取任务详情（带进度）
     */
    Map<String, Object> getAssignmentDetailWithProgress(Long assignmentId, Long studentId);

    /**
     * 开始任务
     */
    Map<String, Object> startAssignment(Long assignmentId, Long studentId);

    /**
     * 获取任务学习内容
     */
    Map<String, Object> getAssignmentContent(Long assignmentId, Long studentId);

    /**
     * 完成任务
     */
    void completeAssignment(Long assignmentId, Long studentId, Map<String, Object> completionData);

    /**
     * 获取任务进度
     */
    Map<String, Object> getAssignmentProgress(Long assignmentId, Long studentId);

    /**
     * 检查任务是否属于指定班级
     */
    boolean isAssignmentInClasses(Long assignmentId, List<Long> classIds);
}