package com.english.config;

import com.english.exception.SecurityExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.security.web.header.writers.StaticHeadersWriter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private Environment environment;

    /**
     * CORS allowed origins, read from config / environment variable.
     * CORS 允许的来源，从配置文件或环境变量读取。
     *
     * Supports two formats / 支持两种格式:
     *   1. Exact origins (精确匹配): http://localhost:3000, https://example.com
     *   2. Wildcard patterns (通配符): https://*.example.com (set via app.cors.allowed-origin-patterns)
     *
     * Production setup / 生产环境:
     *   export APP_CORS_ALLOWED_ORIGINS=https://your-frontend.example.com
     *   # For multiple origins / 多个域名用逗号分隔:
     *   export APP_CORS_ALLOWED_ORIGINS=https://www.example.com,https://admin.example.com
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOriginsConfig;

    /**
     * CORS allowed origin patterns for wildcard subdomain matching.
     * 用于通配符子域名匹配的 CORS 来源模式。
     *
     * Example / 示例: https://*.example.com
     * Leave empty if not needed / 不需要时留空。
     */
    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatternsConfig;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private SecurityExceptionHandler securityExceptionHandler;

    /**
     * 启动时校验 CORS 配置：生产环境禁止 localhost 和通配符 *。
     */
    @PostConstruct
    public void validateCorsConfig() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            if (origins.isEmpty()) {
                throw new IllegalStateException(
                        "CORS allowed-origins must be configured in production. "
                        + "Set APP_CORS_ALLOWED_ORIGINS environment variable.");
            }

            for (String origin : origins) {
                if ("*".equals(origin)) {
                    throw new IllegalStateException(
                            "CORS wildcard '*' is not allowed in production. "
                            + "Set APP_CORS_ALLOWED_ORIGINS to specific domain(s).");
                }
                if (origin.contains("localhost") || origin.contains("127.0.0.1")) {
                    throw new IllegalStateException(
                            "CORS origin '" + origin + "' contains localhost, "
                            + "which is not allowed in production. "
                            + "Set APP_CORS_ALLOWED_ORIGINS to production domain(s).");
                }
            }
            log.info("CORS config validated for production: {}", origins);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Require HTTPS in production (supports both -Dspring.profiles.active and SPRING_PROFILES_ACTIVE)
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            http.requiresChannel().anyRequest().requiresSecure();
        }

        http
                .headers()
                    .frameOptions().deny()
                    .contentTypeOptions().and()
                    .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .addHeaderWriter(new StaticHeadersWriter("X-XSS-Protection", "0"))
                    .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy",
                        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; media-src 'self' https:; connect-src 'self'"))
                .and()
                .cors().configurationSource(corsConfigurationSource())
                .and()
                .csrf().disable() // CSRF disabled: JWT-based stateless API (not vulnerable to CSRF)
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                // 允许访问的接口 - 无需认证
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/api/public/**").permitAll()
                .antMatchers("/api/wordnet/**").permitAll() // 允许公开访问单词查询接口
                .antMatchers(HttpMethod.GET, "/api/media/**").permitAll() // 媒体文件仅 GET 公开
                .antMatchers("/").permitAll()
                .antMatchers("/api/health").permitAll()
                // 管理员权限
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                // 运维权限
                .antMatchers("/api/ops/**").hasAnyRole("OPS", "ADMIN")
                // 教师权限
                .antMatchers("/api/teacher/**").hasAnyRole("TEACHER", "ADMIN")
                // 学生权限
                .antMatchers("/api/student/**").hasAnyRole("USER", "STUDENT", "TEACHER", "ADMIN")
                // 普通用户权限
                .antMatchers("/api/user/**").hasAnyRole("USER", "STUDENT", "TEACHER", "ADMIN")
                // 付费第三方 API（翻译、ASR、Bilibili代理）— 必须登录，防止恶意消耗配额
                .antMatchers("/api/translate/**").authenticated()
                .antMatchers("/api/asr/**").authenticated()
                .antMatchers("/api/bilibili/**").authenticated()
                // 其他请求需要认证
                .anyRequest().authenticated()
                .and()
                .exceptionHandling()
                    .authenticationEntryPoint(securityExceptionHandler)
                    .accessDeniedHandler(securityExceptionHandler)
                .and()
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // ── Exact origin matching / 精确来源匹配 ──
        // Read from config; defaults to localhost for local dev.
        // 从配置读取；默认为 localhost 用于本地开发。
        // Production: set APP_CORS_ALLOWED_ORIGINS env var / 生产环境：设置 APP_CORS_ALLOWED_ORIGINS 环境变量
        List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (origins.isEmpty()) {
            // Fallback to defaults to prevent CORS from being completely unconfigured
            // 回退到默认值，防止 CORS 完全未配置
            origins = Arrays.asList("http://localhost:3000", "http://localhost:5173");
        }

        configuration.setAllowedOrigins(origins);

        // ── Wildcard pattern matching / 通配符模式匹配 ──
        // Supports patterns like https://*.example.com for subdomain matching.
        // 支持如 https://*.example.com 的模式用于子域名匹配。
        // Set via APP_CORS_ALLOWED_ORIGIN_PATTERNS env var / 通过 APP_CORS_ALLOWED_ORIGIN_PATTERNS 设置
        if (allowedOriginPatternsConfig != null && !allowedOriginPatternsConfig.trim().isEmpty()) {
            List<String> patterns = Arrays.stream(allowedOriginPatternsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (!patterns.isEmpty()) {
                configuration.setAllowedOriginPatterns(patterns);
                log.info("CORS allowed origin patterns: {}", patterns);
            }
        }

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "Accept", "X-Requested-With"));
        configuration.setMaxAge(3600L); // Preflight cache 1h / preflight 缓存 1 小时
        configuration.setAllowCredentials(false);

        // Log active CORS origins for troubleshooting / 记录生效的 CORS 来源便于排查
        log.info("CORS allowed origins: {}", origins);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Register CORS for ALL /api/** paths to prevent missing new endpoints.
        // 对所有 /api/** 路径注册 CORS，避免新增端点遗漏。
        // Security is enforced by Spring Security authorization rules, not by CORS path registration.
        // 安全性由 Spring Security 授权规则保障，而非 CORS 路径注册。
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}