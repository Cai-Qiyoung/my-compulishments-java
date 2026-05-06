package com.danmaku.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class SessionItemVO {
    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("conversation_type")
    private String conversationType;

    @JsonProperty("conversation_name")
    private String conversationName;

    @JsonProperty("conversation_avatar")
    private String conversationAvatar;

    @JsonProperty("target_user_id")
    private String targetUserId;

    @JsonProperty("last_message")
    private String lastMessage;

    @JsonProperty("last_message_type")
    private String lastMessageType;

    @JsonProperty("last_message_time")
    private LocalDateTime lastMessageTime;

    private Boolean blocked;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getConversationType() {
        return conversationType;
    }

    public void setConversationType(String conversationType) {
        this.conversationType = conversationType;
    }

    public String getConversationName() {
        return conversationName;
    }

    public void setConversationName(String conversationName) {
        this.conversationName = conversationName;
    }

    public String getConversationAvatar() {
        return conversationAvatar;
    }

    public void setConversationAvatar(String conversationAvatar) {
        this.conversationAvatar = conversationAvatar;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getLastMessageType() {
        return lastMessageType;
    }

    public void setLastMessageType(String lastMessageType) {
        this.lastMessageType = lastMessageType;
    }

    public LocalDateTime getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(LocalDateTime lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public Boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(Boolean blocked) {
        this.blocked = blocked;
    }
}
