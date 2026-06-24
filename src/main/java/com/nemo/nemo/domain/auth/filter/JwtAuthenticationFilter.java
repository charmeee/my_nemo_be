package com.nemo.nemo.domain.auth.filter;

import com.nemo.nemo.domain.auth.service.JwtTokenService;
import com.nemo.nemo.domain.auth.service.RefreshTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    // 요청별 JWT 검증 → 블랙리스트 확인 → SecurityContext에 인증 정보 설정
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtTokenService.extractSubject(token);
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String jti = jwtTokenService.extractJti(token);
        if (jti != null && refreshTokenService.isBlacklisted(jti)) {
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    // EventSource API 는 커스텀 헤더를 못 달아 SSE 는 쿼리 파라미터로 토큰을 보낸다.
    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if ("/notifications/stream".equals(request.getRequestURI())) {
            return request.getParameter("token");
        }
        return null;
    }
}
