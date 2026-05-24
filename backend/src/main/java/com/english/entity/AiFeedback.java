package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("ai_feedback")
public class AiFeedback {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("call_log_id")
    private Long callLogId;

    @TableField("user_id")
    private Long userId;

    @TableField("rating")
    private Integer rating;

    @TableField("is_helpful")
    private Boolean isHelpful;

    @TableField("feedback_text")
    private String feedbackText;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCallLogId() { return callLogId; }
    public void setCallLogId(Long callLogId) { this.callLogId = callLogId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public Boolean getIsHelpful() { return isHelpful; }
    public void setIsHelpful(Boolean isHelpful) { this.isHelpful = isHelpful; }

    public String getFeedbackText() { return feedbackText; }
    public void setFeedbackText(String feedbackText) { this.feedbackText = feedbackText; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
