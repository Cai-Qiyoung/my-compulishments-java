package com.danmaku.mq;

import com.danmaku.constant.MqConstants;
import com.danmaku.entity.ChatMessage;
import com.danmaku.mapper.ChatMessageMapper;
import com.danmaku.mapper.ConversationMapper;
import com.danmaku.model.ChatMessagePayload;
import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ChatMessagePersistConsumer {
    @Resource
    private ChatMessageMapper chatMessageMapper;
    @Resource
    private ConversationMapper conversationMapper;

    @RabbitListener(queues = MqConstants.CHAT_QUEUE)
    public void persist(ChatMessagePayload payload) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(payload.getMessageId());
        chatMessage.setConversationId(payload.getConversationId());
        chatMessage.setSenderId(payload.getSenderId());
        chatMessage.setMessageType(payload.getMessageType());
        chatMessage.setContent(payload.getContent());
        chatMessage.setSentAt(payload.getSentAt());
        chatMessageMapper.insert(chatMessage);
        conversationMapper.updateLastMessage(
                payload.getConversationId(),
                payload.getContent(),
                payload.getMessageType(),
                payload.getSentAt()
        );
    }
}
