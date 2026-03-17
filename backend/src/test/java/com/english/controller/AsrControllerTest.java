package com.english.controller;
import com.english.test.TestUtils;

import com.english.service.TencentAsrService;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AsrController unit tests.
 *
 * Covers: POST /api/asr/recognize
 * - Success path (default and explicit format/lang)
 * - Empty audio data validation
 * - Oversized audio data validation
 * - Service exception fallback
 * - Null format/lang defaults
 */
@ExtendWith(MockitoExtension.class)
class AsrControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TencentAsrService asrService;

    @InjectMocks
    private AsrController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).addFilters(TestUtils.authenticationFilter()).build();
        // AsrController requires isAuthenticated() — set up a logged-in user
        TestHelper.createMockAuthentication("student1", "USER");
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== POST /api/asr/recognize — success ==========

    @Test
    @DisplayName("POST /api/asr/recognize - success with default format and lang")
    void recognize_success_defaultFormatAndLang() throws Exception {
        when(asrService.recognize(eq("dGVzdA=="), eq("webm"), eq("zh")))
                .thenReturn("识别结果文本");

        Map<String, Object> body = new HashMap<>();
        body.put("audioData", "dGVzdA==");
        // format and lang are null → defaults to "webm" and "zh"

        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("识别结果文本"));

        verify(asrService).recognize("dGVzdA==", "webm", "zh");
    }

    @Test
    @DisplayName("POST /api/asr/recognize - success with explicit format=mp3 and lang=en")
    void recognize_success_explicitFormatAndLang() throws Exception {
        when(asrService.recognize(eq("YXVkaW8="), eq("mp3"), eq("en")))
                .thenReturn("recognized text");

        Map<String, Object> body = new HashMap<>();
        body.put("audioData", "YXVkaW8=");
        body.put("format", "mp3");
        body.put("lang", "en");

        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("recognized text"));

        verify(asrService).recognize("YXVkaW8=", "mp3", "en");
    }

    // ========== POST /api/asr/recognize — validation errors ==========

    @Test
    @DisplayName("POST /api/asr/recognize - null audioData returns error")
    void recognize_nullAudioData_returnsError() throws Exception {
        Map<String, Object> body = new HashMap<>();
        // audioData is absent → null

        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("音频数据不能为空"));

        verify(asrService, never()).recognize(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/asr/recognize - empty audioData returns error")
    void recognize_emptyAudioData_returnsError() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("audioData", "");

        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("音频数据不能为空"));

        verify(asrService, never()).recognize(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/asr/recognize - oversized audioData returns error")
    void recognize_oversizedAudioData_returnsError() throws Exception {
        // Create a string larger than 10 * 1024 * 1024 characters
        StringBuilder hugeData = new StringBuilder();
        int targetSize = 10 * 1024 * 1024 + 1;
        while (hugeData.length() < targetSize) {
            hugeData.append("AAAA");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("audioData", hugeData.toString());

        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("音频数据超过大小限制（最大约10MB）"));

        verify(asrService, never()).recognize(anyString(), anyString(), anyString());
    }

    // ========== POST /api/asr/recognize — service exception ==========

    @Test
    @DisplayName("POST /api/asr/recognize - service throws exception returns generic error")
    void recognize_serviceException_returnsGenericError() throws Exception {
        when(asrService.recognize(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("ASR service down"));

        Map<String, Object> body = new HashMap<>();
        body.put("audioData", "dGVzdA==");

        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("语音识别失败，请稍后重试"));
    }

    @Test
    @DisplayName("POST /api/asr/recognize - service throws BusinessException returns generic error")
    void recognize_businessException_returnsGenericError() throws Exception {
        // The controller catches ALL exceptions (catch(Exception)) — not BusinessException separately.
        // So even a BusinessException results in the generic "语音识别失败" message.
        when(asrService.recognize(anyString(), anyString(), anyString()))
                .thenThrow(new com.english.exception.BusinessException("ASR 服务未配置"));

        Map<String, Object> body = new HashMap<>();
        body.put("audioData", "dGVzdA==");

        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("语音识别失败，请稍后重试"));
    }

    // ========== POST /api/asr/recognize — format/lang defaults ==========

    @Test
    @DisplayName("POST /api/asr/recognize - null format defaults to 'webm'")
    void recognize_nullFormat_defaultsToWebm() throws Exception {
        when(asrService.recognize(anyString(), eq("webm"), anyString()))
                .thenReturn("result");

        Map<String, Object> body = new HashMap<>();
        body.put("audioData", "dGVzdA==");
        body.put("lang", "en");
        // format is null

        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(asrService).recognize("dGVzdA==", "webm", "en");
    }

    @Test
    @DisplayName("POST /api/asr/recognize - null lang defaults to 'zh'")
    void recognize_nullLang_defaultsToZh() throws Exception {
        when(asrService.recognize(anyString(), anyString(), eq("zh")))
                .thenReturn("result");

        Map<String, Object> body = new HashMap<>();
        body.put("audioData", "dGVzdA==");
        body.put("format", "mp3");
        // lang is null

        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(asrService).recognize("dGVzdA==", "mp3", "zh");
    }

    // ========== POST /api/asr/recognize — rate limiting ==========

    @Test
    @DisplayName("POST /api/asr/recognize - rate limit after 10 requests per minute")
    void recognize_rateLimitExceeded_returnsError() throws Exception {
        when(asrService.recognize(anyString(), anyString(), anyString()))
                .thenReturn("result");

        Map<String, Object> body = new HashMap<>();
        body.put("audioData", "dGVzdA==");

        // First 10 requests should succeed (isRateLimited counts from 1, > 10 triggers limit)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/asr/recognize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        // 11th request should be rate-limited
        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("语音识别请求过于频繁，请1分钟后重试（每分钟限10次）"));
    }

    @Test
    @DisplayName("POST /api/asr/recognize - empty string result from service returns success")
    void recognize_emptyResult_returnsSuccess() throws Exception {
        when(asrService.recognize(anyString(), anyString(), anyString()))
                .thenReturn("");

        Map<String, Object> body = new HashMap<>();
        body.put("audioData", "dGVzdA==");

        mockMvc.perform(post("/api/asr/recognize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(""));
    }
}
