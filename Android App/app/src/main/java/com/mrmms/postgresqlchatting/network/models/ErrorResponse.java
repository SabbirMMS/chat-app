package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;

public class ErrorResponse {
    @SerializedName("error")
    private String error;

    public String getError() {
        return error != null ? error : "An unexpected error occurred";
    }
}
