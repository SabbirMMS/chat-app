package com.mrmms.postgresqlchatting.network;

import com.mrmms.postgresqlchatting.network.models.AuthRequest;
import com.mrmms.postgresqlchatting.network.models.AuthResponse;
import com.mrmms.postgresqlchatting.network.models.JoinRoomRequest;
import com.mrmms.postgresqlchatting.network.models.MeResponse;
import com.mrmms.postgresqlchatting.network.models.MessagesResponse;
import com.mrmms.postgresqlchatting.network.models.RoomResponse;
import com.mrmms.postgresqlchatting.network.models.RoomsListResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ChatApiService {

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body AuthRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @GET("api/me")
    Call<MeResponse> getMe();

    @POST("api/rooms")
    Call<RoomResponse> createRoom();

    @GET("api/rooms")
    Call<RoomsListResponse> getRooms();

    @POST("api/rooms/join")
    Call<RoomResponse> joinRoom(@Body JoinRoomRequest request);

    @GET("api/rooms/{code}/messages")
    Call<MessagesResponse> getMessages(
            @Path("code") String code,
            @Query("limit") Integer limit,
            @Query("before") String before
    );
}
