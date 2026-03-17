package com.english.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserDTO.
 *
 * Covers no-arg constructor, all-arg constructor, all getter/setter methods,
 * and field isolation (setting one field should not affect others).
 */
class UserDTOTest {

    private UserDTO dto;

    @BeforeEach
    void setUp() {
        dto = new UserDTO();
    }

    // ==================== Constructor Tests ====================

    @Nested
    @DisplayName("No-arg constructor tests")
    class NoArgConstructorTests {

        @Test
        @DisplayName("no-arg constructor should create non-null instance")
        void noArgConstructor_shouldCreateNonNull() {
            assertNotNull(new UserDTO());
        }

        @Test
        @DisplayName("no-arg constructor should have all null fields")
        void noArgConstructor_shouldHaveAllNullFields() {
            UserDTO userDTO = new UserDTO();
            assertNull(userDTO.getId());
            assertNull(userDTO.getUsername());
            assertNull(userDTO.getEmail());
            assertNull(userDTO.getRole());
            assertNull(userDTO.getNickname());
            assertNull(userDTO.getRealName());
            assertNull(userDTO.getAvatar());
            assertNull(userDTO.getStatus());
            assertNull(userDTO.getCreatedAt());
            assertNull(userDTO.getUpdatedAt());
        }
    }

    @Nested
    @DisplayName("All-arg constructor tests")
    class AllArgConstructorTests {

        @Test
        @DisplayName("all-arg constructor should set all fields correctly")
        void allArgConstructor_shouldSetAllFields() {
            LocalDateTime now = LocalDateTime.now();
            UserDTO userDTO = new UserDTO(
                    1L, "testuser", "test@example.com", "USER",
                    "TestNick", "TestReal", "avatar.png", "ACTIVE",
                    now, now
            );

            assertEquals(1L, userDTO.getId());
            assertEquals("testuser", userDTO.getUsername());
            assertEquals("test@example.com", userDTO.getEmail());
            assertEquals("USER", userDTO.getRole());
            assertEquals("TestNick", userDTO.getNickname());
            assertEquals("TestReal", userDTO.getRealName());
            assertEquals("avatar.png", userDTO.getAvatar());
            assertEquals("ACTIVE", userDTO.getStatus());
            assertEquals(now, userDTO.getCreatedAt());
            assertEquals(now, userDTO.getUpdatedAt());
        }

        @Test
        @DisplayName("all-arg constructor should accept all nulls")
        void allArgConstructor_shouldAcceptAllNulls() {
            UserDTO userDTO = new UserDTO(null, null, null, null, null, null, null, null, null, null);
            assertNull(userDTO.getId());
            assertNull(userDTO.getUsername());
        }

        @Test
        @DisplayName("all-arg constructor should handle different LocalDateTime values")
        void allArgConstructor_shouldHandleDifferentTimes() {
            LocalDateTime created = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime updated = LocalDateTime.of(2024, 6, 15, 12, 30);
            UserDTO userDTO = new UserDTO(
                    1L, "user", "e@e.com", "ADMIN",
                    "nick", "real", "av.png", "ACTIVE",
                    created, updated
            );

            assertNotEquals(userDTO.getCreatedAt(), userDTO.getUpdatedAt());
            assertEquals(created, userDTO.getCreatedAt());
            assertEquals(updated, userDTO.getUpdatedAt());
        }
    }

    // ==================== id Getter/Setter ====================

    @Nested
    @DisplayName("id getter/setter tests")
    class IdTests {

        @Test
        @DisplayName("setId/getId should work with positive Long")
        void setGetId_shouldWork() {
            dto.setId(100L);
            assertEquals(100L, dto.getId());
        }

        @Test
        @DisplayName("setId with null should set null")
        void setId_withNull_shouldSetNull() {
            dto.setId(1L);
            dto.setId(null);
            assertNull(dto.getId());
        }

        @Test
        @DisplayName("setId with zero should work")
        void setId_withZero_shouldWork() {
            dto.setId(0L);
            assertEquals(0L, dto.getId());
        }

        @Test
        @DisplayName("setId with large value should work")
        void setId_withLargeValue_shouldWork() {
            dto.setId(Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, dto.getId());
        }
    }

    // ==================== username Getter/Setter ====================

    @Nested
    @DisplayName("username getter/setter tests")
    class UsernameTests {

        @Test
        @DisplayName("setUsername/getUsername should work correctly")
        void setGetUsername_shouldWork() {
            dto.setUsername("admin");
            assertEquals("admin", dto.getUsername());
        }

        @Test
        @DisplayName("setUsername with null should set null")
        void setUsername_withNull_shouldSetNull() {
            dto.setUsername("test");
            dto.setUsername(null);
            assertNull(dto.getUsername());
        }

        @Test
        @DisplayName("setUsername with empty string should set empty")
        void setUsername_withEmpty_shouldSetEmpty() {
            dto.setUsername("");
            assertEquals("", dto.getUsername());
        }
    }

    // ==================== email Getter/Setter ====================

    @Test
    @DisplayName("setEmail/getEmail should work correctly")
    void setGetEmail_shouldWork() {
        dto.setEmail("user@example.com");
        assertEquals("user@example.com", dto.getEmail());
    }

    @Test
    @DisplayName("setEmail with null should set null")
    void setEmail_withNull_shouldSetNull() {
        dto.setEmail(null);
        assertNull(dto.getEmail());
    }

    // ==================== role Getter/Setter ====================

    @Test
    @DisplayName("setRole/getRole should work correctly")
    void setGetRole_shouldWork() {
        dto.setRole("ADMIN");
        assertEquals("ADMIN", dto.getRole());
    }

    @Test
    @DisplayName("setRole with various roles should work")
    void setRole_withVariousRoles_shouldWork() {
        String[] roles = {"USER", "STUDENT", "TEACHER", "ADMIN"};
        for (String role : roles) {
            dto.setRole(role);
            assertEquals(role, dto.getRole());
        }
    }

    // ==================== nickname Getter/Setter ====================

    @Test
    @DisplayName("setNickname/getNickname should work correctly")
    void setGetNickname_shouldWork() {
        dto.setNickname("CoolNick");
        assertEquals("CoolNick", dto.getNickname());
    }

    // ==================== realName Getter/Setter ====================

    @Test
    @DisplayName("setRealName/getRealName should work correctly")
    void setGetRealName_shouldWork() {
        dto.setRealName("Zhang San");
        assertEquals("Zhang San", dto.getRealName());
    }

    // ==================== avatar Getter/Setter ====================

    @Test
    @DisplayName("setAvatar/getAvatar should work correctly")
    void setGetAvatar_shouldWork() {
        dto.setAvatar("/uploads/avatar/user1.png");
        assertEquals("/uploads/avatar/user1.png", dto.getAvatar());
    }

    // ==================== status Getter/Setter ====================

    @Test
    @DisplayName("setStatus/getStatus should work correctly")
    void setGetStatus_shouldWork() {
        dto.setStatus("ACTIVE");
        assertEquals("ACTIVE", dto.getStatus());
    }

    @Test
    @DisplayName("setStatus with various statuses")
    void setStatus_withVariousStatuses() {
        String[] statuses = {"ACTIVE", "DISABLED", "LOCKED"};
        for (String status : statuses) {
            dto.setStatus(status);
            assertEquals(status, dto.getStatus());
        }
    }

    // ==================== createdAt/updatedAt Getter/Setter ====================

    @Test
    @DisplayName("setCreatedAt/getCreatedAt should work correctly")
    void setGetCreatedAt_shouldWork() {
        LocalDateTime now = LocalDateTime.now();
        dto.setCreatedAt(now);
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    @DisplayName("setUpdatedAt/getUpdatedAt should work correctly")
    void setGetUpdatedAt_shouldWork() {
        LocalDateTime now = LocalDateTime.now();
        dto.setUpdatedAt(now);
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    @DisplayName("setCreatedAt with null should set null")
    void setCreatedAt_withNull_shouldSetNull() {
        dto.setCreatedAt(LocalDateTime.now());
        dto.setCreatedAt(null);
        assertNull(dto.getCreatedAt());
    }

    // ==================== Field Isolation Tests ====================

    @Test
    @DisplayName("setting one field should not affect other fields")
    void fieldIsolation_shouldWork() {
        dto.setId(1L);
        dto.setUsername("user1");
        dto.setEmail("user1@example.com");

        // Change username, verify other fields unchanged
        dto.setUsername("user2");
        assertEquals(1L, dto.getId());
        assertEquals("user1@example.com", dto.getEmail());
        assertEquals("user2", dto.getUsername());
    }
}
