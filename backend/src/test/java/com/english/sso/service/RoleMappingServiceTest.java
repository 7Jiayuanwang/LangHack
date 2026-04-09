package com.english.sso.service;

import com.english.sso.config.SsoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
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
        // ADMIN should have highest priority
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
        assertNull(role);
    }

    @Test
    void testMapRole_EmptyList() {
        List<String> memberOf = Arrays.asList();
        String role = roleMappingService.mapRole(memberOf);
        assertNull(role);
    }

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
