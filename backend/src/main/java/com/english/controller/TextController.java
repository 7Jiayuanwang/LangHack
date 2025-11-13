package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Text;
import com.english.service.TextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/texts")
public class TextController {

    @Autowired
    private TextService textService;

    @GetMapping
    public Result<Map<String, Object>> getTextList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Long unitId) {

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("keyword", keyword);
            params.put("difficulty", difficulty);
            params.put("unitId", unitId);

            Map<String, Object> result = textService.getTextsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<Text> getTextById(@PathVariable Long id) {
        try {
            Text text = textService.getTextById(id);
            if (text == null) {
                return Result.error("课文不存在");
            }
            return Result.success(text);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/unit/{unitId}")
    public Result<List<Text>> getTextsByUnit(@PathVariable Long unitId) {
        try {
            List<Text> texts = textService.getTextsByUnit(unitId);
            return Result.success(texts);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/course/{courseId}")
    public Result<List<Text>> getTextsByCourse(@PathVariable Long courseId) {
        try {
            List<Text> texts = textService.getTextsByCourse(courseId);
            return Result.success(texts);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> createText(@RequestBody Text text) {
        try {
            textService.createText(text);
            return Result.success("课文创建成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateText(@PathVariable Long id, @RequestBody Text text) {
        try {
            text.setId(id);
            textService.updateText(text);
            return Result.success("课文更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteText(@PathVariable Long id) {
        try {
            textService.deleteText(id);
            return Result.success("课文删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> importTextFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("unitId") Long unitId,
            @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "contentType", defaultValue = "ARTICLE") String contentType,
            @RequestParam(value = "orderNum", defaultValue = "1") Integer orderNum,
            @RequestParam(value = "customTitle", required = false) String customTitle) {
        try {
            int count = textService.importTextFromFile(unitId, courseId, file, contentType, orderNum, customTitle);
            return Result.success("成功导入 " + count + " 篇课文");
        } catch (Exception e) {
            return Result.error("导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getTextStats() {
        try {
            Map<String, Object> stats = textService.getTextStats();
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
