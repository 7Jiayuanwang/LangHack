package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Reviewer assigned to a course exemption application at submission time. */
@Data
@TableName("course_exemption_reviewer")
public class CourseExemptionReviewer {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("application_id")
    private Long applicationId;

    @TableField("teacher_id")
    private Long teacherId;

    @TableField("created_time")
    private LocalDateTime createdTime;
}
