package com.example.warda_therapist;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class PasswordResetSuccessActivity extends AppCompatActivity {

    private ImageView btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_reset_success);

        // Initialize back button
        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> navigateToLogin());

        // Automatically navigate to login screen after a delay
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToLogin, 2000);
    }

    private void navigateToLogin() {
        Intent intent = new Intent(PasswordResetSuccessActivity.this, login.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}