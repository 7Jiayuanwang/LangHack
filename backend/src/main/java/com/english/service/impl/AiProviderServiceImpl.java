package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.entity.AiProvider;
import com.english.exception.BusinessException;
import com.english.mapper.AiProviderMapper;
import com.english.service.AiProviderService;
import com.english.datasource.UseMaster;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AiProviderServiceImpl implements AiProviderService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderServiceImpl.class);

    @Autowired
    private AiProviderMapper mapper;

    @Autowired
    private Environment env;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** per-service round-robin counter */
    private final Map<String, AtomicInteger> counters = new HashMap<>();

    /**
     * 若数据库中 translate 服务无任何记录，尝试从环境变量读取初始服务商并插入。
     * 这样部署脚本只需写入 .env，无需手动操作运维面板也能快速启动翻译功能。
     */
    @PostConstruct
    public void seedFromEnv() {
        try {
            long count = mapper.selectCount(
                new LambdaQueryWrapper<AiProvider>().eq(AiProvider::getService, "translate"));
            if (count > 0) return;

            // 主翻译服务商（通用 OpenAI 兼容）
            String apiUrl = env.getProperty("AI_TRANSLATE_API_URL", "").trim();
            String apiKey = env.getProperty("AI_TRANSLATE_API_KEY", "").trim();
            String model  = env.getProperty("AI_TRANSLATE_MODEL", "gpt-3.5-turbo").trim();
            if (!apiUrl.isEmpty() && !apiKey.isEmpty()) {
                insertSeedProvider("translate", "默认服务商 (env)", apiUrl, apiKey, null, model, 3, "由部署脚本环境变量自动创建");
            }

            // OpenRouter（可选）
            String orKey = env.getProperty("OPENROUTER_API_KEY", "").trim();
            if (!orKey.isEmpty()) {
                insertSeedProvider("translate", "OpenRouter",
                    "https://openrouter.ai/api/v1/chat/completions",
                    orKey, null, "openai/gpt-3.5-turbo", 2, "由部署脚本 OPENROUTER_API_KEY 自动创建");
            }

            // HiAgent 走专有协议（非 OpenAI 兼容），不加入 ai_provider 负载均衡池
            // 其配置通过 HIAGENT_API_KEY / HIAGENT_APP_ID / HIAGENT_QA_APP_ID 环境变量直接注入 HiAgentConfig
        } catch (Exception e) {
            log.warn("[AiProvider] 种子初始化失败（可忽略）: {}", e.getMessage());
        }
    }

    private void insertSeedProvider(String service, String name, String apiUrl,
                                     String apiKey, String appId, String model, int weight, String remark) {
        AiProvider p = new AiProvider();
        p.setService(service);
        p.setName(name);
        p.setApiUrl(apiUrl);
        p.setApiKey(apiKey);
        p.setAppId(appId);
        p.setModel(model);
        p.setWeight(weight);
        p.setEnabled(1);
        p.setTimeoutMs(30000);
        p.setRemark(remark);
        p.setCreatedTime(LocalDateTime.now());
        p.setUpdatedTime(LocalDateTime.now());
        mapper.insert(p);
        log.info("[AiProvider] 已从环境变量自动插入服务商: {} ({})", name, apiUrl);
    }

    // ------------------------------------------------------------------ query

    @Override
    @UseMaster
    public List<AiProvider> listAll() {
        return mapper.selectList(
            new LambdaQueryWrapper<AiProvider>()
                .orderByAsc(AiProvider::getService)
                .orderByDesc(AiProvider::getWeight)
        );
    }

    @Override
    @UseMaster
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
        if (provider.getModel() == null || provider.getModel().isEmpty())
            throw new BusinessException("模型名称不能为空");
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

        String rawUrl = p.getApiUrl() != null ? p.getApiUrl().stripTrailing() : "";
        if (!rawUrl.contains("/chat/completions")) {
            if (rawUrl.endsWith("/")) rawUrl = rawUrl.substring(0, rawUrl.length() - 1);
            rawUrl = rawUrl + "/v1/chat/completions";
        }
        String testUrl = rawUrl;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input", input);
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> resp = rt.exchange(
                testUrl, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
            long latency = System.currentTimeMillis() - start;
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                String body2 = resp.getBody().stripLeading();
                if (body2.startsWith("<")) {
                    result.put("success", false);
                    result.put("error", "接口 URL 配置有误：服务端返回了 HTML 页面而非 JSON，请检查 API URL 是否为正确的聊天补全端点（如 .../v1/chat/completions）");
                    result.put("latencyMs", latency);
                    return result;
                }
                JsonNode root = objectMapper.readTree(body2);
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
