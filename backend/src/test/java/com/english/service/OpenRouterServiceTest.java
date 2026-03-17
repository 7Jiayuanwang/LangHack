package com.english.service;

import com.english.config.OpenRouterConfig;
import com.english.exception.BusinessException;
import com.english.service.impl.OpenRouterServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OpenRouterServiceImpl.
 *
 * Key observations from source code:
 * - OpenRouterServiceImpl injects OpenRouterConfig for API key, URL, model, timeout, maxRetries
 * - Uses a RestTemplate initialized in @PostConstruct init()
 * - All generate* methods delegate to private callDeepSeek(prompt)
 * - callDeepSeek() validates API key, does retry with exponential backoff
 * - validateInput() checks null/blank and length > 100000
 * - sanitizeErrorMessage() strips sensitive info from error messages
 * - Circuit breaker fallback methods throw BusinessException
 *
 * Testing strategy:
 * - Inject a mock RestTemplate via ReflectionTestUtils (since it's initialized in @PostConstruct)
 * - Mock OpenRouterConfig for API key, URL, model, timeout, maxRetries
 * - Test all 8 public generate methods (4 plain + 4 markdown)
 * - Test input validation (null, blank, too long)
 * - Test API key not configured
 * - Test response parsing (valid JSON, missing choices, null content)
 * - Test retry logic
 */
@ExtendWith(MockitoExtension.class)
class OpenRouterServiceTest {

    @InjectMocks
    private OpenRouterServiceImpl openRouterService;

    @Mock
    private OpenRouterConfig config;

    @Mock
    private RestTemplate restTemplate;

    private static final String VALID_API_KEY = "sk-test-key-123";
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "deepseek/deepseek-r1";

    @BeforeEach
    void setUp() {
        // Inject the mock RestTemplate into the service (bypassing @PostConstruct init())
        ReflectionTestUtils.setField(openRouterService, "restTemplate", restTemplate);
        // Also inject a real ObjectMapper since the field is final and initialized inline
        ReflectionTestUtils.setField(openRouterService, "objectMapper", new ObjectMapper());
    }

    // ==================== generateCET4Version tests ====================

    @Test
    void generateCET4Version_success_returnsGeneratedContent() {
        // Arrange
        setupConfigForSuccess();
        mockSuccessfulApiResponse("Simplified CET-4 content");

        // Act
        String result = openRouterService.generateCET4Version("Some original article content");

        // Assert
        assertEquals("Simplified CET-4 content", result);
        verify(restTemplate).exchange(eq(API_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void generateCET4Version_nullInput_throwsBusinessException() {
        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version(null));
        assertEquals("原文内容不能为空", ex.getMessage());
    }

    @Test
    void generateCET4Version_blankInput_throwsBusinessException() {
        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("   "));
        assertEquals("原文内容不能为空", ex.getMessage());
    }

    @Test
    void generateCET4Version_tooLongInput_throwsBusinessException() {
        // Arrange
        String longContent = "a".repeat(100001);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version(longContent));
        assertEquals("原文内容过长，最多10万字符", ex.getMessage());
    }

    // ==================== generateCET6Version tests ====================

    @Test
    void generateCET6Version_success_returnsGeneratedContent() {
        // Arrange
        setupConfigForSuccess();
        mockSuccessfulApiResponse("CET-6 level content");

        // Act
        String result = openRouterService.generateCET6Version("Original text");

        // Assert
        assertEquals("CET-6 level content", result);
    }

    @Test
    void generateCET6Version_nullInput_throwsBusinessException() {
        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET6Version(null));
        assertEquals("原文内容不能为空", ex.getMessage());
    }

    // ==================== generateAcademicVersion tests ====================

    @Test
    void generateAcademicVersion_success_returnsGeneratedContent() {
        // Arrange
        setupConfigForSuccess();
        mockSuccessfulApiResponse("Academic style content");

        // Act
        String result = openRouterService.generateAcademicVersion("Original text");

        // Assert
        assertEquals("Academic style content", result);
    }

    @Test
    void generateAcademicVersion_nullInput_throwsBusinessException() {
        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateAcademicVersion(null));
        assertEquals("原文内容不能为空", ex.getMessage());
    }

    // ==================== generateChineseParallelVersion tests ====================

    @Test
    void generateChineseParallelVersion_success_returnsGeneratedContent() {
        // Arrange
        setupConfigForSuccess();
        mockSuccessfulApiResponse("Parallel text content");

        // Act
        String result = openRouterService.generateChineseParallelVersion("Original text");

        // Assert
        assertEquals("Parallel text content", result);
    }

    // ==================== Markdown versions tests ====================

    @Test
    void generateCET4VersionMarkdown_success_returnsGeneratedContent() {
        // Arrange
        setupConfigForSuccess();
        mockSuccessfulApiResponse("# Simplified CET-4 Markdown");

        // Act
        String result = openRouterService.generateCET4VersionMarkdown("# Original Markdown");

        // Assert
        assertEquals("# Simplified CET-4 Markdown", result);
    }

    @Test
    void generateCET4VersionMarkdown_nullInput_throwsBusinessException() {
        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4VersionMarkdown(null));
        assertEquals("Markdown内容不能为空", ex.getMessage());
    }

    @Test
    void generateCET6VersionMarkdown_success_returnsGeneratedContent() {
        // Arrange
        setupConfigForSuccess();
        mockSuccessfulApiResponse("# CET-6 Markdown");

        // Act
        String result = openRouterService.generateCET6VersionMarkdown("# Original Markdown");

        // Assert
        assertEquals("# CET-6 Markdown", result);
    }

    @Test
    void generateAcademicVersionMarkdown_success_returnsGeneratedContent() {
        // Arrange
        setupConfigForSuccess();
        mockSuccessfulApiResponse("# Academic Markdown");

        // Act
        String result = openRouterService.generateAcademicVersionMarkdown("# Original Markdown");

        // Assert
        assertEquals("# Academic Markdown", result);
    }

    @Test
    void generateChineseParallelVersionMarkdown_success_returnsGeneratedContent() {
        // Arrange
        setupConfigForSuccess();
        // Use content without newlines to avoid JSON parsing issues in mock
        mockSuccessfulApiResponse("Parallel Markdown with Chinese translation");

        // Act
        String result = openRouterService.generateChineseParallelVersionMarkdown("# Original Markdown");

        // Assert
        assertEquals("Parallel Markdown with Chinese translation", result);
    }

    // ==================== API key validation tests ====================

    @Test
    void generateCET4Version_apiKeyNull_throwsBusinessException() {
        // Arrange
        when(config.getApiKey()).thenReturn(null);
        when(config.getMaxRetries()).thenReturn(1);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("Some content"));
        assertEquals("AI 服务未配置，请联系管理员", ex.getMessage());
    }

    @Test
    void generateCET4Version_apiKeyBlank_throwsBusinessException() {
        // Arrange
        when(config.getApiKey()).thenReturn("   ");
        when(config.getMaxRetries()).thenReturn(1);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("Some content"));
        assertEquals("AI 服务未配置，请联系管理员", ex.getMessage());
    }

    // ==================== Response parsing tests ====================

    @Test
    void generateCET4Version_noChoicesInResponse_throwsBusinessException() {
        // Arrange
        setupConfigForSuccess();
        String responseBody = "{\"id\":\"test\",\"choices\":[]}";
        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("Some content"));
        assertEquals("AI 服务暂时不可用，请稍后重试", ex.getMessage());
    }

    @Test
    void generateCET4Version_nullContentInResponse_throwsBusinessException() {
        // Arrange
        setupConfigForSuccess();
        String responseBody = "{\"choices\":[{\"message\":{\"content\":null}}]}";
        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("Some content"));
        assertEquals("AI 服务暂时不可用，请稍后重试", ex.getMessage());
    }

    @Test
    void generateCET4Version_nonOkStatus_throwsBusinessException() {
        // Arrange
        setupConfigForSuccess();
        ResponseEntity<String> response = new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("Some content"));
        assertEquals("AI 服务暂时不可用，请稍后重试", ex.getMessage());
    }

    @Test
    void generateCET4Version_missingMessageNode_throwsBusinessException() {
        // Arrange
        setupConfigForSuccess();
        String responseBody = "{\"choices\":[{\"index\":0}]}";
        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("Some content"));
        assertEquals("AI 服务暂时不可用，请稍后重试", ex.getMessage());
    }

    // ==================== Retry logic tests ====================

    @Test
    void generateCET4Version_retriesOnFailure_eventuallySucceeds() {
        // Arrange: maxRetries=2, first call throws, second succeeds
        when(config.getApiKey()).thenReturn(VALID_API_KEY);
        when(config.getApiUrl()).thenReturn(API_URL);
        when(config.getModel()).thenReturn(MODEL);
        when(config.getMaxRetries()).thenReturn(2);

        String successBody = "{\"choices\":[{\"message\":{\"content\":\"Success after retry\"}}]}";
        ResponseEntity<String> successResponse = new ResponseEntity<>(successBody, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenReturn(successResponse);

        // Act
        String result = openRouterService.generateCET4Version("Some content");

        // Assert
        assertEquals("Success after retry", result);
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void generateCET4Version_allRetriesExhausted_throwsBusinessException() {
        // Arrange: maxRetries=1
        when(config.getApiKey()).thenReturn(VALID_API_KEY);
        when(config.getApiUrl()).thenReturn(API_URL);
        when(config.getModel()).thenReturn(MODEL);
        when(config.getMaxRetries()).thenReturn(1);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("Some content"));
        assertEquals("AI 服务暂时不可用，请稍后重试", ex.getMessage());
    }

    @Test
    void generateCET4Version_nullMaxRetries_defaultsToOneAttempt() {
        // Arrange: maxRetries is null, should default to 1
        when(config.getApiKey()).thenReturn(VALID_API_KEY);
        when(config.getApiUrl()).thenReturn(API_URL);
        when(config.getModel()).thenReturn(MODEL);
        when(config.getMaxRetries()).thenReturn(null);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // Act & Assert
        assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("Some content"));
        // With default 1 attempt, exchange should be called exactly once
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void generateCET4Version_zeroMaxRetries_defaultsToOneAttempt() {
        // Arrange: maxRetries = 0, should default to 1
        when(config.getApiKey()).thenReturn(VALID_API_KEY);
        when(config.getApiUrl()).thenReturn(API_URL);
        when(config.getModel()).thenReturn(MODEL);
        when(config.getMaxRetries()).thenReturn(0);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // Act & Assert
        assertThrows(BusinessException.class,
                () -> openRouterService.generateCET4Version("Some content"));
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    // ==================== Input validation edge cases ====================

    @Test
    void generateCET4Version_exactlyAtMaxLength_succeeds() {
        // Arrange: content with exactly 100000 chars should be accepted
        setupConfigForSuccess();
        String maxLengthContent = "a".repeat(100000);
        mockSuccessfulApiResponse("Processed content");

        // Act
        String result = openRouterService.generateCET4Version(maxLengthContent);

        // Assert
        assertEquals("Processed content", result);
    }

    // ==================== Helper methods ====================

    private void setupConfigForSuccess() {
        when(config.getApiKey()).thenReturn(VALID_API_KEY);
        when(config.getApiUrl()).thenReturn(API_URL);
        when(config.getModel()).thenReturn(MODEL);
        when(config.getMaxRetries()).thenReturn(1);
    }

    private void mockSuccessfulApiResponse(String content) {
        String responseBody = String.format(
                "{\"choices\":[{\"message\":{\"content\":\"%s\"}}]}", content);
        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);
    }
}
