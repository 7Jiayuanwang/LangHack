package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章收藏实体
 */
@Data
@TableName("article_favorites")
public class ArticleFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("student_id")
    private Long studentId;

    @TableField("text_id")
    private Long textId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_time")
    private LocalDateTime createdTime;
}
