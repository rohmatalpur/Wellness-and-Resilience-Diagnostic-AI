package com.example.warda_therapist;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private RecyclerView recyclerView;
    private EditText message;
    private ImageView send;
    private ImageView btnMenu;
    private View dashboardMenuItem;
    private LinearLayout dashboardSubmenu;
    private ImageView ivExpandDashboard;
    private TextView tvChatTitle;

    private List<MessageModel> list;
    private MessageAdapter adapter;
    private ApiService apiService;
    private SharedPreferences preferences;

    // Added for session management
    private LinearLayout sessionsContainer;
    private View currentSessionItem;
    private TextView tvCurrentSessionTitle;
    private Button btnEndSession;
    private List<ApiService.SessionData> sessionsList = new ArrayList<>();
    private boolean isInActiveSession = false;

    // Added for emotional state tracking
    private EmotionalStateView emotionalStateView;
    private TextView tvEmotionalState;
    private TextView tvEmotionalDescription;
    private View emotionalStateCard;
    private String currentEmotionalState = "neutral";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            Log.d(TAG, "MainActivity onCreate started");

            // Initialize DrawerLayout
            drawerLayout = findViewById(R.id.drawerLayout);
            navigationView = findViewById(R.id.navView);

            // Check if navigationView is properly initialized
            if (navigationView != null) {
                // Don't inflate the menu since we're using a custom layout
                Log.d(TAG, "Navigation view found");
            } else {
                Log.e(TAG, "Navigation view not found in layout");
            }

            // Setup toolbar elements
            btnMenu = findViewById(R.id.btnMenu);
            tvChatTitle = findViewById(R.id.tvChatTitle);

            if (btnMenu != null) {
                btnMenu.setOnClickListener(v -> openDrawer());
                Log.d(TAG, "Menu button set up successfully");
            } else {
                Log.e(TAG, "Menu button not found in layout");
            }

            // Initialize chat UI components
            recyclerView = findViewById(R.id.recyclerView);
            message = findViewById(R.id.message);
            send = findViewById(R.id.send);

            // Check if all UI components are found
            if (recyclerView == null || message == null || send == null) {
                Log.e(TAG, "One or more critical UI components not found");
                Toast.makeText(this, "Error initializing UI components", Toast.LENGTH_LONG).show();
                // Don't return here, try to continue with available components
            }

            // Get user preferences
            preferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

            // Check if user is logged in
            if (!isUserLoggedIn()) {
                Log.w(TAG, "User not logged in, redirecting to login screen");
                // If not logged in, redirect to login
                Toast.makeText(this, "Not logged in. Please log in first.", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, login.class);
                startActivity(intent);
                finish();
                return;
            }

            // Setup user info in navigation drawer
            setupUserInfo();

            // Initialize API Service with context
            apiService = new ApiService(this);

            // Initialize chat list and adapter
            list = new ArrayList<>();

            if (recyclerView != null) {
                LinearLayoutManager layoutManager = new LinearLayoutManager(this);
                layoutManager.setStackFromEnd(true);
                recyclerView.setLayoutManager(layoutManager);

                adapter = new MessageAdapter(list);
                recyclerView.setAdapter(adapter);
                Log.d(TAG, "RecyclerView set up successfully");
            }

            // Check server health when activity starts
            checkServerHealth();

            // Set up logout button
            setupLogoutButton();

            // Set up sessions container in the sidebar
            setupSessionManagement();

            // Load chat history
            loadChatHistory();

            // Set up session as active by default
            isInActiveSession = true;
            updateCurrentSessionUI();

            // Set chat title to current session
            updateChatTitle("Current Session");

            // Get user name for personalized greeting
            String userName = preferences.getString("name", "");
            String greeting = !userName.isEmpty()
                    ? "Hello " + userName + "! I'm WARDA, your mental health AI assistant. How can I help you today?"
                    : "Hello! I'm WARDA, your mental health AI assistant. How can I help you today?";

            // Add welcome message
            addToChat(greeting, MessageModel.SENT_BY_BOT);

            // Set up send button click listener
            if (send != null) {
                send.setOnClickListener(v -> {
                    if (message != null) {
                        String userMessage = message.getText().toString().trim();

                        if (userMessage.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Please type a message", Toast.LENGTH_SHORT).show();
                        } else {
                            // Add user message to chat
                            addToChat(userMessage, MessageModel.SENT_BY_ME);
                            message.setText("");

                            // Show typing indicator
                            addToChat("Typing...", MessageModel.SENT_BY_BOT);

                            // Call API to get response
                            sendMessageToApi(userMessage);
                        }
                    }
                });
            }

            // Set up sidebar menu expand/collapse functionality
            setupSidebarMenu();

            // Setup emotional state tracking
            setupEmotionalStateTracking();

            Log.d(TAG, "MainActivity onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupEmotionalStateTracking() {
        try {
            // Find emotional state card view
            emotionalStateCard = findViewById(R.id.emotionalStateCard);

            if (emotionalStateCard != null) {
                emotionalStateView = emotionalStateCard.findViewById(R.id.emotionalStateView);
                tvEmotionalState = emotionalStateCard.findViewById(R.id.tvEmotionalState);
                tvEmotionalDescription = emotionalStateCard.findViewById(R.id.tvEmotionalDescription);

                // Set up click listener for viewing details
                TextView tvViewDetails = emotionalStateCard.findViewById(R.id.tvViewDetails);
                if (tvViewDetails != null) {
                    tvViewDetails.setOnClickListener(v -> openEmotionalStateDetails());
                }

                // Update emotional state from API
                updateEmotionalState();

                Log.d(TAG, "Emotional state tracking set up successfully");
            } else {
                Log.e(TAG, "Emotional state card not found in layout");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up emotional state tracking: " + e.getMessage(), e);
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private void openEmotionalStateDetails() {
        try {
            Intent intent = new Intent(MainActivity.this, EmotionalStateActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening emotional state details: " + e.getMessage(), e);
        }
    }

    private void updateEmotionalState() {
        try {
            int userId = preferences.getInt("user_id", -1);
            if (userId == -1) {
                Log.w(TAG, "Cannot update emotional state - no user ID found");
                return;
            }

            Log.d(TAG, "Updating emotional state for user ID: " + userId);
            apiService.getCurrentEmotionalState(userId, new ApiService.EmotionalStateCallback() {
                @Override
                public void onSuccess(String state, float confidence, String trend, String colorCode, String description) {
                    runOnUiThread(() -> {
                        try {
                            // Update UI components
                            currentEmotionalState = state;

                            if (emotionalStateView != null) {
                                emotionalStateView.updateState(state, confidence, trend, colorCode);
                            }

                            if (tvEmotionalState != null) {
                                tvEmotionalState.setText(capitalize(state));
                            }

                            if (tvEmotionalDescription != null) {
                                tvEmotionalDescription.setText(description);
                            }

                            Log.d(TAG, "Updated emotional state: " + state + " (confidence: " + confidence + ")");
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating emotional state UI: " + e.getMessage(), e);
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error fetching emotional state: " + errorMessage);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in updateEmotionalState: " + e.getMessage(), e);
        }
    }

    private void setupUserInfo() {
        try {
            // Find user name TextView directly instead of using the header view
            TextView tvUserName = findViewById(R.id.tvUserName);
            if (tvUserName != null) {
                String userName = preferences.getString("name", "User");
                tvUserName.setText(userName);
                Log.d(TAG, "Set user name in nav drawer: " + userName);
            } else {
                Log.e(TAG, "User name TextView not found in nav header");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up user info: " + e.getMessage(), e);
        }
    }

    private void setupLogoutButton() {
        Button btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
            Log.d(TAG, "Logout button set up successfully");
        } else {
            Log.e(TAG, "Logout button not found in layout");
        }
    }

    // Set up the sessions container in the sidebar
    private void setupSessionManagement() {
        try {
            // Find the sessions container
            sessionsContainer = findViewById(R.id.sessionsContainer);

            // Find current session elements
            currentSessionItem = findViewById(R.id.currentSessionItem);
            tvCurrentSessionTitle = findViewById(R.id.tvCurrentSessionTitle);
            btnEndSession = findViewById(R.id.btnEndSession);

            // Set up end session button
            if (btnEndSession != null) {
                btnEndSession.setOnClickListener(v -> endCurrentSession());
            }

            // Set up current session click listener
            if (currentSessionItem != null) {
                currentSessionItem.setOnClickListener(v -> {
                    if (isInActiveSession) {
                        // If we're already in the active session, just close the drawer
                        closeDrawer();
                    } else {
                        // Start new session if not already in one
                        startNewSession();
                    }
                });
            }

            // Set up main navigation items
            setupNavigationItems();

            // Update current session UI
            updateCurrentSessionUI();

            Log.d(TAG, "Session management setup completed");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up session management: " + e.getMessage(), e);
        }
    }

    private void setupNavigationItems() {
        // Dashboard
        View navDashboard = findViewById(R.id.dashboardMenuItem);
        if (navDashboard != null) {
            navDashboard.setOnClickListener(v -> {
                Toast.makeText(this, "Dashboard selected", Toast.LENGTH_SHORT).show();
                closeDrawer();
            });
        }

        // Notification
        View navNotification = findViewById(R.id.notificationMenuItem);
        if (navNotification != null) {
            navNotification.setOnClickListener(v -> {
                Toast.makeText(this, "Notifications selected", Toast.LENGTH_SHORT).show();
                closeDrawer();
            });
        }
    }

    private void setupSidebarMenu() {
        try {
            // Find views in the navigation drawer
            dashboardMenuItem = findViewById(R.id.dashboardMenuItem);
            dashboardSubmenu = findViewById(R.id.dashboardSubmenu);
            ivExpandDashboard = findViewById(R.id.ivExpandDashboard);

            // Check if all views are found
            boolean allViewsFound = dashboardMenuItem != null && dashboardSubmenu != null && ivExpandDashboard != null;

            if (allViewsFound) {
                Log.d(TAG, "Sidebar menu views found, setting up functionality");
                // Initially hide submenu
                dashboardSubmenu.setVisibility(View.GONE);

                // Set click listener to expand/collapse submenu
                dashboardMenuItem.setOnClickListener(v -> {
                    if (dashboardSubmenu.getVisibility() == View.VISIBLE) {
                        dashboardSubmenu.setVisibility(View.GONE);
                        ivExpandDashboard.setImageResource(R.drawable.ic_chevron_down);
                    } else {
                        dashboardSubmenu.setVisibility(View.VISIBLE);
                        ivExpandDashboard.setImageResource(R.drawable.ic_chevron_up);
                    }
                });
            } else {
                Log.e(TAG, "Some sidebar menu views not found");
            }

            // Set up other sidebar menu items
            ImageView btnCollapse = findViewById(R.id.btnCollapse);
            if (btnCollapse != null) {
                btnCollapse.setOnClickListener(v -> closeDrawer());
                Log.d(TAG, "Collapse button set up successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up sidebar menu: " + e.getMessage(), e);
        }
    }

    private void loadChatHistory() {
        try {
            int userId = preferences.getInt("user_id", -1);
            if (userId == -1) {
                Log.w(TAG, "Cannot load chat history - no user ID found");
                return;
            }

            Log.d(TAG, "Loading session history for user ID: " + userId);
            apiService.getSessionHistory(userId, new ApiService.SessionHistoryCallback() {
                @Override
                public void onSuccess(List<ApiService.SessionData> sessions) {
                    sessionsList = sessions;
                    runOnUiThread(() -> {
                        try {
                            Log.d(TAG, "Loaded " + sessions.size() + " sessions");

                            // Update sessions in sidebar
                            updateSessionsView(sessions);
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating sessions view: " + e.getMessage(), e);
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error loading session history: " + errorMessage);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                "Couldn't load chat history: " + errorMessage,
                                Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading chat history: " + e.getMessage(), e);
        }
    }

    // Update the sessions view in the sidebar
    private void updateSessionsView(List<ApiService.SessionData> sessions) {
        try {
            if (sessionsContainer != null) {
                // Clear existing sessions
                sessionsContainer.removeAllViews();

                // No sessions yet
                if (sessions.isEmpty()) {
                    TextView emptyText = new TextView(this);
                    emptyText.setText("No previous sessions");
                    emptyText.setTextColor(getResources().getColor(android.R.color.white));
                    emptyText.setPadding(32, 16, 16, 16);
                    sessionsContainer.addView(emptyText);
                    return;
                }

                // Add each session
                LayoutInflater inflater = getLayoutInflater();

                for (int i = 0; i < sessions.size(); i++) {
                    final ApiService.SessionData session = sessions.get(i);
                    final int sessionIndex = i;

                    // Inflate a session item view
                    View sessionView = inflater.inflate(R.layout.item_session, sessionsContainer, false);

                    TextView tvSessionTitle = sessionView.findViewById(R.id.tvSessionTitle);
                    TextView tvSessionPreview = sessionView.findViewById(R.id.tvSessionPreview);

                    // Set session data - use formatted date or fallback to session title
                    String title = "Session: " + (session.getFormattedDateTime() != null ?
                            session.getFormattedDateTime() : session.getDate());
                    tvSessionTitle.setText(title);
                    tvSessionPreview.setText(session.getPreviewText());

                    // Set click listener
                    sessionView.setOnClickListener(v -> {
                        // Load this session's messages
                        loadSessionMessages(session, sessionIndex);
                        // Close drawer
                        closeDrawer();
                        // Update UI to show we're viewing a past session
                        isInActiveSession = false;
                        updateCurrentSessionUI();
                        // Update chat title
                        updateChatTitle(title);
                    });

                    // Add to container
                    sessionsContainer.addView(sessionView);
                }
            } else {
                Log.e(TAG, "Sessions container view not found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating sessions view: " + e.getMessage(), e);
        }
    }

    // Load messages from a selected session
    private void loadSessionMessages(ApiService.SessionData session, int sessionIndex) {
        try {
            // Clear current chat
            list.clear();

            // Add all messages from this session
            for (ApiService.ChatHistoryItem item : session.getMessages()) {
                list.add(new MessageModel(item.getMessage(), MessageModel.SENT_BY_ME));
                list.add(new MessageModel(item.getResponse(), MessageModel.SENT_BY_BOT));
            }

            // Notify adapter of changes
            adapter.notifyDataSetChanged();

            // Scroll to bottom
            if (recyclerView != null && adapter.getItemCount() > 0) {
                recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            }

            Log.d(TAG, "Loaded " + session.getMessages().size() + " messages from session " + sessionIndex);
        } catch (Exception e) {
            Log.e(TAG, "Error loading session messages: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading session: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Add new methods for session management
    private void startNewSession() {
        try {
            // Clear chat
            list.clear();
            adapter.notifyDataSetChanged();

            // Set active session flag
            isInActiveSession = true;

            // Update UI
            updateCurrentSessionUI();

            // Add greeting message
            String userName = preferences.getString("name", "");
            String greeting = !userName.isEmpty()
                    ? "Hello " + userName + "! I'm WARDA, your mental health AI assistant. How can I help you today?"
                    : "Hello! I'm WARDA, your mental health AI assistant. How can I help you today?";

            // Add welcome message
            addToChat(greeting, MessageModel.SENT_BY_BOT);

            // Update chat title
            updateChatTitle("Current Session");

            // Close drawer
            closeDrawer();

            Log.d(TAG, "Started new session");
        } catch (Exception e) {
            Log.e(TAG, "Error starting new session: " + e.getMessage(), e);
        }
    }

    private void endCurrentSession() {
        try {
            if (!isInActiveSession || list.isEmpty()) {
                Toast.makeText(this, "No active session to end", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show confirmation dialog
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("End Session")
                    .setMessage("Are you sure you want to end the current session?")
                    .setPositiveButton("End Session", (dialog, which) -> {
                        // Set active session flag
                        isInActiveSession = false;

                        // Update UI
                        updateCurrentSessionUI();

                        // Clear chat - the messages are already saved in the database
                        // since they're sent through the API
                        list.clear();
                        adapter.notifyDataSetChanged();

                        // Reload chat history to get the new session
                        loadChatHistory();

                        // Update chat title
                        updateChatTitle("Chat ended");

                        // Close drawer
                        closeDrawer();

                        Toast.makeText(this, "Session ended", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Ended current session");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Error ending session: " + e.getMessage(), e);
        }
    }

    private void updateCurrentSessionUI() {
        try {
            if (btnEndSession != null) {
                btnEndSession.setEnabled(isInActiveSession);
                btnEndSession.setAlpha(isInActiveSession ? 1.0f : 0.5f);
            }

            if (tvCurrentSessionTitle != null) {
                tvCurrentSessionTitle.setText(isInActiveSession ?
                        "Current Active Session" : "Start New Session");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating current session UI: " + e.getMessage(), e);
        }
    }

    private void updateChatTitle(String title) {
        try {
            if (tvChatTitle != null) {
                tvChatTitle.setText(title);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating chat title: " + e.getMessage(), e);
        }
    }

    private void openDrawer() {
        try {
            if (drawerLayout != null) {
                drawerLayout.openDrawer(GravityCompat.START);
                Log.d(TAG, "Opening navigation drawer");
            } else {
                Log.e(TAG, "Cannot open drawer - drawerLayout is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening drawer: " + e.getMessage(), e);
        }
    }

    private void closeDrawer() {
        try {
            if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
                Log.d(TAG, "Closing navigation drawer");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing drawer: " + e.getMessage(), e);
        }
    }

    private void logout() {
        try {
            Log.d(TAG, "Logging out user");

            // 1. Clear shared preferences
            SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            editor.apply();

            // 2. Sign out of Google if needed
            if (GoogleSignIn.getLastSignedInAccount(this) != null) {
                Log.d(TAG, "Signing out from Google");
                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .build();

                GoogleSignIn.getClient(this, gso)
                        .signOut()
                        .addOnCompleteListener(this, task -> {
                            Log.d(TAG, "Google Sign Out completed");
                            // Continue with app logout
                            completeLogout();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Google Sign Out failed: " + e.getMessage());
                            // Continue with app logout even if Google sign-out fails
                            completeLogout();
                        });
            } else {
                // No Google sign-in to handle
                completeLogout();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during logout: " + e.getMessage(), e);
            Toast.makeText(this, "Error during logout: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            completeLogout(); // Try to logout anyway
        }
    }

    private void completeLogout() {
        runOnUiThread(() -> {
            try {
                // 3. Show toast message
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

                // 4. Redirect to login screen
                Intent intent = new Intent(MainActivity.this, login.class);
                // Clear any previous activities in the stack
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } catch (Exception e) {
                Log.e(TAG, "Error completing logout: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void onBackPressed() {
        try {
            if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                closeDrawer();
            } else {
                super.onBackPressed();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling back press: " + e.getMessage(), e);
            super.onBackPressed(); // Fall back to default behavior
        }
    }

    private boolean isUserLoggedIn() {
        return preferences.contains("user_id");
    }

    private void sendMessageToApi(String userMessage) {
        try {
            // If not in active session, start one
            if (!isInActiveSession) {
                isInActiveSession = true;
                updateCurrentSessionUI();
                updateChatTitle("Current Session");
            }

            if (apiService == null) {
                Log.e(TAG, "Cannot send message - apiService is null");
                removeLastMessage();
                addToChat("Sorry, I'm having trouble connecting to the server. Please restart the app.", MessageModel.SENT_BY_BOT);
                return;
            }

            Log.d(TAG, "Sending message to API: " + userMessage);
            apiService.sendChatRequest(userMessage, new ApiService.ChatCallback() {
                @Override
                public void onResponse(String response) {
                    // Remove typing indicator and add actual response
                    removeLastMessage();
                    addToChat(response, MessageModel.SENT_BY_BOT);
                    Log.d(TAG, "Received response from API (length: " + response.length() + ")");

                    // Update emotional state after receiving response
                    updateEmotionalState();
                }

                @Override
                public void onError(String errorMessage) {
                    // Remove typing indicator and add error message
                    removeLastMessage();
                    addToChat("Sorry, I'm having trouble connecting to my brain. Please try again later.", MessageModel.SENT_BY_BOT);
                    Log.e(TAG, "Error from API: " + errorMessage);

                    // Show detailed error in a toast
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error sending message to API: " + e.getMessage(), e);
            // Handle exception in UI
            removeLastMessage();
            addToChat("Sorry, something went wrong. Please try again.", MessageModel.SENT_BY_BOT);
        }
    }

    private void checkServerHealth() {
        try {
            if (apiService == null) {
                Log.e(TAG, "Cannot check server health - apiService is null");
                return;
            }

            Log.d(TAG, "Checking server health");
            apiService.checkServerHealth(new ApiService.ChatCallback() {
                @Override
                public void onResponse(String response) {
                    // Server is healthy, no need to notify user
                    Log.d(TAG, "Server health check passed: " + response);
                }

                @Override
                public void onError(String errorMessage) {
                    // Notify user about server connection issue
                    Log.e(TAG, "Server health check failed: " + errorMessage);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                "Cannot connect to WARDA Therapist backend. " + errorMessage,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error checking server health: " + e.getMessage(), e);
        }
    }

    private void removeLastMessage() {
        try {
            runOnUiThread(() -> {
                if (list != null && !list.isEmpty() && adapter != null) {
                    list.remove(list.size() - 1);
                    adapter.notifyItemRemoved(list.size());
                    Log.d(TAG, "Removed last message from chat");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error removing last message: " + e.getMessage(), e);
        }
    }

    private void addToChat(String message, String sender) {
        try {
            runOnUiThread(() -> {
                if (list != null && adapter != null && recyclerView != null) {
                    list.add(new MessageModel(message, sender));
                    adapter.notifyItemInserted(list.size() - 1);
                    recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                    Log.d(TAG, "Added message to chat from " + sender);
                } else {
                    Log.e(TAG, "Cannot add message to chat - list, adapter, or recyclerView is null");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error adding message to chat: " + e.getMessage(), e);
        }
    }
}