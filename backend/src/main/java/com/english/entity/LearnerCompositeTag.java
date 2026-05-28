package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learner_composite_tags")
public class LearnerCompositeTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String displayName;

    private String tagType;

    private Integer priority;

    private String ruleDefinition;

    private String actionHint;

    private Boolean enabled;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
