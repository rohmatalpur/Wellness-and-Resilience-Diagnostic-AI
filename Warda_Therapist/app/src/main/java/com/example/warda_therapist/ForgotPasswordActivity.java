package com.example.warda_therapist;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Patterns;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail;
    private Button btnNext;
    private ImageView btnBack;
    private ImageView ivCheckmark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize views
        etEmail = findViewById(R.id.etEmail);
        btnNext = findViewById(R.id.btnNext);
        btnBack = findViewById(R.id.btnBack);
        ivCheckmark = findViewById(R.id.ivCheckmark);

        // Set initial visibility of checkmark (hidden until valid email)
        ivCheckmark.setVisibility(View.GONE);

        // Set up email validation with checkmark
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not needed
            }

            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString().trim();
                boolean isValid = isValidEmail(email);
                ivCheckmark.setVisibility(isValid ? View.VISIBLE : View.GONE);
            }
        });

        // Set up back button
        btnBack.setOnClickListener(v -> onBackPressed());

        // Set up next button
        btnNext.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(ForgotPasswordActivity.this, "Please enter your email", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidEmail(email)) {
                Toast.makeText(ForgotPasswordActivity.this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
                return;
            }

            // In a real implementation, you would send a verification code to the email
            // For demo purposes, we'll proceed to the reset password screen
            Intent intent = new Intent(ForgotPasswordActivity.this, ResetPasswordActivity.class);
            intent.putExtra("user_email", email);
            startActivity(intent);
        });
    }

    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
}