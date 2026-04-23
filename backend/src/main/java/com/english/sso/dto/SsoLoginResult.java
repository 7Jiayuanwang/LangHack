package com.english.sso.dto;

import com.english.entity.User;
import lombok.Data;

/**
 * SSO 登录结果 DTO
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Data
public class SsoLoginResult {

    /**
     * JWT token
     */
    private String token;

    /**
     * 用户信息
     */
    private User user;

    /**
     * 是否需要审批
     */
    private boolean needsApproval;

    /**
     * 登录后是否仍保留待审批标记（可登录但等待管理员提升权限）
     */
    private boolean approvalPendingAfterLogin;

    /**
     * 绑定ID（待审批时使用）
     */
    private Long bindingId;

    /**
     * 提示信息
     */
    private String message;

    public static SsoLoginResult success(String token, User user) {
        SsoLoginResult result = new SsoLoginResult();
        result.setToken(token);
        result.setUser(user);
        result.setNeedsApproval(false);
        result.setApprovalPendingAfterLogin(false);
        return result;
    }

    public static SsoLoginResult successWithPendingApproval(String token, User user, Long bindingId, String message) {
        SsoLoginResult result = success(token, user);
        result.setApprovalPendingAfterLogin(true);
        result.setBindingId(bindingId);
        result.setMessage(message);
        return result;
    }

    public static SsoLoginResult pendingApproval(Long bindingId, String message) {
        SsoLoginResult result = new SsoLoginResult();
        result.setNeedsApproval(true);
        result.setApprovalPendingAfterLogin(false);
        result.setBindingId(bindingId);
        result.setMessage(message);
        return result;
    }
}
