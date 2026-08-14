package com.english.service;

import java.util.Map;

/**
 * 教师班级同步服务 — 从学校数据自动创建平台班级并关联学生
 */
public interface TeacherClassSyncService {

    /**
     * 为指定教师同步学校班级数据到平台班级体系。
     *
     * @param userId 平台用户ID（教师）
     * @return 同步结果摘要（创建/更新班级数、添加学生数、跳过数等）
     */
    Map<String, Object> syncClassesForTeacher(Long userId);
}
