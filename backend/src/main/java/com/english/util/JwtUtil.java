package com.english.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private static final String DEFAULT_SECRET = "your_dummy_jwt_secret_for_local_development_at_least_32_chars_long";
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${spring.security.jwt.secret}")
    private String secret;

    @Value("${spring.security.jwt.expiration}")
    private Long expiration;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private Key signingKey;

    @PostConstruct
    public void init() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT Secret 长度不能少于 32 个字符");
        }

        // 验证过期时间配置
        if (expiration == null || expiration <= 0) {
            throw new IllegalStateException("JWT expiration must be configured and greater than 0");
        }

        // 生产环境额外安全检查
        if ("prod".equals(activeProfile)) {
            if (DEFAULT_SECRET.equals(secret)) {
                throw new IllegalStateException(
                    "生产环境检测到默认 JWT Secret，请通过环境变量 JWT_SECRET 设置强密钥后再启动！");
            }
            // 检查密钥强度：不能是纯数字、纯字母或重复字符
            if (secret.matches("^\\d+$") || secret.matches("^[a-zA-Z]+$") ||
                secret.matches("(.)\\1{10,}")) {
                throw new IllegalStateException(
                    "生产环境 JWT Secret 强度不足，请使用包含字母、数字、特殊字符的强密钥");
            }
        }

        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Key getSigningKey() {
        return signingKey;
    }

    public String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return createToken(claims, username);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        long expirationMillis = expiration; // expiration validated in init()
        
        // 防止时间溢出：检查时间加法是否会超过Long范围
        long now = System.currentTimeMillis();
        if (expirationMillis > Long.MAX_VALUE - now) {
            // 时间溢出时使用最大可能值
            expirationMillis = Long.MAX_VALUE - now;
            logger.warn("JWT expiration milliseconds too large, capped to Long.MAX_VALUE");
        }
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMillis))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token, String username) {
        if (token == null || token.isEmpty() || username == null) {
            return false;
        }
        final String tokenUsername = getUsernameFromToken(token);
        return (tokenUsername != null && tokenUsername.equals(username) && !isTokenExpired(token));
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public String getRoleFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("role", String.class));
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, java.util.function.Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        try {
            return claimsResolver.apply(claims);
        } catch (Exception e) {
            logger.warn("Failed to extract claim from token", e);
            return null;
        }
    }

    private Claims getAllClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * 获取 token 剩余有效时间（毫秒）。
     * 如果 token 已过期或无法解析，返回 0。
     */
    public long getTokenRemainingTimeMillis(String token) {
        Date expiration = getExpirationDateFromToken(token);
        if (expiration == null) {
            return 0;
        }
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    public Long getExpiration() {
        return expiration;
    }

    private boolean isTokenExpired(String token) {
        final Date exp = getExpirationDateFromToken(token);
        if (exp == null) return true;
        return exp.before(new Date());
    }
}
