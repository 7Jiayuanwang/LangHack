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
 * 学校课程基本信息实体（与业务表 courses 区分）
 */
@Data
@TableName("school_course")
public class SchoolCourse {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("course_code")
    private String courseCode;

    @TableField("course_name")
    private String courseName;

    @TableField("course_name_en")
    private String courseNameEn;

    @TableField("course_short_name")
    private String courseShortName;

    @TableField("course_nature")
    private String courseNature;

    @TableField("course_category")
    private String courseCategory;

    @TableField("credits")
    private BigDecimal credits;

    @TableField("total_hours")
    private Integer totalHours;

    @TableField("theory_hours")
    private Integer theoryHours;

    @TableField("experiment_hours")
    private Integer experimentHours;

    @TableField("practice_hours")
    private Integer practiceHours;

    @TableField("computer_hours")
    private Integer computerHours;

    @TableField("college_code")
    private String collegeCode;

    @TableField("college_name")
    private String collegeName;

    @TableField("department_code")
    private String departmentCode;

    @TableField("department_name")
    private String departmentName;

    @TableField("leader_code")
    private String leaderCode;

    @TableField("leader_name")
    private String leaderName;

    @TableField("assessment_method")
    private String assessmentMethod;

    @TableField("is_bilingual")
    private String isBilingual;

    @TableField("is_online")
    private String isOnline;

    @TableField("applicable_majors")
    private String applicableMajors;

    @TableField("prerequisite_codes")
    private String prerequisiteCodes;

    @TableField("prerequisite_names")
    private String prerequisiteNames;

    @TableField("course_intro")
    private String courseIntro;

    @TableField("teaching_objectives")
    private String teachingObjectives;

    @TableField("course_status")
    private String courseStatus;

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
