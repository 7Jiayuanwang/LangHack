package com.english.service;

import com.english.entity.StudentClass;
import java.util.List;
import java.util.Map;

public interface StudentClassService {

    /**
     * 分页查询学生班级关联列表
     */
    Map<String, Object> getStudentClassesByPage(Map<String, Object> params);

    /**
     * 根据ID获取学生班级关联详情
     */
    StudentClass getStudentClassById(Long id);

    /**
     * 学生加入班级
     */
    void joinClass(Long studentId, Long classId);

    /**
     * 学生退出班级
     */
    void leaveClass(Long studentId, Long classId);

    /**
     * 根据班级ID获取学生列表
     */
    List<StudentClass> getStudentsByClassId(Long classId);

    /**
     * 根据学生ID获取班级列表
     */
    List<StudentClass> getClassesByStudentId(Long studentId);

    /**
     * 检查学生是否已在班级中
     */
    boolean isStudentInClass(Long studentId, Long classId);

    /**
     * 批量添加学生到班级
     */
    void batchAddStudentsToClass(Long classId, List<Long> studentIds);

    /**
     * 批量移除学生出班级
     */
    void batchRemoveStudentsFromClass(Long classId, List<Long> studentIds);

    /**
     * 获取班级学生数量
     */
    Long getStudentCountByClassId(Long classId);

    /**
     * 获取学生加入的班级数量
     */
    Long getClassCountByStudentId(Long studentId);

    /**
     * 教师添加学生到班级
     */
    void addStudentToClass(Long teacherId, Long classId, Long studentId);

    /**
     * 教师移除学生出班级
     */
    void removeStudentFromClass(Long teacherId, Long classId, Long studentId);

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
     * 获取学生班级关联信息
     */
    Map<String, Object> getStudentClassInfo(Long studentId, Long classId);
}