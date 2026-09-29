package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;

public class RoomResponse {
    @SerializedName("room")
    private Room room;

    public Room getRoom() {
        return room;
    }
}
