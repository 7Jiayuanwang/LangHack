package com.english.dto;

import java.util.List;

/**
 * 任务内容配置DTO
 */
public class AssignmentContent {

    private String type; // UNIT/TEXTS/WORDS
    private ContentConfig content;

    public static class ContentConfig {
        private List<Long> textIds;     // 课文ID列表
        private List<Long> wordIds;     // 单词ID列表
        private Boolean includeAllTexts; // 是否包含所有课文（单元任务用）
        private Boolean includeAllWords; // 是否包含所有单词（单元任务用）

        public ContentConfig() {}

        public ContentConfig(List<Long> textIds, List<Long> wordIds) {
            this.textIds = textIds;
            this.wordIds = wordIds;
        }

        public List<Long> getTextIds() {
            return textIds;
        }

        public void setTextIds(List<Long> textIds) {
            this.textIds = textIds;
        }

        public List<Long> getWordIds() {
            return wordIds;
        }

        public void setWordIds(List<Long> wordIds) {
            this.wordIds = wordIds;
        }

        public Boolean getIncludeAllTexts() {
            return includeAllTexts;
        }

        public void setIncludeAllTexts(Boolean includeAllTexts) {
            this.includeAllTexts = includeAllTexts;
        }

        public Boolean getIncludeAllWords() {
            return includeAllWords;
        }

        public void setIncludeAllWords(Boolean includeAllWords) {
            this.includeAllWords = includeAllWords;
        }
    }

    public AssignmentContent() {}

    public AssignmentContent(String type, ContentConfig content) {
        this.type = type;
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ContentConfig getContent() {
        return content;
    }

    public void setContent(ContentConfig content) {
        this.content = content;
    }
}