package com.nemo.nemo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * N-CORE-12: API 엔드포인트별 Rate Limiting (100 req/min per user).
 * 인증된 userId 기준으로 1분 슬라이딩 윈도우 제한.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_REQUESTS_PER_MINUTE = 100;

    // userId → request count in current minute
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public RateLimitInterceptor() {
        // 매 분마다 카운터 초기화
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(counters::clear, 1, 1, TimeUnit.MINUTES);
    }

    // 요청 전 userId별 카운터 증가 후 분당 한도 초과 시 429 반환
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userId = extractUserId(request);
        if (userId == null) {
            return true; // 미인증 요청은 SecurityConfig에서 처리
        }

        int count = counters
                .computeIfAbsent(userId, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (count > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"rate limit exceeded\"}");
            return false;
        }
        return true;
    }

    private String extractUserId(HttpServletRequest request) {
        java.security.Principal principal = request.getUserPrincipal();
        return principal != null ? principal.getName() : null;
    }
}
