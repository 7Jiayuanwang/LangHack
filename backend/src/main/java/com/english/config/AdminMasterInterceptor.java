package com.english.config;

import com.english.datasource.DynamicDataSourceContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Forces all /api/admin/** and /api/ops/** requests to use the master datasource.
 * <p>
 * Admin operations frequently involve write-then-read patterns (create user → list users).
 * With async master-slave replication, reading from the slave immediately after writing to
 * the master returns stale data. This interceptor ensures admin reads always hit the master.
 */
@Component
public class AdminMasterInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        DynamicDataSourceContext.setForceMaster(true);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        DynamicDataSourceContext.clearForceMaster();
    }
}
