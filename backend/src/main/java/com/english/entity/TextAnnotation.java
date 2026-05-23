package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("text_annotations")
public class TextAnnotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long textId;
    private Long userId;
    private String selectedText;
    private String note;
    private Integer upvoteCount;
    /** 可见性: 1=公开（默认），0=仅自己可见 */
    private Integer visibility;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    // transient — populated by service, not stored in DB
    @TableField(exist = false)
    private String username;
    @TableField(exist = false)
    private Boolean upvotedByMe;
    @TableField(exist = false)
    private Boolean isMine;
}
