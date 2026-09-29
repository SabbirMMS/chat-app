package com.mrmms.postgresqlchatting.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mrmms.postgresqlchatting.R;
import com.mrmms.postgresqlchatting.network.ApiClient;
import com.mrmms.postgresqlchatting.network.models.Message;
import com.mrmms.postgresqlchatting.network.models.MessagesResponse;
import com.mrmms.postgresqlchatting.socket.SocketManager;
import com.mrmms.postgresqlchatting.ui.adapters.MessageAdapter;
import com.mrmms.postgresqlchatting.util.PrefsManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RoomActivity extends AppCompatActivity implements SocketManager.SocketEventListener {

    public static final String EXTRA_ROOM_CODE = "EXTRA_ROOM_CODE";

    private ImageButton btnBack;
    private TextView tvRoomCode;
    private ImageButton btnCopyRoomCode;
    private View viewStatusDot;
    private TextView tvStatusText;
    private TextView tvPresenceNotice;

    private RecyclerView rvMessages;
    private ProgressBar pbLoadingHistory;
    private TextView tvNoMessages;

    private EditText etMessageInput;
    private ImageButton btnSend;

    private String roomCode;
    private MessageAdapter messageAdapter;
    private SocketManager socketManager;
    private PrefsManager prefs;
    private final Handler presenceHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        roomCode = getIntent().getStringExtra(EXTRA_ROOM_CODE);
        if (roomCode == null || roomCode.trim().isEmpty()) {
            Toast.makeText(this, "Invalid room code", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        roomCode = roomCode.trim().toUpperCase();

        prefs = PrefsManager.getInstance(this);
        socketManager = SocketManager.getInstance(this);

        initViews();
        setupListeners();
        setupRecyclerView();

        loadMessageHistory();
        setupSocket();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvRoomCode = findViewById(R.id.tvRoomCode);
        btnCopyRoomCode = findViewById(R.id.btnCopyRoomCode);
        viewStatusDot = findViewById(R.id.viewStatusDot);
        tvStatusText = findViewById(R.id.tvStatusText);
        tvPresenceNotice = findViewById(R.id.tvPresenceNotice);

        rvMessages = findViewById(R.id.rvMessages);
        pbLoadingHistory = findViewById(R.id.pbLoadingHistory);
        tvNoMessages = findViewById(R.id.tvNoMessages);

        etMessageInput = findViewById(R.id.etMessageInput);
        btnSend = findViewById(R.id.btnSend);

        tvRoomCode.setText("#" + roomCode);
        updateStatusDisplay("Connecting", R.color.status_amber);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnCopyRoomCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Room Code", roomCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Room code #" + roomCode + " copied", Toast.LENGTH_SHORT).show();
        });

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void setupRecyclerView() {
        String currentUserId = prefs.getUserId();
        messageAdapter = new MessageAdapter(currentUserId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);
    }

    private void loadMessageHistory() {
        pbLoadingHistory.setVisibility(View.VISIBLE);
        tvNoMessages.setVisibility(View.GONE);

        ApiClient.getInstance(this).getApi().getMessages(roomCode, 50, null).enqueue(new Callback<MessagesResponse>() {
            @Override
            public void onResponse(Call<MessagesResponse> call, Response<MessagesResponse> response) {
                pbLoadingHistory.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<Message> list = response.body().getMessages();
                    messageAdapter.setMessages(list);
                    if (list == null || list.isEmpty()) {
                        tvNoMessages.setVisibility(View.VISIBLE);
                    } else {
                        rvMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
                    }
                } else {
                    String err = ApiClient.getInstance(RoomActivity.this).parseError(response);
                    Toast.makeText(RoomActivity.this, err, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MessagesResponse> call, Throwable t) {
                pbLoadingHistory.setVisibility(View.GONE);
                Toast.makeText(RoomActivity.this, "Failed to load history: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSocket() {
        socketManager.setEventListener(this);
        socketManager.connect();
    }

    private void sendMessage() {
        String content = etMessageInput.getText().toString().trim();
        if (content.isEmpty()) return;

        if (content.length() > 2000) {
            Toast.makeText(this, "Message exceeds 2000 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!socketManager.isConnected()) {
            Toast.makeText(this, "Socket is connecting... Please wait", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clear input immediately for responsive UX
        etMessageInput.setText("");

        socketManager.sendMessage(roomCode, content, (ok, error) -> {
            if (!ok) {
                Toast.makeText(RoomActivity.this, "Failed to send: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStatusDisplay(String status, int colorRes) {
        tvStatusText.setText(status);
        viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, colorRes));
    }

    private void showPresenceNotice(String text) {
        tvPresenceNotice.setText(text);
        tvPresenceNotice.setVisibility(View.VISIBLE);
        presenceHandler.removeCallbacksAndMessages(null);
        presenceHandler.postDelayed(() -> tvPresenceNotice.setVisibility(View.GONE), 3500);
    }

    // --- SocketEventListener implementations ---

    @Override
    public void onConnected() {
        updateStatusDisplay("Connected", R.color.status_green);
        // Join room channel via Socket.IO
        socketManager.joinRoom(roomCode, (ok, error) -> {
            if (!ok) {
                Toast.makeText(RoomActivity.this, "Failed to join room channel: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDisconnected() {
        updateStatusDisplay("Disconnected", R.color.status_red);
    }

    @Override
    public void onConnectError(String error) {
        updateStatusDisplay("Offline", R.color.status_red);
    }

    @Override
    public void onNewMessage(Message message) {
        if (message != null && roomCode.equalsIgnoreCase(message.getCode())) {
            tvNoMessages.setVisibility(View.GONE);
            messageAdapter.addMessage(message);
            rvMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    @Override
    public void onUserJoined(String username, String code) {
        if (roomCode.equalsIgnoreCase(code)) {
            showPresenceNotice(username + " joined the room");
        }
    }

    @Override
    public void onUserLeft(String username, String code) {
        if (roomCode.equalsIgnoreCase(code)) {
            showPresenceNotice(username + " left the room");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        socketManager.disconnect();
        presenceHandler.removeCallbacksAndMessages(null);
    }
}
