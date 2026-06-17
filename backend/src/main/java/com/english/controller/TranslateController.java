package com.english.controller;

import com.english.dto.Result;
import com.english.service.TencentTranslateService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/translate")
public class TranslateController {

    @Autowired
    private TencentTranslateService translateService;

    // 用户限流记录：用户名/IP → [窗口起始时间戳, 当前窗口计数]
    private final ConcurrentHashMap<String, long[]> rateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 50; // 每用户每分钟最多50次翻译

    // 单次翻译接口的输入上限（超过此值自动分块）
    private static final int SINGLE_TEXT_MAX = 10000;

    // 批量翻译每条子项的上限
    private static final int BATCH_ITEM_MAX = 2000;

    // 用于触发定期清理的调用计数器（每100次调用清理一次过期条目）
    private final AtomicInteger cleanupCounter = new AtomicInteger(0);

    /**
     * 滑动窗口限流检查。
     * 返回 true 表示已超限，应拒绝本次请求；false 表示允许。
     * 每 100 次调用会顺便清理超过 5 分钟的旧记录，防止内存泄漏。
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
     * 获取限流键：已登录用户使用用户名，匿名用户使用 IP 地址。
     */
    private String resolveUserId(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            return "user:" + authentication.getName();
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
        return "ip:" + (ip != null ? ip : "unknown");
    }

    /**
     * 翻译文本（自动检测语言）
     * 支持中英文互译，超过 1500 字自动分块处理，最大 10000 字。
     */
    @PostMapping("/auto")
    public Result<String> translateAuto(@RequestBody TranslateRequest request,
                                        Authentication authentication,
                                        HttpServletRequest httpRequest) {
        if (request.getText() == null || request.getText().trim().isEmpty()) {
            return Result.error("翻译文本不能为空");
        }
        if (request.getText().length() > SINGLE_TEXT_MAX) {
            return Result.error("翻译文本不能超过" + SINGLE_TEXT_MAX + "字");
        }
        String userId = resolveUserId(authentication, httpRequest);
        if (isRateLimited(userId)) {
            return Result.error("翻译请求过于频繁，请1分钟后重试（每分钟限50次）");
        }
        try {
            String result = translateService.translateAuto(request.getText());
            return Result.success(result);
        } catch (Exception e) {
            log.error("翻译调用外部服务失败 [auto] user={} textLen={} err={}",
                    userId, request.getText().length(), e.getMessage(), e);
            return Result.error("翻译服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 英译中
     * 支持长文本自动分块，最大 10000 字。
     */
    @PostMapping("/en-to-cn")
    public Result<String> translateEnToCn(@RequestBody TranslateRequest request,
                                          Authentication authentication,
                                          HttpServletRequest httpRequest) {
        if (request.getText() == null || request.getText().trim().isEmpty()) {
            return Result.error("翻译文本不能为空");
        }
        if (request.getText().length() > SINGLE_TEXT_MAX) {
            return Result.error("翻译文本不能超过" + SINGLE_TEXT_MAX + "字");
        }
        String userId = resolveUserId(authentication, httpRequest);
        if (isRateLimited(userId)) {
            return Result.error("翻译请求过于频繁，请1分钟后重试（每分钟限50次）");
        }
        try {
            String result = translateService.translateLong(request.getText(), "en", "zh");
            return Result.success(result);
        } catch (Exception e) {
            log.error("翻译调用外部服务失败 [en-to-cn] user={} textLen={} err={}",
                    userId, request.getText().length(), e.getMessage(), e);
            return Result.error("翻译服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 中译英
     * 支持长文本自动分块，最大 10000 字。
     */
    @PostMapping("/cn-to-en")
    public Result<String> translateCnToEn(@RequestBody TranslateRequest request,
                                          Authentication authentication,
                                          HttpServletRequest httpRequest) {
        if (request.getText() == null || request.getText().trim().isEmpty()) {
            return Result.error("翻译文本不能为空");
        }
        if (request.getText().length() > SINGLE_TEXT_MAX) {
            return Result.error("翻译文本不能超过" + SINGLE_TEXT_MAX + "字");
        }
        String userId = resolveUserId(authentication, httpRequest);
        if (isRateLimited(userId)) {
            return Result.error("翻译请求过于频繁，请1分钟后重试（每分钟限50次）");
        }
        try {
            String result = translateService.translateLong(request.getText(), "zh", "en");
            return Result.success(result);
        } catch (Exception e) {
            log.error("翻译调用外部服务失败 [cn-to-en] user={} textLen={} err={}",
                    userId, request.getText().length(), e.getMessage(), e);
            return Result.error("翻译服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 自定义语言翻译
     */
    // 支持的语言代码：平台默认只支持中英互译（en/zh/auto）。
    // 如需扩展其他语种，请在此白名单和 TencentTranslateService.mapLanguageCode() 中同步添加，
    // 并在 API 文档中注明所用翻译后端是否支持对应语言对。
    private static final java.util.Set<String> ALLOWED_LANG_CODES = java.util.Set.of(
            "en", "zh", "auto"
    );

    /**
     * 自定义语言翻译（目前仅支持 en/zh/auto）
     * 支持长文本自动分块，最大 10000 字。
     */
    @PostMapping("/custom")
    public Result<String> translate(@RequestBody CustomTranslateRequest request,
                                    Authentication authentication,
                                    HttpServletRequest httpRequest) {
        if (request.getText() == null || request.getText().trim().isEmpty()) {
            return Result.error("翻译文本不能为空");
        }
        if (request.getText().length() > SINGLE_TEXT_MAX) {
            return Result.error("翻译文本不能超过" + SINGLE_TEXT_MAX + "字");
        }
        // 验证源语言和目标语言
        if (request.getSource() == null || request.getSource().trim().isEmpty()) {
            return Result.error("源语言不能为空");
        }
        if (request.getTarget() == null || request.getTarget().trim().isEmpty()) {
            return Result.error("目标语言不能为空");
        }
        if (!ALLOWED_LANG_CODES.contains(request.getSource().toLowerCase())) {
            return Result.error("不支持的源语言代码");
        }
        if (!ALLOWED_LANG_CODES.contains(request.getTarget().toLowerCase())) {
            return Result.error("不支持的目标语言代码");
        }
        String userId = resolveUserId(authentication, httpRequest);
        if (isRateLimited(userId)) {
            return Result.error("翻译请求过于频繁，请1分钟后重试（每分钟限50次）");
        }
        try {
            String result = translateService.translateLong(
                request.getText(),
                request.getSource(),
                request.getTarget()
            );
            return Result.success(result);
        } catch (Exception e) {
            log.error("翻译调用外部服务失败 [custom] user={} source={} target={} textLen={} err={}",
                    userId, request.getSource(), request.getTarget(), request.getText().length(),
                    e.getMessage(), e);
            return Result.error("翻译服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 批量翻译（用于字幕整块翻译）
     * 请求体：{ texts: [...], direction: "en-to-zh" | "zh-to-en" }
     * 返回：翻译后的字符串数组
     */
    @PostMapping("/batch")
    public Result<List<String>> translateBatch(@RequestBody BatchTranslateRequest request,
                                              Authentication authentication,
                                              HttpServletRequest httpRequest) {
        if (request.getTexts() == null || request.getTexts().isEmpty()) {
            return Result.error("texts 不能为空");
        }
        if (request.getTexts().size() > 200) {
            return Result.error("批量翻译每次最多200条");
        }

        String direction = request.getDirection() == null ? "en-to-zh" : request.getDirection();
        if (!"en-to-zh".equals(direction) && !"zh-to-en".equals(direction)) {
            return Result.error("direction 只支持 en-to-zh 或 zh-to-en");
        }

        // 验证每条文本长度
        for (String text : request.getTexts()) {
            if (text == null || text.isEmpty()) {
                return Result.error("文本条目不能为空");
            }
            if (text.length() > BATCH_ITEM_MAX) {
                return Result.error("单条翻译文本不能超过" + BATCH_ITEM_MAX + "字");
            }
        }

        String userId = resolveUserId(authentication, httpRequest);
        // 批量翻译按 1 次请求计入限流（而非按条数），避免单次调用耗尽用户整分钟额度
        if (isRateLimited(userId)) {
            return Result.error("请求过于频繁，请稍后重试");
        }

        String src = direction.startsWith("en") ? "en" : "zh";
        String tgt = src.equals("en") ? "zh" : "en";

        List<String> texts = request.getTexts();
        List<String> results = new ArrayList<>(texts.size());

        int start = 0;
        int maxCharPerRequest = 1500;

        while (start < texts.size()) {
            int end = start;
            int charCount = 0;
            while (end < texts.size()) {
                String text = texts.get(end);
                int len = (text != null ? text.length() : 0) + 1;
                if (charCount + len > maxCharPerRequest && end > start) break;
                charCount += len;
                end++;
            }
            List<String> chunk = texts.subList(start, end);
            // BUGFIX: 确保 chunk 中没有 null 元素，否则会导致 NPE
            String joined = chunk.stream()
                    .map(t -> t != null ? t : "")
                    .collect(Collectors.joining("\n"));
            
            try {
                String translated = translateService.translate(joined, src, tgt);
                String[] lines = translated.split("\n", -1);

                // 如果返回行数一致，则一一对应
                if (lines.length == chunk.size()) {
                    for (int i = 0; i < chunk.size(); i++) {
                        results.add(lines[i].trim());
                    }
                } else {
                    // 如果不一致，兜底采用逐条翻译（虽然慢但保准）
                    for (int i = 0; i < chunk.size(); i++) {
                        // BUGFIX: null check before translate
                        String text = chunk.get(i);
                        if (text == null) {
                            results.add("");
                            continue;
                        }
                        try {
                            results.add(translateService.translate(text, src, tgt));
                        } catch (Exception e) {
                            log.warn("批量翻译逐条回退失败 [batch-item] user={} direction={} idx={} textLen={} err={}",
                                    userId, direction, i, text.length(), e.getMessage(), e);
                            results.add("");
                        }
                    }
                }
            } catch (Exception e) {
                // 彻底失败时该批次置空
                log.error("批量翻译整批调用外部服务失败 [batch] user={} direction={} chunkSize={} chunkChars={} err={}",
                        userId, direction, chunk.size(), charCount, e.getMessage(), e);
                for (int i = start; i < end; i++) results.add("");
            }
            start = end;
        }
        return Result.success(results);
    }

    @Data
    public static class TranslateRequest {
        private String text;
    }

    @Data
    public static class CustomTranslateRequest {
        private String text;
        private String source;  // 源语言：en, zh, auto等
        private String target;  // 目标语言：en, zh等
    }

    @Data
    public static class BatchTranslateRequest {
        private List<String> texts;
        private String direction;  // en-to-zh / zh-to-en
    }
}
