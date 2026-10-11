package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 大学英语免修/免考申请。折算成绩按内农大教字〔2025〕18 号细则计算。 */
@Data
@TableName("course_exemption_application")
public class CourseExemptionApplication {
    public static final String APPLY_EXEMPT_FULL = "EXEMPT_FULL";
    public static final String APPLY_EXEMPT_WITH_EXAM = "EXEMPT_WITH_EXAM";

    public static final String EXAM_CET4 = "CET4";
    public static final String EXAM_CET6 = "CET6";
    public static final String EXAM_IELTS = "IELTS";
    public static final String EXAM_TOEFL = "TOEFL";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private Long studentId;

    @TableField("course_name")
    private String courseName;

    @TableField("apply_type")
    private String applyType;

    @TableField("exam_type")
    private String examType;

    @TableField("exam_score")
    private String examScore;

    @TableField("exam_date")
    private LocalDate examDate;

    @TableField("certificate_url")
    private String certificateUrl;

    @TableField("student_remark")
    private String studentRemark;

    @TableField("converted_score")
    private Integer convertedScore;

    @TableField("status")
    private String status;

    @TableField("review_comment")
    private String reviewComment;

    @TableField("reviewed_by")
    private Long reviewedBy;

    @TableField("reviewed_time")
    private LocalDateTime reviewedTime;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
