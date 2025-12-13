package com.english.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.english.dto.PracticeSessionDTO;
import com.english.entity.PracticeSession;

import java.util.List;
import java.util.Map;

public interface PracticeSessionService extends IService<PracticeSession> {

    /**
     * Start a new practice session
     */
    PracticeSessionDTO.SessionResponse startSession(PracticeSessionDTO.StartSessionRequest request);

    /**
     * Complete a practice session
     */
    void completeSession(Long sessionId, PracticeSessionDTO.CompleteSessionRequest request);

    /**
     * Get practice history for a student
     */
    Map<String, Object> getPracticeHistory(Long studentId, Integer page, Integer size);

    /**
     * Get practice statistics for a student
     */
    Map<String, Object> getPracticeStats(Long studentId);
}
