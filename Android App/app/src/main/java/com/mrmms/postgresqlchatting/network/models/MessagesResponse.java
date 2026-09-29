package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MessagesResponse {
    @SerializedName("messages")
    private List<Message> messages;

    public List<Message> getMessages() {
        return messages;
    }
}
