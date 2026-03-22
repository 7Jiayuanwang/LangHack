package com.english.service.impl;

import com.english.config.HiAgentConfig;
import com.english.exception.BusinessException;
import com.english.service.HiAgentService;
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
import java.util.HashMap;
import java.util.Map;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * HiAgent API service implementation strictly following PDF documentation v2.0.1
 */
@Slf4j
@Service
public class HiAgentServiceImpl implements HiAgentService {

    @Autowired
    private HiAgentConfig config;

    @Autowired(required = false)
    private AiCallMetricsService aiCallMetricsService;

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CloseableHttpClient httpClient;
    private PoolingHttpClientConnectionManager connManager;

    @PostConstruct
    public void init() {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("HiAgent API key not configured. Set HIAGENT_API_KEY environment variable. Service will be unavailable.");
        }
        if (config.getApiUrl() == null || config.getApiUrl().isBlank()) {
            log.warn("HiAgent API URL not configured. Set HIAGENT_API_URL environment variable.");
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
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateCET4Version(String originalContent) {
        String prompt = "Please rewrite the following English article to match CET-4 difficulty level. " +
                "Only return the rewritten text.\n\n" + originalContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateCET6Version(String originalContent) {
        String prompt = "Please rewrite the following English article to match CET-6 difficulty level. " +
                "Only return the rewritten text.\n\n" + originalContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateAcademicVersion(String originalContent) {
        String prompt = "Please rewrite the following English article in an academic style. " +
                "Only return the rewritten text.\n\n" + originalContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateChineseParallelVersion(String originalContent) {
        String prompt = "Please create a Chinese-English parallel text version of the following English article. " +
                "Format: English paragraph, then Chinese paragraph. Only return the parallel text.\n\n" + originalContent;
        return callHiAgentInternal(prompt);
    }

    // Markdown support

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateCET4VersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article to match CET-4 difficulty level. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n" + originalMarkdownContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateCET6VersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article to match CET-6 difficulty level. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n" + originalMarkdownContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateAcademicVersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article in an academic style. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n" + originalMarkdownContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateChineseParallelVersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please create a Chinese-English parallel text version of the following Markdown article. " +
                "Format: English paragraph, then Chinese paragraph. PRESERVE ALL Markdown formatting. Only return the parallel Markdown.\n\n" + originalMarkdownContent;
        return callHiAgentInternal(prompt);
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
            log.warn("HiAgent circuit breaker is OPEN, rejecting request");
        } else {
            log.error("HiAgent service failure: {}", e.getMessage(), e);
        }
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    /**
     * Internal implementation for HiAgent call (Two-step process per PDF)
     */
    private String callHiAgentInternal(String prompt) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BusinessException("AI 服务未配置 API Key");
        }

        long startTime = aiCallMetricsService != null ? aiCallMetricsService.startTimer() : 0;
        String user = AiCallMetricsService.getCurrentUser();
        // Extract method name from prompt prefix for logging
        String methodName = extractMethodName(prompt);

        try {
            // Step 1: Create Conversation
            String conversationId = createConversation();

            // Step 2: Chat Query
            String result = chatQuery(conversationId, prompt);

            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordSuccess("HIAGENT", methodName, startTime, user);
            }
            return result;
        } catch (BusinessException e) {
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("HIAGENT", methodName, startTime, e.getMessage(), user);
            }
            throw e;
        } catch (Exception e) {
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("HIAGENT", methodName, startTime, e.getMessage(), user);
            }
            log.error("HiAgent call failed: {}", e.getMessage(), e);
            throw new BusinessException("AI 服务调用失败，请稍后重试");
        }
    }

    private String extractMethodName(String prompt) {
        if (prompt.contains("CET-4")) return "generateCET4Version";
        if (prompt.contains("CET-6")) return "generateCET6Version";
        if (prompt.contains("academic")) return "generateAcademicVersion";
        if (prompt.contains("Chinese-English")) return "generateChineseParallelVersion";
        return "unknown";
    }

    private String createConversation() throws Exception {
        String url = config.getApiUrl() + "/create_conversation";

        HttpHeaders headers = createBaseHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("UserID", config.getUserId());
        body.put("Inputs", new HashMap<String, String>()); // Optional but recommended

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.debug("Creating HiAgent conversation at {}", url);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new BusinessException("无法创建 AI 会话: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode conversation = root.get("Conversation");
        if (conversation == null || conversation.get("AppConversationID") == null) {
            String bodyPreview = truncateForLog(response.getBody());
            log.error("Invalid response from create_conversation: {}", bodyPreview);
            throw new BusinessException("AI 会话创建失败：返回数据格式不正确");
        }

        String conversationId = conversation.get("AppConversationID").asText();
        if (conversationId == null || conversationId.isBlank() || "null".equals(conversationId)) {
            throw new BusinessException("AI 会话创建失败：会话ID为空");
        }

        return conversationId;
    }

    private String chatQuery(String conversationId, String prompt) throws Exception {
        String url = config.getApiUrl() + "/chat_query_v2";

        HttpHeaders headers = createBaseHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("AppConversationID", conversationId);
        body.put("Query", prompt);
        body.put("UserID", config.getUserId());
        body.put("ResponseMode", "blocking"); // Blocking mode returns result directly

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.debug("Sending HiAgent query for conversation {}", conversationId);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new BusinessException("AI 服务响应失败: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());

        // Blocking mode response structure should contain "answer" according to PDF example
        if (root.has("answer")) {
            return root.get("answer").asText();
        } else if (root.has("Answer")) {
            return root.get("Answer").asText();
        }

        String bodyPreview = truncateForLog(response.getBody());
        log.error("Unexpected HiAgent response structure: {}", bodyPreview);
        throw new BusinessException("AI 服务返回结果解析失败");
    }

    private HttpHeaders createBaseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Documentation specifies header name as 'Apikey'
        headers.set("Apikey", config.getApiKey());

        // Include AppID to resolve Gateway error
        headers.set("Appid", config.getAppId());

        return headers;
    }

    /**
     * Truncate response body for safe logging (avoid leaking sensitive data)
     */
    private String truncateForLog(String body) {
        if (body == null) return "null";
        return body.length() > 200 ? body.substring(0, 200) + "...(truncated)" : body;
    }
}
