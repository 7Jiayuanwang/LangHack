package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.ExerciseOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 习题选项Mapper接口
 */
@Mapper
public interface ExerciseOptionMapper extends BaseMapper<ExerciseOption> {

    /**
     * 根据习题ID获取选项列表
     * @param exerciseId 习题ID
     * @return 选项列表
     */
    List<ExerciseOption> getOptionsByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * 获取习题的正确答案
     * @param exerciseId 习题ID
     * @return 正确答案选项
     */
    ExerciseOption getCorrectOption(@Param("exerciseId") Long exerciseId);

    /**
     * 删除习题的所有选项
     * @param exerciseId 习题ID
     * @return 删除数量
     */
    int deleteOptionsByExerciseId(@Param("exerciseId") Long exerciseId);
}