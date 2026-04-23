package com.english.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行政班基本信息实体
 */
@Data
@TableName("administrative_class")
public class AdministrativeClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("class_code")
    private String classCode;

    @TableField("class_name")
    private String className;

    @TableField("class_short_name")
    private String classShortName;

    @TableField("college_code")
    private String collegeCode;

    @TableField("college_name")
    private String collegeName;

    @TableField("major_code")
    private String majorCode;

    @TableField("major_name")
    private String majorName;

    @TableField("grade")
    private String grade;

    @TableField("class_type")
    private String classType;

    @TableField("head_teacher_code")
    private String headTeacherCode;

    @TableField("head_teacher_name")
    private String headTeacherName;

    @TableField("student_count")
    private Integer studentCount;

    @TableField("male_count")
    private Integer maleCount;

    @TableField("female_count")
    private Integer femaleCount;

    @TableField("class_status")
    private String classStatus;

    @TableField("establish_date")
    private LocalDate establishDate;

    @TableField("graduate_date")
    private LocalDate graduateDate;

    @TableField("remark")
    private String remark;

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
