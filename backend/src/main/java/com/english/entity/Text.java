package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("texts")
public class Text {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("unit_id")
    private Long unitId;

    private String title;

    private String content;

    @TableField("cet4_content")
    private String cet4Content;

    @TableField("cet6_content")
    private String cet6Content;

    @TableField("academic_content")
    private String academicContent;

    @TableField("audio_url")
    private String audioUrl;

    private String difficulty;

    @TableField("order_num")
    private Integer orderNum;

    @TableField("uploaded_by")
    private Long uploadedBy;

    private String tags;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("is_public")
    private Integer isPublic;

    private String status;

    @TableField("chinese_parallel_content")
    private String chineseParallelContent;

    // ========== Markdown版本字段 ==========
    @TableField("cet4_markdown_content")
    private String cet4MarkdownContent;

    @TableField("cet6_markdown_content")
    private String cet6MarkdownContent;

    @TableField("academic_markdown_content")
    private String academicMarkdownContent;

    @TableField("chinese_parallel_markdown_content")
    private String chineseParallelMarkdownContent;

    @TableField("markdown_content")
    private String markdownContent;

    @TableField("content_type")
    private String contentType; // TEXT, MARKDOWN

    @TableField("deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getOrderNum() {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum) {
        this.orderNum = orderNum;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    // 提供getCreatedTime方法以兼容AdminController中的使用
    public LocalDateTime getCreatedTime() {
        return createdAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Integer getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Integer isPublic) {
        this.isPublic = isPublic;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCet4Content() {
        return cet4Content;
    }

    public void setCet4Content(String cet4Content) {
        this.cet4Content = cet4Content;
    }

    public String getCet6Content() {
        return cet6Content;
    }

    public void setCet6Content(String cet6Content) {
        this.cet6Content = cet6Content;
    }

    public String getAcademicContent() {
        return academicContent;
    }

    public void setAcademicContent(String academicContent) {
        this.academicContent = academicContent;
    }

    public String getChineseParallelContent() {
        return chineseParallelContent;
    }

    public void setChineseParallelContent(String chineseParallelContent) {
        this.chineseParallelContent = chineseParallelContent;
    }

    // ========== Markdown版本字段的getter/setter ==========
    public String getCet4MarkdownContent() {
        return cet4MarkdownContent;
    }

    public void setCet4MarkdownContent(String cet4MarkdownContent) {
        this.cet4MarkdownContent = cet4MarkdownContent;
    }

    public String getCet6MarkdownContent() {
        return cet6MarkdownContent;
    }

    public void setCet6MarkdownContent(String cet6MarkdownContent) {
        this.cet6MarkdownContent = cet6MarkdownContent;
    }

    public String getAcademicMarkdownContent() {
        return academicMarkdownContent;
    }

    public void setAcademicMarkdownContent(String academicMarkdownContent) {
        this.academicMarkdownContent = academicMarkdownContent;
    }

    public String getChineseParallelMarkdownContent() {
        return chineseParallelMarkdownContent;
    }

    public void setChineseParallelMarkdownContent(String chineseParallelMarkdownContent) {
        this.chineseParallelMarkdownContent = chineseParallelMarkdownContent;
    }

    public String getMarkdownContent() {
        return markdownContent;
    }

    public void setMarkdownContent(String markdownContent) {
        this.markdownContent = markdownContent;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}