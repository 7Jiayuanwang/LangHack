package com.english.service;

import com.english.entity.SyncLog;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 学校数据同步核心服务
 */
public interface SchoolDataSyncService {

    /**
     * 全量同步指定数据源
     *
     * @param dataSource  数据源名称
     * @param triggerType 触发方式 (MANUAL/SCHEDULED)
     * @param operator    操作人
     * @return 同步日志
     */
    SyncLog fullSync(String dataSource, String triggerType, String operator);

    SyncLog fullSync(String dataSource, String triggerType, String operator,
                     com.english.dto.SchoolDataSyncFilterRequest request);

    /**
     * 增量同步指定数据源
     *
     * @param dataSource  数据源名称
     * @param triggerType 触发方式
     * @param operator    操作人
     * @return 同步日志
     */
    SyncLog incrementalSync(String dataSource, String triggerType, String operator);

    SyncLog incrementalSync(String dataSource, String triggerType, String operator,
                            com.english.dto.SchoolDataSyncFilterRequest request);

    /**
     * 全量同步所有数据源
     *
     * @param triggerType 触发方式
     * @param operator    操作人
     * @return 所有数据源的同步日志列表
     */
    List<SyncLog> fullSyncAll(String triggerType, String operator);

    /**
     * 增量同步所有数据源
     *
     * @param triggerType 触发方式
     * @param operator    操作人
     * @return 所有数据源的同步日志列表
     */
    List<SyncLog> incrementalSyncAll(String triggerType, String operator);

    /**
     * 获取同步状态概览
     *
     * @return 各数据源的最近同步状态
     */
    Map<String, Object> getSyncStatusOverview();

    /**
     * 数据校验 - 比对本地数据与源数据的一致性
     *
     * @return 校验结果
     */
    Map<String, Object> validateData();

    /**
     * 同步指定学生的课表数据（从学校API拉取并保存到本地）
     *
     * @param studentId    学号
     * @param academicYear 学年（CDSP 单年份，如 "2025" 表示 2025-2026 学年秋季，"2026" 表示 2025-2026 学年春季）
     * @param semester     学期（1=秋季，2=春季）
     * @return 同步的课表记录列表
     */
    List<com.english.entity.StudentSchedule> syncStudentSchedule(String studentId, String academicYear, Integer semester);

    /**
     * 查询指定学生的本地课表数据
     *
     * @param studentId    学号
     * @param academicYear 学年（可选）
     * @param semester     学期（可选）
     * @return 课表记录列表
     */
    List<com.english.entity.StudentSchedule> getStudentSchedule(String studentId, String academicYear, Integer semester);

    /**
     * 按学院链式拉取学生 / 教师 / 绑定关系并落库。
     * <p>
     * 链路：
     * 1. 按 KKYX 拉 school_course + 客户端过滤
     * 2. 每门课按 KCBH 拉 course_selection（失败降级客户端过滤）
     * 2.5 每门课按 KCDM 拉 teacher_courses → 教师主数据
     * 3. 学号去重 → school_student
     * 3.5 teacher × student (课程) 笛卡尔积 → teacher_student_binding
     *
     * @param collegeName 学院名称（CDSP kkyx，如"外国语学院"）
     * @param matchMode   学院名匹配模式，eq=精确，contains=模糊
     * @param persist     是否落库，false 时只返回统计用于试跑
     * @return 结果统计：courseCount / studentCount / teacherCount / bindingCount /
     *         upstreamKcbhSupported / matchedCourseCodes
     */
    Map<String, Object> chainFetchStudentsByCollege(String collegeName, String matchMode, boolean persist);

    /**
     * 按需同步并返回指定学生的四六级成绩（Lazy 策略）。
     * 本地数据在 staleness 窗口内时直接返回，否则先调 CDSP 刷新再返回。
     *
     * @param studentId 学号
     * @param staleness 数据陈旧度阈值
     * @return 四六级成绩列表（可能为空集合）
     */
    List<com.english.entity.LevelExamScore> getOrSyncLevelExamScores(String studentId, Duration staleness);

    /**
     * 按需同步并返回指定学生的课表（Lazy 策略）。
     *
     * @param studentId    学号
     * @param academicYear 学年（可选）
     * @param semester     学期（可选）
     * @param staleness    数据陈旧度阈值
     * @return 课表记录列表
     */
    List<com.english.entity.StudentSchedule> getOrSyncStudentSchedule(String studentId,
                                                                     String academicYear,
                                                                     Integer semester,
                                                                     Duration staleness);

    /**
     * 按需同步并返回指定学生的选课记录（Lazy 策略）。
     *
     * @param studentId    学号
     * @param academicYear 学年（可选）
     * @param semester     学期（可选）
     * @param staleness    数据陈旧度阈值
     * @return 选课记录列表
     */
    List<com.english.entity.CourseSelection> getOrSyncCourseSelections(String studentId,
                                                                       String academicYear,
                                                                       Integer semester,
                                                                       Duration staleness);
}
