package com.english.controller;

import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.impl.AccessMetricsService;
import com.english.service.impl.AiCallMetricsService;
import com.english.service.impl.SystemMetricsService;
import com.english.test.TestUtils;
import com.english.util.JwtUtil;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoringController 单元测试")
class MonitoringControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private AccessMetricsService accessMetricsService;

    @Mock
    private SystemMetricsService systemMetricsService;

    @Mock
    private AiCallMetricsService aiCallMetricsService;

    @Mock
    private AccessStatsHourlyMapper hourlyMapper;

    @Mock
    private AccessStatsDailyMapper dailyMapper;

    @Mock
    private AiCallLogMapper aiCallLogMapper;

    @Mock
    private AiCallStatsDailyMapper aiCallStatsDailyMapper;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private MonitoringController monitoringController;

    private Authentication authentication;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(monitoringController)
                .setMessageConverters(converter)
                .addFilters(TestUtils.authenticationFilter())
                .build();
        authentication = TestHelper.createMockAuthentication("admin", "ADMIN");
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== GET /api/ops/monitoring/realtime ==========

    @Nested
    @DisplayName("GET /api/ops/monitoring/realtime")
    class RealtimeTests {

        @Test
        @DisplayName("返回200及实时指标数据")
        void getRealTimeMetrics_returnsMetrics() throws Exception {
            // Arrange
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("activeUsers", 15);
            metrics.put("requestsPerSecond", 42.5);
            metrics.put("concurrentRequests", 3);

            when(accessMetricsService.getRealTimeMetrics()).thenReturn(metrics);

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/realtime"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.activeUsers").value(15))
                    .andExpect(jsonPath("$.data.requestsPerSecond").value(42.5))
                    .andExpect(jsonPath("$.data.concurrentRequests").value(3));

            verify(accessMetricsService).getRealTimeMetrics();
        }

        @Test
        @DisplayName("服务异常时返回错误信息")
        void getRealTimeMetrics_serviceException_returnsError() throws Exception {
            // Arrange
            when(accessMetricsService.getRealTimeMetrics())
                    .thenThrow(new RuntimeException("Redis connection failed"));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/realtime"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("获取实时指标失败"));
        }
    }

    // ========== GET /api/ops/monitoring/system ==========

    @Nested
    @DisplayName("GET /api/ops/monitoring/system")
    class SystemMetricsTests {

        @Test
        @DisplayName("返回200及系统指标数据")
        void getSystemMetrics_returnsMetrics() throws Exception {
            // Arrange
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("heapUsed", 256_000_000L);
            metrics.put("heapMax", 512_000_000L);
            metrics.put("cpuLoad", 0.35);

            when(systemMetricsService.getSystemMetrics()).thenReturn(metrics);

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/system"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.heapUsed").value(256_000_000))
                    .andExpect(jsonPath("$.data.heapMax").value(512_000_000))
                    .andExpect(jsonPath("$.data.cpuLoad").value(0.35));

            verify(systemMetricsService).getSystemMetrics();
        }

        @Test
        @DisplayName("服务异常时返回错误信息")
        void getSystemMetrics_serviceException_returnsError() throws Exception {
            // Arrange
            when(systemMetricsService.getSystemMetrics())
                    .thenThrow(new RuntimeException("JMX error"));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/system"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("获取系统指标失败"));
        }
    }

    // ========== GET /api/ops/monitoring/hourly ==========

    @Nested
    @DisplayName("GET /api/ops/monitoring/hourly")
    class HourlyStatsTests {

        @Test
        @DisplayName("返回200及小时统计列表")
        void getHourlyStats_returnsList() throws Exception {
            // Arrange
            AccessStatsHourly stat1 = new AccessStatsHourly();
            stat1.setId(1L);
            stat1.setStatHour(LocalDateTime.of(2026, 3, 22, 10, 0));
            stat1.setTotalRequests(120);
            stat1.setUniqueUsers(15);

            AccessStatsHourly stat2 = new AccessStatsHourly();
            stat2.setId(2L);
            stat2.setStatHour(LocalDateTime.of(2026, 3, 22, 11, 0));
            stat2.setTotalRequests(95);
            stat2.setUniqueUsers(12);

            when(hourlyMapper.selectList(any())).thenReturn(Arrays.asList(stat1, stat2));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/hourly")
                            .param("hours", "24"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].totalRequests").value(120))
                    .andExpect(jsonPath("$.data[1].totalRequests").value(95));

            verify(hourlyMapper).selectList(any());
        }

        @Test
        @DisplayName("默认参数 hours=24 — 无参数时使用默认值")
        void getHourlyStats_defaultHours() throws Exception {
            // Arrange
            when(hourlyMapper.selectList(any())).thenReturn(Collections.emptyList());

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/hourly"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());

            verify(hourlyMapper).selectList(any());
        }

        @Test
        @DisplayName("Mapper异常时返回错误信息")
        void getHourlyStats_mapperException_returnsError() throws Exception {
            // Arrange
            when(hourlyMapper.selectList(any()))
                    .thenThrow(new RuntimeException("DB error"));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/hourly")
                            .param("hours", "24"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("获取小时统计失败"));
        }
    }

    // ========== GET /api/ops/monitoring/daily ==========

    @Nested
    @DisplayName("GET /api/ops/monitoring/daily")
    class DailyStatsTests {

        @Test
        @DisplayName("返回200及日统计列表")
        void getDailyStats_returnsList() throws Exception {
            // Arrange
            AccessStatsDaily stat1 = new AccessStatsDaily();
            stat1.setId(1L);
            stat1.setStatDate(LocalDate.of(2026, 3, 20));
            stat1.setTotalRequests(5000);
            stat1.setUniqueUsers(200);

            AccessStatsDaily stat2 = new AccessStatsDaily();
            stat2.setId(2L);
            stat2.setStatDate(LocalDate.of(2026, 3, 21));
            stat2.setTotalRequests(4800);
            stat2.setUniqueUsers(190);

            when(dailyMapper.selectList(any())).thenReturn(Arrays.asList(stat1, stat2));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/daily")
                            .param("days", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].totalRequests").value(5000))
                    .andExpect(jsonPath("$.data[1].uniqueUsers").value(190));

            verify(dailyMapper).selectList(any());
        }

        @Test
        @DisplayName("Mapper异常时返回错误信息")
        void getDailyStats_mapperException_returnsError() throws Exception {
            // Arrange
            when(dailyMapper.selectList(any()))
                    .thenThrow(new RuntimeException("DB error"));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/daily")
                            .param("days", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("获取日统计失败"));
        }
    }

    // ========== GET /api/ops/monitoring/top-pages ==========

    @Nested
    @DisplayName("GET /api/ops/monitoring/top-pages")
    class TopPagesTests {

        @Test
        @DisplayName("返回200及热门页面数据")
        void getTopPages_returnsTopPagesData() throws Exception {
            // Arrange
            AccessStatsDaily daily = new AccessStatsDaily();
            daily.setId(1L);
            daily.setStatDate(LocalDate.of(2026, 3, 21));
            daily.setTopPages("[{\"path\":\"/courses\",\"count\":500},{\"path\":\"/texts\",\"count\":300}]");

            when(dailyMapper.selectOne(any())).thenReturn(daily);

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/top-pages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isNotEmpty());

            verify(dailyMapper).selectOne(any());
        }

        @Test
        @DisplayName("无数据时返回空JSON数组字符串")
        void getTopPages_noData_returnsEmptyArray() throws Exception {
            // Arrange
            when(dailyMapper.selectOne(any())).thenReturn(null);

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/top-pages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("[]"));
        }

        @Test
        @DisplayName("异常时返回错误信息")
        void getTopPages_exception_returnsError() throws Exception {
            // Arrange
            when(dailyMapper.selectOne(any()))
                    .thenThrow(new RuntimeException("DB error"));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/top-pages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("获取热门页面失败"));
        }
    }

    // ========== GET /api/ops/monitoring/ai-calls/realtime ==========

    @Nested
    @DisplayName("GET /api/ops/monitoring/ai-calls/realtime")
    class AiCallsRealtimeTests {

        @Test
        @DisplayName("返回200及AI调用实时统计")
        void getAiCallsRealtime_returnsStats() throws Exception {
            // Arrange
            Map<String, Object> stats = new LinkedHashMap<>();
            Map<String, Object> hiagentStats = new LinkedHashMap<>();
            hiagentStats.put("totalCalls", 100L);
            hiagentStats.put("successCalls", 95L);
            hiagentStats.put("failedCalls", 5L);
            hiagentStats.put("successRate", 95L);
            hiagentStats.put("avgResponseTimeMs", 50L);
            stats.put("HIAGENT", hiagentStats);

            Map<String, Object> openrouterStats = new LinkedHashMap<>();
            openrouterStats.put("totalCalls", 200L);
            openrouterStats.put("successCalls", 198L);
            openrouterStats.put("failedCalls", 2L);
            openrouterStats.put("successRate", 99L);
            openrouterStats.put("avgResponseTimeMs", 120L);
            stats.put("OPENROUTER", openrouterStats);

            when(aiCallMetricsService.getTodayStats()).thenReturn(stats);

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/ai-calls/realtime"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.HIAGENT.totalCalls").value(100))
                    .andExpect(jsonPath("$.data.HIAGENT.successRate").value(95))
                    .andExpect(jsonPath("$.data.OPENROUTER.totalCalls").value(200))
                    .andExpect(jsonPath("$.data.OPENROUTER.avgResponseTimeMs").value(120));

            verify(aiCallMetricsService).getTodayStats();
        }

        @Test
        @DisplayName("服务异常时返回错误信息")
        void getAiCallsRealtime_serviceException_returnsError() throws Exception {
            // Arrange
            when(aiCallMetricsService.getTodayStats())
                    .thenThrow(new RuntimeException("Redis connection failed"));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/ai-calls/realtime"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("获取AI调用实时统计失败"));
        }
    }

    // ========== GET /api/ops/monitoring/ai-calls/daily ==========

    @Nested
    @DisplayName("GET /api/ops/monitoring/ai-calls/daily")
    class AiCallsDailyTests {

        @Test
        @DisplayName("返回200及AI调用日统计列表")
        void getAiCallsDaily_returnsList() throws Exception {
            // Arrange
            AiCallStatsDaily stat1 = new AiCallStatsDaily();
            stat1.setId(1L);
            stat1.setStatDate(LocalDate.of(2026, 3, 20));
            stat1.setServiceName("HIAGENT");
            stat1.setTotalCalls(500);
            stat1.setSuccessCalls(490);
            stat1.setFailedCalls(10);

            AiCallStatsDaily stat2 = new AiCallStatsDaily();
            stat2.setId(2L);
            stat2.setStatDate(LocalDate.of(2026, 3, 21));
            stat2.setServiceName("OPENROUTER");
            stat2.setTotalCalls(300);
            stat2.setSuccessCalls(295);
            stat2.setFailedCalls(5);

            when(aiCallStatsDailyMapper.selectList(any())).thenReturn(Arrays.asList(stat1, stat2));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/ai-calls/daily")
                            .param("days", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].totalCalls").value(500))
                    .andExpect(jsonPath("$.data[0].serviceName").value("HIAGENT"))
                    .andExpect(jsonPath("$.data[1].totalCalls").value(300));

            verify(aiCallStatsDailyMapper).selectList(any());
        }

        @Test
        @DisplayName("默认参数 days=30 — 无参数时使用默认值")
        void getAiCallsDaily_defaultDays() throws Exception {
            // Arrange
            when(aiCallStatsDailyMapper.selectList(any())).thenReturn(Collections.emptyList());

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/ai-calls/daily"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));

            verify(aiCallStatsDailyMapper).selectList(any());
        }

        @Test
        @DisplayName("Mapper异常时返回错误信息")
        void getAiCallsDaily_mapperException_returnsError() throws Exception {
            // Arrange
            when(aiCallStatsDailyMapper.selectList(any()))
                    .thenThrow(new RuntimeException("DB error"));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/ai-calls/daily")
                            .param("days", "30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("获取AI调用日统计失败"));
        }
    }

    // ========== GET /api/ops/monitoring/ai-calls/recent ==========

    @Nested
    @DisplayName("GET /api/ops/monitoring/ai-calls/recent")
    class AiCallsRecentTests {

        @Test
        @DisplayName("返回200及最近AI调用日志列表")
        void getAiCallsRecent_returnsLogs() throws Exception {
            // Arrange
            AiCallLog log1 = new AiCallLog();
            log1.setId(1L);
            log1.setServiceName("HIAGENT");
            log1.setMethodName("chat");
            log1.setSuccess(true);
            log1.setResponseTimeMs(350L);
            log1.setTriggeredBy("student1");
            log1.setCreatedTime(LocalDateTime.of(2026, 3, 22, 14, 30, 0));

            AiCallLog log2 = new AiCallLog();
            log2.setId(2L);
            log2.setServiceName("OPENROUTER");
            log2.setMethodName("completion");
            log2.setSuccess(false);
            log2.setResponseTimeMs(5000L);
            log2.setErrorMessage("Timeout");
            log2.setTriggeredBy("teacher1");
            log2.setCreatedTime(LocalDateTime.of(2026, 3, 22, 14, 25, 0));

            when(aiCallLogMapper.selectList(any())).thenReturn(Arrays.asList(log1, log2));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/ai-calls/recent")
                            .param("limit", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].serviceName").value("HIAGENT"))
                    .andExpect(jsonPath("$.data[0].success").value(true))
                    .andExpect(jsonPath("$.data[1].serviceName").value("OPENROUTER"))
                    .andExpect(jsonPath("$.data[1].success").value(false))
                    .andExpect(jsonPath("$.data[1].errorMessage").value("Timeout"));

            verify(aiCallLogMapper).selectList(any());
        }

        @Test
        @DisplayName("默认参数 limit=50 — 无参数时使用默认值")
        void getAiCallsRecent_defaultLimit() throws Exception {
            // Arrange
            when(aiCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/ai-calls/recent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());

            verify(aiCallLogMapper).selectList(any());
        }

        @Test
        @DisplayName("Mapper异常时返回错误信息")
        void getAiCallsRecent_mapperException_returnsError() throws Exception {
            // Arrange
            when(aiCallLogMapper.selectList(any()))
                    .thenThrow(new RuntimeException("DB error"));

            // Act & Assert
            mockMvc.perform(get("/api/ops/monitoring/ai-calls/recent")
                            .param("limit", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("获取AI调用记录失败"));
        }
    }
}
