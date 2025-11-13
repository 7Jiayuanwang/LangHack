package com.english.service;

import com.english.entity.AnswerRecord;

import java.util.List;
import java.util.Map;

public interface AnswerRecordService {
    void saveAnswerRecord(AnswerRecord record);
    List<AnswerRecord> getStudentAnswerRecords(Long studentId);
    Map<String, Object> getStudentAnswerStats(Long studentId);
}
