package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_learning_insights")
public class ArticleLearningInsight {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long textId;
    private String contentVersion;
    private String targetLevel;
    private String contentHash;
    private String pointsJson;
    private LocalDateTime createdTime;
}
