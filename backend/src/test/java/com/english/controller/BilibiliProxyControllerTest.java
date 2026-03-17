package com.english.controller;
import com.english.test.TestUtils;

import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.net.URI;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BilibiliProxyController unit tests.
 *
 * Covers:
 * - GET /api/bilibili/view    (video info by bvid)
 * - GET /api/bilibili/player  (subtitle list by bvid + cid)
 * - GET /api/bilibili/subtitle-file (subtitle JSON file proxy)
 *
 * Each endpoint tests: success, invalid params, RestTemplate exception, empty response body.
 */
@ExtendWith(MockitoExtension.class)
class BilibiliProxyControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate mockRestTemplate;
    private BilibiliProxyController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new BilibiliProxyController();

        // Replace the internal RestTemplate with a mock via reflection
        mockRestTemplate = mock(RestTemplate.class);
        Field restTemplateField = BilibiliProxyController.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(controller, mockRestTemplate);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).addFilters(TestUtils.authenticationFilter()).build();

        // BilibiliProxyController requires isAuthenticated()
        TestHelper.createMockAuthentication("student1", "USER");
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== GET /api/bilibili/view ==========

    @Test
    @DisplayName("GET /api/bilibili/view - success with valid bvid")
    void view_success() throws Exception {
        String bilibiliResponse = "{\"code\":0,\"data\":{\"cid\":12345}}";
        ResponseEntity<String> resp = new ResponseEntity<>(bilibiliResponse, HttpStatus.OK);
        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(resp);

        mockMvc.perform(get("/api/bilibili/view").param("bvid", "BV1xx411c7mD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.cid").value(12345));
    }

    @Test
    @DisplayName("GET /api/bilibili/view - invalid bvid format returns 400")
    void view_invalidBvid_returns400() throws Exception {
        mockMvc.perform(get("/api/bilibili/view").param("bvid", "invalid123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("bvid 格式非法"));
    }

    @Test
    @DisplayName("GET /api/bilibili/view - bvid too short returns 400")
    void view_bvidTooShort_returns400() throws Exception {
        mockMvc.perform(get("/api/bilibili/view").param("bvid", "BV123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("bvid 格式非法"));
    }

    @Test
    @DisplayName("GET /api/bilibili/view - empty body from Bilibili returns 502")
    void view_emptyBody_returns502() throws Exception {
        ResponseEntity<String> resp = new ResponseEntity<>("", HttpStatus.OK);
        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(resp);

        mockMvc.perform(get("/api/bilibili/view").param("bvid", "BV1xx411c7mD"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("B站返回空响应"));
    }

    @Test
    @DisplayName("GET /api/bilibili/view - RestTemplate exception returns 502")
    void view_restTemplateException_returns502() throws Exception {
        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));

        mockMvc.perform(get("/api/bilibili/view").param("bvid", "BV1xx411c7mD"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("请求B站API失败"));
    }

    // ========== GET /api/bilibili/player ==========

    @Test
    @DisplayName("GET /api/bilibili/player - success with valid bvid and cid")
    void player_success() throws Exception {
        String subtitleList = "{\"code\":0,\"data\":{\"subtitle\":{\"subtitles\":[]}}}";
        ResponseEntity<String> resp = new ResponseEntity<>(subtitleList, HttpStatus.OK);
        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(resp);

        mockMvc.perform(get("/api/bilibili/player")
                        .param("bvid", "BV1xx411c7mD")
                        .param("cid", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("GET /api/bilibili/player - invalid bvid returns 400")
    void player_invalidBvid_returns400() throws Exception {
        mockMvc.perform(get("/api/bilibili/player")
                        .param("bvid", "XY1234567890")
                        .param("cid", "12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("bvid 格式非法"));
    }

    @Test
    @DisplayName("GET /api/bilibili/player - invalid cid (non-numeric) returns 400")
    void player_invalidCid_returns400() throws Exception {
        mockMvc.perform(get("/api/bilibili/player")
                        .param("bvid", "BV1xx411c7mD")
                        .param("cid", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("cid 格式非法"));
    }

    @Test
    @DisplayName("GET /api/bilibili/player - empty body returns 502")
    void player_emptyBody_returns502() throws Exception {
        ResponseEntity<String> resp = new ResponseEntity<>("", HttpStatus.OK);
        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(resp);

        mockMvc.perform(get("/api/bilibili/player")
                        .param("bvid", "BV1xx411c7mD")
                        .param("cid", "12345"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("字幕列表返回空响应"));
    }

    @Test
    @DisplayName("GET /api/bilibili/player - RestTemplate exception returns 502")
    void player_restTemplateException_returns502() throws Exception {
        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Timeout"));

        mockMvc.perform(get("/api/bilibili/player")
                        .param("bvid", "BV1xx411c7mD")
                        .param("cid", "12345"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("请求字幕列表失败"));
    }

    // ========== GET /api/bilibili/subtitle-file ==========

    @Test
    @DisplayName("GET /api/bilibili/subtitle-file - success with valid hdslb.com URL")
    void subtitleFile_success() throws Exception {
        String subtitleJson = "{\"body\":[{\"content\":\"Hello\"}]}";
        ResponseEntity<String> resp = new ResponseEntity<>(subtitleJson, HttpStatus.OK);
        when(mockRestTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(resp);

        String validUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/abc123_def456.json";

        mockMvc.perform(get("/api/bilibili/subtitle-file").param("url", validUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].content").value("Hello"));
    }

    @Test
    @DisplayName("GET /api/bilibili/subtitle-file - non-hdslb domain returns 400 (SSRF protection)")
    void subtitleFile_nonHdslbDomain_returns400() throws Exception {
        String maliciousUrl = "https://evil.com/bfs/subtitle/abc123.json";

        mockMvc.perform(get("/api/bilibili/subtitle-file").param("url", maliciousUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("非法字幕 URL"));
    }

    @Test
    @DisplayName("GET /api/bilibili/subtitle-file - blank url returns 400")
    void subtitleFile_blankUrl_returns400() throws Exception {
        mockMvc.perform(get("/api/bilibili/subtitle-file").param("url", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("非法字幕 URL"));
    }

    @Test
    @DisplayName("GET /api/bilibili/subtitle-file - path traversal attempt returns 400")
    void subtitleFile_pathTraversal_returns400() throws Exception {
        String traversalUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/../../../etc/passwd.json";

        mockMvc.perform(get("/api/bilibili/subtitle-file").param("url", traversalUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("非法字幕 URL"));
    }

    @Test
    @DisplayName("GET /api/bilibili/subtitle-file - empty body returns 502")
    void subtitleFile_emptyBody_returns502() throws Exception {
        ResponseEntity<String> resp = new ResponseEntity<>("", HttpStatus.OK);
        when(mockRestTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(resp);

        String validUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/abc123.json";

        mockMvc.perform(get("/api/bilibili/subtitle-file").param("url", validUrl))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("字幕文件返回空响应"));
    }

    @Test
    @DisplayName("GET /api/bilibili/subtitle-file - RestTemplate exception returns 502")
    void subtitleFile_restTemplateException_returns502() throws Exception {
        when(mockRestTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Network error"));

        String validUrl = "https://aisubtitle.hdslb.com/bfs/subtitle/abc123.json";

        mockMvc.perform(get("/api/bilibili/subtitle-file").param("url", validUrl))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("下载字幕文件失败"));
    }
}
