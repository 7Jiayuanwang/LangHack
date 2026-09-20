package com.english.service;

import com.english.entity.LearnerAgentProfile;
import com.english.entity.LevelExamScore;
import com.english.entity.User;
import com.english.mapper.LearnerAgentProfileMapper;
import com.english.mapper.LevelExamScoreMapper;
import com.english.service.impl.ArticleInsightLevelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleInsightLevelServiceTest {
    @Mock private UserService users;
    @Mock private LevelExamScoreMapper scores;
    @Mock private LearnerAgentProfileMapper profiles;
    private ArticleInsightLevelService service;

    @BeforeEach
    void setUp() {
        service = new ArticleInsightLevelService(users, scores, profiles);
        User user = new User();
        user.setId(7L);
        user.setUsername("20230001");
        when(users.getUserById(7L)).thenReturn(user);
    }

    @Test
    void usesExamNameBecauseImportedExamTypeCollapsesToCetAndIgnoresWrongPassedFlag() {
        when(scores.selectList(any())).thenReturn(List.of(
                score("CET", "大学英语4级（CET4）", "420", true),
                score("CET", "大学英语6级（CET6）", "424", true)));

        assertEquals("CET6", service.resolve(7L).level());
        verify(profiles, never()).selectOne(any());
    }

    @Test
    void movesFromFourToSixAt425() {
        when(scores.selectList(any())).thenReturn(List.of(score("CET", "大学英语4级（CET4）", "425", false)));
        assertEquals("CET6", service.resolve(7L).level());
    }

    @Test
    void movesFromSixToPostgraduateAt425() {
        when(scores.selectList(any())).thenReturn(List.of(score("CET", "大学英语6级（CET6）", "430", false)));
        assertEquals("POSTGRADUATE", service.resolve(7L).level());
    }

    @Test
    void fallsBackToSelfRatedLevelWhenNoExamScoreExists() {
        when(scores.selectList(any())).thenReturn(List.of());
        LearnerAgentProfile profile = new LearnerAgentProfile();
        profile.setCurrentLevel("UPPER_INTERMEDIATE");
        when(profiles.selectOne(any())).thenReturn(profile);
        assertEquals("CET6", service.resolve(7L).level());
    }

    private LevelExamScore score(String type, String name, String value, boolean passed) {
        LevelExamScore score = new LevelExamScore();
        score.setExamType(type);
        score.setExamName(name);
        score.setScore(new BigDecimal(value));
        score.setIsPassed(passed);
        return score;
    }
}
