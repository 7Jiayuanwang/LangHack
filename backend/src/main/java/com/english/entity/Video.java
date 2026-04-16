package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

@TableName("videos")
public class Video {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("media_id")
    private Long mediaId;

    @TableField("cover_media_id")
    private Long coverMediaId;

    @TableField("video_url")
    private String videoUrl;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("subtitle_content")
    private String subtitleContent;

    @TableField("user_id")
    private Long userId;

    @TableField("is_public")
    private Integer isPublic;

    @TableField("is_featured")
    private Integer isFeatured;

    @TableField("featured_priority")
    private Integer featuredPriority;

    @TableField("tags")
    private String tags;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("status")
    private String status;

    @TableField("associated_text_id")
    private Long associatedTextId;

    @TableField("transcription_status")
    private String transcriptionStatus;

    @TableField("deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    // ========== Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getMediaId() {
        return mediaId;
    }

    public void setMediaId(Long mediaId) {
        this.mediaId = mediaId;
    }

    public Long getCoverMediaId() {
        return coverMediaId;
    }

    public void setCoverMediaId(Long coverMediaId) {
        this.coverMediaId = coverMediaId;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getSubtitleContent() {
        return subtitleContent;
    }

    public void setSubtitleContent(String subtitleContent) {
        this.subtitleContent = subtitleContent;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Integer isPublic) {
        this.isPublic = isPublic;
    }

    @JsonProperty("isFeatured")
    public Integer getIsFeatured() {
        return isFeatured;
    }

    public void setIsFeatured(Integer isFeatured) {
        this.isFeatured = isFeatured;
    }

    public Integer getFeaturedPriority() {
        return featuredPriority;
    }

    public void setFeaturedPriority(Integer featuredPriority) {
        this.featuredPriority = featuredPriority;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAssociatedTextId() {
        return associatedTextId;
    }

    public void setAssociatedTextId(Long associatedTextId) {
        this.associatedTextId = associatedTextId;
    }

    public String getTranscriptionStatus() {
        return transcriptionStatus;
    }

    public void setTranscriptionStatus(String transcriptionStatus) {
        this.transcriptionStatus = transcriptionStatus;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }
}
