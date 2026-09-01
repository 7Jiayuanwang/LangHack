package com.english.controller;

import com.english.entity.AiConversation;
import com.english.entity.User;
import com.english.mapper.AiCallLogMapper;
import com.english.service.ConversationService;
import com.english.service.HiAgentService;
import com.english.service.UserService;
import com.english.service.impl.AiCallMetricsService;
import com.english.service.impl.AiProviderRuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiQaControllerTest {

    @InjectMocks
    private AiQaController controller;

    @Mock
    private HiAgentService hiAgentService;

    @Mock
    private AiProviderRuntimeService aiProviderRuntimeService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private UserService userService;

    @Mock
    private AiCallMetricsService aiCallMetricsService;

    @Mock
    private AiCallLogMapper aiCallLogMapper;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setUsername("student1");
        when(userService.getUserByUsername("student1")).thenReturn(user);
    }

    @Test
    void chatStream_persistsNewConversationWhenStreamCompletes() throws Exception {
        AiConversation conversation = new AiConversation();
        conversation.setId(42L);
        when(conversationService.createConversation(7L, "Explain this sentence"))
                .thenReturn(conversation);
        when(hiAgentService.createConversationForQA()).thenReturn("upstream-conversation");
        when(aiCallMetricsService.startTimer()).thenReturn(100L);

        ArgumentCaptor<Consumer<String>> completionCaptor = ArgumentCaptor.forClass(Consumer.class);
        StreamingResponseBody responseBody = outputStream ->
                completionCaptor.getValue().accept("完整回答");
        when(hiAgentService.chatQueryStreamingWithQACredentials(
                eq("upstream-conversation"), anyString(), eq(42L), completionCaptor.capture()))
                .thenReturn(ResponseEntity.ok(responseBody));

        AiQaController.ChatRequest request = new AiQaController.ChatRequest();
        request.setUserQuestion("Explain this sentence");
        request.setSelectedText("There are two problems with this, though.");

        ResponseEntity<StreamingResponseBody> response = controller.chatStream(
                request,
                new UsernamePasswordAuthenticationToken("student1", "", java.util.Collections.emptyList()));

        verify(conversationService).addMessage(
                42L, 7L, "user", "Explain this sentence",
                "There are two problems with this, though.", "");
        assertNotNull(response.getBody());
        response.getBody().writeTo(new ByteArrayOutputStream());
        verify(conversationService).addMessage(42L, 7L, "assistant", "完整回答", null, null);
        verify(aiCallMetricsService).recordSuccess("QA", "chatQA", 100L, "student1");
    }

    @Test
    void chatStream_learningAgentSceneUsesCompanionPrompt() throws Exception {
        AiConversation conversation = new AiConversation();
        conversation.setId(43L);
        when(conversationService.createConversation(7L, "Help me plan today's English study"))
                .thenReturn(conversation);
        when(hiAgentService.createConversationForQA()).thenReturn("agent-upstream-conversation");
        when(aiCallMetricsService.startTimer()).thenReturn(200L);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Consumer<String>> completionCaptor = ArgumentCaptor.forClass(Consumer.class);
        StreamingResponseBody responseBody = outputStream ->
                completionCaptor.getValue().accept("先完成十分钟阅读。 ");
        when(hiAgentService.chatQueryStreamingWithQACredentials(
                eq("agent-upstream-conversation"), promptCaptor.capture(),
                eq(43L), completionCaptor.capture()))
                .thenReturn(ResponseEntity.ok(responseBody));

        AiQaController.ChatRequest request = new AiQaController.ChatRequest();
        request.setUserQuestion("Help me plan today's English study");
        request.setScene("LEARNING_AGENT");

        ResponseEntity<StreamingResponseBody> response = controller.chatStream(
                request,
                new UsernamePasswordAuthenticationToken("student1", "", java.util.Collections.emptyList()));

        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("长期陪伴大学生的英语学习伙伴"));
        assertTrue(prompt.contains("不要编造用户的学习记录或成就"));
        assertTrue(prompt.contains("Help me plan today's English study"));
        assertNotNull(response.getBody());
        response.getBody().writeTo(new ByteArrayOutputStream());
        verify(conversationService).addMessage(43L, 7L, "assistant", "先完成十分钟阅读。 ", null, null);
        verify(aiCallMetricsService).recordSuccess("QA", "chatQA", 200L, "student1");
    }
}
