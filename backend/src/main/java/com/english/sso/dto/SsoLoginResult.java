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
        return result;
    }

    public static SsoLoginResult pendingApproval(Long bindingId, String message) {
        SsoLoginResult result = new SsoLoginResult();
        result.setNeedsApproval(true);
        result.setBindingId(bindingId);
        result.setMessage(message);
        return result;
    }
}
