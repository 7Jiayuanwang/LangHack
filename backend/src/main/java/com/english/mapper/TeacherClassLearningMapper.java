package com.english.mapper;

import com.english.dto.teacher.TeacherClassStudentLearningRow;
import com.english.dto.teacher.TeacherClassWeeklyLearningRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TeacherClassLearningMapper {
    List<TeacherClassStudentLearningRow> selectStudentLearning(
            @Param("classId") Long classId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("untilTime") LocalDateTime untilTime);

    List<TeacherClassWeeklyLearningRow> selectWeeklyReading(
            @Param("classId") Long classId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("untilTime") LocalDateTime untilTime);

    List<TeacherClassWeeklyLearningRow> selectWeeklyQuiz(
            @Param("classId") Long classId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("untilTime") LocalDateTime untilTime);
}
