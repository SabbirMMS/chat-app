package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;

public class User {
    @SerializedName("id")
    private String id;

    @SerializedName("username")
    private String username;

    @SerializedName("created_at")
    private String createdAt;

    public User() {}

    public User(String id, String username) {
        this.id = id;
        this.username = username;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
