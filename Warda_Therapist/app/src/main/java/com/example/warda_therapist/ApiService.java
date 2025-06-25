package com.example.warda_therapist;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// Add these imports if they don't exist
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ApiService {
    private static final String TAG = "ApiService";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Change this to your actual FastAPI server URL
    // For local testing with emulator, use 10.0.2.2 instead of localhost
    // For a physical device, use your computer's IP address on the local network

    // IMPORTANT: Change this to your actual server IP if needed
    private static final String BASE_URL = "http://10.0.2.2:8000";

    private final OkHttpClient client;
    private final Context context;
    private final SharedPreferences preferences;

    // Constructor with context
    public ApiService(Context context) {
        this.context = context;
        // Configure OkHttp client with appropriate timeouts
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // Initialize SharedPreferences
        preferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

        // Log the BASE_URL for debugging
        Log.d(TAG, "API Service initialized with BASE_URL: " + BASE_URL);
    }

    // Interface for chat callbacks
    public interface ChatCallback {
        void onResponse(String response);
        void onError(String errorMessage);
    }

    // Interface for authentication callbacks
    public interface AuthCallback {
        void onSuccess(int userId, String name, String email);
        void onError(String errorMessage);
    }

    // Interface for chat history callbacks
    public interface ChatHistoryCallback {
        void onSuccess(List<ChatHistoryItem> history);
        void onError(String errorMessage);
    }

    // Chat history item model
    public static class ChatHistoryItem {
        private final int id;
        private final String message;
        private final String response;
        private final String timestamp;

        public ChatHistoryItem(int id, String message, String response, String timestamp) {
            this.id = id;
            this.message = message;
            this.response = response;
            this.timestamp = timestamp;
        }

        public int getId() {
            return id;
        }

        public String getMessage() {
            return message;
        }

        public String getResponse() {
            return response;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }

    // Register a new user
    public void register(String name, String email, String phone, String password, AuthCallback callback) {
        try {
            Log.d(TAG, "Attempting to register user: " + email);

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("name", name);
            jsonBody.put("email", email);
            jsonBody.put("phone", phone);
            jsonBody.put("password", password);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            String url = BASE_URL + "/auth/register";

            Log.d(TAG, "Making register request to: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Register API call failed: " + e.getMessage(), e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = "No response body";
                    try {
                        responseBody = response.body() != null ? response.body().string() : "Empty response body";
                        Log.d(TAG, "Register API response: " + responseBody);

                        JSONObject jsonResponse = new JSONObject(responseBody);

                        if (response.isSuccessful()) {
                            int userId = jsonResponse.getInt("user_id");
                            Log.d(TAG, "Register successful for user ID: " + userId);
                            callback.onSuccess(userId, name, email);
                        } else {
                            String errorMessage = jsonResponse.has("detail") ?
                                    jsonResponse.getString("detail") : "Unknown error";
                            Log.e(TAG, "Register failed: " + errorMessage);
                            callback.onError(errorMessage);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error for response: " + responseBody, e);
                        callback.onError("Response parsing error: " + e.getMessage());
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "JSON creation error", e);
            callback.onError("Request creation error: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in register", e);
            callback.onError("Unexpected error: " + e.getMessage());
        }
    }

    // Login user
    public void login(String email, String password, AuthCallback callback) {
        try {
            Log.d(TAG, "Attempting to login user: " + email);

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("email", email);
            jsonBody.put("password", password);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            String url = BASE_URL + "/auth/login";

            Log.d(TAG, "Making login request to: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Login API call failed: " + e.getMessage(), e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = "No response body";
                    try {
                        responseBody = response.body() != null ? response.body().string() : "Empty response body";
                        Log.d(TAG, "Login API response: " + responseBody);

                        JSONObject jsonResponse = new JSONObject(responseBody);

                        if (response.isSuccessful()) {
                            int userId = jsonResponse.getInt("user_id");
                            String name = jsonResponse.getString("name");
                            String userEmail = jsonResponse.getString("email");

                            Log.d(TAG, "Login successful for user ID: " + userId);

                            // Save user data to SharedPreferences
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("user_id", userId);
                            editor.putString("name", name);
                            editor.putString("email", userEmail);
                            editor.apply();

                            Log.d(TAG, "User data saved to SharedPreferences");

                            callback.onSuccess(userId, name, userEmail);
                        } else {
                            String errorMessage = jsonResponse.has("detail") ?
                                    jsonResponse.getString("detail") : "Unknown error";
                            Log.e(TAG, "Login failed: " + errorMessage);
                            callback.onError(errorMessage);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error for response: " + responseBody, e);
                        callback.onError("Response parsing error: " + e.getMessage());
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "JSON creation error", e);
            callback.onError("Request creation error: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in login", e);
            callback.onError("Unexpected error: " + e.getMessage());
        }
    }

    // Reset password
    public void resetPassword(String email, String newPassword, ChatCallback callback) {
        try {
            Log.d(TAG, "Attempting to reset password for: " + email);

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("email", email);
            jsonBody.put("new_password", newPassword);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            String url = BASE_URL + "/auth/reset-password";

            Log.d(TAG, "Making reset-password request to: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Password reset API call failed: " + e.getMessage(), e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = "No response body";
                    try {
                        responseBody = response.body() != null ? response.body().string() : "Empty response body";
                        Log.d(TAG, "Reset password API response: " + responseBody);

                        JSONObject jsonResponse = new JSONObject(responseBody);

                        if (response.isSuccessful()) {
                            String message = jsonResponse.getString("message");
                            Log.d(TAG, "Password reset successful: " + message);
                            callback.onResponse(message);
                        } else {
                            String errorMessage = jsonResponse.has("detail") ?
                                    jsonResponse.getString("detail") : "Unknown error";
                            Log.e(TAG, "Password reset failed: " + errorMessage);
                            callback.onError(errorMessage);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error for response: " + responseBody, e);
                        callback.onError("Response parsing error: " + e.getMessage());
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "JSON creation error", e);
            callback.onError("Request creation error: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in reset password", e);
            callback.onError("Unexpected error: " + e.getMessage());
        }
    }

    // Send chat request
    public void sendChatRequest(String query, ChatCallback callback) {
        try {
            Log.d(TAG, "Preparing to send chat request: " + query);

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("query", query);

            // Add user_id if available from SharedPreferences
            int userId = preferences.getInt("user_id", -1);
            if (userId != -1) {
                jsonBody.put("user_id", userId);
                Log.d(TAG, "Including user_id in request: " + userId);
            } else {
                Log.w(TAG, "No user_id found in preferences, sending anonymous request");
            }

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            String url = BASE_URL + "/chat/message";

            Log.d(TAG, "Making chat request to: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Chat API call failed: " + e.getMessage(), e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = "No response body";
                    try {
                        responseBody = response.body() != null ? response.body().string() : "Empty response body";

                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String responseText = jsonResponse.getString("response");
                            Log.d(TAG, "Received successful chat response (length: " + responseText.length() + ")");
                            callback.onResponse(responseText);
                        } else {
                            Log.e(TAG, "Chat request failed with code: " + response.code() + ", body: " + responseBody);
                            callback.onError("Server error: " + response.code());
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error for response: " + responseBody, e);
                        callback.onError("Response parsing error: " + e.getMessage());
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "JSON creation error", e);
            callback.onError("Request creation error: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in send chat request", e);
            callback.onError("Unexpected error: " + e.getMessage());
        }
    }

    // Check server health
    public void checkServerHealth(ChatCallback callback) {
        try {
            String url = BASE_URL + "/health";
            Log.d(TAG, "Checking server health at: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Server health check failed: " + e.getMessage(), e);
                    callback.onError("Server health check failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = "No response body";
                    try {
                        responseBody = response.body() != null ? response.body().string() : "Empty response body";
                        Log.d(TAG, "Health check response: " + responseBody);

                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            boolean modelLoaded = jsonResponse.getBoolean("model_loaded");
                            boolean embeddingsLoaded = jsonResponse.getBoolean("embeddings_loaded");

                            Log.d(TAG, "Server health: model_loaded=" + modelLoaded +
                                    ", embeddings_loaded=" + embeddingsLoaded);

                            if (modelLoaded && embeddingsLoaded) {
                                callback.onResponse("Server is healthy");
                            } else {
                                callback.onError("Server is running but some components are not loaded");
                            }
                        } else {
                            Log.e(TAG, "Health check failed with status: " + response.code());
                            callback.onError("Server health check failed: " + response.code());
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error for health check: " + responseBody, e);
                        callback.onError("Response parsing error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in health check", e);
            callback.onError("Unexpected error: " + e.getMessage());
        }
    }

    // Get chat history
    public void getChatHistory(int userId, ChatHistoryCallback callback) {
        try {
            String url = BASE_URL + "/chat/history/" + userId;
            Log.d(TAG, "Fetching chat history from: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Chat history API call failed: " + e.getMessage(), e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = "No response body";
                    try {
                        responseBody = response.body() != null ? response.body().string() : "Empty response body";
                        Log.d(TAG, "Chat history response received (length: " + responseBody.length() + ")");

                        if (response.isSuccessful()) {
                            JSONArray jsonArray = new JSONArray(responseBody);
                            List<ChatHistoryItem> historyItems = new ArrayList<>();

                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject jsonObject = jsonArray.getJSONObject(i);
                                int id = jsonObject.getInt("id");
                                String message = jsonObject.getString("query"); // Changed from "message" to "query"
                                String responseText = jsonObject.getString("response");
                                String timestamp = jsonObject.getString("timestamp");

                                historyItems.add(new ChatHistoryItem(id, message, responseText, timestamp));
                            }

                            Log.d(TAG, "Parsed " + historyItems.size() + " chat history items");
                            callback.onSuccess(historyItems);
                        } else {
                            Log.e(TAG, "Chat history request failed with code: " + response.code());
                            callback.onError("Server error: " + response.code());
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error for response: " + responseBody, e);
                        callback.onError("Response parsing error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in get chat history", e);
            callback.onError("Unexpected error: " + e.getMessage());
        }
    }

    // Session data model
    public static class SessionData {
        private final String title;
        private final String date;
        private final List<ChatHistoryItem> messages;
        private final String formattedDateTime;

        public SessionData(String title, String date, List<ChatHistoryItem> messages) {
            this.title = title;
            this.date = date;
            this.messages = messages;

            String formatted = date; // fallback
            try {
                Log.d("SessionData", "Original date string: " + date); // DEBUG

                SimpleDateFormat inputFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
                Date sessionDate = inputFormat.parse(date);

                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
                assert sessionDate != null;
                formatted = outputFormat.format(sessionDate);
            } catch (ParseException e) {
                Log.e("SessionData", "Date parsing failed for: " + date, e);
            }

            this.formattedDateTime = formatted;
        }


        public String getTitle() {
            return title;
        }

        public String getDate() {
            return date;
        }

        public String getFormattedDateTime() {
            return formattedDateTime;
        }

        public List<ChatHistoryItem> getMessages() {
            return messages;
        }

        public String getPreviewText() {
            if (messages != null && !messages.isEmpty()) {
                String message = messages.get(0).getMessage();
                if (message.length() > 50) {
                    return message.substring(0, 50) + "...";
                }
                return message;
            }
            return "No messages";
        }
    }

    // Interface for session history callbacks
    public interface SessionHistoryCallback {
        void onSuccess(List<SessionData> sessions);
        void onError(String errorMessage);
    }

    // Get chat history grouped into sessions
    public void getSessionHistory(int userId, final SessionHistoryCallback callback) {
        getChatHistory(userId, new ChatHistoryCallback() {
            @Override
            public void onSuccess(List<ChatHistoryItem> history) {
                try {
                    if (history.isEmpty()) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }

                    // Group messages by session (using timestamp proximity as a heuristic)
                    List<SessionData> sessions = new ArrayList<>();
                    List<ChatHistoryItem> currentSessionItems = new ArrayList<>();

                    // Define session break time (e.g., 30 minutes)
                    final long SESSION_BREAK_MS = 30 * 60 * 1000;
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                    SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
                    inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

                    Date lastMessageTime = null;

                    for (ChatHistoryItem item : history) {
                        try {
                            Date currentMessageTime = inputFormat.parse(item.getTimestamp());

                            // Start a new session if this is the first message or too much time has passed
                            if (lastMessageTime == null ||
                                    (currentMessageTime.getTime() - lastMessageTime.getTime() > SESSION_BREAK_MS)) {

                                // Save previous session if it exists
                                if (!currentSessionItems.isEmpty()) {
                                    ChatHistoryItem firstMsg = currentSessionItems.get(0);
                                    Date sessionDate = inputFormat.parse(firstMsg.getTimestamp());
                                    String sessionTitle = "Session: " + outputFormat.format(sessionDate);
                                    sessions.add(new SessionData(sessionTitle,
                                            outputFormat.format(sessionDate),
                                            new ArrayList<>(currentSessionItems)));
                                    currentSessionItems.clear();
                                }
                            }

                            // Add current message to the current session
                            currentSessionItems.add(item);
                            lastMessageTime = currentMessageTime;

                        } catch (ParseException e) {
                            Log.e(TAG, "Error parsing date: " + e.getMessage(), e);
                        }
                    }

                    // Don't forget to add the last session
                    if (!currentSessionItems.isEmpty()) {
                        try {
                            ChatHistoryItem firstMsg = currentSessionItems.get(0);
                            Date sessionDate = inputFormat.parse(firstMsg.getTimestamp());
                            String sessionTitle = "Session: " + outputFormat.format(sessionDate);
                            sessions.add(new SessionData(sessionTitle,
                                    outputFormat.format(sessionDate),
                                    currentSessionItems));
                        } catch (ParseException e) {
                            Log.e(TAG, "Error parsing date for last session: " + e.getMessage(), e);
                        }
                    }

                    Log.d(TAG, "Created " + sessions.size() + " sessions from " + history.size() + " messages");
                    callback.onSuccess(sessions);

                } catch (Exception e) {
                    Log.e(TAG, "Error processing session history: " + e.getMessage(), e);
                    callback.onError("Error processing sessions: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    // Add to ApiService.java

    // Interface for emotional state callbacks
    public interface EmotionalStateCallback {
        void onSuccess(String state, float confidence, String trend, String colorCode, String description);
        void onError(String errorMessage);
    }

    // Interface for timeline callbacks
    public interface TimelineCallback {
        void onSuccess(List<TimelineEntry> timelineEntries, Map<String, String> summary);
        void onError(String errorMessage);
    }

    // Timeline entry data model
    public static class TimelineEntry {
        private final String timestamp;
        private final String emotion;
        private final float confidence;
        private final String color;
        private final int value;
        private final String message;

        public TimelineEntry(String timestamp, String emotion, float confidence,
                             String color, int value, String message) {
            this.timestamp = timestamp;
            this.emotion = emotion;
            this.confidence = confidence;
            this.color = color;
            this.value = value;
            this.message = message;
        }

        // Getters
        public String getTimestamp() { return timestamp; }
        public String getEmotion() { return emotion; }
        public float getConfidence() { return confidence; }
        public String getColor() { return color; }
        public int getValue() { return value; }
        public String getMessage() { return message; }
    }

    // Get current emotional state
    public void getCurrentEmotionalState(int userId, EmotionalStateCallback callback) {
        try {
            String url = BASE_URL + "/state/current/" + userId;
            Log.d(TAG, "Fetching emotional state from: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Emotional state API call failed: " + e.getMessage(), e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = "No response body";
                    try {
                        responseBody = response.body() != null ? response.body().string() : "Empty response body";
                        Log.d(TAG, "Emotional state response received");

                        if (response.isSuccessful()) {
                            JSONObject jsonObject = new JSONObject(responseBody);

                            String state = jsonObject.getString("state");
                            float confidence = (float) jsonObject.getDouble("confidence");
                            String trend = jsonObject.getString("trend");
                            String colorCode = jsonObject.getString("color_code");
                            String description = jsonObject.getString("description");

                            callback.onSuccess(state, confidence, trend, colorCode, description);
                        } else {
                            Log.e(TAG, "Emotional state request failed with code: " + response.code());
                            callback.onError("Server error: " + response.code());
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error for response: " + responseBody, e);
                        callback.onError("Response parsing error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in get emotional state", e);
            callback.onError("Unexpected error: " + e.getMessage());
        }
    }

    // Get emotional state timeline
    public void getEmotionalTimeline(int userId, int days, TimelineCallback callback) {
        try {
            String url = BASE_URL + "/state/timeline/" + userId + "?days=" + days;
            Log.d(TAG, "Fetching emotional timeline from: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Timeline API call failed: " + e.getMessage(), e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = "No response body";
                    try {
                        responseBody = response.body() != null ? response.body().string() : "Empty response body";
                        Log.d(TAG, "Timeline response received");

                        if (response.isSuccessful()) {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            JSONArray timelineArray = jsonObject.getJSONArray("timeline");
                            JSONObject summaryObject = jsonObject.getJSONObject("summary");

                            // Parse timeline entries
                            List<TimelineEntry> timelineEntries = new ArrayList<>();
                            for (int i = 0; i < timelineArray.length(); i++) {
                                JSONObject entry = timelineArray.getJSONObject(i);
                                timelineEntries.add(new TimelineEntry(
                                        entry.getString("timestamp"),
                                        entry.getString("emotion"),
                                        (float) entry.getDouble("confidence"),
                                        entry.getString("color"),
                                        entry.getInt("value"),
                                        entry.getString("short_message")
                                ));
                            }

                            // Parse summary
                            Map<String, String> summary = new HashMap<>();
                            summary.put("state", summaryObject.getString("state"));
                            summary.put("trend", summaryObject.getString("trend"));
                            summary.put("color", summaryObject.getString("color"));
                            summary.put("description", summaryObject.getString("description"));

                            callback.onSuccess(timelineEntries, summary);
                        } else {
                            Log.e(TAG, "Timeline request failed with code: " + response.code());
                            callback.onError("Server error: " + response.code());
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error for response: " + responseBody, e);
                        callback.onError("Response parsing error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in get timeline", e);
            callback.onError("Unexpected error: " + e.getMessage());
        }
    }

    // Get recommendations based on emotional state
    public void getRecommendations(int userId, RecommendationsCallback callback) {
        try {
            String url = BASE_URL + "/state/recommendations/" + userId;
            Log.d(TAG, "Fetching recommendations from: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Recommendations API call failed: " + e.getMessage(), e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = "No response body";
                    try {
                        responseBody = response.body() != null ? response.body().string() : "Empty response body";
                        Log.d(TAG, "Recommendations response received");

                        if (response.isSuccessful()) {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            JSONObject currentState = jsonObject.getJSONObject("current_state");
                            JSONArray recommendationsArray = jsonObject.getJSONArray("recommendations");

                            // Parse current state
                            String emotion = currentState.getString("emotion");
                            String trend = currentState.getString("trend");

                            // Parse recommendations
                            List<String> recommendations = new ArrayList<>();
                            for (int i = 0; i < recommendationsArray.length(); i++) {
                                recommendations.add(recommendationsArray.getString(i));
                            }

                            callback.onSuccess(emotion, trend, recommendations);
                        } else {
                            Log.e(TAG, "Recommendations request failed with code: " + response.code());
                            callback.onError("Server error: " + response.code());
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error for response: " + responseBody, e);
                        callback.onError("Response parsing error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in get recommendations", e);
            callback.onError("Unexpected error: " + e.getMessage());
        }
    }

    // Interface for recommendations callbacks
    public interface RecommendationsCallback {
        void onSuccess(String emotion, String trend, List<String> recommendations);
        void onError(String errorMessage);
    }
}