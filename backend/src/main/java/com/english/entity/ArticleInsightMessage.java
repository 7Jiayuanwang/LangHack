package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_insight_messages")
public class ArticleInsightMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long textId;
    private Long insightId;
    private LocalDateTime readTime;
    private LocalDateTime createdTime;
}
