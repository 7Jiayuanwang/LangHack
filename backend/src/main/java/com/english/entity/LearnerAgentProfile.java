package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learner_agent_profiles")
public class LearnerAgentProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String currentLevel;
    private Integer weeklyMinutes;
    private String preferredFormatsJson;
    private String encouragementStyle;
    private Boolean memoryConsent;
    private Boolean onboardingCompleted;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
