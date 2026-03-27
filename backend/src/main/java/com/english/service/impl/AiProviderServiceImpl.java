package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.entity.AiProvider;
import com.english.exception.BusinessException;
import com.english.mapper.AiProviderMapper;
import com.english.service.AiProviderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AiProviderServiceImpl implements AiProviderService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderServiceImpl.class);

    @Autowired
    private AiProviderMapper mapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** per-service round-robin counter */
    private final Map<String, AtomicInteger> counters = new HashMap<>();

    // ------------------------------------------------------------------ query

    @Override
    public List<AiProvider> listAll() {
        return mapper.selectList(
            new LambdaQueryWrapper<AiProvider>()
                .orderByAsc(AiProvider::getService)
                .orderByDesc(AiProvider::getWeight)
        );
    }

    @Override
    public List<AiProvider> listByService(String service) {
        return mapper.selectList(
            new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getService, service)
                .orderByDesc(AiProvider::getWeight)
        );
    }

    // ------------------------------------------------------------------ write

    @Override
    public void create(AiProvider provider) {
        if (provider.getWeight() == null) provider.setWeight(1);
        if (provider.getEnabled() == null) provider.setEnabled(1);
        if (provider.getTimeoutMs() == null) provider.setTimeoutMs(30000);
        provider.setCreatedTime(LocalDateTime.now());
        provider.setUpdatedTime(LocalDateTime.now());
        mapper.insert(provider);
    }

    @Override
    public void update(AiProvider provider) {
        AiProvider existing = mapper.selectById(provider.getId());
        if (existing == null) throw new BusinessException("服务商不存在");
        // preserve apiKey if not provided
        if (provider.getApiKey() == null || provider.getApiKey().isEmpty()) {
            provider.setApiKey(existing.getApiKey());
        }
        provider.setUpdatedTime(LocalDateTime.now());
        mapper.updateById(provider);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void setEnabled(Long id, int enabled, String operator) {
        AiProvider p = mapper.selectById(id);
        if (p == null) throw new BusinessException("服务商不存在");
        p.setEnabled(enabled);
        p.setUpdatedBy(operator);
        p.setUpdatedTime(LocalDateTime.now());
        mapper.updateById(p);
    }

    // ------------------------------------------------------------------ pick

    /**
     * Weighted round-robin: repeat each provider weight times, then cycle.
     * Falls back to null when no enabled provider exists.
     */
    @Override
    public AiProvider pick(String service) {
        List<AiProvider> list = mapper.selectList(
            new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getService, service)
                .eq(AiProvider::getEnabled, 1)
                .orderByDesc(AiProvider::getWeight)
        );
        if (list.isEmpty()) return null;
        if (list.size() == 1) return list.get(0);

        // build weighted pool
        List<AiProvider> pool = new ArrayList<>();
        for (AiProvider p : list) {
            int w = p.getWeight() != null && p.getWeight() > 0 ? p.getWeight() : 1;
            for (int i = 0; i < w; i++) pool.add(p);
        }

        AtomicInteger counter = counters.computeIfAbsent(service, k -> new AtomicInteger(0));
        int idx = Math.abs(counter.getAndIncrement() % pool.size());
        return pool.get(idx);
    }

    // ------------------------------------------------------------------ test

    @Override
    public Map<String, Object> test(Long id, String testText) {
        AiProvider p = mapper.selectById(id);
        if (p == null) throw new BusinessException("服务商不存在");
        if (p.getApiKey() == null || p.getApiKey().isEmpty()) {
            throw new BusinessException("该服务商未配置 API Key");
        }

        String input = (testText != null && !testText.isEmpty()) ? testText : "Hello, please reply with OK.";
        int timeout = p.getTimeoutMs() != null ? p.getTimeoutMs() : 30000;

        org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory =
            new org.springframework.http.client.HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        RestTemplate rt = new RestTemplate(factory);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(p.getApiKey());

        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", input);

        Map<String, Object> body = new HashMap<>();
        body.put("model", p.getModel());
        body.put("messages", Collections.singletonList(msg));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input", input);
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> resp = rt.exchange(
                p.getApiUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
            long latency = System.currentTimeMillis() - start;
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                String output = root.path("choices").get(0).path("message").path("content").asText("").trim();
                result.put("success", true);
                result.put("output", output);
                result.put("latencyMs", latency);
            } else {
                result.put("success", false);
                result.put("error", "HTTP " + resp.getStatusCodeValue());
                result.put("latencyMs", latency);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("AI provider test failed id={}: {}", id, e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("latencyMs", latency);
        }
        return result;
    }
}
