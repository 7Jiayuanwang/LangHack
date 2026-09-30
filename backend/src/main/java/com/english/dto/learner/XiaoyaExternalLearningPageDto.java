package com.english.dto.learner;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class XiaoyaExternalLearningPageDto {
    private int page;
    private int size;
    private long total;
    private List<XiaoyaGrowthDto.ExternalLearning> items = new ArrayList<>();
}
