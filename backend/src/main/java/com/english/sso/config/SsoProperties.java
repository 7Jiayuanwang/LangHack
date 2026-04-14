package com.english.sso.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.*;

/**
 * SSO 配置属性
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sso")
@Profile("sso")
public class SsoProperties {

    /**
     * SSO 功能总开关
     */
    private boolean enabled = false;

    /**
     * SSO 协议类型: CAS 或 OAUTH2
     */
    private String protocol = "CAS";

    /**
     * CAS 配置
     */
    private CasConfig cas = new CasConfig();

    /**
     * OAuth 2.0 配置
     */
    private OAuth2Config oauth2 = new OAuth2Config();

    /**
     * 角色映射配置
     */
    private RoleMappingConfig roleMapping = new RoleMappingConfig();

    /**
     * 自动审批配置
     */
    private AutoApprovalConfig autoApproval = new AutoApprovalConfig();

    @Data
    public static class CasConfig {
        private boolean enabled = true;
        /**
         * CAS 服务端地址（不含 /login）
         * 对应官方 web.xml 的 casServerUrlPrefix
         * 示例: https://ids.imau.edu.cn/authserver
         */
        private String serverUrl;
        /**
         * 应用服务器地址（协议+域名+端口）
         * 对应官方 web.xml 的 serverName
         * 示例: https://langhack.imau.edu.cn
         *
         * service URL = serverName + "/api/auth/sso/callback"
         * 此值必须与 CAS 服务器注册的域名一致
         */
        private String serverName;
        private Map<String, String> attributes = new HashMap<>();
    }

    @Data
    public static class OAuth2Config {
        private boolean enabled = false;
        private String authorizationUrl;
        private String tokenUrl;
        private String userinfoUrl;
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String scope = "openid,profile,email";
    }

    @Data
    public static class RoleMappingConfig {
        private Map<String, String> defaultRules = new HashMap<>();
    }

    @Data
    public static class AutoApprovalConfig {
        private boolean enabled = true;
        private List<String> allowedRoles = Arrays.asList("TEACHER", "STUDENT", "USER");
    }
}
