package com.english.controller;

import com.english.dto.Result;
import com.english.service.TencentAsrService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Iterator;
import java.util.Map;

@RestController
@RequestMapping("/api/asr")
@PreAuthorize("hasAnyRole('USER', 'STUDENT', 'TEACHER')")
public class AsrController {

    private static final Logger log = LoggerFactory.getLogger(AsrController.class);

    @Autowired
    private TencentAsrService asrService;

    // 用户限流记录：用户名/IP → [窗口起始时间戳, 当前窗口计数]
    private final ConcurrentHashMap<String, long[]> rateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 10; // 每用户每分钟最多10次语音识别

    // 用于触发定期清理的调用计数器（每100次调用清理一次过期条目）
    private final AtomicInteger cleanupCounter = new AtomicInteger(0);

    /**
     * 滑动窗口限流检查
     */
    private boolean isRateLimited(String userId) {
        long now = System.currentTimeMillis();

        // 每100次调用触发一次清理，移除超过5分钟未活跃的条目
        if (cleanupCounter.incrementAndGet() >= 100) {
            cleanupCounter.set(0);
            long now2 = System.currentTimeMillis();
            Iterator<Map.Entry<String, long[]>> it = rateLimitMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, long[]> entry = it.next();
                if (now2 - entry.getValue()[0] > 5 * 60_000L) {
                    it.remove();
                }
            }
        }

        long[] record = rateLimitMap.computeIfAbsent(userId, k -> new long[]{now, 0});
        synchronized (record) {
            if (now - record[0] > 60_000L) {
                // 超过1分钟，重置窗口
                record[0] = now;
                record[1] = 1;
                return false;
            }
            record[1]++;
            return record[1] > MAX_REQUESTS_PER_MINUTE;
        }
    }

    /**
     * 获取限流键：已登录用户使用用户名，匿名用户使用 IP 地址
     */
    private String resolveUserId(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            return "asr:user:" + authentication.getName();
        }
        // 匿名用户：取真实 IP（兼容反向代理）
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return "asr:ip:" + (ip != null ? ip : "unknown");
    }

    @PostMapping("/recognize")
    public Result<String> recognize(@RequestBody AsrRequest request,
                                   Authentication authentication,
                                   HttpServletRequest httpRequest) {
        if (request.getAudioData() == null || request.getAudioData().isEmpty()) {
            return Result.error("音频数据不能为空");
        }
        // Base64 编码后 10MB 音频约为 13.3MB 字符串；限制防止内存耗尽和计费攻击
        if (request.getAudioData().length() > 10 * 1024 * 1024) {
            return Result.error("音频数据超过大小限制（最大约10MB）");
        }

        // 限流检查
        String userId = resolveUserId(authentication, httpRequest);
        if (isRateLimited(userId)) {
            return Result.error("语音识别请求过于频繁，请1分钟后重试（每分钟限10次）");
        }

        try {
            String format = request.getFormat() == null ? "webm" : request.getFormat();
            String lang = request.getLang() == null ? "zh" : request.getLang();

            String result = asrService.recognize(request.getAudioData(), format, lang);
            return Result.success(result);
        } catch (Exception e) {
            log.debug("ASR recognition failed, format={}, lang={}",
                request.getFormat(), request.getLang(), e);
            return Result.error("语音识别失败，请稍后重试");
        }
    }

    @Data
    public static class AsrRequest {
        private String audioData; // Base64
        private String format;    // webm, mp3, etc.
        private String lang;      // zh, en
    }
}
