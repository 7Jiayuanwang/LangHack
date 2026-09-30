package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("learner_goals")
public class LearnerGoal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String goalType;
    private String title;
    private Integer priority;
    private LocalDate targetDate;
    private String status;
    private String source;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
