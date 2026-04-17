package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.CourseSelection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 选课记录Mapper接口
 */
@Mapper
public interface CourseSelectionMapper extends BaseMapper<CourseSelection> {

    /**
     * 按selection_id唯一键UPSERT
     */
    void upsertBySelectionId(CourseSelection entity);
}
