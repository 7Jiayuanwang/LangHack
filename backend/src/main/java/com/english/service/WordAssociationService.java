package com.english.service;

import java.util.List;
import java.util.Map;

/**
 * 单词联想服务接口
 * 提供同义词、衍生词、常用搭配查询
 */
public interface WordAssociationService {

    /**
     * 查询单词的完整联想信息
     * 返回同义词、衍生词、常用搭配三部分
     */
    Map<String, Object> getAssociations(String word);

    /**
     * 获取同义词列表
     */
    List<Map<String, Object>> getSynonyms(String word);

    /**
     * 获取衍生词列表（过去式、过去分词、现在分词、比较级、最高级等）
     */
    List<Map<String, Object>> getDerivatives(String word);

    /**
     * 获取常用搭配列表
     */
    List<Map<String, Object>> getCollocations(String word);
}
