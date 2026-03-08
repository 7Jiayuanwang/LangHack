package com.english.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 TranslateController 滑动窗口限流逻辑。
 * 使用反射直接调用私有方法，隔离限流逻辑与 HTTP 层。
 */
public class TranslateControllerRateLimitTest {

    private TranslateController controller;
    private Method isRateLimitedMethod;
    private Field rateLimitMapField;
    private Field cleanupCounterField;

    @BeforeEach
    public void setUp() throws Exception {
        controller = new TranslateController();
        // 注入一个空的 translateService mock（TranslateController 需要它完成初始化）
        Field serviceField = TranslateController.class.getDeclaredField("translateService");
        serviceField.setAccessible(true);
        serviceField.set(controller, mock(com.english.service.TencentTranslateService.class));

        isRateLimitedMethod = TranslateController.class.getDeclaredMethod("isRateLimited", String.class);
        isRateLimitedMethod.setAccessible(true);

        rateLimitMapField = TranslateController.class.getDeclaredField("rateLimitMap");
        rateLimitMapField.setAccessible(true);

        cleanupCounterField = TranslateController.class.getDeclaredField("cleanupCounter");
        cleanupCounterField.setAccessible(true);
    }

    private boolean callIsRateLimited(String userId) throws Exception {
        return (boolean) isRateLimitedMethod.invoke(controller, userId);
    }

    @Test
    public void firstRequest_shouldNotBeLimited() throws Exception {
        assertFalse(callIsRateLimited("user:alice"), "首次请求不应被限流");
    }

    @Test
    public void requestsWithinLimit_shouldAllPass() throws Exception {
        String userId = "user:bob";
        // 50 次请求应全部通过
        for (int i = 0; i < 50; i++) {
            assertFalse(callIsRateLimited(userId),
                    "第 " + (i + 1) + " 次请求（≤50）应通过");
        }
    }

    @Test
    public void fiftyFirstRequest_shouldBeLimited() throws Exception {
        String userId = "user:charlie";
        for (int i = 0; i < 50; i++) {
            callIsRateLimited(userId); // 消耗配额
        }
        assertTrue(callIsRateLimited(userId), "第 51 次请求应被限流");
    }

    @Test
    public void differentUsers_shouldHaveIndependentLimits() throws Exception {
        // alice 已达上限
        String alice = "user:alice";
        for (int i = 0; i < 51; i++) {
            callIsRateLimited(alice);
        }
        assertTrue(callIsRateLimited(alice), "alice 超限后应被拒绝");

        // bob 是新用户，不应受影响
        assertFalse(callIsRateLimited("user:bob"), "bob 是独立用户，不应受 alice 的限流影响");
    }

    @Test
    public void windowReset_afterExpiry_shouldAllowRequests() throws Exception {
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, long[]> rateLimitMap =
                (ConcurrentHashMap<String, long[]>) rateLimitMapField.get(controller);

        String userId = "user:dave";
        // 模拟 dave 已经在 61 秒前开始了一个窗口，并且计数为 50（已到上限）
        long pastWindowStart = System.currentTimeMillis() - 61_000L;
        rateLimitMap.put(userId, new long[]{pastWindowStart, 50});

        // 窗口过期后的第一次请求应触发重置，计数从 1 开始
        assertFalse(callIsRateLimited(userId), "窗口过期后第一次请求应重置计数并允许通过");
    }

    @Test
    public void cleanup_shouldRemoveStaleEntries() throws Exception {
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, long[]> rateLimitMap =
                (ConcurrentHashMap<String, long[]>) rateLimitMapField.get(controller);

        // 插入一个超过 5 分钟的过期条目
        String staleUser = "user:stale";
        long staleTimestamp = System.currentTimeMillis() - 6 * 60_000L;
        rateLimitMap.put(staleUser, new long[]{staleTimestamp, 1});

        // 设置 cleanupCounter 接近触发阈值（99），使下一次调用触发清理
        Object cleanupCounterObj = cleanupCounterField.get(controller);
        if (cleanupCounterObj instanceof java.util.concurrent.atomic.AtomicInteger) {
            ((java.util.concurrent.atomic.AtomicInteger) cleanupCounterObj).set(99);
        }

        // 触发任意用户请求，顺带执行清理
        callIsRateLimited("user:trigger");

        assertFalse(rateLimitMap.containsKey(staleUser),
                "超过 5 分钟的过期条目应在清理时被删除，防止内存泄漏");
    }

    @Test
    public void anonymousUserKey_shouldUseIPAddress() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        // 通过反射调用 resolveUserId
        try {
            Method resolveUserId = TranslateController.class
                    .getDeclaredMethod("resolveUserId", Authentication.class, HttpServletRequest.class);
            resolveUserId.setAccessible(true);
            String key = (String) resolveUserId.invoke(controller, auth, request);
            assertTrue(key.contains("192.168.1.100"), "匿名用户应使用 IP 地址作为限流键");
        } catch (Exception e) {
            fail("resolveUserId 反射调用失败: " + e.getMessage());
        }
    }
}
