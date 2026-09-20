package com.english.controller;

import com.english.dto.learner.ArticleFeedPageDto;
import com.english.entity.User;
import com.english.service.UserService;
import com.english.service.impl.ArticleFeedService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArticleFeedControllerTest {
    @Test
    void usesAuthenticatedUserAndBoundsParsedExclusions() throws Exception {
        ArticleFeedService service = mock(ArticleFeedService.class);
        UserService users = mock(UserService.class);
        User user = new User();
        user.setId(7L);
        when(users.getUserByUsername("student01")).thenReturn(user);
        when(service.getPage(7L, "FOR_YOU", null, 1, 12, 123L, List.of(3L, 4L)))
                .thenReturn(new ArticleFeedPageDto());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ArticleFeedController(service, users)).build();

        mvc.perform(get("/api/feed/articles?seed=123&exclude=3,wrong,4")
                        .principal(new UsernamePasswordAuthenticationToken("student01", null, Collections.emptyList())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).getPage(7L, "FOR_YOU", null, 1, 12, 123L, List.of(3L, 4L));
    }

    @Test
    void anonymousUserCannotRecordFeedEvent() throws Exception {
        ArticleFeedService service = mock(ArticleFeedService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ArticleFeedController(service, mock(UserService.class))).build();

        mvc.perform(post("/api/feed/events").contentType("application/json")
                        .content("{\"textId\":8,\"eventType\":\"CLICK\",\"source\":\"FEED_FOR_YOU\",\"position\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
