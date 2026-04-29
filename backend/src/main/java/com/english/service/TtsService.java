package com.english.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

/**
 * TTS 语音合成服务 — 基于硅基流动 (SiliconFlow) CosyVoice2 模型。
 *
 * 核心设计：
 * 1. 文件缓存：MD5(text + voice + speed) → /app/uploads/tts/{hash}.mp3
 * 2. 并发去重：ConcurrentHashMap<hash, CompletableFuture> — 同一文本只触发 1 次 API 调用
 * 3. OpenAI 兼容接口：POST /v1/audio/speech，返回 MP3 二进制
 */
@Service
public class TtsService {

    private static final Logger logger = LoggerFactory.getLogger(TtsService.class);

    @Value("${tts.api-url:https://api.siliconflow.cn/v1/audio/speech}")
    private String apiUrl;

    @Value("${tts.api-key:${AI_TRANSLATE_API_KEY:}}")
    private String apiKey;

    @Value("${tts.model:FunAudioLLM/CosyVoice2-0.5B}")
    private String defaultModel;

    @Value("${tts.voice:FunAudioLLM/CosyVoice2-0.5B:alex}")
    private String defaultVoice;

    @Value("${tts.cache-dir:/app/uploads/tts}")
    private String cacheDir;

    @Value("${tts.timeout:60000}")
    private int timeout;

    private RestTemplate restTemplate;
    private CloseableHttpClient httpClient;
    private PoolingHttpClientConnectionManager connManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 并发去重 map：同一文本的多个并发请求只触发 1 次 API 调用。
     * key = md5 hash, value = 正在执行的合成任务。
     * 任务完成后自动移除。
     */
    private final ConcurrentHashMap<String, CompletableFuture<String>> inflightRequests = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 创建缓存目录
        try {
            Files.createDirectories(Paths.get(cacheDir));
            logger.info("TTS cache directory ready: {}", cacheDir);
        } catch (IOException e) {
            logger.error("Failed to create TTS cache directory: {}", cacheDir, e);
        }

        // 初始化 HTTP 客户端（连接池）
        try {
            connManager = new PoolingHttpClientConnectionManager();
            connManager.setMaxTotal(20);
            connManager.setDefaultMaxPerRoute(10);

            httpClient = HttpClientBuilder.create()
                    .setConnectionManager(connManager)
                    .build();

            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            factory.setConnectTimeout(timeout);
            factory.setReadTimeout(timeout);
            factory.setConnectionRequestTimeout(timeout);
            restTemplate = new RestTemplate(factory);
        } catch (Exception e) {
            logger.warn("Failed to initialize pooled HTTP client, falling back to default: {}", e.getMessage());
            restTemplate = new RestTemplate();
        }

        logger.info("TtsService initialized — model={}, voice={}, apiUrl={}", defaultModel, defaultVoice, apiUrl);
    }

    @PreDestroy
    public void destroy() {
        if (httpClient != null) {
            try { httpClient.close(); } catch (Exception e) { /* ignore */ }
        }
        if (connManager != null) {
            try { connManager.close(); } catch (Exception e) { /* ignore */ }
        }
    }

    /**
     * 合成语音（带缓存 + 并发去重）。
     *
     * @param text  要合成的文本
     * @param voice 发音人（可选，默认 alex）
     * @param speed 语速（可选，默认 1.0）
     * @return 音频文件的访问 URL（相对路径），例如 "/api/tts/audio/abc123.mp3"
     */
    public String synthesize(String text, String voice, Double speed) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("合成文本不能为空");
        }

        // 设置默认值
        if (voice == null || voice.isEmpty()) {
            voice = defaultVoice;
        }
        if (speed == null) {
            speed = 1.0;
        }

        // 生成缓存 key
        String hash = md5(text.trim() + "|" + voice + "|" + speed);
        String fileName = hash + ".mp3";
        Path filePath = Paths.get(cacheDir, fileName);
        String audioUrl = "/api/tts/audio/" + fileName;

        // 1. 缓存命中 — 直接返回
        if (Files.exists(filePath)) {
            logger.debug("TTS cache hit: {}", hash);
            return audioUrl;
        }

        // 2. 并发去重：如果已有相同请求在处理中，等待其完成
        final String finalVoice = voice;
        final double finalSpeed = speed;

        CompletableFuture<String> future = inflightRequests.computeIfAbsent(hash, k ->
            CompletableFuture.supplyAsync(() -> {
                try {
                    // 双重检查：在异步线程中再次检查缓存
                    if (Files.exists(filePath)) {
                        return audioUrl;
                    }
                    callApiAndSave(text.trim(), finalVoice, finalSpeed, filePath);
                    return audioUrl;
                } catch (Exception e) {
                    throw new CompletionException(e);
                } finally {
                    inflightRequests.remove(hash);
                }
            })
        );

        try {
            return future.get(timeout + 5000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.error("TTS synthesis timeout for hash={}", hash);
            throw new RuntimeException("语音合成超时，请稍后重试");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("TTS synthesis failed: {}", cause != null ? cause.getMessage() : e.getMessage());
            throw new RuntimeException("语音合成失败：" + (cause != null ? cause.getMessage() : "未知错误"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("语音合成被中断");
        }
    }

    /**
     * 调用硅基流动 API 并保存 MP3 文件。
     */
    private void callApiAndSave(String text, String voice, double speed, Path filePath) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("TTS API Key 未配置，请在环境变量中设置 TTS_API_KEY 或 AI_TRANSLATE_API_KEY");
        }

        // 构造请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", defaultModel);
        body.put("input", text);
        body.put("voice", voice);
        body.put("response_format", "mp3");
        body.put("speed", speed);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        logger.info("TTS API call — voice={}, speed={}, textLen={}", voice, speed, text.length());

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    byte[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                byte[] audioData = response.getBody();

                // 验证返回的确实是音频数据（MP3 文件通常以 0xFF 0xFB 或 ID3 开头）
                if (audioData.length < 100) {
                    // 可能是错误响应（JSON 格式）
                    String bodyStr = new String(audioData);
                    logger.error("TTS API returned non-audio response: {}", bodyStr);
                    throw new RuntimeException("语音合成返回格式异常");
                }

                // 保存到文件
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, audioData);
                logger.info("TTS audio saved: {} ({} bytes)", filePath.getFileName(), audioData.length);
            } else {
                logger.error("TTS API returned status: {}", response.getStatusCode());
                throw new RuntimeException("语音合成服务返回异常状态: " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            logger.error("TTS API HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("语音合成服务调用失败: " + e.getStatusCode());
        } catch (IOException e) {
            logger.error("Failed to write TTS audio file: {}", e.getMessage());
            throw new RuntimeException("保存音频文件失败");
        }
    }

    /**
     * 获取可用的发音人列表。
     */
    public List<Map<String, String>> getAvailableVoices() {
        List<Map<String, String>> voices = new ArrayList<>();
        String prefix = defaultModel + ":";
        voices.add(Map.of("id", prefix + "alex", "name", "Alex", "gender", "male", "style", "沉稳"));
        voices.add(Map.of("id", prefix + "benjamin", "name", "Benjamin", "gender", "male", "style", "低沉"));
        voices.add(Map.of("id", prefix + "charles", "name", "Charles", "gender", "male", "style", "磁性"));
        voices.add(Map.of("id", prefix + "david", "name", "David", "gender", "male", "style", "开朗"));
        voices.add(Map.of("id", prefix + "anna", "name", "Anna", "gender", "female", "style", "沉稳"));
        voices.add(Map.of("id", prefix + "bella", "name", "Bella", "gender", "female", "style", "热情"));
        voices.add(Map.of("id", prefix + "claire", "name", "Claire", "gender", "female", "style", "温柔"));
        voices.add(Map.of("id", prefix + "diana", "name", "Diana", "gender", "female", "style", "开朗"));
        return voices;
    }

    /**
     * MD5 哈希。
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
