package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.TextSimilarityMatrix;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface TextSimilarityMatrixMapper extends BaseMapper<TextSimilarityMatrix> {

    @Select("SELECT COALESCE(AVG(similarity), 0) AS avg_sim FROM text_similarity_matrix")
    Map<String, Object> selectAvgSimilarity();
}
