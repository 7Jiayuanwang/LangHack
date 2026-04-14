package com.english.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 相似度矩阵 Mapper
 * <p>
 * 包含 Shadow Table 策略所需的 DDL 操作（TRUNCATE / RENAME TABLE / DROP TABLE / CREATE TABLE）
 * 以及 CTE 余弦相似度重算 SQL。
 * <p>
 * 注意：DDL 操作使用 @Update 配合多语句（需确保 DB 配置 allowMultiQueries=true）。
 * INSERT SELECT 操作使用 @Select，返回 Integer（MySQL JDBC 对 INSERT RETURNING 可能返回 null）。
 */
@Mapper
public interface SimilarityMatrixMapper {

    // ══════════════════════════════════════════════════════════════
    // 文章相似度矩阵 — Shadow Table 操作
    // ══════════════════════════════════════════════════════════════

    /**
     * 清空 text_similarity_matrix_tmp 表
     */
    @Update("TRUNCATE TABLE text_similarity_matrix_tmp")
    int truncateTmpText();

    /**
     * 重算文章相似度矩阵并插入 tmp 表
     *
     * 余弦相似度公式：
     *   cos(t1, t2) = Σ(s1_i × s2_i) / (√Σs1_i² × √Σs2_i²)
     *
     * 只考虑两篇文章之间有共同评分用户的情况（s1.user_id = s2.user_id），
     * 确保内积只在可比评分之间计算，避免笛卡尔积噪声。
     */
    @Select("INSERT INTO text_similarity_matrix_tmp (text_id, similar_text_id, similarity, created_at, updated_at) " +
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
            "HAVING similarity > 0.01")
    Integer recomputeTextSimilarityIntoTmp();

    /**
     * 查询 tmp 表实际插入数量（用于替代 INSERT RETURNING，因为 MySQL JDBC 可能返回 0）
     */
    @Select("SELECT COUNT(*) FROM text_similarity_matrix_tmp")
    int countTmpText();

    /**
     * 原子切换：text_similarity_matrix → old，tmp → 正式表
     * 需要 DB 配置 allowMultiQueries=true
     */
    @Update("RENAME TABLE text_similarity_matrix TO text_similarity_matrix_old, text_similarity_matrix_tmp TO text_similarity_matrix")
    int atomicRenameToShadowText();

    /**
     * 删除旧表并重建空 tmp 表
     */
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
            "  INDEX idx_similarity (similarity DESC)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章相似度矩阵临时表'")
    int dropOldAndRecreateTmpText();

    // ══════════════════════════════════════════════════════════════
    // 用户相似度矩阵 — Shadow Table 操作
    // ══════════════════════════════════════════════════════════════

    /**
     * 清空 user_similarity_matrix_tmp 表
     */
    @Update("TRUNCATE TABLE user_similarity_matrix_tmp")
    int truncateTmpUser();

    /**
     * 重算用户相似度矩阵并插入 tmp 表
     *
     * 余弦相似度公式：
     *   cos(u1, u2) = Σ(s1_i × s2_i) / (√Σs1_i² × √Σs2_i²)
     *
     * 只考虑两用户对同一篇文章都有评分的情况（s1.text_id = s2.text_id），
     * 确保内积只在可比评分之间计算。
     */
    @Select("INSERT INTO user_similarity_matrix_tmp (user_id, similar_user_id, similarity, created_at, updated_at) " +
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
            "HAVING similarity > 0.01")
    Integer recomputeUserSimilarityIntoTmp();

    /**
     * 查询 tmp 表实际插入数量
     */
    @Select("SELECT COUNT(*) FROM user_similarity_matrix_tmp")
    int countTmpUser();

    /**
     * 原子切换：user_similarity_matrix → old，tmp → 正式表
     */
    @Update("RENAME TABLE user_similarity_matrix TO user_similarity_matrix_old, user_similarity_matrix_tmp TO user_similarity_matrix")
    int atomicRenameToShadowUser();

    /**
     * 删除旧表并重建空 tmp 表
     */
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
            "  INDEX idx_similarity (similarity DESC)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户相似度矩阵临时表'")
    int dropOldAndRecreateTmpUser();

    // ══════════════════════════════════════════════════════════════
    // 推荐查询
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取用户推荐文章（基于阅读历史查找相似文章，排除已读）
     * <p>
         * 逻辑：找到用户读过的文章 → 通过 text_similarity_matrix 找相似文章 → 排除用户已读过的 → 按相似度降序
     */
    @Select("SELECT t.* FROM texts t " +
            "INNER JOIN text_similarity_matrix m ON t.id = m.similar_text_id " +
            "WHERE m.text_id IN (SELECT text_id FROM user_text_reads WHERE user_id = #{userId}) " +
            "AND t.id NOT IN (SELECT text_id FROM user_text_reads WHERE user_id = #{userId}) " +
            "AND t.deleted = 0 " +
            "ORDER BY m.similarity DESC " +
            "LIMIT #{count}")
    List<com.english.entity.Text> selectRecommendedTexts(@Param("userId") Long userId, @Param("count") int count);
}
