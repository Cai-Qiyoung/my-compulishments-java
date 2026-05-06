package com.danmaku.constant;

public final class MqConstants {
    private MqConstants() {
    }

    public static final String CHAT_EXCHANGE = "danmaku.chat.exchange";
    public static final String CHAT_QUEUE = "danmaku.chat.persist.queue";
    public static final String CHAT_ROUTING_KEY = "danmaku.chat.persist";
}
