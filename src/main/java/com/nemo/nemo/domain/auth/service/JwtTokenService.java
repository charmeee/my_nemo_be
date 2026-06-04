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

    public String generateRefreshToken(String userId) {
        long expMs = jwtProperties.getRefreshExpSec() * 1000;
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractSubject(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public String extractJti(String token) {
        try {
            return parseClaims(token).getId();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

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
