package com.english.sso.service;

import com.alibaba.fastjson.JSON;
import com.english.entity.User;
import com.english.service.UserService;
import com.english.sso.dto.SsoLoginResult;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.entity.SsoUserBinding;
import com.english.sso.exception.SsoBusinessException;
import com.english.sso.exception.SsoErrorCode;
import com.english.sso.mapper.SsoUserBindingMapper;
import com.english.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
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
     * 角色同步策略：
     * - SSO 能明确识别角色 → 同步到数据库（覆盖）
     * - SSO 无法识别角色 → 保留数据库现有角色（尊重管理员手动修改）
     * - 待审批用户重新登录 → 如果现在能识别角色，自动审批
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

        // 尝试从 SSO 信息映射角色
        String ssoMappedRole = roleMappingService.mapRole(ssoUserInfo.getMemberOf());

        if (binding != null) {
            // 已绑定用户
            log.info("找到已有绑定: bindingId={}, userId={}", binding.getId(), binding.getUserId());

            if (binding.getAutoApproved() == 0) {
                // 重新评估角色映射：之前因 memberOf 为空等原因进入待审批的用户，
                // 如果角色现在可以自动映射，直接放行（避免用户永远卡在待审批）
                if (ssoMappedRole != null && !roleMappingService.requiresManualConfirmation(ssoMappedRole, ssoUserInfo.getMemberOf())) {
                    log.info("待审批用户角色可自动映射，自动审批: bindingId={}, role={}", binding.getId(), ssoMappedRole);
                    // 更新绑定状态
                    binding.setAutoApproved(1);
                    binding.setLastLoginTime(LocalDateTime.now());
                    bindingMapper.updateById(binding);

                    // 更新用户角色和状态
                    User pendingUser = userService.getUserById(binding.getUserId());
                    if (pendingUser != null) {
                        pendingUser.setRole(ssoMappedRole);
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
                        "对不起，您无权使用LangHack英语学习智能体平台。\n如有疑问请致电：计算机与信息工程学院43019235"
                );
            }

            // 更新最后登录时间
            binding.setLastLoginTime(LocalDateTime.now());
            bindingMapper.updateById(binding);

            // 获取用户信息
            User user = userService.getUserById(binding.getUserId());
            if (user == null) {
                throw new SsoBusinessException(SsoErrorCode.USER_NOT_FOUND,
                        "SSO 绑定已存在但对应用户丢失，请联系管理员");
            }

            // 状态检查：已审批绑定但用户仍为 PENDING_APPROVAL 时自动激活
            // 场景：管理员审批后 binding.autoApproved=1，但 users.status 未更新（理论上不应发生）
            if (!"ACTIVE".equals(user.getStatus())) {
                log.info("已审批用户状态为 {}，自动激活: userId={}", user.getStatus(), user.getId());
                user.setStatus("ACTIVE");
                userService.updateUser(user);
            }

            // SSO 能识别角色 → 同步到数据库（确保角色始终与 SSO 身份一致）
            // 但管理员审批的角色应优先，不被 SSO 映射覆盖
            // 判断依据：binding.autoApproved=1 且 SSO 映射角色 ≠ 数据库角色时，
            //   仅在从未有过管理员干预（即用户是自动创建的）时才同步
            //   管理员审批场景：用户原为 PENDING_APPROVAL，管理员手动设置角色并激活
            //   → 此时不应被 SSO 映射覆盖
            if (ssoMappedRole != null && !ssoMappedRole.equals(user.getRole())) {
                // 检查用户是否曾经是待审批状态（管理员干预过）
                // 如果用户状态为 ACTIVE 且角色不同于 SSO 映射，说明角色可能是管理员指定的
                // 管理员审批场景：createPendingUser 设置 role=STUDENT, status=PENDING_APPROVAL
                //   → 管理员审批后设 role=approvedRole, status=ACTIVE
                //   → 此时 binding.autoApproved=1 是管理员审批时设置的
                // 自动创建场景：processLogin 第5步自动创建，autoApproved=1 且 status=ACTIVE
                // 区分方式：管理员审批前 binding.autoApproved=0，审批后改为 1
                //   自动创建时 autoApproved 一开始就是 1
                // 更简洁的策略：仅当 SSO 映射角色优先级高于当前角色时才同步（升权不降权）
                // 这样管理员审批的低优先级角色（如 STUDENT）不会被 SSO 映射覆盖
                // 但 SSO 映射的高优先级角色（如 ADMIN）仍可同步
                boolean shouldSyncRole = isRoleUpgrade(user.getRole(), ssoMappedRole);
                if (shouldSyncRole) {
                    // 安全护栏：升权到 ADMIN/OPS 等高权限角色必须走人工确认，
                    // 不允许 SSO 组变更直接改写 DB 角色（防止 LDAP 配置被误改或劫持导致权限提升）
                    if (roleMappingService.requiresManualConfirmation(ssoMappedRole, ssoUserInfo.getMemberOf())) {
                        log.warn("SSO 升权到 {} 需人工审批，保留数据库角色: username={}, dbRole={}",
                                ssoMappedRole, user.getUsername(), user.getRole());
                    } else {
                        log.info("SSO 角色优先级更高，同步角色: username={}, dbRole={}, ssoRole={}",
                                user.getUsername(), user.getRole(), ssoMappedRole);
                        user.setRole(ssoMappedRole);
                        userService.updateUser(user);
                    }
                } else {
                    log.info("保留数据库角色（管理员审批/当前角色优先级更高）: username={}, dbRole={}, ssoRole={}",
                            user.getUsername(), user.getRole(), ssoMappedRole);
                }
            }

            // 生成 JWT token（使用数据库中的最新角色）
            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

            log.info("SSO 登录成功: username={}, role={}", user.getUsername(), user.getRole());
            return SsoLoginResult.success(token, user);
        }

        // 2. 首次登录：检查 users 表是否已有同名用户（可能是之前注册或绑定丢失）
        User existingUser = userService.getUserByUsername(ssoUserInfo.getSsoUsername());
        if (existingUser != null) {
            log.info("用户已存在但无绑定，自动关联: userId={}, username={}", existingUser.getId(), existingUser.getUsername());

            // 确定角色：SSO 能识别 → 用 SSO 角色；否则保留数据库角色
            String effectiveRole = ssoMappedRole != null ? ssoMappedRole : existingUser.getRole();
            boolean autoApproved = effectiveRole != null
                    && !roleMappingService.requiresManualConfirmation(effectiveRole, ssoUserInfo.getMemberOf());
            SsoUserBinding newBinding = createBinding(existingUser.getId(), ssoUserInfo, protocol, autoApproved);

            if (!autoApproved) {
                return SsoLoginResult.pendingApproval(newBinding.getId(), "对不起，您无权使用LangHack英语学习智能体平台。\n如有疑问请致电：计算机与信息工程学院43019235");
            }

            // 更新用户的 SSO 信息和角色
            existingUser.setSsoBound(1);
            existingUser.setSsoProvider(ssoUserInfo.getProvider());
            existingUser.setSsoSubject(ssoUserInfo.getSsoUserId());
            existingUser.setRealName(ssoUserInfo.getRealName());
            existingUser.setRole(effectiveRole);
            userService.updateUser(existingUser);

            String token = jwtUtil.generateToken(existingUser.getUsername(), existingUser.getRole());
            log.info("SSO 登录成功（关联已有用户）: username={}, role={}", existingUser.getUsername(), existingUser.getRole());
            return SsoLoginResult.success(token, existingUser);
        }

        // 3. 全新用户：自动识别角色
        if (ssoMappedRole == null) {
            // 无法识别角色 → 创建待审批记录，默认角色 STUDENT（校园环境大多数人是学生）
            log.warn("无法识别角色，创建待审批记录，默认角色 STUDENT");
            return createPendingUser(ssoUserInfo, protocol);
        }

        // 4. 检查是否需要人工确认
        if (roleMappingService.requiresManualConfirmation(ssoMappedRole, ssoUserInfo.getMemberOf())) {
            log.info("角色 {} 需要人工确认", ssoMappedRole);
            return createPendingUser(ssoUserInfo, protocol);
        }

        // 5. 自动创建用户并绑定
        log.info("自动创建用户: username={}, role={}", ssoUserInfo.getSsoUsername(), ssoMappedRole);
        User newUser = createUserFromSso(ssoUserInfo, ssoMappedRole);
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
        pendingUser.setRole("STUDENT"); // 默认角色（校园环境大多数人是学生）
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
                "对不起，您无权使用LangHack英语学习智能体平台。\n如有疑问请致电：计算机与信息工程学院43019235"
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

    /** 角色优先级定义：ADMIN > OPS > TEACHER > STUDENT > USER */
    private static final List<String> ROLE_PRIORITY = Arrays.asList("USER", "STUDENT", "TEACHER", "OPS", "ADMIN");

    /**
     * 判断 newRole 是否比 currentRole 优先级更高（升权）
     * 升权时允许 SSO 同步，降权时保留当前角色（保护管理员审批结果）
     */
    private boolean isRoleUpgrade(String currentRole, String newRole) {
        int currentIndex = ROLE_PRIORITY.indexOf(currentRole);
        int newIndex = ROLE_PRIORITY.indexOf(newRole);
        // 未知角色视为最低优先级
        if (currentIndex < 0) currentIndex = -1;
        if (newIndex < 0) newIndex = -1;
        return newIndex > currentIndex;
    }
}
