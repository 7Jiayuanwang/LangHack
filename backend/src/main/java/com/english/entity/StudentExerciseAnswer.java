package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 学生习题答题记录实体类
 */
@TableName("student_exercise_answers")
public class StudentExerciseAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("exercise_id")
    private Long exerciseId;

    @TableField("student_id")
    private Long studentId;

    @TableField("assignment_id")
    private Long assignmentId;

    @TableField("answer_content")
    private String answerContent;

    @TableField("is_correct")
    private Boolean isCorrect;

    @TableField("answer_time")
    private LocalDateTime answerTime;

    @TableField("time_spent_seconds")
    private Integer timeSpentSeconds;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    // 构造函数
    public StudentExerciseAnswer() {}

    public StudentExerciseAnswer(Long exerciseId, Long studentId, Long assignmentId, String answerContent, Boolean isCorrect, Integer timeSpentSeconds) {
        this.exerciseId = exerciseId;
        this.studentId = studentId;
        this.assignmentId = assignmentId;
        this.answerContent = answerContent;
        this.isCorrect = isCorrect;
        this.timeSpentSeconds = timeSpentSeconds;
    }

    // Getter和Setter方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getAnswerContent() {
        return answerContent;
    }

    public void setAnswerContent(String answerContent) {
        this.answerContent = answerContent;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public LocalDateTime getAnswerTime() {
        return answerTime;
    }

    public void setAnswerTime(LocalDateTime answerTime) {
        this.answerTime = answerTime;
    }

    public Integer getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public void setTimeSpentSeconds(Integer timeSpentSeconds) {
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    @Override
    public String toString() {
        return "StudentExerciseAnswer{" +
                "id=" + id +
                ", exerciseId=" + exerciseId +
                ", studentId=" + studentId +
                ", assignmentId=" + assignmentId +
                ", answerContent='" + answerContent + '\'' +
                ", isCorrect=" + isCorrect +
                ", answerTime=" + answerTime +
                ", timeSpentSeconds=" + timeSpentSeconds +
                ", createdTime=" + createdTime +
                '}';
    }
}