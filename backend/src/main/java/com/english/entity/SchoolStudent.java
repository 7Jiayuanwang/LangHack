package com.english.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CDSP 链式拉取学生主数据实体。
 * <p>
 * 学号作为唯一索引，class_* 字段首次为 NULL，SSO 登录时用 CAS attributes 补全。
 */
@Data
@TableName("school_student")
public class SchoolStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private String studentId;

    @TableField("student_name")
    private String studentName;

    @TableField("college_name")
    private String collegeName;

    @TableField("college_code")
    private String collegeCode;

    @TableField("class_code")
    private String classCode;

    @TableField("class_name")
    private String className;

    @TableField("grade")
    private String grade;

    @TableField("major_name")
    private String majorName;

    @TableField("source_course_codes")
    private String sourceCourseCodes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("sync_time")
    private LocalDateTime syncTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_exam_sync_time")
    private LocalDateTime lastExamSyncTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_schedule_sync_time")
    private LocalDateTime lastScheduleSyncTime;

    @TableField("sync_source")
    private String syncSource;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
