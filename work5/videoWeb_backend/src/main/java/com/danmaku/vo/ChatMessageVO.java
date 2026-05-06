package com.danmaku.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class ChatMessageVO {
    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("sender_id")
    private String senderId;

    @JsonProperty("sender_name")
    private String senderName;

    @JsonProperty("sender_avatar")
    private String senderAvatar;

    @JsonProperty("message_type")
    private String messageType;

    private String content;

    @JsonProperty("sent_at")
    private LocalDateTime sentAt;

    private Boolean self;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderAvatar() {
        return senderAvatar;
    }

    public void setSenderAvatar(String senderAvatar) {
        this.senderAvatar = senderAvatar;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public Boolean getSelf() {
        return self;
    }

    public void setSelf(Boolean self) {
        this.self = self;
    }
}
