package com.english.sso.controller;

import com.alibaba.fastjson.JSON;
import com.english.dto.Result;
import com.english.entity.User;
import com.english.mapper.UserMapper;
import com.english.service.UserService;
import com.english.sso.dto.PendingUserDto;
import com.english.sso.entity.SsoUserBinding;
import com.english.sso.mapper.SsoUserBindingMapper;
import com.english.sso.service.RoleMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SSO 管理员控制器
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/sso")
@Profile("sso")
@ConditionalOnProperty(name = "sso.enabled", havingValue = "true")
@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
public class SsoAdminController {

    @Autowired
    private SsoUserBindingMapper bindingMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMappingService roleMappingService;

    /**
     * 获取待审批用户列表
     */
    @GetMapping("/pending-approvals")
    public Result<List<PendingUserDto>> getPendingApprovals() {
        try {
            List<SsoUserBinding> pendingBindings = bindingMapper.findPendingApprovals();

            List<PendingUserDto> result = pendingBindings.stream()
                    .map(this::convertToPendingUserDto)
                    .collect(Collectors.toList());

            return Result.success(result);

        } catch (Exception e) {
            log.error("获取待审批用户列表失败", e);
            return Result.error("获取待审批用户列表失败");
        }
    }

    /**
     * 审批用户
     */
    @PostMapping("/approve-user")
    public Result<String> approveUser(@RequestBody Map<String, Object> request) {
        try {
            Long bindingId = Long.valueOf(request.get("bindingId").toString());
            String approvedRole = (String) request.get("approvedRole");

            SsoUserBinding binding = bindingMapper.selectById(bindingId);
            if (binding == null) {
                return Result.error("绑定记录不存在");
            }

            // 更新用户角色和状态
            User user = userService.getUserById(binding.getUserId());
            if (user == null) {
                return Result.error("用户不存在");
            }

            user.setRole(approvedRole);
            user.setStatus("ACTIVE");
            userService.updateUser(user);

            // 更新绑定状态
            binding.setAutoApproved(1);
            bindingMapper.updateById(binding);

            log.info("审批用户成功: bindingId={}, userId={}, role={}", bindingId, user.getId(), approvedRole);
            return Result.success("审批成功");

        } catch (Exception e) {
            log.error("审批用户失败", e);
            return Result.error("审批失败: " + e.getMessage());
        }
    }

    /**
     * 拒绝用户
     */
    @PostMapping("/reject-user")
    public Result<String> rejectUser(@RequestBody Map<String, Object> request) {
        try {
            Long bindingId = Long.valueOf(request.get("bindingId").toString());

            SsoUserBinding binding = bindingMapper.selectById(bindingId);
            if (binding == null) {
                return Result.error("绑定记录不存在");
            }

            // 删除用户和绑定
            userService.deleteUser(binding.getUserId());
            bindingMapper.deleteById(bindingId);

            log.info("拒绝用户成功: bindingId={}, userId={}", bindingId, binding.getUserId());
            return Result.success("已拒绝");

        } catch (Exception e) {
            log.error("拒绝用户失败", e);
            return Result.error("拒绝失败: " + e.getMessage());
        }
    }

    /**
     * 查看所有 SSO 绑定
     */
    @GetMapping("/bindings")
    public Result<List<SsoUserBinding>> getAllBindings(
            @RequestParam(required = false) String provider) {
        try {
            List<SsoUserBinding> bindings;
            if (provider != null && !provider.isEmpty()) {
                bindings = bindingMapper.findByProvider(provider);
            } else {
                bindings = bindingMapper.selectList(null);
            }

            return Result.success(bindings);

        } catch (Exception e) {
            log.error("获取 SSO 绑定列表失败", e);
            return Result.error("获取绑定列表失败");
        }
    }

    /**
     * 获取 SSO 统计信息
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getSsoStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 总用户数 - 使用 UserMapper 的 selectCount 方法
            long totalUsers = userMapper.selectCount(null);
            stats.put("totalUsers", totalUsers);

            // SSO 用户数
            long ssoUsers = bindingMapper.selectCount(null);
            stats.put("ssoUsers", ssoUsers);

            // 待审批用户数
            List<SsoUserBinding> pendingBindings = bindingMapper.findPendingApprovals();
            stats.put("pendingUsers", pendingBindings.size());

            // 按提供商统计
            List<SsoUserBinding> casBindings = bindingMapper.findByProvider("CAS");
            List<SsoUserBinding> oauth2Bindings = bindingMapper.findByProvider("OAUTH2");
            Map<String, Long> providerStats = new HashMap<>();
            providerStats.put("CAS", (long) casBindings.size());
            providerStats.put("OAUTH2", (long) oauth2Bindings.size());
            stats.put("providerStats", providerStats);

            return Result.success(stats);

        } catch (Exception e) {
            log.error("获取 SSO 统计信息失败", e);
            return Result.error("获取统计信息失败");
        }
    }

    /**
     * 转换为待审批用户 DTO
     */
    private PendingUserDto convertToPendingUserDto(SsoUserBinding binding) {
        PendingUserDto dto = new PendingUserDto();
        dto.setBindingId(binding.getId());
        dto.setUserId(binding.getUserId());
        dto.setUsername(binding.getSsoUsername());
        dto.setRealName(binding.getSsoRealName());
        dto.setEmail(binding.getSsoEmail());
        dto.setCreatedTime(binding.getCreatedTime());

        // 解析 memberOf
        List<String> memberOf = new ArrayList<>();
        try {
            if (binding.getSsoMemberOf() != null && !binding.getSsoMemberOf().isEmpty()) {
                memberOf = JSON.parseArray(binding.getSsoMemberOf(), String.class);
            }
        } catch (Exception e) {
            log.warn("解析 memberOf 失败: {}", binding.getSsoMemberOf(), e);
        }
        dto.setMemberOf(memberOf);

        // 推断角色
        String inferredRole = roleMappingService.mapRole(memberOf);
        dto.setInferredRole(inferredRole != null ? inferredRole : "USER");

        return dto;
    }
}
