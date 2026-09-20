package com.english.service;

import com.english.dto.teacher.TeacherClassLearningOverviewDto;

public interface TeacherClassLearningService {
    TeacherClassLearningOverviewDto getOverview(Long teacherId, Long classId);
}
