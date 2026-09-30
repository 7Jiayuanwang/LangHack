package com.english.dto.learner;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ArticleFeedPageDto {
    private List<ArticleFeedItemDto> items = new ArrayList<>();
    private ArticleFeedItemDto continueReading;
    private ArticleFeedItemDto review;
    private boolean onboardingSuggested;
    private boolean hasMore;
    private int nextPage;
    private long seed;
}
