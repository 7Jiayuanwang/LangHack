package com.english.sso.service;

import com.english.sso.config.SsoProperties;
import com.english.sso.entity.SsoConfig;
import com.english.sso.mapper.SsoConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 角色映射服务
 *
 * <p>匹配策略：将 memberOf 中的每个 LDAP DN 按 {@code ,} 拆分为独立组件（如
 * {@code "ou=管理员,ou=用户,dc=imau"} → ["ou=管理员", "ou=用户", "dc=imau"]），
 * 再用规则 key 对每个组件做<strong>忽略大小写的精确匹配</strong>。
 * 这样 {@code "ou=管理员"} 只匹配 {@code ou=管理员} 组件，而不会误匹配 {@code ou=2000001}。</p>
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
     * @param memberOf SSO 组织单位列表（每个元素可以是一个完整 DN，如 "ou=管理员,ou=用户,dc=imau,dc=edu,dc=cn"）
     * @return 映射的角色 (ADMIN/OPS/TEACHER/STUDENT)，无法识别时返回 null（由调用方决定是否进入待审批或赋予默认角色）
     */
    public String mapRole(List<String> memberOf) {
        if (memberOf == null || memberOf.isEmpty()) {
            log.info("memberOf 为空，无法识别角色");
            return null;
        }

        // 获取映射规则（优先从数据库读取，缓存）
        Map<String, String> mappingRules = getRoleMappingRules();

        // 将所有 memberOf DN 展开为扁平的组件列表
        // "ou=管理员,ou=用户,dc=imau,dc=edu,dc=cn" → ["ou=管理员", "ou=用户", "dc=imau", "dc=edu", "dc=cn"]
        List<String> allComponents = expandDnComponents(memberOf);

        if (allComponents.isEmpty()) {
            log.info("memberOf 展开后为空，无法识别角色");
            return null;
        }

        // 优先级: ADMIN > OPS > TEACHER > STUDENT
        String[] rolePriority = {"ADMIN", "OPS", "TEACHER", "STUDENT"};

        for (String priorityRole : rolePriority) {
            for (String component : allComponents) {
                String normalizedComponent = component.toLowerCase().trim();

                for (Map.Entry<String, String> rule : mappingRules.entrySet()) {
                    String ruleKey = rule.getKey().toLowerCase().trim();
                    // 精确匹配 DN 组件：规则 key "ou=管理员" 精确匹配组件 "ou=管理员"
                    if (normalizedComponent.equals(ruleKey) && priorityRole.equals(rule.getValue())) {
                        log.info("根据规则 '{}' 映射角色: {} -> {}", rule.getKey(), component, rule.getValue());
                        return rule.getValue();
                    }
                }
            }
        }

        log.warn("无法匹配角色规则，memberOf: {}", memberOf);
        return null; // 无法匹配规则 → 返回 null，由调用方决定是否进入待审批
    }

    /**
     * 将 memberOf 中的每个 LDAP DN 按 "," 拆分为独立组件
     *
     * <p>例如 {@code ["ou=管理员,ou=用户,dc=imau,dc=edu,dc=cn"]}
     * → {@code ["ou=管理员", "ou=用户", "dc=imau", "dc=edu", "dc=cn"]}</p>
     *
     * @param memberOf SSO 组织单位列表
     * @return 展开后的 DN 组件列表
     */
    List<String> expandDnComponents(List<String> memberOf) {
        if (memberOf == null || memberOf.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> components = new ArrayList<>();
        for (String dn : memberOf) {
            if (dn == null || dn.isBlank()) continue;
            // 按 "," 拆分 DN，每个部分是一个 RDN（如 "ou=管理员"）
            // 跳过转义的逗号 (\,) — LDAP DN 中 \, 属于 RDN 内部字符，不应拆分
            // 但 CIAP 7.0 的 containerId 格式简单，不含转义逗号，直接 split 即可
            for (String part : dn.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    components.add(trimmed);
                }
            }
        }
        return components;
    }

    /**
     * 根据 memberOf 映射角色，无法识别时返回默认角色 STUDENT（校园环境大多数人是学生）
     *
     * @param memberOf SSO 组织单位列表
     * @return 映射的角色，无法识别时返回 "STUDENT"
     */
    public String mapRoleWithDefault(List<String> memberOf) {
        String role = mapRole(memberOf);
        return role != null ? role : "STUDENT";
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
     *
     * <p>规则 key 必须包含 "="（如 "ou=管理员"），不含 "=" 的短 key（如 "ou"）
     * 会匹配所有 DN 组件，属于脏数据，加载时自动过滤。</p>
     */
    @Cacheable(value = "ssoRoleMappingRules", unless = "#result == null")
    public Map<String, String> getRoleMappingRules() {
        Map<String, String> rules = new HashMap<>();

        // 1. 加载默认规则
        for (Map.Entry<String, String> entry : ssoProperties.getRoleMapping().getDefaultRules().entrySet()) {
            if (isValidRuleKey(entry.getKey())) {
                rules.put(entry.getKey(), entry.getValue());
            } else {
                log.warn("忽略无效的默认角色映射规则 key: '{}' (必须包含 '=')", entry.getKey());
            }
        }

        // 2. 从数据库加载自定义规则（覆盖默认规则）
        try {
            List<SsoConfig> configs = ssoConfigMapper.findByConfigType("ROLE_MAPPING");
            for (SsoConfig config : configs) {
                String key = config.getConfigKey().replace("sso.role_mapping.", "");
                if (isValidRuleKey(key)) {
                    rules.put(key, config.getConfigValue());
                } else {
                    log.warn("忽略无效的数据库角色映射规则: configKey='{}', strip后key='{}' (必须包含 '=')",
                            config.getConfigKey(), key);
                }
            }
            log.info("加载角色映射规则: {} 条", rules.size());
        } catch (Exception e) {
            log.warn("从数据库加载角色映射规则失败，使用默认规则", e);
        }

        return rules;
    }

    /**
     * 校验规则 key 是否有效
     *
     * <p>有效 key 必须包含 "="，如 "ou=管理员"、"ou=teacher"。
     * 短 key 如 "ou" 会匹配所有 DN 组件，属于脏数据。</p>
     */
    private boolean isValidRuleKey(String key) {
        return key != null && key.contains("=");
    }
}
