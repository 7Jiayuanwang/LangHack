package com.english.config;

import com.english.service.DownloadTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Intercepts backup download requests with a temporary download token,
 * bypassing JWT auth to allow direct browser-native downloads (no blob memory issues).
 * <p>
 * Flow: Frontend (with JWT) calls POST /api/ops/backup/{id}/download-token →
 * gets a short-lived token → opens a direct download URL with ?download_token=xxx →
 * this filter validates the token and sets the auth context.
 */
@Component
public class DownloadTokenFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(DownloadTokenFilter.class);

    private static final Pattern DOWNLOAD_PATH_PATTERN =
            Pattern.compile("^/api/ops/backup/\\d+/download$");

    @Autowired
    private DownloadTokenService downloadTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if ("GET".equalsIgnoreCase(request.getMethod()) && isDownloadPath(request)) {
            String token = request.getParameter("download_token");
            if (token != null && !token.isEmpty()) {
                DownloadTokenService.TokenInfo tokenInfo = downloadTokenService.validateToken(token);
                if (tokenInfo != null) {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_OPS"));
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    "download-token:" + tokenInfo.username,
                                    null,
                                    authorities);
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.debug("Download token accepted: backupId={}, user={}",
                            tokenInfo.backupId, tokenInfo.username);
                } else {
                    logger.warn("Invalid or expired download token from IP: {}", request.getRemoteAddr());
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isDownloadPath(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String uri = request.getRequestURI();
        String path = uri != null && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
        return path != null && DOWNLOAD_PATH_PATTERN.matcher(path).matches();
    }
}
