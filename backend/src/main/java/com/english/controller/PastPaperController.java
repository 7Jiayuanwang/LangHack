package com.english.controller;

import com.english.dto.Result;
import com.english.entity.PastPaper;
import com.english.entity.PastPaperAttempt;
import com.english.entity.User;
import com.english.mapper.PastPaperAttemptMapper;
import com.english.mapper.PastPaperMapper;
import com.english.service.MediaService;
import com.english.service.HiAgentService;
import com.english.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/past-papers")
@RequiredArgsConstructor
@Slf4j
public class PastPaperController {
    private static final int MAX_GRADING_STEM_LENGTH = 2_000;
    private static final int MAX_GRADING_PASSAGE_LENGTH = 6_000;
    private static final int MAX_GRADING_REFERENCE_LENGTH = 6_000;
    private static final int MAX_GRADING_ANSWER_LENGTH = 8_000;
    private static final Set<String> CATEGORIES = new HashSet<>(Arrays.asList("cet4", "cet6", "postgraduate"));
    private static final Set<String> QUESTION_TYPES = new HashSet<>(Arrays.asList("single_choice", "cloze", "matching", "translation", "writing", "listening"));
    private final PastPaperMapper pastPaperMapper;
    private final PastPaperAttemptMapper pastPaperAttemptMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final MediaService mediaService;
    private final HiAgentService hiAgentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(pastPaperMapper.findPublished().stream().map(this::toPayload).collect(Collectors.toList()));
    }

    @PostMapping("/attempts")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Transactional
    public Result<Map<String, Object>> submitAttempt(@RequestBody Map<String, Object> payload,
                                                      Authentication authentication) {
        User student = userService.getUserByUsername(authentication.getName());
        if (student == null) return Result.error("用户不存在");
        Long pastPaperId = nullableLong(payload.get("pastPaperId"));
        PastPaper paper = pastPaperId == null ? null : pastPaperMapper.findById(pastPaperId);
        if (paper == null || !Boolean.TRUE.equals(paper.getPublished())) return Result.error("试卷不存在或未发布");
        String paperKey = paper.getPaperKey();
        String category = paper.getCategory().toLowerCase(Locale.ROOT);
        String paperTitle = paper.getTitle();
        if (paperKey == null || paperKey.isBlank() || !CATEGORIES.contains(category)) return Result.error("试卷信息无效");

        Map<String, Object> answers = payload.get("answers") instanceof Map<?, ?> answerMap
                ? toStringKeyMap(answerMap) : Collections.emptyMap();
        Map<String, Object> content;
        try {
            content = objectMapper.readValue(paper.getContentJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Result.error("试卷内容格式无效");
        }
        Object questionsObject = content.get("questions");
        if (!(questionsObject instanceof List<?> questions) || questions.isEmpty()) return Result.error("试卷没有可判分题目");

        int questionCount = questions.size();
        int answeredCount = 0;
        int objectiveScore = 0;
        int objectiveTotal = 0;
        int subjectiveCount = 0;
        List<Map<String, Object>> subjectiveQuestions = new ArrayList<>();
        for (Object questionObject : questions) {
            if (!(questionObject instanceof Map<?, ?> rawQuestion)) continue;
            Map<String, Object> question = toStringKeyMap(rawQuestion);
            String questionId = stringValue(question.get("id"));
            String type = stringValue(question.get("type"));
            if (type.isEmpty()) type = "single_choice";
            Object userAnswer = answers.get(questionId);
            boolean answered = isAnswered(type, userAnswer, question);
            if (answered) answeredCount++;
            if (isSubjectiveType(type)) {
                subjectiveCount++;
                if (answered) {
                    Map<String, Object> gradingQuestion = new LinkedHashMap<>();
                    gradingQuestion.put("questionId", questionId);
                    gradingQuestion.put("type", type);
                    gradingQuestion.put("section", question.get("section"));
                    gradingQuestion.put("stem", limitedText(question.get("stem"), MAX_GRADING_STEM_LENGTH));
                    gradingQuestion.put("passage", limitedText(question.get("passage"), MAX_GRADING_PASSAGE_LENGTH));
                    gradingQuestion.put("referenceAnswer", limitedText(question.get("referenceAnswer"), MAX_GRADING_REFERENCE_LENGTH));
                    gradingQuestion.put("studentAnswer", limitedText(userAnswer, MAX_GRADING_ANSWER_LENGTH));
                    subjectiveQuestions.add(gradingQuestion);
                }
            } else {
                objectiveTotal++;
                if (answered && isCorrect(type, userAnswer, question)) objectiveScore++;
            }
        }
        if (answeredCount <= 0) return Result.error("至少回答一道题后才能交卷");

        PastPaperAttempt attempt = new PastPaperAttempt();
        attempt.setStudentId(student.getId());
        attempt.setPaperKey(paperKey);
        attempt.setPastPaperId(pastPaperId);
        attempt.setCategory(category);
        attempt.setPaperTitle(paperTitle);
        attempt.setAnsweredCount(answeredCount);
        attempt.setQuestionCount(questionCount);
        attempt.setObjectiveScore(objectiveScore);
        attempt.setObjectiveTotal(objectiveTotal);
        attempt.setSubjectiveCount(subjectiveCount);
        try {
            attempt.setAnswersJson(objectMapper.writeValueAsString(answers));
        } catch (Exception e) {
            return Result.error("答案格式无效");
        }
        attempt.setSubmittedTime(LocalDateTime.now());

        Map<String, Object> grading = new LinkedHashMap<>();
        grading.put("questions", Collections.emptyList());
        grading.put("overallFeedback", "客观题已完成程序判分。");
        grading.put("objectiveScore", objectiveScore);
        grading.put("objectiveTotal", objectiveTotal);
        grading.put("aiScorePercentage", null);
        String gradingStatus = "COMPLETED";
        BigDecimal aiScorePercentage = null;
        if (!subjectiveQuestions.isEmpty()) {
            Map<String, Object> gradingRequest = new LinkedHashMap<>();
            gradingRequest.put("paperKey", paperKey);
            gradingRequest.put("paperTitle", paperTitle);
            gradingRequest.put("category", category);
            gradingRequest.put("questions", subjectiveQuestions);
            try {
                String aiResponse = hiAgentService.gradePastPaper(objectMapper.writeValueAsString(gradingRequest));
                grading = parseGradingResponse(aiResponse, subjectiveQuestions);
                aiScorePercentage = averageAiScore(grading);
                grading.put("objectiveScore", objectiveScore);
                grading.put("objectiveTotal", objectiveTotal);
                grading.put("aiScorePercentage", aiScorePercentage);
            } catch (Exception e) {
                gradingStatus = "AI_UNAVAILABLE";
                grading.put("overallFeedback", "客观题已完成判分，主观题智能分析暂时不可用，请稍后重试。");
                log.warn("Past paper AI grading failed paperId={} studentId={}: {}", pastPaperId, student.getId(), e.getMessage());
            }
        }
        BigDecimal finalPercentage = calculateFinalPercentage(objectiveScore, aiScorePercentage, subjectiveQuestions.size(), questionCount);
        BigDecimal persistedScorePercentage = finalPercentage != null
                ? finalPercentage
                : percentage(objectiveScore, objectiveTotal);
        attempt.setGradingStatus(gradingStatus);
        attempt.setAiScorePercentage(aiScorePercentage);
        attempt.setFinalScorePercentage(finalPercentage);
        // Preserve the legacy objective score when subjective AI grading is unavailable.
        attempt.setScorePercentage(persistedScorePercentage);
        try {
            attempt.setGradingJson(objectMapper.writeValueAsString(grading));
        } catch (Exception e) {
            return Result.error("判分结果保存失败");
        }
        attempt.setGradedTime(LocalDateTime.now());
        pastPaperAttemptMapper.insert(attempt);
        Map<String, Object> result = new HashMap<>();
        result.put("attemptId", attempt.getId());
        result.put("objectiveScore", objectiveScore);
        result.put("objectiveTotal", objectiveTotal);
        result.put("aiScorePercentage", aiScorePercentage);
        result.put("finalScorePercentage", finalPercentage);
        result.put("gradingStatus", gradingStatus);
        result.put("grading", grading);
        result.put("submittedTime", attempt.getSubmittedTime());
        return Result.success(result);
    }

    private boolean isSubjectiveType(String type) {
        return "translation".equals(type) || "writing".equals(type) || "short_answer".equals(type);
    }

    private boolean isAnswered(String type, Object value, Map<String, Object> question) {
        if (value == null) return false;
        if (isSubjectiveType(type)) return !stringValue(value).isBlank();
        if ("cloze".equals(type)) {
            if (!(value instanceof List<?> list)) return false;
            Object expected = question.get("answers");
            return expected instanceof List<?> expectedList && list.size() == expectedList.size()
                    && list.stream().allMatch(item -> item instanceof Number);
        }
        if ("matching".equals(type)) {
            if (!(value instanceof Map<?, ?> map) || !(question.get("items") instanceof List<?> items)) return false;
            return items.stream().allMatch(item -> item instanceof Map<?, ?> itemMap
                    && map.get(stringValue(((Map<?, ?>) itemMap).get("id"))) instanceof Number);
        }
        return value instanceof Number;
    }

    private boolean isCorrect(String type, Object value, Map<String, Object> question) {
        if ("cloze".equals(type) && value instanceof List<?> actual && question.get("answers") instanceof List<?> expected) {
            if (actual.size() != expected.size()) return false;
            for (int i = 0; i < expected.size(); i++) {
                if (!(actual.get(i) instanceof Number) || !(expected.get(i) instanceof Number)
                        || ((Number) actual.get(i)).intValue() != ((Number) expected.get(i)).intValue()) return false;
            }
            return true;
        }
        if ("matching".equals(type) && value instanceof Map<?, ?> actual && question.get("items") instanceof List<?> items) {
            for (Object itemObject : items) {
                if (!(itemObject instanceof Map<?, ?> item)) return false;
                String id = stringValue(item.get("id"));
                Object actualValue = actual.get(id);
                Object expectedValue = item.get("answer");
                if (!(actualValue instanceof Number) || !(expectedValue instanceof Number)
                        || ((Number) actualValue).intValue() != ((Number) expectedValue).intValue()) return false;
            }
            return true;
        }
        Object expected = question.get("answer");
        return value instanceof Number && expected instanceof Number
                && ((Number) value).intValue() == ((Number) expected).intValue();
    }

    private Map<String, Object> parseGradingResponse(String response, List<Map<String, Object>> questions) throws Exception {
        String normalized = response == null ? "" : response.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        Map<String, Object> result = objectMapper.readValue(normalized, new TypeReference<Map<String, Object>>() {});
        if (!(result.get("questions") instanceof List<?>)) throw new IllegalArgumentException("AI 判分结果缺少 questions");
        Set<String> expectedIds = questions.stream().map(q -> stringValue(q.get("questionId"))).collect(Collectors.toSet());
        List<Map<String, Object>> valid = new ArrayList<>();
        for (Object item : (List<?>) result.get("questions")) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> row = toStringKeyMap(raw);
            String id = stringValue(row.get("questionId"));
            if (!expectedIds.contains(id)) continue;
            double score = row.get("score") instanceof Number n ? n.doubleValue() : -1;
            if (score < 0 || score > 100) continue;
            row.put("score", BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
            valid.add(row);
        }
        if (valid.size() != expectedIds.size()) throw new IllegalArgumentException("AI 判分结果题目不完整");
        result.put("questions", valid);
        result.putIfAbsent("overallFeedback", "");
        return result;
    }

    private BigDecimal averageAiScore(Map<String, Object> grading) {
        if (!(grading.get("questions") instanceof List<?> list) || list.isEmpty()) return null;
        double sum = 0;
        for (Object item : list) if (item instanceof Map<?, ?> row && row.get("score") instanceof Number n) sum += n.doubleValue();
        return BigDecimal.valueOf(sum / list.size()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateFinalPercentage(int objectiveScore, BigDecimal aiScore,
                                                int gradedSubjectiveCount, int questionCount) {
        if (questionCount <= 0) return BigDecimal.ZERO.setScale(2);
        if (gradedSubjectiveCount > 0 && aiScore == null) return null;
        double points = objectiveScore;
        if (aiScore != null && gradedSubjectiveCount > 0) points += aiScore.doubleValue() / 100.0 * gradedSubjectiveCount;
        return BigDecimal.valueOf(points * 100.0 / questionCount).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(int score, int total) {
        return total > 0 ? BigDecimal.valueOf(score * 100.0 / total).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String limitedText(Object value, int maxLength) {
        String text = stringValue(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        try {
            JsonNode root = objectMapper.valueToTree(payload);
            String category = text(root, "category");
            String title = text(root, "title");
            JsonNode questions = root.get("questions");
            if (!CATEGORIES.contains(category)) return Result.error("考试分类无效");
            if (title.isEmpty() || title.length() > 255) return Result.error("试卷名称不能为空且不能超过255字");
            if (questions == null || !questions.isArray() || questions.size() == 0) return Result.error("试卷必须包含题目");
            if (questions.size() > 500) return Result.error("单套试卷不能超过500题");
            validateQuestions(questions);

            PastPaper paper = new PastPaper();
            paper.setPaperKey("paper-" + UUID.randomUUID());
            paper.setCategory(category);
            paper.setTitle(title);
            paper.setExamDate(text(root, "date"));
            paper.setDurationMinutes(root.path("duration").asInt(30));
            paper.setScope(text(root, "scope"));
            paper.setQuestionCount(questions.size());
            paper.setContentJson(objectMapper.writeValueAsString(payload));
            paper.setPublished(true);
            pastPaperMapper.insert(paper);
            return Result.success(toPayload(paper));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("真题保存失败");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        if (pastPaperMapper.deleteById(id) == 0) return Result.error("试卷不存在");
        return Result.success(null);
    }

    @PostMapping(value = "/{id}/audio", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> uploadAudio(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return Result.error("请选择听力音频文件");
        PastPaper paper = pastPaperMapper.findById(id);
        if (paper == null) return Result.error("试卷不存在");
        try {
            var media = mediaService.uploadMedia(file, "AUDIO", null);
            String audioUrl = "/api/media/" + media.getId() + "/download";

            Map<String, Object> contentMap = objectMapper.readValue(paper.getContentJson(), new TypeReference<Map<String, Object>>() {});
            // 1. 设置 paper-level audioUrl（前端 PastPapers.vue 会优先用题 audioUrl，缺失则回退 paper.audioUrl）
            contentMap.put("audioUrl", audioUrl);
            // 2. 仅给 type=listening 的题注入 audioUrl（避免污染写作/阅读/翻译题）
            Object qsObj = contentMap.get("questions");
            if (qsObj instanceof List<?> qs) {
                for (Object qo : qs) {
                    if (qo instanceof Map<?, ?> qm) {
                        Object t = qm.get("type");
                        if ("listening".equals(t)) {
                            ((Map<String, Object>) qm).put("audioUrl", audioUrl);
                        }
                    }
                }
            }
            paper.setContentJson(objectMapper.writeValueAsString(contentMap));
            pastPaperMapper.update(paper);
            return Result.success(toPayload(paper));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("听力绑定失败：" + e.getMessage());
        }
    }

    private void validateQuestions(JsonNode questions) {
        for (int index = 0; index < questions.size(); index++) {
            JsonNode question = questions.get(index);
            if (text(question, "stem").isEmpty()) throw new IllegalArgumentException("第" + (index + 1) + "题缺少题干");
            String type = text(question, "type");
            if (type.isEmpty()) type = "single_choice";
            if (!QUESTION_TYPES.contains(type)) throw new IllegalArgumentException("第" + (index + 1) + "题题型无效");
            JsonNode options = question.get("options");
            if ("single_choice".equals(type) || "listening".equals(type)) {
                if (options == null || !options.isArray() || options.size() != 4) throw new IllegalArgumentException("第" + (index + 1) + "题必须有A-D四个选项");
                int answer = question.path("answer").asInt(-1);
                if (answer < 0 || answer > 3) throw new IllegalArgumentException("第" + (index + 1) + "题答案无效");
            } else if ("cloze".equals(type)) {
                JsonNode answers = question.get("answers");
                if (options == null || !options.isArray() || options.size() == 0 || answers == null || !answers.isArray() || answers.size() == 0) throw new IllegalArgumentException("第" + (index + 1) + "题完形内容不完整");
            } else if ("matching".equals(type)) {
                JsonNode items = question.get("items");
                if (options == null || !options.isArray() || options.size() == 0 || items == null || !items.isArray() || items.size() == 0) throw new IllegalArgumentException("第" + (index + 1) + "题匹配内容不完整");
            } else if (text(question, "referenceAnswer").isEmpty()) {
                throw new IllegalArgumentException("第" + (index + 1) + "题缺少参考答案");
            }
            if (text(question, "analysis").isEmpty()) throw new IllegalArgumentException("第" + (index + 1) + "题缺少解析");
        }
    }

    private Map<String, Object> toPayload(PastPaper paper) {
        try {
            Map<String, Object> payload = objectMapper.readValue(paper.getContentJson(), new TypeReference<Map<String, Object>>() {});
            payload.put("id", paper.getId());
            payload.put("serverManaged", true);
            payload.put("status", "ready");
            return payload;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid stored paper JSON", e);
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private int nonNegativeInt(Object value) {
        if (value instanceof Number number) return Math.max(0, number.intValue());
        try {
            return Math.max(0, Integer.parseInt(stringValue(value)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Long nullableLong(Object value) {
        if (value == null || stringValue(value).isEmpty()) return null;
        try {
            return Long.valueOf(stringValue(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
