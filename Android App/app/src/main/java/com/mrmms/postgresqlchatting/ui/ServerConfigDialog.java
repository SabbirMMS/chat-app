package com.mrmms.postgresqlchatting.ui;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.mrmms.postgresqlchatting.R;
import com.mrmms.postgresqlchatting.util.PrefsManager;

public class ServerConfigDialog extends Dialog {

    public interface OnServerUrlChangedListener {
        void onServerUrlChanged(String newBaseUrl);
    }

    public ServerConfigDialog(@NonNull Context context, OnServerUrlChangedListener listener) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_server_config, null);
        setContentView(view);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etServerUrl = view.findViewById(R.id.etServerUrl);
        Button btnReset = view.findViewById(R.id.btnResetDefault);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSave);

        PrefsManager prefs = PrefsManager.getInstance(context);
        etServerUrl.setText(prefs.getBaseUrl());
        etServerUrl.setSelection(etServerUrl.getText().length());

        btnReset.setOnClickListener(v -> {
            etServerUrl.setText(PrefsManager.DEFAULT_BASE_URL);
            etServerUrl.setSelection(etServerUrl.getText().length());
        });

        btnCancel.setOnClickListener(v -> dismiss());

        btnSave.setOnClickListener(v -> {
            String input = etServerUrl.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(context, "Server URL cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.setBaseUrl(input);
            String savedUrl = prefs.getBaseUrl();
            Toast.makeText(context, "Server URL updated to: " + savedUrl, Toast.LENGTH_SHORT).show();

            if (listener != null) {
                listener.onServerUrlChanged(savedUrl);
            }
            dismiss();
        });
    }
}
