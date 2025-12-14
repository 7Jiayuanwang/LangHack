package com.english.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt密码生成工具
 * 用于生成演示账户的BCrypt密码哈希
 */
public class BCryptPasswordGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        System.out.println("=== LangHack 演示账户密码生成 ===\n");

        // 生成admin123的BCrypt哈希
        String adminPassword = "admin123";
        String adminHash = encoder.encode(adminPassword);
        System.out.println("Admin账户:");
        System.out.println("  用户名: admin");
        System.out.println("  密码: " + adminPassword);
        System.out.println("  BCrypt: " + adminHash);
        System.out.println();

        // 生成teacher123的BCrypt哈希
        String teacherPassword = "teacher123";
        String teacherHash = encoder.encode(teacherPassword);
        System.out.println("Teacher账户:");
        System.out.println("  用户名: teacher");
        System.out.println("  密码: " + teacherPassword);
        System.out.println("  BCrypt: " + teacherHash);
        System.out.println();

        // 生成student123的BCrypt哈希
        String studentPassword = "student123";
        String studentHash = encoder.encode(studentPassword);
        System.out.println("Student账户:");
        System.out.println("  用户名: student");
        System.out.println("  密码: " + studentPassword);
        System.out.println("  BCrypt: " + studentHash);
        System.out.println();

        // 生成SQL插入语句
        System.out.println("=== SQL插入语句 ===\n");
        System.out.println("DELETE FROM users WHERE username IN ('admin', 'teacher', 'student');\n");

        System.out.println("INSERT INTO users (username, password, real_name, role, email, status, deleted, created_time, updated_time) VALUES");
        System.out.println("('admin', '" + adminHash + "', '系统管理员', 'ADMIN', 'admin@langhack.com', 'ACTIVE', 0, NOW(), NOW()),");
        System.out.println("('teacher', '" + teacherHash + "', '示范教师', 'TEACHER', 'teacher@langhack.com', 'ACTIVE', 0, NOW(), NOW()),");
        System.out.println("('student', '" + studentHash + "', '示范学生', 'STUDENT', 'student@langhack.com', 'ACTIVE', 0, NOW(), NOW());");
    }
}
