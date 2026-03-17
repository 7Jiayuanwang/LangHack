package com.english.controller;
import com.english.test.TestUtils;

import com.english.service.TencentTranslateService;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TranslateController unit tests.
 *
 * Covers:
 * - POST /api/translate/auto       (auto-detect language)
 * - POST /api/translate/en-to-cn   (English to Chinese)
 * - POST /api/translate/cn-to-en   (Chinese to English)
 * - POST /api/translate/custom     (custom source/target)
 * - POST /api/translate/batch      (batch translation)
 *
 * Each endpoint tests: success, empty text, text too long, service exception.
 * /custom also tests missing source/target.
 * /batch also tests empty list, list too large, individual item too long, and null items.
 */
@ExtendWith(MockitoExtension.class)
class TranslateControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TencentTranslateService translateService;

    @InjectMocks
    private TranslateController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).addFilters(TestUtils.authenticationFilter()).build();
        // TranslateController requires isAuthenticated()
        TestHelper.createMockAuthentication("student1", "USER");
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== POST /api/translate/auto ==========

    @Test
    @DisplayName("POST /api/translate/auto - success")
    void translateAuto_success() throws Exception {
        when(translateService.translateAuto("Hello world")).thenReturn("你好世界");

        Map<String, String> body = Map.of("text", "Hello world");

        mockMvc.perform(post("/api/translate/auto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("你好世界"));

        verify(translateService).translateAuto("Hello world");
    }

    @Test
    @DisplayName("POST /api/translate/auto - null text returns error")
    void translateAuto_nullText_returnsError() throws Exception {
        Map<String, Object> body = new HashMap<>();
        // text is null

        mockMvc.perform(post("/api/translate/auto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("翻译文本不能为空"));

        verify(translateService, never()).translateAuto(anyString());
    }

    @Test
    @DisplayName("POST /api/translate/auto - empty text returns error")
    void translateAuto_emptyText_returnsError() throws Exception {
        Map<String, String> body = Map.of("text", "   ");

        mockMvc.perform(post("/api/translate/auto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("翻译文本不能为空"));
    }

    @Test
    @DisplayName("POST /api/translate/auto - text too long returns error")
    void translateAuto_textTooLong_returnsError() throws Exception {
        String longText = "a".repeat(2001);
        Map<String, String> body = Map.of("text", longText);

        mockMvc.perform(post("/api/translate/auto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("翻译文本不能超过2000字"));
    }

    @Test
    @DisplayName("POST /api/translate/auto - service exception returns generic error")
    void translateAuto_serviceException_returnsError() throws Exception {
        when(translateService.translateAuto(anyString()))
                .thenThrow(new RuntimeException("Service down"));

        Map<String, String> body = Map.of("text", "Hello");

        mockMvc.perform(post("/api/translate/auto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("翻译服务暂时不可用，请稍后重试"));
    }

    // ========== POST /api/translate/en-to-cn ==========

    @Test
    @DisplayName("POST /api/translate/en-to-cn - success")
    void translateEnToCn_success() throws Exception {
        when(translateService.translateEnToCn("Hello")).thenReturn("你好");

        Map<String, String> body = Map.of("text", "Hello");

        mockMvc.perform(post("/api/translate/en-to-cn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("你好"));
    }

    @Test
    @DisplayName("POST /api/translate/en-to-cn - empty text returns error")
    void translateEnToCn_emptyText_returnsError() throws Exception {
        Map<String, String> body = Map.of("text", "");

        mockMvc.perform(post("/api/translate/en-to-cn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("翻译文本不能为空"));
    }

    // ========== POST /api/translate/cn-to-en ==========

    @Test
    @DisplayName("POST /api/translate/cn-to-en - success")
    void translateCnToEn_success() throws Exception {
        when(translateService.translateCnToEn("你好")).thenReturn("Hello");

        Map<String, String> body = Map.of("text", "你好");

        mockMvc.perform(post("/api/translate/cn-to-en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Hello"));
    }

    @Test
    @DisplayName("POST /api/translate/cn-to-en - service exception returns generic error")
    void translateCnToEn_serviceException_returnsError() throws Exception {
        when(translateService.translateCnToEn(anyString()))
                .thenThrow(new RuntimeException("timeout"));

        Map<String, String> body = Map.of("text", "你好");

        mockMvc.perform(post("/api/translate/cn-to-en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("翻译服务暂时不可用，请稍后重试"));
    }

    // ========== POST /api/translate/custom ==========

    @Test
    @DisplayName("POST /api/translate/custom - success")
    void translateCustom_success() throws Exception {
        when(translateService.translate("Hello", "en", "ja")).thenReturn("こんにちは");

        Map<String, String> body = Map.of("text", "Hello", "source", "en", "target", "ja");

        mockMvc.perform(post("/api/translate/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("こんにちは"));
    }

    @Test
    @DisplayName("POST /api/translate/custom - missing source returns error")
    void translateCustom_missingSource_returnsError() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("text", "Hello");
        body.put("target", "zh");
        // source is null

        mockMvc.perform(post("/api/translate/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("源语言不能为空"));
    }

    @Test
    @DisplayName("POST /api/translate/custom - missing target returns error")
    void translateCustom_missingTarget_returnsError() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("text", "Hello");
        body.put("source", "en");
        // target is null

        mockMvc.perform(post("/api/translate/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("目标语言不能为空"));
    }

    @Test
    @DisplayName("POST /api/translate/custom - empty text returns error")
    void translateCustom_emptyText_returnsError() throws Exception {
        Map<String, String> body = Map.of("text", "", "source", "en", "target", "zh");

        mockMvc.perform(post("/api/translate/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("翻译文本不能为空"));
    }

    @Test
    @DisplayName("POST /api/translate/custom - text too long returns error")
    void translateCustom_textTooLong_returnsError() throws Exception {
        String longText = "x".repeat(2001);
        Map<String, String> body = Map.of("text", longText, "source", "en", "target", "zh");

        mockMvc.perform(post("/api/translate/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("翻译文本不能超过2000字"));
    }

    // ========== POST /api/translate/batch ==========

    @Test
    @DisplayName("POST /api/translate/batch - success with matched line count")
    void translateBatch_success() throws Exception {
        // The batch endpoint joins texts with \n, translates as a block, then splits
        when(translateService.translate(anyString(), eq("en"), eq("zh")))
                .thenReturn("你好\n世界");

        Map<String, Object> body = new HashMap<>();
        body.put("texts", List.of("Hello", "World"));
        body.put("direction", "en-to-zh");

        mockMvc.perform(post("/api/translate/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("你好"))
                .andExpect(jsonPath("$.data[1]").value("世界"));
    }

    @Test
    @DisplayName("POST /api/translate/batch - null texts returns error")
    void translateBatch_nullTexts_returnsError() throws Exception {
        Map<String, Object> body = new HashMap<>();
        // texts is null

        mockMvc.perform(post("/api/translate/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("texts 不能为空"));
    }

    @Test
    @DisplayName("POST /api/translate/batch - empty texts list returns error")
    void translateBatch_emptyTexts_returnsError() throws Exception {
        Map<String, Object> body = Map.of("texts", List.of());

        mockMvc.perform(post("/api/translate/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("texts 不能为空"));
    }

    @Test
    @DisplayName("POST /api/translate/batch - more than 200 items returns error")
    void translateBatch_tooManyTexts_returnsError() throws Exception {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            texts.add("item" + i);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("texts", texts);
        body.put("direction", "en-to-zh");

        mockMvc.perform(post("/api/translate/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("批量翻译每次最多200条"));
    }

    @Test
    @DisplayName("POST /api/translate/batch - individual item too long returns error")
    void translateBatch_itemTooLong_returnsError() throws Exception {
        String longText = "a".repeat(2001);
        Map<String, Object> body = new HashMap<>();
        body.put("texts", List.of("Hello", longText));
        body.put("direction", "en-to-zh");

        mockMvc.perform(post("/api/translate/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单条翻译文本不能超过2000字"));
    }

    @Test
    @DisplayName("POST /api/translate/batch - default direction is en-to-zh when null")
    void translateBatch_defaultDirection() throws Exception {
        when(translateService.translate(anyString(), eq("en"), eq("zh")))
                .thenReturn("翻译结果");

        Map<String, Object> body = new HashMap<>();
        body.put("texts", List.of("test"));
        // direction is null → defaults to "en-to-zh"

        mockMvc.perform(post("/api/translate/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        // Should call translate with source="en", target="zh"
        verify(translateService).translate(anyString(), eq("en"), eq("zh"));
    }

    @Test
    @DisplayName("POST /api/translate/batch - zh-to-en direction")
    void translateBatch_zhToEnDirection() throws Exception {
        when(translateService.translate(anyString(), eq("zh"), eq("en")))
                .thenReturn("Hello");

        Map<String, Object> body = new HashMap<>();
        body.put("texts", List.of("你好"));
        body.put("direction", "zh-to-en");

        mockMvc.perform(post("/api/translate/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0]").value("Hello"));

        verify(translateService).translate(anyString(), eq("zh"), eq("en"));
    }
}
