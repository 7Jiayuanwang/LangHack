package com.english.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 相似度矩阵 Shadow Table 重算 Mapper
 *
 * 仅负责 Shadow Table 策略所需的 DDL / INSERT 操作：
 *   - TRUNCATE / INSERT (重算) / RENAME TABLE / DROP + CREATE (清理 tmp)
 *
 * 推荐查询已下沉到 RecommendationServiceImpl，不再在此处。
 *
 * 注意：多语句 DDL (DROP + CREATE) 需要 DB 连接配置 allowMultiQueries=true。
 */
@Mapper
public interface SimilarityMatrixMapper {

    // ══════════════════════════════════════════════════════════════
    // 文章相似度矩阵 — Shadow Table 操作
    // ══════════════════════════════════════════════════════════════

    @Update("TRUNCATE TABLE text_similarity_matrix_tmp")
    int truncateTmpText();

    /**
     * 重算文章相似度矩阵并插入 tmp 表
     *
     * 余弦相似度：cos(t1, t2) = Σ(s1_i × s2_i) / (√Σs1_i² × √Σs2_i²)
     * 只对同一用户的评分做内积 (s1.user_id = s2.user_id)，且只存 t1 < t2 的一半对（对称）。
     */
    @Insert("INSERT INTO text_similarity_matrix_tmp (text_id, similar_text_id, similarity, created_at, updated_at) " +
            "WITH " +
            "  user_text_scores AS ( " +
            "    SELECT DISTINCT v.user_id, v.text_id, v.interaction_score AS score " +
            "    FROM v_user_text_interaction_scores v " +
            "    INNER JOIN users u ON u.id = v.user_id AND u.role IN ('STUDENT', 'USER') " +
            "    WHERE v.interaction_score > 0 " +
            "  ), " +
            "  vector_norms AS ( " +
            "    SELECT user_id, text_id, score, " +
            "      SQRT(SUM(POW(score, 2)) OVER (PARTITION BY text_id)) AS text_norm " +
            "    FROM user_text_scores " +
            "  ), " +
            "  pairs AS ( " +
            "    SELECT s1.text_id, s2.text_id AS similar_text_id, s1.score * s2.score AS dot_product " +
            "    FROM vector_norms s1 " +
            "    INNER JOIN vector_norms s2 " +
            "      ON s1.user_id = s2.user_id AND s1.text_id < s2.text_id " +
            "    WHERE s1.text_norm > 0 AND s2.text_norm > 0 " +
            "  ) " +
            "SELECT p.text_id, p.similar_text_id, " +
            "  ROUND(COALESCE(SUM(p.dot_product), 0) / NULLIF(SUM(s1.text_norm * s2.text_norm), 0), 4) AS similarity, " +
            "  NOW() AS created_at, NOW() AS updated_at " +
            "FROM pairs p " +
            "INNER JOIN (SELECT DISTINCT text_id, text_norm FROM vector_norms) s1 ON s1.text_id = p.text_id " +
            "INNER JOIN (SELECT DISTINCT text_id, text_norm FROM vector_norms) s2 ON s2.text_id = p.similar_text_id " +
            "GROUP BY p.text_id, p.similar_text_id " +
            "HAVING similarity > 0.1")
    int recomputeTextSimilarityIntoTmp();

    @Update("RENAME TABLE text_similarity_matrix TO text_similarity_matrix_old, text_similarity_matrix_tmp TO text_similarity_matrix")
    int atomicRenameToShadowText();

    @Update("DROP TABLE IF EXISTS text_similarity_matrix_old; " +
            "CREATE TABLE IF NOT EXISTS text_similarity_matrix_tmp (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
            "  text_id BIGINT NOT NULL, " +
            "  similar_text_id BIGINT NOT NULL, " +
            "  similarity DECIMAL(5,4) NOT NULL, " +
            "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
            "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
            "  UNIQUE KEY uk_text_pair (text_id, similar_text_id), " +
            "  INDEX idx_text_id (text_id), " +
            "  INDEX idx_similar_text_id (similar_text_id), " +
            "  INDEX idx_similarity (similarity DESC)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章相似度矩阵临时表'")
    int dropOldAndRecreateTmpText();

    // ══════════════════════════════════════════════════════════════
    // 用户相似度矩阵 — Shadow Table 操作
    // ══════════════════════════════════════════════════════════════

    @Update("TRUNCATE TABLE user_similarity_matrix_tmp")
    int truncateTmpUser();

    /**
     * 重算用户相似度矩阵并插入 tmp 表
     *
     * 只对同一篇文章的评分做内积 (s1.text_id = s2.text_id)，且只存 u1 < u2 的一半对（对称）。
     */
    @Insert("INSERT INTO user_similarity_matrix_tmp (user_id, similar_user_id, similarity, created_at, updated_at) " +
            "WITH " +
            "  user_text_scores AS ( " +
            "    SELECT DISTINCT v.user_id, v.text_id, v.interaction_score AS score " +
            "    FROM v_user_text_interaction_scores v " +
            "    INNER JOIN users u ON u.id = v.user_id AND u.role IN ('STUDENT', 'USER') " +
            "    WHERE v.interaction_score > 0 " +
            "  ), " +
            "  vector_norms AS ( " +
            "    SELECT user_id, text_id, score, " +
            "      SQRT(SUM(POW(score, 2)) OVER (PARTITION BY user_id)) AS user_norm " +
            "    FROM user_text_scores " +
            "  ), " +
            "  pairs AS ( " +
            "    SELECT s1.user_id, s2.user_id AS similar_user_id, s1.score * s2.score AS dot_product " +
            "    FROM vector_norms s1 " +
            "    INNER JOIN vector_norms s2 " +
            "      ON s1.text_id = s2.text_id AND s1.user_id < s2.user_id " +
            "    WHERE s1.user_norm > 0 AND s2.user_norm > 0 " +
            "  ) " +
            "SELECT p.user_id, p.similar_user_id, " +
            "  ROUND(COALESCE(SUM(p.dot_product), 0) / NULLIF(SUM(u1.user_norm * u2.user_norm), 0), 4) AS similarity, " +
            "  NOW() AS created_at, NOW() AS updated_at " +
            "FROM pairs p " +
            "INNER JOIN (SELECT DISTINCT user_id, user_norm FROM vector_norms) u1 ON u1.user_id = p.user_id " +
            "INNER JOIN (SELECT DISTINCT user_id, user_norm FROM vector_norms) u2 ON u2.user_id = p.similar_user_id " +
            "GROUP BY p.user_id, p.similar_user_id " +
            "HAVING similarity > 0.1")
    int recomputeUserSimilarityIntoTmp();

    @Update("RENAME TABLE user_similarity_matrix TO user_similarity_matrix_old, user_similarity_matrix_tmp TO user_similarity_matrix")
    int atomicRenameToShadowUser();

    @Update("DROP TABLE IF EXISTS user_similarity_matrix_old; " +
            "CREATE TABLE IF NOT EXISTS user_similarity_matrix_tmp (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
            "  user_id BIGINT NOT NULL, " +
            "  similar_user_id BIGINT NOT NULL, " +
            "  similarity DECIMAL(5,4) NOT NULL, " +
            "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
            "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
            "  UNIQUE KEY uk_user_pair (user_id, similar_user_id), " +
            "  INDEX idx_user_id (user_id), " +
            "  INDEX idx_similar_user_id (similar_user_id), " +
            "  INDEX idx_similarity (similarity DESC)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户相似度矩阵临时表'")
    int dropOldAndRecreateTmpUser();
}
