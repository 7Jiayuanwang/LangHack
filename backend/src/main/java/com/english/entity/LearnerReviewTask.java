package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Next review for one learner and one platform article. */
@Data
@TableName("learner_review_tasks")
public class LearnerReviewTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("text_id")
    private Long textId;
    @TableField("last_attempt_id")
    private Long lastAttemptId;
    @TableField("repeat_count")
    private Integer repeatCount;
    @TableField("last_correct")
    private Integer lastCorrect;
    @TableField("last_total")
    private Integer lastTotal;
    @TableField("last_attempt_time")
    private LocalDateTime lastAttemptTime;
    @TableField("due_at")
    private LocalDateTime dueAt;
    @TableField("deleted")
    private Integer deleted;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
