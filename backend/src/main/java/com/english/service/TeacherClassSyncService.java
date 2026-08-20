package com.english.service;

import java.util.Map;

/**
 * 教师班级同步服务 — 从学校数据自动创建平台班级并关联学生
 */
public interface TeacherClassSyncService {

    /**
     * 为指定教师同步学校班级数据到平台班级体系（默认按当前学年学期）。
     *
     * @param userId 平台用户ID（教师）
     * @return 同步结果摘要（创建/更新班级数、添加学生数、跳过数等）
     */
    Map<String, Object> syncClassesForTeacher(Long userId);

    /**
     * 为指定教师同步指定学年学期的学校班级数据到平台班级体系。
     *
     * @param userId       平台用户ID（教师）
     * @param academicYear 学年（单起始年份，如 "2026"）；空则回退当前学年
     * @param semester     学期："秋"/1=上学期，"春"/2=下学期；空则回退当前学期
     * @return 同步结果摘要（创建/更新班级数、添加学生数、跳过数等）
     */
    Map<String, Object> syncClassesForTeacher(Long userId, String academicYear, String semester);
}
