package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.CampaignActivity;
import com.english.entity.CampaignParticipation;
import com.english.enums.CampaignStatus;
import com.english.enums.EnrollmentMode;
import com.english.enums.EnrollmentStatus;
import com.english.mapper.CampaignParticipationMapper;
import com.english.service.CampaignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 活动报名服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class CampaignEnrollmentServiceImplTest {

    @Mock
    private CampaignParticipationMapper participationMapper;

    @Mock
    private CampaignService campaignService;

    @InjectMocks
    private CampaignEnrollmentServiceImpl enrollmentService;

    private CampaignActivity testCampaign;
    private CampaignParticipation testParticipation;

    @BeforeEach
    void setUp() {
        testCampaign = new CampaignActivity();
        testCampaign.setId(1L);
        testCampaign.setTitle("测试活动");
        testCampaign.setStatus(CampaignStatus.ACTIVE);
        testCampaign.setEnrollmentMode(EnrollmentMode.MANUAL);
        testCampaign.setStartTime(LocalDateTime.now().minusDays(1));
        testCampaign.setEndTime(LocalDateTime.now().plusDays(30));

        testParticipation = new CampaignParticipation();
        testParticipation.setId(1L);
        testParticipation.setCampaignId(1L);
        testParticipation.setUserId(100L);
        testParticipation.setEnrollmentStatus(EnrollmentStatus.ENROLLED);
    }

    @Test
    void testEnrollCampaign_Manual_Success() {
        // Given
        when(campaignService.getCampaignById(1L)).thenReturn(testCampaign);
        when(campaignService.canUserEnroll(1L, 100L)).thenReturn(true);
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(participationMapper.insert(any(CampaignParticipation.class))).thenReturn(1);

        // When
        enrollmentService.enrollCampaign(1L, 100L);

        // Then
        verify(participationMapper, times(1)).insert(any(CampaignParticipation.class));
    }

    @Test
    void testEnrollCampaign_InviteOnly_Pending() {
        // Given
        testCampaign.setEnrollmentMode(EnrollmentMode.INVITE_ONLY);
        when(campaignService.getCampaignById(1L)).thenReturn(testCampaign);
        when(campaignService.canUserEnroll(1L, 100L)).thenReturn(true);
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(participationMapper.insert(any(CampaignParticipation.class))).thenReturn(1);

        // When
        enrollmentService.enrollCampaign(1L, 100L);

        // Then
        verify(participationMapper, times(1)).insert(any(CampaignParticipation.class));
    }

    @Test
    void testEnrollCampaign_AlreadyEnrolled() {
        // Given
        when(campaignService.getCampaignById(1L)).thenReturn(testCampaign);
        when(campaignService.canUserEnroll(1L, 100L)).thenReturn(true);
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(testParticipation);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            enrollmentService.enrollCampaign(1L, 100L);
        });
    }

    @Test
    void testEnrollCampaign_NotEligible() {
        // Given
        when(campaignService.getCampaignById(1L)).thenReturn(testCampaign);
        when(campaignService.canUserEnroll(1L, 100L)).thenReturn(false);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            enrollmentService.enrollCampaign(1L, 100L);
        });
    }

    @Test
    void testWithdrawCampaign_Success() {
        // Given
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(testParticipation);
        when(participationMapper.updateById(any(CampaignParticipation.class))).thenReturn(1);

        // When
        enrollmentService.withdrawCampaign(1L, 100L);

        // Then
        verify(participationMapper, times(1)).updateById(any(CampaignParticipation.class));
    }

    @Test
    void testWithdrawCampaign_NotEnrolled() {
        // Given
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            enrollmentService.withdrawCampaign(1L, 100L);
        });
    }

    @Test
    void testApproveEnrollment_Success() {
        // Given
        testParticipation.setEnrollmentStatus(EnrollmentStatus.PENDING);
        when(participationMapper.selectById(1L)).thenReturn(testParticipation);
        when(participationMapper.updateById(any(CampaignParticipation.class))).thenReturn(1);

        // When
        enrollmentService.approveEnrollment(1L);

        // Then
        verify(participationMapper, times(1)).updateById(any(CampaignParticipation.class));
    }

    @Test
    void testRejectEnrollment_Success() {
        // Given
        testParticipation.setEnrollmentStatus(EnrollmentStatus.PENDING);
        when(participationMapper.selectById(1L)).thenReturn(testParticipation);
        when(participationMapper.updateById(any(CampaignParticipation.class))).thenReturn(1);

        // When
        enrollmentService.rejectEnrollment(1L, "不符合参与条件");

        // Then
        verify(participationMapper, times(1)).updateById(any(CampaignParticipation.class));
    }

    @Test
    void testIsEnrolled_True() {
        // Given
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(testParticipation);

        // When
        boolean result = enrollmentService.isEnrolled(1L, 100L);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsEnrolled_False() {
        // Given
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // When
        boolean result = enrollmentService.isEnrolled(1L, 100L);

        // Then
        assertFalse(result);
    }
}
