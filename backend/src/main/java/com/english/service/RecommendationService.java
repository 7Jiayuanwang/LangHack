package com.english.service;

import com.english.entity.Text;

import java.util.Collection;
import java.util.List;

/**
 * 个性化推荐服务
 * <p>
 * 设计见 docs/推荐算法实现逻辑与过程.md，基于协同过滤：
 *   - 冷启动（已读 < 阈值）：featured + 最新 文章兜底
 *   - 常规：Item-CF 60% + User-CF 40% 加权合并，不足时 featured + 难度匹配兜底
 */
public interface RecommendationService {

    /**
     * 为用户生成个性化推荐列表。
     *
     * @param userId     目标用户 ID（非空）
     * @param count      期望返回的文章数量（外部已夹紧到合理区间）
     * @param excludeIds 额外需要排除的文章 ID（如客户端已展示过的，用于"换一批"场景）
     * @return 推荐文章列表，可能少于 count（极端情况下整个库无可推荐文章）
     */
    List<Text> recommend(Long userId, int count, Collection<Long> excludeIds);

    /**
     * 便捷重载：不排除额外 ID。
     */
    default List<Text> recommend(Long userId, int count) {
        return recommend(userId, count, null);
    }
}
