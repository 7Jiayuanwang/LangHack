package com.english.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CampaignActivity;
import com.english.enums.CampaignStatus;
import com.english.enums.EnrollmentMode;
import com.english.enums.EnrollmentScope;
import com.english.mapper.CampaignActivityMapper;
import com.english.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 活动管理服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest {

    @Mock
    private CampaignActivityMapper campaignActivityMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private CampaignServiceImpl campaignService;

    private CampaignActivity testCampaign;

    @BeforeEach
    void setUp() {
        testCampaign = new CampaignActivity();
        testCampaign.setId(1L);
        testCampaign.setTitle("测试活动");
        testCampaign.setDescription("这是一个测试活动");
        testCampaign.setStartTime(LocalDateTime.now().plusDays(1));
        testCampaign.setEndTime(LocalDateTime.now().plusDays(30));
        testCampaign.setStatus(CampaignStatus.DRAFT);
        testCampaign.setEnrollmentMode(EnrollmentMode.MANUAL);
        testCampaign.setEnrollmentScope(EnrollmentScope.ALL);
        testCampaign.setMaxParticipants(100);
        testCampaign.setShowScores(true);
        testCampaign.setShowRanking(true);
    }

    @Test
    void testCreateCampaign_Success() {
        // Given
        when(campaignActivityMapper.insert(any(CampaignActivity.class))).thenReturn(1);

        // When
        CampaignActivity result = campaignService.createCampaign(testCampaign);

        // Then
        assertNotNull(result);
        assertEquals(CampaignStatus.DRAFT, result.getStatus());
        verify(campaignActivityMapper, times(1)).insert(any(CampaignActivity.class));
    }

    @Test
    void testGetCampaignById_Success() {
        // Given
        when(campaignActivityMapper.selectById(1L)).thenReturn(testCampaign);

        // When
        CampaignActivity result = campaignService.getCampaignById(1L);

        // Then
        assertNotNull(result);
        assertEquals("测试活动", result.getTitle());
        verify(campaignActivityMapper, times(1)).selectById(1L);
    }

    @Test
    void testStartCampaign_Success() {
        // Given
        testCampaign.setStatus(CampaignStatus.DRAFT);
        when(campaignActivityMapper.selectById(1L)).thenReturn(testCampaign);
        when(campaignActivityMapper.updateById(any(CampaignActivity.class))).thenReturn(1);

        // When
        campaignService.startCampaign(1L);

        // Then
        verify(campaignActivityMapper, times(1)).updateById(any(CampaignActivity.class));
    }

    @Test
    void testEndCampaign_Success() {
        // Given
        testCampaign.setStatus(CampaignStatus.ACTIVE);
        when(campaignActivityMapper.selectById(1L)).thenReturn(testCampaign);
        when(campaignActivityMapper.updateById(any(CampaignActivity.class))).thenReturn(1);

        // When
        campaignService.endCampaign(1L);

        // Then
        verify(campaignActivityMapper, times(1)).updateById(any(CampaignActivity.class));
    }

    @Test
    void testDeleteCampaign_Success() {
        // Given
        when(campaignActivityMapper.selectById(1L)).thenReturn(testCampaign);
        when(campaignActivityMapper.updateById(any(CampaignActivity.class))).thenReturn(1);

        // When
        campaignService.deleteCampaign(1L);

        // Then
        verify(campaignActivityMapper, times(1)).updateById(any(CampaignActivity.class));
    }

    @Test
    void testGetCampaigns_Success() {
        // Given
        Page<CampaignActivity> expectedPage = new Page<>(1, 10);
        Map<String, Object> params = new HashMap<>();
        params.put("status", "ACTIVE");

        when(campaignActivityMapper.selectPage(any(), any())).thenReturn(expectedPage);

        // When
        Page<CampaignActivity> result = campaignService.getCampaigns(1, 10, params);

        // Then
        assertNotNull(result);
        verify(campaignActivityMapper, times(1)).selectPage(any(), any());
    }

    @Test
    void testCanUserEnroll_AllScope_ReturnsTrue() {
        // Given
        testCampaign.setEnrollmentScope(EnrollmentScope.ALL);
        testCampaign.setStatus(CampaignStatus.ACTIVE);
        testCampaign.setStartTime(LocalDateTime.now().minusDays(1));
        testCampaign.setEndTime(LocalDateTime.now().plusDays(10));
        when(campaignActivityMapper.selectById(1L)).thenReturn(testCampaign);

        // When
        boolean result = campaignService.canUserEnroll(1L, 100L);

        // Then
        assertTrue(result);
    }

    @Test
    void testCanUserEnroll_ExpiredCampaign_ReturnsFalse() {
        // Given
        testCampaign.setStatus(CampaignStatus.ACTIVE);
        testCampaign.setStartTime(LocalDateTime.now().minusDays(30));
        testCampaign.setEndTime(LocalDateTime.now().minusDays(1));
        when(campaignActivityMapper.selectById(1L)).thenReturn(testCampaign);

        // When
        boolean result = campaignService.canUserEnroll(1L, 100L);

        // Then
        assertFalse(result);
    }
}
