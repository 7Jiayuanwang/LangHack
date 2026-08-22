package com.english.service.impl;

import com.english.config.HiAgentConfig;
import com.english.exception.BusinessException;
import com.english.service.AiProviderService;
import com.english.service.HiAgentService;
import com.english.service.SystemConfigService;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.http.converter.StringHttpMessageConverter;

/**
 * HiAgent API service implementation strictly following PDF documentation v2.0.1
 *
 * API flow: CreateConversation → ChatQueryV2 (blocking mode)
 *
 * 凭证读取优先级：system_config.ai_config.* （前端面板可编辑） → HiAgentConfig (@Value 环境变量)
 * 前端在 ops 面板配置后 SystemConfigService 清缓存，下次调用自动生效（60s cache TTL）
 */
@Slf4j
@Service
public class HiAgentServiceImpl implements HiAgentService {

    /** DB system_config group — HiAgent 凭证覆盖来源 */
    private static final String AI_CONFIG_GROUP = "ai_config";
    private static final String KEY_API_URL     = "hiagent_api_url";
    private static final String KEY_API_KEY     = "hiagent_api_key";
    private static final String KEY_APP_ID      = "hiagent_app_id";
    private static final String KEY_QA_API_KEY  = "hiagent_qa_api_key";
    private static final String KEY_QA_APP_ID   = "hiagent_qa_app_id";
    private static final String KEY_QUIZ_API_KEY = "hiagent_quiz_api_key";
    private static final String KEY_QUIZ_APP_ID  = "hiagent_quiz_app_id";

    /** 严格输出指令：禁止 HiAgent 返回"分析+原文+改写"的结构化报告。 */
    private static final String STRICT_OUTPUT =
            " STRICT OUTPUT: respond with the rewritten content ONLY. " +
                    "Do NOT include analysis, diagnosis, level descriptors, rewriting rules, constraints, " +
                    "the original text, section headings, or labels such as \"Rewritten Text:\". " +
                    "Do not repeat the rewritten content. No preface, no explanation, no markdown headings.";

    @Autowired
    private HiAgentConfig config;

    @Autowired(required = false)
    private AiProviderService aiProviderService;

    @Autowired(required = false)
    private AiCallMetricsService aiCallMetricsService;

    @Autowired(required = false)
    private SystemConfigService systemConfigService;

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CloseableHttpClient httpClient;
    private PoolingHttpClientConnectionManager connManager;

    @PostConstruct
    public void init() {
        // 启动期仅检查环境变量 fallback（DB 配置可运行时动态注入，不强依赖）
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("HiAgent API key not configured in environment. 可通过运维面板「AI 服务管理」在运行时配置，或设置 HIAGENT_API_KEY。");
        }
        if (config.getApiUrl() == null || config.getApiUrl().isBlank()) {
            log.warn("HiAgent API URL not configured in environment. 可通过运维面板「AI 服务管理」在运行时配置，或设置 HIAGENT_API_URL。");
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
            restTemplate.getMessageConverters().stream()
                    .filter(c -> c instanceof StringHttpMessageConverter)
                    .forEach(c -> ((StringHttpMessageConverter) c).setDefaultCharset(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to initialize HTTP client with connection pooling, falling back to default: {}",
                    e.getMessage());
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
            factory.setConnectTimeout(timeoutMs);
            factory.setReadTimeout(timeoutMs);
            factory.setConnectionRequestTimeout(timeoutMs);
            restTemplate = new RestTemplate(factory);
            restTemplate.getMessageConverters().stream()
                    .filter(c -> c instanceof StringHttpMessageConverter)
                    .forEach(c -> ((StringHttpMessageConverter) c).setDefaultCharset(StandardCharsets.UTF_8));
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
                "Only return the rewritten text." + STRICT_OUTPUT + "\n\n" + originalContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateCET6Version(String originalContent) {
        String prompt = "Please rewrite the following English article to match CET-6 difficulty level. " +
                "Only return the rewritten text." + STRICT_OUTPUT + "\n\n" + originalContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateAcademicVersion(String originalContent) {
        String prompt = "Please rewrite the following English article in an academic style. " +
                "Only return the rewritten text." + STRICT_OUTPUT + "\n\n" + originalContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateChineseParallelVersion(String originalContent) {
        String prompt = "Please create a Chinese-English parallel text version of the following English article. " +
                "Format: English paragraph, then Chinese paragraph. Only return the parallel text." + STRICT_OUTPUT + "\n\n" + originalContent;
        return callHiAgentInternal(prompt);
    }

    // Markdown support

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateCET4VersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article to match CET-4 difficulty level. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown." + STRICT_OUTPUT + "\n\n" + originalMarkdownContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateCET6VersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article to match CET-6 difficulty level. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown." + STRICT_OUTPUT + "\n\n" + originalMarkdownContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateAcademicVersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article in an academic style. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown." + STRICT_OUTPUT + "\n\n" + originalMarkdownContent;
        return callHiAgentInternal(prompt);
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String generateChineseParallelVersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please create a Chinese-English parallel text version of the following Markdown article. " +
                "Format: English paragraph, then Chinese paragraph. PRESERVE ALL Markdown formatting. Only return the parallel Markdown." + STRICT_OUTPUT + "\n\n" + originalMarkdownContent;
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
     * Fallback method for circuit breaker (chatQA - 3 String params)
     */
    public String fallback(String userQuestion, String selectedText, String articleContext, Exception e) {
        if (e instanceof BusinessException) {
            throw (BusinessException) e;
        }
        if (e instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            log.warn("HiAgent circuit breaker is OPEN, rejecting Q&A request");
        } else {
            log.error("HiAgent Q&A service failure: {}", e.getMessage(), e);
        }
        throw new BusinessException("AI 问答服务暂时不可用，请稍后重试");
    }

    @Override
    @CircuitBreaker(name = "hiagent-qa", fallbackMethod = "fallback")
    public String chatQA(String userQuestion, String selectedText, String articleContext) {
        // 预检查：QA 凭证必须独立配置（不 fallback 到改写凭证），未配置立即失败，不触发重试
        String qaApiKey = effectiveQaApiKey();
        String qaAppId  = effectiveQaAppId();
        if (qaApiKey == null || qaApiKey.isBlank() || qaAppId == null || qaAppId.isBlank()) {
            throw new BusinessException("AI 问答服务未配置独立凭证，请在运维面板「AI 服务管理」中配置 HiAgent 问答凭证，"
                    + "或设置环境变量 HIAGENT_QA_API_KEY / HIAGENT_QA_APP_ID");
        }

        String prompt = "你是一位专业的英语学习辅导老师。学生正在阅读一篇英语文章，并选中了一段文本。"
                + "请根据选中的文本和文章上下文，用中文回答学生的问题。"
                + "回答要详细、专业，帮助学生理解英语内容。\n\n"
                + "【选中文本】\n" + selectedText + "\n\n"
                + (articleContext != null && !articleContext.isEmpty() ? "【文章上下文】\n" + articleContext + "\n\n" : "")
                + "【学生提问】\n" + userQuestion;

        int maxRetries = config.getMaxRetries();
        Exception lastException = null;

        long startTime = aiCallMetricsService != null ? aiCallMetricsService.startTimer() : 0;
        String user = AiCallMetricsService.getCurrentUser();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String conversationId = createConversationWithQACredentials();
                String result = chatQueryWithQACredentials(conversationId, prompt);
                // 不在此处记录 metrics，由 AiQaController 统一记录，避免重复计数
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("HiAgent Q&A call attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (aiCallMetricsService != null) {
            aiCallMetricsService.recordFailure("HIAGENT", "chatQA", startTime,
                    lastException != null ? lastException.getMessage() : "unknown", user);
        }
        log.error("HiAgent Q&A call failed after {} retries", maxRetries, lastException);
        throw new BusinessException("AI 问答服务调用失败: " + (lastException != null ? lastException.getMessage() : "unknown"));
    }

    /**
     * Internal implementation with retry logic + metrics monitoring (Two-step process per PDF v2.0.1)
     */
    private String callHiAgentInternal(String prompt) {
        if (effectiveApiKey() == null || effectiveApiKey().isBlank()) {
            throw new BusinessException("AI 服务未配置 API Key");
        }

        long startTime = aiCallMetricsService != null ? aiCallMetricsService.startTimer() : 0;
        String user = AiCallMetricsService.getCurrentUser();
        String methodName = extractMethodName(prompt);

        int maxRetries = config.getMaxRetries();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Step 1: Create Conversation
                String conversationId = createConversation();
                // Step 2: Chat Query (blocking mode)
                String result = chatQuery(conversationId, prompt);

                if (aiCallMetricsService != null) {
                    aiCallMetricsService.recordSuccess("HIAGENT", methodName, startTime, user);
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("HiAgent call attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * attempt); // Linear backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (aiCallMetricsService != null) {
            aiCallMetricsService.recordFailure("HIAGENT", methodName, startTime,
                    lastException != null ? lastException.getMessage() : "unknown", user);
        }
        log.error("HiAgent call failed after {} retries", maxRetries, lastException);
        throw new BusinessException("AI 服务调用失败: " + (lastException != null ? lastException.getMessage() : "unknown"));
    }

    private String extractMethodName(String prompt) {
        if (prompt.contains("CET-4")) return "generateCET4Version";
        if (prompt.contains("CET-6")) return "generateCET6Version";
        if (prompt.contains("academic")) return "generateAcademicVersion";
        if (prompt.contains("Chinese-English")) return "generateChineseParallelVersion";
        return "unknown";
    }

    private String createConversation() throws Exception {
        String url = effectiveApiUrl() + "/create_conversation";

        HttpHeaders headers = createBaseHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("UserID", config.getUserId());
        body.put("Inputs", new HashMap<String, String>()); // Optional but recommended

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.debug("Creating HiAgent conversation at {}", url);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            // 记录 URL + 状态码 + 响应体片段，便于定位 HiAgent 签名失败 / AppId 错误 / 服务故障
            log.error("HiAgent create_conversation 非 200 响应 url={} status={} bodyPreview={}",
                    url, response.getStatusCode(), truncateForLog(response.getBody()));
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

    /**
     * ChatQueryV2 with blocking mode.
     */
    private String chatQuery(String conversationId, String prompt) throws Exception {
        String url = effectiveApiUrl() + "/chat_query_v2";

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
            // 记录 URL + conversationId + 状态码 + 响应体片段，便于定位配额超限 / 内容审查失败等
            log.error("HiAgent chat_query_v2 非 200 响应 url={} conversationId={} status={} bodyPreview={}",
                    url, conversationId, response.getStatusCode(), truncateForLog(response.getBody()));
            throw new BusinessException("AI 服务响应失败: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());

        if (root.has("answer")) {
            return root.get("answer").asText();
        } else if (root.has("Answer")) {
            return root.get("Answer").asText();
        }

        String bodyPreview = truncateForLog(response.getBody());
        log.error("Unexpected HiAgent response structure: {}", bodyPreview);
        throw new BusinessException("AI 服务返回结果解析失败");
    }

    /**
     * Returns current UTC time in ISO 8601 basic format required by HiAgent IAM signature.
     * Format: yyyyMMdd'T'HHmmss'Z'  e.g. 20260331T123456Z
     */
    private String currentXDate() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
    }

    /**
     * Create base headers per PDF v2.0.1 documentation.
     * X-Date is required by the IAM signature verification layer.
     */
    private HttpHeaders createBaseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Apikey", effectiveApiKey());
        headers.set("Appid", effectiveAppId());
        headers.set("X-App-Id", effectiveAppId());
        headers.set("X-Date", currentXDate());
        return headers;
    }

    private HttpHeaders createQAHeaders() {
        String qaApiKey = effectiveQaApiKey();
        String qaAppId  = effectiveQaAppId();

        // QA 凭证必须独立配置（与改写凭证完全不同的 HiAgent 应用）。
        // 不允许 fallback 到改写凭证，否则违反"两套完全不同"的应用隔离要求。
        if (qaApiKey == null || qaApiKey.isBlank() || qaAppId == null || qaAppId.isBlank()) {
            throw new BusinessException("AI 问答服务未配置独立凭证，请在运维面板「AI 服务管理」中配置 HiAgent 问答凭证，"
                    + "或设置环境变量 HIAGENT_QA_API_KEY / HIAGENT_QA_APP_ID");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Apikey", qaApiKey);
        headers.set("Appid", qaAppId);
        headers.set("X-App-Id", qaAppId);
        headers.set("X-Date", currentXDate());
        return headers;
    }

    private String createConversationWithQACredentials() throws Exception {
        String url = effectiveApiUrl() + "/create_conversation";

        HttpHeaders headers = createQAHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("UserID", config.getUserId());
        body.put("Inputs", new HashMap<String, String>());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.debug("Creating HiAgent Q&A conversation at {}", url);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            log.error("HiAgent (QA) create_conversation 非 200 响应 url={} status={} bodyPreview={}",
                    url, response.getStatusCode(), truncateForLog(response.getBody()));
            throw new BusinessException("无法创建 AI Q&A 会话: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode conversation = root.get("Conversation");
        if (conversation == null || conversation.get("AppConversationID") == null) {
            String bodyPreview = truncateForLog(response.getBody());
            log.error("Invalid response from create_conversation (QA): {}", bodyPreview);
            throw new BusinessException("AI Q&A 会话创建失败：返回数据格式不正确");
        }

        String conversationId = conversation.get("AppConversationID").asText();
        if (conversationId == null || conversationId.isBlank() || "null".equals(conversationId)) {
            throw new BusinessException("AI Q&A 会话创建失败：会话ID为空");
        }

        return conversationId;
    }

    /**
     * ChatQueryV2 with blocking mode for Q&A credentials.
     */
    private String chatQueryWithQACredentials(String conversationId, String prompt) throws Exception {
        String url = effectiveApiUrl() + "/chat_query_v2";

        HttpHeaders headers = createQAHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("AppConversationID", conversationId);
        body.put("Query", prompt);
        body.put("UserID", config.getUserId());
        body.put("ResponseMode", "blocking");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.debug("Sending HiAgent Q&A query for conversation {}", conversationId);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            log.error("HiAgent (QA) chat_query_v2 非 200 响应 url={} conversationId={} status={} bodyPreview={}",
                    url, conversationId, response.getStatusCode(), truncateForLog(response.getBody()));
            throw new BusinessException("AI Q&A 服务响应失败: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());

        if (root.has("answer")) {
            return root.get("answer").asText();
        } else if (root.has("Answer")) {
            return root.get("Answer").asText();
        }

        String bodyPreview = truncateForLog(response.getBody());
        log.error("Unexpected HiAgent Q&A response structure: {}", bodyPreview);
        throw new BusinessException("AI Q&A 服务返回结果解析失败");
    }

    // ==================== 流式对话（SSE Streaming） ====================

    @Override
    public String createConversationForQA() throws Exception {
        return createConversationWithQACredentials();
    }

    @Override
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody>
            chatQueryStreamingWithQACredentials(
                    String conversationId,
                    String prompt,
                    Long platformConversationId,
                    Consumer<String> completionHandler) {
        URI uri = URI.create(effectiveApiUrl() + "/chat_query_v2");

        HttpHeaders requestHeaders = createQAHeaders();
        requestHeaders.setAccept(java.util.Collections.singletonList(MediaType.TEXT_EVENT_STREAM));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("AppConversationID", conversationId);
        requestBody.put("Query", prompt);
        requestBody.put("UserID", config.getUserId());
        requestBody.put("ResponseMode", "streaming");

        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody streamBody = outputStream -> {
            StringBuilder completeAnswer = new StringBuilder();
            log.debug("Sending HiAgent Q&A streaming query for conversation {}", conversationId);
            Boolean completed = restTemplate.execute(
                    uri,
                    HttpMethod.POST,
                    request -> {
                        request.getHeaders().putAll(requestHeaders);
                        objectMapper.writeValue(request.getBody(), requestBody);
                    },
                    response -> {
                        if (response.getStatusCode() != HttpStatus.OK) {
                            Map<String, Object> error = new HashMap<>();
                            error.put("message", "AI 问答服务暂时不可用，请稍后重试");
                            writePlatformSseEvent(outputStream, "error", error);
                            return false;
                        }

                        return forwardNormalizedSse(response.getBody(), outputStream, completeAnswer);
                    });

            if (Boolean.TRUE.equals(completed)) {
                try {
                    if (completeAnswer.length() == 0) {
                        Map<String, Object> error = new HashMap<>();
                        error.put("message", "AI 问答服务未返回有效内容");
                        writePlatformSseEvent(outputStream, "error", error);
                        return;
                    }
                    if (completionHandler != null) {
                        completionHandler.accept(completeAnswer.toString());
                    }
                    Map<String, Object> done = new HashMap<>();
                    if (platformConversationId != null) {
                        done.put("conversationId", platformConversationId);
                    }
                    writePlatformSseEvent(outputStream, "done", done);
                } catch (Exception persistenceError) {
                    log.error("Failed to persist completed AI Q&A conversation {}: {}",
                            platformConversationId, persistenceError.getMessage(), persistenceError);
                    Map<String, Object> error = new HashMap<>();
                    error.put("message", "回答已生成，但保存历史对话失败");
                    writePlatformSseEvent(outputStream, "error", error);
                }
            }
        };

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.TEXT_EVENT_STREAM);
        responseHeaders.setCacheControl("no-cache");
        responseHeaders.set("X-Accel-Buffering", "no");

        return ResponseEntity.ok().headers(responseHeaders).body(streamBody);
    }

    private boolean forwardNormalizedSse(
            InputStream inputStream,
            OutputStream outputStream,
            StringBuilder completeAnswer) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String eventName = null;
            StringBuilder data = new StringBuilder();
            Boolean terminalResult = null;
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() > 0 && terminalResult == null) {
                        terminalResult = emitNormalizedEvent(
                                eventName, data.toString(), outputStream, completeAnswer);
                    }
                    eventName = null;
                    data.setLength(0);
                    continue;
                }

                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                } else {
                    // Some HiAgent gateways return NDJSON even when streaming was requested.
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.trim());
                }
            }

            if (data.length() > 0 && terminalResult == null) {
                terminalResult = emitNormalizedEvent(
                        eventName, data.toString(), outputStream, completeAnswer);
            }
            return terminalResult == null || terminalResult;
        }
    }

    private Boolean emitNormalizedEvent(
            String sseEventName,
            String data,
            OutputStream outputStream,
            StringBuilder completeAnswer)
            throws IOException {
        if ("[DONE]".equals(data)) {
            return Boolean.TRUE;
        }

        JsonNode payload = null;
        try {
            payload = objectMapper.readTree(data);
        } catch (Exception ignored) {
            // Plain-text chunks are valid fallback data and are forwarded as deltas below.
        }

        String upstreamEvent = sseEventName;
        if ((upstreamEvent == null || upstreamEvent.isBlank()) && payload != null) {
            upstreamEvent = payload.path("event").asText("");
        }

        if ("message_end".equals(upstreamEvent) || "done".equals(upstreamEvent)) {
            return Boolean.TRUE;
        }
        if ("error".equals(upstreamEvent)) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", extractFirstText(payload, "message", "error"));
            writePlatformSseEvent(outputStream, "error", error);
            return Boolean.FALSE;
        }

        String content = payload == null
                ? data
                : extractFirstText(payload, "answer", "Answer", "content", "delta");
        if ((content == null || content.isEmpty()) && payload != null && payload.path("data").isObject()) {
            content = extractFirstText(payload.path("data"), "answer", "Answer", "content", "delta");
        }
        if (content != null && !content.isEmpty()) {
            completeAnswer.append(content);
            Map<String, Object> delta = new HashMap<>();
            delta.put("content", content);
            writePlatformSseEvent(outputStream, "delta", delta);
        }
        return null;
    }

    private String extractFirstText(JsonNode payload, String... fieldNames) {
        if (payload == null) {
            return "AI 问答服务返回了无法解析的错误";
        }
        for (String fieldName : fieldNames) {
            JsonNode value = payload.get(fieldName);
            if (value != null && value.isValueNode() && !value.asText().isEmpty()) {
                return value.asText();
            }
        }
        return "";
    }

    private void writePlatformSseEvent(OutputStream outputStream, String eventName, Object data)
            throws IOException {
        String event = "event: " + eventName + "\n"
                + "data: " + objectMapper.writeValueAsString(data) + "\n\n";
        outputStream.write(event.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    // ==================== AI 出题（Quiz）====================

    /**
     * 使用出题专用凭证调用 HiAgent 生成选择题（连通测试用）
     */
    @CircuitBreaker(name = "hiagent-qa", fallbackMethod = "fallbackQuiz")
    public String generateQuiz(String title, String content, int count) {
        String quizApiKey = effectiveQuizApiKey();
        String quizAppId  = effectiveQuizAppId();
        if (quizApiKey == null || quizApiKey.isBlank() || quizAppId == null || quizAppId.isBlank()) {
            throw new BusinessException("AI 出题服务未配置独立凭证，请在运维面板「AI 服务管理」中配置 HiAgent 出题凭证");
        }

        String prompt = buildQuizPrompt(title, content, count);

        long startTime = aiCallMetricsService != null ? aiCallMetricsService.startTimer() : 0;
        String user = AiCallMetricsService.getCurrentUser();

        try {
            String conversationId = createConversationWithQuizCredentials();
            String result = chatQueryWithQuizCredentials(conversationId, prompt);
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordSuccess("HIAGENT", "generateQuiz", startTime, user);
            }
            return result;
        } catch (Exception e) {
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("HIAGENT", "generateQuiz", startTime, e.getMessage(), user);
            }
            throw new BusinessException("AI 出题服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 构建出题 prompt。超长文章自动截断，防止超过模型上下文窗口。
     */
    private String buildQuizPrompt(String title, String content, int count) {
        final int MAX_CONTENT_CHARS = 8000;
        String truncated;
        if (content.length() > MAX_CONTENT_CHARS) {
            truncated = content.substring(0, MAX_CONTENT_CHARS)
                    + "\n\n[... 文章过长已截断，共 " + content.length() + " 字符 ...]";
        } else {
            truncated = content;
        }
        return "请为以下文章生成 " + count + " 道选择题。\n\n"
                + "【文章标题】\n" + title + "\n\n"
                + "【文章内容】\n" + truncated;
    }

    public String fallbackQuiz(String title, String content, int count, Exception e) {
        if (e instanceof BusinessException) {
            throw (BusinessException) e;
        }
        throw new BusinessException("AI 出题服务暂时不可用，请稍后重试");
    }

    private HttpHeaders createQuizHeaders() {
        String quizApiKey = effectiveQuizApiKey();
        String quizAppId  = effectiveQuizAppId();

        if (quizApiKey == null || quizApiKey.isBlank() || quizAppId == null || quizAppId.isBlank()) {
            throw new BusinessException("AI 出题服务未配置独立凭证，请在运维面板「AI 服务管理」中配置 HiAgent 出题凭证");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Apikey", quizApiKey);
        headers.set("Appid", quizAppId);
        headers.set("X-App-Id", quizAppId);
        headers.set("X-Date", currentXDate());
        return headers;
    }

    private String createConversationWithQuizCredentials() throws Exception {
        String url = effectiveApiUrl() + "/create_conversation";

        HttpHeaders headers = createQuizHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("UserID", config.getUserId());
        body.put("Inputs", new HashMap<String, String>());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.debug("Creating HiAgent Quiz conversation at {}", url);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            log.error("HiAgent (Quiz) create_conversation 非 200 响应 url={} status={} bodyPreview={}",
                    url, response.getStatusCode(), truncateForLog(response.getBody()));
            throw new BusinessException("无法创建 AI 出题会话: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode conversation = root.get("Conversation");
        if (conversation == null || conversation.get("AppConversationID") == null) {
            String bodyPreview = truncateForLog(response.getBody());
            log.error("Invalid response from create_conversation (Quiz): {}", bodyPreview);
            throw new BusinessException("AI 出题会话创建失败：返回数据格式不正确");
        }

        String conversationId = conversation.get("AppConversationID").asText();
        if (conversationId == null || conversationId.isBlank() || "null".equals(conversationId)) {
            throw new BusinessException("AI 出题会话创建失败：会话ID为空");
        }

        return conversationId;
    }

    private String chatQueryWithQuizCredentials(String conversationId, String prompt) throws Exception {
        String url = effectiveApiUrl() + "/chat_query_v2";

        HttpHeaders headers = createQuizHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("AppConversationID", conversationId);
        body.put("Query", prompt);
        body.put("UserID", config.getUserId());
        body.put("ResponseMode", "blocking");
        body.put("MaxTokens", 16384);  // 防止长文章出题输出被模型截断

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.debug("Sending HiAgent Quiz query for conversation {}", conversationId);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            log.error("HiAgent (Quiz) chat_query_v2 非 200 响应 url={} conversationId={} status={} bodyPreview={}",
                    url, conversationId, response.getStatusCode(), truncateForLog(response.getBody()));
            throw new BusinessException("AI 出题服务响应失败: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());

        if (root.has("answer")) {
            return root.get("answer").asText();
        } else if (root.has("Answer")) {
            return root.get("Answer").asText();
        }

        String bodyPreview = truncateForLog(response.getBody());
        log.error("Unexpected HiAgent Quiz response structure: {}", bodyPreview);
        throw new BusinessException("AI 出题服务返回结果解析失败");
    }

    /**
     * Truncate response body for safe logging (avoid leaking sensitive data)
     */
    private String truncateForLog(String body) {
        if (body == null) return "null";
        return body.length() > 200 ? body.substring(0, 200) + "...(truncated)" : body;
    }

    // ══════════════════════════════════════════════════════════════
    // Effective credential accessors — 优先 system_config.ai_config，回退 HiAgentConfig（@Value 环境变量）
    //
    // SystemConfigService.getByGroup 自带 60s 缓存；前端面板调用 set/batchUpdate 会清缓存，
    // 下次 HiAgent 调用自动取最新值（最长 60s 延迟生效，满足运维场景）
    // ══════════════════════════════════════════════════════════════

    private String effectiveApiUrl() {
        return resolveWithFallback(KEY_API_URL, config.getApiUrl());
    }

    private String effectiveApiKey() {
        return resolveWithFallback(KEY_API_KEY, config.getApiKey());
    }

    private String effectiveAppId() {
        return resolveWithFallback(KEY_APP_ID, config.getAppId());
    }

    private String effectiveQaApiKey() {
        return resolveWithFallback(KEY_QA_API_KEY, config.getQaApiKey());
    }

    private String effectiveQaAppId() {
        return resolveWithFallback(KEY_QA_APP_ID, config.getQaAppId());
    }

    private String effectiveQuizApiKey() {
        return resolveWithFallback(KEY_QUIZ_API_KEY, config.getQuizApiKey());
    }

    private String effectiveQuizAppId() {
        return resolveWithFallback(KEY_QUIZ_APP_ID, config.getQuizAppId());
    }

    /**
     * DB 值非空则取 DB，否则回退到 @Value 注入值（环境变量 / application.yml）
     */
    private String resolveWithFallback(String dbKey, String fallback) {
        if (systemConfigService != null) {
            try {
                String dbValue = systemConfigService.get(AI_CONFIG_GROUP, dbKey);
                if (dbValue != null && !dbValue.isBlank()) {
                    return dbValue;
                }
            } catch (Exception e) {
                // DB 读失败不阻塞调用，降级到环境变量
                log.warn("读取 system_config.{}.{} 失败，回退环境变量: {}", AI_CONFIG_GROUP, dbKey, e.getMessage());
            }
        }
        return fallback;
    }
}
