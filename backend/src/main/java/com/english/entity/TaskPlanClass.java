package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("task_plan_classes")
public class TaskPlanClass {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long classId;
    private Integer optedOut;
    private Integer articleTargetOverride;
    private Integer wordTargetOverride;
    private String wordCategoryOverride;
    private Integer paperTargetOverride;
    private Integer articleWeightOverride;
    private Integer wordWeightOverride;
    private Integer paperWeightOverride;
    private String cycleTypeOverride;
    private LocalDate semesterStartOverride;
    private LocalDate semesterEndOverride;
    private Integer articleEnabledOverride;
    private Integer wordEnabledOverride;
    private Integer paperEnabledOverride;
    private Long modifiedBy;
    private LocalDateTime modifiedTime;

    @TableField(exist = false)
    private String className;
    @TableField(exist = false)
    private String teacherName;
    @TableField(exist = false)
    private Long teacherId;
    @TableField(exist = false)
    private String courseSeq;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public Integer getOptedOut() { return optedOut; }
    public void setOptedOut(Integer optedOut) { this.optedOut = optedOut; }
    public Integer getArticleTargetOverride() { return articleTargetOverride; }
    public void setArticleTargetOverride(Integer v) { this.articleTargetOverride = v; }
    public Integer getWordTargetOverride() { return wordTargetOverride; }
    public void setWordTargetOverride(Integer v) { this.wordTargetOverride = v; }
    public String getWordCategoryOverride() { return wordCategoryOverride; }
    public void setWordCategoryOverride(String v) { this.wordCategoryOverride = v; }
    public Integer getPaperTargetOverride() { return paperTargetOverride; }
    public void setPaperTargetOverride(Integer v) { this.paperTargetOverride = v; }
    public Integer getArticleWeightOverride() { return articleWeightOverride; }
    public void setArticleWeightOverride(Integer v) { this.articleWeightOverride = v; }
    public Integer getWordWeightOverride() { return wordWeightOverride; }
    public void setWordWeightOverride(Integer v) { this.wordWeightOverride = v; }
    public Integer getPaperWeightOverride() { return paperWeightOverride; }
    public void setPaperWeightOverride(Integer v) { this.paperWeightOverride = v; }
    public String getCycleTypeOverride() { return cycleTypeOverride; }
    public void setCycleTypeOverride(String v) { this.cycleTypeOverride = v; }
    public LocalDate getSemesterStartOverride() { return semesterStartOverride; }
    public void setSemesterStartOverride(LocalDate v) { this.semesterStartOverride = v; }
    public LocalDate getSemesterEndOverride() { return semesterEndOverride; }
    public void setSemesterEndOverride(LocalDate v) { this.semesterEndOverride = v; }
    public Integer getArticleEnabledOverride() { return articleEnabledOverride; }
    public void setArticleEnabledOverride(Integer v) { this.articleEnabledOverride = v; }
    public Integer getWordEnabledOverride() { return wordEnabledOverride; }
    public void setWordEnabledOverride(Integer v) { this.wordEnabledOverride = v; }
    public Integer getPaperEnabledOverride() { return paperEnabledOverride; }
    public void setPaperEnabledOverride(Integer v) { this.paperEnabledOverride = v; }
    public Long getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(Long modifiedBy) { this.modifiedBy = modifiedBy; }
    public LocalDateTime getModifiedTime() { return modifiedTime; }
    public void setModifiedTime(LocalDateTime modifiedTime) { this.modifiedTime = modifiedTime; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getCourseSeq() { return courseSeq; }
    public void setCourseSeq(String courseSeq) { this.courseSeq = courseSeq; }
}
