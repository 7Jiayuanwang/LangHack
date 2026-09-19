package com.english.service;

import com.english.dto.learner.InternetContentRecommendationDto;
import com.english.entity.LearnerInterest;
import com.english.entity.StudentSchedule;
import com.english.entity.Text;
import com.english.entity.User;
import com.english.mapper.LearnerGoalMapper;
import com.english.mapper.LearnerInterestMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.service.impl.InternetContentRecommendationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternetContentRecommendationServiceTest {

    @Mock private SharedOpenContentPool sharedContentPool;
    @Mock private LearnerInterestMapper interestMapper;
    @Mock private LearnerGoalMapper goalMapper;
    @Mock private SchoolStudentMapper schoolStudentMapper;
    @Mock private SchoolDataSyncService schoolDataSyncService;
    @Mock private UserService userService;
    @Mock private RecommendationService recommendationService;

    private InternetContentRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InternetContentRecommendationServiceImpl(
                sharedContentPool, interestMapper, goalMapper, schoolStudentMapper,
                schoolDataSyncService, userService, recommendationService);
        when(goalMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void ranksSharedCandidatesLocallyFromConfirmedInterest() {
        LearnerInterest interest = new LearnerInterest();
        interest.setName("人工智能");
        when(interestMapper.selectList(any())).thenReturn(List.of(interest));
        User user = new User();
        user.setId(7L);
        user.setUsername("student01");
        when(userService.getUserById(7L)).thenReturn(user);
        when(schoolDataSyncService.getStudentSchedule("student01", null, null))
                .thenReturn(Collections.emptyList());
        when(sharedContentPool.currentCandidates()).thenReturn(List.of(
                candidate("Artificial intelligence", "artificial intelligence", "ai"),
                candidate("History of film", "culture", "film")));
        when(recommendationService.recommend(7L, 2)).thenReturn(Collections.emptyList());

        List<InternetContentRecommendationDto> result = service.recommendForUser(7L, 4);

        assertFalse(result.isEmpty());
        assertEquals("Artificial intelligence", result.get(0).getTitle());
        assertTrue(result.get(0).getReason().contains("人工智能"));
        assertTrue(result.get(0).isExternal());
    }

    @Test
    void fallsBackToExistingPlatformRecommendationBeforePoolIsReady() {
        when(interestMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userService.getUserById(7L)).thenReturn(null);
        when(sharedContentPool.currentCandidates()).thenReturn(Collections.emptyList());
        Text text = new Text();
        text.setId(42L);
        text.setTitle("A useful article");
        text.setContent("Useful English content");
        when(recommendationService.recommend(7L, 3)).thenReturn(List.of(text));

        List<InternetContentRecommendationDto> result = service.recommendForUser(7L, 3);

        assertEquals(1, result.size());
        assertEquals("/article/42", result.get(0).getUrl());
        assertFalse(result.get(0).isExternal());
    }

    @Test
    void reservesAPlatformReadingSlotWhenExternalPoolIsFull() {
        when(interestMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userService.getUserById(7L)).thenReturn(null);
        when(sharedContentPool.currentCandidates()).thenReturn(List.of(
                candidate("Artificial intelligence", "artificial intelligence", "ai"),
                candidate("History of film", "culture", "film"),
                candidate("Environmental science", "environmental science", "environment")));
        Text text = new Text();
        text.setId(55L);
        text.setTitle("A platform article to study");
        text.setContent("A full English article with level versions and questions.");
        when(recommendationService.recommend(7L, 1)).thenReturn(List.of(text));

        List<InternetContentRecommendationDto> result = service.recommendForUser(7L, 3);

        assertEquals(3, result.size());
        assertEquals(1, result.stream().filter(item -> !item.isExternal()).count());
    }

    @Test
    void doesNotUseGenericScienceTokenForUnrelatedCourseRecommendation() {
        when(interestMapper.selectList(any())).thenReturn(Collections.emptyList());
        User user = new User();
        user.setId(7L);
        user.setUsername("student01");
        when(userService.getUserById(7L)).thenReturn(user);

        StudentSchedule physicalEducation = new StudentSchedule();
        physicalEducation.setCourseName("大学体育2");
        physicalEducation.setDayOfWeek(java.time.LocalDate.now().getDayOfWeek().getValue());
        when(schoolDataSyncService.getStudentSchedule("student01", null, null))
                .thenReturn(List.of(physicalEducation));
        when(sharedContentPool.currentCandidates()).thenReturn(List.of(
                candidate("Environmental science", "environmental science", "environment")));

        List<InternetContentRecommendationDto> result = service.recommendForUser(7L, 1);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getReason().contains("大学体育2"));
        assertEquals("为你保留的探索性英语内容", result.get(0).getReason());
    }

    private SharedOpenContentPool.ContentCandidate candidate(String title, String topic, String id) {
        return new SharedOpenContentPool.ContentCandidate(
                title, "An English summary about " + topic,
                "https://en.wikipedia.org/wiki/" + id,
                "Wikipedia", "ENCYCLOPEDIA", topic, null);
    }
}
