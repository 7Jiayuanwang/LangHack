package com.english.service;

import com.english.dto.Result;
import java.util.Map;

public interface TaskPlanService {

    Result<?> createPlan(Map<String, Object> params, Long creatorId);

    Result<?> updatePlan(Long planId, Map<String, Object> params);

    Result<?> publishPlan(Long planId);

    Result<?> closePlan(Long planId);

    Result<?> listPlans(Map<String, Object> params);

    Result<?> getPlanDetail(Long planId);

    Result<?> getPlanStats(Long planId);

    byte[] exportPlanScore(Long planId);

    Result<?> listTeacherPlans(Long teacherId);

    Result<?> updateClassOverride(Long planId, Long classId, Map<String, Object> params, Long teacherId);

    Result<?> optOutClass(Long planId, Long classId, Long teacherId);

    Result<?> optInClass(Long planId, Long classId, Long teacherId);

    Result<?> getClassProgress(Long planId, Long classId);

    byte[] exportClassScore(Long planId, Long classId);

    Result<?> getStudentPlans(Long studentId);

    void refreshPeriodRecords(Long planId);
}
