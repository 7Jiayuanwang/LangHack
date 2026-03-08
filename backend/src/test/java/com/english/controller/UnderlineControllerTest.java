package com.english.controller;

import com.english.entity.Underline;
import com.english.entity.User;
import com.english.service.UnderlineService;
import com.english.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Collections;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnderlineControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UnderlineService underlineService;

    @Mock
    private UserService userService;

    @InjectMocks
    private UnderlineController underlineController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(underlineController).build();

        // Set up SecurityContext for the controller
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testuser", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Mock userService to return a valid user
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        when(userService.getUserByUsername("testuser")).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUnderline_ShouldReturnSuccess() throws Exception {
        String payload = "{\"userId\":1,\"textId\":100,\"selectedText\":\"test\",\"startIndex\":0,\"endIndex\":4}";

        mockMvc.perform(post("/api/underlines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(underlineService).createUnderline(
            eq(1L),
            eq(100L),
            eq("test"),
            eq(0),
            eq(4),
            any()  // 最后一个参数使用 any() matcher 增加灵活性
        );
    }

    @Test
    void getUnderlines_ShouldReturnList() throws Exception {
        Long textId = 100L;
        when(underlineService.getUnderlinesByTextIdAndUserId(textId, 1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/underlines/text/" + textId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }
}
