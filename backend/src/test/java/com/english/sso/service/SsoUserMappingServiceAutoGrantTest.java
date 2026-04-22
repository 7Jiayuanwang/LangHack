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
            return;
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
            doNothing().when(schoolStudentMapper).updateClassInfo(anyString(), anyString(), anyString(), anyString());

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
                    user.getEnabled()
            ));
            verify(bindingMapper).insert(any(SsoUserBinding.class));
        }

        @Test
        @DisplayName("feature toggle关闭时不自动授权学生")
        void autoGrant_featureDisabled_doesNotGrantStudent() {
            mockNoBindingNoExistingUser("2023122066164");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("false");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_teacher", "false"))
                    .thenReturn("false");
            when(roleMappingService.mapRole(anyList())).thenReturn(null);

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertTrue(result.isNeedsApproval());
            verify(userService, never()).createUser(any(User.class));
        }

        @Test
        @DisplayName("school_student未命中时不自动授权")
        void autoGrant_studentNotFound_doesNotGrant() {
            mockNoBindingNoExistingUser("2023122066164");
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_student", "false"))
                    .thenReturn("true");
            when(schoolStudentMapper.selectByStudentId("2023122066164")).thenReturn(null);
            when(systemConfigService.get("feature_toggle", "sso.auto_grant_teacher", "false"))
                    .thenReturn("false");
            when(roleMappingService.mapRole(anyList())).thenReturn(null);

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertTrue(result.isNeedsApproval());
            verify(userService, never()).createUser(any(User.class));
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
            when(schoolStudentMapper.selectByStudentId("T001")).thenReturn(null);
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
                    user.getEnabled()
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
            doNothing().when(schoolStudentMapper).updateClassInfo(anyString(), anyString(), anyString(), anyString());

            SsoLoginResult result = ssoUserMappingService.processLogin(ssoUserInfo, SsoProtocolType.CAS);

            assertNotNull(result);
            assertEquals("STUDENT", result.getUser().getRole());
            verify(schoolStudentMapper).selectByStudentId("2023122066164");
            verify(schoolTeacherMapper, never()).selectByTeacherId(anyString());
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
            doNothing().when(schoolStudentMapper).updateClassInfo(anyString(), anyString(), anyString(), anyString());

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
}
