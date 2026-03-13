package com.english.service;

import com.english.entity.Class;
import java.util.List;
import java.util.Map;

public interface ClassService {

    /**
     * 分页查询班级列表
     */
    Map<String, Object> getClassesByPage(Map<String, Object> params);

    /**
     * 根据ID获取班级详情
     */
    Class getClassById(Long id);

    /**
     * 创建班级
     */
    void createClass(Class classInfo);

    /**
     * 更新班级
     */
    void updateClass(Class classInfo);

    /**
     * 删除班级
     */
    void deleteClass(Long id);

    /**
     * 根据教师ID获取班级列表
     */
    List<Class> getClassesByTeacherId(Long teacherId);

    /**
     * 根据学生ID获取班级列表
     */
    List<Class> getClassesByStudentId(Long studentId);

    /**
     * 根据课程ID获取班级列表
     */
    List<Class> getClassesByCourseId(Long courseId);

    /**
     * 搜索班级
     */
    List<Class> searchClasses(String keyword);

    /**
     * 获取班级统计信息
     */
    Map<String, Object> getClassStats();

    /**
     * 检查班级名称是否已存在
     */
    boolean checkClassNameExists(String name, Long excludeId);

    /**
     * 验证教师是否可以管理班级
     */
    boolean validateTeacherPermission(Long teacherId, Long classId);

    /**
     * 根据班级名称获取班级（用于批量导入）
     */
    Class getClassByName(String name);

    /**
     * 创建班级（用于批量导入，无需验证课程和教师）
     */
    void createClassForImport(Class classInfo);

    /**
     * 学生搜索自己加入的班级
     */
    List<Class> searchClassesForStudent(Long studentId, String keyword);
}