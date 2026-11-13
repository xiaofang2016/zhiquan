package com.zhiquan.sport.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 社区读接口不能进 CDN。视频清单、密钥和分片仍按播放器需要缓存。 */
@Component
public class CommunityCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && ((path.startsWith("/prod-api/community/")
                && !path.startsWith("/prod-api/community/video/")
                && !path.startsWith("/prod-api/community/media/"))
                || path.startsWith("/prod-api/chat/groups")
                || path.startsWith("/prod-api/goods/")
                || path.startsWith("/prod-api/domain-rewrites"))) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
