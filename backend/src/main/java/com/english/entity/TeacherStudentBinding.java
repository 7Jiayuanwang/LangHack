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
 * 教师-学生绑定关系（链式拉取衍生，teacher_courses ⋈ course_selection）。
 * <p>
 * 唯一键：(teacher_id, student_id, course_code, academic_year, semester)
 * 注意：academic_year / semester NULL 会导致 MySQL UNIQUE 索引对 NULL 特殊处理，
 *       因此 DDL 用 NOT NULL DEFAULT '' 保证 upsert 幂等。
 */
@Data
@TableName("teacher_student_binding")
public class TeacherStudentBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private String teacherId;

    @TableField("teacher_name")
    private String teacherName;

    @TableField("student_id")
    private String studentId;

    @TableField("student_name")
    private String studentName;

    @TableField("college_name")
    private String collegeName;

    @TableField("course_code")
    private String courseCode;

    @TableField("course_name")
    private String courseName;

    @TableField("course_class_code")
    private String courseClassCode;

    @TableField("academic_year")
    private String academicYear;

    @TableField("semester")
    private String semester;

    @TableField("source_type")
    private String sourceType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("sync_time")
    private LocalDateTime syncTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
