package com.zhiquan.sport.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * CloudFront 的缓存键不含 Origin。没带 Origin 的请求先把响应写进缓存后，
 * 浏览器再来就会命中一份没有跨域头的响应。这里不论有没有 Origin 都带上。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProdApiCorsFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/prod-api/")) {
            allow(response);
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
        }
        chain.doFilter(request, response);
        if (request.getRequestURI().startsWith("/prod-api/")) {
            allow(response);
        }
    }

    private static void allow(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");
    }
}
