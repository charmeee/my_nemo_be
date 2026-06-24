package com.nemo.nemo.domain.auth.handler;

import com.nemo.nemo.config.AppProperties;
import com.nemo.nemo.config.JwtProperties;
import com.nemo.nemo.domain.auth.dto.OAuthUserInfo;
import com.nemo.nemo.domain.auth.service.JwtTokenService;
import com.nemo.nemo.domain.auth.service.OAuthLoginService;
import com.nemo.nemo.domain.auth.service.RefreshTokenService;
import com.nemo.nemo.domain.member.entity.AuthProvider;
import com.nemo.nemo.domain.member.entity.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final OAuthLoginService oAuthLoginService;
    private final AppProperties appProperties;
    private final JwtProperties jwtProperties;

    public OAuth2LoginSuccessHandler(JwtTokenService jwtTokenService,
                                     RefreshTokenService refreshTokenService,
                                     OAuthLoginService oAuthLoginService,
                                     AppProperties appProperties,
                                     JwtProperties jwtProperties) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.oAuthLoginService = oAuthLoginService;
        this.appProperties = appProperties;
        this.jwtProperties = jwtProperties;
    }

    // OAuth2 로그인 성공 시: 회원 조회/생성 → JWT 발급 → refreshToken 쿠키 + 프론트로 리다이렉트
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String registrationId = extractRegistrationId(request);
        OAuthUserInfo info = buildUserInfo(oauth2User, registrationId);

        Member member = oAuthLoginService.findOrCreateMember(info);
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

        response.sendRedirect(appProperties.getOauth().getFrontendRedirectUri() + "?token=" + accessToken);
    }

    private String extractRegistrationId(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // URI pattern: /login/oauth2/code/{registrationId}
        String[] parts = uri.split("/");
        return parts[parts.length - 1];
    }

    @SuppressWarnings("unchecked")
    private OAuthUserInfo buildUserInfo(OAuth2User oauth2User, String registrationId) {
        Map<String, Object> attributes = oauth2User.getAttributes();

        if ("kakao".equals(registrationId)) {
            String providerId = String.valueOf(attributes.get("id"));
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
            String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
            String nickname = properties != null ? (String) properties.get("nickname") : null;
            String profileImage = properties != null ? (String) properties.get("profile_image") : null;
            return new OAuthUserInfo(providerId, email, nickname, profileImage, AuthProvider.KAKAO);
        } else {
            String providerId = (String) attributes.get("sub");
            String email = (String) attributes.get("email");
            String nickname = (String) attributes.get("name");
            String profileImage = (String) attributes.get("picture");
            return new OAuthUserInfo(providerId, email, nickname, profileImage, AuthProvider.GOOGLE);
        }
    }
}
