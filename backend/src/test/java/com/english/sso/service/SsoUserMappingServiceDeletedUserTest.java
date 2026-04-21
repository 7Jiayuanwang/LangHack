package com.english.sso.service;

import com.english.entity.User;
import com.english.mapper.UserMapper;
import com.english.service.UserService;
import com.english.sso.dto.SsoLoginResult;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.entity.SsoUserBinding;
import com.english.sso.mapper.SsoUserBindingMapper;
import com.english.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测试 SSO 用户被删除后重新登录的处理逻辑
 */
@ExtendWith(MockitoExtension.class)
class SsoUserMappingServiceDeletedUserTest {

    @Mock
    private SsoUserBindingMapper bindingMapper;

    @Mock
    private UserService userService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMappingService roleMappingService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SsoUserMappingService ssoUserMappingService;

    private SsoUserInfo ssoUserInfo;
    private SsoUserBinding existingBinding;
    private User deletedUser;

    @BeforeEach
    void setUp() {
        // 准备 SSO 用户信息
        ssoUserInfo = new SsoUserInfo();
        ssoUserInfo.setProvider("CAS");
        ssoUserInfo.setSsoUserId("2023112101479");
        ssoUserInfo.setSsoUsername("2023112101479");
        ssoUserInfo.setRealName("白伟杰");
        ssoUserInfo.setEmail("test@example.com");
        ssoUserInfo.setMemberOf(Collections.singletonList("ou=2000001,ou=People"));

        // 准备已存在的绑定
        existingBinding = new SsoUserBinding();
        existingBinding.setId(1L);
        existingBinding.setUserId(5L);
        existingBinding.setSsoProvider("CAS");
        existingBinding.setSsoUserId("2023112101479");
        existingBinding.setAutoApproved(1);

        // 准备被删除的用户
        deletedUser = new User();
        deletedUser.setId(5L);
        deletedUser.setUsername("2023112101479");
        deletedUser.setRole("STUDENT");
        deletedUser.setStatus("ACTIVE");
        deletedUser.setDeleted(1);
        deletedUser.setUpdatedTime(LocalDateTime.of(2026, 4, 15, 10, 30));
    }

    @Test
    void testDeletedUserReturnsForApproval() {
        // 模拟：找到已有绑定
        when(bindingMapper.findByProviderAndSsoUserId("CAS", "2023112101479"))
                .thenReturn(existingBinding);

        // 模拟：用户已被软删除（getUserById 返回 null）
        when(userService.getUserById(5L)).thenReturn(null);

        // 模拟：通过 findDeletedById 找到被删除的用户
        when(userMapper.findDeletedById(5L)).thenReturn(deletedUser);

        // 模拟：角色映射服务
        when(roleMappingService.mapRole(anyList())).thenReturn(null);

        // 执行登录
        SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

        // 验证结果
        assertFalse(result.isSuccess(), "应该返回待审批状态");
        assertTrue(result.isPendingApproval(), "应该标记为待审批");
        assertEquals(1L, result.getBindingId(), "应该返回绑定 ID");
        assertTrue(result.getMessage().contains("曾被删除"), "错误消息应该提示用户曾被删除");

        // 验证绑定被更新为待审批状态
        verify(bindingMapper, times(2)).updateById(argThat(binding ->
            binding.getId().equals(1L) &&
            binding.getAutoApproved() == 0 &&
            binding.getAdminRemark() != null &&
            binding.getAdminRemark().contains("曾于") &&
            binding.getAdminRemark().contains("被删除")
        ));
    }

    @Test
    void testDeletedUserRemarkContainsCorrectInfo() {
        // 模拟场景
        when(bindingMapper.findByProviderAndSsoUserId("CAS", "2023112101479"))
                .thenReturn(existingBinding);
        when(userService.getUserById(5L)).thenReturn(null);
        when(userMapper.findDeletedById(5L)).thenReturn(deletedUser);
        when(roleMappingService.mapRole(anyList())).thenReturn(null);

        // 执行登录
        ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

        // 捕获更新的绑定对象
        verify(bindingMapper, times(2)).updateById(argThat(binding -> {
            if (binding.getAdminRemark() == null) {
                return true; // 第一次更新（lastLoginTime）没有 remark
            }
            String remark = binding.getAdminRemark();
            // 验证备注包含关键信息
            return remark.contains("2023112101479") &&
                   remark.contains("ID: 5") &&
                   remark.contains("2026-04-15") &&
                   remark.contains("STUDENT") &&
                   remark.contains("ACTIVE");
        }));
    }

    @Test
    void testHardDeletedUserThrowsException() {
        // 模拟：找到已有绑定
        when(bindingMapper.findByProviderAndSsoUserId("CAS", "2023112101479"))
                .thenReturn(existingBinding);

        // 模拟：用户彻底不存在（硬删除或数据异常）
        when(userService.getUserById(5L)).thenReturn(null);
        when(userMapper.findDeletedById(5L)).thenReturn(null);

        // 执行登录，应该抛出异常
        assertThrows(Exception.class, () -> {
            ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);
        });
    }
}
