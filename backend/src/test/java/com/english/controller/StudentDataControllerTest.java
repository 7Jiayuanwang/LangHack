package com.english.controller;

import com.english.dto.Result;
import com.english.entity.LevelExamScore;
import com.english.entity.User;
import com.english.service.SchoolDataSyncService;
import com.english.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StudentDataController.
 *
 * Key implementation facts:
 * - Endpoints under /api/student
 * - @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
 * - Lazy sync: getOrSyncLevelExamScores triggers CDSP pull if stale
 * - studentId resolved from Authentication → userService.getUserByUsername
 */
@ExtendWith(MockitoExtension.class)
class StudentDataControllerTest {

    @InjectMocks
    private StudentDataController controller;

    @Mock
    private SchoolDataSyncService syncService;

    @Mock
    private UserService userService;

    private Authentication auth;

    @BeforeEach
    void setUp() {
        auth = new TestingAuthenticationToken("2023122066164", null, "ROLE_STUDENT");
    }

    @Nested
    @DisplayName("getMyLevelExamScores Lazy 拉取")
    class GetMyLevelExamScoresTests {

        @Test
        @DisplayName("已登录学生获取四六级成绩成功")
        void myLevelExams_returnsDataFromLocalOrSync() {
            User user = new User();
            user.setUsername("2023122066164");
            user.setRole("STUDENT");
            when(userService.getUserByUsername("2023122066164")).thenReturn(user);

            LevelExamScore score = new LevelExamScore();
            score.setStudentId("2023122066164");
            score.setExamType("CET4");
            score.setScore(java.math.BigDecimal.valueOf(450));
            when(syncService.getOrSyncLevelExamScores(eq("2023122066164"), any(Duration.class)))
                    .thenReturn(List.of(score));

            Result<List<LevelExamScore>> result = controller.getMyLevelExamScores(auth);

            assertNotNull(result);
            assertEquals(200, result.getCode());
            assertNotNull(result.getData());
            assertEquals(1, result.getData().size());
            assertEquals("CET4", result.getData().get(0).getExamType());
            verify(syncService).getOrSyncLevelExamScores(eq("2023122066164"), any(Duration.class));
        }

        @Test
        @DisplayName("用户不存在时抛出异常")
        void myLevelExams_userNotFound_throwsException() {
            when(userService.getUserByUsername("2023122066164")).thenReturn(null);

            assertThrows(RuntimeException.class, () -> controller.getMyLevelExamScores(auth));
        }

        @Test
        @DisplayName("Lazy同步失败时降级返回本地数据")
        void myLevelExams_syncFailure_returnsLocalData() {
            User user = new User();
            user.setUsername("2023122066164");
            when(userService.getUserByUsername("2023122066164")).thenReturn(user);

            when(syncService.getOrSyncLevelExamScores(eq("2023122066164"), any(Duration.class)))
                    .thenReturn(Collections.emptyList());

            Result<List<LevelExamScore>> result = controller.getMyLevelExamScores(auth);

            assertNotNull(result);
            assertEquals(200, result.getCode());
            assertNotNull(result.getData());
            assertTrue(result.getData().isEmpty());
        }
    }

    @Nested
    @DisplayName("getMyScores 直接查询")
    class GetMyScoresTests {

        @Test
        @DisplayName("按考试类型筛选成绩")
        void myScores_withExamType_filterApplied() {
            User user = new User();
            user.setUsername("2023122066164");
            when(userService.getUserByUsername("2023122066164")).thenReturn(user);

            Result<List<LevelExamScore>> result = controller.getMyScores("CET4", auth);

            assertNotNull(result);
            assertEquals(200, result.getCode());
        }

        @Test
        @DisplayName("不传考试类型返回全部成绩")
        void myScores_withoutExamType_returnsAll() {
            User user = new User();
            user.setUsername("2023122066164");
            when(userService.getUserByUsername("2023122066164")).thenReturn(user);

            Result<List<LevelExamScore>> result = controller.getMyScores(null, auth);

            assertNotNull(result);
            assertEquals(200, result.getCode());
        }
    }
}
