package com.mrmms.postgresqlchatting.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {
    private static final String PREF_NAME = "chat_app_prefs";
    private static final String KEY_BASE_URL = "key_base_url";
    private static final String KEY_TOKEN = "key_jwt_token";
    private static final String KEY_USER_ID = "key_user_id";
    private static final String KEY_USERNAME = "key_username";

    // 10.0.2.2 is localhost from Android emulator. For real device or tunnel, user can edit.
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:3000";

    private static PrefsManager instance;
    private final SharedPreferences prefs;

    private PrefsManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized PrefsManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrefsManager(context);
        }
        return instance;
    }

    public String getBaseUrl() {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    public void setBaseUrl(String url) {
        String cleanUrl = normalizeUrl(url);
        prefs.edit().putString(KEY_BASE_URL, cleanUrl).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public void setToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public void saveUser(String id, String username) {
        prefs.edit()
                .putString(KEY_USER_ID, id)
                .putString(KEY_USERNAME, username)
                .apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null && !getToken().trim().isEmpty();
    }

    public void clearAuth() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .apply();
    }

    public static String normalizeUrl(String url) {
        if (url == null) return DEFAULT_BASE_URL;
        String clean = url.trim();
        if (clean.isEmpty()) return DEFAULT_BASE_URL;

        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://" + clean;
        }

        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }
}
