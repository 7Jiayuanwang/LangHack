package com.english.service;

import com.english.entity.TagRecalibrationLog;

import java.util.List;
import java.util.Map;

public interface TagRecalibrationService {

    List<Map<String, Object>> getTagEffectiveness(int lastNDays);

    List<Map<String, Object>> getRecalibrationProposals();

    TagRecalibrationLog recalibrate(String tagCode, String newThreshold, String reason, String operator);
}
