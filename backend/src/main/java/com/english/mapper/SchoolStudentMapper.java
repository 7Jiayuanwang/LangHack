package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.SchoolStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 学生主数据 Mapper。
 */
@Mapper
public interface SchoolStudentMapper extends BaseMapper<SchoolStudent> {

    /**
     * 按 student_id 唯一键 UPSERT。
     */
    void upsertByStudentId(SchoolStudent entity);

    /**
     * 只更新四六级最后同步时间（Lazy 拉取成功后调用）。
     */
    int updateLastExamSyncTime(@Param("studentId") String studentId,
                               @Param("lastExamSyncTime") LocalDateTime lastExamSyncTime);

    /**
     * 只更新课表最后同步时间（Lazy 拉取成功后调用）。
     */
    int updateLastScheduleSyncTime(@Param("studentId") String studentId,
                                   @Param("lastScheduleSyncTime") LocalDateTime lastScheduleSyncTime);

    /**
     * SSO 登录时从 CAS attributes 回填班级/专业信息。
     * 仅当参数非 NULL 时才覆盖，已有值不覆盖为 NULL。
     */
    int updateClassInfo(@Param("studentId") String studentId,
                        @Param("classCode") String classCode,
                        @Param("className") String className,
                        @Param("majorName") String majorName);

    /**
     * 获取 last_exam_sync_time（null 表示从未同步）。
     */
    LocalDateTime selectLastExamSyncTime(@Param("studentId") String studentId);

    /**
     * 获取 last_schedule_sync_time（null 表示从未同步）。
     */
    LocalDateTime selectLastScheduleSyncTime(@Param("studentId") String studentId);

    /**
     * 按学号查询（便于 SSO 匹配）。
     */
    SchoolStudent selectByStudentId(@Param("studentId") String studentId);

    /**
     * 系统实际覆盖的行政班数（school_student.class_name 去重，且至少有一个未软删的 users 行）。
     */
    long countDistinctClassName();

    /**
     * 系统实际覆盖的学院数（school_student.college_name 去重，且至少有一个未软删的 users 行）。
     */
    long countDistinctCollegeName();
}
