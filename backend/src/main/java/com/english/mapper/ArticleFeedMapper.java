package com.english.mapper;

import com.english.dto.learner.ArticleFeedCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArticleFeedMapper {
    String COLUMNS = "SELECT id, title, "
            + "LEFT(COALESCE(NULLIF(content, ''), markdown_content), 320) AS excerpt, "
            + "category, tags, difficulty, cover_url AS coverUrl, "
            + "is_featured AS isFeatured, featured_priority AS featuredPriority, "
            + "created_time AS createdAt, "
            + "CHAR_LENGTH(COALESCE(NULLIF(content, ''), markdown_content)) AS contentLength ";
    String VISIBLE = " FROM texts WHERE deleted = 0 "
            + "AND (uploaded_by IS NULL OR (is_public = 1 AND status = 'APPROVED')) ";

    @Select(COLUMNS + VISIBLE + "AND is_featured = 1 "
            + "AND (created_time IS NULL OR created_time <= FROM_UNIXTIME(#{seed} / 1000)) "
            + "ORDER BY featured_priority DESC, created_time DESC, id DESC LIMIT #{limit}")
    List<ArticleFeedCandidate> selectFeatured(@Param("seed") long seed, @Param("limit") int limit);

    @Select(COLUMNS + VISIBLE
            + "AND (created_time IS NULL OR created_time <= FROM_UNIXTIME(#{seed} / 1000)) "
            + "ORDER BY created_time DESC, id DESC LIMIT #{limit}")
    List<ArticleFeedCandidate> selectRecent(@Param("seed") long seed, @Param("limit") int limit);

    @Select(COLUMNS + VISIBLE
            + "AND (#{category} IS NULL OR category = #{category}) "
            + "AND (created_time IS NULL OR created_time <= FROM_UNIXTIME(#{seed} / 1000)) "
            + "ORDER BY created_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ArticleFeedCandidate> selectExplore(@Param("category") String category,
                                             @Param("seed") long seed,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    @Select(COLUMNS + VISIBLE + "AND id = #{id} LIMIT 1")
    ArticleFeedCandidate selectEligibleById(@Param("id") Long id);
}
