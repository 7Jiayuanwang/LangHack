package com.english.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtUtil.
 *
 * Key observations from source code:
 * - JwtUtil uses @Value for secret, expiration, activeProfile
 * - Uses jjwt with HS256 signing algorithm
 * - Has a @PostConstruct init() that initializes signingKey from secret
 * - generateToken(username, role) creates tokens with "role" claim
 * - validateToken(token, username) checks username match AND expiration, returns false on failure
 * - getUsernameFromToken / getRoleFromToken return null on invalid tokens (exceptions caught internally)
 * - getExpirationDateFromToken is public
 * - isTokenExpired is private
 *
 * Since this is a pure unit test (no Spring context), we use reflection to set @Value fields
 * and then call init() to initialize the signingKey.
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    // Secret must be at least 32 bytes for HS256
    private static final String TEST_SECRET = "ThisIsATestSecretKeyForHS256Algorithm!";
    private static final Long TEST_EXPIRATION = 86400000L; // 24 hours
    private static final String TEST_PROFILE = "dev";

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();

        // Set @Value fields via reflection since we are not using Spring context
        setField(jwtUtil, "secret", TEST_SECRET);
        setField(jwtUtil, "expiration", TEST_EXPIRATION);
        setField(jwtUtil, "activeProfile", TEST_PROFILE);

        // Call @PostConstruct init() to initialize signingKey
        jwtUtil.init();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ==================== init() validation tests ====================

    @Test
    void init_secretTooShort_throwsIllegalStateException() throws Exception {
        JwtUtil badJwtUtil = new JwtUtil();
        setField(badJwtUtil, "secret", "short");
        setField(badJwtUtil, "expiration", TEST_EXPIRATION);
        setField(badJwtUtil, "activeProfile", TEST_PROFILE);

        assertThrows(IllegalStateException.class, badJwtUtil::init);
    }

    @Test
    void init_nullSecret_throwsIllegalStateException() throws Exception {
        JwtUtil badJwtUtil = new JwtUtil();
        // secret defaults to null if not set
        setField(badJwtUtil, "expiration", TEST_EXPIRATION);
        setField(badJwtUtil, "activeProfile", TEST_PROFILE);

        assertThrows(IllegalStateException.class, badJwtUtil::init);
    }

    @Test
    void init_nullExpiration_throwsIllegalStateException() throws Exception {
        JwtUtil badJwtUtil = new JwtUtil();
        setField(badJwtUtil, "secret", TEST_SECRET);
        // expiration defaults to null if not set
        setField(badJwtUtil, "activeProfile", TEST_PROFILE);

        assertThrows(IllegalStateException.class, badJwtUtil::init);
    }

    @Test
    void init_zeroExpiration_throwsIllegalStateException() throws Exception {
        JwtUtil badJwtUtil = new JwtUtil();
        setField(badJwtUtil, "secret", TEST_SECRET);
        setField(badJwtUtil, "expiration", 0L);
        setField(badJwtUtil, "activeProfile", TEST_PROFILE);

        assertThrows(IllegalStateException.class, badJwtUtil::init);
    }

    @Test
    void init_negativeExpiration_throwsIllegalStateException() throws Exception {
        JwtUtil badJwtUtil = new JwtUtil();
        setField(badJwtUtil, "secret", TEST_SECRET);
        setField(badJwtUtil, "expiration", -1L);
        setField(badJwtUtil, "activeProfile", TEST_PROFILE);

        assertThrows(IllegalStateException.class, badJwtUtil::init);
    }

    @Test
    void init_prodProfileWithDefaultSecret_throwsIllegalStateException() throws Exception {
        JwtUtil prodJwtUtil = new JwtUtil();
        setField(prodJwtUtil, "secret",
                "your_dummy_jwt_secret_for_local_development_at_least_32_chars_long");
        setField(prodJwtUtil, "expiration", TEST_EXPIRATION);
        setField(prodJwtUtil, "activeProfile", "prod");

        assertThrows(IllegalStateException.class, prodJwtUtil::init);
    }

    @Test
    void init_prodProfileWithWeakSecret_throwsIllegalStateException() throws Exception {
        // Pure digits - weak secret
        JwtUtil prodJwtUtil = new JwtUtil();
        setField(prodJwtUtil, "secret", "12345678901234567890123456789012");
        setField(prodJwtUtil, "expiration", TEST_EXPIRATION);
        setField(prodJwtUtil, "activeProfile", "prod");

        assertThrows(IllegalStateException.class, prodJwtUtil::init);
    }

    @Test
    void init_prodProfileWithStrongSecret_succeeds() throws Exception {
        JwtUtil prodJwtUtil = new JwtUtil();
        setField(prodJwtUtil, "secret", "Str0ng!S3cret#Key_For_Pr0duct1on!");
        setField(prodJwtUtil, "expiration", TEST_EXPIRATION);
        setField(prodJwtUtil, "activeProfile", "prod");

        // Should not throw
        assertDoesNotThrow(prodJwtUtil::init);
    }

    // ==================== generateToken tests ====================

    @Test
    void generateToken_returnsNonNullNonEmptyToken() {
        String token = jwtUtil.generateToken("testuser", "USER");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateToken_returnsThreePartJwt() {
        String token = jwtUtil.generateToken("testuser", "USER");

        // JWT format: header.payload.signature
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts separated by dots");
    }

    @Test
    void generateToken_containsUsernameAndRole() {
        String token = jwtUtil.generateToken("testuser", "ADMIN");

        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        assertEquals("testuser", username);
        assertEquals("ADMIN", role);
    }

    // ==================== getUsernameFromToken tests ====================

    @Test
    void getUsernameFromToken_validToken_returnsCorrectUsername() {
        String token = jwtUtil.generateToken("john_doe", "STUDENT");

        String username = jwtUtil.getUsernameFromToken(token);

        assertEquals("john_doe", username);
    }

    @Test
    void getUsernameFromToken_malformedToken_returnsNull() {
        // JwtUtil catches JwtException internally and returns null
        String result = jwtUtil.getUsernameFromToken("not.a.valid.jwt.token");

        assertNull(result);
    }

    @Test
    void getUsernameFromToken_nullToken_throwsIllegalArgument() {
        // Passing null triggers IllegalArgumentException from jjwt parser (not caught by JwtException handler)
        assertThrows(IllegalArgumentException.class, () -> jwtUtil.getUsernameFromToken(null));
    }

    @Test
    void getUsernameFromToken_emptyToken_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> jwtUtil.getUsernameFromToken(""));
    }

    // ==================== getRoleFromToken tests ====================

    @Test
    void getRoleFromToken_validToken_returnsCorrectRole() {
        String token = jwtUtil.generateToken("testuser", "TEACHER");

        String role = jwtUtil.getRoleFromToken(token);

        assertEquals("TEACHER", role);
    }

    @Test
    void getRoleFromToken_differentRoles_returnsCorrectRole() {
        String[] roles = {"USER", "STUDENT", "TEACHER", "ADMIN"};

        for (String expectedRole : roles) {
            String token = jwtUtil.generateToken("user", expectedRole);
            String actualRole = jwtUtil.getRoleFromToken(token);
            assertEquals(expectedRole, actualRole,
                    "Role mismatch for expected role: " + expectedRole);
        }
    }

    @Test
    void getRoleFromToken_malformedToken_returnsNull() {
        String result = jwtUtil.getRoleFromToken("invalid.token.here");

        assertNull(result);
    }

    // ==================== validateToken tests ====================

    @Test
    void validateToken_validTokenAndMatchingUsername_returnsTrue() {
        String token = jwtUtil.generateToken("testuser", "USER");

        boolean isValid = jwtUtil.validateToken(token, "testuser");

        assertTrue(isValid);
    }

    @Test
    void validateToken_validTokenButWrongUsername_returnsFalse() {
        String token = jwtUtil.generateToken("testuser", "USER");

        boolean isValid = jwtUtil.validateToken(token, "wronguser");

        assertFalse(isValid);
    }

    @Test
    void validateToken_nullToken_returnsFalse() {
        boolean isValid = jwtUtil.validateToken(null, "testuser");

        assertFalse(isValid);
    }

    @Test
    void validateToken_emptyToken_returnsFalse() {
        boolean isValid = jwtUtil.validateToken("", "testuser");

        assertFalse(isValid);
    }

    @Test
    void validateToken_nullUsername_returnsFalse() {
        String token = jwtUtil.generateToken("testuser", "USER");

        boolean isValid = jwtUtil.validateToken(token, null);

        assertFalse(isValid);
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        String token = jwtUtil.generateToken("testuser", "USER");
        // Tamper with the token by flipping a character in the middle of the signature
        // (changing the last char can be unreliable due to Base64url padding bits)
        int lastDot = token.lastIndexOf('.');
        int midSig = lastDot + (token.length() - lastDot) / 2;
        char original = token.charAt(midSig);
        char replacement = (original == 'A') ? 'B' : 'A';
        String tamperedToken = token.substring(0, midSig) + replacement + token.substring(midSig + 1);

        // JwtUtil catches JwtException internally, so tampered tokens return false
        boolean isValid = jwtUtil.validateToken(tamperedToken, "testuser");

        assertFalse(isValid);
    }

    @Test
    void validateToken_tokenFromDifferentSecret_returnsFalse() throws Exception {
        // Create a token with a different secret
        JwtUtil otherJwtUtil = new JwtUtil();
        setField(otherJwtUtil, "secret", "ACompletelyDifferentSecretKeyForTesting!");
        setField(otherJwtUtil, "expiration", TEST_EXPIRATION);
        setField(otherJwtUtil, "activeProfile", TEST_PROFILE);
        otherJwtUtil.init();

        String tokenFromOther = otherJwtUtil.generateToken("testuser", "USER");

        // Validate with the original jwtUtil - should fail signature verification
        boolean isValid = jwtUtil.validateToken(tokenFromOther, "testuser");

        assertFalse(isValid);
    }

    // ==================== expired token tests ====================

    @Test
    void validateToken_expiredToken_returnsFalse() throws Exception {
        // Create a JwtUtil with very short expiration
        JwtUtil shortLivedJwtUtil = new JwtUtil();
        setField(shortLivedJwtUtil, "secret", TEST_SECRET);
        setField(shortLivedJwtUtil, "expiration", 1L); // 1 millisecond
        setField(shortLivedJwtUtil, "activeProfile", TEST_PROFILE);
        shortLivedJwtUtil.init();

        String token = shortLivedJwtUtil.generateToken("testuser", "USER");

        // Wait for token to expire
        Thread.sleep(50);

        // validateToken catches ExpiredJwtException internally and returns false
        boolean isValid = shortLivedJwtUtil.validateToken(token, "testuser");

        assertFalse(isValid);
    }

    @Test
    void getUsernameFromToken_expiredToken_returnsNull() throws Exception {
        JwtUtil shortLivedJwtUtil = new JwtUtil();
        setField(shortLivedJwtUtil, "secret", TEST_SECRET);
        setField(shortLivedJwtUtil, "expiration", 1L);
        setField(shortLivedJwtUtil, "activeProfile", TEST_PROFILE);
        shortLivedJwtUtil.init();

        String token = shortLivedJwtUtil.generateToken("testuser", "USER");

        Thread.sleep(50);

        // Expired token -> getAllClaimsFromToken catches JwtException -> returns null
        String username = shortLivedJwtUtil.getUsernameFromToken(token);

        assertNull(username);
    }

    // ==================== getExpirationDateFromToken tests ====================

    @Test
    void getExpirationDateFromToken_returnsDateInFuture() {
        String token = jwtUtil.generateToken("testuser", "USER");

        Date expirationDate = jwtUtil.getExpirationDateFromToken(token);

        assertNotNull(expirationDate);
        assertTrue(expirationDate.after(new Date()),
                "Expiration date should be in the future");
    }

    @Test
    void getExpirationDateFromToken_expirationMatchesConfiguredDuration() {
        long beforeGeneration = System.currentTimeMillis();
        String token = jwtUtil.generateToken("testuser", "USER");
        long afterGeneration = System.currentTimeMillis();

        Date expirationDate = jwtUtil.getExpirationDateFromToken(token);

        assertNotNull(expirationDate);
        long expirationTime = expirationDate.getTime();
        // Expiration should be approximately now + TEST_EXPIRATION
        // Allow a window for the time between beforeGeneration and afterGeneration
        assertTrue(expirationTime >= beforeGeneration + TEST_EXPIRATION - 1000,
                "Expiration should be at least (now + expiration - 1s)");
        assertTrue(expirationTime <= afterGeneration + TEST_EXPIRATION + 1000,
                "Expiration should be at most (now + expiration + 1s)");
    }

    @Test
    void getExpirationDateFromToken_invalidToken_returnsNull() {
        Date result = jwtUtil.getExpirationDateFromToken("garbage.token.value");

        assertNull(result);
    }

    // ==================== token consistency tests ====================

    @Test
    void generatedTokens_areDifferentForDifferentUsers() {
        String token1 = jwtUtil.generateToken("user1", "USER");
        String token2 = jwtUtil.generateToken("user2", "USER");

        assertNotEquals(token1, token2);
    }

    @Test
    void generatedTokens_areDifferentForDifferentRoles() {
        String token1 = jwtUtil.generateToken("testuser", "USER");
        String token2 = jwtUtil.generateToken("testuser", "ADMIN");

        assertNotEquals(token1, token2);
    }

    @Test
    void generatedToken_canBeValidatedImmediately() {
        String username = "freshuser";
        String token = jwtUtil.generateToken(username, "STUDENT");

        assertTrue(jwtUtil.validateToken(token, username));
        assertEquals(username, jwtUtil.getUsernameFromToken(token));
        assertEquals("STUDENT", jwtUtil.getRoleFromToken(token));
    }
}
