package com.english.service.impl;

import com.english.config.OpenRouterConfig;
import com.english.exception.BusinessException;
import com.english.service.OpenRouterService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * OpenRouter AI service implementation
 */
@Slf4j
@Service
public class OpenRouterServiceImpl implements OpenRouterService {

    @Autowired
    private OpenRouterConfig config;

    @Autowired(required = false)
    private AiCallMetricsService aiCallMetricsService;

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CloseableHttpClient httpClient;
    private PoolingHttpClientConnectionManager connManager;

    /** 严格输出指令：禁止 AI 返回"分析+原文+改写"的结构化报告。 */
    private static final String STRICT_OUTPUT =
            " STRICT OUTPUT: respond with the rewritten content ONLY. " +
                    "Do NOT include analysis, diagnosis, level descriptors, rewriting rules, constraints, " +
                    "the original text, section headings, or labels such as \"Rewritten Text:\". " +
                    "Do not repeat the rewritten content. No preface, no explanation, no markdown headings.";

    @PostConstruct
    public void init() {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("OpenRouter API key not configured. Set OPENROUTER_API_KEY environment variable. Service will be unavailable.");
        }

        int timeoutMs = config.getTimeout() != null ? config.getTimeout() : 60000;

        try {
            connManager = new PoolingHttpClientConnectionManager();
            connManager.setMaxTotal(50);
            connManager.setDefaultMaxPerRoute(10);

            httpClient = HttpClientBuilder.create()
                    .setConnectionManager(connManager)
                    .build();
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            factory.setConnectTimeout(timeoutMs);
            factory.setReadTimeout(timeoutMs);
            factory.setConnectionRequestTimeout(timeoutMs);
            restTemplate = new RestTemplate(factory);
        } catch (Exception e) {
            log.warn("Failed to initialize HTTP client with connection pooling, falling back to default: {}",
                    e.getMessage());
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
            factory.setConnectTimeout(timeoutMs);
            factory.setReadTimeout(timeoutMs);
            factory.setConnectionRequestTimeout(timeoutMs);
            restTemplate = new RestTemplate(factory);
        }
    }

    @PreDestroy
    public void destroy() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (Exception e) {
                log.warn("Error closing HTTP client: {}", e.getMessage());
            }
        }
        if (connManager != null) {
            try {
                connManager.close();
            } catch (Exception e) {
                log.warn("Error closing connection manager: {}", e.getMessage());
            }
        }
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateCET4Version(String originalContent) {
        String prompt = "Please rewrite the following English article to match CET-4 difficulty level. " +
                "Only return the rewritten text." + STRICT_OUTPUT + "\n\n" + originalContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateCET6Version(String originalContent) {
        String prompt = "Please rewrite the following English article to match CET-6 difficulty level. " +
                "Only return the rewritten text." + STRICT_OUTPUT + "\n\n" + originalContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateAcademicVersion(String originalContent) {
        String prompt = "Please rewrite the following English article in an academic style. " +
                "Only return the rewritten text." + STRICT_OUTPUT + "\n\n" + originalContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateChineseParallelVersion(String originalContent) {
        String prompt = "Please create a Chinese-English parallel text version of the following English article. " +
                "Format: English paragraph, then Chinese paragraph. Only return the parallel text." + STRICT_OUTPUT + "\n\n" + originalContent;
        return callOpenAI(prompt);
    }

    // Markdown support

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateCET4VersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article to match CET-4 difficulty level. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown." + STRICT_OUTPUT + "\n\n" + originalMarkdownContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateCET6VersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article to match CET-6 difficulty level. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown." + STRICT_OUTPUT + "\n\n" + originalMarkdownContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateAcademicVersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article in an academic style. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown." + STRICT_OUTPUT + "\n\n" + originalMarkdownContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateChineseParallelVersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please create a Chinese-English parallel text version of the following Markdown article. " +
                "Format: English paragraph, then Chinese paragraph. PRESERVE ALL Markdown formatting. Only return the parallel Markdown." + STRICT_OUTPUT + "\n\n" + originalMarkdownContent;
        return callOpenAI(prompt);
    }

    /**
     * Fallback method for circuit breaker.
     * Transparently re-throws BusinessException; wraps other exceptions.
     */
    public String fallback(String content, Exception e) {
        if (e instanceof BusinessException) {
            throw (BusinessException) e;
        }
        if (e instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            log.warn("OpenRouter circuit breaker is OPEN, rejecting request");
        } else {
            log.error("OpenRouter service failure: {}", e.getMessage(), e);
        }
        throw new BusinessException("备用 AI 服务暂时不可用，请稍后重试");
    }

    /**
     * OpenAI compatible interface call
     */
    private String callOpenAI(String prompt) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BusinessException("备用 AI 服务未配置 API Key");
        }

        long startTime = aiCallMetricsService != null ? aiCallMetricsService.startTimer() : 0;
        String user = AiCallMetricsService.getCurrentUser();
        String methodName = extractMethodName(prompt);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getApiKey());

            // OpenRouter specific headers
            headers.set("HTTP-Referer", "http://localhost:8080");
            headers.set("X-Title", "English Learning Platform");

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(message);

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("messages", messages);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            log.info("Calling OpenRouter API for prompt generation (Model: {})", config.getModel());

            ResponseEntity<String> response = restTemplate.exchange(
                    config.getApiUrl(),
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
                        if (aiCallMetricsService != null) {
                            aiCallMetricsService.recordSuccess("OPENROUTER", methodName,
                                    config.getModel(), startTime, user);
                        }
                        return result;
                    }
                }
            }
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("OPENROUTER", methodName, startTime,
                        "Invalid response format", user);
            }
            throw new BusinessException("备用 AI 服务返回格式无效或响应为空");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("OPENROUTER", methodName, startTime, e.getMessage(), user);
            }
            log.error("Failed to call OpenRouter API: {}", e.getMessage(), e);
            throw new BusinessException("备用 AI 服务调用失败，请稍后重试");
        }
    }

    private String extractMethodName(String prompt) {
        if (prompt.contains("CET-4")) return "generateCET4Version";
        if (prompt.contains("CET-6")) return "generateCET6Version";
        if (prompt.contains("academic")) return "generateAcademicVersion";
        if (prompt.contains("Chinese-English")) return "generateChineseParallelVersion";
        return "unknown";
    }
}
