package com.danmaku.config;

import com.danmaku.constant.MqConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(MqConstants.CHAT_EXCHANGE, true, false);
    }

    @Bean
    public Queue chatPersistQueue() {
        return new Queue(MqConstants.CHAT_QUEUE, true);
    }

    @Bean
    public Binding chatPersistBinding(Queue chatPersistQueue, DirectExchange chatExchange) {
        return BindingBuilder.bind(chatPersistQueue).to(chatExchange).with(MqConstants.CHAT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        ObjectMapper mqObjectMapper = objectMapper.copy();
        mqObjectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mqObjectMapper);
    }
}
