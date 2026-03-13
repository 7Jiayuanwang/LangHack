package com.english.dto;

/**
 * 登录响应DTO
 * 使用UserDTO而不是User实体，以隐藏敏感字段（如password）
 */
public class LoginResponse {
    private String token;
    private UserDTO user;
    private Long expiresIn;

    public LoginResponse() {}

    public LoginResponse(String token, UserDTO user, Long expiresIn) {
        this.token = token;
        this.user = user;
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
