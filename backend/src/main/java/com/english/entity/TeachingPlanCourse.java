package com.english.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教学计划课程明细实体
 */
@Data
@TableName("teaching_plan_courses")
public class TeachingPlanCourse {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("detail_id")
    private String detailId;

    @TableField("plan_id")
    private String planId;

    @TableField("course_code")
    private String courseCode;

    @TableField("course_name")
    private String courseName;

    @TableField("course_type")
    private String courseType;

    @TableField("course_category")
    private String courseCategory;

    @TableField("credits")
    private BigDecimal credits;

    @TableField("hours")
    private Integer hours;

    @TableField("theory_hours")
    private Integer theoryHours;

    @TableField("lab_hours")
    private Integer labHours;

    @TableField("semester")
    private Integer semester;

    @TableField("assessment_method")
    private String assessmentMethod;

    @TableField("prerequisite_courses")
    private String prerequisiteCourses;

    @TableField("sort_order")
    private Integer sortOrder;

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
