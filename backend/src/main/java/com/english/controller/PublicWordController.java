package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.entity.Word;
import com.english.service.WordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开单词控制器
 * 为普通用户提供单词查询服务
 */
@RestController
@RequestMapping("/api/public/words")
public class PublicWordController {

    private static final Logger log = LoggerFactory.getLogger(PublicWordController.class);

    @Autowired
    private WordService wordService;

    /**
     * 获取单词列表
     */
    @GetMapping
    public Result<Map<String, Object>> getWords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String keyword) {
        try {
            // 限制最大获取数量，确保页码和每页数量合法
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100)); // 添加最小值校验，防止负数

            Page<Word> pageParam = new Page<>(page, size);
            QueryWrapper<Word> queryWrapper = new QueryWrapper<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                if (keyword.length() > 100) {
                    return Result.error("搜索关键词过长");
                }
                queryWrapper.like("word", escapeLikeWildcard(keyword));
            }

            queryWrapper.eq("deleted", 0);
            queryWrapper.orderByAsc("word");

            IPage<Word> pageResult = wordService.page(pageParam, queryWrapper);

            Map<String, Object> result = new HashMap<>();
            result.put("records", pageResult.getRecords());
            result.put("total", pageResult.getTotal());
            result.put("pages", pageResult.getPages());
            result.put("current", pageResult.getCurrent());
            result.put("size", pageResult.getSize());

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取单词列表失败", e);
            return Result.error("获取单词列表失败，请稍后重试");
        }
    }

    /**
     * 搜索单词
     */
    @GetMapping("/search")
    public Result<List<Word>> searchWords(@RequestParam String word) {
        if (word == null || word.trim().isEmpty()) {
            return Result.error("搜索关键词不能为空");
        }
        if (word.length() > 100) {
            return Result.error("搜索关键词过长");
        }
        try {
            QueryWrapper<Word> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("deleted", 0)
                        .like("word", escapeLikeWildcard(word))
                        .orderByAsc("word")
                        .last("LIMIT 50"); // 限制搜索结果数量

            List<Word> words = wordService.list(queryWrapper);
            return Result.success(words);
        } catch (Exception e) {
            log.error("搜索单词失败, keyword={}", word, e);
            return Result.error("搜索单词失败，请稍后重试");
        }
    }

    /**
     * 获取单词详情
     */
    @GetMapping("/{wordId}")
    public Result<Word> getWordDetail(@PathVariable Long wordId) {
        try {
            QueryWrapper<Word> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("id", wordId).eq("deleted", 0);
            Word word = wordService.getOne(queryWrapper);
            if (word == null) {
                return Result.error("单词不存在");
            }
            return Result.success(word);
        } catch (Exception e) {
            log.error("获取单词详情失败, wordId={}", wordId, e);
            return Result.error("获取单词详情失败，请稍后重试");
        }
    }

    /**
     * 转义 SQL LIKE 特殊字符，防止通配符注入
     * LIKE 中 % 和 _ 是特殊字符，未转义会导致意外的模式匹配
     */
    private String escapeLikeWildcard(String input) {
        if (input == null) return null;
        return input.replace("\\", "\\\\")  // 转义反斜杠
                    .replace("%", "\\%")    // 转义百分号
                    .replace("_", "\\_");   // 转义下划线
    }
}
