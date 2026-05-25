package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.UserSimilarityMatrix;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface UserSimilarityMatrixMapper extends BaseMapper<UserSimilarityMatrix> {

    @Select("SELECT COALESCE(AVG(similarity), 0) AS avg_sim FROM user_similarity_matrix")
    Map<String, Object> selectAvgSimilarity();
}
