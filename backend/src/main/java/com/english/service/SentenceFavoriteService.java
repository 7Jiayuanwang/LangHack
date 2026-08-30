package com.english.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.english.entity.SentenceFavorite;

import java.util.Map;

public interface SentenceFavoriteService {
    Map<String, Object> toggle(Long userId, Long textId, String sentence, boolean staffAccess);

    IPage<SentenceFavorite> list(Long userId, int page, int size);

    void delete(Long id, Long userId);
}
