package com.english.controller;

import com.english.dto.Result;
import com.english.exception.BusinessException;
import com.english.service.StudentAiQuizService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理员端：学生 AI 题做题记录查询
 */
@RestController
@RequestMapping("/api/admin/quiz-records")
@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
public class AdminQuizRecordsController {

    private static final Logger log = LoggerFactory.getLogger(AdminQuizRecordsController.class);

    @Autowired
    private StudentAiQuizService studentAiQuizService;

    /**
     * 分页查询做题尝试列表
     * 任一过滤条件可省略
     */
    @GetMapping("/attempts")
    public Result<Map<String, Object>> listAttempts(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long textId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            Map<String, Object> data = studentAiQuizService.queryAttemptsForAdmin(
                    studentId, textId, startTime, endTime, page, pageSize);
            return Result.success(data);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("管理员查询做题记录失败", e);
            return Result.error("查询失败，请稍后重试");
        }
    }

    /**
     * 查单次做题尝试明细（含每题快照、答案、对错）
     */
    @GetMapping("/attempts/{attemptId}")
    public Result<Map<String, Object>> getAttemptDetail(@PathVariable Long attemptId) {
        try {
            return Result.success(studentAiQuizService.getAttemptDetailForAdmin(attemptId));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("管理员查询做题记录明细失败 attemptId={}", attemptId, e);
            return Result.error("查询失败，请稍后重试");
        }
    }
}
