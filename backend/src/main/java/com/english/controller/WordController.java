package com.english.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.english.dto.Result;
import com.english.entity.Word;
import com.english.service.WordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/words")
public class WordController {

    private static final Logger log = LoggerFactory.getLogger(WordController.class);

    @Autowired
    private WordService wordService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<Map<String, Object>> getWords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty) {

        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("keyword", keyword);
            params.put("difficulty", difficulty);

            Map<String, Object> result = wordService.getWordsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            log.error("操作失败", e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    @GetMapping("/search")
    public Result<Word> searchWord(@RequestParam String word) {
        try {
            // 关键词长度限制
            if (word != null && word.length() > 100) {
                word = word.substring(0, 100);
            }

            Word foundWord = wordService.getWordByWord(word);
            if (foundWord == null) {
                return Result.error("单词不存在");
            }
            return Result.success(foundWord);
        } catch (Exception e) {
            log.error("操作失败", e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    @GetMapping("/random")
    public Result<List<Word>> getRandomWords(@RequestParam(defaultValue = "10") int count) {
        try {
            // 边界校验：count 不能为负数或超过限制
            if (count <= 0) {
                count = 10;
            }
            count = Math.min(count, 50);

            List<Word> words = wordService.getRandomWords(count);
            return Result.success(words);
        } catch (Exception e) {
            log.error("操作失败", e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    @GetMapping("/{id}")
    public Result<Word> getWordById(@PathVariable Long id) {
        try {
            Word word = wordService.getWordById(id);
            if (word == null) {
                return Result.error("单词不存在");
            }
            return Result.success(word);
        } catch (Exception e) {
            log.error("操作失败", e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> addWord(@RequestBody Word word) {
        try {
            // 检查单词是否已存在
            if (wordService.getWordByWord(word.getWord()) != null) {
                return Result.error("单词已存在");
            }

            boolean success = wordService.createWord(word);
            if (success) {
                return Result.success("单词添加成功");
            }
            return Result.error("单词添加失败");
        } catch (Exception e) {
            log.error("操作失败", e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> updateWord(@PathVariable Long id, @RequestBody Word word) {
        try {
            word.setId(id);
            boolean success = wordService.updateWord(word);
            if (success) {
                return Result.success("单词更新成功");
            }
            return Result.error("单词更新失败");
        } catch (Exception e) {
            log.error("操作失败", e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> deleteWord(@PathVariable Long id) {
        try {
            boolean success = wordService.deleteWord(id);
            if (success) {
                return Result.success("单词删除成功");
            }
            return Result.error("单词删除失败");
        } catch (Exception e) {
            log.error("操作失败", e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getWordStats() {
        try {
            Map<String, Object> stats = wordService.getWordStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("操作失败", e);
            return Result.error("操作失败，请稍后重试");
        }
    }
}