package com.english.service;

import java.util.List;
import java.util.Map;

/**
 * 生词本服务接口
 */
public interface VocabularyBooksService {

    /**
     * 统计学生的生词数量
     */
    int countByStudentId(Long studentId);

    /**
     * 获取学生最近添加的单词
     */
    List<Map<String, Object>> getRecentWords(Long studentId, int limit);

    /**
     * 检查学生是否已有指定单词
     */
    boolean existsByStudentAndWord(Long studentId, Long wordId);

    /**
     * 添加单词到生词本
     */
    void addToVocabulary(Long studentId, Long wordId);

    /**
     * 添加单词到生词本并设置掌握程度
     */
    void addToVocabularyWithLevel(Long studentId, Long wordId, String masteryLevel);

    /**
     * 添加单词到生词本并设置掌握程度及来源类型
     */
    void addToVocabularyWithLevel(Long studentId, Long wordId, String masteryLevel, String sourceType);

    /**
     * 当生词本中已存在该词时，如果其来源不是 ARTICLE，则升级为 ARTICLE
     * 用于"用户在文章页再次加同一个词"场景，把旧的 MANUAL 记录升级，让它能被文章生词列表统计到
     * @return true 表示进行了升级，false 表示原本就是 ARTICLE 或记录不存在
     */
    boolean upgradeSourceTypeToArticle(Long studentId, Long wordId);

    /**
     * 获取生词本列表（分页）
     */
    Map<String, Object> getVocabularyWithPagination(Map<String, Object> params);

    /**
     * 更新单词掌握程度
     */
    void updateMasteryLevel(Long studentId, Long wordId, String masteryLevel, Map<String, Object> sm2Params);

    /**
     * 获取单词详情（包含学习状态）
     */
    Map<String, Object> getWordDetailWithLearningState(Long studentId, Long wordId);

    /**
     * 创建学习会话
     */
    Map<String, Object> createStudySession(Long studentId, Long unitId);

    /**
     * 处理学习结果
     */
    void processStudyResults(Long studentId, Map<String, Object> results);

    /**
     * 获取单词学习统计
     */
    Map<String, Object> getWordStudyStats(Long studentId, String period, Long courseId, Long unitId);

    /**
     * 获取单词学习统计（简化版本）
     */
    Map<String, Object> getWordStudyStats(Long studentId, String period);

    /**
     * 批量删除生词
     */
    void batchDeleteVocabulary(Long studentId, List<Long> wordIds);

    /**
     * 批量更新掌握程度
     */
    void batchUpdateMasteryLevel(Long studentId, List<Long> wordIds, String masteryLevel);

    /**
     * 从生词本移除单词
     */
    void removeFromVocabulary(Long studentId, Long wordId);

    /**
     * 获取生词本统计信息
     */
    Map<String, Object> getVocabularyStats(Long studentId);
}