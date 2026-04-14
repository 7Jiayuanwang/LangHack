package com.english.sso.service;

import com.english.sso.dto.SsoLoginResult;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.entity.SsoLoginLog;
import com.english.sso.mapper.SsoLoginLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * SSO 登录日志服务
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Slf4j
@Service
@Profile("sso")
public class SsoLoginLogService {

    @Autowired
    private SsoLoginLogMapper loginLogMapper;

    /**
     * 记录 SSO 登录日志
     *
     * @param ssoUserInfo SSO 用户信息
     * @param result      登录结果
     * @param request     HTTP 请求
     */
    public void log(SsoUserInfo ssoUserInfo, SsoLoginResult result, HttpServletRequest request) {
        try {
            SsoLoginLog loginLog = new SsoLoginLog();
            loginLog.setSsoProvider(ssoUserInfo.getProvider());
            loginLog.setSsoUserId(ssoUserInfo.getSsoUserId());
            loginLog.setSsoUsername(ssoUserInfo.getSsoUsername());

            if (result.isNeedsApproval()) {
                loginLog.setLoginResult("PENDING_APPROVAL");
                loginLog.setFailureReason(result.getMessage());
            } else {
                loginLog.setLoginResult("SUCCESS");
            }

            loginLog.setIpAddress(getClientIp(request));
            loginLog.setUserAgent(request.getHeader("User-Agent"));
            loginLog.setCreatedTime(LocalDateTime.now());

            loginLogMapper.insert(loginLog);
            log.debug("记录 SSO 登录日志: provider={}, userId={}, result={}",
                    ssoUserInfo.getProvider(), ssoUserInfo.getSsoUserId(), loginLog.getLoginResult());

        } catch (Exception e) {
            log.error("记录 SSO 登录日志失败", e);
            // 不抛出异常，避免影响登录流程
        }
    }

    /**
     * 记录 SSO 登录失败日志
     *
     * @param provider SSO 提供商
     * @param userId   SSO 用户ID
     * @param username SSO 用户名
     * @param reason   失败原因
     * @param request  HTTP 请求
     */
    public void logFailure(String provider, String userId, String username, String reason, HttpServletRequest request) {
        try {
            SsoLoginLog loginLog = new SsoLoginLog();
            loginLog.setSsoProvider(provider);
            loginLog.setSsoUserId(userId);
            loginLog.setSsoUsername(username);
            loginLog.setLoginResult("FAILED");
            loginLog.setFailureReason(reason);
            loginLog.setIpAddress(getClientIp(request));
            loginLog.setUserAgent(request.getHeader("User-Agent"));
            loginLog.setCreatedTime(LocalDateTime.now());

            loginLogMapper.insert(loginLog);
            log.debug("记录 SSO 登录失败日志: provider={}, userId={}, reason={}",
                    provider, userId, reason);

        } catch (Exception e) {
            log.error("记录 SSO 登录失败日志失败", e);
        }
    }

    /**
     * 获取客户端 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
