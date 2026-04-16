package com.english.sso.service;

import com.alibaba.fastjson.JSON;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.UserService;
import com.english.sso.dto.SsoLoginResult;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.entity.SsoUserBinding;
import com.english.sso.mapper.SsoUserBindingMapper;
import com.english.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SSO 用户映射服务
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Slf4j
@Service
@Profile("sso")
public class SsoUserMappingService {

    @Autowired
    private SsoUserBindingMapper bindingMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleMappingService roleMappingService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 处理 SSO 登录：绑定/创建用户，返回登录结果
     *
     * @param ssoUserInfo SSO 用户信息
     * @param protocol    SSO 协议类型
     * @return 登录结果
     */
    @Transactional(rollbackFor = Exception.class)
    public SsoLoginResult processLogin(SsoUserInfo ssoUserInfo, SsoProtocolType protocol) {
        log.info("处理 SSO 登录: provider={}, userId={}", ssoUserInfo.getProvider(), ssoUserInfo.getSsoUserId());

        // 1. 查找是否已有绑定
        SsoUserBinding binding = bindingMapper.findByProviderAndSsoUserId(
                ssoUserInfo.getProvider(),
                ssoUserInfo.getSsoUserId()
        );

        if (binding != null) {
            // 已绑定用户
            log.info("找到已有绑定: bindingId={}, userId={}", binding.getId(), binding.getUserId());

            if (binding.getAutoApproved() == 0) {
                // 重新评估角色映射：之前因 memberOf 为空等原因进入待审批的用户，
                // 如果角色现在可以自动映射，直接放行（避免用户永远卡在待审批）
                String reMappedRole = roleMappingService.mapRole(ssoUserInfo.getMemberOf());
                if (reMappedRole != null && !roleMappingService.requiresManualConfirmation(reMappedRole, ssoUserInfo.getMemberOf())) {
                    log.info("待审批用户角色可自动映射，自动审批: bindingId={}, role={}", binding.getId(), reMappedRole);
                    // 更新绑定状态
                    binding.setAutoApproved(1);
                    binding.setLastLoginTime(LocalDateTime.now());
                    bindingMapper.updateById(binding);

                    // 更新用户角色和状态
                    User pendingUser = userService.getUserById(binding.getUserId());
                    if (pendingUser != null) {
                        pendingUser.setRole(reMappedRole);
                        pendingUser.setStatus("ACTIVE");
                        userService.updateUser(pendingUser);

                        String token = jwtUtil.generateToken(pendingUser.getUsername(), pendingUser.getRole());
                        log.info("SSO 自动审批登录成功: username={}, role={}", pendingUser.getUsername(), pendingUser.getRole());
                        return SsoLoginResult.success(token, pendingUser);
                    }
                }

                // 仍需人工审批
                log.warn("用户待审批: bindingId={}", binding.getId());
                return SsoLoginResult.pendingApproval(
                        binding.getId(),
                        "您的账号正在审批中，请联系管理员"
                );
            }

            // 更新最后登录时间
            binding.setLastLoginTime(LocalDateTime.now());
            bindingMapper.updateById(binding);

            // 获取用户信息
            User user = userService.getUserById(binding.getUserId());
            if (user == null) {
                throw new BusinessException("用户不存在");
            }

            // 生成 JWT token
            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

            log.info("SSO 登录成功: username={}, role={}", user.getUsername(), user.getRole());
            return SsoLoginResult.success(token, user);
        }

        // 2. 首次登录：检查 users 表是否已有同名用户（可能是之前注册或绑定丢失）
        User existingUser = userService.getUserByUsername(ssoUserInfo.getSsoUsername());
        if (existingUser != null) {
            log.info("用户已存在但无绑定，自动关联: userId={}, username={}", existingUser.getId(), existingUser.getUsername());
            String mappedRole = roleMappingService.mapRole(ssoUserInfo.getMemberOf());
            boolean autoApproved = mappedRole != null && !roleMappingService.requiresManualConfirmation(mappedRole, ssoUserInfo.getMemberOf());
            SsoUserBinding newBinding = createBinding(existingUser.getId(), ssoUserInfo, protocol, autoApproved);

            if (!autoApproved) {
                return SsoLoginResult.pendingApproval(newBinding.getId(), "您的账号正在审批中，请联系管理员");
            }

            // 更新用户的 SSO 信息
            existingUser.setSsoBound(1);
            existingUser.setSsoProvider(ssoUserInfo.getProvider());
            existingUser.setSsoSubject(ssoUserInfo.getSsoUserId());
            existingUser.setRealName(ssoUserInfo.getRealName());
            userService.updateUser(existingUser);

            String token = jwtUtil.generateToken(existingUser.getUsername(), existingUser.getRole());
            log.info("SSO 登录成功（关联已有用户）: username={}, role={}", existingUser.getUsername(), existingUser.getRole());
            return SsoLoginResult.success(token, existingUser);
        }

        // 3. 自动识别角色
        String mappedRole = roleMappingService.mapRole(ssoUserInfo.getMemberOf());

        if (mappedRole == null) {
            // 无法识别角色 → 创建待审批记录
            log.warn("无法识别角色，创建待审批记录");
            return createPendingUser(ssoUserInfo, protocol);
        }

        // 4. 检查是否需要人工确认
        if (roleMappingService.requiresManualConfirmation(mappedRole, ssoUserInfo.getMemberOf())) {
            log.info("角色 {} 需要人工确认", mappedRole);
            return createPendingUser(ssoUserInfo, protocol);
        }

        // 5. 自动创建用户并绑定
        log.info("自动创建用户: username={}, role={}", ssoUserInfo.getSsoUsername(), mappedRole);
        User newUser = createUserFromSso(ssoUserInfo, mappedRole);
        SsoUserBinding newBinding2 = createBinding(newUser.getId(), ssoUserInfo, protocol, true);

        String token = jwtUtil.generateToken(newUser.getUsername(), newUser.getRole());

        log.info("SSO 登录成功（新用户）: username={}, role={}", newUser.getUsername(), newUser.getRole());
        return SsoLoginResult.success(token, newUser);
    }

    /**
     * 从 SSO 信息创建用户
     */
    private User createUserFromSso(SsoUserInfo ssoInfo, String role) {
        User user = new User();
        user.setUsername(ssoInfo.getSsoUsername());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // 随机密码
        user.setEmail(ssoInfo.getEmail());
        user.setRealName(ssoInfo.getRealName());
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setSsoBound(1);
        user.setSsoProvider(ssoInfo.getProvider());
        user.setSsoSubject(ssoInfo.getSsoUserId());

        userService.createUser(user);
        log.info("创建用户成功: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    /**
     * 创建待审批用户
     */
    private SsoLoginResult createPendingUser(SsoUserInfo ssoInfo, SsoProtocolType protocol) {
        // 创建临时用户（待审批状态）
        User pendingUser = new User();
        pendingUser.setUsername(ssoInfo.getSsoUsername());
        pendingUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        pendingUser.setEmail(ssoInfo.getEmail());
        pendingUser.setRealName(ssoInfo.getRealName());
        pendingUser.setRole("USER"); // 临时角色
        pendingUser.setStatus("PENDING_APPROVAL");
        pendingUser.setSsoBound(1);
        pendingUser.setSsoProvider(ssoInfo.getProvider());
        pendingUser.setSsoSubject(ssoInfo.getSsoUserId());

        userService.createUser(pendingUser);
        log.info("创建待审批用户: id={}, username={}", pendingUser.getId(), pendingUser.getUsername());

        // 创建待审批绑定
        SsoUserBinding binding = createBinding(pendingUser.getId(), ssoInfo, protocol, false);

        return SsoLoginResult.pendingApproval(
                binding.getId(),
                "您的账号正在审批中，请联系管理员"
        );
    }

    /**
     * 创建 SSO 绑定
     */
    private SsoUserBinding createBinding(Long userId, SsoUserInfo ssoInfo, SsoProtocolType protocol, boolean autoApproved) {
        SsoUserBinding binding = new SsoUserBinding();
        binding.setUserId(userId);
        binding.setSsoProvider(ssoInfo.getProvider());
        binding.setSsoUserId(ssoInfo.getSsoUserId());
        binding.setSsoUsername(ssoInfo.getSsoUsername());
        binding.setSsoEmail(ssoInfo.getEmail());
        binding.setSsoRealName(ssoInfo.getRealName());
        binding.setSsoMemberOf(JSON.toJSONString(ssoInfo.getMemberOf()));
        binding.setAutoApproved(autoApproved ? 1 : 0);
        binding.setLastLoginTime(LocalDateTime.now());
        binding.setCreatedTime(LocalDateTime.now());
        binding.setUpdatedTime(LocalDateTime.now());

        bindingMapper.insert(binding);
        log.info("创建 SSO 绑定: id={}, userId={}, autoApproved={}", binding.getId(), userId, autoApproved);
        return binding;
    }
}
