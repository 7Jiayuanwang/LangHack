package com.english.service;

import com.english.dto.SchoolDataSyncFilterRequest;
import com.english.service.impl.SchoolDataFetchResult;

import java.util.List;
import java.util.Map;

/**
 * 学校数据拉取服务，仅负责从学校 API 获取原始数据。
 */
public interface SchoolDataFetchService {

    List<Map<String, Object>> fetchRawData(String dataSource, SchoolDataSyncFilterRequest request);

    SchoolDataFetchResult fetchRawDataWithSummary(String dataSource, SchoolDataSyncFilterRequest request);

    List<Map<String, Object>> fetchStudentScheduleRaw(String studentId, String academicYear, Integer semester);
}
