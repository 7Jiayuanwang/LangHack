package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章学习记录实体
 */
@Data
@TableName("text_learning_records")
public class TextLearningRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 学生ID
     */
    @TableField("student_id")
    private Long studentId;

    /**
     * 课文ID
     */
    @TableField("text_id")
    private Long textId;

    /**
     * 关联的任务ID
     */
    @TableField("assignment_id")
    private Long assignmentId;

    /**
     * 阅读开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 阅读结束(完成)时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 阅读时长(秒)
     */
    @TableField("duration_seconds")
    private Integer durationSeconds;

    /**
     * 是否已完成阅读
     */
    @TableField("is_completed")
    private Integer isCompleted;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 学生阅读的内容版本: original/cet4/cet6/academic/chinese_parallel
     */
    @TableField("content_version")
    private String contentVersion;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    public TextLearningRecord() {
        this.startTime = LocalDateTime.now();
        this.isCompleted = 0;
        this.durationSeconds = 0;
    }
}
