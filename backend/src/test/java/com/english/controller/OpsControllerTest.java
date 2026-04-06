package com.english.controller;

import com.english.entity.User;
import com.english.service.AiProviderService;
import com.english.service.FeatureToggleService;
import com.english.service.SystemConfigService;
import com.english.service.TencentTranslateService;
import com.english.service.UserService;
import com.english.service.impl.DataCleanupServiceImpl;
import com.english.test.TestUtils;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OpsController unit tests.
 * Covers: data cleanup, data statistics, and tag member lookup endpoints.
 */
@DisplayName("OpsController Tests")
class OpsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DataCleanupServiceImpl dataCleanupService;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private TencentTranslateService translateService;
    @Mock
    private AiProviderService aiProviderService;
    @Mock
    private FeatureToggleService featureToggleService;
    @Mock
    private UserService userService;

    @InjectMocks
    private OpsController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin1", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/ops/cleanup-data - cleanup data success")
    void cleanupData_success() throws Exception {
        doNothing().when(dataCleanupService).cleanupInconsistentData();

        mockMvc.perform(post("/api/ops/cleanup-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("数据清理完成，请查看控制台输出获取详细信息"));

        verify(dataCleanupService).cleanupInconsistentData();
    }

    @Test
    @DisplayName("POST /api/ops/cleanup-data - cleanup data failure returns error")
    void cleanupData_failure_returnsError() throws Exception {
        doThrow(new RuntimeException("DB error")).when(dataCleanupService).cleanupInconsistentData();

        mockMvc.perform(post("/api/ops/cleanup-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("数据清理失败，请稍后重试"));
    }

    @Test
    @DisplayName("GET /api/ops/data-stats - print data statistics success")
    void printDataStatistics_success() throws Exception {
        doNothing().when(dataCleanupService).printDataStatistics();

        mockMvc.perform(get("/api/ops/data-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("数据统计信息已打印到控制台"));

        verify(dataCleanupService).printDataStatistics();
    }

    @Test
    @DisplayName("GET /api/ops/data-stats - data statistics failure returns error")
    void printDataStatistics_failure_returnsError() throws Exception {
        doThrow(new RuntimeException("DB error")).when(dataCleanupService).printDataStatistics();

        mockMvc.perform(get("/api/ops/data-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取数据统计失败，请稍后重试"));
    }

    @Test
    @DisplayName("GET /api/ops/user-tags/{tagName}/users - returns user details")
    void getUsersByTag_returnsUserDetails() throws Exception {
        User u = new User();
        u.setId(1L);
        u.setUsername("alice");
        u.setNickname("Alice");
        u.setRole("STUDENT");

        when(featureToggleService.getUserIdsByTag("vip")).thenReturn(List.of(1L));
        when(userService.getById(1L)).thenReturn(u);

        mockMvc.perform(get("/api/ops/user-tags/vip/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].username").value("alice"))
                .andExpect(jsonPath("$.data[0].nickname").value("Alice"))
                .andExpect(jsonPath("$.data[0].role").value("STUDENT"));
    }
}
