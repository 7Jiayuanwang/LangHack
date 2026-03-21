package com.english.service;

import com.english.config.OpenRouterConfig;
import com.english.exception.BusinessException;
import com.english.service.impl.OpenRouterServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OpenRouterServiceImpl.
 *
 * Tests the OpenAI-compatible API flow, response parsing,
 * fallback behavior, and API key validation.
 */
@ExtendWith(MockitoExtension.class)
class OpenRouterServiceTest {

    @InjectMocks
    private OpenRouterServiceImpl openRouterService;

    @Mock
    private OpenRouterConfig config;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Inject mock RestTemplate (bypasses @PostConstruct init)
        ReflectionTestUtils.setField(openRouterService, "restTemplate", restTemplate);
    }

    // ==================== API Key Validation ====================

    @Test
    void generateCET4Version_noApiKey_throwsBusinessException() {
        when(config.getApiKey()).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("test content"));
        assertTrue(ex.getMessage().contains("API Key"));
    }

    @Test
    void generateCET4Version_blankApiKey_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("test content"));
        assertTrue(ex.getMessage().contains("API Key"));
    }

    // ==================== Success Cases ====================

    @Test
    void generateCET4Version_success_returnsContent() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://openrouter.ai/api/v1/chat/completions");
        when(config.getModel()).thenReturn("test-model");

        String response = "{\"choices\":[{\"message\":{\"content\":\"CET-4 rewritten text\"}}]}";
        when(restTemplate.exchange(
                eq("https://openrouter.ai/api/v1/chat/completions"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        String result = openRouterService.generateCET4Version("original content");
        assertEquals("CET-4 rewritten text", result);
    }

    @Test
    void generateCET6Version_success() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://api.test.com/v1");
        when(config.getModel()).thenReturn("model");

        String response = "{\"choices\":[{\"message\":{\"content\":\"CET-6 text\"}}]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        String result = openRouterService.generateCET6Version("original");
        assertEquals("CET-6 text", result);
    }

    @Test
    void generateAcademicVersion_success() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://api.test.com/v1");
        when(config.getModel()).thenReturn("model");

        String response = "{\"choices\":[{\"message\":{\"content\":\"Academic text\"}}]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        String result = openRouterService.generateAcademicVersion("original");
        assertEquals("Academic text", result);
    }

    @Test
    void generateChineseParallelVersion_success() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://api.test.com/v1");
        when(config.getModel()).thenReturn("model");

        String response = "{\"choices\":[{\"message\":{\"content\":\"Parallel text\"}}]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        String result = openRouterService.generateChineseParallelVersion("original");
        assertEquals("Parallel text", result);
    }

    // ==================== Error Cases ====================

    @Test
    void generateCET4Version_emptyChoices_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://api.test.com/v1");
        when(config.getModel()).thenReturn("model");

        String response = "{\"choices\":[]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("content"));
    }

    @Test
    void generateCET4Version_noContentField_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://api.test.com/v1");
        when(config.getModel()).thenReturn("model");

        String response = "{\"choices\":[{\"message\":{}}]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("content"));
    }

    @Test
    void generateCET4Version_nullResponseBody_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://api.test.com/v1");
        when(config.getModel()).thenReturn("model");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("content"));
    }

    @Test
    void generateCET4Version_non200Status_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://api.test.com/v1");
        when(config.getModel()).thenReturn("model");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("content"));
    }

    @Test
    void generateCET4Version_restTemplateThrows_throwsBusinessException() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://api.test.com/v1");
        when(config.getModel()).thenReturn("model");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("content"));
    }

    // ==================== Markdown versions ====================

    @Test
    void generateCET4VersionMarkdown_success() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://api.test.com/v1");
        when(config.getModel()).thenReturn("model");

        String response = "{\"choices\":[{\"message\":{\"content\":\"# CET-4 Markdown\"}}]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        String result = openRouterService.generateCET4VersionMarkdown("# Original");
        assertEquals("# CET-4 Markdown", result);
    }

    @Test
    void generateCET6VersionMarkdown_success() {
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getApiUrl()).thenReturn("https://api.test.com/v1");
        when(config.getModel()).thenReturn("model");

        String response = "{\"choices\":[{\"message\":{\"content\":\"# CET-6 Markdown\"}}]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        String result = openRouterService.generateCET6VersionMarkdown("# Original");
        assertEquals("# CET-6 Markdown", result);
    }

    // ==================== Fallback ====================

    @Test
    void fallback_businessException_rethrown() {
        BusinessException original = new BusinessException("业务异常");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.fallback("content", original));
        assertSame(original, ex);
    }

    @Test
    void fallback_callNotPermitted_throwsBusinessException() {
        io.github.resilience4j.circuitbreaker.CallNotPermittedException cnpe =
                mock(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.fallback("content", cnpe));
        assertTrue(ex.getMessage().contains("暂时不可用"));
    }

    @Test
    void fallback_genericException_wrapsInBusinessException() {
        RuntimeException original = new RuntimeException("timeout");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.fallback("content", original));
        assertTrue(ex.getMessage().contains("暂时不可用"));
    }

    // ==================== Destroy ====================

    @Test
    void destroy_nullClients_noException() {
        ReflectionTestUtils.setField(openRouterService, "httpClient", null);
        ReflectionTestUtils.setField(openRouterService, "connManager", null);

        assertDoesNotThrow(() -> openRouterService.destroy());
    }
}
