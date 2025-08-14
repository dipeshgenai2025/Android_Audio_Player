package com.example.audiofun;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for visualizing frequency bands as animated bars
 * Displays frequency spectrum from 20Hz to 20kHz with normalized amplitudes
 */
public class FrequencyVisualizerView extends View {
    private static final String TAG = "FrequencyVisualizerView";
    
    // Drawing configuration
    private Paint barPaint;
    private Paint textPaint;
    private Paint backgroundPaint;
    private Paint gridPaint;
    
    // Visualization data
    private List<AudioAnalyzer.FrequencyBand> frequencyBands;
    private float[] barHeights; // Current bar heights (0-10 scale)
    private float[] targetHeights; // Target bar heights for smooth animation
    private float[] barVelocities; // Animation velocities
    private boolean[] shouldReset; // Track which bars should reset to 0
    private boolean isActive = false; // Track if visualization should be active
    
    // Layout configuration
    private int barCount = 11; // Changed to 11 for custom frequency bands
    private float barWidth;
    private float barSpacing;
    private float maxBarHeight;
    private float animationSpeed = 0.15f; // Animation smoothing factor
    private float gravity = 0.8f; // Gravity effect for falling bars
    private float resetThreshold = 0.1f; // Threshold for reset animation
    
    // Colors for 11 custom frequency bands
    private int[] barColors = {
        Color.rgb(255, 0, 0),      // Red - Band 1 (50-100 Hz)
        Color.rgb(255, 64, 0),     // Orange Red - Band 2 (120-250 Hz)
        Color.rgb(255, 128, 0),    // Orange - Band 3 (5000-15000 Hz)
        Color.rgb(255, 192, 0),    // Yellow Orange - Band 4 (40-400 Hz)
        Color.rgb(255, 255, 0),    // Yellow - Band 5 (80-1200 Hz)
        Color.rgb(192, 255, 0),    // Yellow Green - Band 6 (27-4200 Hz)
        Color.rgb(128, 255, 0),    // Green - Band 7 (200-3500 Hz)
        Color.rgb(64, 255, 0),     // Light Green - Band 8 (250-2500 Hz)
        Color.rgb(0, 255, 0),      // Green - Band 9 (165-1000 Hz)
        Color.rgb(0, 255, 64),     // Green Blue - Band 10 (85-180 Hz)
        Color.rgb(0, 255, 128)     // Light Blue - Band 11 (165-255 Hz)
    };
    
    public FrequencyVisualizerView(Context context) {
        super(context);
        init();
    }
    
    public FrequencyVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public FrequencyVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // Initialize paints
        barPaint = new Paint();
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setAntiAlias(true);
        
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(12);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.rgb(20, 20, 20));
        backgroundPaint.setStyle(Paint.Style.FILL);
        
        gridPaint = new Paint();
        gridPaint.setColor(Color.rgb(40, 40, 40));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1);
        
        // Initialize arrays
        barHeights = new float[barCount];
        targetHeights = new float[barCount];
        barVelocities = new float[barCount];
        shouldReset = new boolean[barCount];
        
        // Set background
        setBackgroundColor(Color.rgb(20, 20, 20));
    }
    
    /**
     * Update frequency bands data and trigger redraw
     * @param bands List of frequency bands with normalized amplitudes
     */
    public void updateFrequencyBands(List<AudioAnalyzer.FrequencyBand> bands) {
        if (bands == null || bands.isEmpty()) {
            isActive = false;
            return;
        }
        
        this.frequencyBands = new ArrayList<>(bands);
        isActive = true; // Set active when we have data
        
        // Update target heights and check for reset conditions
        for (int i = 0; i < Math.min(barCount, bands.size()); i++) {
            float newTarget = (float) bands.get(i).normalizedAmplitude;
            float currentTarget = targetHeights[i];
            
            // Check if the amplitude change is significant enough to trigger a reset
            if (Math.abs(newTarget - currentTarget) > resetThreshold) {
                shouldReset[i] = true; // Mark this bar for reset
            }
            
            targetHeights[i] = newTarget;
        }
        
        // Fill remaining bars with zero if we have fewer bands
        for (int i = bands.size(); i < barCount; i++) {
            if (targetHeights[i] > resetThreshold) {
                shouldReset[i] = true; // Reset bars that were previously active
            }
            targetHeights[i] = 0.0f;
        }
        
        // Trigger animation update
        invalidate();
    }
    
    /**
     * Set the number of bars to display
     * @param count Number of bars
     */
    public void setBarCount(int count) {
        if (count > 0 && count != barCount) {
            barCount = count;
            barHeights = new float[barCount];
            targetHeights = new float[barCount];
            barVelocities = new float[barCount];
            shouldReset = new boolean[barCount];
            requestLayout();
        }
    }
    
    /**
     * Set animation speed
     * @param speed Animation smoothing factor (0.1f to 0.3f recommended)
     */
    public void setAnimationSpeed(float speed) {
        this.animationSpeed = Math.max(0.01f, Math.min(1.0f, speed));
    }
    
    /**
     * Set gravity effect for falling bars
     * @param gravity Gravity factor (0.5f to 0.95f recommended)
     */
    public void setGravity(float gravity) {
        this.gravity = Math.max(0.1f, Math.min(0.99f, gravity));
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // Calculate bar dimensions
        float totalBarWidth = w * 0.9f; // Use 90% of width for bars
        barWidth = totalBarWidth / barCount;
        barSpacing = (w - totalBarWidth) / (barCount + 1);
        maxBarHeight = h * 0.8f; // Use 80% of height for bars
        
        // Leave space for text at bottom
        maxBarHeight -= 30;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw background
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        
        // Draw grid lines
        drawGrid(canvas);
        
        // Update bar animations
        updateAnimations();
        
        // Draw frequency bars
        drawFrequencyBars(canvas);
        
        // Draw labels
        drawLabels(canvas);
        
        // Continue animation only if active
        if (isActive) {
            postInvalidateOnAnimation();
        }
    }
    
    private void drawGrid(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        
        // Draw horizontal grid lines (amplitude levels)
        for (int i = 1; i <= 10; i++) {
            float y = height * 0.8f - (i * maxBarHeight / 10);
            canvas.drawLine(0, y, width, y, gridPaint);
        }
        
        // Draw vertical grid lines (frequency bands)
        for (int i = 0; i <= barCount; i++) {
            float x = barSpacing + i * (barWidth + barSpacing);
            canvas.drawLine(x, 0, x, height * 0.8f, gridPaint);
        }
    }
    
    private void updateAnimations() {
        for (int i = 0; i < barCount; i++) {
            // Check if the bar should reset
            if (shouldReset[i]) {
                // Reset bar to 0
                barHeights[i] = 0.0f;
                barVelocities[i] = 0.0f; // Stop any current velocity
                shouldReset[i] = false; // Reset the flag
            } else {
                // Smooth animation towards target height
                float diff = targetHeights[i] - barHeights[i];
                barVelocities[i] += diff * animationSpeed;
                barVelocities[i] *= gravity; // Apply gravity
                barHeights[i] += barVelocities[i];
                
                // Ensure bar doesn't go below 0
                if (barHeights[i] < 0) {
                    barHeights[i] = 0;
                    barVelocities[i] = 0;
                }
            }
        }
    }
    
    private void drawFrequencyBars(Canvas canvas) {
        float startX = barSpacing;
        
        for (int i = 0; i < barCount; i++) {
            float barHeight = barHeights[i] * maxBarHeight / 10.0f; // Scale to 0-10
            
            // Ensure minimum height for visibility (at least 2 pixels)
            float minHeight = 2.0f;
            if (barHeight < minHeight && barHeights[i] > 0.1f) {
                barHeight = minHeight;
            }
            
            float barY = getHeight() * 0.8f - barHeight;
            
            // Create rounded rectangle for bar
            RectF barRect = new RectF(
                startX, 
                barY, 
                startX + barWidth, 
                getHeight() * 0.8f
            );
            
            // Set bar color based on frequency band
            int colorIndex = i % barColors.length;
            barPaint.setColor(barColors[colorIndex]);
            
            // Add gradient effect based on height with better contrast
            float alpha = 0.5f + (barHeights[i] / 10.0f) * 0.5f; // Minimum 50% opacity
            barPaint.setAlpha((int) (255 * alpha));
            
            // Draw bar with rounded corners
            float cornerRadius = barWidth * 0.1f;
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint);
            
            // Draw bar border with better contrast
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(2);
            barPaint.setAlpha(255);
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint);
            barPaint.setStyle(Paint.Style.FILL);
            
            startX += barWidth + barSpacing;
        }
    }
    
    private void drawLabels(Canvas canvas) {
        if (frequencyBands == null || frequencyBands.isEmpty()) {
            return;
        }
        
        float startX = barSpacing + barWidth / 2;
        float labelY = getHeight() * 0.8f + 20;
        
        for (int i = 0; i < Math.min(barCount, frequencyBands.size()); i++) {
            AudioAnalyzer.FrequencyBand band = frequencyBands.get(i);
            
            // Draw frequency range
            String freqLabel = String.format("%d-%d", band.minFreq, band.maxFreq);
            textPaint.setTextSize(10);
            canvas.drawText(freqLabel, startX, labelY, textPaint);
            
            // Draw amplitude value
            String ampLabel = String.format("%.1f", band.normalizedAmplitude);
            textPaint.setTextSize(8);
            canvas.drawText(ampLabel, startX, labelY + 15, textPaint);
            
            startX += barWidth + barSpacing;
        }
    }
    
    /**
     * Get current bar heights for external use
     * @return Array of current bar heights (0-10 scale)
     */
    public float[] getBarHeights() {
        return barHeights.clone();
    }
    
    /**
     * Get target bar heights for external use
     * @return Array of target bar heights (0-10 scale)
     */
    public float[] getTargetHeights() {
        return targetHeights.clone();
    }
    
    /**
     * Reset all bars to zero
     */
    public void reset() {
        isActive = false; // Stop animation
        for (int i = 0; i < barCount; i++) {
            barHeights[i] = 0.0f;
            targetHeights[i] = 0.0f;
            barVelocities[i] = 0.0f;
            shouldReset[i] = true; // Set flag to reset
        }
        invalidate();
    }
    
    /**
     * Stop visualization and clear all data
     */
    public void stop() {
        isActive = false;
        frequencyBands = null;
        for (int i = 0; i < barCount; i++) {
            barHeights[i] = 0.0f;
            targetHeights[i] = 0.0f;
            barVelocities[i] = 0.0f;
            shouldReset[i] = false;
        }
        invalidate();
    }
    
    /**
     * Check if visualization is currently active
     * @return true if visualization is active, false otherwise
     */
    public boolean isActive() {
        return isActive;
    }
}
