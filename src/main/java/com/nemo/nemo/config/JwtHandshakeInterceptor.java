package com.nemo.nemo.config;

import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.auth.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * WebSocket handshake 시 JWT 검증 및 albumId/userId 추출.
 * URI 패턴: /sync/albums/{albumId}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenService jwtTokenService;
    private final AlbumMemberRepository albumMemberRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            // JWT: Authorization 헤더 또는 쿼리 파라미터
            String token = extractToken(request);
            if (token == null) {
                log.warn("WebSocket handshake rejected: no token. URI={}", request.getURI());
                return false;
            }

            String userId = jwtTokenService.extractSubject(token);
            if (userId == null) {
                log.warn("WebSocket handshake rejected: invalid token. token_prefix={}, uri={}", token.substring(0, Math.min(20, token.length())), request.getURI());
                return false;
            }

            // URI에서 albumId 추출
            String path = request.getURI().getPath();
            String albumId = extractAlbumId(path);
            if (albumId == null) {
                return false;
            }

            // 앨범 멤버십 검증
            boolean isMember = albumMemberRepository
                    .findActiveByAlbumIdAndUserId(UUID.fromString(albumId), UUID.fromString(userId))
                    .isPresent();
            if (!isMember) {
                log.warn("WebSocket handshake rejected: user {} not member of album {}", userId, albumId);
                return false;
            }

            attributes.put("userId", userId);
            attributes.put("albumId", albumId);
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        return null;
    }

    private String extractAlbumId(String path) {
        // /sync/albums/{albumId}
        String[] parts = path.split("/");
        if (parts.length >= 4 && "sync".equals(parts[1]) && "albums".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
}
