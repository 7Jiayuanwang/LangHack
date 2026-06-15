package com.english.sso.dto;

import com.english.entity.User;
import lombok.Data;

/**
 * SSO 登录结果 DTO
 *
 * <p>双 token 设计：
 * <ul>
 *   <li>{@link #accessToken}：短期（2h），用于访问受保护资源</li>
 *   <li>{@link #refreshToken}：长期（30d），仅用于 /api/auth/refresh 换取新 accessToken</li>
 *   <li>{@link #token}：保留字段（=accessToken），用于向后兼容旧前端</li>
 * </ul>
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Data
public class SsoLoginResult {

    /**
     * JWT token（兼容字段 = accessToken）。新代码优先使用 accessToken/refreshToken。
     */
    private String token;

    /**
     * 短期 access token（2 小时），含 role + type=access claim。
     */
    private String accessToken;

    /**
     * 长期 refresh token（30 天），不含 role claim，仅用于 /api/auth/refresh。
     */
    private String refreshToken;

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

    /**
     * 构造成功结果（双 token）。
     */
    public static SsoLoginResult success(String accessToken, String refreshToken, User user) {
        SsoLoginResult result = new SsoLoginResult();
        result.setAccessToken(accessToken);
        result.setRefreshToken(refreshToken);
        result.setToken(accessToken);
        result.setUser(user);
        result.setNeedsApproval(false);
        result.setApprovalPendingAfterLogin(false);
        return result;
    }

    /**
     * 构造待审批结果（双 token，前端需展示审批提示并等待管理员审核）。
     */
    public static SsoLoginResult successWithPendingApproval(String accessToken, String refreshToken, User user, Long bindingId, String message) {
        SsoLoginResult result = success(accessToken, refreshToken, user);
        result.setApprovalPendingAfterLogin(true);
        result.setBindingId(bindingId);
        result.setMessage(message);
        return result;
    }

    /**
     * 旧 API 兼容包装（无 refresh token）。新代码应使用 {@link #success(String, String, User)}。
     *
     * @deprecated refresh token 机制接入后调用方应传入 refreshToken；保留此方法仅用于
     *             未接入 refresh token 的旧路径（理论应为零）。
     */
    @Deprecated
    public static SsoLoginResult success(String token, User user) {
        return success(token, null, user);
    }

    /**
     * 旧 API 兼容包装（无 refresh token）。新代码应使用
     * {@link #successWithPendingApproval(String, String, User, Long, String)}。
     */
    @Deprecated
    public static SsoLoginResult successWithPendingApproval(String token, User user, Long bindingId, String message) {
        return successWithPendingApproval(token, null, user, bindingId, message);
    }

    /**
     * 完全待审批（无 token），用户需等待管理员审核。
     */
    public static SsoLoginResult pendingApproval(Long bindingId, String message) {
        SsoLoginResult result = new SsoLoginResult();
        result.setNeedsApproval(true);
        result.setApprovalPendingAfterLogin(false);
        result.setBindingId(bindingId);
        result.setMessage(message);
        return result;
    }
}
