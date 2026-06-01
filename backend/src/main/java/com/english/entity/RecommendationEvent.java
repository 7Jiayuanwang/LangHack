package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recommendation_events")
public class RecommendationEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long textId;

    private String eventType;

    private String source;

    private Integer position;

    @TableField("persona_tags")
    private String personaTags;

    private LocalDateTime createdTime;
}
