package com.english.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.sso.entity.SsoConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SSO 配置 Mapper
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Mapper
public interface SsoConfigMapper extends BaseMapper<SsoConfig> {

    /**
     * 根据配置键查找
     */
    SsoConfig findByConfigKey(@Param("configKey") String configKey);

    /**
     * 根据配置类型查找所有配置
     */
    List<SsoConfig> findByConfigType(@Param("configType") String configType);
}
