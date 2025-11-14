package com.english.dto;

import com.english.entity.User;
import lombok.Data;

import java.util.List;

@Data
public class UserStatsDTO {
    private Integer adminCount;
    private Integer teacherCount;
    private Integer studentCount;
    private List<User> recentUsers;
}
