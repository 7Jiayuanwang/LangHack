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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateCET4Version(String originalContent) {
        String prompt = "Please rewrite the following English article to match CET-4 difficulty level. " +
                "Only return the rewritten text.\n\n" + originalContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateCET6Version(String originalContent) {
        String prompt = "Please rewrite the following English article to match CET-6 difficulty level. " +
                "Only return the rewritten text.\n\n" + originalContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateAcademicVersion(String originalContent) {
        String prompt = "Please rewrite the following English article in an academic style. " +
                "Only return the rewritten text.\n\n" + originalContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateChineseParallelVersion(String originalContent) {
        String prompt = "Please create a Chinese-English parallel text version of the following English article. " +
                "Format: English paragraph, then Chinese paragraph. Only return the parallel text.\n\n" + originalContent;
        return callOpenAI(prompt);
    }

    // Markdown support

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateCET4VersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article to match CET-4 difficulty level. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n" + originalMarkdownContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateCET6VersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article to match CET-6 difficulty level. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n" + originalMarkdownContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateAcademicVersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please rewrite the following Markdown article in an academic style. " +
                "PRESERVE ALL Markdown formatting and image/video references. Only return the rewritten Markdown.\n\n" + originalMarkdownContent;
        return callOpenAI(prompt);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "fallback")
    public String generateChineseParallelVersionMarkdown(String originalMarkdownContent) {
        String prompt = "Please create a Chinese-English parallel text version of the following Markdown article. " +
                "Format: English paragraph, then Chinese paragraph. PRESERVE ALL Markdown formatting. Only return the parallel Markdown.\n\n" + originalMarkdownContent;
        return callOpenAI(prompt);
    }

    /**
     * Fallback method for circuit breaker
     */
    public String fallback(String content, Exception e) {
        log.error("OpenRouter service failure: {}", e.getMessage(), e);
        throw new BusinessException("备用 AI 服务暂时不可用，请稍后重试");
    }

    /**
     * OpenAI compatible interface call
     */
    private String callOpenAI(String prompt) {
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
                        return msgNode.get("content").asText().trim();
                    }
                }
            }
            throw new BusinessException("OpenRouter API returned invalid format or empty response");
        } catch (Exception e) {
            log.error("Failed to call OpenRouter API: {}", e.getMessage());
            throw new BusinessException("备用 AI 服务调用失败: " + e.getMessage());
        }
    }
}
