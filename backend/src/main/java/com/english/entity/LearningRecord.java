package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 学习记录实体类
 */
@TableName("learning_records")
public class LearningRecord {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 单词ID
     */
    private Long wordId;

    @TableField("text_id")
    private Long textId;

    /**
     * 任务ID
     */
    private Long assignmentId;

    /**
     * 记录类型：VIEW-浏览，REVIEW-复习，TEST-测试
     */
    private String recordType;

    /**
     * 学习时长（秒）
     */
    private Integer durationSeconds;

    /**
     * 正确次数
     */
    private Integer correctCount;

    /**
     * 总次数
     */
    private Integer totalCount;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    public LearningRecord() {
        this.durationSeconds = 0;
        this.correctCount = 0;
        this.totalCount = 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getWordId() {
        return wordId;
    }

    public void setWordId(Long wordId) {
        this.wordId = wordId;
    }

    public Long getTextId() {
        return textId;
    }

    public void setTextId(Long textId) {
        this.textId = textId;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(Integer correctCount) {
        this.correctCount = correctCount;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    @Override
    public String toString() {
        return "LearningRecord{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", wordId=" + wordId +
                ", assignmentId=" + assignmentId +
                ", recordType='" + recordType + '\'' +
                ", durationSeconds=" + durationSeconds +
                ", correctCount=" + correctCount +
                ", totalCount=" + totalCount +
                ", createdTime=" + createdTime +
                '}';
    }
}