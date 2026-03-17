package com.english.test;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.security.Principal;

/**
 * 共享测试工具类。
 */
public class TestUtils {

    /**
     * 创建一个 Servlet Filter，将 SecurityContextHolder 中的 Authentication
     * 设置为请求的 UserPrincipal。
     *
     * 解决 MockMvc standalone setup 中 Spring 内置 PrincipalMethodArgumentResolver
     * 通过 request.getUserPrincipal() 解析 Authentication 参数返回 null 的问题。
     * Filter 在参数解析之前执行，优先级高于 argument resolver。
     */
    public static Filter authenticationFilter() {
        return (request, response, chain) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && request instanceof HttpServletRequest) {
                chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) request) {
                    @Override
                    public Principal getUserPrincipal() {
                        return auth;
                    }
                }, response);
            } else {
                chain.doFilter(request, response);
            }
        };
    }

    /**
     * 将 mock mapper 注入到 ServiceImpl 的 baseMapper 字段。
     * 解决 @InjectMocks 不注入 MyBatis Plus ServiceImpl 父类 baseMapper 字段的问题。
     */
    public static void injectBaseMapper(Object serviceImpl, Object mapper) {
        ReflectionTestUtils.setField(serviceImpl, "baseMapper", mapper);
    }
}
