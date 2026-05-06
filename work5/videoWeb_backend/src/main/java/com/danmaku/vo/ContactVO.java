package com.danmaku.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ContactVO {
    private String id;
    private String username;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private Boolean blocked;

    @JsonProperty("conversation_id")
    private String conversationId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(Boolean blocked) {
        this.blocked = blocked;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
