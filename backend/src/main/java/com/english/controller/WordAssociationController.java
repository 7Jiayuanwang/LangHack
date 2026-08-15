package com.english.controller;

import com.english.dto.Result;
import com.english.exception.BusinessException;
import com.english.service.WordAssociationService;
import com.english.service.WordEtymologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单词联想控制器
 * 提供同义词、衍生词、常用搭配查询
 */
@RestController
@RequestMapping("/api/student/word-associations")
public class WordAssociationController {

    private static final Logger logger = LoggerFactory.getLogger(WordAssociationController.class);

    @Autowired
    private WordAssociationService wordAssociationService;

    @Autowired
    private WordEtymologyService wordEtymologyService;

    /**
     * 查询单词的完整联想信息
     * 返回同义词、衍生词、常用搭配三部分
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
    public Result<Map<String, Object>> getAssociations(@RequestParam String word) {
        try {
            if (word == null || word.trim().isEmpty()) {
                return Result.error("单词不能为空");
            }
            if (word.trim().length() > 100) {
                return Result.error("单词长度不能超过100个字符");
            }

            Map<String, Object> result = wordAssociationService.getAssociations(word.trim());
            // 加入词根词缀分析结果
            result.put("etymology", wordEtymologyService.analyze(word.trim()));
            return Result.success(result);

        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("查询单词联想失败: word={}", word, e);
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 仅查询同义词
     */
    @GetMapping("/synonyms")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
    public Result<List<Map<String, Object>>> getSynonyms(@RequestParam String word) {
        try {
            if (word == null || word.trim().isEmpty()) {
                return Result.error("单词不能为空");
            }
            if (word.trim().length() > 100) {
                return Result.error("单词长度不能超过100个字符");
            }

            List<Map<String, Object>> synonyms = wordAssociationService.getSynonyms(word.trim());
            return Result.success(synonyms);

        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("查询同义词失败: word={}", word, e);
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 仅查询衍生词
     */
    @GetMapping("/derivatives")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
    public Result<List<Map<String, Object>>> getDerivatives(@RequestParam String word) {
        try {
            if (word == null || word.trim().isEmpty()) {
                return Result.error("单词不能为空");
            }
            if (word.trim().length() > 100) {
                return Result.error("单词长度不能超过100个字符");
            }

            List<Map<String, Object>> derivatives = wordAssociationService.getDerivatives(word.trim());
            return Result.success(derivatives);

        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("查询衍生词失败: word={}", word, e);
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 仅查询常用搭配
     */
    @GetMapping("/collocations")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
    public Result<List<Map<String, Object>>> getCollocations(@RequestParam String word) {
        try {
            if (word == null || word.trim().isEmpty()) {
                return Result.error("单词不能为空");
            }
            if (word.trim().length() > 100) {
                return Result.error("单词长度不能超过100个字符");
            }

            List<Map<String, Object>> collocations = wordAssociationService.getCollocations(word.trim());
            return Result.success(collocations);

        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("查询搭配失败: word={}", word, e);
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 词根词缀分析（前缀、后缀、词根、联想记忆、词性变化）
     */
    @GetMapping("/etymology")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN', 'OPS')")
    public Result<Map<String, Object>> getEtymology(@RequestParam String word) {
        try {
            if (word == null || word.trim().isEmpty()) {
                return Result.error("单词不能为空");
            }
            Map<String, Object> result = wordEtymologyService.analyze(word.trim());
            return Result.success(result);
        } catch (Exception e) {
            logger.error("词根词缀分析失败: word={}", word, e);
            return Result.error("分析失败，请稍后重试");
        }
    }
}
