package com.nemo.nemo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    // Rate Limit 인터셉터를 주요 API 경로에 등록 (공개 invite info는 제외)
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/albums/**", "/invite/**", "/notifications/**", "/trash/**")
                .excludePathPatterns("/invite/*/info"); // 공개 엔드포인트
    }
}
