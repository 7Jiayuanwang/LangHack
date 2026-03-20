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
import java.util.HashMap;
import java.util.Map;

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
        } catch (Exception e) {
            log.warn("Failed to initialize HTTP client with connection pooling, falling back to default: {}",
                    e.getMessage());
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
            factory.setConnectTimeout(config.getTimeout());
            factory.setReadTimeout(config.getTimeout());
            restTemplate = new RestTemplate(factory);
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
     * Fallback method for circuit breaker
     */
    public String fallback(String content, Exception e) {
        log.error("HiAgent service failure: {}", e.getMessage(), e);
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    /**
     * Internal implementation for HiAgent call (Two-step process per PDF)
     */
    private String callHiAgentInternal(String prompt) {
        try {
            // Step 1: Create Conversation
            String conversationId = createConversation();
            
            // Step 2: Chat Query
            return chatQuery(conversationId, prompt);
        } catch (Exception e) {
            log.error("HiAgent call failed: {}", e.getMessage(), e);
            throw new BusinessException("AI 服务调用失败: " + e.getMessage());
        }
    }

    private String createConversation() throws Exception {
        String url = config.getApiUrl() + "/create_conversation";
        
        HttpHeaders headers = createBaseHeaders();
        
        Map<String, Object> body = new HashMap<>();
        body.put("UserID", config.getUserId());
        body.put("Inputs", new HashMap<String, String>()); // Optional but recommended
        
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

    private String chatQuery(String conversationId, String prompt) throws Exception {
        String url = config.getApiUrl() + "/chat_query_v2";
        
        HttpHeaders headers = createBaseHeaders();
        
        Map<String, Object> body = new HashMap<>();
        body.put("AppConversationID", conversationId);
        body.put("Query", prompt);
        body.put("UserID", config.getUserId());
        body.put("ResponseMode", "blocking"); // Blocking mode returns result directly
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.info("Sending HiAgent query for conversation {}", conversationId);
        
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
        
        log.error("Unexpected HiAgent response structure: {}", response.getBody());
        throw new BusinessException("AI 服务返回结果解析失败");
    }

    private HttpHeaders createBaseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // CRITICAL: Documentation specifies header name as 'Apikey' (Case sensitive or documented as such)
        headers.set("Apikey", config.getApiKey());
        
        // CRITICAL: Include AppID to resolve Gateway error "未获取到AppID"
        // Trying common header names for AppID in such gateways
        headers.set("Appid", config.getAppId());
        headers.set("X-App-Id", config.getAppId());
        
        return headers;
    }
}
