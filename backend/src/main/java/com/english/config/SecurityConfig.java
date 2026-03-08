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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
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
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                // 允许访问的接口 - 无需认证
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/api/public/**").permitAll()
                .antMatchers("/api/articles/**").permitAll() // 允许公开访问 articles API
                .antMatchers("/audio/**").permitAll() // 允许公开访问音频文件
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
        List<String> origins = Arrays.asList(allowedOriginsConfig.split(","));
        // 清理每个来源的空格
        origins = origins.stream().map(String::trim).collect(Collectors.toList());
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "Accept", "X-Requested-With"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}