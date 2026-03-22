package com.english.controller;

import com.english.test.TestUtils;
import com.english.service.impl.DataCleanupServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OpsController unit tests.
 * Covers: data cleanup and data statistics endpoints migrated from AdminController.
 */
@DisplayName("OpsController Tests")
class OpsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DataCleanupServiceImpl dataCleanupService;

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
}
