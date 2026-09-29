package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;

public class Message {
    @SerializedName("id")
    private String id;

    @SerializedName("code")
    private String code;

    @SerializedName("content")
    private String content;

    @SerializedName("sender")
    private User sender;

    @SerializedName("created_at")
    private String createdAt;

    public Message() {}

    public Message(String id, String content, User sender, String createdAt) {
        this.id = id;
        this.content = content;
        this.sender = sender;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getContent() {
        return content;
    }

    public User getSender() {
        return sender;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
