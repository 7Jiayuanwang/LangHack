package com.english.service;

import com.english.entity.AiProvider;
import com.english.exception.BusinessException;
import com.english.service.impl.AiCallMetricsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class TencentTranslateService {

    private static final Logger logger = LoggerFactory.getLogger(TencentTranslateService.class);

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired(required = false)
    private AiProviderService aiProviderService;

    @Value("${ai.translate.api-url:https://api.siliconflow.cn/v1/chat/completions}")
    private String defaultApiUrl;

    @Value("${ai.translate.api-key:}")
    private String defaultApiKey;

    @Value("${ai.translate.model:Qwen/Qwen2.5-7B-Instruct}")
    private String defaultModel;

    @Value("${ai.translate.timeout:30000}")
    private int defaultTimeout;

    @Autowired(required = false)
    private AiCallMetricsService aiCallMetricsService;

    /** Concurrency limiter: max 10 concurrent translation requests */
    private final Semaphore semaphore = new Semaphore(10);

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CloseableHttpClient httpClient;
    private PoolingHttpClientConnectionManager connManager;

    @PostConstruct
    public void init() {
        try {
            connManager = new PoolingHttpClientConnectionManager();
            connManager.setMaxTotal(50);
            connManager.setDefaultMaxPerRoute(10);

            httpClient = HttpClientBuilder.create()
                    .setConnectionManager(connManager)
                    .build();

            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            factory.setConnectTimeout(defaultTimeout);
            factory.setReadTimeout(defaultTimeout);
            factory.setConnectionRequestTimeout(defaultTimeout);
            restTemplate = new RestTemplate(factory);
        } catch (Exception e) {
            logger.warn("Failed to initialize HTTP client with connection pooling, falling back to default: {}",
                    e.getMessage());
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
            factory.setConnectTimeout(defaultTimeout);
            factory.setReadTimeout(defaultTimeout);
            factory.setConnectionRequestTimeout(defaultTimeout);
            restTemplate = new RestTemplate(factory);
        }

        logger.info("TranslateService initialized (OpenAI-compatible API mode)");
    }

    @PreDestroy
    public void destroy() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception e) {
                logger.warn("Error closing HTTP client: {}", e.getMessage());
            }
        }
        if (connManager != null) {
            try {
                connManager.close();
            } catch (Exception e) {
                logger.warn("Error closing connection manager: {}", e.getMessage());
            }
        }
    }

    /**
     * Resolve config: ai_provider table (weighted round-robin) > system_config > application.yml
     */
    private Map<String, String> resolveConfig() {
        // 1. 优先从多服务商表加权轮询
        if (aiProviderService != null) {
            AiProvider provider = aiProviderService.pick("translate");
            if (provider != null) {
                Map<String, String> resolved = new HashMap<>();
                String rawUrl = provider.getApiUrl() != null ? provider.getApiUrl() : defaultApiUrl;
                resolved.put("api_url", normalizeApiUrl(rawUrl));
                resolved.put("api_key", provider.getApiKey());
                resolved.put("model", provider.getModel() != null ? provider.getModel() : defaultModel);
                resolved.put("enabled", "true");
                resolved.put("provider_name", provider.getName());
                return resolved;
            }
        }
        // 2. 回退：system_config 表 > application.yml 默认值
        Map<String, String> dbConfig = systemConfigService.getByGroup("ai_translate");
        String apiUrl = getOrDefault(dbConfig, "api_url", defaultApiUrl);
        String apiKey = getOrDefault(dbConfig, "api_key", defaultApiKey);
        String model = getOrDefault(dbConfig, "model", defaultModel);
        String enabled = getOrDefault(dbConfig, "enabled", "true");

        Map<String, String> resolved = new HashMap<>();
        resolved.put("api_url", normalizeApiUrl(apiUrl));
        resolved.put("api_key", apiKey);
        resolved.put("model", model);
        resolved.put("enabled", enabled);
        return resolved;
    }

    /**
     * Ensure the URL points to the chat completions endpoint.
     * Accepts both bare base URLs (e.g. https://api.siliconflow.cn)
     * and full endpoint URLs.
     */
    private String normalizeApiUrl(String url) {
        if (url == null || url.isEmpty()) return defaultApiUrl;
        String trimmed = url.stripTrailing();
        if (trimmed.contains("/chat/completions")) return trimmed;
        // Remove trailing slash before appending
        if (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed + "/v1/chat/completions";
    }

    private String getOrDefault(Map<String, String> dbConfig, String key, String fallback) {
        String value = dbConfig.get(key);
        return (value != null && !value.isEmpty()) ? value : fallback;
    }

    /**
     * Translate text via OpenAI-compatible Chat Completions API
     */
    @CircuitBreaker(name = "tencent-translate", fallbackMethod = "translateFallback")
    public String translate(String text, String source, String target) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        Map<String, String> config = resolveConfig();

        // Check if service is enabled
        if (!"true".equalsIgnoreCase(config.get("enabled"))) {
            throw new BusinessException("翻译服务已禁用");
        }

        // Check API key
        String apiKey = config.get("api_key");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new BusinessException("翻译服务未配置 API Key，请在运维面板配置");
        }

        // Acquire semaphore with timeout
        try {
            if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                logger.warn("Semaphore acquire timeout, available permits: {}", semaphore.availablePermits());
                throw new BusinessException("翻译服务繁忙，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("翻译请求被中断，请重试");
        }

        long startTime = aiCallMetricsService != null ? aiCallMetricsService.startTimer() : 0;
        String user = AiCallMetricsService.getCurrentUser();

        try {
            if (source == null || source.isEmpty()) {
                source = "auto";
            }
            if (target == null || target.isEmpty()) {
                target = "zh";
            }

            String sourceLang = mapLanguageCode(source);
            String targetLang = mapLanguageCode(target);

            String prompt = String.format(
                    "将以下%s翻译为%s，只返回翻译结果，不要附加任何解释。" +
                    "不要使用 LaTeX 数学标记（如 \\(...\\) 或 $$...$$），用普通文本表示数学公式（如 2^t 写作 2的t次方）:\n%s",
                    sourceLang, targetLang, text);

            // Build OpenAI Chat Completions request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(message);

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.get("model"));
            body.put("messages", messages);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            logger.debug("Calling translate API - source: {}, target: {}, textLength: {}, model: {}",
                    source, target, text.length(), config.get("model"));

            ResponseEntity<String> response = restTemplate.exchange(
                    config.get("api_url"),
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode msgNode = choices.get(0).path("message");
                    if (msgNode.has("content")) {
                        String result = msgNode.get("content").asText().trim();
                        logger.debug("Translation succeeded, result length: {}", result.length());
                        if (aiCallMetricsService != null) {
                            aiCallMetricsService.recordSuccess("TENCENT_TRANSLATE", "translate",
                                    config.get("model"), startTime, user);
                        }
                        return result;
                    }
                }
            }

            logger.error("Translate API returned invalid response format");
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("TENCENT_TRANSLATE", "translate", startTime,
                        "Invalid response format", user);
            }
            throw new BusinessException("翻译失败，请稍后重试");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Translation error: {}", e.getMessage());
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("TENCENT_TRANSLATE", "translate", startTime,
                        e.getMessage(), user);
            }
            throw new BusinessException("翻译失败，请稍后重试");
        } finally {
            semaphore.release();
        }
    }

    private String translateFallback(String text, String source, String target, Exception e) {
        logger.warn("Translate circuit breaker triggered: {}", e.getMessage());
        throw new BusinessException("翻译服务暂时不可用，请稍后重试");
    }

    /**
     * Map language codes to readable Chinese names for the prompt
     */
    private String mapLanguageCode(String code) {
        switch (code.toLowerCase()) {
            case "en":
                return "英语";
            case "zh":
                return "中文";
            case "auto":
                return "原文";
            default:
                return code;
        }
    }

    public String translateEnToCn(String text) {
        return translate(text, "en", "zh");
    }

    public String translateCnToEn(String text) {
        return translate(text, "zh", "en");
    }

    public String translateAuto(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.matches(".*[\u4e00-\u9fa5]+.*")) {
            return translate(text, "zh", "en");
        } else {
            return translate(text, "en", "zh");
        }
    }

    /**
     * Test translation connectivity (used by OPS panel)
     */
    public String testTranslation(String testText) {
        if (testText == null || testText.isEmpty()) {
            testText = "Hello, world!";
        }
        return translate(testText, "en", "zh");
    }
}
