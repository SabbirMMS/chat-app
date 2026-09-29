package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class RoomsListResponse {
    @SerializedName("rooms")
    private List<Room> rooms;

    public List<Room> getRooms() {
        return rooms;
    }
}
