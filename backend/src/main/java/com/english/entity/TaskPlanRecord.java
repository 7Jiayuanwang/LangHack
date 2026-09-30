package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("task_plan_records")
public class TaskPlanRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long studentId;
    private Long classId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer articlesCompleted;
    private Integer wordsCompleted;
    private Integer papersCompleted;
    private LocalDateTime updatedTime;

    @TableField(exist = false)
    private String studentName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public Integer getArticlesCompleted() { return articlesCompleted; }
    public void setArticlesCompleted(Integer v) { this.articlesCompleted = v; }
    public Integer getWordsCompleted() { return wordsCompleted; }
    public void setWordsCompleted(Integer v) { this.wordsCompleted = v; }
    public Integer getPapersCompleted() { return papersCompleted; }
    public void setPapersCompleted(Integer v) { this.papersCompleted = v; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
}
