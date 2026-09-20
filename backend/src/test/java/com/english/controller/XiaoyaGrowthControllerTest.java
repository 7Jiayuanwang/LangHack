package com.english.controller;

import com.english.dto.learner.XiaoyaGrowthDto;
import com.english.entity.User;
import com.english.service.UserService;
import com.english.service.impl.XiaoyaGrowthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class XiaoyaGrowthControllerTest {
    @Test
    void readsOnlyAuthenticatedLearnersOwnGrowth() throws Exception {
        XiaoyaGrowthService service = mock(XiaoyaGrowthService.class);
        UserService users = mock(UserService.class);
        User user = new User();
        user.setId(7L);
        when(users.getUserByUsername("student01")).thenReturn(user);
        XiaoyaGrowthDto growth = new XiaoyaGrowthDto();
        growth.setStage("SPROUT");
        when(service.forUser(7L)).thenReturn(growth);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new XiaoyaGrowthController(service, users)).build();

        mvc.perform(get("/api/student/agent/growth")
                        .principal(new UsernamePasswordAuthenticationToken("student01", null, Collections.emptyList())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("SPROUT"));
        verify(service).forUser(7L);
    }

    @Test
    void refusesAnonymousExternalLearning() throws Exception {
        XiaoyaGrowthService service = mock(XiaoyaGrowthService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new XiaoyaGrowthController(service, mock(UserService.class))).build();

        mvc.perform(post("/api/student/agent/growth/external")
                        .contentType("application/json")
                        .content("{\"sourceType\":\"ARTICLE\",\"takeawayEn\":\"I learned five English words today.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
