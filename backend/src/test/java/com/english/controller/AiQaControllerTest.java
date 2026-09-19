package com.english.controller;

import com.english.entity.AiConversation;
import com.english.entity.AiMessage;
import com.english.entity.User;
import com.english.mapper.AiCallLogMapper;
import com.english.service.ConversationService;
import com.english.service.HiAgentService;
import com.english.service.LearningAgentPromptContextService;
import com.english.service.UserService;
import com.english.service.impl.AiCallMetricsService;
import com.english.service.impl.AiProviderRuntimeService;
import com.english.service.impl.QaUpstreamSessionStore;
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
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.List;
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
    private LearningAgentPromptContextService learningAgentPromptContextService;

    @Mock
    private AiCallMetricsService aiCallMetricsService;

    @Mock
    private AiCallLogMapper aiCallLogMapper;

    @Mock
    private QaUpstreamSessionStore qaUpstreamSessionStore;

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
        verify(qaUpstreamSessionStore).put(7L, 42L, "upstream-conversation");
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
        when(learningAgentPromptContextService.buildForUser(7L)).thenReturn(
                "【平台提供的学习画像】\n- 当前英语水平：INTERMEDIATE\n- 兴趣：人工智能\n【学习画像结束】");

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

        assertNotNull(response.getBody());
        response.getBody().writeTo(new ByteArrayOutputStream());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("长期陪伴大学生的英语学习伙伴"));
        assertTrue(prompt.contains("不要编造用户的学习记录或成就"));
        assertTrue(prompt.contains("当前英语水平：INTERMEDIATE"));
        assertTrue(prompt.contains("兴趣：人工智能"));
        assertTrue(prompt.contains("只引用画像中确实存在的事实"));
        assertTrue(prompt.contains("Help me plan today's English study"));
        verify(learningAgentPromptContextService).buildForUser(7L);
        verify(conversationService).addMessage(43L, 7L, "assistant", "先完成十分钟阅读。 ", null, null);
        verify(aiCallMetricsService).recordSuccess("QA", "chatQA", 200L, "student1");
    }

    @Test
    void chatStream_reusesUpstreamConversationWithoutResendingHistory() throws Exception {
        when(qaUpstreamSessionStore.get(7L, 42L)).thenReturn("existing-upstream");
        when(hiAgentService.chatQueryStreamingWithQACredentials(
                eq("existing-upstream"), anyString(), eq(42L), any()))
                .thenReturn(ResponseEntity.ok(output -> {}));

        AiQaController.ChatRequest request = new AiQaController.ChatRequest();
        request.setUserQuestion("And what about tomorrow?");
        request.setScene("LEARNING_AGENT");
        request.setConversationId(42L);

        ResponseEntity<StreamingResponseBody> response = controller.chatStream(
                request, new UsernamePasswordAuthenticationToken("student1", "", java.util.Collections.emptyList()));
        response.getBody().writeTo(new ByteArrayOutputStream());

        verify(hiAgentService, never()).createConversationForQA();
        verify(conversationService, never()).getMessages(anyLong(), anyLong());
        verify(hiAgentService).chatQueryStreamingWithQACredentials(
                eq("existing-upstream"), contains("And what about tomorrow?"), eq(42L), any());
    }

    @Test
    void chatStream_fallsBackToConfiguredProviderWhenHiAgentHandshakeFails() throws Exception {
        AiConversation conversation = new AiConversation();
        conversation.setId(44L);
        when(conversationService.createConversation(7L, "Help me study"))
                .thenReturn(conversation);
        when(hiAgentService.createConversationForQA())
                .thenThrow(new RuntimeException("TLS handshake failed"));
        when(aiProviderRuntimeService.completeIfConfigured(eq("chat"), eq("chatQA"), anyString()))
                .thenReturn(Optional.of("Let's read for ten minutes."));

        AiQaController.ChatRequest request = new AiQaController.ChatRequest();
        request.setUserQuestion("Help me study");
        request.setScene("LEARNING_AGENT");
        ResponseEntity<StreamingResponseBody> response = controller.chatStream(
                request, new UsernamePasswordAuthenticationToken("student1", "", java.util.Collections.emptyList()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        String events = output.toString(StandardCharsets.UTF_8);
        assertTrue(events.contains("event: delta"));
        assertTrue(events.contains("Let's read for ten minutes."));
        assertTrue(events.contains("event: done"));
        verify(conversationService).addMessage(44L, 7L, "assistant",
                "Let's read for ten minutes.", null, null);
        verify(conversationService, never()).getRecentMessagesBefore(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void chatStream_sharesOnlyBoundedTextHistoryAfterStudentOptIn() throws Exception {
        AiMessage current = new AiMessage();
        current.setId(100L);
        when(conversationService.addMessage(42L, 7L, "user", "And tomorrow?", "", ""))
                .thenReturn(current);
        AiMessage priorUser = new AiMessage();
        priorUser.setRole("user");
        priorUser.setContent("How should I start?");
        priorUser.setSelectedText("PRIVATE-OLD-ESSAY");
        AiMessage priorAssistant = new AiMessage();
        priorAssistant.setRole("assistant");
        priorAssistant.setContent("Read for ten minutes today." + "x".repeat(800)
                + "SHOULD-BE-TRUNCATED");
        when(conversationService.getRecentMessagesBefore(42L, 7L, 100L, 8))
                .thenReturn(List.of(priorUser, priorAssistant));
        when(hiAgentService.createConversationForQA())
                .thenThrow(new RuntimeException("TLS handshake failed"));
        ArgumentCaptor<String> fallbackPrompt = ArgumentCaptor.forClass(String.class);
        when(aiProviderRuntimeService.completeIfConfigured(eq("chat"), eq("chatQA"),
                fallbackPrompt.capture())).thenReturn(Optional.of("Try a short article tomorrow."));

        AiQaController.ChatRequest request = new AiQaController.ChatRequest();
        request.setConversationId(42L);
        request.setUserQuestion("And tomorrow?");
        request.setScene("LEARNING_AGENT");
        request.setAllowFallbackHistory(true);
        ResponseEntity<StreamingResponseBody> response = controller.chatStream(
                request, new UsernamePasswordAuthenticationToken(
                        "student1", "", java.util.Collections.emptyList()));
        response.getBody().writeTo(new ByteArrayOutputStream());

        String sent = fallbackPrompt.getValue();
        assertTrue(sent.contains("How should I start?"));
        assertTrue(sent.contains("Read for ten minutes today."));
        assertTrue(sent.contains("And tomorrow?"));
        assertTrue(!sent.contains("PRIVATE-OLD-ESSAY"));
        assertTrue(!sent.contains("SHOULD-BE-TRUNCATED"));
        verify(conversationService).getRecentMessagesBefore(42L, 7L, 100L, 8);
    }
}
