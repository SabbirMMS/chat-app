package com.mrmms.postgresqlchatting.network.models;

import com.google.gson.annotations.SerializedName;

public class Room {
    @SerializedName("id")
    private String id;

    @SerializedName("code")
    private String code;

    @SerializedName("created_by")
    private String createdBy;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("joined_at")
    private String joinedAt;

    @SerializedName("creator_username")
    private String creatorUsername;

    @SerializedName("member_count")
    private int memberCount;

    public Room() {}

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getJoinedAt() {
        return joinedAt;
    }

    public String getCreatorUsername() {
        return creatorUsername;
    }

    public int getMemberCount() {
        return memberCount;
    }
}
