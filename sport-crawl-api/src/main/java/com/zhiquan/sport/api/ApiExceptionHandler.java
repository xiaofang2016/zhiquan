package com.zhiquan.sport.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RestControllerAdvice
public class ApiExceptionHandler implements WebMvcConfigurer {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResult.fail("400", ex.getMessage()));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/prod-api/user/**").allowedOrigins("*").allowedMethods("POST", "OPTIONS").allowedHeaders("*");
        registry.addMapping("/prod-api/chat/**").allowedOrigins("*").allowedMethods("POST", "OPTIONS").allowedHeaders("*");
        registry.addMapping("/prod-api/community/**").allowedOrigins("*").allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("*");
        registry.addMapping("/prod-api/admin/**").allowedOrigins("*").allowedMethods("POST", "OPTIONS").allowedHeaders("*");
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("GET");
        registry.addMapping("/prod-api/**").allowedOrigins("*").allowedMethods("GET");
    }
}
