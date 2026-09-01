package com.english.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台级公开英文内容池。刷新过程只使用平台预设主题，不接收任何用户画像或课表信息。
 */
public interface SharedOpenContentPool {

    List<ContentCandidate> currentCandidates();

    @Data
    @AllArgsConstructor
    class ContentCandidate {
        private String title;
        private String summary;
        private String url;
        private String sourceName;
        private String sourceType;
        private String catalogTopic;
        private LocalDateTime publishedTime;
    }
}
