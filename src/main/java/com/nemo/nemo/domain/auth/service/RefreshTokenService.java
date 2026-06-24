package com.nemo.nemo.domain.auth.service;

import com.nemo.nemo.config.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    // refreshToken을 Redis에 TTL과 함께 저장
    public void save(String userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + userId,
                refreshToken,
                Duration.ofSeconds(jwtProperties.getRefreshExpSec())
        );
    }

    public Optional<String> find(String userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId));
    }

    public void delete(String userId) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
    }

    // 로그아웃 시 accessToken의 jti를 남은 유효시간만큼 블랙리스트에 등록
    public void blacklistAccessToken(String jti, long remainingSeconds) {
        if (remainingSeconds > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_KEY_PREFIX + jti,
                    "1",
                    Duration.ofSeconds(remainingSeconds)
            );
        }
    }

    // jti가 블랙리스트에 등록되었는지 확인
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti));
    }
}
