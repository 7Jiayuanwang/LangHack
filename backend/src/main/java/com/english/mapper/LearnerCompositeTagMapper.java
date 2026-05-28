package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.LearnerCompositeTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LearnerCompositeTagMapper extends BaseMapper<LearnerCompositeTag> {

    @Select("SELECT * FROM learner_composite_tags WHERE enabled = 1 ORDER BY priority ASC")
    List<LearnerCompositeTag> selectAllEnabled();

    @Select("SELECT * FROM learner_composite_tags ORDER BY priority ASC")
    List<LearnerCompositeTag> selectAllOrdered();

    @Select("SELECT COUNT(*) FROM learner_composite_tags WHERE code = #{code}")
    int countByCode(@Param("code") String code);

    @Select("SELECT COUNT(*) FROM learner_composite_tags WHERE code = #{code} AND id != #{excludeId}")
    int countByCodeExcluding(@Param("code") String code, @Param("excludeId") Long excludeId);
}
