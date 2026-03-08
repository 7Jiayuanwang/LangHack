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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenRouter DeepSeek API service implementation
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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getTimeout());
        factory.setReadTimeout(config.getTimeout());
        restTemplate = new RestTemplate(factory);
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "generateCET4VersionFallback")
    public String generateCET4Version(String originalContent) {
        String prompt = String.format(
                "Please rewrite the following English article to match CET-4 (College English Test Band 4) difficulty level. "
                        +
                        "Use simpler vocabulary and sentence structures suitable for intermediate English learners. " +
                        "Keep the same meaning and main ideas, but make it easier to understand. " +
                        "Return ONLY the rewritten article text, without any explanations or meta-commentary.\n\n" +
                        "Original article:\n%s",
                originalContent);
        return callDeepSeek(prompt);
    }

    private String generateCET4VersionFallback(String originalContent, Exception e) {
        log.warn("OpenRouter circuit breaker triggered for generateCET4Version: {}", e.getMessage());
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "generateCET6VersionFallback")
    public String generateCET6Version(String originalContent) {
        String prompt = String.format(
                "Please rewrite the following English article to match CET-6 (College English Test Band 6) difficulty level. "
                        +
                        "Use vocabulary and sentence structures suitable for upper-intermediate English learners. " +
                        "Keep the same meaning and main ideas, but adjust the complexity to CET-6 level. " +
                        "Return ONLY the rewritten article text, without any explanations or meta-commentary.\n\n" +
                        "Original article:\n%s",
                originalContent);
        return callDeepSeek(prompt);
    }

    private String generateCET6VersionFallback(String originalContent, Exception e) {
        log.warn("OpenRouter circuit breaker triggered for generateCET6Version: {}", e.getMessage());
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "generateAcademicVersionFallback")
    public String generateAcademicVersion(String originalContent) {
        String prompt = String.format(
                "Please rewrite the following English article in an academic style. " +
                        "Use formal language, advanced vocabulary, complex sentence structures, and academic tone. " +
                        "Include appropriate academic expressions and maintain scholarly rigor. " +
                        "Return ONLY the rewritten article text, without any explanations or meta-commentary.\n\n" +
                        "Original article:\n%s",
                originalContent);
        return callDeepSeek(prompt);
    }

    private String generateAcademicVersionFallback(String originalContent, Exception e) {
        log.warn("OpenRouter circuit breaker triggered for generateAcademicVersion: {}", e.getMessage());
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "generateChineseParallelVersionFallback")
    public String generateChineseParallelVersion(String originalContent) {
        String prompt = String.format(
                "Please create a Chinese-English parallel text version of the following English article. " +
                        "For each paragraph, provide the English text followed by its Chinese translation. " +
                        "Format: English paragraph, then Chinese paragraph, alternating throughout. " +
                        "Use clear paragraph breaks to separate English and Chinese sections. " +
                        "Return ONLY the parallel text, without any explanations or meta-commentary.\n\n" +
                        "Original article:\n%s",
                originalContent);
        return callDeepSeek(prompt);
    }

    private String generateChineseParallelVersionFallback(String originalContent, Exception e) {
        log.warn("OpenRouter circuit breaker triggered for generateChineseParallelVersion: {}", e.getMessage());
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    /**
     * 脱敏异常消息，防止敏感信息泄漏
     */
    private String sanitizeErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "API call failed";
        // 移除可能的敏感信息（API Key、Authorization、Request ID 等）
        return msg.replaceAll("(?i)(Bearer|Authorization|X-Api-Key|api[_-]?key).*", "[REDACTED]")
                  .replaceAll("\"api_key\".*?\"[^\"]*\"", "\"api_key\":\"[REDACTED]\"")
                  .replaceAll("(?i)request[_-]?id[:\\s=]*[\\w\\-]+", "request_id=[REDACTED]");
    }

    /**
     * Core method to call OpenRouter DeepSeek API
     *
     * @param prompt The prompt to send to DeepSeek
     * @return Generated content
     */
    private String callDeepSeek(String prompt) {
        int retries = 0;
        Exception lastException = null;

        while (retries < config.getMaxRetries()) {
            try {
                // Build request body
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", config.getModel());

                Map<String, String> message = new HashMap<>();
                message.put("role", "user");
                message.put("content", prompt);
                requestBody.put("messages", List.of(message));

                // Build headers (不在日志中输出 Authorization header)
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + config.getApiKey());
                // 注意：Authorization 不会在下面的日志中输出

                // Create request entity
                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

                log.info("Calling OpenRouter DeepSeek API (attempt {}/{})", retries + 1, config.getMaxRetries());

                // Make API call
                ResponseEntity<String> response = restTemplate.exchange(
                        config.getApiUrl(),
                        HttpMethod.POST,
                        requestEntity,
                        String.class);

                // Parse response
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode choices = root.get("choices");

                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode message_node = choices.get(0).get("message");
                        if (message_node != null) {
                            JsonNode content = message_node.get("content");
                            if (content != null && !content.isNull() && content.isTextual()) {
                                String generatedContent = content.asText();
                                log.info("Successfully generated content ({} characters)", generatedContent.length());
                                return generatedContent;
                            }
                        }
                    }

                    log.error("Unexpected response structure from OpenRouter API");
                    throw new BusinessException("AI 服务暂时不可用，请稍后重试");
                }

                log.error("API call failed with status: {}", response.getStatusCode());
                throw new BusinessException("AI 服务暂时不可用，请稍后重试");

            } catch (Exception e) {
                lastException = e;
                retries++;
                log.error("OpenRouter API call failed (attempt {}/{}): {}", retries, config.getMaxRetries(),
                    sanitizeErrorMessage(e), e);

                if (retries < config.getMaxRetries()) {
                    try {
                        // Exponential backoff: 2^retry * 1000ms，最多 30 秒
                        long waitTime = Math.min((long) Math.pow(2, retries) * 1000, 30000);
                        log.info("Waiting {}ms before retry...", waitTime);
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
                    }
                }
            }
        }

        log.error("All retry attempts exhausted for OpenRouter API call");
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    // ========== Markdown支持的方法实现 ==========

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "generateCET4VersionMarkdownFallback")
    public String generateCET4VersionMarkdown(String originalMarkdownContent) {
        String prompt = String.format(
                "Please rewrite the following Markdown article to match CET-4 (College English Test Band 4) difficulty level. " +
                        "CRITICAL REQUIREMENTS - DO NOT MODIFY THESE:\n" +
                        "1. PRESERVE ALL image references EXACTLY: ![alt text](/api/media/X/download) - DO NOT change the /api/media/X/download URLs\n" +
                        "2. PRESERVE ALL video references EXACTLY - DO NOT change any media URLs or paths\n" +
                        "3. PRESERVE ALL LaTeX math formulas EXACTLY: $formula$ and $$formula$$ - DO NOT modify mathematical expressions\n" +
                        "4. PRESERVE ALL Markdown formatting: headers (#), lists (-), links, etc.\n" +
                        "REWRITING REQUIREMENTS:\n" +
                        "5. Use simpler vocabulary and sentence structures suitable for intermediate English learners\n" +
                        "6. Keep the same meaning and main ideas, but make it easier to understand\n" +
                        "7. Only rewrite the text content, keep all syntax, formulas, and media references intact\n" +
                        "8. Return ONLY the rewritten Markdown, without any explanations\n\n" +
                        "Original Markdown article:\n%s",
                originalMarkdownContent);
        return callDeepSeek(prompt);
    }

    private String generateCET4VersionMarkdownFallback(String originalMarkdownContent, Exception e) {
        log.warn("OpenRouter circuit breaker triggered for generateCET4VersionMarkdown: {}", e.getMessage());
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "generateCET6VersionMarkdownFallback")
    public String generateCET6VersionMarkdown(String originalMarkdownContent) {
        String prompt = String.format(
                "Please rewrite the following Markdown article to match CET-6 (College English Test Band 6) difficulty level. " +
                        "CRITICAL REQUIREMENTS - DO NOT MODIFY THESE:\n" +
                        "1. PRESERVE ALL image references EXACTLY: ![alt text](/api/media/X/download) - DO NOT change the /api/media/X/download URLs\n" +
                        "2. PRESERVE ALL video references EXACTLY - DO NOT change any media URLs or paths\n" +
                        "3. PRESERVE ALL LaTeX math formulas EXACTLY: $formula$ and $$formula$$ - DO NOT modify mathematical expressions\n" +
                        "4. PRESERVE ALL Markdown formatting: headers (#), lists (-), links, etc.\n" +
                        "REWRITING REQUIREMENTS:\n" +
                        "5. Use vocabulary and sentence structures suitable for upper-intermediate English learners\n" +
                        "6. Keep the same meaning and main ideas, but adjust the complexity to CET-6 level\n" +
                        "7. Only rewrite the text content, keep all syntax, formulas, and media references intact\n" +
                        "8. Return ONLY the rewritten Markdown, without any explanations\n\n" +
                        "Original Markdown article:\n%s",
                originalMarkdownContent);
        return callDeepSeek(prompt);
    }

    private String generateCET6VersionMarkdownFallback(String originalMarkdownContent, Exception e) {
        log.warn("OpenRouter circuit breaker triggered for generateCET6VersionMarkdown: {}", e.getMessage());
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "generateAcademicVersionMarkdownFallback")
    public String generateAcademicVersionMarkdown(String originalMarkdownContent) {
        String prompt = String.format(
                "Please rewrite the following Markdown article in an academic style. " +
                        "CRITICAL REQUIREMENTS - DO NOT MODIFY THESE:\n" +
                        "1. PRESERVE ALL image references EXACTLY: ![alt text](/api/media/X/download) - DO NOT change the /api/media/X/download URLs\n" +
                        "2. PRESERVE ALL video references EXACTLY - DO NOT change any media URLs or paths\n" +
                        "3. PRESERVE ALL LaTeX math formulas EXACTLY: $formula$ and $$formula$$ - DO NOT modify mathematical expressions\n" +
                        "4. PRESERVE ALL Markdown formatting: headers (#), lists (-), links, etc.\n" +
                        "REWRITING REQUIREMENTS:\n" +
                        "5. Use formal language, advanced vocabulary, complex sentence structures, and academic tone\n" +
                        "6. Include appropriate academic expressions and maintain scholarly rigor\n" +
                        "7. Only rewrite the text content, keep all syntax, formulas, and media references intact\n" +
                        "8. Return ONLY the rewritten Markdown, without any explanations\n\n" +
                        "Original Markdown article:\n%s",
                originalMarkdownContent);
        return callDeepSeek(prompt);
    }

    private String generateAcademicVersionMarkdownFallback(String originalMarkdownContent, Exception e) {
        log.warn("OpenRouter circuit breaker triggered for generateAcademicVersionMarkdown: {}", e.getMessage());
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }

    @Override
    @CircuitBreaker(name = "openrouter", fallbackMethod = "generateChineseParallelVersionMarkdownFallback")
    public String generateChineseParallelVersionMarkdown(String originalMarkdownContent) {
        String prompt = String.format(
                "Please create a Chinese-English parallel text version of the following Markdown article. " +
                        "CRITICAL REQUIREMENTS - DO NOT MODIFY THESE:\n" +
                        "1. PRESERVE ALL image references EXACTLY: ![alt text](/api/media/X/download) - DO NOT change the /api/media/X/download URLs\n" +
                        "2. PRESERVE ALL video references EXACTLY - DO NOT change any media URLs or paths\n" +
                        "3. PRESERVE ALL LaTeX math formulas EXACTLY: $formula$ and $$formula$$ - DO NOT modify mathematical expressions\n" +
                        "4. PRESERVE ALL Markdown formatting: headers (#), lists (-), links, etc.\n" +
                        "TRANSLATION REQUIREMENTS:\n" +
                        "5. For each paragraph, provide the English text followed by its Chinese translation\n" +
                        "6. Format: English paragraph, then Chinese paragraph, alternating throughout\n" +
                        "7. Translate only the text content, keep all syntax, formulas, and media references intact\n" +
                        "8. Use clear paragraph breaks to separate English and Chinese sections\n" +
                        "9. Return ONLY the parallel Markdown text, without any explanations\n\n" +
                        "Original Markdown article:\n%s",
                originalMarkdownContent);
        return callDeepSeek(prompt);
    }

    private String generateChineseParallelVersionMarkdownFallback(String originalMarkdownContent, Exception e) {
        log.warn("OpenRouter circuit breaker triggered for generateChineseParallelVersionMarkdown: {}", e.getMessage());
        throw new BusinessException("AI 服务暂时不可用，请稍后重试");
    }
}
