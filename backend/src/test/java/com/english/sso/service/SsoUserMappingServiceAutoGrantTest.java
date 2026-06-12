package com.english.sso.service;

import com.english.entity.SchoolStudent;
import com.english.entity.SchoolTeacher;
import com.english.entity.User;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.SchoolTeacherMapper;
import com.english.mapper.UserMapper;
import com.english.service.SystemConfigService;
import com.english.service.UserService;
import com.english.sso.dto.SsoLoginResult;
import com.english.sso.dto.SsoUserInfo;
import com.english.sso.entity.SsoUserBinding;
import com.english.sso.mapper.SsoUserBindingMapper;
import com.english.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测试 SSO 按学校主数据自动授权功能
 *
 * processLogin flow:
 * 1. Check existing binding
 * 2. Check existing user by username
 * 2.5. tryAutoGrantFromSchoolDirectory (student → teacher → null)
 * 3-5. Original flow (role mapping, pending, auto-create)
 */
@ExtendWith(MockitoExtension.class)
class SsoUserMappingServiceAutoGrantTest {

    @Mock
    private SsoUserBindingMapper bindingMapper;

    @Mock
    private UserService userService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SchoolStudentMapper schoolStudentMapper;

    @Mock
    private SchoolTeacherMapper schoolTeacherMapper;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private RoleMappingService roleMappingService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SsoUserMappingService ssoUserMappingService;

    private SsoUserInfo ssoUserInfo;

    @BeforeEach
    void setUp() {
        ssoUserInfo = new SsoUserInfo();
        ssoUserInfo.setProvider("CAS");
        ssoUserInfo.setSsoUserId("2023122066164");
        ssoUserInfo.setSsoUsername("2023122066164");
        ssoUserInfo.setRealName("张三");
        ssoUserInfo.setEmail("zhangsan@imau.edu.cn");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("class", "外语2301");
        attributes.put("classCode", "FL2301");
        attributes.put("major", "英语");
        ssoUserInfo.setAttributes(attributes);
    }

    /**
     * Helper: mock steps 1+2 (no binding, no existing user) for auto-grant path
     */
    private void mockNoBindingNoExistingUser(String username) {
        when(bindingMapper.findByProviderAndSsoUserId("CAS", username)).thenReturn(null);
        when(userService.getUserByUsername(username)).thenReturn(null);
    }

    /**
     * Helper: mock user creation via userService.createUser
     */
    private void mockCreateUser(long userId) {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return null;
        }).when(userService).createUser(any(User.class));
    }

    /**
     * Helper: mock binding creation
     */
    private void mockCreateBinding(long bindingId) {
        doAnswer(invocation -> {
            SsoUserBinding binding = invocation.getArgument(0);
            binding.setId(bindingId);
            return 1;
        }).when(bindingMapper).insert(any(SsoUserBinding.class));
    }

    @Nested
    @DisplayName("学生自动授权")
    class StudentAutoGrantTests {

        @Test
        @DisplayName("school_student命中时自动创建STUDENT账户")
        void autoGrant_studentMatch_createsStudentAccount() {
            mockNoBindingNoExistingUser("2023122066164");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");

            SchoolStudent student = new SchoolStudent();
            student.setStudentId("2023122066164");
            student.setStudentName("张三");
            student.setCollegeName("外国语学院");
            when(schoolStudentMapper.selectByStudentId("2023122066164")).thenReturn(student);

            mockCreateUser(100L);
            mockCreateBinding(200L);
            when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock_jwt_token");
            when(schoolStudentMapper.updateClassInfo(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(1);

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertFalse(result.isNeedsApproval());
            assertEquals("mock_jwt_token", result.getToken());
            assertNotNull(result.getUser());
            assertEquals("2023122066164", result.getUser().getUsername());
            assertEquals("STUDENT", result.getUser().getRole());

            verify(schoolStudentMapper).selectByStudentId("2023122066164");
            verify(schoolStudentMapper).updateClassInfo("2023122066164", "FL2301", "外语2301", "英语");
            verify(userService).createUser(argThat(user ->
                    "2023122066164".equals(user.getUsername()) &&
                    "STUDENT".equals(user.getRole()) &&
                    "ACTIVE".equals(user.getStatus())
            ));
            verify(bindingMapper).insert(any(SsoUserBinding.class));
        }

        @Test
        @DisplayName("CAS subject与uid不同时按uid匹配学校目录并保留subject绑定")
        void autoGrant_subjectDifferentFromUsername_usesUsernameForDirectoryAndSubjectForBinding() {
            ssoUserInfo.setSsoUserId("opaque-cas-subject");
            ssoUserInfo.setSsoUsername("2023122066164");

            when(bindingMapper.findByProviderAndSsoUserId("CAS", "opaque-cas-subject")).thenReturn(null);
            when(userService.getUserByUsername("2023122066164")).thenReturn(null);
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");

            SchoolStudent student = new SchoolStudent();
            student.setStudentId("2023122066164");
            student.setStudentName("张三");
            when(schoolStudentMapper.selectByStudentId("2023122066164")).thenReturn(student);

            mockCreateUser(104L);
            mockCreateBinding(204L);
            when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock_jwt_token");
            when(schoolStudentMapper.updateClassInfo(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(1);

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertEquals("STUDENT", result.getUser().getRole());
            assertEquals("2023122066164", result.getUser().getUsername());
            verify(schoolStudentMapper).selectByStudentId("2023122066164");
            verify(bindingMapper).insert(argThat(binding ->
                    "opaque-cas-subject".equals(binding.getSsoUserId())
                            && "2023122066164".equals(binding.getSsoUsername())
                            && binding.getAutoApproved() == 1
            ));
        }

        @Test
        @DisplayName("feature toggle关闭时降级为USER待审批")
        void autoGrant_featureDisabled_fallsBackToPendingUser() {
            mockNoBindingNoExistingUser("2023122066164");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("false");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_teacher", "false"))
                    .thenReturn("false");
            when(roleMappingService.mapRole(anyList())).thenReturn(null);
            mockCreateUser(102L);
            mockCreateBinding(202L);
            when(jwtUtil.generateToken("2023122066164", "USER")).thenReturn("pending_token");

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertFalse(result.isNeedsApproval());
            assertTrue(result.isApprovalPendingAfterLogin());
            assertEquals("pending_token", result.getToken());
            assertEquals("USER", result.getUser().getRole());
            verify(userService).createUser(argThat(user ->
                    "2023122066164".equals(user.getUsername()) &&
                    "USER".equals(user.getRole()) &&
                    "ACTIVE".equals(user.getStatus())
            ));
            verify(bindingMapper).insert(argThat(binding ->
                    Long.valueOf(102L).equals(binding.getUserId()) && binding.getAutoApproved() == 0
            ));
        }

        @Test
        @DisplayName("school_student未命中时降级为USER待审批")
        void autoGrant_studentNotFound_fallsBackToPendingUser() {
            mockNoBindingNoExistingUser("2023122066164");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");
            when(schoolStudentMapper.selectByStudentId("2023122066164")).thenReturn(null);
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_teacher", "false"))
                    .thenReturn("false");
            when(roleMappingService.mapRole(anyList())).thenReturn(null);
            mockCreateUser(103L);
            mockCreateBinding(203L);
            when(jwtUtil.generateToken("2023122066164", "USER")).thenReturn("pending_token");

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertFalse(result.isNeedsApproval());
            assertTrue(result.isApprovalPendingAfterLogin());
            assertEquals("pending_token", result.getToken());
            assertEquals("USER", result.getUser().getRole());
            verify(schoolStudentMapper).selectByStudentId("2023122066164");
            verify(userService).createUser(argThat(user ->
                    "2023122066164".equals(user.getUsername()) &&
                    "USER".equals(user.getRole()) &&
                    "ACTIVE".equals(user.getStatus())
            ));
            verify(bindingMapper).insert(argThat(binding ->
                    Long.valueOf(103L).equals(binding.getUserId()) && binding.getAutoApproved() == 0
            ));
        }
    }

    @Nested
    @DisplayName("教师自动授权")
    class TeacherAutoGrantTests {

        @Test
        @DisplayName("school_teacher命中时自动创建TEACHER账户")
        void autoGrant_teacherMatch_createsTeacherAccount() {
            ssoUserInfo.setSsoUserId("T001");
            ssoUserInfo.setSsoUsername("T001");
            ssoUserInfo.setRealName("李老师");

            mockNoBindingNoExistingUser("T001");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_teacher", "false"))
                    .thenReturn("true");

            SchoolTeacher teacher = new SchoolTeacher();
            teacher.setTeacherId("T001");
            teacher.setTeacherName("李老师");
            teacher.setCollegeName("外国语学院");
            when(schoolTeacherMapper.selectByTeacherId("T001")).thenReturn(teacher);

            mockCreateUser(101L);
            mockCreateBinding(201L);
            when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock_jwt_token");

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertFalse(result.isNeedsApproval());
            assertNotNull(result.getUser());
            assertEquals("T001", result.getUser().getUsername());
            assertEquals("TEACHER", result.getUser().getRole());

            verify(schoolTeacherMapper).selectByTeacherId("T001");
            verify(userService).createUser(argThat(user ->
                    "T001".equals(user.getUsername()) &&
                    "TEACHER".equals(user.getRole()) &&
                    "ACTIVE".equals(user.getStatus())
            ));
        }

        @Test
        @DisplayName("优先使用解析出的userId完成本地用户与学校教师目录配对")
        void autoGrant_teacherMatch_usesParsedUserIdForDatabasePairing() {
            ssoUserInfo.setSsoUserId("opaque-subject");
            ssoUserInfo.setSsoUsername("display-name");
            ssoUserInfo.setRealName("李老师");
            ssoUserInfo.getAttributes().put("userId", "T001");

            when(bindingMapper.findByProviderAndSsoUserId("CAS", "opaque-subject")).thenReturn(null);
            when(userService.getUserByUsername("T001")).thenReturn(null);
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_teacher", "false"))
                    .thenReturn("true");

            SchoolTeacher teacher = new SchoolTeacher();
            teacher.setTeacherId("T001");
            teacher.setTeacherName("李老师");
            teacher.setCollegeName("外国语学院");
            when(schoolTeacherMapper.selectByTeacherId("T001")).thenReturn(teacher);

            mockCreateUser(105L);
            mockCreateBinding(205L);
            when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock_jwt_token");

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertEquals("TEACHER", result.getUser().getRole());
            assertEquals("T001", result.getUser().getUsername());
            verify(userService).getUserByUsername("T001");
            verify(schoolTeacherMapper).selectByTeacherId("T001");
            verify(bindingMapper).insert(argThat(binding ->
                    "opaque-subject".equals(binding.getSsoUserId())
                            && "T001".equals(binding.getSsoUsername())
                            && binding.getAutoApproved() == 1
            ));
        }

        @Test
        @DisplayName("student优先于teacher检查")
        void autoGrant_studentPriority_checksStudentFirst() {
            mockNoBindingNoExistingUser("2023122066164");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");

            SchoolStudent student = new SchoolStudent();
            student.setStudentId("2023122066164");
            when(schoolStudentMapper.selectByStudentId("2023122066164")).thenReturn(student);

            mockCreateUser(100L);
            mockCreateBinding(200L);
            when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock_jwt_token");
            when(schoolStudentMapper.updateClassInfo(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(1);

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertEquals("STUDENT", result.getUser().getRole());
            verify(schoolStudentMapper).selectByStudentId("2023122066164");
            verify(schoolTeacherMapper, never()).selectByTeacherId(anyString());
        }
    }

    @Nested
    @DisplayName("已有用户登录时按学校目录升权")
    class ExistingUserUpgradeTests {

        @Test
        @DisplayName("待审批绑定保留本地高权限时必须人工审核且不发高权限token")
        void pendingBindingWithElevatedLocalRole_requiresReviewWithoutToken() {
            SsoUserBinding binding = new SsoUserBinding();
            binding.setId(401L);
            binding.setUserId(400L);
            binding.setAutoApproved(0);

            User pendingUser = new User();
            pendingUser.setId(400L);
            pendingUser.setUsername("2023122066164");
            pendingUser.setRole("TEACHER");
            pendingUser.setStatus("PENDING_APPROVAL");

            when(bindingMapper.findByProviderAndSsoUserId("CAS", "2023122066164")).thenReturn(binding);
            when(userService.getUserById(400L)).thenReturn(pendingUser);
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("false");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_teacher", "false"))
                    .thenReturn("false");
            when(roleMappingService.mapRole(anyList())).thenReturn(null);

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertTrue(result.isNeedsApproval());
            assertNull(result.getToken());
            assertNull(result.getUser());
            assertEquals(401L, result.getBindingId());
            verify(userService, never()).updateUser(any(User.class));
            verify(jwtUtil, never()).generateToken(eq("2023122066164"), eq("TEACHER"));
        }

        @Test
        @DisplayName("已有USER用户命中school_student时自动升权为STUDENT")
        void existingUser_studentDirectoryMatch_upgradesToStudent() {
            User existingUser = new User();
            existingUser.setId(300L);
            existingUser.setUsername("2023122066164");
            existingUser.setRole("USER");
            existingUser.setStatus("ACTIVE");

            when(bindingMapper.findByProviderAndSsoUserId("CAS", "2023122066164")).thenReturn(null);
            when(userService.getUserByUsername("2023122066164")).thenReturn(existingUser);
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");

            SchoolStudent student = new SchoolStudent();
            student.setStudentId("2023122066164");
            student.setStudentName("张三");
            when(schoolStudentMapper.selectByStudentId("2023122066164")).thenReturn(student);

            when(schoolStudentMapper.updateClassInfo(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(1);
            doNothing().when(userService).updateUser(any(User.class));
            doAnswer(invocation -> {
                SsoUserBinding binding = invocation.getArgument(0);
                binding.setId(301L);
                return 1;
            }).when(bindingMapper).insert(any(SsoUserBinding.class));
            when(jwtUtil.generateToken("2023122066164", "STUDENT")).thenReturn("student_token");

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertFalse(result.isNeedsApproval());
            assertEquals("STUDENT", result.getUser().getRole());
            assertEquals("student_token", result.getToken());
            verify(userService).updateUser(argThat(user ->
                    user.getId().equals(300L)
                            && "STUDENT".equals(user.getRole())
                            && "ACTIVE".equals(user.getStatus())
            ));
            verify(bindingMapper).insert(argThat(binding ->
                    binding.getUserId().equals(300L) && binding.getAutoApproved() == 1
            ));
        }

        @Test
        @DisplayName("已有TEACHER用户仅命中school_student时进入人工审核且不发教师token")
        void existingTeacher_studentDirectoryMatch_requiresReviewWithoutTeacherToken() {
            User existingUser = new User();
            existingUser.setId(302L);
            existingUser.setUsername("2023122066164");
            existingUser.setRole("TEACHER");
            existingUser.setStatus("ACTIVE");

            when(bindingMapper.findByProviderAndSsoUserId("CAS", "2023122066164")).thenReturn(null);
            when(userService.getUserByUsername("2023122066164")).thenReturn(existingUser);
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");

            SchoolStudent student = new SchoolStudent();
            student.setStudentId("2023122066164");
            when(schoolStudentMapper.selectByStudentId("2023122066164")).thenReturn(student);
            when(roleMappingService.mapRole(anyList())).thenReturn(null);
            doAnswer(invocation -> {
                SsoUserBinding binding = invocation.getArgument(0);
                binding.setId(302L);
                return 1;
            }).when(bindingMapper).insert(any(SsoUserBinding.class));

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertTrue(result.isNeedsApproval());
            assertFalse(result.isApprovalPendingAfterLogin());
            assertNull(result.getToken());
            assertNull(result.getUser());
            verify(userService, never()).updateUser(any(User.class));
            verify(jwtUtil, never()).generateToken(eq("2023122066164"), eq("TEACHER"));
            verify(bindingMapper).insert(argThat(binding ->
                    binding.getUserId().equals(302L) && binding.getAutoApproved() == 0
            ));
            verify(schoolStudentMapper).selectByStudentId("2023122066164");
        }
    }

    @Nested
    @DisplayName("CAS attributes回填")
    class CasAttributesBackfillTests {

        @Test
        @DisplayName("CAS attributes包含class信息时回填到school_student")
        void backfill_casAttributesPresent_updatesClassInfo() {
            mockNoBindingNoExistingUser("2023122066164");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");

            SchoolStudent student = new SchoolStudent();
            student.setStudentId("2023122066164");
            when(schoolStudentMapper.selectByStudentId("2023122066164")).thenReturn(student);

            mockCreateUser(100L);
            mockCreateBinding(200L);
            when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock_jwt_token");
            when(schoolStudentMapper.updateClassInfo(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(1);

            ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            // backfillStudentClassInfo: classCode="FL2301", className="外语2301" (from "class" key), major="英语"
            verify(schoolStudentMapper).updateClassInfo("2023122066164", "FL2301", "外语2301", "英语");
        }

        @Test
        @DisplayName("CAS attributes缺失class信息时不回填")
        void backfill_casAttributesMissing_doesNotUpdate() {
            ssoUserInfo.setAttributes(new HashMap<>());

            mockNoBindingNoExistingUser("2023122066164");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");

            SchoolStudent student = new SchoolStudent();
            student.setStudentId("2023122066164");
            when(schoolStudentMapper.selectByStudentId("2023122066164")).thenReturn(student);

            mockCreateUser(100L);
            mockCreateBinding(200L);
            when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock_jwt_token");

            ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            verify(schoolStudentMapper, never()).updateClassInfo(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("管理员角色锁定保护")
    class AdminRoleLockedTests {

        @Test
        @DisplayName("adminRoleLocked=1 时学校目录命中也不覆盖管理员指定角色")
        void adminRoleLocked_schoolDirectoryMatch_doesNotOverrideRole() {
            SsoUserBinding binding = new SsoUserBinding();
            binding.setId(500L);
            binding.setUserId(501L);
            binding.setAutoApproved(1);
            binding.setAdminRoleLocked(1);

            User user = new User();
            user.setId(501L);
            user.setUsername("2023122066164");
            user.setRole("USER"); // 管理员有意指定为 USER（限制权限）
            user.setStatus("ACTIVE");

            when(bindingMapper.findByProviderAndSsoUserId("CAS", "2023122066164")).thenReturn(binding);
            when(userService.getUserById(501L)).thenReturn(user);
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_teacher", "false"))
                    .thenReturn("true");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");
            // 学校目录中有该学生记录
            SchoolStudent student = new SchoolStudent();
            student.setStudentId("2023122066164");
            student.setStudentName("张三");
            when(schoolStudentMapper.selectByStudentId("2023122066164")).thenReturn(student);
            when(roleMappingService.mapRole(anyList())).thenReturn(null);
            when(jwtUtil.generateToken("2023122066164", "USER")).thenReturn("user_token");

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertFalse(result.isNeedsApproval());
            assertEquals("USER", result.getUser().getRole());
            assertEquals("user_token", result.getToken());
            // 管理员锁定后不允许 updateUser 修改角色
            verify(userService, never()).updateUser(any(User.class));
        }

        @Test
        @DisplayName("adminRoleLocked=1 时 SSO 映射角色更高也不覆盖管理员指定角色")
        void adminRoleLocked_ssoMappedRoleHigher_doesNotOverrideRole() {
            SsoUserBinding binding = new SsoUserBinding();
            binding.setId(502L);
            binding.setUserId(503L);
            binding.setAutoApproved(1);
            binding.setAdminRoleLocked(1);

            User user = new User();
            user.setId(503L);
            user.setUsername("T001");
            user.setRole("STUDENT"); // 管理员有意降为 STUDENT
            user.setStatus("ACTIVE");

            when(bindingMapper.findByProviderAndSsoUserId("CAS", "2023122066164")).thenReturn(binding);
            when(userService.getUserById(503L)).thenReturn(user);
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_teacher", "false"))
                    .thenReturn("false");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("false");
            // SSO 映射出 TEACHER（比 STUDENT 高）
            when(roleMappingService.mapRole(anyList())).thenReturn("TEACHER");
            when(jwtUtil.generateToken("T001", "STUDENT")).thenReturn("student_token");

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertFalse(result.isNeedsApproval());
            assertEquals("STUDENT", result.getUser().getRole());
            assertEquals("student_token", result.getToken());
            verify(userService, never()).updateUser(any(User.class));
        }
    }
}
