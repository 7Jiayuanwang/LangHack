package com.english.dto;

import java.util.List;

/** Content configuration stored in an assignment description JSON value. */
public class AssignmentContent {
    private String type;
    private String note;
    private ContentConfig content;

    public static class ContentConfig {
        private List<Long> textIds;
        private List<Long> innerTextIds;
        private List<Long> outerTextIds;
        private List<Long> wordIds;
        private Boolean includeAllTexts;
        private Boolean includeAllWords;
        /** Flexible reading assignment rules. */
        private Integer innerRequiredCount;
        private Integer outerRequiredCount;
        private String innerMode;
        private String outerMode;

        public ContentConfig() {}
        public ContentConfig(List<Long> textIds, List<Long> wordIds) {
            this.textIds = textIds;
            this.wordIds = wordIds;
        }
        public List<Long> getTextIds() { return textIds; }
        public void setTextIds(List<Long> textIds) { this.textIds = textIds; }
        public List<Long> getInnerTextIds() { return innerTextIds; }
        public void setInnerTextIds(List<Long> innerTextIds) { this.innerTextIds = innerTextIds; }
        public List<Long> getOuterTextIds() { return outerTextIds; }
        public void setOuterTextIds(List<Long> outerTextIds) { this.outerTextIds = outerTextIds; }
        public List<Long> getWordIds() { return wordIds; }
        public void setWordIds(List<Long> wordIds) { this.wordIds = wordIds; }
        public Boolean getIncludeAllTexts() { return includeAllTexts; }
        public void setIncludeAllTexts(Boolean includeAllTexts) { this.includeAllTexts = includeAllTexts; }
        public Boolean getIncludeAllWords() { return includeAllWords; }
        public void setIncludeAllWords(Boolean includeAllWords) { this.includeAllWords = includeAllWords; }
        public Integer getInnerRequiredCount() { return innerRequiredCount; }
        public void setInnerRequiredCount(Integer innerRequiredCount) { this.innerRequiredCount = innerRequiredCount; }
        public Integer getOuterRequiredCount() { return outerRequiredCount; }
        public void setOuterRequiredCount(Integer outerRequiredCount) { this.outerRequiredCount = outerRequiredCount; }
        public String getInnerMode() { return innerMode; }
        public void setInnerMode(String innerMode) { this.innerMode = innerMode; }
        public String getOuterMode() { return outerMode; }
        public void setOuterMode(String outerMode) { this.outerMode = outerMode; }
    }

    public AssignmentContent() {}
    public AssignmentContent(String type, ContentConfig content) {
        this.type = type;
        this.content = content;
    }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public ContentConfig getContent() { return content; }
    public void setContent(ContentConfig content) { this.content = content; }
}
