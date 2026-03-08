package com.english.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.english.entity.ScoreRecord;

import java.util.List;
import java.util.Map;

public interface ScoreRecordService extends IService<ScoreRecord> {

    /**
     * 记录分数
     */
    void recordScore(ScoreRecord scoreRecord);

    /**
     * 获取学生的分数记录列表
     */
    List<Map<String, Object>> getStudentScores(Long studentId, String scoreType, Long courseId, Long unitId, Long assignmentId);

    /**
     * 获取学生的平均分数
     */
    Map<String, Object> getStudentAverageScore(Long studentId, String scoreType, Long courseId);

    /**
     * 获取课程/单元/任务的分数统计
     */
    Map<String, Object> getScoreStatistics(Long courseId, Long unitId, Long assignmentId);

    /**
     * 根据任务ID获取学生的任务分数
     */
    ScoreRecord getAssignmentScore(Long assignmentId, Long studentId);

    /**
     * 更新任务分数（基于任务完成情况自动计算）
     */
    void updateAssignmentScore(Long assignmentId, Long studentId);
    
    /**
     * 导出班级成绩数据
     */
    List<Map<String, Object>> exportClassScores(Long classId);
    
    /**
     * 生成班级成绩Excel文件
     */
    byte[] generateClassScoresExcel(List<Map<String, Object>> data, Long classId);
}

