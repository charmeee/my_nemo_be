package com.nemo.nemo.config;

import com.nemo.nemo.domain.excalidraw.handler.ExcalidrawSyncHandler;
import com.nemo.nemo.domain.sync.handler.TLDrawSyncHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TLDrawSyncHandler syncHandler;
    private final ExcalidrawSyncHandler excalidrawSyncHandler;
    private final JwtHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // TLDraw (legacy, backward compat)
        registry.addHandler(syncHandler, "/sync/albums/*")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");

        // Excalidraw (new)
        registry.addHandler(excalidrawSyncHandler, "/sync/excalidraw/*")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
