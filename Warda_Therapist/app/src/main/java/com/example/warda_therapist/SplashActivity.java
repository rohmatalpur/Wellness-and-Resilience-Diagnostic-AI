package com.example.warda_therapist;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        // Hide Action Bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize SharedPreferences
        SharedPreferences preferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

        // Use Handler with Looper to avoid deprecation warning
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Check if user is already logged in
            if (isUserLoggedIn(preferences)) {
                // User is logged in, go to main activity
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            } else {
                // User is not logged in, go to login activity
                startActivity(new Intent(SplashActivity.this, login.class));
            }
            finish(); // Closes SplashActivity
        }, 2000); // 2-second delay
    }

    /**
     * Check if user is already logged in by checking if user_id exists in preferences
     * @param preferences SharedPreferences instance
     * @return true if user is logged in, false otherwise
     */
    private boolean isUserLoggedIn(SharedPreferences preferences) {
        return preferences.contains("user_id");
    }
}