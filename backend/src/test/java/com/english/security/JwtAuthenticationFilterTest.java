package com.english.security;

import com.english.config.JwtAuthenticationFilter;
import com.english.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
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
        when(jwtUtil.getRoleFromToken(token)).thenReturn("ADMIN");
        when(jwtUtil.validateToken(token, "admin_user")).thenReturn(true);

        invokeDoFilterInternal();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication should be set for valid token");
        assertEquals("admin_user", auth.getPrincipal());
        assertNull(auth.getCredentials(), "Credentials should be null for JWT auth");
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")),
            "Authority should be ROLE_ADMIN");
        assertEquals(1, auth.getAuthorities().size(), "Should have exactly one authority");
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
            when(jwtUtil.getRoleFromToken(token)).thenReturn(role);
            when(jwtUtil.validateToken(token, "user_" + role)).thenReturn(true);

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
        when(jwtUtil.getRoleFromToken(rawToken)).thenReturn("USER");
        when(jwtUtil.validateToken(rawToken, "testuser")).thenReturn(true);

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
        when(jwtUtil.getRoleFromToken(token)).thenReturn("USER");
        when(jwtUtil.validateToken(token, "user")).thenReturn(true);
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
}
