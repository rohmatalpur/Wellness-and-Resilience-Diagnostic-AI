package com.example.warda_therapist;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

public class login extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 123;

    private EditText Email, Password;
    private Button btnSignIn;
    private TextView ForgetPassword, tvSignUp;
    private ImageView btnShowPassword, btnGoogleSignIn;
    private ApiService apiService;
    private GoogleSignInClient googleSignInClient;
    private boolean isPasswordVisible = false;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Initialize UI elements
        Email = findViewById(R.id.Email);
        Password = findViewById(R.id.Password);
        btnSignIn = findViewById(R.id.btnSignIn);
        ForgetPassword = findViewById(R.id.ForgetPassword);
        tvSignUp = findViewById(R.id.tvSignUp);
        btnShowPassword = findViewById(R.id.btnShowPassword);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);

        // Initialize API service
        apiService = new ApiService(this);

        // Initialize SharedPreferences
        preferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

        // Configure Google Sign-In
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Google Sign-In: " + e.getMessage());
            // Disable Google Sign-In button if there's an error
            if (btnGoogleSignIn != null) {
                btnGoogleSignIn.setEnabled(false);
            }
        }

        // Set up password visibility toggle
        if (btnShowPassword != null) {
            btnShowPassword.setOnClickListener(v -> togglePasswordVisibility());
        }

        // Sign-in button click listener
        if (btnSignIn != null) {
            btnSignIn.setOnClickListener(v -> validateLogin());
        }

        // Forgot password click listener
        if (ForgetPassword != null) {
            ForgetPassword.setOnClickListener(v -> navigateToForgotPassword());
        }

        // Sign up click listener
        if (tvSignUp != null) {
            tvSignUp.setOnClickListener(v -> navigateToSignUp());
        }

        // Google Sign-In click listener
        if (btnGoogleSignIn != null) {
            btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        }
    }

    private void signInWithGoogle() {
        try {
            Log.d(TAG, "Starting Google Sign-In");

            // Default sign-in configuration - no ID token needed for basic sign-in
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build();

            googleSignInClient = GoogleSignIn.getClient(this, gso);
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        } catch (Exception e) {
            Log.e(TAG, "Error launching Google Sign-In: " + e.getMessage(), e);
            Toast.makeText(this, "Google Sign-In setup error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Improve the error handling in onActivityResult
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            try {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                Log.d(TAG, "Google Sign-In result received");

                if (task.isSuccessful()) {
                    handleGoogleSignInResult(task);
                } else {
                    Exception e = task.getException();
                    Log.e(TAG, "Google Sign-In failed: " + (e != null ? e.getMessage() : "Unknown error"), e);
                    Toast.makeText(this, "Google Sign-In failed: " + (e != null ? e.getMessage() : "Unknown error"), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in Google Sign-In result: " + e.getMessage(), e);
                Toast.makeText(this, "Google Sign-In processing error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);

            // Get account info
            String email = account.getEmail();
            String name = account.getDisplayName();

            if (email == null || name == null) {
                Toast.makeText(this, "Could not get user info from Google", Toast.LENGTH_SHORT).show();
                return;
            }

            // Register this Google user with your backend
            apiService.register(name, email, "Google User", "google_auth_user", new ApiService.AuthCallback() {
                @Override
                public void onSuccess(int userId, String name, String email) {
                    // Save the REAL user ID returned from the server
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("user_id", userId);
                    editor.putString("name", name);
                    editor.putString("email", email);
                    editor.apply();

                    Toast.makeText(login.this, "Signed in as " + name, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(login.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(login.this, "Error registering with server: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });

        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            Toast.makeText(login.this, "Google Sign In failed: " + e.getStatusMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in Google Sign-In: " + e.getMessage());
            Toast.makeText(login.this, "Sign In error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePasswordVisibility() {
        try {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                // Show password
                Password.setTransformationMethod(null);
                btnShowPassword.setImageResource(R.drawable.ic_visibility_off);
            } else {
                // Hide password
                Password.setTransformationMethod(new PasswordTransformationMethod());
                btnShowPassword.setImageResource(R.drawable.ic_visibility);
            }
            // Move cursor to the end
            Password.setSelection(Password.getText().length());
        } catch (Exception e) {
            Log.e(TAG, "Error toggling password visibility: " + e.getMessage());
        }
    }

    private void validateLogin() {
        String email = Email.getText().toString().trim();
        String password = Password.getText().toString().trim();

        // Check if fields are empty
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and Password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        btnSignIn.setEnabled(false);
        btnSignIn.setText("Signing in...");

        // Call API to log in user
        apiService.login(email, password, new ApiService.AuthCallback() {
            @Override
            public void onSuccess(int userId, String name, String email) {
                runOnUiThread(() -> {
                    try {
                        Toast.makeText(login.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(login.this, MainActivity.class);
                        startActivity(intent);
                        finish(); // Close login activity
                    } catch (Exception e) {
                        Log.e(TAG, "Error navigating after login: " + e.getMessage());
                        Toast.makeText(login.this, "Error after login: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    try {
                        Toast.makeText(login.this, errorMessage, Toast.LENGTH_SHORT).show();
                        btnSignIn.setEnabled(true);
                        btnSignIn.setText("Sign In");
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling login failure: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void navigateToForgotPassword() {
        try {
            Intent intent = new Intent(login.this, ForgotPasswordActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to forgot password: " + e.getMessage());
            Toast.makeText(this, "Navigation error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToSignUp() {
        try {
            Intent intent = new Intent(login.this, signup.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to signup: " + e.getMessage());
            Toast.makeText(this, "Navigation error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}