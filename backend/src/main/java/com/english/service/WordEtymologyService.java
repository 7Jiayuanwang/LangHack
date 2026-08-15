package com.english.service;

import java.util.Map;
import java.util.List;

/**
 * 单词词源/词根词缀分析服务
 * 提供前缀、后缀、词根、词性变化、联想记忆等分析
 */
public interface WordEtymologyService {

    /**
     * 分析单词的完整词源信息
     */
    Map<String, Object> analyze(String word);

    /**
     * 提取前缀
     */
    List<Map<String, String>> extractPrefixes(String word);

    /**
     * 提取后缀
     */
    List<Map<String, String>> extractSuffixes(String word);

    /**
     * 提取词根
     */
    List<Map<String, String>> extractRoots(String word);

    /**
     * 生成联想记忆
     */
    List<String> generateMnemonics(String word);

    /**
     * 获取词性变化
     */
    Map<String, String> getPartOfSpeechChanges(String word);
}
