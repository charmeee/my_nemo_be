package com.nemo.nemo.domain.auth.service;

import com.nemo.nemo.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    // accessToken 발급 — jti 포함(로그아웃 시 블랙리스트용)
    public String generateAccessToken(String userId) {
        long expMs = jwtProperties.getAccessExpSec() * 1000;
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 비회원 게스트용 24시간 토큰.
     * subject = "guest:{albumId}" — DB 멤버십 없이 해당 앨범 읽기 전용 접근 허용.
     */
    public String generateGuestToken(String albumId) {
        long expMs = 24 * 60 * 60 * 1000L;
        return Jwts.builder()
                .subject("guest:" + albumId)
                .claim("type", "guest")
                .claim("albumId", albumId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expMs))
                .signWith(getSigningKey())
                .compact();
    }

    // refreshToken 발급 (장기 만료)
    public String generateRefreshToken(String userId) {
        long expMs = jwtProperties.getRefreshExpSec() * 1000;
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expMs))
                .signWith(getSigningKey())
                .compact();
    }

    // 토큰 subject(userId) 추출 — 실패 시 null
    public String extractSubject(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    // 토큰 jti 추출 — 실패 시 null
    public String extractJti(String token) {
        try {
            return parseClaims(token).getId();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    // 토큰 남은 유효 시간(초) — 블랙리스트 TTL 산정용
    public long getRemainingSeconds(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
