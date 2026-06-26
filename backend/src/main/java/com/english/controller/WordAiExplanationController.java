package com.english.controller;

import com.english.dto.Result;
import com.english.dto.WordExplanationDTO;
import com.english.service.WordAiExplanationService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 单词 AI 详细介绍 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/student/word-explanation")
@PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
public class WordAiExplanationController {

    @Autowired
    private WordAiExplanationService wordAiExplanationService;

    /**
     * 获取单词详细介绍
     */
    @PostMapping("/get")
    public Result<WordExplanationDTO> getWordExplanation(@RequestBody WordRequest request) {
        try {
            if (request.getWord() == null || request.getWord().trim().isEmpty()) {
                return Result.error("单词不能为空");
            }

            String word = request.getWord().trim();
            log.info("[单词介绍] 收到请求: word={}, regenerate={}", word, request.getRegenerate());

            WordExplanationDTO explanation;
            if (Boolean.TRUE.equals(request.getRegenerate())) {
                // 强制重新生成
                explanation = wordAiExplanationService.regenerateWordExplanation(word);
            } else {
                // 优先从缓存读取
                explanation = wordAiExplanationService.getWordExplanation(word);
            }

            return Result.success(explanation);

        } catch (IllegalArgumentException e) {
            log.warn("[单词介绍] 参数错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[单词介绍] 处理失败", e);
            return Result.error("获取单词介绍失败：" + e.getMessage());
        }
    }

    @Data
    public static class WordRequest {
        private String word;
        private Boolean regenerate;  // 是否强制重新生成
    }
}
