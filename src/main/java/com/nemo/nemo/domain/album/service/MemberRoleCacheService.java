package com.nemo.nemo.domain.album.service;

import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed cache for album member roles.
 * Key: album:member:{albumId}:{userId} → role name or "NONE" (TTL 1h)
 *
 * N-CORE-05: push마다 DB 직접 조회 대신 Redis 캐시 활용
 */
@Service
@RequiredArgsConstructor
public class MemberRoleCacheService {

    private static final String KEY_PREFIX = "album:member:";
    private static final Duration TTL = Duration.ofHours(1);
    private static final String NONE = "NONE";

    private final StringRedisTemplate redis;
    private final AlbumMemberRepository albumMemberRepository;

    /** 역할 조회 — Redis 우선, miss 시 DB → Redis 저장. 멤버 아니면 null 반환. */
    public AlbumRole getRole(String albumId, String userId) {
        String key = key(albumId, userId);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return NONE.equals(cached) ? null : AlbumRole.valueOf(cached);
        }

        AlbumRole role = albumMemberRepository
                .findActiveByAlbumIdAndUserId(UUID.fromString(albumId), UUID.fromString(userId))
                .map(am -> am.getRole())
                .orElse(null);

        redis.opsForValue().set(key, role != null ? role.name() : NONE, TTL);
        return role;
    }

    /** 역할 변경/추방/탈퇴 시 호출해 캐시 무효화 */
    public void invalidate(String albumId, String userId) {
        redis.delete(key(albumId, userId));
    }

    private String key(String albumId, String userId) {
        return KEY_PREFIX + albumId + ":" + userId;
    }
}
