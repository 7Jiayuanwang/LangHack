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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.http.converter.StringHttpMessageConverter;

/**
 * HiAgent API service implementation strictly following PDF documentation v2.0.1
 *
 * API flow: CreateConversation → ChatQueryV2 (streaming mode)
 * Uses streaming mode so that token consumption is properly logged in HiAgent platform.
 */
@Slf4j
@Service
public class HiAgentServiceImpl implements HiAgentService {

    @Autowired
    private HiAgentConfig config;

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
            connManager.setMaxTotal(50);
            connManager.setDefaultMaxPerRoute(10);

            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(
                    HttpClientBuilder.create()
                            .setConnectionManager(connManager)
                            .build());
            factory.setConnectTimeout(config.getTimeout());
            factory.setReadTimeout(config.getTimeout());
            factory.setConnectionRequestTimeout(config.getTimeout());
            restTemplate = new RestTemplate(factory);
            restTemplate.getMessageConverters().stream()
                    .filter(c -> c instanceof StringHttpMessageConverter)
                    .forEach(c -> ((StringHttpMessageConverter) c).setDefaultCharset(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to initialize HTTP client with connection pooling, falling back to default: {}",
                    e.getMessage());
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
            factory.setConnectTimeout(config.getTimeout());
            factory.setReadTimeout(config.getTimeout());
            restTemplate = new RestTemplate(factory);
            restTemplate.getMessageConverters().stream()
                    .filter(c -> c instanceof StringHttpMessageConverter)
                    .forEach(c -> ((StringHttpMessageConverter) c).setDefaultCharset(StandardCharsets.UTF_8));
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
     * Fallback method for circuit breaker (single String param methods)
     */
    public String fallback(String content, Exception e) {
        log.error("HiAgent service failure: {}", e.getMessage(), e);
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    /**
     * Fallback method for circuit breaker (chatQA - 3 String params)
     */
    public String fallback(String userQuestion, String selectedText, String articleContext, Exception e) {
        log.error("HiAgent Q&A service failure: {}", e.getMessage(), e);
        throw new BusinessException("AI 问答服务暂时不可用，请稍后重试");
    }

    @Override
    @CircuitBreaker(name = "hiagent", fallbackMethod = "fallback")
    public String chatQA(String userQuestion, String selectedText, String articleContext) {
        String prompt = "你是一位专业的英语学习辅导老师。学生正在阅读一篇英语文章，并选中了一段文本。"
                + "请根据选中的文本和文章上下文，用中文回答学生的问题。"
                + "回答要详细、专业，帮助学生理解英语内容。\n\n"
                + "【选中文本】\n" + selectedText + "\n\n"
                + (articleContext != null && !articleContext.isEmpty() ? "【文章上下文】\n" + articleContext + "\n\n" : "")
                + "【学生提问】\n" + userQuestion;

        int maxRetries = config.getMaxRetries();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String conversationId = createConversationWithQACredentials();
                return chatQueryWithQACredentials(conversationId, prompt);
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

        log.error("HiAgent Q&A call failed after {} retries", maxRetries, lastException);
        throw new BusinessException("AI 问答服务调用失败: " + (lastException != null ? lastException.getMessage() : "unknown"));
    }

    /**
     * Internal implementation with retry logic (Two-step process per PDF v2.0.1)
     */
    private String callHiAgentInternal(String prompt) {
        int maxRetries = config.getMaxRetries();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Step 1: Create Conversation
                String conversationId = createConversation();
                // Step 2: Chat Query (streaming mode)
                return chatQuery(conversationId, prompt);
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

        log.error("HiAgent call failed after {} retries", maxRetries, lastException);
        throw new BusinessException("AI 服务调用失败: " + (lastException != null ? lastException.getMessage() : "unknown"));
    }

    private String createConversation() throws Exception {
        String url = config.getApiUrl() + "/create_conversation";

        HttpHeaders headers = createBaseHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("UserID", config.getUserId());
        body.put("Inputs", new HashMap<String, String>());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.info("Creating HiAgent conversation at {}", url);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new BusinessException("无法创建 AI 会话: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode conversation = root.get("Conversation");
        if (conversation == null || conversation.get("AppConversationID") == null) {
            log.error("Invalid response from create_conversation: {}", response.getBody());
            throw new BusinessException("AI 会话创建失败：返回数据格式不正确");
        }

        return conversation.get("AppConversationID").asText();
    }

    /**
     * ChatQueryV2 with streaming mode (recommended by PDF v2.0.1).
     * Streaming mode ensures token consumption is logged in HiAgent platform.
     * Response is SSE format; answer fragments from 'message' events are concatenated.
     */
    private String chatQuery(String conversationId, String prompt) throws Exception {
        String url = config.getApiUrl() + "/chat_query_v2";

        HttpHeaders headers = createBaseHeaders();
        headers.set("Accept", "text/event-stream"); // Required for streaming per PDF

        Map<String, Object> body = new HashMap<>();
        body.put("AppConversationID", conversationId);
        body.put("Query", prompt);
        body.put("UserID", config.getUserId());
        body.put("ResponseMode", "streaming");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.info("Sending HiAgent streaming query for conversation {}", conversationId);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new BusinessException("AI 服务响应失败: " + response.getStatusCode());
        }

        return parseSSEResponse(response.getBody());
    }

    /**
     * Parse SSE response from ChatQueryV2 streaming mode.
     * V2 SSE format: standard SSE, each data line is a JSON object (no nested data field).
     * Concatenate 'answer' fragments from all 'message' events.
     */
    private String parseSSEResponse(String sseBody) {
        StringBuilder answer = new StringBuilder();
        String[] lines = sseBody.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (data.isEmpty()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(data);
                String event = node.has("event") ? node.get("event").asText() : "";
                if ("message".equals(event) && node.has("answer")) {
                    answer.append(node.get("answer").asText());
                }
            } catch (Exception e) {
                log.debug("Skipping non-JSON SSE data: {}", data);
            }
        }

        if (answer.length() == 0) {
            log.error("No answer found in SSE response: {}", sseBody);
            throw new BusinessException("AI 服务返回结果为空");
        }

        return answer.toString();
    }

    /**
     * Create base headers per PDF v2.0.1 documentation.
     * Required: Apikey header.
     * Appid/X-App-Id: not in official docs but needed to resolve gateway "未获取到AppID" error.
     */
    private HttpHeaders createBaseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Apikey", config.getApiKey());
        // Gateway workaround: resolve "未获取到AppID" error (not in official PDF spec)
        headers.set("Appid", config.getAppId());
        headers.set("X-App-Id", config.getAppId());
        return headers;
    }

    private HttpHeaders createQAHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Apikey", config.getQaApiKey());
        // Gateway workaround
        headers.set("Appid", config.getQaAppId());
        headers.set("X-App-Id", config.getQaAppId());
        return headers;
    }

    private String createConversationWithQACredentials() throws Exception {
        String url = config.getApiUrl() + "/create_conversation";

        HttpHeaders headers = createQAHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("UserID", config.getUserId());
        body.put("Inputs", new HashMap<String, String>());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.info("Creating HiAgent Q&A conversation at {}", url);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new BusinessException("无法创建 AI Q&A 会话: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode conversation = root.get("Conversation");
        if (conversation == null || conversation.get("AppConversationID") == null) {
            log.error("Invalid response from create_conversation (QA): {}", response.getBody());
            throw new BusinessException("AI Q&A 会话创建失败：返回数据格式不正确");
        }

        return conversation.get("AppConversationID").asText();
    }

    /**
     * ChatQueryV2 with streaming mode for Q&A credentials.
     */
    private String chatQueryWithQACredentials(String conversationId, String prompt) throws Exception {
        String url = config.getApiUrl() + "/chat_query_v2";

        HttpHeaders headers = createQAHeaders();
        headers.set("Accept", "text/event-stream");

        Map<String, Object> body = new HashMap<>();
        body.put("AppConversationID", conversationId);
        body.put("Query", prompt);
        body.put("UserID", config.getUserId());
        body.put("ResponseMode", "streaming");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.info("Sending HiAgent Q&A streaming query for conversation {}", conversationId);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new BusinessException("AI Q&A 服务响应失败: " + response.getStatusCode());
        }

        return parseSSEResponse(response.getBody());
    }
}
