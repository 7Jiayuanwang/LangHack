package com.english.service.impl;

import com.english.entity.AiProvider;
import com.english.service.AiProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderRuntimeServiceTest {

    @Mock
    private AiProviderService aiProviderService;

    @Mock
    private AiCallMetricsService aiCallMetricsService;

    @Mock
    private RestTemplate restTemplate;

    private TestableAiProviderRuntimeService runtimeService;

    @BeforeEach
    void setUp() {
        runtimeService = new TestableAiProviderRuntimeService(restTemplate);
        ReflectionTestUtils.setField(runtimeService, "aiProviderService", aiProviderService);
        ReflectionTestUtils.setField(runtimeService, "aiCallMetricsService", aiCallMetricsService);
        when(aiCallMetricsService.startTimer()).thenReturn(100L);
    }

    @Test
    @DisplayName("OpenAI 兼容服务商应返回 choices[0].message.content 并记录监控")
    void completeIfConfigured_openAiCompatible_returnsContentAndRecordsMetric() {
        AiProvider provider = provider("chat", "openai_compatible");
        provider.setApiUrl("https://api.example.com");
        provider.setModel("gpt-test");
        when(aiProviderService.pick("chat")).thenReturn(provider);
        when(restTemplate.exchange(
                eq("https://api.example.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"choices\":[{\"message\":{\"content\":\"回答内容\"}}]}"));

        Optional<String> result = runtimeService.completeIfConfigured("chat", "chatQA", "prompt");

        assertThat(result).contains("回答内容");
        verify(aiCallMetricsService).recordSuccess("OPENAI_COMPATIBLE", "chatQA", "gpt-test", 100L, "SYSTEM");
    }

    @Test
    @DisplayName("HiAgent 服务商应完成 create_conversation/chat_query_v2 两步调用")
    void completeIfConfigured_hiAgent_returnsAnswerAndRecordsMetric() {
        AiProvider provider = provider("article", "hiagent");
        provider.setName("HiAgent Rewrite");
        provider.setApiUrl("https://hi.example.com/chat_query_v2");
        provider.setAppId("app-id");
        when(aiProviderService.pick("article")).thenReturn(provider);
        when(restTemplate.exchange(
                eq("https://hi.example.com/create_conversation"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"Conversation\":{\"AppConversationID\":\"conv-1\"}}"));
        when(restTemplate.exchange(
                eq("https://hi.example.com/chat_query_v2"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"answer\":\"改写结果\"}"));

        Optional<String> result = runtimeService.completeIfConfigured(
                "article", "generateCET4Version", "prompt");

        assertThat(result).contains("改写结果");
        verify(aiCallMetricsService).recordSuccess(
                "HIAGENT", "generateCET4Version", "HiAgent Rewrite", 100L, "SYSTEM");
    }

    private AiProvider provider(String service, String protocol) {
        AiProvider provider = new AiProvider();
        provider.setService(service);
        provider.setProtocol(protocol);
        provider.setName("Provider");
        provider.setApiKey("api-key");
        provider.setTimeoutMs(5000);
        return provider;
    }

    private static class TestableAiProviderRuntimeService extends AiProviderRuntimeService {
        private final RestTemplate restTemplate;

        private TestableAiProviderRuntimeService(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }

        @Override
        protected RestTemplate createRestTemplate(AiProvider provider) {
            return restTemplate;
        }
    }
}
