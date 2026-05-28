package com.english.dto.learner;

import lombok.Data;

@Data
public class LearnerIdentityDto {
    private Long userId;
    private String username;
    private String realName;
    private String role;
    private String className;
    private String collegeName;
    private String grade;
    private String majorName;
}
