package com.english.service;

import com.english.entity.Text;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;
import com.english.dto.ArticleFilterDTO;

public interface TextService extends IService<Text> {
    Map<String, Object> getTextsByPage(Map<String, Object> params);
    Text getTextById(Long id);
    void createText(Text text);
    void updateText(Text text);
    void deleteText(Long id);
    List<Text> getTextsByUnit(Long unitId);

    /**
     * 根据单元ID获取课文列表（别名方法，兼容性）
     */
    default List<Text> getTextsByUnitId(Long unitId) {
        return getTextsByUnit(unitId);
    }
    List<Text> getTextsByDifficulty(String difficulty);
    List<Text> searchTexts(String keyword);
    Map<String, Object> getTextStats();

    // 新增用户课文相关方法
    List<Text> getTextsByUploader(Long uploaderId);
    List<Text> getPublicTexts(int page, int size, String difficulty, String keyword);
    Integer getMaxOrderInUnit(Long unitId);

    /**
     * 原子递增查看次数
     */
    void incrementViewCount(Long textId);

    /**
     * 原子递增点赞次数
     */
    void incrementLikeCount(Long textId);

    List<Text> getArticles(ArticleFilterDTO filter);

    com.baomidou.mybatisplus.core.metadata.IPage<Text> getArticlesPaged(ArticleFilterDTO filter);
}
