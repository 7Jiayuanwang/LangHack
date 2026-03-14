package com.english.config;

import com.english.exception.SecurityExceptionHandler;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOriginsConfig;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private SecurityExceptionHandler securityExceptionHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Require HTTPS in production (except localhost for development)
        String profile = System.getProperty("spring.profiles.active", "dev");
        if ("prod".equalsIgnoreCase(profile)) {
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
        // 从配置读取允许的来源，本地开发默认允许 localhost，生产环境通过环境变量 APP_CORS_ALLOWED_ORIGINS 指定
        List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty()) // 过滤掉空字符串
                .collect(Collectors.toList());

        if (origins.isEmpty()) {
            // fallback 到默认值，防止 CORS 配置完全无法加载
            origins = Arrays.asList("http://localhost:3000", "http://localhost:5173");
        }

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "Accept", "X-Requested-With"));
        configuration.setMaxAge(3600L); // preflight 缓存 1 小时，减少浏览器 OPTIONS 请求
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Only apply CORS to public and user-facing paths, not admin/sensitive endpoints
        source.registerCorsConfiguration("/api/auth/**", configuration);
        source.registerCorsConfiguration("/api/public/**", configuration);
        source.registerCorsConfiguration("/api/wordnet/**", configuration);
        source.registerCorsConfiguration("/api/student/**", configuration);
        source.registerCorsConfiguration("/api/user/**", configuration);
        source.registerCorsConfiguration("/api/teacher/**", configuration);
        source.registerCorsConfiguration("/api/media/**", configuration);
        source.registerCorsConfiguration("/api/translate/**", configuration);
        source.registerCorsConfiguration("/api/asr/**", configuration);
        source.registerCorsConfiguration("/api/bilibili/**", configuration);
        source.registerCorsConfiguration("/api/admin/**", configuration);
        return source;
    }
}