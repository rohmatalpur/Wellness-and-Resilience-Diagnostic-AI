// EmotionalStateActivity.java
package com.example.warda_therapist;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class EmotionalStateActivity extends AppCompatActivity {
    private static final String TAG = "EmotionalStateActivity";

    private ApiService apiService;
    private SharedPreferences preferences;
    private LineChart chart;
    private LinearLayout recommendationsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emotional_state);

        // Initialize views
        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);
        chart = findViewById(R.id.lineChart);
        recommendationsContainer = findViewById(R.id.recommendationsContainer);

        // Set up back button
        btnBack.setOnClickListener(v -> onBackPressed());

        // Initialize API service
        apiService = new ApiService(this);

        // Get user preferences
        preferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

        // Set up chart
        setupChart();

        // Load data
        loadEmotionalTimeline();
        loadRecommendations();
    }

    private void setupChart() {
        // Basic chart setup
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawBorders(false);

        // X-axis setup
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        // Y-axis setup
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(-3f);
        leftAxis.setAxisMaximum(2f);
        chart.getAxisRight().setEnabled(false);

        // Set Y-axis labels
        leftAxis.setLabelCount(6, true);
        leftAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Distressed", "Sad", "Anxious/Angry", "Neutral", "Content", "Hopeful"}));

        // Legend setup
        chart.getLegend().setEnabled(false);
    }

    private void loadEmotionalTimeline() {
        try {
            int userId = preferences.getInt("user_id", -1);
            if (userId == -1) {
                Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            apiService.getEmotionalTimeline(userId, 7, new ApiService.TimelineCallback() {
                @Override
                public void onSuccess(List<ApiService.TimelineEntry> timelineEntries, Map<String, String> summary) {
                    runOnUiThread(() -> {
                        try {
                            updateChart(timelineEntries);

                            // Update summary info
                            TextView tvSummaryState = findViewById(R.id.tvSummaryState);
                            TextView tvSummaryDescription = findViewById(R.id.tvSummaryDescription);

                            if (tvSummaryState != null && summary.containsKey("state")) {
                                String state = summary.get("state");
                                tvSummaryState.setText("Current State: " + capitalize(state));
                            }

                            if (tvSummaryDescription != null && summary.containsKey("description")) {
                                tvSummaryDescription.setText(summary.get("description"));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating chart: " + e.getMessage(), e);
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        Toast.makeText(EmotionalStateActivity.this,
                                "Error loading timeline: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error loading emotional timeline: " + e.getMessage(), e);
        }
    }

    private void updateChart(List<ApiService.TimelineEntry> entries) {
        if (entries.isEmpty()) {
            chart.setNoDataText("No emotional data available");
            return;
        }

        List<Entry> chartEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        SimpleDateFormat outputFormat = new SimpleDateFormat("MM/dd HH:mm", Locale.US);
        inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        for (int i = 0; i < entries.size(); i++) {
            ApiService.TimelineEntry entry = entries.get(i);
            chartEntries.add(new Entry(i, entry.getValue()));

            // Format date for label
            try {
                Date date = inputFormat.parse(entry.getTimestamp());
                labels.add(outputFormat.format(date));
            } catch (ParseException e) {
                labels.add(""); // Empty label on error
            }
        }

        // Create dataset
        LineDataSet dataSet = new LineDataSet(chartEntries, "Emotional State");
        dataSet.setDrawIcons(false);
        dataSet.setColor(Color.parseColor("#E28484"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#E28484"));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#E28484"));
        dataSet.setFillAlpha(50);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        // Set X-axis labels
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        // Update chart
        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.invalidate();
    }

    private void loadRecommendations() {
        try {
            int userId = preferences.getInt("user_id", -1);
            if (userId == -1) {
                return;
            }

            apiService.getRecommendations(userId, new ApiService.RecommendationsCallback() {
                @Override
                public void onSuccess(String emotion, String trend, List<String> recommendations) {
                    runOnUiThread(() -> {
                        try {
                            updateRecommendations(recommendations);
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating recommendations: " + e.getMessage(), e);
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error loading recommendations: " + errorMessage);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error loading recommendations: " + e.getMessage(), e);
        }
    }

    private void updateRecommendations(List<String> recommendations) {
        if (recommendationsContainer == null) {
            return;
        }

        recommendationsContainer.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();

        for (String recommendation : recommendations) {
            View view = inflater.inflate(R.layout.item_recommendation, recommendationsContainer, false);
            TextView tvRecommendation = view.findViewById(R.id.tvRecommendation);
            tvRecommendation.setText(recommendation);
            recommendationsContainer.addView(view);
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}