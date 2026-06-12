package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.AccessLog;
import com.english.entity.User;
import com.english.mapper.AccessLogMapper;
import com.english.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AccessLogQueryService 测试类
 *
 * 覆盖功能点:
 * 1. queryAccessLogs - 分页查询（包含姓名/昵称/账号名查询）
 * 2. getAccessLogStats - 访问统计
 * 3. getResponseTimeDistribution - 响应时间分布
 * 4. getLoginStats - 登录统计
 */
@ExtendWith(MockitoExtension.class)
class AccessLogQueryServiceTest {

    @Mock
    private AccessLogMapper accessLogMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AccessLogQueryService accessLogQueryService;

    private LocalDateTime testStartTime;
    private LocalDateTime testEndTime;

    @BeforeEach
    void setUp() {
        testStartTime = LocalDateTime.of(2026, 6, 1, 0, 0);
        testEndTime = LocalDateTime.of(2026, 6, 30, 23, 59);
    }

    // ========== queryAccessLogs 测试（11个用例） ==========

    @Test
    void testQueryAccessLogs_WithNoFilters() {
        // 无过滤条件，应返回所有日志
        // 验证分页参数正确传递
        accessLogQueryService.queryAccessLogs(1, 10, null, null, null, null, null);

        verify(accessLogMapper, times(1)).selectPage(any(), argThat(wrapper -> {
            // 验证QueryWrapper为空（无过滤条件）
            return wrapper.getCustomSqlSegment() == null || wrapper.getCustomSqlSegment().isEmpty();
        }));
    }

    @Test
    void testQueryAccessLogs_ByAccountNameOnly() {
        // 仅按账号名查询，无姓名匹配
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        accessLogQueryService.queryAccessLogs(1, 10, "teacher", null, null, null, null);

        // 验证执行了用户查询（姓名匹配）
        verify(userMapper, times(1)).selectList(any());
        // 验证最终执行了访问日志查询
        verify(accessLogMapper, times(1)).selectPage(any(), any());
    }

    @Test
    void testQueryAccessLogs_ByRealName() {
        // 按姓名查询（张老师 -> teacher）
        User teacher = new User();
        teacher.setUsername("teacher");
        teacher.setRealName("张老师");

        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(teacher));

        accessLogQueryService.queryAccessLogs(1, 10, "张老师", null, null, null, null);

        // 验证用户查询被调用
        verify(userMapper, times(1)).selectList(any());
        // 验证访问日志查询（应包含 IN 条件）
        verify(accessLogMapper, times(1)).selectPage(any(), any());
    }

    @Test
    void testQueryAccessLogs_ByNickname() {
        // 按昵称查询
        User user = new User();
        user.setUsername("student001");
        user.setNickname("小李");

        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(user));

        accessLogQueryService.queryAccessLogs(1, 10, "小李", null, null, null, null);

        verify(userMapper, times(1)).selectList(any());
        verify(accessLogMapper, times(1)).selectPage(any(), any());
    }

    @Test
    void testQueryAccessLogs_MultipleUsersMatched() {
        // 一个关键词匹配多个用户（如"李"匹配"李老师"和"小李"）
        User user1 = new User();
        user1.setUsername("teacher_li");
        user1.setRealName("李老师");

        User user2 = new User();
        user2.setUsername("student_li");
        user2.setNickname("小李");

        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(user1, user2));

        accessLogQueryService.queryAccessLogs(1, 10, "李", null, null, null, null);

        verify(userMapper, times(1)).selectList(any());
        verify(accessLogMapper, times(1)).selectPage(any(), argThat(wrapper -> {
            // 验证生成了 IN 条件（包含2个用户名）
            return wrapper.getCustomSqlSegment().contains("IN");
        }));
    }

    @Test
    void testQueryAccessLogs_ByUri() {
        accessLogQueryService.queryAccessLogs(1, 10, null, "/api/words", null, null, null);

        verify(accessLogMapper, times(1)).selectPage(any(), argThat(wrapper ->
            wrapper.getCustomSqlSegment().contains("uri")
        ));
    }

    @Test
    void testQueryAccessLogs_ByStatusCode() {
        accessLogQueryService.queryAccessLogs(1, 10, null, null, 404, null, null);

        verify(accessLogMapper, times(1)).selectPage(any(), argThat(wrapper ->
            wrapper.getCustomSqlSegment().contains("status_code")
        ));
    }

    @Test
    void testQueryAccessLogs_ByTimeRange() {
        accessLogQueryService.queryAccessLogs(1, 10, null, null, null, testStartTime, testEndTime);

        verify(accessLogMapper, times(1)).selectPage(any(), argThat(wrapper -> {
            String sql = wrapper.getCustomSqlSegment();
            return sql.contains("created_time") && sql.contains(">=") && sql.contains("<=");
        }));
    }

    @Test
    void testQueryAccessLogs_CombinedFilters() {
        // 组合过滤：姓名+URI+状态码+时间
        User teacher = new User();
        teacher.setUsername("teacher");
        teacher.setRealName("张老师");
        when(userMapper.selectList(any())).thenReturn(Arrays.asList(teacher));

        accessLogQueryService.queryAccessLogs(
            1, 20,
            "张老师",
            "/api/words",
            200,
            testStartTime,
            testEndTime
        );

        verify(userMapper, times(1)).selectList(any());
        verify(accessLogMapper, times(1)).selectPage(any(), argThat(wrapper -> {
            String sql = wrapper.getCustomSqlSegment();
            return sql.contains("username") &&
                   sql.contains("uri") &&
                   sql.contains("status_code") &&
                   sql.contains("created_time");
        }));
    }

    @Test
    void testQueryAccessLogs_PaginationParameters() {
        // 验证分页参数正确传递
        accessLogQueryService.queryAccessLogs(3, 25, null, null, null, null, null);

        verify(accessLogMapper, times(1)).selectPage(argThat(page ->
            page.getCurrent() == 3 && page.getSize() == 25
        ), any());
    }

    @Test
    void testQueryAccessLogs_EmptyUsernameIgnored() {
        // 空字符串或空白字符串应被忽略
        accessLogQueryService.queryAccessLogs(1, 10, "   ", null, null, null, null);

        // 不应调用userMapper（空字符串被trim后为空）
        verify(userMapper, never()).selectList(any());
        verify(accessLogMapper, times(1)).selectPage(any(), argThat(wrapper ->
            wrapper.getCustomSqlSegment() == null || !wrapper.getCustomSqlSegment().contains("username")
        ));
    }

    // ========== getAccessLogStats 测试（6个用例） ==========

    @Test
    void testGetAccessLogStats_WithTimeRange() {
        when(accessLogMapper.selectCount(any())).thenReturn(1000L, 45L);
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> stats = accessLogQueryService.getAccessLogStats(testStartTime, testEndTime);

        assertThat(stats).containsKeys("totalRequests", "errorCount", "errorRate");
    }

    @Test
    void testGetAccessLogStats_WithoutTimeRange() {
        when(accessLogMapper.selectCount(any())).thenReturn(5000L, 123L);
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> stats = accessLogQueryService.getAccessLogStats(null, null);

        assertThat(stats.get("totalRequests")).isEqualTo(5000L);
        assertThat(stats.get("errorCount")).isEqualTo(123L);
    }

    @Test
    void testGetAccessLogStats_ErrorRateCalculation() {
        when(accessLogMapper.selectCount(any())).thenReturn(1000L, 50L);
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> stats = accessLogQueryService.getAccessLogStats(null, null);

        String errorRate = (String) stats.get("errorRate");
        assertThat(errorRate).isEqualTo("5.00");
    }

    @Test
    void testGetAccessLogStats_ZeroRequests() {
        when(accessLogMapper.selectCount(any())).thenReturn(0L, 0L);
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> stats = accessLogQueryService.getAccessLogStats(null, null);

        assertThat(stats.get("errorRate")).isEqualTo("0.00");
    }

    @Test
    void testGetAccessLogStats_OnlyStartTime() {
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        accessLogQueryService.getAccessLogStats(testStartTime, null);
        verify(accessLogMapper, atLeast(1)).selectCount(any());
    }

    @Test
    void testGetAccessLogStats_OnlyEndTime() {
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        accessLogQueryService.getAccessLogStats(null, testEndTime);
        verify(accessLogMapper, atLeast(1)).selectCount(any());
    }

    // ========== getResponseTimeDistribution 测试（5个用例） ==========

    @Test
    void testGetResponseTimeDistribution_ReturnsAllKeys() {
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> distribution = accessLogQueryService.getResponseTimeDistribution(null, null);

        assertThat(distribution).containsKeys("under50", "s50to100", "s100to200", "s200to500", "s500to2000", "over2000", "p50", "p90", "p95", "p99");
    }

    @Test
    void testGetResponseTimeDistribution_WithTimeRange() {
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        accessLogQueryService.getResponseTimeDistribution(testStartTime, testEndTime);

        verify(accessLogMapper, times(1)).selectList(any());
    }

        @Test
    void testGetResponseTimeDistribution_BucketClassification() {
        AccessLog log1 = createAccessLog(50L);
        AccessLog log2 = createAccessLog(150L);
        AccessLog log3 = createAccessLog(350L);
        AccessLog log4 = createAccessLog(750L);
        AccessLog log5 = createAccessLog(1500L);
        AccessLog log6 = createAccessLog(3000L);
        when(accessLogMapper.selectList(any())).thenReturn(
            Arrays.asList(log1, log2, log3, log4, log5, log6)
        );

        Map<String, Object> distribution = accessLogQueryService.getResponseTimeDistribution(null, null);

        assertThat(distribution.get("under50")).isEqualTo(1);
        assertThat(distribution.get("s50to100")).isEqualTo(0);
        assertThat(distribution.get("s100to200")).isEqualTo(1);
        assertThat(distribution.get("s200to500")).isEqualTo(1);
        assertThat(distribution.get("s500to2000")).isEqualTo(2);
        assertThat(distribution.get("over2000")).isEqualTo(1);
    }

    @Test
    void testGetResponseTimeDistribution_P99Calculation() {
        // 模拟100条日志（1ms ~ 1000ms）
        List<AccessLog> logs = new java.util.ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            logs.add(createAccessLog((long) i * 10));
        }
        when(accessLogMapper.selectList(any())).thenReturn(logs);

        Map<String, Object> distribution = accessLogQueryService.getResponseTimeDistribution(null, null);

        long p99 = (long) distribution.get("p99");
        assertThat(p99).isEqualTo(990L); // 第99个值（索引98）= 990ms
    }

    @Test
    void testGetResponseTimeDistribution_EmptyData() {
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> distribution = accessLogQueryService.getResponseTimeDistribution(null, null);

        assertThat(distribution.get("p99")).isEqualTo(0L);
        assertThat(distribution.get("under50")).isEqualTo(0);
    }

    // ========== getLoginStats 测试（4个用例） ==========

    @Test
    void testGetLoginStats_WithTimeRange() {
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> stats = accessLogQueryService.getLoginStats(testStartTime, testEndTime, 50);

        assertThat(stats).containsKeys("loginSuccess", "loginFail", "logoutCount", "records");
        verify(accessLogMapper, times(1)).selectList(any());
    }

    @Test
    void testGetLoginStats_SuccessAndFailCounts() {
        AccessLog successLog = new AccessLog();
        successLog.setUri("/api/auth/login");
        successLog.setStatusCode(200);
        AccessLog failLog = new AccessLog();
        failLog.setUri("/api/auth/login");
        failLog.setStatusCode(401);
        when(accessLogMapper.selectList(any())).thenReturn(Arrays.asList(successLog, failLog));

        Map<String, Object> stats = accessLogQueryService.getLoginStats(null, null, 10);

        assertThat(stats.get("loginSuccess")).isEqualTo(1L);
        assertThat(stats.get("loginFail")).isEqualTo(1L);
    }

    @Test
    void testGetLoginStats_ZeroLogins() {
        when(accessLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> stats = accessLogQueryService.getLoginStats(null, null, 10);

        assertThat(stats.get("loginSuccess")).isEqualTo(0L);
        assertThat(stats.get("loginFail")).isEqualTo(0L);
        assertThat(stats.get("logoutCount")).isEqualTo(0L);
    }

    @Test
    void testGetLoginStats_AllLoginsFailed() {
        AccessLog failLog = new AccessLog();
        failLog.setUri("/api/auth/login");
        failLog.setStatusCode(500);
        when(accessLogMapper.selectList(any())).thenReturn(Arrays.asList(failLog));

        Map<String, Object> stats = accessLogQueryService.getLoginStats(null, null, 10);

        assertThat(stats.get("loginSuccess")).isEqualTo(0L);
        assertThat(stats.get("loginFail")).isEqualTo(1L);
    }

    // ========== 辅助方法 ==========

    private AccessLog createAccessLog(Long responseTimeMs) {
        AccessLog log = new AccessLog();
        log.setResponseTimeMs(responseTimeMs);
        log.setCreatedTime(LocalDateTime.now());
        log.setMethod("GET");
        log.setUri("/api/test");
        log.setStatusCode(200);
        return log;
    }
}
