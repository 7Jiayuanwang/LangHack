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
    private Long studentId;

    /**
     * 单词ID
     */
    private Long wordId;

    /**
     * 添加时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime addedTime;

    /**
     * 复习次数
     */
    private Integer reviewCount;

    /**
     * 最后复习时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastReviewed;

    /**
     * 掌握程度：NEW-新词，LEARNING-学习中，MASTERED-已掌握
     */
    private String masteryLevel;

    public VocabularyBook() {
        this.addedTime = LocalDateTime.now();
        this.reviewCount = 0;
        this.masteryLevel = "NEW";
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
                '}';
    }
}