package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;

public class MeResponse {
    @SerializedName("user")
    private User user;

    public User getUser() {
        return user;
    }
}
