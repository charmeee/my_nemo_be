package com.nemo.nemo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket handshake 시 albumId 추출.
 * 인증(JWT 검증)은 URL 쿼리 파라미터 대신 최초 connect 메시지 본문에서 처리합니다.
 * — URL에 토큰을 담으면 서버 액세스 로그/프록시에 평문 노출되는 문제 방지.
 * URI 패턴: /sync/excalidraw/{albumId}
 */
@Slf4j
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    // handshake 단계에서 URI의 albumId만 추출해 attributes에 저장 (JWT는 connect 메시지에서 검증)
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            String path = request.getURI().getPath();
            String albumId = extractAlbumId(path);
            if (albumId == null) {
                log.warn("WebSocket handshake rejected: invalid path. URI={}", request.getURI());
                return false;
            }
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

    private String extractAlbumId(String path) {
        // /sync/excalidraw/{albumId}
        String[] parts = path.split("/");
        if (parts.length >= 4 && "sync".equals(parts[1])
                && ("albums".equals(parts[2]) || "excalidraw".equals(parts[2]))) {
            return parts[3];
        }
        return null;
    }
}
