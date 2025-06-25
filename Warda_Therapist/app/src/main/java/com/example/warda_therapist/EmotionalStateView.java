// EmotionalStateView.java
package com.example.warda_therapist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

public class EmotionalStateView extends View {
    private Paint paint;
    private RectF rect;
    private float confidence = 0.5f;
    private String trend = "stable";
    private String colorCode = "#34C759"; // Default green for neutral

    public EmotionalStateView(Context context) {
        super(context);
        init();
    }

    public EmotionalStateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EmotionalStateView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        rect = new RectF();
    }

    public void updateState(String state, float confidence, String trend, String colorCode) {
        this.confidence = confidence;
        this.trend = trend;
        this.colorCode = colorCode;
        invalidate(); // Redraw with new state
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Draw background bar
        paint.setColor(Color.parseColor("#EEEEEE"));
        rect.set(0, 0, width, height);
        canvas.drawRoundRect(rect, (float) height / 2, (float) height / 2, paint);

        // Draw filled bar based on emotional state and confidence
        try {
            paint.setColor(Color.parseColor(colorCode));
        } catch (IllegalArgumentException e) {
            paint.setColor(Color.parseColor("#34C759")); // Default to green
        }

        // The width is determined by confidence level (0.0 to 1.0)
        float filledWidth = width * confidence;
        rect.set(0, 0, filledWidth, height);
        canvas.drawRoundRect(rect, (float) height / 2, (float) height / 2, paint);

        // Add trend indicator (optional visual enhancement)
        paint.setColor(Color.WHITE);
        int indicatorSize = height / 3;

        if (trend.equals("improving")) {
            // Draw up arrow at the end of the bar
            int centerX = (int) filledWidth - indicatorSize;
            int centerY = height / 2;
            canvas.drawCircle(centerX, centerY, indicatorSize, paint);
        } else if (trend.equals("declining")) {
            // Draw down arrow at the end of the bar
            int centerX = (int) filledWidth - indicatorSize;
            int centerY = height / 2;
            canvas.drawCircle(centerX, centerY, indicatorSize, paint);
        }
    }
}