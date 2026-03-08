package com.english.service;

import com.english.entity.Underline;
import java.util.List;

public interface UnderlineService {
    void createUnderline(Long userId, Long textId, String selectedText, Integer startIndex, Integer endIndex,
            String serializedRange);

    List<Underline> getUnderlinesByTextId(Long textId);

    List<Underline> getUnderlinesByTextIdAndUserId(Long textId, Long userId);

    /**
     * 获取指定ID的划线记录
     */
    Underline getUnderlineById(Long underlineId);

    /**
     * 删除指定 ID 的划线记录，仅允许划线所有者操作
     * @return true 表示删除成功，false 表示记录不存在或无权限
     */
    boolean deleteUnderline(Long underlineId, Long userId);

    /**
     * 级联删除指定课文的所有划线记录（用于课文删除时的数据清理）
     * @param textId 课文ID
     */
    void deleteUnderlinesByTextId(Long textId);
}
