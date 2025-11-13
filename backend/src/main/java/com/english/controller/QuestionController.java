package com.english.controller;

import com.english.dto.Result;
import com.english.entity.Question;
import com.english.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    /**
     * 分页查询题目列表（支持过滤）
     */
    @GetMapping
    public Result<Map<String, Object>> getQuestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String examType,
            @RequestParam(required = false) String keyword) {

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("questionType", questionType);
            params.put("difficulty", difficulty);
            params.put("examType", examType);
            params.put("keyword", keyword);

            Map<String, Object> result = questionService.getQuestionsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取题目详情
     */
    @GetMapping("/{id}")
    public Result<Question> getQuestionById(@PathVariable Long id) {
        try {
            Question question = questionService.getQuestionById(id);
            if (question == null) {
                return Result.error("题目不存在");
            }
            return Result.success(question);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 创建题目（管理员/教师）
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> createQuestion(@RequestBody Question question) {
        try {
            questionService.createQuestion(question);
            return Result.success("题目创建成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新题目（管理员/教师）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public Result<String> updateQuestion(@PathVariable Long id, @RequestBody Question question) {
        try {
            question.setId(id);
            questionService.updateQuestion(question);
            return Result.success("题目更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除题目（管理员）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteQuestion(@PathVariable Long id) {
        try {
            questionService.deleteQuestion(id);
            return Result.success("题目删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取知识点相关题目
     */
    @GetMapping("/knowledge-point/{kpId}")
    public Result<List<Question>> getQuestionsByKnowledgePoint(@PathVariable Long kpId) {
        try {
            List<Question> questions = questionService.getQuestionsByKnowledgePoint(kpId);
            return Result.success(questions);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取推荐题目（学生）
     */
    @GetMapping("/recommended")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<Question>> getRecommendedQuestions(
            @RequestParam Long studentId,
            @RequestParam(required = false) Long knowledgePointId,
            @RequestParam(defaultValue = "10") int count) {

        try {
            List<Question> questions = questionService.getRecommendedQuestions(
                studentId, knowledgePointId, count);
            return Result.success(questions);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
