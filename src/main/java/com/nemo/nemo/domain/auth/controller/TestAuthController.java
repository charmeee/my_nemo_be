package com.nemo.nemo.domain.auth.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.config.AppProperties;
import com.nemo.nemo.config.JwtProperties;
import com.nemo.nemo.domain.auth.dto.RefreshResponse;
import com.nemo.nemo.domain.auth.service.JwtTokenService;
import com.nemo.nemo.domain.auth.service.RefreshTokenService;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 개발/테스트 전용 인증 엔드포인트.
 * @Profile("!prod") — 프로덕션 환경에서는 비활성화.
 */
@Profile("!prod")
@RestController
@RequestMapping("/auth")
public class TestAuthController {

    private final MemberRepository memberRepository;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final AppProperties appProperties;

    public TestAuthController(MemberRepository memberRepository,
                              JwtTokenService jwtTokenService,
                              RefreshTokenService refreshTokenService,
                              JwtProperties jwtProperties,
                              AppProperties appProperties) {
        this.memberRepository = memberRepository;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
        this.appProperties = appProperties;
    }

    /**
     * TC-AUTH 테스트용: 이메일로 유저를 찾거나 없으면 생성 후 토큰 발급.
     */
    @PostMapping("/test-login")
    @Transactional
    public ApiResponse<RefreshResponse> testLogin(
            @RequestBody TestLoginRequest req,
            HttpServletResponse response) {

        Member member = memberRepository.findByEmail(req.email())
                .orElseGet(() -> memberRepository.save(
                        Member.create(req.email(), req.nickname(), null)));

        String userId = member.getId().toString();
        String accessToken = jwtTokenService.generateAccessToken(userId);
        String refreshToken = jwtTokenService.generateRefreshToken(userId);
        refreshTokenService.save(userId, refreshToken);

        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(appProperties.getCookie().isSecure())
                .path("/")
                .maxAge(jwtProperties.getRefreshExpSec())
                .sameSite(appProperties.getCookie().getSameSite());
        String cookieDomain = appProperties.getCookie().getDomain();
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookieBuilder.domain(cookieDomain);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());

        return ApiResponse.ok(new RefreshResponse(accessToken, jwtProperties.getAccessExpSec()));
    }

    public record TestLoginRequest(String email, String nickname) {}
}
