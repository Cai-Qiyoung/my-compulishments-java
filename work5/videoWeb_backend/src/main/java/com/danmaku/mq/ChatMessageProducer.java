package com.danmaku.mq;

import com.danmaku.constant.MqConstants;
import com.danmaku.model.ChatMessagePayload;
import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageProducer {
    @Resource
    private RabbitTemplate rabbitTemplate;

    public void send(ChatMessagePayload payload) {
        rabbitTemplate.convertAndSend(MqConstants.CHAT_EXCHANGE, MqConstants.CHAT_ROUTING_KEY, payload);
    }
}
