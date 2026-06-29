package com.english.controller;

import com.english.entity.CampaignActivity;
import com.english.enums.CampaignStatus;
import com.english.enums.EnrollmentMode;
import com.english.service.CampaignService;
import com.english.test.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 活动管理控制器单元测试
 *
 * 采用 standaloneSetup 模式（与 AdminControllerTest 一致），避免 @WebMvcTest 切片
 * 加载 AccessLogInterceptor 依赖链（AccessLogBufferService 等非切片 bean 无法满足）。
 * @PreAuthorize 不在 standalone 模式下强制，因此不测试 401/403 路径——权限由
 * SecurityConfig 过滤链在集成测试中覆盖。
 */
class CampaignControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CampaignService campaignService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CampaignController campaignController;

    private CampaignActivity testCampaign;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(campaignController)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        // 注入 ADMIN 认证到 SecurityContext（standalone 不走 Spring Security 过滤链）
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin1", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        testCampaign = new CampaignActivity();
        testCampaign.setId(1L);
        testCampaign.setTitle("测试活动");
        testCampaign.setDescription("这是一个测试活动");
        testCampaign.setStatus(CampaignStatus.DRAFT);
        testCampaign.setEnrollmentMode(EnrollmentMode.MANUAL);
        testCampaign.setStartTime(LocalDateTime.now().plusDays(1));
        testCampaign.setEndTime(LocalDateTime.now().plusDays(30));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCreateCampaign_Success() throws Exception {
        // Given
        when(campaignService.createCampaign(any(CampaignActivity.class))).thenReturn(testCampaign);

        // When & Then
        mockMvc.perform(post("/api/admin/campaigns")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCampaign)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("测试活动"));

        verify(campaignService, times(1)).createCampaign(any(CampaignActivity.class));
    }

    @Test
    void testUpdateCampaign_Success() throws Exception {
        // Given
        when(campaignService.updateCampaign(eq(1L), any(CampaignActivity.class))).thenReturn(testCampaign);

        // When & Then
        mockMvc.perform(put("/api/admin/campaigns/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCampaign)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campaignService, times(1)).updateCampaign(eq(1L), any(CampaignActivity.class));
    }

    @Test
    void testDeleteCampaign_Success() throws Exception {
        // Given
        doNothing().when(campaignService).deleteCampaign(1L);

        // When & Then
        mockMvc.perform(delete("/api/admin/campaigns/1")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campaignService, times(1)).deleteCampaign(1L);
    }

    @Test
    void testGetCampaignById_Success() throws Exception {
        // Given
        when(campaignService.getCampaignById(1L)).thenReturn(testCampaign);

        // When & Then
        mockMvc.perform(get("/api/admin/campaigns/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("测试活动"));

        verify(campaignService, times(1)).getCampaignById(1L);
    }

    @Test
    void testStartCampaign_Success() throws Exception {
        // Given
        doNothing().when(campaignService).startCampaign(1L);

        // When & Then
        mockMvc.perform(put("/api/admin/campaigns/1/start")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campaignService, times(1)).startCampaign(1L);
    }

    @Test
    void testEndCampaign_Success() throws Exception {
        // Given
        doNothing().when(campaignService).endCampaign(1L);

        // When & Then
        mockMvc.perform(put("/api/admin/campaigns/1/end")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campaignService, times(1)).endCampaign(1L);
    }
}
