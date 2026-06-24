package com.english.dto;

import com.english.entity.User;

/**
 * 作者信息传输对象
 *
 * 用于向所有用户公开展示文章/视频的上传者信息。
 * 仅包含可公开的字段（真名、学号、角色），不暴露邮箱、密码等隐私数据。
 *
 * 平台为实名制学校平台，展示名优先使用真名（realName），
 * 回退到学号（username）。系统内置内容（无上传者）标记为官方内容。
 */
public class AuthorDTO {

    /** 上传者用户 ID（官方内容为 null） */
    private Long id;

    /** 展示名：真名优先，回退到学号 */
    private String displayName;

    /** 角色：ADMIN / TEACHER / STUDENT */
    private String role;

    /** 角色中文名：管理员 / 老师 / 学生 */
    private String roleLabel;

    /** 头像 URL（沿用平台用户头像；官方内容为 null，由前端用官方标识兜底） */
    private String avatar;

    /** 是否为平台官方内容（无具体上传者） */
    private boolean official;

    public AuthorDTO() {
    }

    /**
     * 由 User 构建作者信息。user 为 null 时返回官方内容标识。
     */
    public static AuthorDTO from(User user) {
        AuthorDTO dto = new AuthorDTO();
        if (user == null) {
            dto.official = true;
            dto.displayName = "官方内容";
            return dto;
        }
        dto.id = user.getId();
        dto.role = user.getRole();
        dto.roleLabel = toRoleLabel(user.getRole());
        dto.displayName = resolveDisplayName(user);
        dto.avatar = user.getAvatar();
        return dto;
    }

    /**
     * 官方内容（无上传者）。
     */
    public static AuthorDTO official() {
        return from(null);
    }

    /** 展示名：真名 → 学号 → 兜底"匿名用户" */
    private static String resolveDisplayName(User user) {
        if (user.getRealName() != null && !user.getRealName().trim().isEmpty()) {
            return user.getRealName().trim();
        }
        if (user.getUsername() != null && !user.getUsername().trim().isEmpty()) {
            return user.getUsername().trim();
        }
        return "匿名用户";
    }

    private static String toRoleLabel(String role) {
        if (role == null) {
            return "";
        }
        switch (role) {
            case "ADMIN":
                return "管理员";
            case "TEACHER":
                return "老师";
            case "STUDENT":
                return "学生";
            default:
                return role;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public void setRoleLabel(String roleLabel) {
        this.roleLabel = roleLabel;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public boolean isOfficial() {
        return official;
    }

    public void setOfficial(boolean official) {
        this.official = official;
    }
}
