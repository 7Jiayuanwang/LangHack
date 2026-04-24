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
 * 选课记录实体
 */
@Data
@TableName("course_selection")
public class CourseSelection {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("selection_id")
    private String selectionId;

    @TableField("student_id")
    private String studentId;

    @TableField("student_name")
    private String studentName;

    @TableField("course_code")
    private String courseCode;

    @TableField("course_name")
    private String courseName;

    @TableField("class_id")
    private String classId;

    @TableField("teacher_id")
    private String teacherId;

    @TableField("teacher_name")
    private String teacherName;

    @TableField("credits")
    private BigDecimal credits;

    @TableField("academic_year")
    private String academicYear;

    @TableField("semester")
    private Integer semester;

    @TableField("class_time")
    private String classTime;

    @TableField("class_location")
    private String classLocation;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("selection_time")
    private LocalDateTime selectionTime;

    @TableField("selection_status")
    private String selectionStatus;

    @TableField("regular_grade")
    private BigDecimal regularGrade;

    @TableField("final_grade")
    private BigDecimal finalGrade;

    @TableField("total_grade")
    private BigDecimal totalGrade;

    @TableField("grade_point")
    private BigDecimal gradePoint;

    @TableField("attendance_count")
    private Integer attendanceCount;

    @TableField("absence_count")
    private Integer absenceCount;

    @TableField("grade_remark")
    private String gradeRemark;

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
