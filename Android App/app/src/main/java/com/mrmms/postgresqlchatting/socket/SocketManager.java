package com.mrmms.postgresqlchatting.socket;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.mrmms.postgresqlchatting.network.models.Message;
import com.mrmms.postgresqlchatting.util.PrefsManager;

import org.json.JSONObject;

import java.net.URI;
import java.util.Collections;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;

public class SocketManager {
    private static final String TAG = "SocketManager";
    private static SocketManager instance;
    private final Context appContext;
    private Socket socket;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public interface SocketEventListener {
        void onConnected();
        void onDisconnected();
        void onConnectError(String error);
        void onNewMessage(Message message);
        void onUserJoined(String username, String code);
        void onUserLeft(String username, String code);
    }

    public interface AckResultListener {
        void onResult(boolean ok, String error);
    }

    private SocketEventListener eventListener;

    private SocketManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized SocketManager getInstance(Context context) {
        if (instance == null) {
            instance = new SocketManager(context);
        }
        return instance;
    }

    public void setEventListener(SocketEventListener listener) {
        this.eventListener = listener;
    }

    public synchronized void connect() {
        disconnect();

        String baseUrl = PrefsManager.getInstance(appContext).getBaseUrl();
        String token = PrefsManager.getInstance(appContext).getToken();

        try {
            IO.Options options = IO.Options.builder()
                    .setPath("/socket.io")
                    .setAuth(Collections.singletonMap("token", token != null ? token : ""))
                    .setTransports(new String[]{"websocket", "polling"})
                    .setReconnection(true)
                    .setReconnectionAttempts(5)
                    .setReconnectionDelay(1000)
                    .setTimeout(20000)
                    .build();

            socket = IO.socket(URI.create(baseUrl), options);

            socket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG, "Socket connected to " + baseUrl);
                mainHandler.post(() -> {
                    if (eventListener != null) eventListener.onConnected();
                });
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                Log.d(TAG, "Socket disconnected");
                mainHandler.post(() -> {
                    if (eventListener != null) eventListener.onDisconnected();
                });
            });

            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                String errMsg = (args != null && args.length > 0 && args[0] != null)
                        ? args[0].toString()
                        : "Connection error";
                Log.e(TAG, "Socket connect_error: " + errMsg);
                mainHandler.post(() -> {
                    if (eventListener != null) eventListener.onConnectError(errMsg);
                });
            });

            socket.on("new_message", args -> {
                if (args != null && args.length > 0 && args[0] instanceof JSONObject) {
                    JSONObject obj = (JSONObject) args[0];
                    Message msg = gson.fromJson(obj.toString(), Message.class);
                    mainHandler.post(() -> {
                        if (eventListener != null) eventListener.onNewMessage(msg);
                    });
                }
            });

            socket.on("user_joined", args -> {
                if (args != null && args.length > 0 && args[0] instanceof JSONObject) {
                    JSONObject obj = (JSONObject) args[0];
                    String code = obj.optString("code");
                    JSONObject userObj = obj.optJSONObject("user");
                    String username = userObj != null ? userObj.optString("username") : "Someone";
                    mainHandler.post(() -> {
                        if (eventListener != null) eventListener.onUserJoined(username, code);
                    });
                }
            });

            socket.on("user_left", args -> {
                if (args != null && args.length > 0 && args[0] instanceof JSONObject) {
                    JSONObject obj = (JSONObject) args[0];
                    String code = obj.optString("code");
                    JSONObject userObj = obj.optJSONObject("user");
                    String username = userObj != null ? userObj.optString("username") : "Someone";
                    mainHandler.post(() -> {
                        if (eventListener != null) eventListener.onUserLeft(username, code);
                    });
                }
            });

            socket.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize socket", e);
            if (eventListener != null) {
                eventListener.onConnectError(e.getMessage());
            }
        }
    }

    public synchronized void joinRoom(String code, AckResultListener callback) {
        if (socket == null || !socket.connected()) {
            if (callback != null) callback.onResult(false, "Socket is not connected");
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("code", code);

            socket.emit("join_room", data, (Ack) args -> {
                boolean ok = false;
                String error = "Unknown error";
                if (args != null && args.length > 0 && args[0] instanceof JSONObject) {
                    JSONObject res = (JSONObject) args[0];
                    ok = res.optBoolean("ok", false);
                    error = res.optString("error", "Failed to join room");
                }
                boolean finalOk = ok;
                String finalError = error;
                mainHandler.post(() -> {
                    if (callback != null) callback.onResult(finalOk, finalOk ? null : finalError);
                });
            });
        } catch (Exception e) {
            if (callback != null) callback.onResult(false, e.getMessage());
        }
    }

    public synchronized void sendMessage(String code, String content, AckResultListener callback) {
        if (socket == null || !socket.connected()) {
            if (callback != null) callback.onResult(false, "Socket is not connected");
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("code", code);
            data.put("content", content);

            socket.emit("send_message", data, (Ack) args -> {
                boolean ok = false;
                String error = "Unknown error";
                if (args != null && args.length > 0 && args[0] instanceof JSONObject) {
                    JSONObject res = (JSONObject) args[0];
                    ok = res.optBoolean("ok", false);
                    error = res.optString("error", "Failed to send message");
                }
                boolean finalOk = ok;
                String finalError = error;
                mainHandler.post(() -> {
                    if (callback != null) callback.onResult(finalOk, finalOk ? null : finalError);
                });
            });
        } catch (Exception e) {
            if (callback != null) callback.onResult(false, e.getMessage());
        }
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public synchronized void disconnect() {
        if (socket != null) {
            socket.off();
            socket.disconnect();
            socket = null;
        }
    }
}
