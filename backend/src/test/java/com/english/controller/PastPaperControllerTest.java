package com.english.controller;

import com.english.dto.Result;
import com.english.entity.PastPaper;
import com.english.entity.PastPaperAttempt;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.PastPaperAttemptMapper;
import com.english.mapper.PastPaperMapper;
import com.english.service.HiAgentService;
import com.english.service.MediaService;
import com.english.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PastPaperControllerTest {

    @Mock private PastPaperMapper pastPaperMapper;
    @Mock private PastPaperAttemptMapper pastPaperAttemptMapper;
    @Mock private UserService userService;
    @Mock private MediaService mediaService;
    @Mock private HiAgentService hiAgentService;

    private PastPaperController controller;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("student1", "");

    @BeforeEach
    void setUp() {
        controller = new PastPaperController(
                pastPaperMapper, pastPaperAttemptMapper, userService, objectMapper, mediaService, hiAgentService);

        User student = new User();
        student.setId(7L);
        student.setUsername("student1");
        when(userService.getUserByUsername("student1")).thenReturn(student);
        when(pastPaperMapper.findById(12L)).thenReturn(buildPaper());
        when(pastPaperAttemptMapper.insert(any(PastPaperAttempt.class))).thenReturn(1);
    }

    @Test
    void submitAttempt_combinesObjectiveAndAiScores() {
        when(hiAgentService.gradePastPaper(any())).thenReturn("""
                {
                  "questions": [{
                    "questionId": "q2",
                    "score": 80,
                    "summary": "内容完整",
                    "issues": [],
                    "suggestions": ["增加连接词"],
                    "revisedAnswer": "Improved answer"
                  }],
                  "overallFeedback": "继续保持"
                }
                """);

        Result<Map<String, Object>> result = controller.submitAttempt(buildPayload(), authentication);

        assertEquals(200, result.getCode());
        assertEquals(new BigDecimal("80.00"), result.getData().get("aiScorePercentage"));
        assertEquals(new BigDecimal("90.00"), result.getData().get("finalScorePercentage"));
        assertEquals("COMPLETED", result.getData().get("gradingStatus"));

        ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
        verify(hiAgentService).gradePastPaper(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().contains("Reference"));
        assertFalse(requestCaptor.getValue().contains("Rubric"));

        ArgumentCaptor<PastPaperAttempt> captor = ArgumentCaptor.forClass(PastPaperAttempt.class);
        verify(pastPaperAttemptMapper).insert(captor.capture());
        PastPaperAttempt saved = captor.getValue();
        assertEquals(new BigDecimal("90.00"), saved.getScorePercentage());
        assertEquals(new BigDecimal("90.00"), saved.getFinalScorePercentage());
        assertTrue(saved.getGradingJson().contains("Improved answer"));
    }

    @Test
    void submitAttempt_keepsObjectiveScoreWhenAiIsUnavailable() {
        when(hiAgentService.gradePastPaper(any())).thenThrow(new BusinessException("grading unavailable"));

        Result<Map<String, Object>> result = controller.submitAttempt(buildPayload(), authentication);

        assertEquals(200, result.getCode());
        assertEquals("AI_UNAVAILABLE", result.getData().get("gradingStatus"));
        assertNull(result.getData().get("aiScorePercentage"));
        assertNull(result.getData().get("finalScorePercentage"));

        ArgumentCaptor<PastPaperAttempt> captor = ArgumentCaptor.forClass(PastPaperAttempt.class);
        verify(pastPaperAttemptMapper).insert(captor.capture());
        PastPaperAttempt saved = captor.getValue();
        assertEquals(new BigDecimal("100.00"), saved.getScorePercentage());
        assertNull(saved.getFinalScorePercentage());
        assertEquals("AI_UNAVAILABLE", saved.getGradingStatus());
    }

    private PastPaper buildPaper() {
        PastPaper paper = new PastPaper();
        paper.setId(12L);
        paper.setPaperKey("paper-12");
        paper.setCategory("cet4");
        paper.setTitle("CET-4 Practice");
        paper.setPublished(true);
        paper.setContentJson("""
                {
                  "questions": [
                    {"id":"q1","type":"single_choice","stem":"Choose","answer":1},
                    {"id":"q2","type":"writing","stem":"Write","referenceAnswer":"Reference","analysis":"Rubric"}
                  ]
                }
                """);
        return paper;
    }

    private Map<String, Object> buildPayload() {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("q1", 1);
        answers.put("q2", "My answer");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pastPaperId", 12);
        payload.put("answers", answers);
        return payload;
    }
}
