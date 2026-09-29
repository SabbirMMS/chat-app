package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;

public class JoinRoomRequest {
    @SerializedName("code")
    private String code;

    public JoinRoomRequest(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
