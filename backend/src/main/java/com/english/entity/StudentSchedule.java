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
 * 学生课表实体
 * 数据源: /api/xskb
 */
@Data
@TableName("student_schedule")
public class StudentSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private String studentId;

    @TableField("student_name")
    private String studentName;

    @TableField("class_id")
    private String classId;

    @TableField("course_code")
    private String courseCode;

    @TableField("course_name")
    private String courseName;

    @TableField("teacher_id")
    private String teacherId;

    @TableField("teacher_name")
    private String teacherName;

    @TableField("day_of_week")
    private Integer dayOfWeek;

    @TableField("period")
    private String period;

    @TableField("week_range")
    private String weekRange;

    @TableField("class_location")
    private String classLocation;

    @TableField("academic_year")
    private String academicYear;

    @TableField("semester")
    private Integer semester;

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
