package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;

public class AuthRequest {
    @SerializedName("username")
    private String username;

    @SerializedName("password")
    private String password;

    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
