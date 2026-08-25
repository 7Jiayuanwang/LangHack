package com.english.controller;

import com.english.dto.Result;
import com.english.entity.PastPaper;
import com.english.mapper.PastPaperMapper;
import com.english.service.MediaService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/past-papers")
@RequiredArgsConstructor
public class PastPaperController {
    private static final Set<String> CATEGORIES = new HashSet<>(Arrays.asList("cet4", "cet6", "postgraduate"));
    private static final Set<String> QUESTION_TYPES = new HashSet<>(Arrays.asList("single_choice", "cloze", "matching", "translation", "writing", "listening"));
    private final PastPaperMapper pastPaperMapper;
    private final ObjectMapper objectMapper;
    private final MediaService mediaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(pastPaperMapper.findPublished().stream().map(this::toPayload).collect(Collectors.toList()));
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
            if ("single_choice".equals(type)) {
                if (options == null || !options.isArray() || options.size() != 4) throw new IllegalArgumentException("第" + (index + 1) + "题必须有A-D四个选项");
                int answer = question.path("answer").asInt(-1);
                if (answer < 0 || answer > 3) throw new IllegalArgumentException("第" + (index + 1) + "题答案无效");
            } else if ("cloze".equals(type)) {
                JsonNode answers = question.get("answers");
                if (options == null || !options.isArray() || options.size() == 0 || answers == null || !answers.isArray() || answers.size() == 0) throw new IllegalArgumentException("第" + (index + 1) + "题完形内容不完整");
            } else if ("matching".equals(type)) {
                JsonNode items = question.get("items");
                if (options == null || !options.isArray() || options.size() == 0 || items == null || !items.isArray() || items.size() == 0) throw new IllegalArgumentException("第" + (index + 1) + "题匹配内容不完整");
            } else if (text(question, "referenceAnswer").isEmpty() && !"listening".equals(type)) {
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
}
