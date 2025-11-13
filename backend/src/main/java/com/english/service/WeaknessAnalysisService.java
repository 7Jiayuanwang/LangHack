package com.english.service;

import com.english.entity.WeaknessAnalysis;

import java.util.List;
import java.util.Map;

public interface WeaknessAnalysisService {
    void analyzeStudentWeakness(Long studentId);
    List<WeaknessAnalysis> getStudentWeaknessList(Long studentId);
    Map<String, Object> getWeaknessReport(Long studentId);
    List<WeaknessAnalysis> getTopWeaknesses(Long studentId, int limit);
}
