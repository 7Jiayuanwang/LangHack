package com.english.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.sso.entity.SsoLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * SSO 登录日志 Mapper
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Mapper
public interface SsoLoginLogMapper extends BaseMapper<SsoLoginLog> {
}
