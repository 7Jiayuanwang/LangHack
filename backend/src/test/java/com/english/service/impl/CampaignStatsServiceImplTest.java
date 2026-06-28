package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.CampaignParticipation;
import com.english.entity.SchoolStudent;
import com.english.entity.User;
import com.english.enums.EnrollmentStatus;
import com.english.mapper.CampaignParticipationMapper;
import com.english.mapper.CampaignTaskCompletionMapper;
import com.english.mapper.CampaignTaskMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.UserMapper;
import com.english.service.CampaignService;
import com.english.service.LearningRecordsService;
import com.english.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动统计服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class CampaignStatsServiceImplTest {

    @Mock
    private CampaignTaskCompletionMapper completionMapper;

    @Mock
    private CampaignParticipationMapper participationMapper;

    @Mock
    private CampaignTaskMapper taskMapper;

    @Mock
    private LearningRecordsService learningRecordsService;

    @Mock
    private CampaignService campaignService;

    @Mock
    private UserService userService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SchoolStudentMapper schoolStudentMapper;

    @InjectMocks
    private CampaignStatsServiceImpl statsService;

    @Test
    void testGetMyRanking_ReturnsUnenrolledWhenParticipationMissing() {
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        Map<String, Object> result = statsService.getMyRanking(1L, 100L);

        assertFalse((Boolean) result.get("enrolled"));
        assertNull(result.get("myRank"));
        assertEquals(0, result.get("totalParticipants"));
        assertEquals(List.of(), result.get("aroundMe"));
        assertNull(result.get("enrollmentStatus"));
        verify(participationMapper).selectOne(any(QueryWrapper.class));
        verify(participationMapper, never()).selectList(any(QueryWrapper.class));
    }

    @Test
    void testGetMyRanking_ReturnsUnenrolledWhenStatusIsPending() {
        CampaignParticipation pendingParticipation = createParticipation(1L, 100L, EnrollmentStatus.PENDING, 80, 1);
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(pendingParticipation);

        Map<String, Object> result = statsService.getMyRanking(1L, 100L);

        assertFalse((Boolean) result.get("enrolled"));
        assertNull(result.get("myRank"));
        assertEquals(0, result.get("totalParticipants"));
        assertEquals(List.of(), result.get("aroundMe"));
        assertEquals(EnrollmentStatus.PENDING, result.get("enrollmentStatus"));
        verify(participationMapper).selectOne(any(QueryWrapper.class));
        verify(participationMapper, never()).selectCount(any(QueryWrapper.class));
    }

    @Test
    void testGetMyRanking_ReturnsTieAwareAroundMeWithEnrichedFields() {
        CampaignParticipation me = createParticipation(3L, 103L, EnrollmentStatus.ENROLLED, 90, 2);
        List<CampaignParticipation> allRanked = List.of(
            createParticipation(1L, 101L, EnrollmentStatus.ENROLLED, 100, 3),
            createParticipation(2L, 102L, EnrollmentStatus.ENROLLED, 90, 2),
            createParticipation(3L, 103L, EnrollmentStatus.ENROLLED, 90, 2),
            createParticipation(4L, 104L, EnrollmentStatus.ENROLLED, 80, 1),
            createParticipation(5L, 105L, EnrollmentStatus.ENROLLED, 70, 1)
        );

        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(me);
        when(participationMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L, 5L);
        when(participationMapper.selectList(any(QueryWrapper.class))).thenReturn(allRanked);

        // Current implementation batch-loads users via userMapper.selectBatchIds
        when(userMapper.selectBatchIds(any())).thenReturn(java.util.Arrays.asList(
            createUser(101L, "u101", "昵称101", "真实姓名101", 11L),
            createUser(102L, "u102", "昵称102", null, 12L),
            createUser(103L, "2023003", null, null, null),
            createUser(104L, "u104", null, null, 14L),
            createUser(105L, "u105", null, null, null)
        ));

        // Batch-loaded school students by schoolStudentId
        when(schoolStudentMapper.selectBatchIds(any())).thenReturn(java.util.Arrays.asList(
            createStudent(11L, "2023001", "一班"),
            createStudent(12L, "2023002", "二班"),
            createStudent(14L, "2023004", "四班")
        ));
        // Fallback path: lookup by username as studentId
        when(schoolStudentMapper.selectByStudentId("2023003")).thenReturn(createStudent(13L, "2023003", "三班"));

        Map<String, Object> result = statsService.getMyRanking(1L, 103L);

        assertTrue((Boolean) result.get("enrolled"));
        assertEquals(2L, result.get("myRank"));
        assertEquals(5L, result.get("totalParticipants"));
        assertEquals(90, result.get("totalScore"));
        assertEquals(2, result.get("completedTasks"));
        assertEquals(EnrollmentStatus.ENROLLED, result.get("enrollmentStatus"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> aroundMe = (List<Map<String, Object>>) result.get("aroundMe");
        assertNotNull(aroundMe);
        assertEquals(5, aroundMe.size());

        Map<String, Object> first = aroundMe.get(0);
        assertEquals(1L, first.get("rank"));
        assertEquals(101L, first.get("userId"));
        assertEquals("真实姓名101", first.get("userName"));
        assertEquals("一班", first.get("className"));
        assertEquals(100, first.get("totalScore"));
        assertFalse((Boolean) first.get("isMe"));

        Map<String, Object> second = aroundMe.get(1);
        assertEquals(2L, second.get("rank"));
        assertEquals("昵称102", second.get("userName"));
        assertEquals("二班", second.get("className"));

        Map<String, Object> self = aroundMe.get(2);
        assertEquals(2L, self.get("rank"));
        assertEquals(103L, self.get("userId"));
        assertEquals("2023003", self.get("userName"));
        assertEquals("三班", self.get("className"));
        assertEquals(90, self.get("totalScore"));
        assertTrue((Boolean) self.get("isMe"));

        Map<String, Object> next = aroundMe.get(3);
        assertEquals(4L, next.get("rank"));
        assertEquals("四班", next.get("className"));
    }

    @Test
    void testGetMyProgress_ReturnsUnenrolledWhenStatusIsPending() {
        CampaignParticipation pendingParticipation = createParticipation(1L, 100L, EnrollmentStatus.PENDING, 80, 1);
        pendingParticipation.setCompletionStatus("IN_PROGRESS");
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(pendingParticipation);

        Map<String, Object> result = statsService.getMyProgress(1L, 100L);

        assertFalse((Boolean) result.get("enrolled"));
        assertEquals(EnrollmentStatus.PENDING, result.get("enrollmentStatus"));
        verify(participationMapper).selectOne(any(QueryWrapper.class));
        verify(participationMapper, never()).selectCount(any(QueryWrapper.class));
    }

    @Test
    void testGetMyProgress_ReturnsScoreAndRankForEnrolledUser() {
        CampaignParticipation me = createParticipation(1L, 100L, EnrollmentStatus.ENROLLED, 90, 2);
        me.setCompletionStatus("IN_PROGRESS");
        when(participationMapper.selectOne(any(QueryWrapper.class))).thenReturn(me);
        when(participationMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        Map<String, Object> result = statsService.getMyProgress(1L, 100L);

        assertTrue((Boolean) result.get("enrolled"));
        assertEquals(90, result.get("totalScore"));
        assertEquals(2, result.get("completedTasks"));
        assertEquals("IN_PROGRESS", result.get("completionStatus"));
        assertEquals(2L, result.get("myRank"));
        assertEquals(EnrollmentStatus.ENROLLED, result.get("enrollmentStatus"));
    }

    private CampaignParticipation createParticipation(Long id, Long userId, EnrollmentStatus status, Integer totalScore, Integer completedTasks) {
        CampaignParticipation participation = new CampaignParticipation();
        participation.setId(id);
        participation.setCampaignId(1L);
        participation.setUserId(userId);
        participation.setEnrollmentStatus(status);
        participation.setTotalScore(totalScore);
        participation.setCompletedTasks(completedTasks);
        return participation;
    }

    private User createUser(Long id, String username, String nickname, String realName, Long schoolStudentId) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setRealName(realName);
        user.setSchoolStudentId(schoolStudentId);
        return user;
    }

    private SchoolStudent createStudent(Long id, String studentId, String className) {
        SchoolStudent student = new SchoolStudent();
        student.setId(id);
        student.setStudentId(studentId);
        student.setClassName(className);
        return student;
    }
}
