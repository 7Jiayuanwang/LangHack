package com.english.service;

import java.util.List;
import java.util.Map;

/**
 * 学习记录服务接口
 */
public interface LearningRecordsService {

    /**
     * 获取今日学习时长（分钟）
     */
    int getTodayStudyTime(Long studentId);

    /**
     * 获取单元学习统计（包含进度）
     */
    List<Map<String, Object>> getUnitsWithLearningStats(Long studentId, Long courseId);

    /**
     * 获取单元内容（包含学习状态）
     */
    Map<String, Object> getUnitContentWithLearningStates(Long studentId, Long unitId);

    /**
     * 获取课文详情（包含单词标注）
     */
    Map<String, Object> getTextDetailWithAnnotation(Long studentId, Long textId);

    /**
     * 记录课文查看
     */
    void recordTextView(Long studentId, Long textId);

    /**
     * 记录单词查看
     */
    void recordWordView(Long studentId, Long wordId);

    /**
     * 记录添加生词
     */
    void recordVocabularyAdd(Long studentId, Long wordId);

    /**
     * 记录单词复习
     */
    void recordWordReview(Long studentId, Long wordId, boolean isCorrect);

    /**
     * 获取今日学习统计
     */
    Map<String, Object> getTodayLearningStats(Long studentId);

    /**
     * 获取学习统计概览
     */
    Map<String, Object> getLearningOverview(Long studentId, Map<String, Object> params);

    /**
     * 获取学习记录（分页）
     */
    Map<String, Object> getLearningRecordsWithPagination(Map<String, Object> params);

    /**
     * 获取课程学习统计
     */
    Map<String, Object> getCourseLearningStats(Long studentId, Long courseId);

    /**
     * 获取所有课程学习统计
     */
    Map<String, Object> getAllCoursesLearningStats(Long studentId);

    /**
     * 获取学习时长统计
     */
    Map<String, Object> getStudyTimeStats(Long studentId, Map<String, Object> params);

    /**
     * 生成进度报告
     */
    Map<String, Object> generateProgressReport(Long studentId, String type);

    /**
     * 获取成就统计
     */
    Map<String, Object> getAchievementStats(Long studentId);

    /**
     * 获取学习排名
     */
    Map<String, Object> getLearningRanking(Long studentId, String type, Long targetId);

    /**
     * 导出学习数据
     */
    Map<String, Object> exportLearningData(Long studentId, Map<String, Object> params);

    /**
     * 获取任务学习记录
     */
    Map<String, Object> getAssignmentLearningRecords(Long assignmentId, Long studentId, int page, int size);
}