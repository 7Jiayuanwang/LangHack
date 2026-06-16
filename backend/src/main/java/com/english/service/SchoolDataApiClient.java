package com.english.service;

import java.util.List;
import java.util.Map;

/**
 * 学校数据API客户端 - 对接学校数据源REST API
 */
public interface SchoolDataApiClient {

    /**
     * 获取行政班数据
     *
     * @param params 查询参数（academicYear, collegeCode等）
     * @return 行政班数据列表
     */
    List<Map<String, Object>> fetchAdministrativeClasses(Map<String, String> params);

    /**
     * 获取学校课程数据
     *
     * @param params 查询参数（academicYear, collegeCode等）
     * @return 课程数据列表
     */
    List<Map<String, Object>> fetchSchoolCourses(Map<String, String> params);

    /**
     * 获取教学班数据
     *
     * @param params 查询参数（academicYear, semester等）
     * @return 教学班数据列表
     */
    List<Map<String, Object>> fetchTeachingClasses(Map<String, String> params);

    /**
     * 获取课程安排数据
     *
     * @param params 查询参数（academicYear, semester等）
     * @return 课程安排数据列表
     */
    List<Map<String, Object>> fetchCourseSchedules(Map<String, String> params);

    /**
     * 获取选课记录数据
     *
     * @param params 查询参数（academicYear, semester, studentId等）
     * @return 选课记录数据列表
     */
    List<Map<String, Object>> fetchCourseSelections(Map<String, String> params);

    /**
     * 获取等级考试成绩数据
     *
     * @param params 查询参数（examType, academicYear等）
     * @return 成绩数据列表
     */
    List<Map<String, Object>> fetchLevelExamScores(Map<String, String> params);

    /**
     * 获取教学计划数据
     *
     * @param params 查询参数（majorCode, grade等）
     * @return 教学计划数据列表
     */
    List<Map<String, Object>> fetchTeachingPlans(Map<String, String> params);

    /**
     * 获取教学计划课程明细
     *
     * @param planId 教学计划编号
     * @return 课程明细数据列表
     */
    List<Map<String, Object>> fetchTeachingPlanCourses(String planId);

    /**
     * 获取学生课表数据
     *
     * @param studentId    学号
     * @param academicYear 学年
     * @param semester     学期
     * @return 课表数据列表
     */
    List<Map<String, Object>> fetchStudentSchedules(String studentId, String academicYear, Integer semester);

    /**
     * 获取教师授课数据
     *
     * @param params 查询参数（academicYear, semester, teacherId等）
     * @return 教师授课数据列表
     */
    List<Map<String, Object>> fetchTeacherCourses(Map<String, String> params);

    /**
     * 获取易辅导系统学生基本信息（DS0104）
     *
     * @param params 查询参数（xh 学号、xm 姓名等）
     * @return 学生基本信息数据列表
     */
    List<Map<String, Object>> fetchStudentBasicInfos(Map<String, String> params);

    /**
     * 测试API连通性
     *
     * @return 是否连通
     */
    boolean testConnection();
}
