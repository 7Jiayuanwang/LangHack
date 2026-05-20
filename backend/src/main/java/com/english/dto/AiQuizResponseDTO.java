package com.english.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AiQuizResponseDTO {

    @JsonProperty("source_info")
    private SourceInfo sourceInfo;

    @JsonProperty("passage_analysis")
    private PassageAnalysis passageAnalysis;

    @JsonProperty("questions")
    private List<QuestionItem> questions;

    @JsonProperty("quality_metrics")
    private QualityMetrics qualityMetrics;

    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }

    public void setSourceInfo(SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
    }

    public PassageAnalysis getPassageAnalysis() {
        return passageAnalysis;
    }

    public void setPassageAnalysis(PassageAnalysis passageAnalysis) {
        this.passageAnalysis = passageAnalysis;
    }

    public List<QuestionItem> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionItem> questions) {
        this.questions = questions;
    }

    public QualityMetrics getQualityMetrics() {
        return qualityMetrics;
    }

    public void setQualityMetrics(QualityMetrics qualityMetrics) {
        this.qualityMetrics = qualityMetrics;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceInfo {
        private String title;

        @JsonProperty("original_word_count")
        private Integer originalWordCount;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Integer getOriginalWordCount() {
            return originalWordCount;
        }

        public void setOriginalWordCount(Integer originalWordCount) {
            this.originalWordCount = originalWordCount;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PassageAnalysis {
        private String structure;

        @JsonProperty("key_arguments")
        private List<String> keyArguments;

        @JsonProperty("attitude_markers")
        private List<String> attitudeMarkers;

        public String getStructure() {
            return structure;
        }

        public void setStructure(String structure) {
            this.structure = structure;
        }

        public List<String> getKeyArguments() {
            return keyArguments;
        }

        public void setKeyArguments(List<String> keyArguments) {
            this.keyArguments = keyArguments;
        }

        public List<String> getAttitudeMarkers() {
            return attitudeMarkers;
        }

        public void setAttitudeMarkers(List<String> attitudeMarkers) {
            this.attitudeMarkers = attitudeMarkers;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionItem {
        private String id;
        private String type;
        private String question;
        private Map<String, String> options;

        @JsonProperty("correct_answer")
        private String correctAnswer;

        private String analysis;

        @JsonProperty("distractor_analysis")
        private Map<String, String> distractorAnalysis;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public Map<String, String> getOptions() {
            return options;
        }

        public void setOptions(Map<String, String> options) {
            this.options = options;
        }

        public String getCorrectAnswer() {
            return correctAnswer;
        }

        public void setCorrectAnswer(String correctAnswer) {
            this.correctAnswer = correctAnswer;
        }

        public String getAnalysis() {
            return analysis;
        }

        public void setAnalysis(String analysis) {
            this.analysis = analysis;
        }

        public Map<String, String> getDistractorAnalysis() {
            return distractorAnalysis;
        }

        public void setDistractorAnalysis(Map<String, String> distractorAnalysis) {
            this.distractorAnalysis = distractorAnalysis;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QualityMetrics {
        @JsonProperty("difficulty_match")
        private String difficultyMatch;

        @JsonProperty("type_coverage")
        private List<String> typeCoverage;

        @JsonProperty("all_options_plausible")
        private Boolean allOptionsPlausible;

        public String getDifficultyMatch() {
            return difficultyMatch;
        }

        public void setDifficultyMatch(String difficultyMatch) {
            this.difficultyMatch = difficultyMatch;
        }

        public List<String> getTypeCoverage() {
            return typeCoverage;
        }

        public void setTypeCoverage(List<String> typeCoverage) {
            this.typeCoverage = typeCoverage;
        }

        public Boolean getAllOptionsPlausible() {
            return allOptionsPlausible;
        }

        public void setAllOptionsPlausible(Boolean allOptionsPlausible) {
            this.allOptionsPlausible = allOptionsPlausible;
        }
    }
}
