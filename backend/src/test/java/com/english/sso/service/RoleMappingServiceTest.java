package com.english.sso.service;

import com.english.sso.config.SsoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleMappingServiceTest {

    @Mock
    private SsoProperties ssoProperties;

    @InjectMocks
    private RoleMappingService roleMappingService;

    private SsoProperties.RoleMappingConfig roleMappingConfig;
    private SsoProperties.AutoApprovalConfig autoApprovalConfig;

    @BeforeEach
    void setUp() {
        // Setup default role mapping rules
        Map<String, String> defaultRules = new HashMap<>();
        defaultRules.put("ou=管理员", "ADMIN");
        defaultRules.put("ou=运维", "OPS");
        defaultRules.put("ou=教师", "TEACHER");
        defaultRules.put("ou=学生", "STUDENT");

        roleMappingConfig = new SsoProperties.RoleMappingConfig();
        roleMappingConfig.setDefaultRules(defaultRules);

        // Setup auto-approval config
        autoApprovalConfig = new SsoProperties.AutoApprovalConfig();
        autoApprovalConfig.setEnabled(true);
        autoApprovalConfig.setAllowedRoles(Arrays.asList("TEACHER", "STUDENT", "USER"));

        lenient().when(ssoProperties.getRoleMapping()).thenReturn(roleMappingConfig);
        lenient().when(ssoProperties.getAutoApproval()).thenReturn(autoApprovalConfig);
    }

    @Test
    void testMapRole_Student() {
        List<String> memberOf = Arrays.asList("ou=学生,ou=用户,dc=imau,dc=edu,dc=cn");
        String role = roleMappingService.mapRole(memberOf);
        assertEquals("STUDENT", role);
    }

    @Test
    void testMapRole_Teacher() {
        List<String> memberOf = Arrays.asList("ou=教师,ou=用户,dc=imau,dc=edu,dc=cn");
        String role = roleMappingService.mapRole(memberOf);
        assertEquals("TEACHER", role);
    }

    @Test
    void testMapRole_Admin() {
        List<String> memberOf = Arrays.asList("ou=管理员,ou=用户,dc=imau,dc=edu,dc=cn");
        String role = roleMappingService.mapRole(memberOf);
        assertEquals("ADMIN", role);
    }

    @Test
    void testMapRole_Ops() {
        List<String> memberOf = Arrays.asList("ou=运维,ou=用户,dc=imau,dc=edu,dc=cn");
        String role = roleMappingService.mapRole(memberOf);
        assertEquals("OPS", role);
    }

    @Test
    void testMapRole_Priority() {
        // ADMIN should have highest priority even if STUDENT appears first in DN
        List<String> memberOf = Arrays.asList(
            "ou=学生,ou=用户,dc=imau,dc=edu,dc=cn",
            "ou=管理员,ou=用户,dc=imau,dc=edu,dc=cn"
        );
        String role = roleMappingService.mapRole(memberOf);
        assertEquals("ADMIN", role);
    }

    @Test
    void testMapRole_NoMatch() {
        List<String> memberOf = Arrays.asList("ou=其他,ou=用户,dc=imau,dc=edu,dc=cn");
        String role = roleMappingService.mapRole(memberOf);
        assertNull(role); // 无法匹配规则 → 返回 null，由调用方决定是否待审批
    }

    @Test
    void testMapRole_EmptyList() {
        List<String> memberOf = Arrays.asList();
        String role = roleMappingService.mapRole(memberOf);
        assertNull(role); // memberOf 为空 → 返回 null，由调用方决定是否待审批
    }

    @Test
    void testMapRole_NullInput() {
        String role = roleMappingService.mapRole(null);
        assertNull(role);
    }

    @Test
    void testMapRoleWithDefault_NoMatch() {
        List<String> memberOf = Arrays.asList("ou=其他,ou=用户,dc=imau,dc=edu,dc=cn");
        String role = roleMappingService.mapRoleWithDefault(memberOf);
        assertEquals("STUDENT", role); // 无法匹配规则 → 默认角色 STUDENT（校园环境大多数人是学生）
    }

    @Test
    void testMapRoleWithDefault_EmptyList() {
        List<String> memberOf = Arrays.asList();
        String role = roleMappingService.mapRoleWithDefault(memberOf);
        assertEquals("STUDENT", role); // memberOf 为空 → 默认角色 STUDENT
    }

    @Test
    void testMapRoleWithDefault_Matched() {
        List<String> memberOf = Arrays.asList("ou=教师,ou=用户,dc=imau,dc=edu,dc=cn");
        String role = roleMappingService.mapRoleWithDefault(memberOf);
        assertEquals("TEACHER", role); // 匹配到规则 → 返回映射角色
    }

    // ===== 新增：DN 组件精确匹配相关测试 =====

    @Test
    void testMapRole_ContainerIdFallback_NotMatchRole() {
        // containerId 格式：ou=2000001,ou=People — 这不是角色信息，不应匹配任何角色
        List<String> memberOf = Arrays.asList("ou=2000001,ou=People");
        String role = roleMappingService.mapRole(memberOf);
        assertNull(role); // containerId 不含角色标识 → 返回 null
    }

    @Test
    void testMapRole_ContainerIdWithDifferentNumber() {
        List<String> memberOf = Arrays.asList("ou=1000001,ou=People");
        String role = roleMappingService.mapRole(memberOf);
        assertNull(role); // 数字编号的 ou 不应匹配角色规则
    }

    @Test
    void testMapRole_DnComponentExactMatch_CaseInsensitive() {
        // 规则 key "ou=管理员" 应精确匹配 DN 组件 "ou=管理员"（忽略大小写）
        List<String> memberOf = Arrays.asList("OU=管理员,OU=用户,DC=imau,DC=edu,DC=cn");
        String role = roleMappingService.mapRole(memberOf);
        assertEquals("ADMIN", role);
    }

    @Test
    void testMapRole_OuPrefixNotMatchOuSuffix() {
        // 关键测试：ou=学生 不应匹配 ou=学生组织的组件（精确匹配，非 contains）
        // 但 ou=学生 应该匹配 ou=学生 组件
        List<String> memberOf = Arrays.asList("ou=学生组织,ou=用户,dc=imau,dc=edu,dc=cn");
        String role = roleMappingService.mapRole(memberOf);
        assertNull(role); // "ou=学生组织" 不等于 "ou=学生"，精确匹配失败
    }

    @Test
    void testMapRole_ShortOuKeyIgnored() {
        // 模拟数据库脏规则：key='ou' 不含 '='，应在 getRoleMappingRules() 中被过滤
        // 这里直接测试 mapRole 不会因为 containerId 中的 'ou' 组件而误匹配
        List<String> memberOf = Arrays.asList("ou=2000001,ou=People");
        // 展开后组件为 ["ou=2000001", "ou=people"]
        // 即使有 key='ou' 的规则（不含=），它会被 isValidRuleKey 过滤
        // 而包含=的规则如 "ou=学生" 不会匹配 "ou=2000001" 或 "ou=people"
        String role = roleMappingService.mapRole(memberOf);
        assertNull(role);
    }

    @Test
    void testMapRole_MultipleDns() {
        // 多个 memberOf DN 条目
        List<String> memberOf = Arrays.asList(
            "ou=教师,ou=用户,dc=imau,dc=edu,dc=cn",
            "ou=管理员,ou=系统,dc=imau,dc=edu,dc=cn"
        );
        String role = roleMappingService.mapRole(memberOf);
        assertEquals("ADMIN", role); // ADMIN 优先级高于 TEACHER
    }

    @Test
    void testMapRole_EnglishRuleKey() {
        // 测试英文规则 key（如 ou=teacher）
        Map<String, String> rulesWithEnglish = new HashMap<>();
        rulesWithEnglish.put("ou=管理员", "ADMIN");
        rulesWithEnglish.put("ou=运维", "OPS");
        rulesWithEnglish.put("ou=教师", "TEACHER");
        rulesWithEnglish.put("ou=学生", "STUDENT");
        rulesWithEnglish.put("ou=teacher", "TEACHER");
        rulesWithEnglish.put("ou=student", "STUDENT");
        roleMappingConfig.setDefaultRules(rulesWithEnglish);

        List<String> memberOf = Arrays.asList("ou=teacher,ou=users,dc=imau,dc=edu,dc=cn");
        String role = roleMappingService.mapRole(memberOf);
        assertEquals("TEACHER", role);
    }

    @Test
    void testExpandDnComponents_SimpleDn() {
        List<String> memberOf = Arrays.asList("ou=管理员,ou=用户,dc=imau,dc=edu,dc=cn");
        List<String> components = roleMappingService.expandDnComponents(memberOf);
        assertEquals(5, components.size());
        assertEquals("ou=管理员", components.get(0));
        assertEquals("ou=用户", components.get(1));
        assertEquals("dc=imau", components.get(2));
        assertEquals("dc=edu", components.get(3));
        assertEquals("dc=cn", components.get(4));
    }

    @Test
    void testExpandDnComponents_ContainerId() {
        List<String> memberOf = Arrays.asList("ou=2000001,ou=People");
        List<String> components = roleMappingService.expandDnComponents(memberOf);
        assertEquals(2, components.size());
        assertEquals("ou=2000001", components.get(0));
        assertEquals("ou=People", components.get(1));
    }

    @Test
    void testExpandDnComponents_NullInput() {
        List<String> components = roleMappingService.expandDnComponents(null);
        assertTrue(components.isEmpty());
    }

    @Test
    void testExpandDnComponents_EmptyInput() {
        List<String> components = roleMappingService.expandDnComponents(Collections.emptyList());
        assertTrue(components.isEmpty());
    }

    @Test
    void testExpandDnComponents_MultipleDns() {
        List<String> memberOf = Arrays.asList(
            "ou=教师,ou=用户,dc=imau",
            "ou=管理员,ou=系统,dc=imau"
        );
        List<String> components = roleMappingService.expandDnComponents(memberOf);
        assertEquals(6, components.size());
        assertTrue(components.contains("ou=教师"));
        assertTrue(components.contains("ou=管理员"));
    }

    // ===== requiresManualConfirmation 测试 =====

    @Test
    void testRequiresApproval_Student() {
        List<String> memberOf = Arrays.asList("ou=学生,ou=用户,dc=imau,dc=edu,dc=cn");
        assertFalse(roleMappingService.requiresManualConfirmation("STUDENT", memberOf));
    }

    @Test
    void testRequiresApproval_Teacher() {
        List<String> memberOf = Arrays.asList("ou=教师,ou=用户,dc=imau,dc=edu,dc=cn");
        assertFalse(roleMappingService.requiresManualConfirmation("TEACHER", memberOf));
    }

    @Test
    void testRequiresApproval_Admin() {
        List<String> memberOf = Arrays.asList("ou=管理员,ou=用户,dc=imau,dc=edu,dc=cn");
        assertTrue(roleMappingService.requiresManualConfirmation("ADMIN", memberOf));
    }

    @Test
    void testRequiresApproval_Ops() {
        List<String> memberOf = Arrays.asList("ou=运维,ou=用户,dc=imau,dc=edu,dc=cn");
        assertTrue(roleMappingService.requiresManualConfirmation("OPS", memberOf));
    }

    @Test
    void testRequiresApproval_NullRole() {
        List<String> memberOf = Arrays.asList();
        assertTrue(roleMappingService.requiresManualConfirmation(null, memberOf));
    }

    @Test
    void testRequiresApproval_DisabledAutoApproval() {
        autoApprovalConfig.setEnabled(false);
        List<String> memberOf = Arrays.asList("ou=学生,ou=用户,dc=imau,dc=edu,dc=cn");
        assertTrue(roleMappingService.requiresManualConfirmation("STUDENT", memberOf));
        assertTrue(roleMappingService.requiresManualConfirmation("TEACHER", memberOf));
    }
}
