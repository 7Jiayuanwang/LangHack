package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("learner_interests")
public class LearnerInterest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String source;
    private BigDecimal weight;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
