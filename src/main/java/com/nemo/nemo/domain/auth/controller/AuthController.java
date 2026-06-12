package com.nemo.nemo.domain.auth.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.config.AppProperties;
import com.nemo.nemo.config.JwtProperties;
import com.nemo.nemo.domain.auth.dto.EmailLoginRequest;
import com.nemo.nemo.domain.auth.dto.EmailRegisterRequest;
import com.nemo.nemo.domain.auth.dto.MemberResponse;
import com.nemo.nemo.domain.auth.dto.RefreshResponse;
import com.nemo.nemo.domain.auth.service.EmailAuthService;
import com.nemo.nemo.domain.auth.service.JwtTokenService;
import com.nemo.nemo.domain.auth.service.RefreshTokenService;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final MemberRepository memberRepository;
    private final JwtProperties jwtProperties;
    private final AppProperties appProperties;
    private final EmailAuthService emailAuthService;

    public AuthController(JwtTokenService jwtTokenService,
                          RefreshTokenService refreshTokenService,
                          MemberRepository memberRepository,
                          JwtProperties jwtProperties,
                          AppProperties appProperties,
                          EmailAuthService emailAuthService) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.memberRepository = memberRepository;
        this.jwtProperties = jwtProperties;
        this.appProperties = appProperties;
        this.emailAuthService = emailAuthService;
    }

    @PostMapping("/register")
    public ApiResponse<RefreshResponse> register(@RequestBody EmailRegisterRequest req, HttpServletResponse response) {
        Member member = emailAuthService.register(req.email(), req.password(), req.nickname());
        return issueTokens(member.getId().toString(), response);
    }

    @PostMapping("/login")
    public ApiResponse<RefreshResponse> emailLogin(@RequestBody EmailLoginRequest req, HttpServletResponse response) {
        Member member = emailAuthService.login(req.email(), req.password());
        return issueTokens(member.getId().toString(), response);
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, "refreshToken");
        if (refreshToken == null) {
            throw new NemoException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        String userId = jwtTokenService.extractSubject(refreshToken);
        if (userId == null) {
            throw new NemoException(ErrorCode.INVALID_TOKEN);
        }

        String stored = refreshTokenService.find(userId)
                .orElseThrow(() -> new NemoException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (!stored.equals(refreshToken)) {
            throw new NemoException(ErrorCode.INVALID_TOKEN);
        }

        String newAccessToken = jwtTokenService.generateAccessToken(userId);
        return ApiResponse.ok(new RefreshResponse(newAccessToken, jwtProperties.getAccessExpSec()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            String jti = jwtTokenService.extractJti(accessToken);
            String userId = jwtTokenService.extractSubject(accessToken);
            if (jti != null) {
                long remaining = jwtTokenService.getRemainingSeconds(accessToken);
                refreshTokenService.blacklistAccessToken(jti, remaining);
            }
            if (userId != null) {
                refreshTokenService.delete(userId);
            }
        }

        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(appProperties.getCookie().isSecure())
                .path("/")
                .maxAge(0)
                .sameSite(appProperties.getCookie().getSameSite())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());

        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<MemberResponse> me(@AuthenticationPrincipal String userId) {
        Member member = memberRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new NemoException(ErrorCode.MEMBER_NOT_FOUND));

        MemberResponse body = new MemberResponse(
                member.getId().toString(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImage()
        );
        return ApiResponse.ok(body);
    }

    private ApiResponse<RefreshResponse> issueTokens(String userId, HttpServletResponse response) {
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

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
