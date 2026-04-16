package com.english.sso.service;

import com.english.sso.config.SsoProperties;
import com.english.sso.entity.SsoConfig;
import com.english.sso.mapper.SsoConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色映射服务
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Slf4j
@Service
@Profile("sso")
public class RoleMappingService {

    @Autowired
    private SsoProperties ssoProperties;

    @Autowired
    private SsoConfigMapper ssoConfigMapper;

    /**
     * 根据 memberOf 属性映射角色
     *
     * @param memberOf SSO 组织单位列表
     * @return 映射的角色 (ADMIN/OPS/TEACHER/STUDENT/USER)，无法识别时返回默认角色 USER
     */
    public String mapRole(List<String> memberOf) {
        if (memberOf == null || memberOf.isEmpty()) {
            log.info("memberOf 为空，使用默认角色 USER");
            return "USER";
        }

        // 获取映射规则（优先从数据库读取，缓存）
        Map<String, String> mappingRules = getRoleMappingRules();

        // 优先级: ADMIN > OPS > TEACHER > STUDENT > USER
        // 收集所有匹配的角色，然后按优先级返回最高的
        String[] rolePriority = {"ADMIN", "OPS", "TEACHER", "STUDENT", "USER"};

        for (String priorityRole : rolePriority) {
            for (String ou : memberOf) {
                String normalized = ou.toLowerCase().trim();

                // 检查自定义规则
                for (Map.Entry<String, String> rule : mappingRules.entrySet()) {
                    if (normalized.contains(rule.getKey().toLowerCase()) &&
                        priorityRole.equals(rule.getValue())) {
                        log.info("根据规则 '{}' 映射角色: {} -> {}", rule.getKey(), ou, rule.getValue());
                        return rule.getValue();
                    }
                }
            }
        }

        log.warn("无法匹配角色规则，memberOf: {}，使用默认角色 USER", memberOf);
        return "USER"; // 无法匹配规则 → 默认给 USER 角色（自动审批）
    }

    /**
     * 检查角色是否需要人工确认
     *
     * @param role     推断的角色
     * @param memberOf 组织单位列表
     * @return true=需要确认, false=自动审批
     */
    public boolean requiresManualConfirmation(String role, List<String> memberOf) {
        // 高权限角色需要人工确认
        if ("ADMIN".equals(role) || "OPS".equals(role)) {
            log.info("高权限角色 {} 需要人工确认", role);
            return true;
        }

        // 检查自动审批是否启用
        if (!ssoProperties.getAutoApproval().isEnabled()) {
            log.info("自动审批已禁用，所有角色需要人工确认");
            return true;
        }

        // 检查是否在自动审批白名单中
        List<String> autoApproveRoles = ssoProperties.getAutoApproval().getAllowedRoles();
        if (!autoApproveRoles.contains(role)) {
            log.info("角色 {} 不在自动审批白名单中", role);
            return true;
        }

        // 检查特殊组织（临时、试用、实习）
        if (memberOf != null) {
            for (String ou : memberOf) {
                if (ou.contains("临时") || ou.contains("试用") || ou.contains("实习")) {
                    log.info("检测到特殊组织 '{}'，需要人工确认", ou);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 获取角色映射规则（带缓存）
     */
    @Cacheable(value = "ssoRoleMappingRules", unless = "#result == null")
    public Map<String, String> getRoleMappingRules() {
        Map<String, String> rules = new HashMap<>();

        // 1. 加载默认规则
        rules.putAll(ssoProperties.getRoleMapping().getDefaultRules());

        // 2. 从数据库加载自定义规则（覆盖默认规则）
        try {
            List<SsoConfig> configs = ssoConfigMapper.findByConfigType("ROLE_MAPPING");
            for (SsoConfig config : configs) {
                String key = config.getConfigKey().replace("sso.role_mapping.", "");
                rules.put(key, config.getConfigValue());
            }
            log.info("加载角色映射规则: {} 条", rules.size());
        } catch (Exception e) {
            log.warn("从数据库加载角色映射规则失败，使用默认规则", e);
        }

        return rules;
    }
}
