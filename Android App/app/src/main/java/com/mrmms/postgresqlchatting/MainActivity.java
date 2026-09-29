package com.mrmms.postgresqlchatting;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.mrmms.postgresqlchatting.ui.DashboardActivity;
import com.mrmms.postgresqlchatting.ui.LoginActivity;
import com.mrmms.postgresqlchatting.util.PrefsManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PrefsManager prefs = PrefsManager.getInstance(this);

        if (prefs.isLoggedIn()) {
            Intent intent = new Intent(this, DashboardActivity.class);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }
        finish();
    }
}