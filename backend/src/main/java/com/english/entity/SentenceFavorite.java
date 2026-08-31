package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sentence_favorites")
public class SentenceFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long textId;
    private String sentence;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @TableField(exist = false)
    private String sourceTitle;
}
