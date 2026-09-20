package com.english.service;

import com.english.dto.teacher.TeacherClassLearningOverviewDto;
import com.english.dto.teacher.TeacherClassStudentLearningRow;
import com.english.dto.teacher.TeacherClassWeeklyLearningRow;
import com.english.entity.Class;
import com.english.exception.BusinessException;
import com.english.mapper.ClassMapper;
import com.english.mapper.TeacherClassLearningMapper;
import com.english.service.impl.TeacherClassLearningServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherClassLearningServiceTest {
    @Mock private ClassMapper classMapper;
    @Mock private TeacherClassLearningMapper mapper;
    @InjectMocks private TeacherClassLearningServiceImpl service;

    @Test
    void rejectsOtherTeachersClassBeforeReadingStudentData() {
        when(classMapper.selectById(8L)).thenReturn(aClass(8L, 99L));

        assertThrows(BusinessException.class, () -> service.getOverview(7L, 8L));

        verify(mapper, never()).selectStudentLearning(any(), any(), any());
        verify(mapper, never()).selectWeeklyReading(any(), any(), any());
        verify(mapper, never()).selectWeeklyQuiz(any(), any(), any());
    }

    @Test
    void summarizesRecordedReadingAndQuestionWeightedAccuracyWithoutInventingOfflineActivity() {
        when(classMapper.selectById(8L)).thenReturn(aClass(8L, 7L));
        LocalDateTime now = LocalDateTime.now();
        TeacherClassStudentLearningRow first = student(1L, "Alice", 2, 1, 1, 8, 10, now);
        TeacherClassStudentLearningRow second = student(2L, "Bob", 0, 0, 2, 1, 4, now.minusDays(2));
        TeacherClassStudentLearningRow third = student(3L, "Carol", 0, 0, 0, 0, 0, null);
        when(mapper.selectStudentLearning(eq(8L), any(), any())).thenReturn(List.of(first, second, third));

        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        TeacherClassWeeklyLearningRow reading = new TeacherClassWeeklyLearningRow();
        reading.setWeekStart(weekStart);
        reading.setReadingStudents(1);
        reading.setReadingSessions(2);
        when(mapper.selectWeeklyReading(eq(8L), any(), any())).thenReturn(List.of(reading));
        TeacherClassWeeklyLearningRow quiz = new TeacherClassWeeklyLearningRow();
        quiz.setWeekStart(weekStart);
        quiz.setQuizStudents(2);
        quiz.setPracticeAttempts(3);
        quiz.setCorrectAnswers(9);
        quiz.setAnsweredQuestions(14);
        when(mapper.selectWeeklyQuiz(eq(8L), any(), any())).thenReturn(List.of(quiz));

        TeacherClassLearningOverviewDto result = service.getOverview(7L, 8L);

        assertEquals(3, result.getTotalStudents());
        assertEquals(2, result.getActiveStudents());
        assertEquals(1, result.getNoRecordStudents());
        assertEquals(1, result.getReadingStudents());
        assertEquals(2, result.getQuizStudents());
        assertEquals(3, result.getPracticeAttempts());
        assertEquals(64, result.getCorrectRate()); // 9/14 questions, not an average of attempts
        assertEquals(4, result.getWeeklyActivity().size());
        assertEquals(2, result.getWeeklyActivity().get(3).getQuizStudents());
        assertEquals(64, result.getWeeklyActivity().get(3).getCorrectRate());
        assertEquals(List.of(1, 1, 0, 1), result.getAccuracyDistribution().stream()
                .map(TeacherClassLearningOverviewDto.AccuracyBand::getStudentCount).toList());
        assertEquals(80, result.getStudentProgress().get(0).getCorrectRate());
        assertEquals(25, result.getStudentProgress().get(1).getCorrectRate());
        assertNull(result.getStudentProgress().get(2).getCorrectRate());
        assertEquals(now, result.getStudentProgress().get(0).getLastActivityAt());
    }

    @Test
    void returnsHonestEmptyStateWhenClassHasNoMembers() {
        when(classMapper.selectById(8L)).thenReturn(aClass(8L, 7L));
        when(mapper.selectStudentLearning(eq(8L), any(), any())).thenReturn(List.of());

        TeacherClassLearningOverviewDto result = service.getOverview(7L, 8L);

        assertEquals(0, result.getTotalStudents());
        assertNull(result.getCorrectRate());
        assertTrue(result.getStudentProgress().isEmpty());
        assertEquals(4, result.getWeeklyActivity().size());
    }

    private Class aClass(Long id, Long teacherId) {
        Class result = new Class();
        result.setId(id);
        result.setTeacherId(teacherId);
        result.setName("Academic English");
        return result;
    }

    private TeacherClassStudentLearningRow student(Long id, String name, long readings, long texts,
                                                    long attempts, long correct, long questions,
                                                    LocalDateTime lastActivity) {
        TeacherClassStudentLearningRow row = new TeacherClassStudentLearningRow();
        row.setStudentId(id);
        row.setUsername("student" + id);
        row.setDisplayName(name);
        row.setReadingSessions(readings);
        row.setTextsRead(texts);
        row.setPracticeAttempts(attempts);
        row.setCorrectAnswers(correct);
        row.setAnsweredQuestions(questions);
        row.setLastReadingAt(readings > 0 ? lastActivity : null);
        row.setLastPracticeAt(attempts > 0 ? lastActivity : null);
        return row;
    }
}
