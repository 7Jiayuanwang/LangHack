package com.english.service;

import com.english.entity.LearnerProfileSnapshot;

import java.time.LocalDate;
import java.util.List;

public interface LearnerProfileSnapshotService {

    LearnerProfileSnapshot takeSnapshot(Long userId);

    void takeDailySnapshotsForAllActiveUsers();

    List<LearnerProfileSnapshot> getSnapshotHistory(Long userId, int lastNDays);

    LearnerProfileSnapshot getLatestSnapshot(Long userId);
}
