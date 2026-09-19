package com.english.service;

import com.english.dto.learner.AgentStudyLoopDto;
import com.english.entity.LearnerReviewTask;
import com.english.entity.StudentAiQuizAttempt;
import com.english.entity.StudentAiQuizRecord;
import com.english.entity.Text;
import com.english.service.impl.AgentStudyLoopService;
import com.english.service.impl.AgentReviewPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AgentStudyLoopServiceTest {

    @Mock private StudentAiQuizService quizService;
    @Mock private TextService textService;
    @Mock private AgentReviewPlanService reviewPlanService;
    @InjectMocks private AgentStudyLoopService service;

    @Test
    void derivesDueReviewAndConcreteMistakeFromOwnedLatestAttempt() {
        StudentAiQuizAttempt latest = attempt(101L, 20L, 2, 5, LocalDateTime.now().minusDays(2));
        StudentAiQuizAttempt old = attempt(100L, 20L, 5, 5, LocalDateTime.now().minusDays(5));
        when(quizService.listRecentMyAttempts(7L, 200)).thenReturn(List.of(latest, old));
        LearnerReviewTask review = new LearnerReviewTask();
        review.setTextId(20L);
        review.setDueAt(LocalDateTime.now().minusDays(1));
        review.setLastCorrect(2);
        review.setLastTotal(5);
        when(reviewPlanService.countDue(eq(7L), any(LocalDateTime.class))).thenReturn(1L);
        when(reviewPlanService.listDue(eq(7L), any(LocalDateTime.class), eq(5)))
                .thenReturn(List.of(review));
        Text text = new Text();
        text.setTitle("An article about agriculture");
        when(textService.getTextById(20L)).thenReturn(text);
        StudentAiQuizRecord wrong = new StudentAiQuizRecord();
        wrong.setIsCorrect(false);
        wrong.setQuestionSnapshot("Which idea is supported by the article?");
        wrong.setUserAnswer("B");
        wrong.setCorrectAnswer("A");
        wrong.setAnalysisSnapshot("The second paragraph explains it.");
        when(quizService.getMyAttemptDetail(7L, 101L))
                .thenReturn(Map.of("records", List.of(wrong)));

        AgentStudyLoopDto result = service.forUser(7L);

        assertEquals(2, result.getAttemptCount());
        assertEquals(1, result.getPracticedArticleCount());
        assertEquals(7, result.getCorrectQuestionCount());
        assertEquals(1, result.getDueReviews().size());
        assertEquals(20L, result.getDueReviews().get(0).getTextId());
        assertEquals(1, result.getRecentWrongQuestions().size());
        assertEquals("A", result.getRecentWrongQuestions().get(0).getCorrectAnswer());
    }

    @Test
    void returnsEmptyEvidenceWithoutPretendingThereAreAchievements() {
        when(reviewPlanService.listDue(eq(7L), any(LocalDateTime.class), eq(5)))
                .thenReturn(List.of());
        when(quizService.listRecentMyAttempts(7L, 200)).thenReturn(List.of());
        AgentStudyLoopDto result = service.forUser(7L);
        assertEquals(0, result.getAttemptCount());
        assertTrue(result.getDueReviews().isEmpty());
        assertTrue(result.getRecentWrongQuestions().isEmpty());
    }

    @Test
    void marksStatisticsAsRecentOnlyWhenAttemptWindowIsFull() {
        when(reviewPlanService.listDue(eq(7L), any(LocalDateTime.class), eq(5)))
                .thenReturn(List.of());
        LocalDateTime submitted = LocalDateTime.now();
        List<StudentAiQuizAttempt> attempts = IntStream.range(0, 200)
                .mapToObj(i -> attempt(200L - i, 20L, 4, 5, submitted))
                .toList();
        when(quizService.listRecentMyAttempts(7L, 200)).thenReturn(attempts);
        when(quizService.getMyAttemptDetail(7L, 200L))
                .thenReturn(Map.of("records", List.of()));

        AgentStudyLoopDto result = service.forUser(7L);

        assertEquals(200, result.getAttemptCount());
        assertTrue(result.isSampleLimited());
    }

    private StudentAiQuizAttempt attempt(Long id, Long textId, int correct, int total,
                                         LocalDateTime submitted) {
        StudentAiQuizAttempt attempt = new StudentAiQuizAttempt();
        attempt.setId(id);
        attempt.setTextId(textId);
        attempt.setCorrectCount(correct);
        attempt.setTotalQuestions(total);
        attempt.setSubmittedTime(submitted);
        return attempt;
    }
}
