package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("past_paper_attempts")
public class PastPaperAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private String paperKey;
    private Long pastPaperId;
    private String category;
    private String paperTitle;
    private Integer answeredCount;
    private Integer questionCount;
    private Integer objectiveScore;
    private Integer objectiveTotal;
    private Integer subjectiveCount;
    private BigDecimal scorePercentage;
    private String answersJson;
    private String gradingStatus;
    private BigDecimal aiScorePercentage;
    private BigDecimal finalScorePercentage;
    private String gradingJson;
    private LocalDateTime gradedTime;
    private LocalDateTime submittedTime;
    private LocalDateTime createdTime;
}
