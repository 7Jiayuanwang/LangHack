package com.english.util;

import com.english.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * Test utility class for creating mock authentication and user objects.
 * Used across all controller tests to avoid duplicating setup code.
 */
public class TestHelper {

    /**
     * Creates a mock Authentication object and sets it in SecurityContextHolder.
     * The role is stored with "ROLE_" prefix (matching JWT filter behavior).
     *
     * @param username the username for the authentication principal
     * @param role     the role WITHOUT "ROLE_" prefix (e.g., "ADMIN", "TEACHER", "STUDENT")
     * @return the created Authentication object
     */
    public static Authentication createMockAuthentication(String username, String role) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, Collections.singletonList(authority));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return auth;
    }

    /**
     * Creates a mock User entity with the given parameters.
     * The role is stored WITHOUT "ROLE_" prefix (matching DB format).
     *
     * @param id       the user ID
     * @param username the username
     * @param role     the role WITHOUT "ROLE_" prefix (e.g., "ADMIN", "TEACHER")
     * @return the created User object
     */
    public static User createMockUser(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setCreatedTime(LocalDateTime.now());
        user.setUpdatedTime(LocalDateTime.now());
        return user;
    }

    /**
     * Creates a mock User with additional fields populated.
     *
     * @param id       the user ID
     * @param username the username
     * @param role     the role WITHOUT "ROLE_" prefix
     * @param email    the email address
     * @param nickname the nickname
     * @return the created User object
     */
    public static User createMockUser(Long id, String username, String role, String email, String nickname) {
        User user = createMockUser(id, username, role);
        user.setEmail(email);
        user.setNickname(nickname);
        return user;
    }

    /**
     * Clears the SecurityContext. Should be called in @AfterEach to prevent test pollution.
     */
    public static void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
