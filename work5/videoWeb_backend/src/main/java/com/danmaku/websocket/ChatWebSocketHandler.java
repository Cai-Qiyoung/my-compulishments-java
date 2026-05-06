package com.danmaku.websocket;

import com.danmaku.exception.BusinessException;
import com.danmaku.model.WebSocketChatCommand;
import com.danmaku.service.MessageService;
import com.danmaku.vo.ChatMessageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    @Resource
    private ChatSessionRegistry chatSessionRegistry;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private MessageService messageService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = resolveUserId(session);
        if (userId != null) {
            chatSessionRegistry.register(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            WebSocketChatCommand command = objectMapper.readValue(message.getPayload(), WebSocketChatCommand.class);
            String action = command.getAction() == null ? "" : command.getAction().trim().toUpperCase();
            if ("PING".equals(action)) {
                session.sendMessage(new TextMessage("{\"event\":\"PONG\"}"));
                return;
            }
            if (!"SEND".equals(action)) {
                sendError(session, "不支持的 WebSocket 操作");
                return;
            }
            ChatMessageVO result = messageService.sendMessageByUserId(
                    resolveUserId(session),
                    command.getConversationId(),
                    command.getMessageType(),
                    command.getContent()
            );
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("event", "MESSAGE_ACK");
            envelope.put("data", result);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
        } catch (BusinessException ex) {
            sendError(session, ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = resolveUserId(session);
        if (userId != null) {
            chatSessionRegistry.unregister(userId, session);
        }
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "ERROR");
        payload.put("message", message);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private String resolveUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        return userId == null ? null : userId.toString();
    }
}
