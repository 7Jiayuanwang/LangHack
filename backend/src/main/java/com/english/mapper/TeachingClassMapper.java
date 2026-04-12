package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.TeachingClass;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教学班Mapper接口
 */
@Mapper
public interface TeachingClassMapper extends BaseMapper<TeachingClass> {

    /**
     * 按class_id唯一键UPSERT
     */
    void upsertByClassId(TeachingClass entity);
}
