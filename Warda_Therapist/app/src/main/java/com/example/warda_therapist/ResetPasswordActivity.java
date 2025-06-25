package com.example.warda_therapist;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText ForgetNewPassword, ForgetConfirmPassword;
    private ImageView btnShowPassword, btnShowConfirmPassword;
    private ImageView btnBack;
    private Button btnSetPassword;
    private String userEmail;
    private ApiService apiService;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        // Retrieve email from intent
        userEmail = getIntent().getStringExtra("user_email");
        if (userEmail == null || userEmail.isEmpty()) {
            Toast.makeText(this, "Email not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize API service
        apiService = new ApiService(this);

        // Initialize views
        ForgetNewPassword = findViewById(R.id.ForgetNewPassword);
        ForgetConfirmPassword = findViewById(R.id.ForgetConfirmPassword);
        btnShowPassword = findViewById(R.id.btnShowPassword);
        btnShowConfirmPassword = findViewById(R.id.btnShowConfirmPassword);
        btnSetPassword = findViewById(R.id.btnSetPassword);
        btnBack = findViewById(R.id.btnBack);

        // Set up back button
        btnBack.setOnClickListener(v -> onBackPressed());

        // Set up password visibility toggles
        btnShowPassword.setOnClickListener(v -> togglePasswordVisibility(ForgetNewPassword, btnShowPassword, true));
        btnShowConfirmPassword.setOnClickListener(v -> togglePasswordVisibility(ForgetConfirmPassword, btnShowConfirmPassword, false));

        // Set up reset password button
        btnSetPassword.setOnClickListener(v -> resetPassword());
    }

    private void togglePasswordVisibility(EditText editText, ImageView toggleButton, boolean isMainPassword) {
        boolean isVisible;
        if (isMainPassword) {
            isPasswordVisible = !isPasswordVisible;
            isVisible = isPasswordVisible;
        } else {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            isVisible = isConfirmPasswordVisible;
        }

        if (isVisible) {
            // Show password
            editText.setTransformationMethod(null);
            toggleButton.setImageResource(R.drawable.ic_visibility_off);
        } else {
            // Hide password
            editText.setTransformationMethod(new PasswordTransformationMethod());
            toggleButton.setImageResource(R.drawable.ic_visibility);
        }
        // Move cursor to the end
        editText.setSelection(editText.getText().length());
    }

    private void resetPassword() {
        String newPassword = ForgetNewPassword.getText().toString().trim();
        String confirmPassword = ForgetConfirmPassword.getText().toString().trim();

        // Check if fields are empty
        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if passwords match
        if (!newPassword.equals(confirmPassword)) {
            ForgetConfirmPassword.setError("Passwords do not match");
            return;
        }

        // Show loading state
        btnSetPassword.setEnabled(false);
        btnSetPassword.setText("Updating password...");

        // Call API to reset password
        apiService.resetPassword(userEmail, newPassword, new ApiService.ChatCallback() {
            @Override
            public void onResponse(String response) {
                runOnUiThread(() -> {
                    // Navigate to success screen
                    Intent intent = new Intent(ResetPasswordActivity.this, PasswordResetSuccessActivity.class);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(ResetPasswordActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    btnSetPassword.setEnabled(true);
                    btnSetPassword.setText("Reset Password");
                });
            }
        });
    }
}