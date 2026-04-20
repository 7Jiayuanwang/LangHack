package com.english.controller;

import com.english.dto.Result;
import com.english.entity.AiProvider;
import com.english.entity.FeatureOverride;
import com.english.entity.SystemConfig;
import com.english.exception.BusinessException;
import com.english.service.AiProviderService;
import com.english.service.FeatureToggleService;
import com.english.service.HiAgentService;
import com.english.service.SchoolDataConfigRefreshService;
import com.english.service.SystemConfigService;
import com.english.service.TencentTranslateService;
import com.english.service.UserService;
import com.english.service.impl.DataCleanupServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维管理控制器
 * 处理系统运维相关的数据清理、统计和系统配置管理功能
 */
@RestController
@RequestMapping("/api/ops")
@PreAuthorize("hasAnyRole('OPS', 'ADMIN')")
public class OpsController {

    private static final Logger logger = LoggerFactory.getLogger(OpsController.class);

    @Autowired
    private DataCleanupServiceImpl dataCleanupService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private TencentTranslateService translateService;

    @Autowired
    private AiProviderService aiProviderService;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private UserService userService;

    @Autowired
    private SchoolDataConfigRefreshService schoolDataConfigRefreshService;

    @Autowired(required = false)
    private HiAgentService hiAgentService;

    /**
     * 清理不一致的数据
     */
    @PostMapping("/cleanup-data")
    public Result<String> cleanupData() {
        try {
            dataCleanupService.cleanupInconsistentData();
            return Result.success("数据清理完成，请查看控制台输出获取详细信息");
        } catch (Exception e) {
            logger.error("数据清理失败", e);
            return Result.error("数据清理失败，请稍后重试");
        }
    }

    /**
     * 打印详细数据统计信息
     */
    @GetMapping("/data-stats")
    public Result<String> printDataStatistics() {
        try {
            dataCleanupService.printDataStatistics();
            return Result.success("数据统计信息已打印到控制台");
        } catch (Exception e) {
            logger.error("获取数据统计失败", e);
            return Result.error("获取数据统计失败，请稍后重试");
        }
    }

    // ==================== 系统配置管理 ====================

    /**
     * 获取指定分组的系统配置（敏感字段脱敏）
     */
    @GetMapping("/system-config")
    public Result<List<SystemConfig>> getSystemConfig(@RequestParam String group) {
        try {
            List<SystemConfig> configs = systemConfigService.listByGroup(group);
            // Mask secret values for display
            for (SystemConfig config : configs) {
                if (config.getIsSecret() != null && config.getIsSecret() == 1
                        && config.getConfigValue() != null && !config.getConfigValue().isEmpty()) {
                    String value = config.getConfigValue();
                    if (value.length() <= 4) {
                        config.setConfigValue("****");
                    } else {
                        config.setConfigValue("****" + value.substring(value.length() - 4));
                    }
                }
            }
            return Result.success(configs);
        } catch (Exception e) {
            logger.error("获取系统配置失败", e);
            return Result.error("获取系统配置失败，请稍后重试");
        }
    }

    /**
     * 批量更新系统配置
     */
    @PutMapping("/system-config")
    public Result<String> updateSystemConfig(@RequestBody List<SystemConfig> configs,
                                             Authentication authentication) {
        try {
            String username = authentication.getName();
            systemConfigService.batchUpdate(configs, username);
            return Result.success("配置更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新系统配置失败", e);
            return Result.error("更新系统配置失败，请稍后重试");
        }
    }

    /**
     * 测试翻译服务连通性
     */
    @PostMapping("/system-config/test-translate")
    public Result<Map<String, Object>> testTranslate(@RequestBody(required = false) Map<String, String> body) {
        try {
            String testText = (body != null && body.get("text") != null) ? body.get("text") : "Hello, world!";
            String result = translateService.testTranslation(testText);

            Map<String, Object> data = new HashMap<>();
            data.put("input", testText);
            data.put("output", result);
            data.put("success", true);
            return Result.success(data);
        } catch (BusinessException e) {
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("error", e.getMessage());
            return Result.success(data);
        } catch (Exception e) {
            logger.error("翻译测试失败", e);
            return Result.error("翻译测试失败，请稍后重试");
        }
    }

    // ==================== AI 服务商管理 ====================

    /**
     * 获取指定服务的所有 AI 服务商（脱敏）
     */
    @GetMapping("/ai-providers")
    public Result<List<AiProvider>> listAiProviders(@RequestParam(required = false) String service) {
        List<AiProvider> list = service != null
                ? aiProviderService.listByService(service)
                : aiProviderService.listAll();
        // mask api_key
        for (AiProvider p : list) {
            if (p.getApiKey() != null && p.getApiKey().length() > 8) {
                p.setApiKey(p.getApiKey().substring(0, 4) + "****" + p.getApiKey().substring(p.getApiKey().length() - 4));
            } else if (p.getApiKey() != null) {
                p.setApiKey("****");
            }
        }
        return Result.success(list);
    }

    /**
     * 新增 AI 服务商
     */
    @PostMapping("/ai-providers")
    public Result<AiProvider> createAiProvider(@RequestBody AiProvider provider, Authentication authentication) {
        provider.setUpdatedBy(authentication.getName());
        aiProviderService.create(provider);
        return Result.success(provider);
    }

    /**
     * 更新 AI 服务商（若 api_key 含 **** 则不覆盖原值）
     */
    @PutMapping("/ai-providers/{id}")
    public Result<String> updateAiProvider(@PathVariable Long id,
                                           @RequestBody AiProvider provider,
                                           Authentication authentication) {
        provider.setId(id);
        provider.setUpdatedBy(authentication.getName());
        // 若前端回传脱敏 key，保留数据库原值
        if (provider.getApiKey() != null && provider.getApiKey().contains("****")) {
            provider.setApiKey(null);
        }
        aiProviderService.update(provider);
        return Result.success("更新成功");
    }

    /**
     * 删除 AI 服务商
     */
    @DeleteMapping("/ai-providers/{id}")
    public Result<String> deleteAiProvider(@PathVariable Long id) {
        aiProviderService.delete(id);
        return Result.success("删除成功");
    }

    /**
     * 启用 / 禁用 AI 服务商
     */
    @PatchMapping("/ai-providers/{id}/enabled")
    public Result<String> toggleAiProvider(@PathVariable Long id,
                                           @RequestBody Map<String, Integer> body,
                                           Authentication authentication) {
        aiProviderService.setEnabled(id, body.get("enabled"), authentication.getName());
        return Result.success("操作成功");
    }

    /**
     * 手动触发：将环境变量中的 AI 密钥种子化到 ai_provider 表（已存在则跳过）
     */
    @PostMapping("/ai-providers/seed-from-env")
    public Result<String> seedAiProvidersFromEnv() {
        aiProviderService.seedFromEnv();
        return Result.success("种子化完成");
    }

    /**
     * 测试指定服务商连通性
     */
    @PostMapping("/ai-providers/{id}/test")
    public Result<Map<String, Object>> testAiProvider(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, String> body) {
        try {
            String testText = (body != null && body.get("text") != null) ? body.get("text") : "Hello, world!";
            Map<String, Object> data = aiProviderService.test(id, testText);
            return Result.success(data);
        } catch (BusinessException e) {
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("error", e.getMessage());
            return Result.success(data);
        } catch (Exception e) {
            logger.error("AI服务商连通性测试失败 id={}", id, e);
            return Result.error("测试失败，请稍后重试");
        }
    }

    // ==================== 学校数据配置管理 ====================

    /**
     * 更新学校数据配置（含刷新 + 副作用：重调度、重建数据源）
     */
    @PutMapping("/school-data/config")
    public Result<String> updateSchoolDataConfig(@RequestBody List<SystemConfig> configs,
                                                  Authentication authentication) {
        try {
            String username = authentication.getName();
            schoolDataConfigRefreshService.updateAndRefresh(configs, username);
            return Result.success("学校数据配置更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新学校数据配置失败", e);
            return Result.error("更新学校数据配置失败，请稍后重试");
        }
    }

    /**
     * 测试学校数据源连接
     */
    @PostMapping("/school-data/test-connection")
    public Result<Map<String, Object>> testSchoolDataConnection() {
        try {
            boolean success = schoolDataConfigRefreshService.testConnection();
            Map<String, Object> data = new HashMap<>();
            data.put("success", success);
            data.put("source", schoolDataConfigRefreshService.getCurrentSource());
            return Result.success(data);
        } catch (Exception e) {
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("error", e.getMessage());
            return Result.success(data);
        }
    }

    /**
     * 查询学校数据集成配置状态（敏感值不返回，仅返回 "是否已配置" 的 boolean）
     * 前端根据此结果展示顶部警告横幅与未配置项 badge。
     */
    @GetMapping("/school-data/config-status")
    public Result<Map<String, Object>> getSchoolDataConfigStatus() {
        try {
            return Result.success(schoolDataConfigRefreshService.getConfigStatus());
        } catch (Exception e) {
            logger.error("获取学校数据配置状态失败", e);
            return Result.error("获取配置状态失败");
        }
    }

    // ==================== 个性化功能开关管理 ====================

    /**
     * 列出所有标签名称
     */
    @GetMapping("/user-tags")
    public Result<List<String>> listAllTags() {
        return Result.success(featureToggleService.listAllTagNames());
    }

    /**
     * 查询某标签下的所有用户ID
     */
    @GetMapping("/user-tags/{tagName}/users")
    public Result<List<Map<String, Object>>> getUsersByTag(@PathVariable String tagName) {
        List<Long> ids = featureToggleService.getUserIdsByTag(tagName);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : ids) {
            com.english.entity.User u = userService.getUserById(id);
            if (u == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("nickname", u.getNickname());
            item.put("realName", u.getRealName());
            item.put("role", u.getRole());
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 删除标签及其所有规则和用户关联
     */
    @DeleteMapping("/user-tags/{tagName}")
    public Result<String> deleteTag(@PathVariable String tagName, Authentication authentication) {
        featureToggleService.deleteTag(tagName, authentication.getName());
        return Result.success("标签已删除");
    }

    /**
     * 查询用户当前的标签列表
     */
    @GetMapping("/users/{userId}/tags")
    public Result<List<String>> getUserTags(@PathVariable Long userId) {
        return Result.success(featureToggleService.getUserTags(userId));
    }

    /**
     * 设置用户标签（全量覆盖）
     */
    @PutMapping("/users/{userId}/tags")
    public Result<String> setUserTags(@PathVariable Long userId,
                                      @RequestBody List<String> tags,
                                      Authentication authentication) {
        featureToggleService.setUserTags(userId, tags, authentication.getName());
        return Result.success("用户标签已更新");
    }

    /**
     * 查询覆盖规则列表（可按 scope / scopeValue 过滤）
     */
    @GetMapping("/feature-overrides")
    public Result<List<FeatureOverride>> listFeatureOverrides(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String scopeValue) {
        return Result.success(featureToggleService.listOverrides(scope, scopeValue));
    }

    /**
     * 新增或更新覆盖规则
     */
    @PutMapping("/feature-overrides")
    public Result<String> upsertFeatureOverride(@RequestBody FeatureOverride override,
                                                Authentication authentication) {
        featureToggleService.upsertOverride(override, authentication.getName());
        return Result.success("覆盖规则已保存");
    }

    /**
     * 删除覆盖规则
     */
    @DeleteMapping("/feature-overrides")
    public Result<String> deleteFeatureOverride(@RequestParam String scope,
                                                @RequestParam String scopeValue,
                                                @RequestParam String featureKey,
                                                Authentication authentication) {
        featureToggleService.deleteOverride(scope, scopeValue, featureKey, authentication.getName());
        return Result.success("覆盖规则已删除");
    }

    // ==================== HiAgent 凭证配置与连通测试 ====================

    /**
     * HiAgent 配置状态汇总（不返回真实密钥，仅返回每项是否已配置）
     * 用于前端面板展示"改写/问答凭证是否就绪"
     */
    @GetMapping("/hiagent/status")
    public Result<Map<String, Object>> hiAgentStatus() {
        Map<String, String> dbCfg = systemConfigService.getByGroup("ai_config");
        Map<String, Object> result = new HashMap<>();
        for (String key : new String[]{"hiagent_api_url", "hiagent_api_key", "hiagent_app_id",
                                        "hiagent_qa_api_key", "hiagent_qa_app_id"}) {
            String v = dbCfg.get(key);
            result.put(key + "_configured", v != null && !v.isBlank());
        }
        // 改写 + 问答 两路是否具备最小凭证
        boolean rewriteReady = Boolean.TRUE.equals(result.get("hiagent_api_key_configured"))
                && Boolean.TRUE.equals(result.get("hiagent_app_id_configured"));
        boolean qaReady = Boolean.TRUE.equals(result.get("hiagent_qa_api_key_configured"))
                && Boolean.TRUE.equals(result.get("hiagent_qa_app_id_configured"));
        result.put("rewrite_ready_from_db", rewriteReady);
        result.put("qa_ready_from_db", qaReady);
        return Result.success(result);
    }

    /**
     * HiAgent 改写应用连通测试（调用短文本 CET-4 改写）
     */
    @PostMapping("/hiagent/test-rewrite")
    public Result<Map<String, Object>> testHiAgentRewrite(@RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> data = new HashMap<>();
        if (hiAgentService == null) {
            data.put("success", false);
            data.put("error", "HiAgent 服务未注入");
            return Result.success(data);
        }
        String sample = (body != null && body.get("text") != null && !body.get("text").isBlank())
                ? body.get("text") : "This is a short test sentence for HiAgent rewrite.";
        try {
            String out = hiAgentService.generateCET4Version(sample);
            data.put("success", true);
            data.put("preview", out != null && out.length() > 200 ? out.substring(0, 200) + "..." : out);
        } catch (BusinessException e) {
            data.put("success", false);
            data.put("error", e.getMessage());
        } catch (Exception e) {
            logger.error("HiAgent 改写测试失败", e);
            data.put("success", false);
            data.put("error", "改写测试调用失败，请检查日志");
        }
        return Result.success(data);
    }

    /**
     * HiAgent 问答应用连通测试（调用 chatQA，独立凭证校验）
     */
    @PostMapping("/hiagent/test-qa")
    public Result<Map<String, Object>> testHiAgentQa(@RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> data = new HashMap<>();
        if (hiAgentService == null) {
            data.put("success", false);
            data.put("error", "HiAgent 服务未注入");
            return Result.success(data);
        }
        String question = (body != null && body.get("question") != null && !body.get("question").isBlank())
                ? body.get("question") : "请用一句话解释这句话的含义。";
        String selected = (body != null && body.get("selectedText") != null && !body.get("selectedText").isBlank())
                ? body.get("selectedText") : "Knowledge is power.";
        try {
            String out = hiAgentService.chatQA(question, selected, "");
            data.put("success", true);
            data.put("preview", out != null && out.length() > 200 ? out.substring(0, 200) + "..." : out);
        } catch (BusinessException e) {
            data.put("success", false);
            data.put("error", e.getMessage());
        } catch (Exception e) {
            logger.error("HiAgent 问答测试失败", e);
            data.put("success", false);
            data.put("error", "问答测试调用失败，请检查日志");
        }
        return Result.success(data);
    }
}
