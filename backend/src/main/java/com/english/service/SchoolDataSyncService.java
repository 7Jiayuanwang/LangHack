package com.english.service;

import com.english.entity.SyncLog;

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
     * @param academicYear 学年（如 2025-2026）
     * @param semester     学期（1 或 2）
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
}
