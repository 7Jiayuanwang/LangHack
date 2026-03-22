package com.english.service;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.LoginRequest;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.UserMapper;
import com.english.service.impl.UserServiceImpl;
import com.english.util.JwtUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserServiceImpl.
 *
 * Actual dependencies (from @Autowired fields):
 * - PasswordEncoder passwordEncoder
 * - JwtUtil jwtUtil
 * - UserMapper baseMapper (inherited from ServiceImpl)
 *
 * Key implementation facts:
 * - UserServiceImpl extends ServiceImpl<UserMapper, User>
 * - login() returns Map with "token", "user", "expiresIn" -- NO refreshToken
 * - login() checks user.getStatus() == "ACTIVE" before password check
 * - deleteUser() simply calls removeById(id) -> baseMapper.deleteById(id)
 * - createUser() uses getUserByUsername() + getOne() for duplicate checks, save() for insert
 * - updateUser() fetches existing via getUserById(), preserves password if blank/null
 * - getUserById() uses getOne(wrapper) with deleted=0 filter
 * - getUserByUsername() uses getOne(wrapper) with deleted=0 filter
 * - getUserStats() delegates to baseMapper.selectUserStats()
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserMapper baseMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("$2a$10$encodedPassword");
        testUser.setEmail("test@example.com");
        testUser.setRole("USER");
        testUser.setNickname("TestNick");
        testUser.setRealName("Test User");
        testUser.setStatus("ACTIVE");
        testUser.setDeleted(0);
        testUser.setCreatedTime(LocalDateTime.now());
        testUser.setUpdatedTime(LocalDateTime.now());
    }

    // ==================== login tests ====================

    @Nested
    class LoginTests {

        @Test
        void login_correctCredentials_returnsTokenAndUser() {
            // Arrange
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setUsername("testuser");
            loginRequest.setPassword("rawPassword123");

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);
            when(passwordEncoder.matches("rawPassword123", "$2a$10$encodedPassword")).thenReturn(true);
            when(jwtUtil.generateToken("testuser", "USER")).thenReturn("jwt-access-token");
            when(jwtUtil.getExpiration()).thenReturn(2592000000L);

            // Act
            Map<String, Object> result = userService.login(loginRequest);

            // Assert
            assertNotNull(result);
            assertEquals("jwt-access-token", result.get("token"));
            assertEquals(testUser, result.get("user"));
            assertEquals(2592000000L, result.get("expiresIn"));
            // login() does NOT return refreshToken
            assertNull(result.get("refreshToken"));
            // password should be cleared before returning
            assertNull(testUser.getPassword());
        }

        @Test
        void login_userNotFound_throwsBusinessException() {
            // Arrange
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setUsername("nonexistent");
            loginRequest.setPassword("anyPassword");

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> userService.login(loginRequest));
            assertEquals("用户不存在", exception.getMessage());
        }

        @Test
        void login_disabledAccount_throwsBusinessException() {
            // Arrange
            User disabledUser = new User();
            disabledUser.setId(2L);
            disabledUser.setUsername("disabled");
            disabledUser.setPassword("$2a$10$encoded");
            disabledUser.setStatus("DISABLED");

            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setUsername("disabled");
            loginRequest.setPassword("password");

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(disabledUser);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> userService.login(loginRequest));
            assertEquals("账户已被禁用，请联系管理员", exception.getMessage());
            // passwordEncoder.matches should NOT be called for disabled accounts
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        void login_wrongPassword_throwsBusinessException() {
            // Arrange
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setUsername("testuser");
            loginRequest.setPassword("wrongPassword");

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);
            when(passwordEncoder.matches("wrongPassword", "$2a$10$encodedPassword")).thenReturn(false);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> userService.login(loginRequest));
            assertEquals("密码错误", exception.getMessage());
        }

        @Test
        void login_clearsPasswordBeforeReturning() {
            // Arrange
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setUsername("testuser");
            loginRequest.setPassword("rawPassword123");

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);
            when(passwordEncoder.matches("rawPassword123", "$2a$10$encodedPassword")).thenReturn(true);
            when(jwtUtil.generateToken("testuser", "USER")).thenReturn("token");

            // Act
            Map<String, Object> result = userService.login(loginRequest);

            // Assert: password is set to null before putting user in result map
            User returnedUser = (User) result.get("user");
            assertNull(returnedUser.getPassword());
        }
    }

    // ==================== getUserById tests ====================

    @Nested
    class GetUserByIdTests {

        @Test
        void getUserById_found_returnsUser() {
            // Arrange: getUserById uses getOne(wrapper) with eq("id", id) + eq("deleted", 0)
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);

            // Act
            User result = userService.getUserById(1L);

            // Assert
            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("testuser", result.getUsername());
        }

        @Test
        void getUserById_notFound_returnsNull() {
            // Arrange
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            // Act
            User result = userService.getUserById(999L);

            // Assert
            assertNull(result);
        }
    }

    // ==================== getUserByUsername tests ====================

    @Nested
    class GetUserByUsernameTests {

        @Test
        void getUserByUsername_found_returnsUser() {
            // Arrange
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);

            // Act
            User result = userService.getUserByUsername("testuser");

            // Assert
            assertNotNull(result);
            assertEquals("testuser", result.getUsername());
        }

        @Test
        void getUserByUsername_notFound_returnsNull() {
            // Arrange
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            // Act
            User result = userService.getUserByUsername("nonexistent");

            // Assert
            assertNull(result);
        }
    }

    // ==================== createUser tests ====================

    @Nested
    class CreateUserTests {

        @Test
        void createUser_success_encodesPasswordAndSetsDefaults() {
            // Arrange
            User newUser = new User();
            newUser.setUsername("newuser");
            newUser.setPassword("rawPassword123");
            newUser.setEmail("new@example.com");

            // getUserByUsername returns null (no duplicate username)
            // getOne for email check also returns null (no duplicate email)
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
            when(passwordEncoder.encode("rawPassword123")).thenReturn("$2a$10$encodedNewPassword");
            when(baseMapper.insert(any(User.class))).thenReturn(1);

            // Act
            userService.createUser(newUser);

            // Assert
            assertEquals("$2a$10$encodedNewPassword", newUser.getPassword());
            assertEquals("ACTIVE", newUser.getStatus());
            assertEquals(0, newUser.getDeleted());
            assertNotNull(newUser.getCreatedTime());
            assertNotNull(newUser.getUpdatedTime());
            verify(passwordEncoder).encode("rawPassword123");
            verify(baseMapper).insert(any(User.class));
        }

        @Test
        void createUser_duplicateUsername_throwsBusinessException() {
            // Arrange
            User newUser = new User();
            newUser.setUsername("testuser"); // already exists
            newUser.setPassword("rawPassword123");
            newUser.setEmail("new@example.com");

            // First selectOne call (getUserByUsername) returns existing user
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> userService.createUser(newUser));
            assertEquals("用户名已存在", exception.getMessage());
            verify(baseMapper, never()).insert(any(User.class));
        }

        @Test
        void createUser_duplicateEmail_throwsBusinessException() {
            // Arrange
            User newUser = new User();
            newUser.setUsername("uniqueuser");
            newUser.setPassword("rawPassword123");
            newUser.setEmail("test@example.com"); // already exists

            // First selectOne (getUserByUsername for "uniqueuser") returns null
            // Second selectOne (email check) returns existing user
            when(baseMapper.selectOne(any(QueryWrapper.class)))
                    .thenReturn(null)   // username check
                    .thenReturn(testUser); // email check

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> userService.createUser(newUser));
            assertEquals("邮箱已存在", exception.getMessage());
            verify(baseMapper, never()).insert(any(User.class));
        }

        @Test
        void createUser_blankEmail_skipsEmailDuplicateCheck() {
            // Arrange
            User newUser = new User();
            newUser.setUsername("newuser");
            newUser.setPassword("rawPassword123");
            newUser.setEmail("   "); // blank email, should skip email check

            // Only username check, returns null
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
            when(passwordEncoder.encode("rawPassword123")).thenReturn("$2a$10$encoded");
            when(baseMapper.insert(any(User.class))).thenReturn(1);

            // Act
            userService.createUser(newUser);

            // Assert: only 1 selectOne call (username check), no email check
            verify(baseMapper, times(1)).selectOne(any(QueryWrapper.class));
            verify(baseMapper).insert(any(User.class));
        }

        @Test
        void createUser_nullEmail_skipsEmailDuplicateCheck() {
            // Arrange
            User newUser = new User();
            newUser.setUsername("newuser");
            newUser.setPassword("rawPassword123");
            newUser.setEmail(null); // null email, should skip email check

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
            when(passwordEncoder.encode("rawPassword123")).thenReturn("$2a$10$encoded");
            when(baseMapper.insert(any(User.class))).thenReturn(1);

            // Act
            userService.createUser(newUser);

            // Assert
            verify(baseMapper, times(1)).selectOne(any(QueryWrapper.class));
            verify(baseMapper).insert(any(User.class));
        }
    }

    // ==================== updateUser tests ====================

    @Nested
    class UpdateUserTests {

        @Test
        void updateUser_withNewPassword_encodesPassword() {
            // Arrange
            User updateUser = new User();
            updateUser.setId(1L);
            updateUser.setPassword("newRawPassword");

            // getUserById returns existing user
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);
            when(passwordEncoder.encode("newRawPassword")).thenReturn("$2a$10$newEncoded");
            when(baseMapper.updateById(any(User.class))).thenReturn(1);

            // Act
            userService.updateUser(updateUser);

            // Assert
            assertEquals("$2a$10$newEncoded", updateUser.getPassword());
            assertNotNull(updateUser.getUpdatedTime());
            verify(passwordEncoder).encode("newRawPassword");
            verify(baseMapper).updateById(updateUser);
        }

        @Test
        void updateUser_blankPassword_preservesExistingPassword() {
            // Arrange
            User updateUser = new User();
            updateUser.setId(1L);
            updateUser.setPassword("   "); // blank
            updateUser.setEmail("updated@example.com");

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);
            when(baseMapper.updateById(any(User.class))).thenReturn(1);

            // Act
            userService.updateUser(updateUser);

            // Assert: password preserved from existing user, not encoded
            assertEquals("$2a$10$encodedPassword", updateUser.getPassword());
            verify(passwordEncoder, never()).encode(anyString());
            verify(baseMapper).updateById(updateUser);
        }

        @Test
        void updateUser_nullPassword_preservesExistingPassword() {
            // Arrange
            User updateUser = new User();
            updateUser.setId(1L);
            updateUser.setPassword(null);

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);
            when(baseMapper.updateById(any(User.class))).thenReturn(1);

            // Act
            userService.updateUser(updateUser);

            // Assert
            assertEquals("$2a$10$encodedPassword", updateUser.getPassword());
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        void updateUser_userNotFound_throwsBusinessException() {
            // Arrange
            User updateUser = new User();
            updateUser.setId(999L);

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> userService.updateUser(updateUser));
            assertEquals("用户不存在", exception.getMessage());
            verify(baseMapper, never()).updateById(any(User.class));
        }

        @Test
        void updateUser_setsUpdatedTime() {
            // Arrange
            User updateUser = new User();
            updateUser.setId(1L);
            updateUser.setPassword("newpass");

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);
            when(passwordEncoder.encode("newpass")).thenReturn("$2a$10$enc");
            when(baseMapper.updateById(any(User.class))).thenReturn(1);

            // Act
            userService.updateUser(updateUser);

            // Assert
            assertNotNull(updateUser.getUpdatedTime());
        }
    }

    // ==================== deleteUser tests ====================

    @Nested
    class DeleteUserTests {

        @Test
        void deleteUser_callsRemoveById() {
            // Arrange: deleteUser simply calls removeById(id),
            // which for User entity (has @TableLogic + fill=INSERT_UPDATE)
            // internally calls baseMapper.deleteById(User entity), not deleteById(Serializable id)
            when(baseMapper.deleteById(any(User.class))).thenReturn(1);

            // Act
            userService.deleteUser(1L);

            // Assert
            verify(baseMapper).deleteById(any(User.class));
        }

        @Test
        void deleteUser_noCleanupLogic() {
            // Verify that deleteUser does NOT interact with any other mapper/service.
            // The actual implementation is a single removeById(id) call.
            when(baseMapper.deleteById(any(User.class))).thenReturn(1);

            // Act
            userService.deleteUser(42L);

            // Assert: only deleteById is called, nothing else
            verify(baseMapper).deleteById(any(User.class));
            verifyNoMoreInteractions(baseMapper);
        }
    }

    // ==================== validatePassword tests ====================

    @Nested
    class ValidatePasswordTests {

        @Test
        void validatePassword_matching_returnsTrue() {
            // Arrange
            when(passwordEncoder.matches("rawPass", "$2a$10$encoded")).thenReturn(true);

            // Act
            boolean result = userService.validatePassword("rawPass", "$2a$10$encoded");

            // Assert
            assertTrue(result);
        }

        @Test
        void validatePassword_notMatching_returnsFalse() {
            // Arrange
            when(passwordEncoder.matches("wrongPass", "$2a$10$encoded")).thenReturn(false);

            // Act
            boolean result = userService.validatePassword("wrongPass", "$2a$10$encoded");

            // Assert
            assertFalse(result);
        }
    }

    // ==================== getTeachers tests ====================

    @Nested
    class GetTeachersTests {

        @Test
        void getTeachers_returnsActiveTeachers() {
            // Arrange
            User teacher1 = new User();
            teacher1.setId(10L);
            teacher1.setRole("TEACHER");
            teacher1.setStatus("ACTIVE");

            User teacher2 = new User();
            teacher2.setId(11L);
            teacher2.setRole("TEACHER");
            teacher2.setStatus("ACTIVE");

            when(baseMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(teacher1, teacher2));

            // Act
            List<User> result = userService.getTeachers();

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        void getTeachers_noTeachers_returnsEmptyList() {
            // Arrange
            when(baseMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            List<User> result = userService.getTeachers();

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== getStudents tests ====================

    @Nested
    class GetStudentsTests {

        @Test
        void getStudents_returnsActiveStudentsAndUsers() {
            // Arrange: getStudents() filters role IN ("STUDENT", "USER")
            User student = new User();
            student.setId(20L);
            student.setRole("STUDENT");

            User user = new User();
            user.setId(21L);
            user.setRole("USER");

            when(baseMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(student, user));

            // Act
            List<User> result = userService.getStudents();

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        void getStudents_noStudents_returnsEmptyList() {
            // Arrange
            when(baseMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            List<User> result = userService.getStudents();

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== searchUsers tests ====================

    @Nested
    class SearchUsersTests {

        @Test
        void searchUsers_withKeywordAndRole_returnsMatchingUsers() {
            // Arrange
            when(baseMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Collections.singletonList(testUser));

            // Act
            List<User> result = userService.searchUsers("test", "USER");

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("testuser", result.get(0).getUsername());
        }

        @Test
        void searchUsers_nullKeywordNullRole_returnsAllActiveUsers() {
            // Arrange
            when(baseMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(testUser));

            // Act
            List<User> result = userService.searchUsers(null, null);

            // Assert
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        void searchUsers_blankKeyword_treatedAsNoKeyword() {
            // Arrange
            when(baseMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(testUser));

            // Act
            List<User> result = userService.searchUsers("   ", "");

            // Assert
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        void searchUsers_noResults_returnsEmptyList() {
            // Arrange
            when(baseMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            List<User> result = userService.searchUsers("nonexistent", "TEACHER");

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== getUsersByPage tests ====================

    @Nested
    class GetUsersByPageTests {

        @Test
        @SuppressWarnings("unchecked")
        void getUsersByPage_withParams_returnsPagedResult() {
            // Arrange
            Map<String, Object> params = new HashMap<>();
            params.put("page", 1);
            params.put("size", 10);
            params.put("keyword", "test");
            params.put("role", "USER");

            IPage<User> mockPage = mock(IPage.class);
            when(mockPage.getRecords()).thenReturn(Collections.singletonList(testUser));
            when(mockPage.getTotal()).thenReturn(1L);
            when(mockPage.getPages()).thenReturn(1L);
            when(mockPage.getCurrent()).thenReturn(1L);
            when(mockPage.getSize()).thenReturn(10L);

            doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

            // Act
            Map<String, Object> result = userService.getUsersByPage(params);

            // Assert
            assertNotNull(result);
            assertEquals(1L, result.get("total"));
            assertEquals(1L, result.get("pages"));
            assertEquals(1L, result.get("current"));
            assertEquals(10L, result.get("size"));
            @SuppressWarnings("unchecked")
            List<User> records = (List<User>) result.get("records");
            assertNotNull(records);
            assertEquals(1, records.size());
        }

        @Test
        @SuppressWarnings("unchecked")
        void getUsersByPage_defaultPageAndSize_whenParamsNull() {
            // Arrange: page and size default to 1 and 10 when null
            Map<String, Object> params = new HashMap<>();
            // no page/size/keyword/role keys

            IPage<User> mockPage = mock(IPage.class);
            when(mockPage.getRecords()).thenReturn(Collections.emptyList());
            when(mockPage.getTotal()).thenReturn(0L);
            when(mockPage.getPages()).thenReturn(0L);
            when(mockPage.getCurrent()).thenReturn(1L);
            when(mockPage.getSize()).thenReturn(10L);

            doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

            // Act
            Map<String, Object> result = userService.getUsersByPage(params);

            // Assert
            assertNotNull(result);
            assertEquals(0L, result.get("total"));
        }
    }

    // ==================== getUserStats tests ====================

    @Nested
    class GetUserStatsTests {

        @Test
        void getUserStats_returnsAggregatedStats() {
            // Arrange: getUserStats() delegates to baseMapper.selectUserStats()
            Map<String, Object> rawStats = new HashMap<>();
            rawStats.put("adminCount", 2L);
            rawStats.put("teacherCount", 5L);
            rawStats.put("studentCount", 100L);
            rawStats.put("activeCount", 95L);

            when(baseMapper.selectUserStats()).thenReturn(rawStats);

            // Act
            Map<String, Object> result = userService.getUserStats();

            // Assert
            assertNotNull(result);
            assertEquals(2L, result.get("totalAdmins"));
            assertEquals(5L, result.get("totalTeachers"));
            assertEquals(100L, result.get("totalStudents"));
            assertEquals(95L, result.get("totalActive"));
            assertEquals(107L, result.get("totalUsers")); // 2 + 5 + 100
        }

        @Test
        void getUserStats_nullFromMapper_returnsZeros() {
            // Arrange: selectUserStats returns null
            when(baseMapper.selectUserStats()).thenReturn(null);

            // Act
            Map<String, Object> result = userService.getUserStats();

            // Assert
            assertNotNull(result);
            assertEquals(0L, result.get("totalAdmins"));
            assertEquals(0L, result.get("totalTeachers"));
            assertEquals(0L, result.get("totalStudents"));
            assertEquals(0L, result.get("totalActive"));
            assertEquals(0L, result.get("totalUsers"));
        }

        @Test
        void getUserStats_integerValuesFromMapper_handledCorrectly() {
            // Arrange: MySQL may return Integer instead of Long
            Map<String, Object> rawStats = new HashMap<>();
            rawStats.put("adminCount", 1);    // Integer
            rawStats.put("teacherCount", 3);   // Integer
            rawStats.put("studentCount", 50);  // Integer
            rawStats.put("activeCount", 40);   // Integer

            when(baseMapper.selectUserStats()).thenReturn(rawStats);

            // Act
            Map<String, Object> result = userService.getUserStats();

            // Assert: Number pattern matching handles Integer -> longValue()
            assertEquals(1L, result.get("totalAdmins"));
            assertEquals(3L, result.get("totalTeachers"));
            assertEquals(50L, result.get("totalStudents"));
            assertEquals(40L, result.get("totalActive"));
            assertEquals(54L, result.get("totalUsers")); // 1 + 3 + 50
        }
    }

    // ==================== updateUserStatus tests ====================

    @Nested
    class UpdateUserStatusTests {

        @Test
        void updateUserStatus_success_updatesStatusAndTimestamp() {
            // Arrange
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);
            when(baseMapper.updateById(any(User.class))).thenReturn(1);

            // Act
            userService.updateUserStatus(1L, "DISABLED");

            // Assert
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(baseMapper).updateById(captor.capture());
            User updatedUser = captor.getValue();
            assertEquals(1L, updatedUser.getId());
            assertEquals("DISABLED", updatedUser.getStatus());
            assertNotNull(updatedUser.getUpdatedTime());
        }

        @Test
        void updateUserStatus_userNotFound_throwsBusinessException() {
            // Arrange
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> userService.updateUserStatus(999L, "DISABLED"));
            assertEquals("用户不存在", exception.getMessage());
            verify(baseMapper, never()).updateById(any(User.class));
        }
    }

    // ==================== resetPassword tests ====================

    @Nested
    class ResetPasswordTests {

        @Test
        void resetPassword_success_encodesAndUpdates() {
            // Arrange
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(testUser);
            when(passwordEncoder.encode("newPassword123")).thenReturn("$2a$10$newEncoded");
            when(baseMapper.updateById(any(User.class))).thenReturn(1);

            // Act
            userService.resetPassword(1L, "newPassword123");

            // Assert
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(baseMapper).updateById(captor.capture());
            User updatedUser = captor.getValue();
            assertEquals(1L, updatedUser.getId());
            assertEquals("$2a$10$newEncoded", updatedUser.getPassword());
            assertNotNull(updatedUser.getUpdatedTime());
            verify(passwordEncoder).encode("newPassword123");
        }

        @Test
        void resetPassword_userNotFound_throwsBusinessException() {
            // Arrange
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> userService.resetPassword(999L, "newPassword123"));
            assertEquals("用户不存在", exception.getMessage());
            verify(passwordEncoder, never()).encode(anyString());
            verify(baseMapper, never()).updateById(any(User.class));
        }
    }

    // ==================== initDefaultAdmin tests ====================

    @Nested
    class InitDefaultAdminTests {

        @Test
        void initDefaultAdmin_noExistingAdmin_createsAdmin() {
            // Arrange
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
            when(passwordEncoder.encode("admin123")).thenReturn("$2a$10$encodedAdminPass");
            when(baseMapper.insert(any(User.class))).thenReturn(1);

            // Act
            userService.initDefaultAdmin();

            // Assert
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(baseMapper).insert(captor.capture());
            User admin = captor.getValue();
            assertEquals("admin", admin.getUsername());
            assertEquals("$2a$10$encodedAdminPass", admin.getPassword());
            assertEquals("系统管理员", admin.getNickname());
            assertEquals("admin@english.com", admin.getEmail());
            assertEquals("ADMIN", admin.getRole());
            assertEquals("ACTIVE", admin.getStatus());
            assertEquals(0, admin.getDeleted());
            assertNotNull(admin.getCreatedTime());
            assertNotNull(admin.getUpdatedTime());
        }

        @Test
        void initDefaultAdmin_adminAlreadyExists_doesNothing() {
            // Arrange
            User existingAdmin = new User();
            existingAdmin.setId(1L);
            existingAdmin.setUsername("admin");
            existingAdmin.setRole("ADMIN");

            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(existingAdmin);

            // Act
            userService.initDefaultAdmin();

            // Assert: no insert should be called
            verify(baseMapper, never()).insert(any(User.class));
        }
    }
}
