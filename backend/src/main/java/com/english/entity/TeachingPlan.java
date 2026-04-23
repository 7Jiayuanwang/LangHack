package com.english.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 教学计划主表实体
 */
@Data
@TableName("teaching_plans")
public class TeachingPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("plan_id")
    private String planId;

    @TableField("plan_name")
    private String planName;

    @TableField("major_code")
    private String majorCode;

    @TableField("major_name")
    private String majorName;

    @TableField("grade")
    private String grade;

    @TableField("schooling_length")
    private Integer schoolingLength;

    @TableField("degree_type")
    private String degreeType;

    @TableField("education_level")
    private String educationLevel;

    @TableField("college_code")
    private String collegeCode;

    @TableField("college_name")
    private String collegeName;

    @TableField("total_credits")
    private BigDecimal totalCredits;

    @TableField("required_credits")
    private BigDecimal requiredCredits;

    @TableField("elective_credits")
    private BigDecimal electiveCredits;

    @TableField("practice_credits")
    private BigDecimal practiceCredits;

    @TableField("general_education_credits")
    private BigDecimal generalEducationCredits;

    @TableField("major_core_credits")
    private BigDecimal majorCoreCredits;

    @TableField("major_elective_credits")
    private BigDecimal majorElectiveCredits;

    @TableField("training_objective")
    private String trainingObjective;

    @TableField("training_requirements")
    private String trainingRequirements;

    @TableField("main_disciplines")
    private String mainDisciplines;

    @TableField("core_courses")
    private String coreCourses;

    @TableField("plan_status")
    private String planStatus;

    @TableField("effective_date")
    private LocalDate effectiveDate;

    @TableField("expiry_date")
    private LocalDate expiryDate;

    @TableField("creator")
    private String creator;

    @TableField("reviewer")
    private String reviewer;

    @TableField("review_date")
    private LocalDate reviewDate;

    @TableField("version")
    private String version;

    @TableField("revision_notes")
    private String revisionNotes;

    @TableField("attachment_path")
    private String attachmentPath;

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
