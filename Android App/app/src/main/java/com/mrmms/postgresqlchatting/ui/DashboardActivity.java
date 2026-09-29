package com.mrmms.postgresqlchatting.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.mrmms.postgresqlchatting.R;
import com.mrmms.postgresqlchatting.network.ApiClient;
import com.mrmms.postgresqlchatting.network.models.JoinRoomRequest;
import com.mrmms.postgresqlchatting.network.models.MeResponse;
import com.mrmms.postgresqlchatting.network.models.Room;
import com.mrmms.postgresqlchatting.network.models.RoomResponse;
import com.mrmms.postgresqlchatting.network.models.RoomsListResponse;
import com.mrmms.postgresqlchatting.ui.adapters.RoomAdapter;
import com.mrmms.postgresqlchatting.util.PrefsManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvUserBadge;
    private ImageButton btnConfigServer;
    private ImageButton btnLogout;

    // Create Room
    private Button btnCreateRoom;
    private ProgressBar pbCreateRoom;
    private LinearLayout layoutCreatedRoom;
    private TextView tvCreatedRoomCode;
    private Button btnCopyCreatedCode;
    private Button btnEnterCreatedRoom;
    private String lastCreatedCode;

    // Join Room
    private EditText etJoinCode;
    private Button btnJoinRoom;
    private ProgressBar pbJoinRoom;

    // Rooms list
    private SwipeRefreshLayout swipeRefreshRooms;
    private RecyclerView rvRooms;
    private TextView tvEmptyRooms;
    private Button btnRefreshRooms;
    private RoomAdapter roomAdapter;

    private PrefsManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        prefs = PrefsManager.getInstance(this);

        if (!prefs.isLoggedIn()) {
            goToLogin();
            return;
        }

        initViews();
        setupListeners();
        setupRecyclerView();
        verifySession();
        loadRooms();
    }

    private void initViews() {
        tvUserBadge = findViewById(R.id.tvUserBadge);
        btnConfigServer = findViewById(R.id.btnConfigServer);
        btnLogout = findViewById(R.id.btnLogout);

        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        pbCreateRoom = findViewById(R.id.pbCreateRoom);
        layoutCreatedRoom = findViewById(R.id.layoutCreatedRoom);
        tvCreatedRoomCode = findViewById(R.id.tvCreatedRoomCode);
        btnCopyCreatedCode = findViewById(R.id.btnCopyCreatedCode);
        btnEnterCreatedRoom = findViewById(R.id.btnEnterCreatedRoom);

        etJoinCode = findViewById(R.id.etJoinCode);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        pbJoinRoom = findViewById(R.id.pbJoinRoom);

        swipeRefreshRooms = findViewById(R.id.swipeRefreshRooms);
        rvRooms = findViewById(R.id.rvRooms);
        tvEmptyRooms = findViewById(R.id.tvEmptyRooms);
        btnRefreshRooms = findViewById(R.id.btnRefreshRooms);

        String username = prefs.getUsername();
        tvUserBadge.setText(username != null ? "@" + username : "@user");
    }

    private void setupListeners() {
        btnConfigServer.setOnClickListener(v -> {
            new ServerConfigDialog(this, newUrl -> {
                loadRooms();
            }).show();
        });

        btnLogout.setOnClickListener(v -> {
            prefs.clearAuth();
            goToLogin();
        });

        btnCreateRoom.setOnClickListener(v -> createRoom());

        btnCopyCreatedCode.setOnClickListener(v -> {
            if (lastCreatedCode != null) {
                copyToClipboard(lastCreatedCode);
                Toast.makeText(this, "Room code copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        btnEnterCreatedRoom.setOnClickListener(v -> {
            if (lastCreatedCode != null) {
                openRoom(lastCreatedCode);
            }
        });

        btnJoinRoom.setOnClickListener(v -> joinRoom());

        btnRefreshRooms.setOnClickListener(v -> loadRooms());

        swipeRefreshRooms.setOnRefreshListener(this::loadRooms);
    }

    private void setupRecyclerView() {
        roomAdapter = new RoomAdapter(room -> openRoom(room.getCode()));
        rvRooms.setLayoutManager(new LinearLayoutManager(this));
        rvRooms.setAdapter(roomAdapter);
    }

    private void verifySession() {
        ApiClient.getInstance(this).getApi().getMe().enqueue(new Callback<MeResponse>() {
            @Override
            public void onResponse(Call<MeResponse> call, Response<MeResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getUser() != null) {
                    prefs.saveUser(response.body().getUser().getId(), response.body().getUser().getUsername());
                    tvUserBadge.setText("@" + response.body().getUser().getUsername());
                } else if (response.code() == 401) {
                    prefs.clearAuth();
                    goToLogin();
                }
            }

            @Override
            public void onFailure(Call<MeResponse> call, Throwable t) {
                // Ignore network glitch on profile check
            }
        });
    }

    private void loadRooms() {
        swipeRefreshRooms.setRefreshing(true);
        ApiClient.getInstance(this).getApi().getRooms().enqueue(new Callback<RoomsListResponse>() {
            @Override
            public void onResponse(Call<RoomsListResponse> call, Response<RoomsListResponse> response) {
                swipeRefreshRooms.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    var list = response.body().getRooms();
                    roomAdapter.setRooms(list);
                    tvEmptyRooms.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    Toast.makeText(DashboardActivity.this, "Failed to load rooms", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RoomsListResponse> call, Throwable t) {
                swipeRefreshRooms.setRefreshing(false);
                Toast.makeText(DashboardActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createRoom() {
        setCreatingState(true);
        layoutCreatedRoom.setVisibility(View.GONE);

        ApiClient.getInstance(this).getApi().createRoom().enqueue(new Callback<RoomResponse>() {
            @Override
            public void onResponse(Call<RoomResponse> call, Response<RoomResponse> response) {
                setCreatingState(false);
                if (response.isSuccessful() && response.body() != null && response.body().getRoom() != null) {
                    Room room = response.body().getRoom();
                    lastCreatedCode = room.getCode();
                    tvCreatedRoomCode.setText(lastCreatedCode);
                    layoutCreatedRoom.setVisibility(View.VISIBLE);
                    loadRooms();
                } else {
                    String msg = ApiClient.getInstance(DashboardActivity.this).parseError(response);
                    Toast.makeText(DashboardActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<RoomResponse> call, Throwable t) {
                setCreatingState(false);
                Toast.makeText(DashboardActivity.this, "Error creating room: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void joinRoom() {
        String code = etJoinCode.getText().toString().trim().toUpperCase();
        if (code.isEmpty()) {
            Toast.makeText(this, "Please enter a 6-character room code", Toast.LENGTH_SHORT).show();
            return;
        }

        setJoiningState(true);

        JoinRoomRequest request = new JoinRoomRequest(code);
        ApiClient.getInstance(this).getApi().joinRoom(request).enqueue(new Callback<RoomResponse>() {
            @Override
            public void onResponse(Call<RoomResponse> call, Response<RoomResponse> response) {
                setJoiningState(false);
                if (response.isSuccessful() && response.body() != null && response.body().getRoom() != null) {
                    etJoinCode.setText("");
                    openRoom(response.body().getRoom().getCode());
                } else {
                    String msg = ApiClient.getInstance(DashboardActivity.this).parseError(response);
                    Toast.makeText(DashboardActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<RoomResponse> call, Throwable t) {
                setJoiningState(false);
                Toast.makeText(DashboardActivity.this, "Error joining room: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setCreatingState(boolean creating) {
        btnCreateRoom.setVisibility(creating ? View.INVISIBLE : View.VISIBLE);
        pbCreateRoom.setVisibility(creating ? View.VISIBLE : View.GONE);
    }

    private void setJoiningState(boolean joining) {
        btnJoinRoom.setVisibility(joining ? View.INVISIBLE : View.VISIBLE);
        pbJoinRoom.setVisibility(joining ? View.VISIBLE : View.GONE);
        etJoinCode.setEnabled(!joining);
    }

    private void openRoom(String code) {
        Intent intent = new Intent(this, RoomActivity.class);
        intent.putExtra("EXTRA_ROOM_CODE", code);
        startActivity(intent);
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Room Code", text);
        clipboard.setPrimaryClip(clip);
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
