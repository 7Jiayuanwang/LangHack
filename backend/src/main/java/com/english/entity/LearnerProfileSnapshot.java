package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("learner_profile_snapshots")
public class LearnerProfileSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate snapshotDate;

    private String profileStatus;

    private String riskLevel;

    private Integer activeDays7d;

    private Integer activeDays14d;

    private Integer studyMinutes14d;

    private Integer inactiveDays;

    private Integer newCount;

    private Integer learningCount;

    private Integer masteredCount;

    private Double weakRatio;

    private Integer overdueReviewCount;

    private Double overdueReviewRatio;

    private String primaryContentVersion;

    private String versionDistributionJson;

    private String matchedTagCodes;

    private String tagScoresJson;

    private LocalDateTime createdTime;
}
