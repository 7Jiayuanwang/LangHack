package com.english.sso.service;

import com.alibaba.fastjson.JSON;
import com.english.entity.SchoolStudent;
import com.english.entity.SchoolTeacher;
import com.english.entity.User;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.SchoolTeacherMapper;
import com.english.mapper.UserMapper;
import com.english.service.SystemConfigService;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    private static final String DEFAULT_PENDING_USER_MESSAGE =
            "当前已授予普通用户权限，您仍可正常登录；如需学生/教师等更高权限，请等待管理员审核。";
    private static final String PRIVILEGED_REVIEW_MESSAGE =
            "当前 SSO 身份尚未通过学校目录或角色映射确认，不能自动授予本地高权限角色，请等待管理员审核。";

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

    @Autowired
    private UserMapper userMapper;

    @Autowired(required = false)
    private SchoolStudentMapper schoolStudentMapper;

    @Autowired(required = false)
    private SchoolTeacherMapper schoolTeacherMapper;

    @Autowired(required = false)
    private SystemConfigService systemConfigService;

    /**
     * 处理 SSO 登录：绑定/创建用户，返回登录结果
     *
     * 角色同步策略：
     * - SSO/学校主数据可安全确认角色时，按升权规则同步到数据库
     * - 无法安全确认时，保留现有角色；首次接入授予 USER 并保留待审批
     * - 待审批用户重新登录时，优先尝试学校主数据自动升权，其次尝试可自动审批的 SSO 映射
     *
     * @param ssoUserInfo SSO 用户信息
     * @param protocol    SSO 协议类型
     * @return 登录结果
     */
    @Transactional(rollbackFor = Exception.class)
    public SsoLoginResult processLogin(SsoUserInfo ssoUserInfo, SsoProtocolType protocol) {
        String uid = normalizeIdentifier(ssoUserInfo.getSsoUsername());
        log.info("[SSO全链路] ① 登录入口: provider={}, ssoUserId={}, ssoUsername={}, realName={}",
                ssoUserInfo.getProvider(), ssoUserInfo.getSsoUserId(), uid, ssoUserInfo.getRealName());

        // 1. 查找是否已有绑定
        SsoUserBinding binding = bindingMapper.findByProviderAndSsoUserId(
                ssoUserInfo.getProvider(),
                ssoUserInfo.getSsoUserId()
        );

        // 尝试从 SSO 信息映射角色
        List<String> memberOf = ssoUserInfo.getMemberOf() == null ? Collections.emptyList() : ssoUserInfo.getMemberOf();
        String ssoMappedRole = roleMappingService.mapRole(memberOf);
        log.info("[SSO全链路] ② 角色映射: memberOf={}, ssoMappedRole={}", memberOf, ssoMappedRole);

        if (binding != null) {
            // 已绑定用户
            log.info("找到已有绑定: bindingId={}, userId={}", binding.getId(), binding.getUserId());

            if (binding.getAutoApproved() == 0) {
                User pendingUser = userService.getUserById(binding.getUserId());
                if (pendingUser == null) {
                    throw new SsoBusinessException(SsoErrorCode.USER_NOT_FOUND,
                            "SSO 绑定存在但本地用户不存在，请联系管理员");
                }
                if (isDisabledStatus(pendingUser.getStatus())) {
                    throw new SsoBusinessException(SsoErrorCode.AUTH_FAILED, "账号已被禁用，请联系管理员");
                }

                RoleUpgradeResult upgradeResult = tryUpgradeFromSchoolDirectory(pendingUser, ssoUserInfo, binding);
                if (upgradeResult.upgraded()) {
                    String token = jwtUtil.generateToken(pendingUser.getUsername(), pendingUser.getRole());
                    log.info("待审批用户根据学校数据自动升级并登录成功: username={}, role={}", pendingUser.getUsername(), pendingUser.getRole());
                    return SsoLoginResult.success(token, pendingUser);
                }

                if (ssoMappedRole != null && !roleMappingService.requiresManualConfirmation(ssoMappedRole, memberOf)) {
                    log.info("待审批用户角色可自动映射，自动审批: bindingId={}, role={}", binding.getId(), ssoMappedRole);
                    binding.setAutoApproved(1);
                    binding.setLastLoginTime(LocalDateTime.now());
                    bindingMapper.updateById(binding);
                    pendingUser.setRole(ssoMappedRole);
                    pendingUser.setStatus("ACTIVE");
                    userService.updateUser(pendingUser);
                    String token = jwtUtil.generateToken(pendingUser.getUsername(), pendingUser.getRole());
                    log.info("SSO 自动审批登录成功: username={}, role={}", pendingUser.getUsername(), pendingUser.getRole());
                    return SsoLoginResult.success(token, pendingUser);
                }

                if (isElevatedRole(pendingUser.getRole())) {
                    binding.setLastLoginTime(LocalDateTime.now());
                    bindingMapper.updateById(binding);
                    log.warn("待审批 SSO 绑定未通过目录/映射确认，不授予本地高权限角色: username={}, role={}, bindingId={}",
                            pendingUser.getUsername(), pendingUser.getRole(), binding.getId());
                    return SsoLoginResult.pendingApproval(binding.getId(), PRIVILEGED_REVIEW_MESSAGE);
                }

                pendingUser.setStatus("ACTIVE");
                pendingUser.setRole("USER");
                userService.updateUser(pendingUser);
                binding.setLastLoginTime(LocalDateTime.now());
                bindingMapper.updateById(binding);
                String token = jwtUtil.generateToken(pendingUser.getUsername(), pendingUser.getRole());
                log.info("待审批用户以普通用户身份登录: username={}, role={}, bindingId={}", pendingUser.getUsername(), pendingUser.getRole(), binding.getId());
                return SsoLoginResult.successWithPendingApproval(
                        token,
                        pendingUser,
                        binding.getId(),
                        DEFAULT_PENDING_USER_MESSAGE
                );
            }

            // 更新最后登录时间
            binding.setLastLoginTime(LocalDateTime.now());
            bindingMapper.updateById(binding);

            // 获取用户信息
            User user = userService.getUserById(binding.getUserId());
            if (user == null) {
                // 检查用户是否被软删除
                User deletedUser = userMapper.findDeletedById(binding.getUserId());
                if (deletedUser != null) {
                    log.warn("SSO 用户对应的本地账号已被删除，提交管理员审核: bindingId={}, userId={}, username={}, deletedTime={}",
                            binding.getId(), deletedUser.getId(), deletedUser.getUsername(), deletedUser.getUpdatedTime());

                    // 构建管理员备注
                    String remark = String.format(
                            "用户 %s (ID: %d) 曾于 %s 被删除，于 %s 通过 SSO 重新登录。" +
                            "删除前角色: %s，状态: %s。请管理员审核是否允许恢复访问。",
                            deletedUser.getUsername(),
                            deletedUser.getId(),
                            deletedUser.getUpdatedTime(),
                            LocalDateTime.now(),
                            deletedUser.getRole(),
                            deletedUser.getStatus()
                    );

                    // 更新绑定为待审批状态，并添加备注
                    binding.setAutoApproved(0);
                    binding.setAdminRemark(remark);
                    bindingMapper.updateById(binding);

                    log.info("已将绑定设为待审批状态并添加备注: bindingId={}", binding.getId());
                    return SsoLoginResult.pendingApproval(
                            binding.getId(),
                            "您的账号曾被删除，需要管理员重新审核。\n如有疑问请致电：计算机与信息工程学院43019235"
                    );
                }

                // 用户彻底不存在（硬删除或数据异常）
                throw new SsoBusinessException(SsoErrorCode.USER_NOT_FOUND,
                        "SSO 绑定已存在但对应用户丢失，请联系管理员");
            }

            // 仅对待审批残留状态自动激活；其他状态（如 DISABLED）保持原限制
            if ("PENDING_APPROVAL".equals(user.getStatus())) {
                log.info("已审批用户状态为 PENDING_APPROVAL，自动激活: userId={}", user.getId());
                user.setStatus("ACTIVE");
                userService.updateUser(user);
            } else if (!"ACTIVE".equals(user.getStatus())) {
                throw new SsoBusinessException(SsoErrorCode.AUTH_FAILED, "账户已被禁用，请联系管理员");
            }

            RoleUpgradeResult approvedUpgradeResult = tryUpgradeFromSchoolDirectory(user, ssoUserInfo, binding);
            if (approvedUpgradeResult.upgraded()) {
                String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
                log.info("已审批用户根据学校数据自动升级并登录成功: username={}, role={}", user.getUsername(), user.getRole());
                return SsoLoginResult.success(token, user);
            }

            // SSO 仅在安全可接受的升权场景下同步角色；否则保留数据库角色
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
                    if (roleMappingService.requiresManualConfirmation(ssoMappedRole, memberOf)) {
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
            if (isDisabledStatus(existingUser.getStatus())) {
                throw new SsoBusinessException(SsoErrorCode.AUTH_FAILED, "账号已被禁用，请联系管理员");
            }

            existingUser.setSsoBound(1);
            existingUser.setSsoProvider(ssoUserInfo.getProvider());
            existingUser.setSsoSubject(ssoUserInfo.getSsoUserId());
            existingUser.setRealName(ssoUserInfo.getRealName());

            RoleUpgradeResult upgradeResult = tryUpgradeFromSchoolDirectory(existingUser, ssoUserInfo, null);
            if (upgradeResult.upgraded()) {
                SsoUserBinding newBinding = createBinding(existingUser.getId(), ssoUserInfo, protocol, true);
                String token = jwtUtil.generateToken(existingUser.getUsername(), existingUser.getRole());
                log.info("SSO 登录成功（关联已有用户并按学校数据升级）: username={}, role={}, bindingId={}", existingUser.getUsername(), existingUser.getRole(), newBinding.getId());
                return SsoLoginResult.success(token, existingUser);
            }

            if (ssoMappedRole != null && isRoleUpgrade(existingUser.getRole(), ssoMappedRole)
                    && !roleMappingService.requiresManualConfirmation(ssoMappedRole, memberOf)) {
                existingUser.setRole(ssoMappedRole);
                SsoUserBinding newBinding = createBinding(existingUser.getId(), ssoUserInfo, protocol, true);
                userService.updateUser(existingUser);
                String token = jwtUtil.generateToken(existingUser.getUsername(), existingUser.getRole());
                log.info("SSO 登录成功（关联已有用户并按SSO升权）: username={}, role={}, bindingId={}", existingUser.getUsername(), existingUser.getRole(), newBinding.getId());
                return SsoLoginResult.success(token, existingUser);
            }

            SsoUserBinding newBinding = createBinding(existingUser.getId(), ssoUserInfo, protocol, false);
            if (isElevatedRole(existingUser.getRole())) {
                log.warn("已有本地高权限账号缺少可验证的 SSO/学校目录角色证据，转入人工审核: username={}, role={}, bindingId={}",
                        existingUser.getUsername(), existingUser.getRole(), newBinding.getId());
                return SsoLoginResult.pendingApproval(newBinding.getId(), PRIVILEGED_REVIEW_MESSAGE);
            }

            existingUser.setStatus("ACTIVE");
            existingUser.setRole("USER");
            userService.updateUser(existingUser);
            String token = jwtUtil.generateToken(existingUser.getUsername(), existingUser.getRole());
            log.info("SSO 登录成功（关联已有用户为 USER 待审批）: username={}, role={}, bindingId={}", existingUser.getUsername(), existingUser.getRole(), newBinding.getId());
            return SsoLoginResult.successWithPendingApproval(
                    token,
                    existingUser,
                    newBinding.getId(),
                    DEFAULT_PENDING_USER_MESSAGE
            );
        }

        // 2.5 链式主数据自动授权：school_student / school_teacher 命中时直接创建对应角色账户
        //     优先级：student > teacher > 原流程（避免学号与工号号段重叠时误判）
        SsoLoginResult autoGrantResult = tryAutoGrantFromSchoolDirectory(ssoUserInfo, protocol);
        if (autoGrantResult != null) {
            return autoGrantResult;
        }
        log.info("[SSO全链路] ④ 自动授权未命中，进入原有流程: uid={}", normalizeIdentifier(ssoUserInfo.getSsoUsername()));

        // 3. 全新用户：自动识别角色
        if (ssoMappedRole == null) {
            log.warn("无法识别角色，授予普通用户 USER，并保留待审批状态等待管理员提升权限");
            User newUser = createUserFromSso(ssoUserInfo, "USER");
            SsoUserBinding pendingBinding = createBinding(newUser.getId(), ssoUserInfo, protocol, false);
            String token = jwtUtil.generateToken(newUser.getUsername(), newUser.getRole());
            log.info("SSO 登录成功（无法识别角色时授予 USER 且待审批）: username={}, role={}, bindingId={}", newUser.getUsername(), newUser.getRole(), pendingBinding.getId());
            return SsoLoginResult.successWithPendingApproval(
                    token,
                    newUser,
                    pendingBinding.getId(),
                    "当前已授予普通用户权限，您仍可正常登录；如需学生/教师等更高权限，请等待管理员审核。"
            );
        }

        // 4. 检查是否需要人工确认
        if (roleMappingService.requiresManualConfirmation(ssoMappedRole, memberOf)) {
            log.info("角色 {} 需要人工确认，先授予 USER 并保留待审批", ssoMappedRole);
            User newUser = createUserFromSso(ssoUserInfo, "USER");
            SsoUserBinding pendingBinding = createBinding(newUser.getId(), ssoUserInfo, protocol, false);
            String token = jwtUtil.generateToken(newUser.getUsername(), newUser.getRole());
            return SsoLoginResult.successWithPendingApproval(
                    token,
                    newUser,
                    pendingBinding.getId(),
                    "当前已授予普通用户权限，您仍可正常登录；如需更高权限，请等待管理员审核。"
            );
        }

        // 5. 自动创建用户并绑定
        log.info("自动创建用户: username={}, role={}", ssoUserInfo.getSsoUsername(), ssoMappedRole);
        User newUser = createUserFromSso(ssoUserInfo, ssoMappedRole);
        createBinding(newUser.getId(), ssoUserInfo, protocol, true);

        String token = jwtUtil.generateToken(newUser.getUsername(), newUser.getRole());

        log.info("SSO 登录成功（新用户）: username={}, role={}", newUser.getUsername(), newUser.getRole());
        return SsoLoginResult.success(token, newUser);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 从 SSO 信息创建用户
     */
    private User createUserFromSso(SsoUserInfo ssoInfo, String role) {
        User user = new User();
        user.setUsername(normalizeIdentifier(ssoInfo.getSsoUsername()));
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

    private record RoleUpgradeResult(boolean upgraded) {}

    private RoleUpgradeResult tryUpgradeFromSchoolDirectory(User user, SsoUserInfo ssoUserInfo, SsoUserBinding binding) {
        if (user == null) {
            return new RoleUpgradeResult(false);
        }
        String username = normalizeIdentifier(user.getUsername());
        if (username == null) {
            return new RoleUpgradeResult(false);
        }

        // 教师优先：工号查 school_teacher，避免教师被误判为学生
        if (schoolTeacherMapper != null && isFeatureEnabled("sso.auto_grant_teacher")) {
            SchoolTeacher teacher = schoolTeacherMapper.selectByTeacherId(username);
            if (teacher != null && isRoleUpgrade(user.getRole(), "TEACHER")) {
                if ((user.getRealName() == null || user.getRealName().isEmpty()) && teacher.getTeacherName() != null) {
                    user.setRealName(teacher.getTeacherName());
                }
                user.setRole("TEACHER");
                user.setStatus("ACTIVE");
                userService.updateUser(user);
                if (binding != null) {
                    binding.setAutoApproved(1);
                    binding.setLastLoginTime(LocalDateTime.now());
                    bindingMapper.updateById(binding);
                }
                return new RoleUpgradeResult(true);
            }
        }

        if (schoolStudentMapper != null && isFeatureEnabled("sso.auto_grant_student")) {
            SchoolStudent student = schoolStudentMapper.selectByStudentId(username);
            if (student != null && isRoleUpgrade(user.getRole(), "STUDENT")) {
                backfillStudentClassInfo(username, ssoUserInfo.getAttributes());
                if ((user.getRealName() == null || user.getRealName().isEmpty()) && student.getStudentName() != null) {
                    user.setRealName(student.getStudentName());
                }
                user.setRole("STUDENT");
                user.setStatus("ACTIVE");
                userService.updateUser(user);
                if (binding != null) {
                    binding.setAutoApproved(1);
                    binding.setLastLoginTime(LocalDateTime.now());
                    bindingMapper.updateById(binding);
                }
                return new RoleUpgradeResult(true);
            }
        }

        return new RoleUpgradeResult(false);
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

    private boolean isElevatedRole(String role) {
        String normalizedRole = firstNonBlank(role);
        return normalizedRole != null && !"USER".equals(normalizedRole);
    }

    private boolean isDisabledStatus(String status) {
        String normalizedStatus = firstNonBlank(status);
        return normalizedStatus != null
                && !"ACTIVE".equals(normalizedStatus)
                && !"PENDING_APPROVAL".equals(normalizedStatus);
    }

    private String normalizeIdentifier(String value) {
        return firstNonBlank(value);
    }

    // =========================================================================
    // 链式主数据自动授权（school_student / school_teacher）
    // =========================================================================

    /**
     * 根据 school_student / school_teacher 主数据自动授权：
     * - 命中 school_student 且 feature toggle sso.auto_grant_student=true → 创建 STUDENT
     * - 命中 school_teacher 且 feature toggle sso.auto_grant_teacher=true → 创建 TEACHER
     * - 未命中或未启用 → 返回 null，走原流程
     *
     * 返回 SsoLoginResult 表示已处理；返回 null 表示跳过自动授权。
     */
    private SsoLoginResult tryAutoGrantFromSchoolDirectory(SsoUserInfo ssoUserInfo, SsoProtocolType protocol) {
        if (schoolStudentMapper == null && schoolTeacherMapper == null) {
            log.info("[SSO全链路] ③ 自动授权跳过: schoolStudentMapper={}, schoolTeacherMapper={}",
                    schoolStudentMapper != null, schoolTeacherMapper != null);
            return null;
        }
        String uid = normalizeIdentifier(ssoUserInfo.getSsoUsername());
        if (uid == null) {
            log.warn("[SSO全链路] ③ 自动授权跳过: ssoUsername 为空");
            return null;
        }

        boolean teacherToggle = isFeatureEnabled("sso.auto_grant_teacher");
        boolean studentToggle = isFeatureEnabled("sso.auto_grant_student");
        log.info("[SSO全链路] ③ 自动授权检查: uid={}, teacher_toggle={}, student_toggle={}",
                uid, teacherToggle, studentToggle);

        // 教师优先：工号查 school_teacher，避免教师被误判为学生
        if (schoolTeacherMapper != null && teacherToggle) {
            SchoolTeacher st = null;
            try {
                st = schoolTeacherMapper.selectByTeacherId(uid);
            } catch (Exception e) {
                log.warn("查询 school_teacher 失败 uid={}: {}", uid, e.getMessage());
            }
            log.info("[SSO全链路] ③ school_teacher 查询: uid={}, 命中={}", uid, st != null);
            if (st != null) {
                log.info("school_teacher 命中，自动授权 TEACHER: uid={}, college={}", uid, st.getCollegeName());
                if ((ssoUserInfo.getRealName() == null || ssoUserInfo.getRealName().isEmpty())
                        && st.getTeacherName() != null) {
                    ssoUserInfo.setRealName(st.getTeacherName());
                }
                User teacher = createUserFromSso(ssoUserInfo, "TEACHER");
                SsoUserBinding binding = createBinding(teacher.getId(), ssoUserInfo, protocol, true);
                String token = jwtUtil.generateToken(teacher.getUsername(), teacher.getRole());
                log.info("SSO 自动授权 TEACHER 成功: username={}, bindingId={}", teacher.getUsername(), binding.getId());
                return SsoLoginResult.success(token, teacher);
            }
        }

        // 学生次之：学号查 school_student
        if (schoolStudentMapper != null && studentToggle) {
            SchoolStudent ss = null;
            try {
                ss = schoolStudentMapper.selectByStudentId(uid);
            } catch (Exception e) {
                log.warn("查询 school_student 失败 uid={}: {}", uid, e.getMessage());
            }
            log.info("[SSO全链路] ③ school_student 查询: uid={}, 命中={}", uid, ss != null);
            if (ss != null) {
                log.info("school_student 命中，自动授权 STUDENT: uid={}, college={}", uid, ss.getCollegeName());
                backfillStudentClassInfo(uid, ssoUserInfo.getAttributes());
                if ((ssoUserInfo.getRealName() == null || ssoUserInfo.getRealName().isEmpty())
                        && ss.getStudentName() != null) {
                    ssoUserInfo.setRealName(ss.getStudentName());
                }
                User student = createUserFromSso(ssoUserInfo, "STUDENT");
                SsoUserBinding binding = createBinding(student.getId(), ssoUserInfo, protocol, true);
                String token = jwtUtil.generateToken(student.getUsername(), student.getRole());
                log.info("SSO 自动授权 STUDENT 成功: username={}, bindingId={}", student.getUsername(), binding.getId());
                return SsoLoginResult.success(token, student);
            }
        }

        log.info("[SSO全链路] ③ 自动授权未命中: uid={}, 走原有流程", uid);
        return null;
    }
     * 兼容常见字段名：class / className / classCode / major / majorName / department。
     */
    private void backfillStudentClassInfo(String studentId, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty() || schoolStudentMapper == null) return;
        String classCode = firstNonEmpty(attributes, "classCode", "class_code");
        String className = firstNonEmpty(attributes, "className", "class_name", "class");
        String majorName = firstNonEmpty(attributes, "majorName", "major_name", "major", "majorDescription");
        if (classCode == null && className == null && majorName == null) {
            return;
        }
        try {
            schoolStudentMapper.updateClassInfo(studentId, classCode, className, majorName);
            log.info("从 CAS attributes 回填 school_student.class_* 成功: uid={}, classCode={}, className={}, major={}",
                    studentId, classCode, className, majorName);
        } catch (Exception e) {
            log.warn("回填 school_student.class_* 失败 uid={}: {}", studentId, e.getMessage());
        }
    }

    private String firstNonEmpty(Map<String, Object> attrs, String... keys) {
        for (String key : keys) {
            Object v = attrs.get(key);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
            }
        }
        return null;
    }

    /**
     * 读取 feature toggle（system_config.feature_toggle 分组）。
     */
    private boolean isFeatureEnabled(String key) {
        if (systemConfigService == null) return false;
        try {
            String value = systemConfigService.get("feature_toggle", key, "false");
            return "true".equalsIgnoreCase(value) || "1".equals(value);
        } catch (Exception e) {
            log.warn("读取 feature toggle {} 失败: {}", key, e.getMessage());
            return false;
        }
    }
}
