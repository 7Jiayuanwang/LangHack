package com.english.controller;

import com.english.entity.CampaignActivity;
import com.english.enums.CampaignStatus;
import com.english.enums.EnrollmentMode;
import com.english.service.CampaignService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 活动管理控制器集成测试
 */
@WebMvcTest(CampaignController.class)
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CampaignService campaignService;

    private CampaignActivity testCampaign;

    @BeforeEach
    void setUp() {
        testCampaign = new CampaignActivity();
        testCampaign.setId(1L);
        testCampaign.setTitle("测试活动");
        testCampaign.setDescription("这是一个测试活动");
        testCampaign.setStatus(CampaignStatus.DRAFT);
        testCampaign.setEnrollmentMode(EnrollmentMode.MANUAL);
        testCampaign.setStartTime(LocalDateTime.now().plusDays(1));
        testCampaign.setEndTime(LocalDateTime.now().plusDays(30));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateCampaign_Success() throws Exception {
        // Given
        when(campaignService.createCampaign(any(CampaignActivity.class))).thenReturn(testCampaign);

        // When & Then
        mockMvc.perform(post("/api/admin/campaigns")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCampaign)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("测试活动"));

        verify(campaignService, times(1)).createCampaign(any(CampaignActivity.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateCampaign_Success() throws Exception {
        // Given
        when(campaignService.updateCampaign(eq(1L), any(CampaignActivity.class))).thenReturn(testCampaign);

        // When & Then
        mockMvc.perform(put("/api/admin/campaigns/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCampaign)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campaignService, times(1)).updateCampaign(eq(1L), any(CampaignActivity.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteCampaign_Success() throws Exception {
        // Given
        doNothing().when(campaignService).deleteCampaign(1L);

        // When & Then
        mockMvc.perform(delete("/api/admin/campaigns/1")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campaignService, times(1)).deleteCampaign(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetCampaignById_Success() throws Exception {
        // Given
        when(campaignService.getCampaignById(1L)).thenReturn(testCampaign);

        // When & Then
        mockMvc.perform(get("/api/admin/campaigns/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("测试活动"));

        verify(campaignService, times(1)).getCampaignById(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testStartCampaign_Success() throws Exception {
        // Given
        doNothing().when(campaignService).startCampaign(1L);

        // When & Then
        mockMvc.perform(put("/api/admin/campaigns/1/start")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campaignService, times(1)).startCampaign(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testEndCampaign_Success() throws Exception {
        // Given
        doNothing().when(campaignService).endCampaign(1L);

        // When & Then
        mockMvc.perform(put("/api/admin/campaigns/1/end")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(campaignService, times(1)).endCampaign(1L);
    }

    @Test
    void testCreateCampaign_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/admin/campaigns")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCampaign)))
                .andExpect(status().isUnauthorized());

        verify(campaignService, never()).createCampaign(any(CampaignActivity.class));
    }

    @Test
    @WithMockUser(roles = "USER")
    void testCreateCampaign_Forbidden() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/admin/campaigns")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCampaign)))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).createCampaign(any(CampaignActivity.class));
    }
}
