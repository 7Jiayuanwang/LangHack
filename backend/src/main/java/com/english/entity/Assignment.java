package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.english.dto.AssignmentContent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;

@TableName("assignments")
public class Assignment {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    @TableField("class_id")
    private Long classId;

    @TableField("unit_id")
    private Long unitId;

    @TableField("created_by")
    private Long createdBy;

    private LocalDateTime deadline;

    @TableField("weight")
    private Double weight; // 任务权重，默认1.00

    @TableField("ideal_study_time_minutes")
    private Integer idealStudyTimeMinutes; // 理想学习时长（分钟），默认60分钟

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField("deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    // 关联字段（不映射到数据库）
    @TableField(exist = false)
    private String className;

    @TableField(exist = false)
    private String unitName;

    @TableField(exist = false)
    private String creatorName; // 创建者姓名

    @TableField(exist = false)
    private String creatorNickname; // 创建者昵称

    @TableField(exist = false)
    private Integer studentCount; // 学生数量（用于统计查询）

    // 关联字段（不映射到数据库）
    @TableField(exist = false)
    private AssignmentContent assignmentContent; // 任务内容配置（从description解析）

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public Double getWeight() {
        return weight != null ? weight : 1.0; // 默认权重为1.0
    }

    public void setWeight(Double weight) {
        this.weight = weight != null ? weight : 1.0; // 默认权重为1.0
    }

    public Integer getIdealStudyTimeMinutes() {
        return idealStudyTimeMinutes != null ? idealStudyTimeMinutes : 60; // 默认60分钟
    }

    public void setIdealStudyTimeMinutes(Integer idealStudyTimeMinutes) {
        this.idealStudyTimeMinutes = idealStudyTimeMinutes != null ? idealStudyTimeMinutes : 60; // 默认60分钟
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }


    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getCreatorNickname() {
        return creatorNickname;
    }

    public void setCreatorNickname(String creatorNickname) {
        this.creatorNickname = creatorNickname;
    }

    public Integer getStudentCount() {
        return studentCount;
    }

    public void setStudentCount(Integer studentCount) {
        this.studentCount = studentCount;
    }

    public AssignmentContent getAssignmentContent() {
        return assignmentContent;
    }

    public void setAssignmentContent(AssignmentContent assignmentContent) {
        this.assignmentContent = assignmentContent;
        // 同时更新description字段为JSON格式
        try {
            if (assignmentContent != null) {
                this.description = objectMapper.writeValueAsString(assignmentContent);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize assignment content", e);
        }
    }

    /**
     * 从description字段解析任务内容
     * @return AssignmentContent 如果description不是JSON格式则返回null
     */
    @JsonIgnore
    public AssignmentContent parseContentFromDescription() {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }

        try {
            // 尝试解析为JSON
            return objectMapper.readValue(description, AssignmentContent.class);
        } catch (JsonProcessingException e) {
            // 如果不是JSON格式，说明是普通的任务描述，返回null
            return null;
        }
    }

    /**
     * 判断是否为单元级别任务
     */
    @JsonIgnore
    public boolean isUnitTask() {
        AssignmentContent content = parseContentFromDescription();
        return content != null && "UNIT".equals(content.getType());
    }

    /**
     * 判断是否为课文级别任务
     */
    @JsonIgnore
    public boolean isTextsTask() {
        AssignmentContent content = parseContentFromDescription();
        return content != null && "TEXTS".equals(content.getType());
    }

    /**
     * 判断是否为单词级别任务
     */
    @JsonIgnore
    public boolean isWordsTask() {
        AssignmentContent content = parseContentFromDescription();
        return content != null && "WORDS".equals(content.getType());
    }

    /**
     * 获取指定课文ID列表（仅对课文任务有效）
     */
    @JsonIgnore
    public java.util.List<Long> getTextIds() {
        AssignmentContent content = parseContentFromDescription();
        if (content != null && content.getContent() != null && "TEXTS".equals(content.getType())) {
            return content.getContent().getTextIds();
        }
        return null;
    }

    /**
     * 获取指定单词ID列表（仅对单词任务有效）
     */
    @JsonIgnore
    public java.util.List<Long> getWordIds() {
        AssignmentContent content = parseContentFromDescription();
        if (content != null && content.getContent() != null && "WORDS".equals(content.getType())) {
            return content.getContent().getWordIds();
        }
        return null;
    }
}
