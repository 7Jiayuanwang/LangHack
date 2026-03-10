package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 生词本实体类
 */
@TableName("vocabulary_books")
public class VocabularyBook {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 学生ID
     */
    @TableField("student_id")
    private Long studentId;

    /**
     * 单词ID
     */
    @TableField("word_id")
    private Long wordId;

    /**
     * 添加时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "added_time", fill = FieldFill.INSERT)
    private LocalDateTime addedTime;

    /**
     * 复习次数
     */
    @TableField("review_count")
    private Integer reviewCount;

    /**
     * 最后复习时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_reviewed")
    private LocalDateTime lastReviewed;

    /**
     * 掌握程度：NEW-新词，LEARNING-学习中，MASTERED-已掌握
     */
    @TableField("mastery_level")
    private String masteryLevel;

    /**
     * SM-2 复习次数 (成功复习次数)
     */
    private Integer repetition;

    /**
     * SM-2 复习间隔 (天)
     */
    @TableField("interval_days")
    private Integer intervalDays;

    /**
     * SM-2 难度因子
     */
    @TableField("easiness_factor")
    private Double easinessFactor;

    /**
     * 下次复习时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("next_review_time")
    private LocalDateTime nextReviewTime;

    public VocabularyBook() {
        this.reviewCount = 0;
        this.masteryLevel = "NEW";
        this.repetition = 0;
        this.intervalDays = 0;
        this.easinessFactor = 2.5;
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

    public LocalDateTime getAddedTime() {
        return addedTime;
    }

    public void setAddedTime(LocalDateTime addedTime) {
        this.addedTime = addedTime;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public LocalDateTime getLastReviewed() {
        return lastReviewed;
    }

    public void setLastReviewed(LocalDateTime lastReviewed) {
        this.lastReviewed = lastReviewed;
    }

    public String getMasteryLevel() {
        return masteryLevel;
    }

    public void setMasteryLevel(String masteryLevel) {
        this.masteryLevel = masteryLevel;
    }

    public Integer getRepetition() {
        return repetition;
    }

    public void setRepetition(Integer repetition) {
        this.repetition = repetition;
    }

    public Integer getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(Integer intervalDays) {
        this.intervalDays = intervalDays;
    }

    public Double getEasinessFactor() {
        return easinessFactor;
    }

    public void setEasinessFactor(Double easinessFactor) {
        this.easinessFactor = easinessFactor;
    }

    public LocalDateTime getNextReviewTime() {
        return nextReviewTime;
    }

    public void setNextReviewTime(LocalDateTime nextReviewTime) {
        this.nextReviewTime = nextReviewTime;
    }

    @Override
    public String toString() {
        return "VocabularyBook{" +
                "id=" + id +
                ", studentId=" + studentId +
                ", wordId=" + wordId +
                ", addedTime=" + addedTime +
                ", reviewCount=" + reviewCount +
                ", lastReviewed=" + lastReviewed +
                ", masteryLevel=" + masteryLevel +
                ", repetition=" + repetition +
                ", intervalDays=" + intervalDays +
                ", easinessFactor=" + easinessFactor +
                ", nextReviewTime=" + nextReviewTime +
                '}';
    }
}