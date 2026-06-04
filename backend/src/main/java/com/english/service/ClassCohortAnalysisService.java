package com.english.service;

import com.english.dto.learner.ClassCohortAnalysisDto;

import java.util.List;

public interface ClassCohortAnalysisService {

    ClassCohortAnalysisDto analyzeCohort(Long classId);

    List<ClassCohortAnalysisDto> compareClasses(List<Long> classIds);
}
