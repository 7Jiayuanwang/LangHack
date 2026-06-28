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

    /** Access token 过期时间（2 小时），用于 refresh token 机制 */
    public static final long ACCESS_TOKEN_EXPIRATION_MILLIS = 7200000L;
    /** Refresh token 过期时间（7 天） */
    public static final long REFRESH_TOKEN_EXPIRATION_MILLIS = 604800000L;
    /**
     * Refresh token 滑动续期：每次刷新都重置过期时间为 7 天，
     * 用户只要保持活跃即不掉登录；连续 7 天未访问才真正过期。
     */
    public static final long REFRESH_TOKEN_ROTATE_THRESHOLD_MILLIS = REFRESH_TOKEN_EXPIRATION_MILLIS;

    /** Claim key: token 类型 */
    private static final String CLAIM_TOKEN_TYPE = "type";
    /** Token 类型: 访问令牌 */
    public static final String TOKEN_TYPE_ACCESS = "access";
    /** Token 类型: 刷新令牌 */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

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
        return createToken(claims, username, expiration);
    }

    /**
     * 生成 access token（2 小时过期）。新登录建议同时返回 access + refresh token。
     * 与 generateToken 的区别：过期时间更短 + claim 中标记 type=access。
     */
    public String generateAccessToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
        return createToken(claims, username, ACCESS_TOKEN_EXPIRATION_MILLIS);
    }

    /**
     * 生成 refresh token（7 天过期），不含 role claim（避免权限随 refresh token 泄漏）。
     * 用法：/api/auth/refresh 端点接收此 token，签发新的 access token。
     * 续期策略：每次刷新都重新签发并黑名单旧 token，实现「每次访问重置 7 天」滑动续期。
     */
    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH);
        return createToken(claims, username, REFRESH_TOKEN_EXPIRATION_MILLIS);
    }

    /**
     * 读取 token 的 type claim。旧 token（无 type 字段）返回 null，调用方视同 access。
     */
    public String getTokenType(String token) {
        return getClaimFromToken(token, claims -> claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    /** token 是否为 refresh 类型（旧 token 无 type 字段时返回 false） */
    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(getTokenType(token));
    }

    /**
     * 判断 refresh token 是否需要续期（滑动续期）。
     *
     * <p>只要 refresh token 未过期且为 refresh 类型，每次都返回 true，
     * 实现「每次刷新重置 7 天」的滑动续期。/refresh 端点据此签发新 refresh token
     * 并将旧 token 加入黑名单。</p>
     *
     * <p>非 refresh token 或已过期 token 返回 false（调用方应另行拒绝）。</p>
     */
    public boolean shouldRotateRefreshToken(String token) {
        if (!isRefreshToken(token)) {
            return false;
        }
        return getTokenRemainingTimeMillis(token) > 0;
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return createToken(claims, subject, expiration);
    }

    private String createToken(Map<String, Object> claims, String subject, long expirationMillis) {
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
