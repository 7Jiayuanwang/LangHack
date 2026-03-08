package com.english.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoginRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        // 初始化 Validator
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== LoginRequest 字段验证 ====================

    /**
     * 测试用户名为空字符串时的验证
     * 验证 @NotBlank 注解是否捕获空字符串
     */
    @Test
    void testLoginRequest_blankUsername_violatesConstraint() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("");  // 空字符串
        loginRequest.setPassword("password123");

        // 执行验证
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        // 应该有一个违反约束的字段
        assertFalse(violations.isEmpty(), "应该存在违反约束的字段");
        assertEquals(1, violations.size(), "应该只有一个违反约束");

        // 验证违反的是 username 字段
        ConstraintViolation<LoginRequest> violation = violations.iterator().next();
        assertEquals("username", violation.getPropertyPath().toString());
        assertEquals("用户名不能为空", violation.getMessage());
    }

    /**
     * 测试用户名为 null 时的验证
     * 验证 @NotBlank 注解是否捕获 null
     */
    @Test
    void testLoginRequest_nullUsername_violatesConstraint() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(null);  // null
        loginRequest.setPassword("password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        assertFalse(violations.isEmpty(), "null 用户名应该违反约束");
        assertEquals(1, violations.size());

        ConstraintViolation<LoginRequest> violation = violations.iterator().next();
        assertEquals("username", violation.getPropertyPath().toString());
        assertEquals("用户名不能为空", violation.getMessage());
    }

    /**
     * 测试用户名仅包含空格时的验证
     * 验证 @NotBlank 注解是否捕获仅空格的字符串
     */
    @Test
    void testLoginRequest_whitespaceUsername_violatesConstraint() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("   ");  // 仅空格
        loginRequest.setPassword("password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        assertFalse(violations.isEmpty(), "仅空格的用户名应该违反约束");
        assertEquals(1, violations.size());

        ConstraintViolation<LoginRequest> violation = violations.iterator().next();
        assertEquals("username", violation.getPropertyPath().toString());
    }

    /**
     * 测试密码为空字符串时的验证
     * 验证 @NotBlank 注解是否捕获空密码
     */
    @Test
    void testLoginRequest_blankPassword_violatesConstraint() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("");  // 空字符串

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        assertFalse(violations.isEmpty(), "应该存在违反约束的字段");
        assertEquals(1, violations.size(), "应该只有一个违反约束");

        // 验证违反的是 password 字段
        ConstraintViolation<LoginRequest> violation = violations.iterator().next();
        assertEquals("password", violation.getPropertyPath().toString());
        assertEquals("密码不能为空", violation.getMessage());
    }

    /**
     * 测试密码为 null 时的验证
     * 验证 @NotBlank 注解是否捕获 null 密码
     */
    @Test
    void testLoginRequest_nullPassword_violatesConstraint() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword(null);  // null

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        assertFalse(violations.isEmpty(), "null 密码应该违反约束");
        assertEquals(1, violations.size());

        ConstraintViolation<LoginRequest> violation = violations.iterator().next();
        assertEquals("password", violation.getPropertyPath().toString());
        assertEquals("密码不能为空", violation.getMessage());
    }

    /**
     * 测试密码仅包含空格时的验证
     * 验证 @NotBlank 注解是否捕获仅空格的密码
     */
    @Test
    void testLoginRequest_whitespacePassword_violatesConstraint() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("   ");  // 仅空格

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        assertFalse(violations.isEmpty(), "仅空格的密码应该违反约束");
        assertEquals(1, violations.size());

        ConstraintViolation<LoginRequest> violation = violations.iterator().next();
        assertEquals("password", violation.getPropertyPath().toString());
    }

    /**
     * 测试同时有效的用户名和密码
     * 验证 @NotBlank 注解在有效输入时是否不产生违反
     */
    @Test
    void testLoginRequest_validUsernameAndPassword_noViolations() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        // 应该没有任何违反
        assertTrue(violations.isEmpty(), "有效输入不应该有违反约束");
    }

    /**
     * 测试用户名为单个字符时
     * 验证 @NotBlank 注解接受最小长度的非空字符
     */
    @Test
    void testLoginRequest_singleCharUsername_valid() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("a");
        loginRequest.setPassword("password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        // @NotBlank 只要求非空，不对长度做限制
        assertTrue(violations.isEmpty(), "单个字符用户名应该通过验证");
    }

    /**
     * 测试密码为单个字符时
     * 验证 @NotBlank 注解接受最小长度的非空字符
     */
    @Test
    void testLoginRequest_singleCharPassword_valid() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("a");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        // @NotBlank 只要求非空，不对长度做限制
        assertTrue(violations.isEmpty(), "单个字符密码应该通过验证");
    }

    /**
     * 测试用户名和密码都为空时
     * 验证是否同时报告两个违反
     */
    @Test
    void testLoginRequest_bothBlank_violatesBothConstraints() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("");  // 空用户名
        loginRequest.setPassword("");  // 空密码

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        // 应该有两个违反
        assertEquals(2, violations.size(), "应该有两个违反约束");

        // 提取违反的字段名
        Set<String> violatedFields = new java.util.HashSet<>();
        violations.forEach(v -> violatedFields.add(v.getPropertyPath().toString()));

        assertTrue(violatedFields.contains("username"), "应该违反 username 字段");
        assertTrue(violatedFields.contains("password"), "应该违反 password 字段");
    }

    /**
     * 测试用户名包含特殊字符时
     * 验证 @NotBlank 注解是否接受包含特殊字符的用户名
     */
    @Test
    void testLoginRequest_specialCharacters_valid() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("user@example.com");
        loginRequest.setPassword("p@$$w0rd!#");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        // @NotBlank 不限制字符类型，只要求非空
        assertTrue(violations.isEmpty(), "包含特殊字符的输入应该通过 @NotBlank 验证");
    }

    /**
     * 测试用户名包含 Tab 和换行符时
     * 验证 @NotBlank 是否正确处理制表符和换行符
     * 注：@NotBlank 的实现通常会 trim() 后判断是否为空
     */
    @Test
    void testLoginRequest_usernameWithTabAndNewline_violatesConstraint() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("\t\n");  // Tab 和换行符
        loginRequest.setPassword("password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        // @NotBlank 应该将 \t\n 视为空白，trimmed() 后为空
        assertEquals(1, violations.size(), "仅包含空白字符应该违反 @NotBlank");
        assertEquals("username", violations.iterator().next().getPropertyPath().toString());
    }

    /**
     * 测试用户名前后有空格的情况
     * 验证 @NotBlank 是否正确处理带有前后空格的字符串
     */
    @Test
    void testLoginRequest_usernameWithLeadingTrailingSpaces_valid() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("  testuser  ");  // 前后有空格
        loginRequest.setPassword("password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        // @NotBlank 应该接受，因为 trim() 后非空
        assertTrue(violations.isEmpty(), "前后带空格的用户名应该通过 @NotBlank 验证");
    }

    /**
     * 测试极长的用户名和密码
     * 验证 @NotBlank 是否接受极长的字符串
     */
    @Test
    void testLoginRequest_veryLongCredentials_valid() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("a".repeat(1000));  // 1000 个 'a'
        loginRequest.setPassword("p".repeat(1000));  // 1000 个 'p'

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(loginRequest);

        // @NotBlank 只关心是否为空，不对长度做限制
        assertTrue(violations.isEmpty(), "极长的凭证应该通过 @NotBlank 验证");
    }

    /**
     * 测试 getter/setter 是否正常工作
     * 验证 LoginRequest 对象的属性访问是否正确
     */
    @Test
    void testLoginRequest_gettersSetters_workCorrectly() {
        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setUsername("testuser123");
        loginRequest.setPassword("mypassword456");

        assertEquals("testuser123", loginRequest.getUsername(), "getter 应该返回设置的 username");
        assertEquals("mypassword456", loginRequest.getPassword(), "getter 应该返回设置的 password");
    }

    /**
     * 测试 LoginRequest 默认构造函数
     * 验证新创建的对象字段是否为 null
     */
    @Test
    void testLoginRequest_defaultConstructor_fieldsNull() {
        LoginRequest loginRequest = new LoginRequest();

        assertNull(loginRequest.getUsername(), "新对象的 username 应该为 null");
        assertNull(loginRequest.getPassword(), "新对象的 password 应该为 null");
    }

    /**
     * 测试多次验证同一对象
     * 验证 Validator 是否可以多次使用
     */
    @Test
    void testLoginRequest_multipleValidations_consistent() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("");
        loginRequest.setPassword("password123");

        // 第一次验证
        Set<ConstraintViolation<LoginRequest>> violations1 = validator.validate(loginRequest);
        assertEquals(1, violations1.size());

        // 第二次验证（应该结果相同）
        Set<ConstraintViolation<LoginRequest>> violations2 = validator.validate(loginRequest);
        assertEquals(1, violations2.size());

        // 修改对象后再次验证
        loginRequest.setUsername("validuser");
        Set<ConstraintViolation<LoginRequest>> violations3 = validator.validate(loginRequest);
        assertEquals(0, violations3.size());
    }
}
