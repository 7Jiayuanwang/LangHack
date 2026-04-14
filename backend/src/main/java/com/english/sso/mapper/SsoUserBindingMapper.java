package com.english.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.sso.entity.SsoUserBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SSO 用户绑定 Mapper
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Mapper
public interface SsoUserBindingMapper extends BaseMapper<SsoUserBinding> {

    /**
     * 根据提供商和SSO用户ID查找绑定
     */
    SsoUserBinding findByProviderAndSsoUserId(@Param("provider") String provider,
                                               @Param("ssoUserId") String ssoUserId);

    /**
     * 根据用户ID查找绑定
     */
    SsoUserBinding findByUserId(@Param("userId") Long userId);

    /**
     * 查找待审批的绑定
     */
    List<SsoUserBinding> findPendingApprovals();

    /**
     * 根据提供商查找所有绑定
     */
    List<SsoUserBinding> findByProvider(@Param("provider") String provider);
}
