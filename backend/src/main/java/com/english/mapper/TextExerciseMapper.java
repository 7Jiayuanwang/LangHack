package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.TextExercise;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 课文习题Mapper接口
 */
@Mapper
public interface TextExerciseMapper extends BaseMapper<TextExercise> {

    /**
     * 根据课文ID获取习题列表
     * @param textId 课文ID
     * @return 习题列表
     */
    List<TextExercise> getExercisesByTextId(@Param("textId") Long textId);

    /**
     * 获取课文习题数量
     * @param textId 课文ID
     * @return 习题数量
     */
    int countExercisesByTextId(@Param("textId") Long textId);

    /**
     * 删除课文的所有习题
     * @param textId 课文ID
     * @return 删除数量
     */
    int deleteExercisesByTextId(@Param("textId") Long textId);
}