package com.english.service.impl;

import com.english.dto.learner.AgentStudyLoopDto;
import com.english.datasource.UseMaster;
import com.english.entity.LearnerReviewTask;
import com.english.entity.StudentAiQuizAttempt;
import com.english.entity.StudentAiQuizRecord;
import com.english.entity.Text;
import com.english.service.StudentAiQuizService;
import com.english.service.TextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns already persisted article exercises into evidence-backed next steps. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStudyLoopService {

    private final StudentAiQuizService studentAiQuizService;
    private final TextService textService;
    private final AgentReviewPlanService reviewPlanService;

    @UseMaster
    public AgentStudyLoopDto forUser(Long userId) {
        AgentStudyLoopDto result = new AgentStudyLoopDto();
        LocalDateTime now = LocalDateTime.now();
        result.setDueReviewCount((int) Math.min(Integer.MAX_VALUE,
                reviewPlanService.countDue(userId, now)));
        for (LearnerReviewTask review : reviewPlanService.listDue(userId, now, 5)) {
            Text text = textService.getTextById(review.getTextId());
            if (text == null) continue;
            AgentStudyLoopDto.ReviewTask task = new AgentStudyLoopDto.ReviewTask();
            task.setTextId(review.getTextId());
            task.setTitle(text.getTitle() == null ? "已学习文章" : text.getTitle());
            task.setDueAt(review.getDueAt());
            task.setLatestCorrect(safe(review.getLastCorrect()));
            task.setLatestTotal(safe(review.getLastTotal()));
            boolean weak = safe(review.getLastCorrect()) * 100 < safe(review.getLastTotal()) * 80;
            task.setReason(weak ? "上次练习还有错题，建议再练一次"
                    : "距离上次练习已有一段时间，做一次巩固练习");
            result.getDueReviews().add(task);
        }

        List<StudentAiQuizAttempt> attempts = studentAiQuizService.listRecentMyAttempts(userId, 200);
        if (attempts == null || attempts.isEmpty()) return result;
        result.setSampleLimited(attempts.size() >= 200);

        Set<Long> seenTexts = new HashSet<>();
        Set<Long> latestAttemptIds = new HashSet<>();
        Set<String> seenQuestions = new HashSet<>();
        for (StudentAiQuizAttempt attempt : attempts) {
            if (attempt.getTextId() == null) continue;
            result.setAttemptCount(result.getAttemptCount() + 1);
            result.setAnsweredQuestionCount(result.getAnsweredQuestionCount() + safe(attempt.getTotalQuestions()));
            result.setCorrectQuestionCount(result.getCorrectQuestionCount() + safe(attempt.getCorrectCount()));
            LocalDateTime submitted = attempt.getSubmittedTime();
            if (submitted != null && !submitted.isBefore(now.minusDays(7))) {
                result.setRecentSevenDayAttempts(result.getRecentSevenDayAttempts() + 1);
            }

            if (!seenTexts.add(attempt.getTextId())) continue;
            latestAttemptIds.add(attempt.getId());
        }
        result.setPracticedArticleCount(seenTexts.size());

        // Return only a few concrete, owned errors. Never label a skill as weak from one item.
        int detailLookups = 0;
        for (StudentAiQuizAttempt attempt : attempts) {
            if (result.getRecentWrongQuestions().size() >= 5) break;
            if (attempt.getTextId() == null || attempt.getId() == null
                    || !latestAttemptIds.contains(attempt.getId())) continue;
            if (++detailLookups > 12) break;
            try {
                Map<String, Object> detail = studentAiQuizService.getMyAttemptDetail(userId, attempt.getId());
                Object records = detail.get("records");
                if (!(records instanceof List<?>)) continue;
                Text text = textService.getTextById(attempt.getTextId());
                for (Object value : (List<?>) records) {
                    if (!(value instanceof StudentAiQuizRecord)) continue;
                    StudentAiQuizRecord record = (StudentAiQuizRecord) value;
                    if (!Boolean.FALSE.equals(record.getIsCorrect()) || record.getQuestionSnapshot() == null) continue;
                    String key = attempt.getTextId() + ":" + record.getQuestionSnapshot();
                    if (!seenQuestions.add(key)) continue;
                    AgentStudyLoopDto.WrongQuestion wrong = new AgentStudyLoopDto.WrongQuestion();
                    wrong.setTextId(attempt.getTextId());
                    wrong.setTitle(text != null ? text.getTitle() : "已学习文章");
                    wrong.setQuestion(record.getQuestionSnapshot());
                    wrong.setUserAnswer(record.getUserAnswer());
                    wrong.setCorrectAnswer(record.getCorrectAnswer());
                    wrong.setAnalysis(record.getAnalysisSnapshot());
                    result.getRecentWrongQuestions().add(wrong);
                    if (result.getRecentWrongQuestions().size() >= 5) break;
                }
            } catch (Exception e) {
                log.debug("学习伙伴读取答题明细失败: attemptId={}, reason={}", attempt.getId(), e.getMessage());
            }
        }
        return result;
    }

    private int safe(Integer value) { return value == null ? 0 : Math.max(0, value); }
}
