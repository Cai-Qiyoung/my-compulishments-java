package com.danmaku.websocket;

import com.danmaku.util.JwtUtil;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    @Resource
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String token = servletRequest.getServletRequest().getHeader("Access-Token");
        if (token == null || token.isBlank()) {
            token = servletRequest.getServletRequest().getParameter("access_token");
        }
        if (token == null || token.isBlank() || !jwtUtil.validateToken(token) || !jwtUtil.isAccessToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Long userId = jwtUtil.getUserIdFromToken(token);
        attributes.put("userId", String.valueOf(userId));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }
}
