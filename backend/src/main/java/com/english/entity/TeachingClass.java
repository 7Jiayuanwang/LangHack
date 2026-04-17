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
 * 教学班信息实体
 */
@Data
@TableName("teaching_class")
public class TeachingClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("class_id")
    private String classId;

    @TableField("class_name")
    private String className;

    @TableField("course_code")
    private String courseCode;

    @TableField("course_name")
    private String courseName;

    @TableField("teacher_id")
    private String teacherId;

    @TableField("teacher_name")
    private String teacherName;

    @TableField("academic_year")
    private String academicYear;

    @TableField("semester")
    private Integer semester;

    @TableField("capacity")
    private Integer capacity;

    @TableField("enrolled_count")
    private Integer enrolledCount;

    @TableField("class_status")
    private String classStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("sync_time")
    private LocalDateTime syncTime;

    @TableField("sync_source")
    private String syncSource;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
