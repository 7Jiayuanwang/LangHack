package com.english.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.english.dto.Result;
import com.english.entity.Word;
import com.english.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/words")
public class WordController {

    @Autowired
    private WordService wordService;

    @GetMapping
    public Result<Map<String, Object>> getWords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty) {

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("keyword", keyword);
            params.put("difficulty", difficulty);

            Map<String, Object> result = wordService.getWordsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/search")
    public Result<Word> searchWord(@RequestParam String word) {
        try {
            Word foundWord = wordService.getWordByWord(word);
            if (foundWord == null) {
                return Result.error("单词不存在");
            }
            return Result.success(foundWord);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/word/{word}")
    public Result<Word> getWordByWord(@PathVariable String word) {
        try {
            Word foundWord = wordService.getWordByWord(word);
            if (foundWord == null) {
                return Result.error("单词不存在");
            }
            return Result.success(foundWord);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/random")
    public Result<List<Word>> getRandomWords(@RequestParam(defaultValue = "10") int count) {
        try {
            List<Word> words = wordService.getRandomWords(Math.min(count, 50));
            return Result.success(words);
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
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
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<String> updateWord(@PathVariable Long id, @RequestBody Word word) {
        try {
            word.setId(id);
            boolean success = wordService.updateWord(word);
            if (success) {
                return Result.success("单词更新成功");
            }
            return Result.error("单词更新失败");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteWord(@PathVariable Long id) {
        try {
            boolean success = wordService.deleteWord(id);
            if (success) {
                return Result.success("单词删除成功");
            }
            return Result.error("单词删除失败");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getWordStats() {
        try {
            Map<String, Object> stats = wordService.getWordStats();
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}