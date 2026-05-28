package com.english.dto.learner;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LearnerEvidenceDto {
    private LocalDateTime profileGeneratedAt;
    private String dataCompleteness;
    private int dataPointCount;
}
