package com.english.service;

import com.english.dto.LoginRequest;
import com.english.entity.User;

import java.util.List;
import java.util.Map;

public interface UserService {
    Map<String, Object> login(LoginRequest loginRequest);
    User getUserById(Long id);
    /** 批量按 ID 查询用户，用于列表场景避免 N+1 查询 */
    List<User> getUsersByIds(java.util.Collection<Long> ids);
    User getUserByUsername(String username);
    Map<String, Object> getUsersByPage(Map<String, Object> params);
    void createUser(User user);
    void updateUser(User user);
    void deleteUser(Long id);
    void initDefaultAdmin();
    boolean validatePassword(String rawPassword, String encodedPassword);
    List<User> getTeachers();
    List<User> getStudents();
    Map<String, Object> getUserStats();
    List<User> searchUsers(String keyword, String role);
    void updateUserStatus(Long id, String status);
    void resetPassword(Long id, String newPassword);
}