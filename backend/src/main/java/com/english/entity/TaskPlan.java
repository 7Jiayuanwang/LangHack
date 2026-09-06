package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("task_plans")
public class TaskPlan {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String title;
    private Long courseId;
    private String cycleType;
    private LocalDate semesterStart;
    private LocalDate semesterEnd;
    private Integer articleTarget;
    private Integer wordTarget;
    private String wordCategory;
    private Integer paperTarget;
    private Integer articleEnabled;
    private Integer wordEnabled;
    private Integer paperEnabled;
    private Integer articleWeight;
    private Integer wordWeight;
    private Integer paperWeight;
    private BigDecimal totalScore;
    private String status;
    private Long createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String courseName;
    @TableField(exist = false)
    private String creatorName;
    @TableField(exist = false)
    private Integer classCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCycleType() { return cycleType; }
    public void setCycleType(String cycleType) { this.cycleType = cycleType; }
    public LocalDate getSemesterStart() { return semesterStart; }
    public void setSemesterStart(LocalDate semesterStart) { this.semesterStart = semesterStart; }
    public LocalDate getSemesterEnd() { return semesterEnd; }
    public void setSemesterEnd(LocalDate semesterEnd) { this.semesterEnd = semesterEnd; }
    public Integer getArticleTarget() { return articleTarget; }
    public void setArticleTarget(Integer articleTarget) { this.articleTarget = articleTarget; }
    public Integer getWordTarget() { return wordTarget; }
    public void setWordTarget(Integer wordTarget) { this.wordTarget = wordTarget; }
    public String getWordCategory() { return wordCategory; }
    public void setWordCategory(String wordCategory) { this.wordCategory = wordCategory; }
    public Integer getPaperTarget() { return paperTarget; }
    public void setPaperTarget(Integer paperTarget) { this.paperTarget = paperTarget; }
    public Integer getArticleEnabled() { return articleEnabled; }
    public void setArticleEnabled(Integer articleEnabled) { this.articleEnabled = articleEnabled; }
    public Integer getWordEnabled() { return wordEnabled; }
    public void setWordEnabled(Integer wordEnabled) { this.wordEnabled = wordEnabled; }
    public Integer getPaperEnabled() { return paperEnabled; }
    public void setPaperEnabled(Integer paperEnabled) { this.paperEnabled = paperEnabled; }
    public Integer getArticleWeight() { return articleWeight; }
    public void setArticleWeight(Integer articleWeight) { this.articleWeight = articleWeight; }
    public Integer getWordWeight() { return wordWeight; }
    public void setWordWeight(Integer wordWeight) { this.wordWeight = wordWeight; }
    public Integer getPaperWeight() { return paperWeight; }
    public void setPaperWeight(Integer paperWeight) { this.paperWeight = paperWeight; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public Integer getClassCount() { return classCount; }
    public void setClassCount(Integer classCount) { this.classCount = classCount; }
}
