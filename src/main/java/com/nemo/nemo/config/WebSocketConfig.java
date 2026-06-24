package com.nemo.nemo.config;

import com.nemo.nemo.domain.excalidraw.handler.ExcalidrawSyncHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ExcalidrawSyncHandler excalidrawSyncHandler;
    private final JwtHandshakeInterceptor handshakeInterceptor;

    // Excalidraw 동기화 WS 엔드포인트 등록 + handshake 인터셉터 부착
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(excalidrawSyncHandler, "/sync/excalidraw/*")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
