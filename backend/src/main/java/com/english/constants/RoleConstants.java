package com.english.constants;

/**
 * 角色常量类
 * 统一定义系统中使用的角色字符串，确保角色检查的一致性
 */
public final class RoleConstants {

    /**
     * 角色名称 - 管理员
     */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /**
     * 角色名称 - 教师
     */
    public static final String ROLE_TEACHER = "ROLE_TEACHER";

    /**
     * 角色名称 - 学生
     */
    public static final String ROLE_STUDENT = "ROLE_STUDENT";

    /**
     * Spring Security注解中使用的角色名称（不带ROLE_前缀）
     */
    public static final class Annotation {
        public static final String ADMIN = "ADMIN";
        public static final String TEACHER = "TEACHER";
        public static final String STUDENT = "STUDENT";
    }

    /**
     * 私有构造函数，防止实例化
     */
    private RoleConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}
