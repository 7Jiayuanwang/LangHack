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
                String protocol = provider.getProtocol() != null ? provider.getProtocol() : "openai_compatible";
                resolved.put("protocol", protocol);
                resolved.put("api_url", provider.getApiUrl());
                resolved.put("api_key", provider.getApiKey());
                resolved.put("app_id", provider.getAppId());
                resolved.put("model", provider.getModel() != null ? provider.getModel() : defaultModel);
                resolved.put("enabled", "true");
                resolved.put("provider_name", provider.getName());
                return resolved;
            }
        }
        // 2. 回退：system_config 表 > application.yml 默认值
        Map<String, String> dbConfig = systemConfigService.getByGroup("ai_translate");
        Map<String, String> resolved = new HashMap<>();
        resolved.put("protocol", "openai_compatible");
        resolved.put("api_url", normalizeApiUrl(getOrDefault(dbConfig, "api_url", defaultApiUrl)));
        resolved.put("api_key", getOrDefault(dbConfig, "api_key", defaultApiKey));
        resolved.put("model", getOrDefault(dbConfig, "model", defaultModel));
        resolved.put("enabled", getOrDefault(dbConfig, "enabled", "true"));
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

        String protocol = config.getOrDefault("protocol", "openai_compatible");
        if ("hiagent".equals(protocol)) {
            return translateViaHiAgent(config, text, source, target, startTime, user);
        }

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

            logger.debug("Calling translate API - source: {}, target: {}, textLength: {}, model: ",
                    source, target, text.length(), config.get("model"));

            ResponseEntity<String> response = restTemplate.exchange(
                    normalizeApiUrl(config.get("api_url")),
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

            // 记录原始响应片段和 HTTP 状态码，便于诊断上游返回格式变化 / 鉴权失败
            String bodyPreview = truncateForLog(response.getBody());
            logger.error("翻译上游返回格式无效 apiUrl={} model={} status={} bodyPreview={}",
                    config.get("api_url"), config.get("model"),
                    response.getStatusCode(), bodyPreview);
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("TENCENT_TRANSLATE", "translate", startTime,
                        "Invalid response format: status=" + response.getStatusCode(), user);
            }
            throw new BusinessException("翻译失败，请稍后重试");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 保留堆栈以诊断网络/超时/JSON 解析等问题
            logger.error("翻译调用外部 API 异常 apiUrl={} model={} source={} target={} err={}",
                    config.get("api_url"), config.get("model"), source, target,
                    e.getMessage(), e);
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

    /**
     * HiAgent 协议翻译实现
     */
    private String translateViaHiAgent(Map<String, String> config, String text, String source, String target,
                                       long startTime, String user) {
        String apiUrl = config.get("api_url");
        String apiKey = config.get("api_key");
        String appId = config.get("app_id");

        if (appId == null || appId.isEmpty()) {
            throw new BusinessException("HiAgent 协议需要配置 App ID");
        }

        try {
            String sourceLang = mapLanguageCode(source);
            String targetLang = mapLanguageCode(target);
            String prompt = String.format("将以下%s翻译为%s，只返回翻译结果:\n%s", sourceLang, targetLang, text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Apikey", apiKey);
            headers.set("Appid", appId);

            // Step 1: create_conversation
            Map<String, Object> createBody = new HashMap<>();
            createBody.put("UserID", user != null ? user : "system");
            createBody.put("Inputs", new HashMap<>());

            ResponseEntity<String> createResp = restTemplate.exchange(
                    apiUrl + "/create_conversation", HttpMethod.POST,
                    new HttpEntity<>(createBody, headers), String.class);

            JsonNode createRoot = objectMapper.readTree(createResp.getBody());
            String conversationId = createRoot.path("Conversation").path("AppConversationID").asText();

            // Step 2: chat_query_v2
            Map<String, Object> chatBody = new HashMap<>();
            chatBody.put("AppConversationID", conversationId);
            chatBody.put("Query", prompt);
            chatBody.put("UserID", user != null ? user : "system");
            chatBody.put("ResponseMode", "blocking");

            ResponseEntity<String> chatResp = restTemplate.exchange(
                    apiUrl + "/chat_query_v2", HttpMethod.POST,
                    new HttpEntity<>(chatBody, headers), String.class);

            JsonNode chatRoot = objectMapper.readTree(chatResp.getBody());
            String answer = chatRoot.path("answer").asText("");
            if (answer.isEmpty()) answer = chatRoot.path("Answer").asText("");

            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordSuccess("HIAGENT_TRANSLATE", "translate", "hiagent", startTime, user);
            }
            return answer.trim();
        } catch (Exception e) {
            logger.error("HiAgent 翻译失败: {}", e.getMessage(), e);
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("HIAGENT_TRANSLATE", "translate", startTime, e.getMessage(), user);
            }
            throw new BusinessException("翻译失败，请稍后重试");
        } finally {
            semaphore.release();
        }
    }

    /**
     * 截断响应体片段用于日志，避免泄漏过长内容或敏感 key 回显
     */
    private String truncateForLog(String body) {
        if (body == null) return "null";
        return body.length() > 200 ? body.substring(0, 200) + "...(truncated)" : body;
    }
}
