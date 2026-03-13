package com.english.controller;

import com.english.dto.Result;
import com.english.service.ECDICTService;
import com.english.service.WordNetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WordNet词汇查询控制器
 * 提供交互式课文阅读的词汇查询功能
 */
@RestController
@RequestMapping("/api/wordnet")
public class WordNetController {

    private static final Logger logger = LoggerFactory.getLogger(WordNetController.class);

    @Autowired
    private WordNetService wordNetService;

    @Autowired
    private ECDICTService ecdictService;

    /**
     * 获取单词详细信息（集成ECDICT优先）
     * @param word 要查询的单词
     * @return 单词详细信息（包括定义、同义词、反义词等）
     */
    @GetMapping("/word/{word}")
    @Cacheable(value = "wordnet", key = "#word.toLowerCase()", unless = "#result == null || #result.getCode() != 200")
    public Result<Map<String, Object>> getWordInfo(@PathVariable String word) {
        try {
            if (word == null || word.isEmpty() || word.length() > 100) {
                return Result.error("单词参数无效");
            }
            Map<String, Object> wordInfo = wordNetService.getWordInfo(word);
            return Result.success(wordInfo);
        } catch (Exception e) {
            logger.error("查询单词信息失败", e);

            return Result.error("查询单词信息失败，请稍后重试");
        }
    }

    /**
     * 搜索词汇
     * @param keyword 搜索关键词
     * @param limit 返回结果数量限制
     * @return 匹配的词汇列表
     */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> searchWords(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            if (keyword == null || keyword.trim().isEmpty() || keyword.length() > 100) {
                return Result.error("关键词不能为空且不超过100字符");
            }
            // FIX: 在参数接收层添加 limit 的边界检查，防止负数
            if (limit <= 0) {
                limit = 10;
            }
            int safeLim = Math.max(1, Math.min(limit, 100));
            List<Map<String, Object>> results = wordNetService.searchWords(keyword, safeLim);
            return Result.success(results);
        } catch (Exception e) {
            logger.error("搜索词汇失败", e);

            return Result.error("搜索词汇失败，请稍后重试");
        }
    }

    /**
     * 获取词典统计信息
     * @return 词典统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getDictionaryStats() {
        try {
            Map<String, Object> stats = wordNetService.getDictionaryStats();
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取词典统计失败", e);

            return Result.error("获取词典统计失败，请稍后重试");
        }
    }

    /**
     * 检查ECDICT服务状态
     * @return ECDICT服务状态
     */
    @GetMapping("/ecdict/status")
    public Result<Map<String, Object>> getECDICTStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("available", ecdictService.isECDICTAvailable());

            if (ecdictService.isECDICTAvailable()) {
                Map<String, Object> ecdictStats = ecdictService.getDictionaryStats();
                status.putAll(ecdictStats);
            } else {
                status.put("message", "ECDICT服务不可用");
            }

            return Result.success(status);
        } catch (Exception e) {
            logger.error("获取ECDICT状态失败", e);

            return Result.error("获取ECDICT状态失败，请稍后重试");
        }
    }

    /**
     * 获取文本中可点击的单词
     * @param text 课文文本
     * @return 可点击单词列表
     */
    @PostMapping("/text/words")
    public Result<List<Map<String, Object>>> getClickableWords(@RequestBody Map<String, String> request) {
        try {
            // FIX: 添加请求体 null 检查，防止 NPE
            if (request == null) {
                return Result.error("请求体不能为空");
            }
            String text = request.get("text");
            if (text == null || text.trim().isEmpty()) {
                return Result.error("文本内容不能为空");
            }
            if (text.length() > 50000) {
                return Result.error("文本内容过长");
            }

            List<Map<String, Object>> clickableWords = wordNetService.getClickableWordsFromText(text);
            return Result.success(clickableWords);
        } catch (Exception e) {
            logger.error("获取可点击单词失败", e);

            return Result.error("获取可点击单词失败，请稍后重试");
        }
    }

    /**
     * 批量获取多个单词的信息
     * @param request 包含单词列表的请求体
     * @return 单词信息列表
     */
    @PostMapping("/words/batch")
    public Result<Map<String, Object>> getBatchWordInfo(@RequestBody Map<String, Object> request) {
        try {
            // FIX: 添加请求体 null 检查，防止 NPE
            if (request == null) {
                return Result.error("请求体不能为空");
            }
            Object wordsObj = request.get("words");
            if (!(wordsObj instanceof List)) {
                return Result.error("单词列表格式错误");
            }
            @SuppressWarnings("unchecked")
            List<String> words = (List<String>) wordsObj;
            if (words == null || words.isEmpty()) {
                return Result.error("单词列表不能为空");
            }
            if (words.size() > 200) {
                return Result.error("批量查询最多支持200个单词");
            }

            // 验证每个单词的长度
            for (String word : words) {
                if (word == null || word.isEmpty() || word.length() > 100) {
                    return Result.error("单词参数无效（长度应在1-100字符）");
                }
            }

            Map<String, Object> result = new java.util.HashMap<>();
            for (String word : words) {
                Map<String, Object> wordInfo = wordNetService.getWordInfo(word);
                result.put(word, wordInfo);
            }

            return Result.success(result);
        } catch (Exception e) {
            logger.error("批量查询单词信息失败", e);

            return Result.error("批量查询单词信息失败，请稍后重试");
        }
    }

    /**
     * 查询或创建单词信息（用于自动创建新词汇）
     * 如果数据库中不存在该词，则从ECDICT获取信息并自动创建
     * @param word 要查询或创建的单词
     * @return 单词详细信息（包含是否为新创建的标识）
     */
    @GetMapping("/word/{word}/get-or-create")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER', 'ADMIN')")
    public Result<Map<String, Object>> getOrCreateWord(@PathVariable String word) {
        try {
            if (word == null || word.isEmpty() || word.length() > 100) {
                return Result.error("单词参数无效");
            }
            Map<String, Object> wordInfo = wordNetService.getOrCreateWord(word);
            return Result.success(wordInfo);
        } catch (Exception e) {
            logger.error("查询或创建单词信息失败", e);

            return Result.error("查询或创建单词信息失败，请稍后重试");
        }
    }

    /**
     * 检查WordNet服务状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getWordNetStatus() {
        try {
            Map<String, Object> status = new java.util.HashMap<>();
            status.put("available", wordNetService.isWordNetAvailable());
            status.put("message", wordNetService.isWordNetAvailable() ?
                "WordNet服务正常" : "WordNet服务不可用，使用数据库词汇数据");

            return Result.success(status);
        } catch (Exception e) {
            logger.error("获取WordNet状态失败", e);

            return Result.error("获取WordNet状态失败，请稍后重试");
        }
    }
}
