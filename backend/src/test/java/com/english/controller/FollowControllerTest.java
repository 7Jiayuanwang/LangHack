package com.english.controller;

import com.english.dto.Result;
import com.english.entity.User;
import com.english.service.FollowService;
import com.english.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowControllerTest {

    @Mock
    private FollowService followService;

    @Mock
    private UserService userService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private FollowController followController;

    @Test
    void getFollowStatus_returnsMutualRelationship() {
        User currentUser = new User();
        currentUser.setId(1L);
        when(authentication.getName()).thenReturn("student1");
        when(userService.getUserByUsername("student1")).thenReturn(currentUser);
        when(followService.isFollowing(1L, 2L)).thenReturn(true);
        when(followService.isFollowing(2L, 1L)).thenReturn(true);

        Result<Map<String, Object>> result = followController.getFollowStatus(2L, authentication);

        assertEquals(200, result.getCode());
        assertEquals(true, result.getData().get("isFollowing"));
        assertEquals(true, result.getData().get("isFollowedBy"));
        assertEquals(true, result.getData().get("isMutualFollowing"));
    }
}
