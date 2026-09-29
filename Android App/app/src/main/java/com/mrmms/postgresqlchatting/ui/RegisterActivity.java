package com.mrmms.postgresqlchatting.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mrmms.postgresqlchatting.R;
import com.mrmms.postgresqlchatting.network.ApiClient;
import com.mrmms.postgresqlchatting.network.models.AuthRequest;
import com.mrmms.postgresqlchatting.network.models.AuthResponse;
import com.mrmms.postgresqlchatting.util.PrefsManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private Button btnRegister;
    private ProgressBar progressBar;
    private TextView tvError;
    private TextView tvCurrentBaseUrl;
    private LinearLayout layoutServerConfig;
    private TextView tvGoLogin;

    private PrefsManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        prefs = PrefsManager.getInstance(this);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);
        tvCurrentBaseUrl = findViewById(R.id.tvCurrentBaseUrl);
        layoutServerConfig = findViewById(R.id.layoutServerConfig);
        tvGoLogin = findViewById(R.id.tvGoLogin);

        updateServerUrlDisplay();

        layoutServerConfig.setOnClickListener(v -> {
            new ServerConfigDialog(this, newUrl -> updateServerUrlDisplay()).show();
        });

        btnRegister.setOnClickListener(v -> attemptRegister());

        tvGoLogin.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServerUrlDisplay();
    }

    private void updateServerUrlDisplay() {
        tvCurrentBaseUrl.setText(prefs.getBaseUrl());
    }

    private void attemptRegister() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        tvError.setVisibility(View.GONE);

        if (username.length() < 3) {
            showError("Username must be at least 3 characters");
            return;
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showError("Username can only contain letters, numbers, and underscores");
            return;
        }

        if (password.length() < 6) {
            showError("Password must be at least 6 characters");
            return;
        }

        setLoading(true);

        AuthRequest request = new AuthRequest(username, password);
        ApiClient.getInstance(this).getApi().register(request).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse auth = response.body();
                    prefs.setToken(auth.getToken());
                    if (auth.getUser() != null) {
                        prefs.saveUser(auth.getUser().getId(), auth.getUser().getUsername());
                    }

                    Toast.makeText(RegisterActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(RegisterActivity.this, DashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    String msg = ApiClient.getInstance(RegisterActivity.this).parseError(response);
                    showError(msg);
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                showError("Connection failed: " + t.getMessage());
            }
        });
    }

    private void setLoading(boolean loading) {
        btnRegister.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        etUsername.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
}
