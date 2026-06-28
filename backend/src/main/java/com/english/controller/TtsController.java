package com.english.controller;

import com.english.dto.Result;
import com.english.service.TtsService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/api/tts")
public class TtsController {

    @Autowired
    private TtsService ttsService;

    @Value("${tts.cache-dir:/app/uploads/tts}")
    private String cacheDir;

    // 用户限流记录
    private final ConcurrentHashMap<String, long[]> rateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private final AtomicInteger cleanupCounter = new AtomicInteger(0);

    /**
     * 合成语音。
     * 请求体：{ text: "要合成的文本", voice: "发音人(可选)", speed: 1.0(可选) }
     * 返回：{ code: 200, data: { audioUrl: "/api/tts/audio/xxx.mp3" } }
     */
    @PostMapping("/synthesize")
    public Result<Map<String, String>> synthesize(
            @RequestBody TtsRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        // 验证参数
        if (request.getText() == null || request.getText().trim().isEmpty()) {
            return Result.error("合成文本不能为空");
        }
        if (request.getText().length() > 5000) {
            return Result.error("文本长度不能超过5000字符");
        }

        // 限流
        String userId = resolveUserId(authentication, httpRequest);
        if (isRateLimited(userId)) {
            return Result.error("语音合成请求过于频繁，请1分钟后重试");
        }

        try {
            String audioUrl = ttsService.synthesize(
                    request.getText(),
                    request.getVoice(),
                    request.getSpeed()
            );
            return Result.success(Map.of("audioUrl", audioUrl));
        } catch (Exception e) {
            log.error("TTS synthesis failed: user={} textLen={} err={}",
                    userId, request.getText().length(), e.getMessage());
            return Result.error("语音合成失败，请稍后重试");
        }
    }

    /**
     * 获取缓存的音频文件。
     */
    @GetMapping("/audio/{filename}")
    public ResponseEntity<Resource> getAudio(@PathVariable String filename) {
        // 安全验证：只允许 [a-f0-9]+.mp3 格式
        if (!filename.matches("^[a-f0-9]+\\.mp3$")) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = Paths.get(cacheDir, filename);

        // 防止路径遍历
        try {
            Path realPath = filePath.toRealPath();
            Path realCacheDir = Paths.get(cacheDir).toRealPath();
            if (!realPath.startsWith(realCacheDir)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }

        File file = filePath.toFile();
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400") // 缓存24小时
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentLength(file.length())
                .body(resource);
    }

    /**
     * 获取可用的发音人列表。
     */
    @GetMapping("/voices")
    public Result<List<Map<String, String>>> getVoices() {
        return Result.success(ttsService.getAvailableVoices());
    }

    // ────────── 限流逻辑（复用 TranslateController 模式） ──────────

    private boolean isRateLimited(String userId) {
        long now = System.currentTimeMillis();

        if (cleanupCounter.incrementAndGet() >= 100) {
            cleanupCounter.set(0);
            rateLimitMap.entrySet().removeIf(e -> now - e.getValue()[0] > 5 * 60_000L);
        }

        long[] record = rateLimitMap.computeIfAbsent(userId, k -> new long[]{now, 0});
        synchronized (record) {
            if (now - record[0] > 60_000L) {
                record[0] = now;
                record[1] = 1;
                return false;
            }
            record[1]++;
            return record[1] > MAX_REQUESTS_PER_MINUTE;
        }
    }

    private String resolveUserId(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            return "tts:user:" + authentication.getName();
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return "tts:ip:" + (ip != null ? ip : "unknown");
    }

    @Data
    public static class TtsRequest {
        private String text;
        private String voice;   // 可选，默认 alex
        private Double speed;   // 可选，默认 1.0
    }
}
