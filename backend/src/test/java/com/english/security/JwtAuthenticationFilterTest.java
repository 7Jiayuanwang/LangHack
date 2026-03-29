package com.english.security;

import com.english.config.JwtAuthenticationFilter;
import com.english.entity.User;
import com.english.service.TokenBlacklistService;
import com.english.service.UserService;
import com.english.service.impl.ElevationService;
import com.english.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * Tests the filter in isolation by mocking JwtUtil and invoking
 * doFilterInternal via reflection (it is protected in OncePerRequestFilter).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserService userService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ElevationService elevationService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();

        // Set up Redis mock: stringRedisTemplate.opsForValue() returns valueOperations
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ========== Happy Path ==========

    @Test
    @DisplayName("Valid token with ADMIN role sets authentication in SecurityContext")
    void validTokenSetsAuthentication() throws Exception {
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("admin_user");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("ADMIN");
        when(jwtUtil.validateToken(token, "admin_user")).thenReturn(true);

        // Mock user active check
        User adminUser = new User();
        adminUser.setRole("ADMIN");
        when(userService.getUserByUsername("admin_user")).thenReturn(adminUser);

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication should be set for valid token");
        assertEquals("admin_user", auth.getPrincipal());
        assertNull(auth.getCredentials(), "Credentials should be null for JWT auth");
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")),
            "Authority should be ROLE_ADMIN");
    }

    @Test
    @DisplayName("ROLE_ prefix added correctly for different roles")
    void rolePrefixAddedCorrectly() throws Exception {
        String[] roles = {"ADMIN", "TEACHER", "STUDENT", "USER"};

        for (String role : roles) {
            SecurityContextHolder.clearContext();
            request = new MockHttpServletRequest();
            response = new MockHttpServletResponse();
            filterChain = new MockFilterChain();

            String token = "token_for_" + role;
            request.addHeader("Authorization", "Bearer " + token);

            when(jwtUtil.getUsernameFromToken(token)).thenReturn("user_" + role);
            when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
            when(jwtUtil.getRoleFromToken(token)).thenReturn(role);
            when(jwtUtil.validateToken(token, "user_" + role)).thenReturn(true);

            // Mock user active check
            User user = new User();
            user.setRole(role);
            when(userService.getUserByUsername("user_" + role)).thenReturn(user);

            invokeDoFilterInternal();

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(auth, "Auth should be set for role: " + role);
            assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_" + role)),
                "Authority should be ROLE_" + role);
        }
    }

    @Test
    @DisplayName("Bearer prefix correctly stripped from token")
    void bearerPrefixStripped() throws Exception {
        String rawToken = "my.actual.jwt.token";
        request.addHeader("Authorization", "Bearer " + rawToken);

        when(jwtUtil.getUsernameFromToken(rawToken)).thenReturn("testuser");
        when(tokenBlacklistService.isBlacklisted(rawToken)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(rawToken)).thenReturn("USER");
        when(jwtUtil.validateToken(rawToken, "testuser")).thenReturn(true);

        // Mock user active check
        User user = new User();
        user.setRole("USER");
        when(userService.getUserByUsername("testuser")).thenReturn(user);

        invokeDoFilterInternal();

        // Verify jwtUtil was called with the token WITHOUT "Bearer " prefix
        verify(jwtUtil).getUsernameFromToken(rawToken);
        verify(jwtUtil).getRoleFromToken(rawToken);
        verify(jwtUtil).validateToken(rawToken, "testuser");
    }

    // ========== No Authentication Cases ==========

    @Test
    @DisplayName("Missing Authorization header passes through without authentication")
    void missingAuthorizationHeader() throws Exception {
        // No Authorization header set on request
        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set when Authorization header is missing");
        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("Authorization header without Bearer prefix passes through")
    void noBearerPrefix() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set for non-Bearer authorization");
        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("Invalid token does not set authentication (filter passes through)")
    void invalidTokenNoAuthentication() throws Exception {
        String token = "invalid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenThrow(new RuntimeException("Invalid token"));

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set for invalid token");
    }

    @Test
    @DisplayName("Token with invalid role does not set authentication")
    void invalidRoleNoAuthentication() throws Exception {
        String token = "token.with.bad.role";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("testuser");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("SUPERADMIN"); // Not in VALID_ROLES set

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set for invalid role");
        // validateToken should not even be called if role is invalid
        verify(jwtUtil, never()).validateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("Token with null role does not set authentication")
    void nullRoleNoAuthentication() throws Exception {
        String token = "token.null.role";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("testuser");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn(null);

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set when role is null");
    }

    @Test
    @DisplayName("Token with empty role does not set authentication")
    void emptyRoleNoAuthentication() throws Exception {
        String token = "token.empty.role";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("testuser");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("  ");

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set when role is empty/blank");
    }

    @Test
    @DisplayName("Token that fails validation does not set authentication")
    void validationFailureNoAuthentication() throws Exception {
        String token = "token.validation.fail";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("testuser");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("USER");
        when(jwtUtil.validateToken(token, "testuser")).thenReturn(false);

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set when token validation fails");
    }

    @Test
    @DisplayName("Expired token throws exception during validation, filter continues chain")
    void expiredTokenContinuesChain() throws Exception {
        String token = "expired.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("testuser");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("USER");
        when(jwtUtil.validateToken(token, "testuser")).thenThrow(new RuntimeException("Token expired"));

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set for expired token");
        // Filter should NOT block the chain
    }

    // ========== Chain Continuity ==========

    @Test
    @DisplayName("Filter always continues the chain regardless of token validity")
    void filterAlwaysContinuesChain() throws Exception {
        // Case 1: No token
        invokeDoFilterInternal();
        assertNotNull(filterChain, "Filter chain should continue without token");

        // Case 2: Invalid token
        setUp(); // Reset
        request.addHeader("Authorization", "Bearer bad.token");
        when(jwtUtil.getUsernameFromToken("bad.token")).thenThrow(new RuntimeException("bad"));
        invokeDoFilterInternal();
        // If we reach here, the chain was not blocked

        // Case 3: Valid token
        setUp(); // Reset
        String token = "good.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtUtil.getUsernameFromToken(token)).thenReturn("user");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("USER");
        when(jwtUtil.validateToken(token, "user")).thenReturn(true);

        // Mock user active check
        User user = new User();
        user.setRole("USER");
        when(userService.getUserByUsername("user")).thenReturn(user);

        invokeDoFilterInternal();
        // If we reach here, the chain was not blocked
    }

    @Test
    @DisplayName("Existing authentication is not overwritten")
    void existingAuthNotOverwritten() throws Exception {
        // Pre-set authentication in SecurityContext
        var existingAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "existing_user", null,
            java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        String token = "new.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtUtil.getUsernameFromToken(token)).thenReturn("new_user");

        invokeDoFilterInternal();

        // The original authentication should remain
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("existing_user", auth.getPrincipal(),
            "Existing authentication should not be overwritten");
    }

    @Test
    @DisplayName("Bearer with only spaces after prefix does not authenticate")
    void bearerWithOnlySpaces() throws Exception {
        request.addHeader("Authorization", "Bearer    ");

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set for Bearer with only spaces");
    }

    // ========== Utility ==========

    /**
     * Invokes the protected doFilterInternal method via reflection.
     * OncePerRequestFilter.doFilterInternal is protected, so we use reflection to test it directly.
     */
    private void invokeDoFilterInternal() throws Exception {
        Method method = JwtAuthenticationFilter.class.getDeclaredMethod(
            "doFilterInternal",
            javax.servlet.http.HttpServletRequest.class,
            javax.servlet.http.HttpServletResponse.class,
            javax.servlet.FilterChain.class
        );
        method.setAccessible(true);
        method.invoke(filter, request, response, filterChain);
    }

    // ========== Blacklisted Token ==========

    @Test
    @DisplayName("Blacklisted token does not set authentication")
    void blacklistedTokenNoAuthentication() throws Exception {
        String token = "blacklisted.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("testuser");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(true);

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set for blacklisted token");
        // getRoleFromToken should not be called for blacklisted tokens
        verify(jwtUtil, never()).getRoleFromToken(anyString());
    }

    @Test
    @DisplayName("Query token is accepted for approved SSE endpoints only")
    void queryTokenAllowedSsePathAuthenticates() throws Exception {
        String token = "query.token.allowed";
        request.setMethod("GET");
        request.setRequestURI("/api/ops/update/trigger");
        request.setParameter("token", token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("ops_user");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("OPS");
        when(jwtUtil.validateToken(token, "ops_user")).thenReturn(true);

        User user = new User();
        user.setRole("OPS");
        when(userService.getUserByUsername("ops_user")).thenReturn(user);

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Approved SSE endpoints should accept query token auth");
        assertEquals("ops_user", auth.getPrincipal());
    }

    @Test
    @DisplayName("Query token is ignored for non-SSE endpoints")
    void queryTokenIgnoredOutsideApprovedSsePaths() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/auth/userinfo");
        request.setParameter("token", "query.token.blocked");

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Non-SSE endpoints must not authenticate from query tokens");
        verifyNoInteractions(jwtUtil, tokenBlacklistService, userService, elevationService);
    }

    // ========== Elevation Tests ==========

    @Test
    @DisplayName("User with elevation gets additional authority")
    void validTokenWithElevation_setsMultipleAuthorities() throws Exception {
        String token = "token.with.elevation";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("ops_user");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("ADMIN");
        when(jwtUtil.validateToken(token, "ops_user")).thenReturn(true);

        User adminUser = new User();
        adminUser.setRole("ADMIN");
        when(userService.getUserByUsername("ops_user")).thenReturn(adminUser);

        // Elevation: ADMIN user elevated to OPS
        when(elevationService.getElevatedRole("ops_user")).thenReturn("OPS");

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication should be set");
        assertEquals(2, auth.getAuthorities().size(), "Should have base role + elevated role");
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")),
            "Should have base ROLE_ADMIN");
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_OPS")),
            "Should have elevated ROLE_OPS");
    }

    @Test
    @DisplayName("User without elevation gets only base authority")
    void validTokenWithoutElevation_singleAuthority() throws Exception {
        String token = "token.no.elevation";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("normal_user");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("USER");
        when(jwtUtil.validateToken(token, "normal_user")).thenReturn(true);

        User normalUser = new User();
        normalUser.setRole("USER");
        when(userService.getUserByUsername("normal_user")).thenReturn(normalUser);

        // No elevation
        when(elevationService.getElevatedRole("normal_user")).thenReturn(null);

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication should be set");
        assertEquals(1, auth.getAuthorities().size(), "Should have only base role");
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("Elevation service failure gracefully falls back to base role only")
    void elevationServiceFailure_fallsBackToBaseRole() throws Exception {
        String token = "token.elevation.error";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("admin_user");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("ADMIN");
        when(jwtUtil.validateToken(token, "admin_user")).thenReturn(true);

        User adminUser = new User();
        adminUser.setRole("ADMIN");
        when(userService.getUserByUsername("admin_user")).thenReturn(adminUser);

        // Elevation check throws exception (e.g., Redis down)
        when(elevationService.getElevatedRole("admin_user")).thenThrow(new RuntimeException("Redis unavailable"));

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication should be set even if elevation check fails");
        assertEquals(1, auth.getAuthorities().size(), "Should have only base role");
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ========== User Active Check ==========

    @Test
    @DisplayName("Deleted user does not get authentication")
    void deletedUserNoAuthentication() throws Exception {
        String token = "token.deleted.user";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("deleted_user");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("USER");
        when(jwtUtil.validateToken(token, "deleted_user")).thenReturn(true);

        // User not found (deleted)
        when(userService.getUserByUsername("deleted_user")).thenReturn(null);

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set for deleted user");
    }

    @Test
    @DisplayName("User with changed role does not get authentication with old token")
    void roleChangedUserNoAuthentication() throws Exception {
        String token = "token.role.changed";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtil.getUsernameFromToken(token)).thenReturn("changed_user");
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getRoleFromToken(token)).thenReturn("ADMIN");
        when(jwtUtil.validateToken(token, "changed_user")).thenReturn(true);

        // User exists but role changed from ADMIN to USER
        User changedUser = new User();
        changedUser.setRole("USER");
        when(userService.getUserByUsername("changed_user")).thenReturn(changedUser);

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No auth should be set when user role changed");
    }
}
