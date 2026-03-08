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
        if (DEFAULT_SECRET.equals(secret) && activeProfile.contains("prod")) {
            throw new IllegalStateException(
                "生产环境检测到默认 JWT Secret，请通过环境变量 JWT_SECRET 设置强密钥后再启动！");
        }
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT Secret 长度不能少于 32 个字符");
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
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
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

    private boolean isTokenExpired(String token) {
        final Date exp = getExpirationDateFromToken(token);
        if (exp == null) return true;
        return exp.before(new Date());
    }
}