package com.english.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.english.dto.LearningStatsDTO;
import com.english.entity.AnswerRecord;

import java.util.List;
import java.util.Map;

public interface AnswerRecordService extends IService<AnswerRecord> {
    void saveAnswerRecord(AnswerRecord record);
    List<AnswerRecord> getStudentAnswerRecords(Long studentId);
    Map<String, Object> getStudentAnswerStats(Long studentId);
    List<LearningStatsDTO.ActiveStudentDTO> getActiveStudentRanking(int limit);
}
