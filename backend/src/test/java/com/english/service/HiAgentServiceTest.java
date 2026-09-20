package com.english.service;

import com.english.config.HiAgentConfig;
import com.english.exception.BusinessException;
import com.english.service.impl.HiAgentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HiAgentServiceImpl.
 *
 * Tests the two-step HiAgent API flow (CreateConversation → ChatQueryV2),
 * fallback behavior, API key validation, and response parsing.
 */
@ExtendWith(MockitoExtension.class)
class HiAgentServiceTest {

    @InjectMocks
    private HiAgentServiceImpl hiAgentService;

    @Mock
    private HiAgentConfig config;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Inject mock RestTemplate (bypasses @PostConstruct init)
        ReflectionTestUtils.setField(hiAgentService, "restTemplate", restTemplate);
    }

    // ==================== API Key Validation ====================

    @Test
    void generateCET4Version_noApiKey_throwsBusinessException() {
        when(config.getApiKey()).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hiAgentService.generateCET4Version("test content"));
        assertTrue(ex.getMessage().contains("API Key"));
    }

    @Test
    void generateCET4Version_blankApiKey_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("   ");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hiAgentService.generateCET4Version("test content"));
        assertTrue(ex.getMessage().contains("API Key"));
    }

    // ==================== Full Flow Success ====================

    @Test
    void generateCET4Version_success_returnsChatAnswer() {
        // Arrange
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getAppId()).thenReturn("test-app");
        when(config.getUserId()).thenReturn("test-user");
        when(config.getMaxRetries()).thenReturn(1);

        // Step 1: createConversation returns valid response
        String createResponse = "{\"Conversation\":{\"AppConversationID\":\"conv-123\"}}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/create_conversation"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(createResponse, HttpStatus.OK));

        // Step 2: chatQuery returns answer
        String chatResponse = "{\"answer\":\"Rewritten CET-4 content\"}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/chat_query_v2"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(chatResponse, HttpStatus.OK));

        // Act
        String result = hiAgentService.generateCET4Version("original content");

        // Assert
        assertEquals("Rewritten CET-4 content", result);
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    void generateCET6Version_success_answerCapitalKey() {
        // Arrange - test "Answer" (capital A) response key
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getAppId()).thenReturn("test-app");
        when(config.getUserId()).thenReturn("test-user");
        when(config.getMaxRetries()).thenReturn(1);

        String createResponse = "{\"Conversation\":{\"AppConversationID\":\"conv-456\"}}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/create_conversation"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(createResponse, HttpStatus.OK));

        String chatResponse = "{\"Answer\":\"CET-6 rewritten text\"}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/chat_query_v2"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(chatResponse, HttpStatus.OK));

        // Act
        String result = hiAgentService.generateCET6Version("original content");

        // Assert
        assertEquals("CET-6 rewritten text", result);
    }

    // ==================== CreateConversation Failures ====================

    @Test
    void generateAcademicVersion_createConversationFails_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getAppId()).thenReturn("test-app");
        when(config.getUserId()).thenReturn("test-user");
        when(config.getMaxRetries()).thenReturn(1);

        // createConversation returns non-OK status
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/create_conversation"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(BusinessException.class,
                () -> hiAgentService.generateAcademicVersion("test content"));
    }

    @Test
    void generateAcademicVersion_missingConversationId_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getAppId()).thenReturn("test-app");
        when(config.getUserId()).thenReturn("test-user");
        when(config.getMaxRetries()).thenReturn(1);

        // Response missing Conversation.AppConversationID
        String badResponse = "{\"Conversation\":{}}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/create_conversation"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(badResponse, HttpStatus.OK));

        assertThrows(BusinessException.class,
                () -> hiAgentService.generateAcademicVersion("test content"));
    }

    @Test
    void generateAcademicVersion_nullConversationId_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getAppId()).thenReturn("test-app");
        when(config.getUserId()).thenReturn("test-user");
        when(config.getMaxRetries()).thenReturn(1);

        // AppConversationID is "null" string
        String badResponse = "{\"Conversation\":{\"AppConversationID\":null}}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/create_conversation"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(badResponse, HttpStatus.OK));

        assertThrows(BusinessException.class,
                () -> hiAgentService.generateAcademicVersion("test content"));
    }

    // ==================== ChatQuery Failures ====================

    @Test
    void generateChineseParallelVersion_chatQueryNoAnswer_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getAppId()).thenReturn("test-app");
        when(config.getUserId()).thenReturn("test-user");
        when(config.getMaxRetries()).thenReturn(1);

        // Step 1 succeeds
        String createResponse = "{\"Conversation\":{\"AppConversationID\":\"conv-789\"}}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/create_conversation"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(createResponse, HttpStatus.OK));

        // Step 2: response has neither "answer" nor "Answer"
        String chatResponse = "{\"status\":\"ok\",\"data\":{}}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/chat_query_v2"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(chatResponse, HttpStatus.OK));

        assertThrows(BusinessException.class,
                () -> hiAgentService.generateChineseParallelVersion("test content"));
    }

    @Test
    void generateChineseParallelVersion_chatQueryFailsHttp_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getAppId()).thenReturn("test-app");
        when(config.getUserId()).thenReturn("test-user");
        when(config.getMaxRetries()).thenReturn(1);

        // Step 1 succeeds
        String createResponse = "{\"Conversation\":{\"AppConversationID\":\"conv-789\"}}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/create_conversation"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(createResponse, HttpStatus.OK));

        // Step 2 returns 500
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/chat_query_v2"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(BusinessException.class,
                () -> hiAgentService.generateChineseParallelVersion("test content"));
    }

    // ==================== Fallback ====================

    @Test
    void fallback_businessException_rethrown() {
        BusinessException original = new BusinessException("业务异常");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hiAgentService.fallback("content", original));
        assertSame(original, ex);
    }

    @Test
    void fallback_callNotPermitted_throwsBusinessException() {
        io.github.resilience4j.circuitbreaker.CallNotPermittedException cnpe =
                mock(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hiAgentService.fallback("content", cnpe));
        assertTrue(ex.getMessage().contains("暂时不可用"));
    }

    @Test
    void fallback_genericException_wrapsInBusinessException() {
        RuntimeException original = new RuntimeException("connection timeout");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hiAgentService.fallback("content", original));
        assertTrue(ex.getMessage().contains("暂时不可用"));
    }

    // ==================== Markdown versions ====================

    @Test
    void generateCET4VersionMarkdown_success() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getAppId()).thenReturn("test-app");
        when(config.getUserId()).thenReturn("test-user");
        when(config.getMaxRetries()).thenReturn(1);

        String createResponse = "{\"Conversation\":{\"AppConversationID\":\"conv-md\"}}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/create_conversation"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(createResponse, HttpStatus.OK));

        String chatResponse = "{\"answer\":\"# CET-4 Markdown\"}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/chat_query_v2"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(chatResponse, HttpStatus.OK));

        String result = hiAgentService.generateCET4VersionMarkdown("# Original");
        assertEquals("# CET-4 Markdown", result);
    }

    // ==================== Streaming Q&A ====================

    @Test
    void chatQueryStreaming_executesUpstreamRequestWhileWritingResponse() throws Exception {
        when(config.getQaApiKey()).thenReturn("qa-key");
        when(config.getQaAppId()).thenReturn("qa-app");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getUserId()).thenReturn("test-user");

        ClientHttpRequest upstreamRequest = mock(ClientHttpRequest.class);
        ByteArrayOutputStream upstreamRequestBody = new ByteArrayOutputStream();
        org.springframework.http.HttpHeaders upstreamRequestHeaders =
                new org.springframework.http.HttpHeaders();
        when(upstreamRequest.getHeaders()).thenReturn(upstreamRequestHeaders);
        when(upstreamRequest.getBody()).thenReturn(upstreamRequestBody);

        ClientHttpResponse upstreamResponse = mock(ClientHttpResponse.class);
        when(upstreamResponse.getStatusCode()).thenReturn(HttpStatus.OK);
        when(upstreamResponse.getBody()).thenReturn(new ByteArrayInputStream(
                ("data: {\"event\":\"message\",\"answer\":\"hello\"}\n\n"
                        + "data: {\"event\":\"message_end\"}\n\n")
                        .getBytes(StandardCharsets.UTF_8)));

        when(restTemplate.execute(
                eq(URI.create("https://test.api.com/v1/chat_query_v2")),
                eq(HttpMethod.POST),
                any(RequestCallback.class),
                any(ResponseExtractor.class)))
                .thenAnswer(invocation -> {
                    RequestCallback requestCallback = invocation.getArgument(2);
                    ResponseExtractor<?> responseExtractor = invocation.getArgument(3);
                    requestCallback.doWithRequest(upstreamRequest);
                    return responseExtractor.extractData(upstreamResponse);
                });

        AtomicReference<String> completedAnswer = new AtomicReference<>();
        ResponseEntity<StreamingResponseBody> response =
                hiAgentService.chatQueryStreamingWithQACredentials(
                        "conv-stream", "prompt", 42L, completedAnswer::set);

        verify(restTemplate, never()).execute(
                any(URI.class), any(HttpMethod.class),
                any(RequestCallback.class), any(ResponseExtractor.class));
        assertEquals(MediaType.TEXT_EVENT_STREAM, response.getHeaders().getContentType());
        assertEquals("no", response.getHeaders().getFirst("X-Accel-Buffering"));

        ByteArrayOutputStream browserResponse = new ByteArrayOutputStream();
        assertNotNull(response.getBody());
        response.getBody().writeTo(browserResponse);

        assertEquals("event: delta\ndata: {\"content\":\"hello\"}\n\n"
                        + "event: done\ndata: {\"conversationId\":42}\n\n",
                browserResponse.toString(StandardCharsets.UTF_8));
        assertEquals("hello", completedAnswer.get());
        assertEquals("qa-key", upstreamRequestHeaders.getFirst("Apikey"));
        assertEquals(MediaType.TEXT_EVENT_STREAM,
                upstreamRequestHeaders.getAccept().get(0));
        assertTrue(upstreamRequestBody.toString(StandardCharsets.UTF_8)
                .contains("\"ResponseMode\":\"streaming\""));
        verify(restTemplate).execute(
                eq(URI.create("https://test.api.com/v1/chat_query_v2")),
                eq(HttpMethod.POST),
                any(RequestCallback.class),
                any(ResponseExtractor.class));
    }

    @Test
    void chatQueryStreaming_doesNotForwardThinkingEvents() throws Exception {
        when(config.getQaApiKey()).thenReturn("qa-key");
        when(config.getQaAppId()).thenReturn("qa-app");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getUserId()).thenReturn("test-user");

        ClientHttpRequest upstreamRequest = mock(ClientHttpRequest.class);
        when(upstreamRequest.getHeaders()).thenReturn(new org.springframework.http.HttpHeaders());
        when(upstreamRequest.getBody()).thenReturn(new ByteArrayOutputStream());
        ClientHttpResponse upstreamResponse = mock(ClientHttpResponse.class);
        when(upstreamResponse.getStatusCode()).thenReturn(HttpStatus.OK);
        when(upstreamResponse.getBody()).thenReturn(new ByteArrayInputStream(
                ("event: thought\ndata: {\"content\":\"internal plan\"}\n\n"
                        + "event: message\ndata: {\"answer\":\"final answer\"}\n\n"
                        + "event: done\ndata: {}\n\n").getBytes(StandardCharsets.UTF_8)));

        when(restTemplate.execute(any(URI.class), eq(HttpMethod.POST),
                any(RequestCallback.class), any(ResponseExtractor.class)))
                .thenAnswer(invocation -> {
                    RequestCallback callback = invocation.getArgument(2);
                    ResponseExtractor<?> extractor = invocation.getArgument(3);
                    callback.doWithRequest(upstreamRequest);
                    return extractor.extractData(upstreamResponse);
                });

        ResponseEntity<StreamingResponseBody> response = hiAgentService
                .chatQueryStreamingWithQACredentials("conv", "prompt", null, ignored -> { });
        ByteArrayOutputStream browserResponse = new ByteArrayOutputStream();
        response.getBody().writeTo(browserResponse);
        String output = browserResponse.toString(StandardCharsets.UTF_8);

        assertFalse(output.contains("internal plan"));
        assertTrue(output.contains("final answer"));
    }

    // ==================== Destroy ====================

    @Test
    void destroy_nullClients_noException() {
        // When httpClient and connManager are null (not initialized), destroy should not throw
        ReflectionTestUtils.setField(hiAgentService, "httpClient", null);
        ReflectionTestUtils.setField(hiAgentService, "connManager", null);

        assertDoesNotThrow(() -> hiAgentService.destroy());
    }

    // ==================== chatQA — Credential Isolation ====================
    // 业务契约：AI 问答必须使用独立 QA 凭证（与改写凭证完全不同），不 fallback。

    @Test
    void chatQA_qaApiKeyNull_throwsBusinessExceptionWithoutRetry() {
        when(config.getQaApiKey()).thenReturn(null);
        lenient().when(config.getQaAppId()).thenReturn("qa-app");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hiAgentService.chatQA("什么意思?", "Hello world", "context"));
        assertTrue(ex.getMessage().contains("HIAGENT_QA_API_KEY"),
                "Expected error to name the missing env var, got: " + ex.getMessage());
        // Fast-fail：pre-check 必须在任何 HTTP 调用之前拒绝，且不触发 maxRetries 重试
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    void chatQA_qaApiKeyBlank_throwsBusinessExceptionWithoutRetry() {
        when(config.getQaApiKey()).thenReturn("   ");
        lenient().when(config.getQaAppId()).thenReturn("qa-app");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hiAgentService.chatQA("问题", "text", null));
        assertTrue(ex.getMessage().contains("独立凭证"));
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    void chatQA_qaAppIdMissing_throwsBusinessExceptionWithoutRetry() {
        when(config.getQaApiKey()).thenReturn("qa-key");
        when(config.getQaAppId()).thenReturn("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hiAgentService.chatQA("问题", "text", null));
        assertTrue(ex.getMessage().contains("HIAGENT_QA_APP_ID"));
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    void chatQA_distinctQaCredentials_successUsesQaHeaders() {
        // Arrange — 改写凭证 vs 问答凭证完全不同
        when(config.getQaApiKey()).thenReturn("qa-key-distinct");
        when(config.getQaAppId()).thenReturn("qa-app-distinct");
        when(config.getApiUrl()).thenReturn("https://test.api.com/v1");
        when(config.getUserId()).thenReturn("test-user");
        when(config.getMaxRetries()).thenReturn(1);

        String createResponse = "{\"Conversation\":{\"AppConversationID\":\"qa-conv-1\"}}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/create_conversation"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(createResponse, HttpStatus.OK));

        String chatResponse = "{\"answer\":\"这是 AI 问答的回答\"}";
        when(restTemplate.exchange(
                eq("https://test.api.com/v1/chat_query_v2"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(chatResponse, HttpStatus.OK));

        // Act
        String answer = hiAgentService.chatQA("这段话什么意思?",
                "Artificial intelligence", "AI 是一门计算机科学分支...");

        // Assert
        assertEquals("这是 AI 问答的回答", answer);

        // 关键验证：QA 请求必须携带 QA 专用 Apikey/Appid，而非改写凭证
        org.mockito.ArgumentCaptor<HttpEntity<?>> captor =
                org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST),
                captor.capture(), eq(String.class));
        for (HttpEntity<?> entity : captor.getAllValues()) {
            assertEquals("qa-key-distinct", entity.getHeaders().getFirst("Apikey"),
                    "chatQA 必须使用 QA 专用 Apikey，禁止 fallback 到改写 Api-Key");
            assertEquals("qa-app-distinct", entity.getHeaders().getFirst("Appid"),
                    "chatQA 必须使用 QA 专用 Appid，禁止 fallback 到改写 App-ID");
        }

        // 不应读取改写凭证 — 保证两套应用隔离
        verify(config, never()).getApiKey();
        verify(config, never()).getAppId();
    }
}
