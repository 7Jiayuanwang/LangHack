package com.english.service;

import com.english.dto.WordExplanationDTO;

/**
 * 单词 AI 详细介绍服务
 */
public interface WordAiExplanationService {

    /**
     * 获取单词的详细介绍（优先从缓存读取，缓存未命中则调用 AI 生成）
     * @param word 单词
     * @return 单词详细介绍
     */
    WordExplanationDTO getWordExplanation(String word) throws Exception;

    /**
     * 强制重新生成单词介绍
     * @param word 单词
     * @return 单词详细介绍
     */
    WordExplanationDTO regenerateWordExplanation(String word) throws Exception;
}
