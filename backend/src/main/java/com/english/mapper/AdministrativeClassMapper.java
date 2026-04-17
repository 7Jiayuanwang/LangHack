package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.AdministrativeClass;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行政班Mapper接口
 */
@Mapper
public interface AdministrativeClassMapper extends BaseMapper<AdministrativeClass> {

    /**
     * 按class_code唯一键UPSERT
     */
    void upsertByClassCode(AdministrativeClass entity);
}
