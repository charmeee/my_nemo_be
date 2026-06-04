package com.nemo.nemo.domain.auth.service;

import com.nemo.nemo.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenService - 토큰 생성/검증")
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-must-be-at-least-32-bytes!!");
        props.setAccessExpSec(900);
        props.setRefreshExpSec(604800);
        jwtTokenService = new JwtTokenService(props);
    }

    @Test
    @DisplayName("generateAccessToken - subject 추출 성공")
    void accessToken_subject_추출() {
        String userId = "user-uuid-1234";
        String token = jwtTokenService.generateAccessToken(userId);

        assertThat(jwtTokenService.extractSubject(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("generateAccessToken - jti 포함")
    void accessToken_jti_포함() {
        String token = jwtTokenService.generateAccessToken("user-1");

        assertThat(jwtTokenService.extractJti(token)).isNotBlank();
    }

    @Test
    @DisplayName("generateRefreshToken - subject 추출 성공")
    void refreshToken_subject_추출() {
        String userId = "user-uuid-5678";
        String token = jwtTokenService.generateRefreshToken(userId);

        assertThat(jwtTokenService.extractSubject(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("getRemainingSeconds - 유효한 토큰은 양수 반환")
    void remainingSeconds_양수() {
        String token = jwtTokenService.generateAccessToken("user-1");

        assertThat(jwtTokenService.getRemainingSeconds(token)).isGreaterThan(0L);
    }

    @Test
    @DisplayName("유효하지 않은 토큰 - extractSubject null 반환")
    void 잘못된_토큰_null반환() {
        assertThat(jwtTokenService.extractSubject("invalid.token.string")).isNull();
    }

    @Test
    @DisplayName("유효하지 않은 토큰 - getRemainingSeconds 0 반환")
    void 잘못된_토큰_남은시간_0() {
        assertThat(jwtTokenService.getRemainingSeconds("invalid.token.string")).isEqualTo(0L);
    }

    @Test
    @DisplayName("만료된 토큰 - extractSubject null 반환")
    void 만료된_토큰_null반환() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-must-be-at-least-32-bytes!!");
        props.setAccessExpSec(-1); // 이미 만료
        JwtTokenService expiredService = new JwtTokenService(props);

        String token = expiredService.generateAccessToken("user-1");

        assertThat(jwtTokenService.extractSubject(token)).isNull();
    }
}
