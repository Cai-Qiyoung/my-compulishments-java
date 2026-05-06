package com.danmaku.config;

import com.danmaku.websocket.ChatHandshakeInterceptor;
import com.danmaku.websocket.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;
    @Resource
    private ChatHandshakeInterceptor chatHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .setAllowedOriginPatterns("*")
                .addInterceptors(chatHandshakeInterceptor);
    }
}
