package com.english.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.english.entity.UnitWord;

import java.util.List;
import java.util.Map;

public interface UnitWordService extends IService<UnitWord> {

    /**
     * 检查单词是否已在单元中
     */
    boolean exists(Long unitId, Long wordId);

    /**
     * 将单词添加到单元
     */
    void addToUnit(Long unitId, Long wordId);

    /**
     * 从单元移除单词
     */
    void removeFromUnit(Long unitId, Long wordId);

    /**
     * 获取单元下的所有单词（带详细信息）
     */
    List<Map<String, Object>> getWordsWithDetailsByUnitId(Long unitId);

    /**
     * 获取单词所在的单元列表
     */
    List<Map<String, Object>> getUnitsWithDetailsByWordId(Long wordId);

    /**
     * 批量添加单词到单元
     */
    void batchAddToUnit(Long unitId, List<Long> wordIds);

    /**
     * 批量从单元移除单词
     */
    void batchRemoveFromUnit(Long unitId, List<Long> wordIds);
}