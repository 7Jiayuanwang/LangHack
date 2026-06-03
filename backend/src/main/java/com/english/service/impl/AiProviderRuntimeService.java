package com.english.service.impl;

import com.english.entity.AiProvider;
import com.english.exception.BusinessException;
import com.english.service.AiProviderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AiProviderRuntimeService {

    @Autowired(required = false)
    private AiProviderService aiProviderService;

    @Autowired(required = false)
    private AiCallMetricsService aiCallMetricsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<String> completeIfConfigured(String service, String methodName, String prompt) {
        if (aiProviderService == null) {
            return Optional.empty();
        }

        AiProvider provider = aiProviderService.pick(service);
        if (provider == null) {
            return Optional.empty();
        }

        String protocol = provider.getProtocol() != null
                ? provider.getProtocol().toLowerCase(Locale.ROOT)
                : "openai_compatible";
        String metricService = metricServiceName(provider, protocol);
        String user = AiCallMetricsService.getCurrentUser();
        long startTime = aiCallMetricsService != null ? aiCallMetricsService.startTimer() : 0L;

        try {
            String output = "hiagent".equals(protocol)
                    ? callHiAgent(provider, prompt)
                    : callOpenAiCompatible(provider, prompt);
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordSuccess(metricService, methodName, modelName(provider, protocol),
                        startTime, user);
            }
            return Optional.of(output);
        } catch (BusinessException e) {
            recordFailure(metricService, methodName, startTime, e.getMessage(), user);
            throw e;
        } catch (Exception e) {
            recordFailure(metricService, methodName, startTime, e.getMessage(), user);
            throw new BusinessException("AI 服务调用失败: " + e.getMessage());
        }
    }

    private String callOpenAiCompatible(AiProvider provider, String prompt) throws Exception {
        validateRequired(provider.getApiUrl(), "API URL");
        validateRequired(provider.getApiKey(), "API Key");
        validateRequired(provider.getModel(), "模型名称");

        RestTemplate restTemplate = createRestTemplate(provider);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(provider.getApiKey());
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title", "English Learning Platform");

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", provider.getModel());
        body.put("messages", Collections.singletonList(message));
        body.put("max_tokens", 16384);  // 防止长文章 AI 出题输出被截断

        ResponseEntity<String> response = restTemplate.exchange(
                normalizeOpenAiUrl(provider.getApiUrl()),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException("OpenAI 兼容接口响应失败: HTTP " + response.getStatusCodeValue());
        }

        String responseBody = response.getBody().stripLeading();
        if (responseBody.startsWith("<")) {
            throw new BusinessException("OpenAI 兼容接口返回 HTML，请检查 API URL 是否为 chat completions 端点");
        }

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode choice = choices.get(0);
            String content = choice.path("message").path("content").asText("").trim();
            if (content.isEmpty()) {
                content = choice.path("text").asText("").trim();
            }
            if (!content.isEmpty()) {
                return content;
            }
        }

        throw new BusinessException("OpenAI 兼容接口返回格式无效或内容为空");
    }

    private String callHiAgent(AiProvider provider, String prompt) throws Exception {
        validateRequired(provider.getApiUrl(), "HiAgent API URL");
        validateRequired(provider.getApiKey(), "HiAgent API Key");
        validateRequired(provider.getAppId(), "HiAgent App ID");

        RestTemplate restTemplate = createRestTemplate(provider);
        String baseUrl = normalizeHiAgentBaseUrl(provider.getApiUrl());
        String user = AiCallMetricsService.getCurrentUser();

        HttpHeaders headers = createHiAgentHeaders(provider);

        Map<String, Object> createBody = new HashMap<>();
        createBody.put("UserID", user);
        createBody.put("Inputs", new HashMap<String, String>());

        ResponseEntity<String> createResponse = restTemplate.exchange(
                baseUrl + "/create_conversation",
                HttpMethod.POST,
                new HttpEntity<>(createBody, headers),
                String.class);

        if (!createResponse.getStatusCode().is2xxSuccessful() || createResponse.getBody() == null) {
            throw new BusinessException("HiAgent 创建会话失败: HTTP " + createResponse.getStatusCodeValue());
        }

        JsonNode createRoot = objectMapper.readTree(createResponse.getBody());
        String conversationId = createRoot.path("Conversation").path("AppConversationID").asText("").trim();
        if (conversationId.isEmpty() || "null".equalsIgnoreCase(conversationId)) {
            throw new BusinessException("HiAgent 创建会话返回格式无效");
        }

        Map<String, Object> chatBody = new HashMap<>();
        chatBody.put("AppConversationID", conversationId);
        chatBody.put("Query", prompt);
        chatBody.put("UserID", user);
        chatBody.put("ResponseMode", "blocking");

        ResponseEntity<String> chatResponse = restTemplate.exchange(
                baseUrl + "/chat_query_v2",
                HttpMethod.POST,
                new HttpEntity<>(chatBody, headers),
                String.class);

        if (!chatResponse.getStatusCode().is2xxSuccessful() || chatResponse.getBody() == null) {
            throw new BusinessException("HiAgent 问答响应失败: HTTP " + chatResponse.getStatusCodeValue());
        }

        JsonNode chatRoot = objectMapper.readTree(chatResponse.getBody());
        String answer = chatRoot.path("answer").asText("").trim();
        if (answer.isEmpty()) {
            answer = chatRoot.path("Answer").asText("").trim();
        }
        if (answer.isEmpty()) {
            throw new BusinessException("HiAgent 返回内容为空或格式无效");
        }

        return answer;
    }

    protected RestTemplate createRestTemplate(AiProvider provider) {
        int timeout = provider.getTimeoutMs() != null ? provider.getTimeoutMs() : 180000;
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(timeout);
        factory.setConnectionRequestTimeout(30000);
        return new RestTemplate(factory);
    }

    private HttpHeaders createHiAgentHeaders(AiProvider provider) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Apikey", provider.getApiKey());
        headers.set("Appid", provider.getAppId());
        headers.set("X-App-Id", provider.getAppId());
        headers.set("X-Date", currentXDate());
        return headers;
    }

    private String normalizeOpenAiUrl(String url) {
        String trimmed = url.stripTrailing();
        if (trimmed.contains("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "/v1/chat/completions";
    }

    private String normalizeHiAgentBaseUrl(String url) {
        String trimmed = url.stripTrailing();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/create_conversation")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/create_conversation".length());
        }
        if (trimmed.endsWith("/chat_query_v2")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/chat_query_v2".length());
        }
        return trimmed;
    }

    private String currentXDate() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
    }

    private void validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(fieldName + " 未配置");
        }
    }

    private void recordFailure(String serviceName, String methodName, long startTime, String error, String user) {
        if (aiCallMetricsService != null) {
            aiCallMetricsService.recordFailure(serviceName, methodName, startTime, error, user);
        }
    }

    private String metricServiceName(AiProvider provider, String protocol) {
        if ("hiagent".equals(protocol)) {
            return "HIAGENT";
        }
        String apiUrl = provider.getApiUrl() != null ? provider.getApiUrl().toLowerCase(Locale.ROOT) : "";
        String name = provider.getName() != null ? provider.getName().toLowerCase(Locale.ROOT) : "";
        if (apiUrl.contains("openrouter") || name.contains("openrouter")) {
            return "OPENROUTER";
        }
        return "OPENAI_COMPATIBLE";
    }

    private String modelName(AiProvider provider, String protocol) {
        if ("hiagent".equals(protocol)) {
            return provider.getName();
        }
        return provider.getModel();
    }
}
