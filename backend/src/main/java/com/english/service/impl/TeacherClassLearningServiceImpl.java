package com.english.service.impl;

import com.english.dto.teacher.TeacherClassLearningOverviewDto;
import com.english.dto.teacher.TeacherClassStudentLearningRow;
import com.english.dto.teacher.TeacherClassWeeklyLearningRow;
import com.english.datasource.UseMaster;
import com.english.entity.Class;
import com.english.exception.BusinessException;
import com.english.mapper.ClassMapper;
import com.english.mapper.TeacherClassLearningMapper;
import com.english.service.TeacherClassLearningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@UseMaster
public class TeacherClassLearningServiceImpl implements TeacherClassLearningService {

    private final ClassMapper classMapper;
    private final TeacherClassLearningMapper learningMapper;

    public TeacherClassLearningServiceImpl(ClassMapper classMapper, TeacherClassLearningMapper learningMapper) {
        this.classMapper = classMapper;
        this.learningMapper = learningMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherClassLearningOverviewDto getOverview(Long teacherId, Long classId) {
        if (teacherId == null || classId == null || classId <= 0) {
            throw new BusinessException("无效的教师或班级ID");
        }
        Class classInfo = classMapper.selectById(classId);
        if (classInfo == null || !teacherId.equals(classInfo.getTeacherId())) {
            throw new BusinessException("无权查看此班级的学习数据");
        }

        LocalDate today = LocalDate.now();
        LocalDate firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(3);
        LocalDateTime fromTime = firstMonday.atStartOfDay();
        LocalDateTime untilTime = today.plusDays(1).atStartOfDay();

        List<TeacherClassStudentLearningRow> rows = learningMapper.selectStudentLearning(classId, fromTime, untilTime);
        if (rows == null) rows = List.of();

        TeacherClassLearningOverviewDto overview = new TeacherClassLearningOverviewDto();
        overview.setClassId(classId);
        overview.setClassName(classInfo.getName());
        overview.setWindowStart(firstMonday);
        overview.setWindowEnd(today);
        overview.setTotalStudents(rows.size());

        long readingSessions = 0;
        long practiceAttempts = 0;
        long correctAnswers = 0;
        long answeredQuestions = 0;
        int activeStudents = 0;
        int readingStudents = 0;
        int quizStudents = 0;
        int below60 = 0;
        int from60To79 = 0;
        int atLeast80 = 0;
        List<TeacherClassLearningOverviewDto.StudentProgress> students = new ArrayList<>();
        for (TeacherClassStudentLearningRow row : rows) {
            TeacherClassLearningOverviewDto.StudentProgress student = new TeacherClassLearningOverviewDto.StudentProgress();
            student.setStudentId(row.getStudentId());
            student.setUsername(row.getUsername());
            student.setDisplayName(row.getDisplayName());
            student.setReadingSessions(row.getReadingSessions());
            student.setTextsRead(row.getTextsRead());
            student.setPracticeAttempts(row.getPracticeAttempts());
            student.setAnsweredQuestions(row.getAnsweredQuestions());
            student.setCorrectRate(percent(row.getCorrectAnswers(), row.getAnsweredQuestions()));
            student.setLastActivityAt(latest(row.getLastReadingAt(), row.getLastPracticeAt()));
            students.add(student);

            readingSessions += row.getReadingSessions();
            practiceAttempts += row.getPracticeAttempts();
            correctAnswers += row.getCorrectAnswers();
            answeredQuestions += row.getAnsweredQuestions();
            boolean read = row.getReadingSessions() > 0;
            boolean practiced = row.getPracticeAttempts() > 0;
            if (read || practiced) activeStudents++;
            if (read) readingStudents++;
            if (practiced) quizStudents++;

            if (student.getCorrectRate() == null) {
                continue;
            }
            if (student.getCorrectRate() < 60) below60++;
            else if (student.getCorrectRate() < 80) from60To79++;
            else atLeast80++;
        }
        overview.setActiveStudents(activeStudents);
        overview.setReadingStudents(readingStudents);
        overview.setQuizStudents(quizStudents);
        overview.setNoRecordStudents(rows.size() - activeStudents);
        overview.setReadingSessions(readingSessions);
        overview.setPracticeAttempts(practiceAttempts);
        overview.setAnsweredQuestions(answeredQuestions);
        overview.setCorrectRate(percent(correctAnswers, answeredQuestions));
        overview.setStudentProgress(students);
        overview.setAccuracyDistribution(List.of(
                new TeacherClassLearningOverviewDto.AccuracyBand("NO_QUIZ", "未答题", rows.size() - below60 - from60To79 - atLeast80),
                new TeacherClassLearningOverviewDto.AccuracyBand("BELOW_60", "低于60%", below60),
                new TeacherClassLearningOverviewDto.AccuracyBand("FROM_60_TO_79", "60%–79%", from60To79),
                new TeacherClassLearningOverviewDto.AccuracyBand("AT_LEAST_80", "80%及以上", atLeast80)));
        overview.setWeeklyActivity(buildWeeklyActivity(firstMonday, classId, fromTime, untilTime));
        return overview;
    }

    private List<TeacherClassLearningOverviewDto.WeeklyActivity> buildWeeklyActivity(
            LocalDate firstMonday, Long classId, LocalDateTime fromTime, LocalDateTime untilTime) {
        Map<LocalDate, TeacherClassLearningOverviewDto.WeeklyActivity> weeks = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            LocalDate weekStart = firstMonday.plusWeeks(i);
            TeacherClassLearningOverviewDto.WeeklyActivity activity = new TeacherClassLearningOverviewDto.WeeklyActivity();
            activity.setWeekStart(weekStart);
            weeks.put(weekStart, activity);
        }
        List<TeacherClassWeeklyLearningRow> readings = learningMapper.selectWeeklyReading(classId, fromTime, untilTime);
        if (readings != null) {
            for (TeacherClassWeeklyLearningRow row : readings) {
                TeacherClassLearningOverviewDto.WeeklyActivity activity = weeks.get(row.getWeekStart());
                if (activity == null) continue;
                activity.setReadingStudents(row.getReadingStudents());
                activity.setReadingSessions(row.getReadingSessions());
            }
        }
        List<TeacherClassWeeklyLearningRow> quizzes = learningMapper.selectWeeklyQuiz(classId, fromTime, untilTime);
        if (quizzes != null) {
            for (TeacherClassWeeklyLearningRow row : quizzes) {
                TeacherClassLearningOverviewDto.WeeklyActivity activity = weeks.get(row.getWeekStart());
                if (activity == null) continue;
                activity.setQuizStudents(row.getQuizStudents());
                activity.setPracticeAttempts(row.getPracticeAttempts());
                activity.setCorrectRate(percent(row.getCorrectAnswers(), row.getAnsweredQuestions()));
            }
        }
        return new ArrayList<>(weeks.values());
    }

    private Integer percent(long correct, long total) {
        return total > 0 ? (int) Math.round(100.0 * correct / total) : null;
    }

    private LocalDateTime latest(LocalDateTime first, LocalDateTime second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }
}
