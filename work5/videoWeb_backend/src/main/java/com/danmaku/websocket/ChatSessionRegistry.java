package com.danmaku.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSessionRegistry {
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Resource
    private ObjectMapper objectMapper;

    public void register(String userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(String userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            userSessions.remove(userId);
        }
    }

    public void sendToUser(String userId, Object payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        sendToSessions(sessions, payload);
    }

    public void sendToUsers(Collection<String> userIds, Object payload) {
        if (userIds == null) {
            return;
        }
        for (String userId : userIds) {
            sendToUser(userId, payload);
        }
    }

    private void sendToSessions(Set<WebSocketSession> sessions, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) {
                    continue;
                }
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException ignored) {
        }
    }
}
