package com.english.controller;

import com.english.dto.learner.AgentStudyLoopDto;
import com.english.entity.User;
import com.english.service.UserService;
import com.english.service.impl.AgentStudyLoopService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentStudyLoopControllerTest {

    @Test
    void exposesOnlyCurrentStudentsStudyEvidence() throws Exception {
        AgentStudyLoopService service = mock(AgentStudyLoopService.class);
        UserService users = mock(UserService.class);
        User user = new User();
        user.setId(7L);
        when(users.getUserByUsername("student01")).thenReturn(user);
        AgentStudyLoopDto result = new AgentStudyLoopDto();
        result.setAttemptCount(2);
        when(service.forUser(7L)).thenReturn(result);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AgentStudyLoopController(service, users)).build();

        mvc.perform(get("/api/student/agent/study-loop")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "student01", null, Collections.emptyList())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptCount").value(2));
        verify(service).forUser(7L);
    }
}
