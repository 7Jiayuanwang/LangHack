package com.english.controller;

import com.english.dto.Result;
import com.english.entity.AnswerRecord;
import com.english.service.AnswerRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/practice")
@PreAuthorize("hasRole('STUDENT')")
public class PracticeController {

    @Autowired
    private AnswerRecordService answerRecordService;

    /**
     * 提交答题记录
     */
    @PostMapping("/submit")
    public Result<String> submitAnswer(@RequestBody AnswerRecord record) {
        try {
            answerRecordService.saveAnswerRecord(record);
            return Result.success("答题记录提交成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取我的答题记录
     */
    @GetMapping("/records")
    public Result<List<AnswerRecord>> getMyAnswerRecords(@RequestParam Long studentId) {
        try {
            List<AnswerRecord> records = answerRecordService.getStudentAnswerRecords(studentId);
            return Result.success(records);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取答题统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getAnswerStats(@RequestParam Long studentId) {
        try {
            Map<String, Object> stats = answerRecordService.getStudentAnswerStats(studentId);
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
