package com.english.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * B 站 API 代理，解决浏览器直连 api.bilibili.com 的 CORS/DNS 问题。
 *
 * 端点：
 *   GET /api/bilibili/view?bvid=BVxxx           → 视频信息（含 cid）
 *   GET /api/bilibili/player?bvid=BVxxx&cid=xxx → 字幕列表
 *   GET /api/bilibili/subtitle-file?url=xxx      → 字幕 JSON 文件
 */
@RestController
@RequestMapping("/api/bilibili")
public class BilibiliProxyController {

    private static final Pattern BVID_PATTERN = Pattern.compile("^BV[a-zA-Z0-9]{10}$");
    private static final Pattern CID_PATTERN  = Pattern.compile("^\\d{1,20}$");
    // 只允许 *.hdslb.com/bfs/subtitle/ 下纯字母数字路径，拒绝 .. 等路径遍历
    private static final Pattern SUBTITLE_URL_PATTERN = Pattern.compile(
        "^https://[a-z0-9-]+\\.hdslb\\.com/bfs/subtitle/[a-zA-Z0-9_/\\-]+\\.json(\\?.*)?$"
    );

    // 配置：不对 4xx/5xx 抛异常，直接透传 B 站原始响应
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BilibiliProxyController() {
        // 配置超时：5s 连接，10s 读取，防止 B 站无响应时线程永久阻塞
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        restTemplate = new RestTemplate(factory);
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                // 不抛异常，让调用方拿到原始 body
            }
        });
    }

    private HttpEntity<Void> buildRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        headers.set("Referer", "https://www.bilibili.com/");
        return new HttpEntity<>(headers);
    }

    private ResponseEntity<String> error(String msg) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("code", -1, "message", msg));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"code\":-1,\"message\":\"proxy error\"}");
        }
    }

    /** 获取视频信息（bvid → cid） */
    @GetMapping("/view")
    public ResponseEntity<String> view(@RequestParam String bvid) {
        if (bvid == null || !BVID_PATTERN.matcher(bvid).matches()) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":-1,\"message\":\"bvid 格式非法\"}");
        }
        try {
            String url = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, buildRequest(), String.class);
            String body = resp.getBody();
            if (body == null || body.isBlank()) return error("B站返回空响应");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (Exception e) {
            return error("请求B站API失败");
        }
    }

    /** 获取字幕列表 */
    @GetMapping("/player")
    public ResponseEntity<String> player(@RequestParam String bvid, @RequestParam String cid) {
        if (bvid == null || !BVID_PATTERN.matcher(bvid).matches()) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":-1,\"message\":\"bvid 格式非法\"}");
        }
        if (cid == null || !CID_PATTERN.matcher(cid).matches()) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":-1,\"message\":\"cid 格式非法\"}");
        }
        try {
            String url = "https://api.bilibili.com/x/player/v2?cid=" + cid + "&bvid=" + bvid;
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, buildRequest(), String.class);
            String body = resp.getBody();
            if (body == null || body.isBlank()) return error("字幕列表返回空响应");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (Exception e) {
            return error("请求字幕列表失败");
        }
    }

    /**
     * 代理字幕 JSON 文件。
     * 防 SSRF：只允许 *.hdslb.com 域名（B 站字幕 CDN），且路径不含路径遍历字符。
     */
    @GetMapping("/subtitle-file")
    public ResponseEntity<String> subtitleFile(@RequestParam String url) {
        if (url == null || url.contains("..") || url.contains("//") ||
            !SUBTITLE_URL_PATTERN.matcher(url).matches()) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":-1,\"message\":\"非法字幕 URL\"}");
        }
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                URI.create(url), HttpMethod.GET, buildRequest(), String.class);
            String body = resp.getBody();
            if (body == null || body.isBlank()) return error("字幕文件返回空响应");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (Exception e) {
            return error("下载字幕文件失败");
        }
    }
}
