package com.english.service;

import com.english.dto.learner.ArticleFeedCandidate;
import com.english.dto.learner.ArticleFeedEventRequest;
import com.english.dto.learner.ArticleFeedPageDto;
import com.english.entity.LearnerInterest;
import com.english.entity.RecommendationEvent;
import com.english.entity.SchoolStudent;
import com.english.entity.StudentSchedule;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.ArticleFeedMapper;
import com.english.mapper.ArticleInsightMessageMapper;
import com.english.mapper.LearnerGoalMapper;
import com.english.mapper.LearnerInterestMapper;
import com.english.mapper.RecommendationEventMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.TextLearningRecordMapper;
import com.english.mapper.UserTextReadMapper;
import com.english.service.impl.ArticleFeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleFeedServiceTest {
    private final ArticleFeedMapper feedMapper = mock(ArticleFeedMapper.class);
    private final LearnerInterestMapper interestMapper = mock(LearnerInterestMapper.class);
    private final LearnerGoalMapper goalMapper = mock(LearnerGoalMapper.class);
    private final SchoolStudentMapper studentMapper = mock(SchoolStudentMapper.class);
    private final SchoolDataSyncService schoolData = mock(SchoolDataSyncService.class);
    private final UserService users = mock(UserService.class);
    private final UserTextReadMapper reads = mock(UserTextReadMapper.class);
    private final TextLearningRecordMapper learning = mock(TextLearningRecordMapper.class);
    private final ArticleInsightMessageMapper insights = mock(ArticleInsightMessageMapper.class);
    private final RecommendationEventMapper events = mock(RecommendationEventMapper.class);
    private ArticleFeedService service;

    @BeforeEach
    void setUp() {
        service = new ArticleFeedService(feedMapper, interestMapper, goalMapper, studentMapper,
                schoolData, users, reads, learning, insights, events);
    }

    @Test
    void coldStartUsesExplicitInterestAndKeepsFullBodiesOutOfResponse() {
        LearnerInterest interest = new LearnerInterest();
        interest.setName("人工智能");
        when(interestMapper.selectList(any())).thenReturn(List.of(interest));
        when(feedMapper.selectFeatured(123L, 80)).thenReturn(List.of(
                candidate(2L, "A history of film", "culture", 1)));
        when(feedMapper.selectRecent(123L, 220)).thenReturn(List.of(
                candidate(1L, "Artificial intelligence for farming", "technology", 0)));

        ArticleFeedPageDto page = service.getPage(7L, "FOR_YOU", null, 1, 6, 123L, List.of());

        assertEquals(1L, page.getItems().get(0).getId());
        assertTrue(page.getItems().get(0).getReason().contains("人工智能"));
        assertEquals("short preview", page.getItems().get(0).getExcerpt());
        assertFalse(page.isOnboardingSuggested());
    }

    @Test
    void excludesPreviouslyDismissedContentFromPersonalizedFeed() {
        when(feedMapper.selectFeatured(123L, 80)).thenReturn(List.of(
                candidate(1L, "First", "culture", 1),
                candidate(2L, "Second", "technology", 1)));
        when(events.selectRecentFeedDismissals(7L)).thenReturn(List.of(1L));

        ArticleFeedPageDto page = service.getPage(7L, "FOR_YOU", null, 1, 6, 123L, List.of());

        assertEquals(List.of(2L), page.getItems().stream().map(item -> item.getId()).toList());
    }

    @Test
    void usesMajorAndOnlyCurrentTermCoursesAsSignals() {
        User user = new User();
        user.setUsername("20260001");
        SchoolStudent student = new SchoolStudent();
        student.setMajorName("计算机科学");
        when(users.getUserById(7L)).thenReturn(user);
        when(studentMapper.selectByStudentId("20260001")).thenReturn(student);
        LocalDate today = LocalDate.now();
        int startYear = today.getMonthValue() >= 8 ? today.getYear() : today.getYear() - 1;
        int semester = today.getMonthValue() >= 8 || today.getMonthValue() == 1 ? 1 : 2;
        StudentSchedule current = new StudentSchedule();
        current.setCourseName("环境科学");
        current.setAcademicYear(startYear + "-" + (startYear + 1));
        current.setSemester(semester);
        current.setDayOfWeek(today.getDayOfWeek().getValue());
        StudentSchedule historical = new StudentSchedule();
        historical.setCourseName("心理学");
        historical.setAcademicYear((startYear - 1) + "-" + startYear);
        historical.setSemester(semester);
        historical.setDayOfWeek(today.getDayOfWeek().getValue());
        when(schoolData.getStudentSchedule("20260001", null, null))
                .thenReturn(List.of(current, historical));
        when(feedMapper.selectFeatured(123L, 80)).thenReturn(List.of(
                candidate(1L, "Psychology in daily life", "culture", 1)));
        when(feedMapper.selectRecent(123L, 220)).thenReturn(List.of(
                candidate(2L, "Computer science and coding", "technology", 0),
                candidate(3L, "Environmental science news", "technology", 0)));

        ArticleFeedPageDto page = service.getPage(7L, "FOR_YOU", null, 1, 6, 123L, List.of());

        assertTrue(page.getItems().stream().anyMatch(item -> item.getReason().contains("计算机科学")));
        assertTrue(page.getItems().stream().anyMatch(item -> item.getReason().contains("环境科学")));
        assertFalse(page.getItems().stream().anyMatch(item -> item.getReason().contains("心理学")));
    }

    @Test
    void exploresServerSideCategoryWithRealPagination() {
        when(feedMapper.selectExplore(eq("technology"), eq(123L), eq(0), eq(3)))
                .thenReturn(List.of(candidate(1L, "One", "technology", 0),
                        candidate(2L, "Two", "technology", 0),
                        candidate(3L, "Three", "technology", 0)));

        ArticleFeedPageDto page = service.getPage(7L, "EXPLORE", "technology", 1, 2, 123L, null);

        assertEquals(2, page.getItems().size());
        assertTrue(page.isHasMore());
        assertEquals(2, page.getNextPage());
        assertEquals("EXPLORE", page.getItems().get(0).getSlot());
        verify(feedMapper).selectExplore("technology", 123L, 0, 3);
    }

    @Test
    void eventRequiresEligibleArticleAndWhitelistedFields() {
        ArticleFeedEventRequest request = new ArticleFeedEventRequest();
        request.setTextId(8L);
        request.setEventType("IMPRESSION");
        request.setSource("FEED_FOR_YOU");
        request.setPosition(2);
        assertThrows(BusinessException.class, () -> service.recordEvent(7L, request));
        verify(events, never()).insert(any());

        when(feedMapper.selectEligibleById(8L)).thenReturn(candidate(8L, "Visible", "culture", 1));
        service.recordEvent(7L, request);
        ArgumentCaptor<RecommendationEvent> captor = ArgumentCaptor.forClass(RecommendationEvent.class);
        verify(events).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("IMPRESSION", captor.getValue().getEventType());

        request.setSource("UNTRUSTED");
        assertThrows(BusinessException.class, () -> service.recordEvent(7L, request));
    }

    private ArticleFeedCandidate candidate(Long id, String title, String category, int featured) {
        ArticleFeedCandidate item = new ArticleFeedCandidate();
        item.setId(id);
        item.setTitle(title);
        item.setExcerpt("short preview");
        item.setCategory(category);
        item.setContentLength(1600);
        item.setIsFeatured(featured);
        item.setFeaturedPriority(featured);
        return item;
    }
}
