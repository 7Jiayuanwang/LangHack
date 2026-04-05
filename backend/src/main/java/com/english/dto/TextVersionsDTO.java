package com.english.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class TextVersionsDTO {
    private Long id;
    private String title;
    private String difficulty;
    private String audioUrl;
    private String coverUrl;
    private Integer viewCount;
    private Integer likeCount;
    private String contentType;
    private String markdownContent;
    private Map<String, String> versions;
    private List<String> availableVersions;

    public TextVersionsDTO() {
        this.versions = new HashMap<>();
        this.availableVersions = new ArrayList<>();
    }

    // Getters and Setters
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

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
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

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getMarkdownContent() {
        return markdownContent;
    }

    public void setMarkdownContent(String markdownContent) {
        this.markdownContent = markdownContent;
    }

    public Map<String, String> getVersions() {
        return versions;
    }

    public void setVersions(Map<String, String> versions) {
        this.versions = versions;
    }

    public List<String> getAvailableVersions() {
        return availableVersions;
    }

    public void setAvailableVersions(List<String> availableVersions) {
        this.availableVersions = availableVersions;
    }

    public void addVersion(String key, String content) {
        if (content != null && !content.trim().isEmpty()) {
            this.versions.put(key, content);
            this.availableVersions.add(key);
        }
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
}
