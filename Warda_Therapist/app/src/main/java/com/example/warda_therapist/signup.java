package com.example.warda_therapist;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class signup extends AppCompatActivity {
    private static final String TAG = "SignupActivity";

    private EditText FullName, Email, Password, etDob, etCountry;
    private Button btnSignUp;
    private ImageView btnBack, btnCalendar, btnShowPassword;
    private CheckBox cbTerms;
    private TextView tvTerms;
    private ApiService apiService;
    private Calendar calendar;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            EdgeToEdge.enable(this);
            setContentView(R.layout.signup);
            Log.d(TAG, "Signup activity onCreate started");

            // Initialize UI components
            FullName = findViewById(R.id.FullName);
            Email = findViewById(R.id.Email);
            Password = findViewById(R.id.Password);
            etDob = findViewById(R.id.etDob);
            etCountry = findViewById(R.id.etCountry);
            btnSignUp = findViewById(R.id.btnSignUp);
            btnBack = findViewById(R.id.btnBack);
            btnCalendar = findViewById(R.id.btnCalendar);
            btnShowPassword = findViewById(R.id.btnShowPassword);
            cbTerms = findViewById(R.id.cbTerms);
            tvTerms = findViewById(R.id.tvTerms);

            // Check if all views were found
            boolean allViewsFound = FullName != null && Email != null && Password != null &&
                    etDob != null && etCountry != null && btnSignUp != null &&
                    btnBack != null && btnCalendar != null && btnShowPassword != null &&
                    cbTerms != null && tvTerms != null;

            if (!allViewsFound) {
                Log.e(TAG, "Some views not found in layout");
                Toast.makeText(this, "Error initializing UI components", Toast.LENGTH_LONG).show();
                // Don't return, continue with available components
            }

            // Initialize calendar
            calendar = Calendar.getInstance();

            // Initialize API service
            apiService = new ApiService(this);

            // Set up back button
            if (btnBack != null) {
                btnBack.setOnClickListener(v -> onBackPressed());
            }

            // Set up date picker
            DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
                @Override
                public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateLabel();
                }
            };

            if (btnCalendar != null) {
                btnCalendar.setOnClickListener(v -> {
                    try {
                        new DatePickerDialog(signup.this, dateSetListener,
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Error showing date picker: " + e.getMessage(), e);
                        Toast.makeText(signup.this, "Error with date selection", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if (etDob != null) {
                etDob.setOnClickListener(v -> {
                    try {
                        new DatePickerDialog(signup.this, dateSetListener,
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Error showing date picker: " + e.getMessage(), e);
                        Toast.makeText(signup.this, "Error with date selection", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Set up terms and conditions clickable text
            if (tvTerms != null) {
                tvTerms.setOnClickListener(v -> {
                    // Show terms and conditions (would be implemented in a real app)
                    Toast.makeText(signup.this, "Terms and Conditions", Toast.LENGTH_SHORT).show();
                });
            }

            // Set up password visibility toggle
            if (btnShowPassword != null) {
                btnShowPassword.setOnClickListener(v -> togglePasswordVisibility());
            }

            // Set click listener for Sign Up button
            if (btnSignUp != null) {
                btnSignUp.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        validateAndRegisterUser();
                    }
                });
            }

            Log.d(TAG, "Signup activity onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing signup screen: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateDateLabel() {
        try {
            if (etDob != null) {
                String format = "MM/dd/yyyy";
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                etDob.setText(sdf.format(calendar.getTime()));
                Log.d(TAG, "Date updated: " + sdf.format(calendar.getTime()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating date label: " + e.getMessage(), e);
        }
    }

    private void togglePasswordVisibility() {
        try {
            if (Password != null && btnShowPassword != null) {
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
                Log.d(TAG, "Password visibility toggled: " + isPasswordVisible);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling password visibility: " + e.getMessage(), e);
        }
    }

    private void validateAndRegisterUser() {
        try {
            // Check if UI components exist
            if (FullName == null || Email == null || Password == null ||
                    etDob == null || etCountry == null || cbTerms == null) {
                Log.e(TAG, "Cannot validate form - UI components are null");
                Toast.makeText(this, "UI error. Please restart the app.", Toast.LENGTH_SHORT).show();
                return;
            }

            String name = FullName.getText().toString().trim();
            String email = Email.getText().toString().trim();
            String dob = etDob.getText().toString().trim();
            String country = etCountry.getText().toString().trim();
            String password = Password.getText().toString().trim();

            // Check if fields are empty
            if (name.isEmpty() || email.isEmpty() || dob.isEmpty() || country.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Validation failed - empty fields");
                return;
            }

            // Check if email is valid
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Email.setError("Invalid email format");
                Log.d(TAG, "Validation failed - invalid email format");
                return;
            }

            // Check if terms are accepted
            if (!cbTerms.isChecked()) {
                Toast.makeText(this, "Please accept the Terms and Conditions", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Validation failed - terms not accepted");
                return;
            }

            // Show loading state
            btnSignUp.setEnabled(false);
            btnSignUp.setText("Creating account...");
            Log.d(TAG, "Form validated successfully, attempting to register user");

            // Call API to register user
            // Note: Your current API doesn't support DOB and country, so we'll just use phone field for now
            apiService.register(name, email, country, password, new ApiService.AuthCallback() {
                @Override
                public void onSuccess(int userId, String name, String email) {
                    Log.d(TAG, "User registered successfully with ID: " + userId);
                    runOnUiThread(() -> {
                        try {
                            Toast.makeText(signup.this, "Signup successful!", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(signup.this, login.class);
                            startActivity(intent);
                            finish(); // Close signup activity
                        } catch (Exception e) {
                            Log.e(TAG, "Error navigating after successful registration: " + e.getMessage(), e);
                            Toast.makeText(signup.this, "Error after registration: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Registration failed: " + errorMessage);
                    runOnUiThread(() -> {
                        try {
                            Toast.makeText(signup.this, errorMessage, Toast.LENGTH_SHORT).show();
                            btnSignUp.setEnabled(true);
                            btnSignUp.setText("Create account");
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating UI after registration failure: " + e.getMessage(), e);
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in validateAndRegisterUser: " + e.getMessage(), e);
            Toast.makeText(this, "Error during registration: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            btnSignUp.setEnabled(true);
            btnSignUp.setText("Create account");
        }
    }
}