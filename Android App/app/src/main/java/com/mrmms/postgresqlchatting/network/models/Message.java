package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

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

    @SerializedName("seen_by")
    private List<User> seenBy;

    public Message() {}

    public Message(String id, String content, User sender, String createdAt) {
        this.id = id;
        this.content = content;
        this.sender = sender;
        this.createdAt = createdAt;
        this.seenBy = new ArrayList<>();
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

    public List<User> getSeenBy() {
        return seenBy != null ? seenBy : new ArrayList<>();
    }

    public void addSeenUser(User user) {
        if (user == null || user.getId() == null) return;
        if (seenBy == null) {
            seenBy = new ArrayList<>();
        }
        for (User u : seenBy) {
            if (user.getId().equals(u.getId())) return;
        }
        seenBy.add(user);
    }
}
