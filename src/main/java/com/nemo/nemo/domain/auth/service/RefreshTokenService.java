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

    public void blacklistAccessToken(String jti, long remainingSeconds) {
        if (remainingSeconds > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_KEY_PREFIX + jti,
                    "1",
                    Duration.ofSeconds(remainingSeconds)
            );
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti));
    }
}
