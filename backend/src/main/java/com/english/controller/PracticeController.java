package com.english.controller;

import com.english.constants.RoleConstants;
import com.english.dto.PracticeSessionDTO;
import com.english.dto.Result;
import com.english.entity.AnswerRecord;
import com.english.entity.Question;
import com.english.service.AnswerRecordService;
import com.english.service.PracticeSessionService;
import com.english.service.QuestionService;
import com.english.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/practice")
@PreAuthorize("hasRole('" + RoleConstants.Annotation.STUDENT + "')")
public class PracticeController {

    @Autowired
    private AnswerRecordService answerRecordService;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private PracticeSessionService practiceSessionService;

    /**
     * Get practice questions with filters
     */
    @GetMapping("/questions")
    public Result<Map<String, Object>> getPracticeQuestions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String examType,
            @RequestParam(required = false) Long knowledgePointId,
            @RequestParam(required = false) String keyword
    ) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("page", page != null ? page : 1);
            params.put("size", size != null ? size : 10);
            params.put("questionType", questionType);
            params.put("difficulty", difficulty);
            params.put("examType", examType);
            params.put("knowledgePointId", knowledgePointId);
            params.put("keyword", keyword);

            Map<String, Object> result = questionService.getQuestionsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Start a new practice session
     */
    @PostMapping("/session/start")
    public Result<PracticeSessionDTO.SessionResponse> startPracticeSession(
            @RequestBody PracticeSessionDTO.StartSessionRequest request,
            Authentication authentication
    ) {
        try {
            // Get student ID from authentication context
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            request.setStudentId(studentId);

            PracticeSessionDTO.SessionResponse response = practiceSessionService.startSession(request);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Get single question detail for practice
     */
    @GetMapping("/questions/{questionId}")
    public Result<PracticeSessionDTO.QuestionDetailDTO> getQuestionDetail(
            @PathVariable Long questionId,
            Authentication authentication
    ) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            PracticeSessionDTO.QuestionDetailDTO questionDetail =
                    questionService.getQuestionDetailForPractice(questionId, studentId);
            return Result.success(questionDetail);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Get question analysis/explanation
     */
    @GetMapping("/questions/{questionId}/analysis")
    public Result<PracticeSessionDTO.QuestionAnalysisDTO> getQuestionAnalysis(
            @PathVariable Long questionId,
            Authentication authentication
    ) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            PracticeSessionDTO.QuestionAnalysisDTO analysis =
                    questionService.getQuestionAnalysis(questionId, studentId);
            return Result.success(analysis);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Submit answer for a question
     */
    @PostMapping("/submit")
    public Result<String> submitAnswer(@RequestBody AnswerRecord record, Authentication authentication) {
        try {
            // Use SecurityUtil to get current student ID for security
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            record.setStudentId(studentId);

            answerRecordService.saveAnswerRecord(record);
            return Result.success("答题记录提交成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Complete a practice session
     */
    @PostMapping("/session/{sessionId}/complete")
    public Result<String> completePracticeSession(
            @PathVariable Long sessionId,
            @RequestBody PracticeSessionDTO.CompleteSessionRequest request
    ) {
        try {
            practiceSessionService.completeSession(sessionId, request);
            return Result.success("练习会话已完成");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Get practice history
     */
    @GetMapping("/history")
    public Result<Map<String, Object>> getPracticeHistory(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            Map<String, Object> history = practiceSessionService.getPracticeHistory(
                    studentId, page, size);
            return Result.success(history);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Get practice statistics
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getAnswerStats(Authentication authentication) {
        try {
            // Get student ID from authentication context
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            Map<String, Object> stats = answerRecordService.getStudentAnswerStats(studentId);
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Get recommended questions based on student's weakness
     */
    @GetMapping("/recommended")
    public Result<List<Question>> getRecommendedQuestions(
            @RequestParam(required = false) Long knowledgePointId,
            @RequestParam(required = false) Integer count,
            Authentication authentication
    ) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            int questionCount = count != null ? count : 10;

            List<Question> questions = questionService.getRecommendedQuestions(
                    studentId, knowledgePointId, questionCount);
            return Result.success(questions);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Toggle favorite for a question
     */
    @PostMapping("/questions/{questionId}/favorite")
    public Result<String> favoriteQuestion(
            @PathVariable Long questionId,
            Authentication authentication
    ) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            answerRecordService.toggleFavorite(questionId, studentId);
            return Result.success("收藏状态已更新");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Remove favorite (same as toggle - for REST compliance)
     */
    @DeleteMapping("/questions/{questionId}/favorite")
    public Result<String> unfavoriteQuestion(
            @PathVariable Long questionId,
            Authentication authentication
    ) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            answerRecordService.toggleFavorite(questionId, studentId);
            return Result.success("已取消收藏");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Mark question as difficult (implemented via favorite for now)
     */
    @PostMapping("/questions/{questionId}/difficult")
    public Result<String> markDifficult(
            @PathVariable Long questionId,
            Authentication authentication
    ) {
        try {
            // For now, using the same mechanism as favorites
            // In a production system, you'd have a separate difficult_questions table
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            answerRecordService.toggleFavorite(questionId, studentId);
            return Result.success("已标记为难题");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Get wrong questions for student
     */
    @GetMapping("/wrong-questions")
    public Result<List<Question>> getWrongQuestions(Authentication authentication) {
        try {
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            List<Question> wrongQuestions = questionService.getWrongQuestions(studentId);
            return Result.success(wrongQuestions);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * Get my answer records
     */
    @GetMapping("/records")
    public Result<List<AnswerRecord>> getMyAnswerRecords(Authentication authentication) {
        try {
            // Get student ID from authentication context
            Long studentId = SecurityUtil.getCurrentStudentId(authentication);
            List<AnswerRecord> records = answerRecordService.getStudentAnswerRecords(studentId);
            return Result.success(records);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
