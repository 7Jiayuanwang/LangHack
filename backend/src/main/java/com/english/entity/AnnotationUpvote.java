package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("annotation_upvotes")
public class AnnotationUpvote {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long annotationId;
    private Long userId;
    private LocalDateTime createdTime;
}
