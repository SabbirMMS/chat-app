package com.mrmms.postgresqlchatting.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import com.mrmms.postgresqlchatting.network.models.User;
import com.mrmms.postgresqlchatting.socket.SocketManager;
import com.mrmms.postgresqlchatting.ui.adapters.MessageAdapter;
import com.mrmms.postgresqlchatting.util.PrefsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RoomActivity extends AppCompatActivity implements SocketManager.SocketEventListener {

    public static final String EXTRA_ROOM_CODE = "EXTRA_ROOM_CODE";

    private ImageButton btnBack;
    private TextView tvRoomCode;
    private ImageButton btnCopyRoomCode;
    private LinearLayout layoutMembersPill;
    private TextView tvOnlineCount;
    private View viewStatusDot;
    private TextView tvStatusText;
    private TextView tvPresenceNotice;
    private TextView tvTypingIndicator;

    private RecyclerView rvMessages;
    private ProgressBar pbLoadingHistory;
    private TextView tvNoMessages;

    private EditText etMessageInput;
    private ImageButton btnSend;

    private String roomCode;
    private MessageAdapter messageAdapter;
    private SocketManager socketManager;
    private PrefsManager prefs;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler presenceHandler = new Handler(Looper.getMainLooper());
    private final Handler typingDebounceHandler = new Handler(Looper.getMainLooper());

    private boolean isTyping = false;
    private final Set<String> activeTypingUsers = new HashSet<>();
    private final List<User> currentOnlineMembers = new ArrayList<>();

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
        layoutMembersPill = findViewById(R.id.layoutMembersPill);
        tvOnlineCount = findViewById(R.id.tvOnlineCount);
        viewStatusDot = findViewById(R.id.viewStatusDot);
        tvStatusText = findViewById(R.id.tvStatusText);
        tvPresenceNotice = findViewById(R.id.tvPresenceNotice);
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator);

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

        // Online members list dialog
        layoutMembersPill.setOnClickListener(v -> showOnlineMembersDialog());

        // Typing ping detection with 2.5s debounce
        etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!socketManager.isConnected()) return;

                if (!isTyping && s != null && s.toString().trim().length() > 0) {
                    isTyping = true;
                    socketManager.sendTyping(roomCode, true);
                }

                typingDebounceHandler.removeCallbacksAndMessages(null);
                typingDebounceHandler.postDelayed(() -> {
                    if (isTyping) {
                        isTyping = false;
                        socketManager.sendTyping(roomCode, false);
                    }
                }, 2500);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
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
                    // Mark messages seen
                    if (socketManager.isConnected()) {
                        socketManager.markAllSeen(roomCode);
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

        // Cancel typing status immediately
        typingDebounceHandler.removeCallbacksAndMessages(null);
        isTyping = false;
        socketManager.sendTyping(roomCode, false);

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

    private void showOnlineMembersDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Active Members in #" + roomCode);

        if (currentOnlineMembers.isEmpty()) {
            builder.setMessage("No active members found");
        } else {
            String[] names = new String[currentOnlineMembers.size()];
            String myId = prefs.getUserId();
            for (int i = 0; i < currentOnlineMembers.size(); i++) {
                User u = currentOnlineMembers.get(i);
                boolean isMe = u.getId() != null && u.getId().equals(myId);
                names[i] = "@" + u.getUsername() + (isMe ? " (You)" : "");
            }
            builder.setItems(names, null);
        }
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    // --- SocketEventListener implementations ---

    @Override
    public void onConnected() {
        updateStatusDisplay("Connected", R.color.status_green);
        socketManager.joinRoom(roomCode, (ok, error) -> {
            if (!ok) {
                Toast.makeText(RoomActivity.this, "Failed to join room channel: " + error, Toast.LENGTH_SHORT).show();
            } else {
                // Mark all seen upon joining
                socketManager.markAllSeen(roomCode);
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

            // Mark incoming message as seen
            String myId = prefs.getUserId();
            if (message.getSender() != null && !message.getSender().getId().equals(myId)) {
                socketManager.markSeen(roomCode, message.getId());
            }
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
            activeTypingUsers.remove(username);
            updateTypingUI();
        }
    }

    @Override
    public void onUserTyping(String username, boolean isTyping, String code) {
        if (roomCode.equalsIgnoreCase(code)) {
            String myUsername = prefs.getUsername();
            if (myUsername != null && myUsername.equalsIgnoreCase(username)) {
                return; // ignore self typing
            }
            if (isTyping) {
                activeTypingUsers.add(username);
            } else {
                activeTypingUsers.remove(username);
            }
            updateTypingUI();
        }
    }

    private void updateTypingUI() {
        if (activeTypingUsers.isEmpty()) {
            tvTypingIndicator.setVisibility(View.GONE);
        } else {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (String u : activeTypingUsers) {
                if (i > 0) sb.append(", ");
                sb.append(u);
                i++;
            }
            sb.append(activeTypingUsers.size() > 1 ? " are typing..." : " is typing...");
            tvTypingIndicator.setText(sb.toString());
            tvTypingIndicator.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onMessageSeen(String messageId, User user, String code) {
        if (roomCode.equalsIgnoreCase(code)) {
            messageAdapter.markMessageSeen(messageId, user);
        }
    }

    @Override
    public void onRoomMessagesSeen(User user, String code) {
        if (roomCode.equalsIgnoreCase(code)) {
            messageAdapter.markAllSeenByUser(user);
        }
    }

    @Override
    public void onRoomMembersUpdated(List<User> members, String code) {
        if (roomCode.equalsIgnoreCase(code) && members != null) {
            currentOnlineMembers.clear();
            currentOnlineMembers.addAll(members);
            tvOnlineCount.setText(members.size() + " online");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isTyping) {
            socketManager.sendTyping(roomCode, false);
        }
        socketManager.disconnect();
        presenceHandler.removeCallbacksAndMessages(null);
        typingDebounceHandler.removeCallbacksAndMessages(null);
    }
}
