package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learner_profile_summaries")
public class LearnerProfileSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String summaryType;

    private String language;

    private String summaryText;

    private String modelId;

    private Long inputSnapshotId;

    private String inputTagsJson;

    private Integer generationTimeMs;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
