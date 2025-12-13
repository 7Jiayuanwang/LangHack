package com.english.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.english.dto.Result;
import com.english.entity.Word;
import com.english.service.WordService;
import com.english.util.ExcelUtil;
import com.english.util.FileValidationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
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

    /**
     * Excel批量导入单词
     * Excel格式：word, phonetic, definition, example_sentence, difficulty, part_of_speech
     * 使用事务确保批量导入的原子性
     */
    @PostMapping("/import")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> importWords(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 综合安全验证文件（大小、类型、文件名安全性、magic number验证）
            FileValidationUtil.ValidationResult validationResult =
                FileValidationUtil.validateUploadedExcelFile(file);

            if (!validationResult.isValid()) {
                return Result.error(validationResult.getMessage());
            }

            // 2. 读取Excel
            List<Map<String, String>> dataList = ExcelUtil.readExcel(file);
            if (dataList.isEmpty()) {
                return Result.error("Excel文件为空或格式错误");
            }

            // 3. 转换为Word对象列表
            List<Word> words = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < dataList.size(); i++) {
                Map<String, String> row = dataList.get(i);
                try {
                    // 必填字段检查
                    String wordText = row.get("word");
                    if (wordText == null || wordText.trim().isEmpty()) {
                        errors.add("第" + (i + 2) + "行：单词不能为空");
                        failCount++;
                        continue;
                    }

                    // 检查单词是否已存在
                    if (wordService.getWordByWord(wordText.trim()) != null) {
                        errors.add("第" + (i + 2) + "行：单词'" + wordText + "'已存在");
                        failCount++;
                        continue;
                    }

                    Word word = new Word();
                    word.setWord(wordText.trim());
                    word.setPhonetic(row.getOrDefault("phonetic", ""));
                    word.setDefinition(row.getOrDefault("definition", ""));
                    word.setExampleSentence(row.getOrDefault("example_sentence", ""));

                    // 难度，默认MEDIUM
                    String difficulty = row.getOrDefault("difficulty", "MEDIUM").toUpperCase();
                    if (!difficulty.matches("EASY|MEDIUM|HARD")) {
                        difficulty = "MEDIUM";
                    }
                    word.setDifficulty(difficulty);

                    word.setPartOfSpeech(row.getOrDefault("part_of_speech", ""));

                    words.add(word);
                    successCount++;
                } catch (Exception e) {
                    errors.add("第" + (i + 2) + "行：" + e.getMessage());
                    failCount++;
                }
            }

            // 4. 批量保存
            if (!words.isEmpty()) {
                boolean saved = wordService.batchCreateWords(words);
                if (!saved) {
                    return Result.error("批量保存失败");
                }
            }

            // 5. 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("total", dataList.size());
            result.put("success", successCount);
            result.put("fail", failCount);
            result.put("errors", errors);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("导入失败：" + e.getMessage());
        }
    }
}