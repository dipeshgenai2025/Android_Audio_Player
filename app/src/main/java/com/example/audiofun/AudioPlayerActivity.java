package com.example.audiofun;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlayer;

import com.example.audiofun.databinding.ActivityAudioPlayerBinding; // Generated ViewBinding class

import java.util.List;

@OptIn(markerClass = UnstableApi.class) // Annotation for ExoPlayer APIs that might change
public class AudioPlayerActivity extends AppCompatActivity {

    private static final String TAG = "AudioPlayerActivity";

    private ActivityAudioPlayerBinding viewBinding;
    private ExoPlayer exoPlayer;
    private Uri selectedAudioUri;
    private String currentPlayerState = "Player Status: Player.STATE_IDLE";
    private boolean isCurrentlyPlaying = false;
    
    // Audio analysis components
    private AudioAnalyzer audioAnalyzer;
    private boolean isAnalyzing = false;
    
    // Visualization components - removed fixed timer
    // private Handler updateHandler;
    // private Runnable updateRunnable;
    // private static final int UPDATE_INTERVAL_MS = 50; // 20 FPS for smooth animation

    // Activity Result Launcher for file selection
    private final ActivityResultLauncher<String> audioFileLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedAudioUri = uri;
                    Log.d(TAG, "Audio file selected: " + selectedAudioUri);
                    Toast.makeText(this, getString(R.string.audio_file_selected), Toast.LENGTH_SHORT).show();
                    
                    // Initialize player with the selected audio file
                    initializePlayer();
                }
            }
    );
    
    // Permission launcher for microphone access
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Microphone permission granted");
                    Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "Microphone permission denied");
                    Toast.makeText(this, "Microphone permission required for audio analysis", Toast.LENGTH_LONG).show();
                }
            }
    );

    // Example: Play an audio file from a URL (uncomment and replace)
    // private static final String NETWORK_AUDIO_URL = "YOUR_AUDIO_URL_HERE";

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            String stateString;
            switch (playbackState) {
                case Player.STATE_IDLE:
                    stateString = "Player.STATE_IDLE";
                    break;
                case Player.STATE_BUFFERING:
                    stateString = "Player.STATE_BUFFERING";
                    break;
                case Player.STATE_READY:
                    stateString = "Player.STATE_READY";
                    break;
                case Player.STATE_ENDED:
                    stateString = "Player.STATE_ENDED";
                    break;
                default:
                    stateString = "UNKNOWN_STATE";
                    break;
            }
            Log.d(TAG, "Playback state changed to " + stateString);
            currentPlayerState = "Player Status: " + stateString;
            updateStatusDisplay();

            if (playbackState == Player.STATE_ENDED) {
                Toast.makeText(AudioPlayerActivity.this, "Audio Finished", Toast.LENGTH_SHORT).show();
                // Optionally:
                // if (exoPlayer != null) {
                //     exoPlayer.seekToDefaultPosition();
                //     exoPlayer.setPlayWhenReady(false);
                // }
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            Log.d(TAG, "isPlaying: " + isPlaying);
            isCurrentlyPlaying = isPlaying;
            updateStatusDisplay();
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Log.e(TAG, "Player Error: " + error.getMessage(), error);
            // Get the error code name using the static method
            String errorCodeNameString = PlaybackException.getErrorCodeName(error.errorCode);
            Toast.makeText(
                    AudioPlayerActivity.this,
                    "Error playing audio: " + errorCodeNameString + " - " + error.getLocalizedMessage(),
                    Toast.LENGTH_LONG
            ).show();
            // Update the TextView with the error code name
            currentPlayerState = getString(R.string.player_error) + errorCodeNameString;
            updateStatusDisplay();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Starting layout inflation");
        
        try {
            viewBinding = ActivityAudioPlayerBinding.inflate(getLayoutInflater());
            setContentView(viewBinding.getRoot());
            Log.d(TAG, "onCreate: Layout inflated successfully");
        } catch (Exception e) {
            Log.e(TAG, "onCreate: Error inflating layout", e);
            Toast.makeText(this, "Error loading layout: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        
        Log.d(TAG, "Activity Created");

        // Set up button click listeners
        try {
            viewBinding.btnLoadAudio.setOnClickListener(v -> openAudioFilePicker());
            
            // Audio analysis controls
            viewBinding.btnStartAnalysis.setOnClickListener(v -> startAudioAnalysis());
            viewBinding.btnStopAnalysis.setOnClickListener(v -> stopAudioAnalysis());
            
            // Buffer size controls
            viewBinding.btnBuffer25.setOnClickListener(v -> setAnalysisBufferSize(25));
            viewBinding.btnBuffer50.setOnClickListener(v -> setAnalysisBufferSize(50));
            viewBinding.btnBuffer100.setOnClickListener(v -> setAnalysisBufferSize(100));
            
            Log.d(TAG, "onCreate: All button click listeners set successfully");
        } catch (Exception e) {
            Log.e(TAG, "onCreate: Error setting button listeners", e);
        }
        
        // Initialize audio analyzer with default 13 bands and 100ms buffer
        initializeAudioAnalyzer();
        
        // Initialize visualization update mechanism
        initializeVisualization();
        
        // Request microphone permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
        
        // Initialize player immediately so controls are always visible
        initializePlayer();
    }
    
    /**
     * Initialize audio analyzer with custom frequency bands
     */
    private void initializeAudioAnalyzer() {
        // Use custom frequency bands (11 bands) as specified
        AudioAnalyzer.FrequencyBand[] customBands = createCustomFrequencyBands();
        
        // Initialize analyzer with 100ms buffer for better frequency resolution
        audioAnalyzer = new AudioAnalyzer(100, customBands);
        
        // Set up callback for real-time visualization updates
        audioAnalyzer.setOnFrequencyAnalysisListener(new AudioAnalyzer.OnFrequencyAnalysisListener() {
            @Override
            public void onFrequencyAnalysisUpdated(List<AudioAnalyzer.FrequencyBand> frequencyBands) {
                // Update visualization on main thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isAnalyzing && frequencyBands != null && !frequencyBands.isEmpty()) {
                            viewBinding.frequencyVisualizer.updateFrequencyBands(frequencyBands);
                        } else if (!isAnalyzing && viewBinding.frequencyVisualizer.isActive()) {
                            // Stop visualizer if analysis is not active
                            viewBinding.frequencyVisualizer.stop();
                        }
                    }
                });
            }
        });
        
        Log.d(TAG, "AudioAnalyzer initialized with " + customBands.length + " frequency bands");
    }
    
    /**
     * Create custom frequency bands as specified
     */
    private AudioAnalyzer.FrequencyBand[] createCustomFrequencyBands() {
        AudioAnalyzer.FrequencyBand[] bands = new AudioAnalyzer.FrequencyBand[11];
        
        // Custom frequency bands in exact order specified
        bands[0] = new AudioAnalyzer.FrequencyBand("Band 1", 50, 100);
        bands[1] = new AudioAnalyzer.FrequencyBand("Band 2", 120, 250);
        bands[2] = new AudioAnalyzer.FrequencyBand("Band 3", 5000, 15000);
        bands[3] = new AudioAnalyzer.FrequencyBand("Band 4", 40, 400);
        bands[4] = new AudioAnalyzer.FrequencyBand("Band 5", 80, 1200);
        bands[5] = new AudioAnalyzer.FrequencyBand("Band 6", 27, 4200);
        bands[6] = new AudioAnalyzer.FrequencyBand("Band 7", 200, 3500);
        bands[7] = new AudioAnalyzer.FrequencyBand("Band 8", 250, 2500);
        bands[8] = new AudioAnalyzer.FrequencyBand("Band 9", 165, 1000);
        bands[9] = new AudioAnalyzer.FrequencyBand("Band 10", 85, 180);
        bands[10] = new AudioAnalyzer.FrequencyBand("Band 11", 165, 255);
        
        return bands;
    }
    
    /**
     * Initialize visualization update mechanism
     */
    private void initializeVisualization() {
        // Removed fixed timer and updateRunnable
        // updateHandler = new Handler(Looper.getMainLooper());
        // updateRunnable = new Runnable() {
        //     @Override
        //     public void run() {
        //         updateVisualization();
        //         if (isAnalyzing) {
        //             updateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        //         }
        //     }
        // };
        
        // Configure visualizer for better animation and visibility
        viewBinding.frequencyVisualizer.setBarCount(11);
        viewBinding.frequencyVisualizer.setAnimationSpeed(0.2f); // Even faster animation
        viewBinding.frequencyVisualizer.setGravity(0.7f); // Less gravity for more responsive bars
    }
    
    /**
     * Update the frequency visualization with current data
     * This method is now called via callback from AudioAnalyzer
     */
    private void updateVisualization() {
        // This method is kept for compatibility but is no longer used
        // Visualization updates are now handled via callback from AudioAnalyzer
    }

    private void openAudioFilePicker() {
        try {
            audioFileLauncher.launch("audio/*");
        } catch (Exception e) {
            Log.e(TAG, "Error opening file picker", e);
            Toast.makeText(this, "Error opening file picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializePlayer() {
        if (exoPlayer == null) {
            exoPlayer = new ExoPlayer.Builder(this).build();
            viewBinding.playerView.setPlayer(exoPlayer); // Link ExoPlayer to the PlayerView
        }

        // Clear any existing media items
        exoPlayer.clearMediaItems();

        // Create a MediaItem from the selected audio file
        if (selectedAudioUri != null) {
            MediaItem mediaItem = MediaItem.fromUri(selectedAudioUri);
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.addListener(playerListener);
            exoPlayer.prepare(); // Prepare the player with the media source.
            Log.d(TAG, "ExoPlayer initialized with selected audio file: " + selectedAudioUri);
        } else {
            // Fallback to raw resource if no file is selected
            Uri rawAudioUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.sample_audio);
            MediaItem mediaItem = MediaItem.fromUri(rawAudioUri);
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.addListener(playerListener);
            exoPlayer.prepare();
            Log.d(TAG, "ExoPlayer initialized with raw audio resource");
        }
    }

    private void updateStatusDisplay() {
        String displayText = currentPlayerState;
        if (isCurrentlyPlaying) {
            displayText += "\nPlaying";
        } else if (currentPlayerState.contains("Player.STATE_READY")) {
            displayText += "\nPaused";
        }
        
        // Add analysis status
        if (isAnalyzing) {
            displayText += "\nAnalyzing (" + audioAnalyzer.getBufferSizeMs() + "ms buffer)";
            displayText += "\nFrequency Resolution: " + String.format("%.1f", audioAnalyzer.getFrequencyResolution()) + " Hz";
        }
        
        viewBinding.tvStatus.setText(displayText);
    }
    
    /**
     * Start audio analysis
     */
    public void startAudioAnalysis() {
        if (isAnalyzing) {
            Log.w(TAG, "Audio analysis already in progress");
            return;
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Microphone permission not granted");
            Toast.makeText(this, "Microphone permission required for audio analysis", Toast.LENGTH_LONG).show();
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        
        try {
            audioAnalyzer.startRecording();
            isAnalyzing = true;
            updateStatusDisplay();
            
            // Start visualization updates
            // updateHandler.post(updateRunnable); // Removed fixed timer
            
            Log.d(TAG, "Audio analysis started");
            Toast.makeText(this, "Audio analysis started - real-time visualization active", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio analysis", e);
            Toast.makeText(this, "Error starting audio analysis: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Stop audio analysis
     */
    public void stopAudioAnalysis() {
        if (!isAnalyzing) {
            Log.w(TAG, "No audio analysis in progress");
            return;
        }
        
        try {
            audioAnalyzer.stopRecording();
            isAnalyzing = false;
            updateStatusDisplay();
            
            // Stop visualization updates
            // updateHandler.removeCallbacks(updateRunnable); // Removed fixed timer
            
            // Stop visualizer completely
            viewBinding.frequencyVisualizer.stop();
            
            Log.d(TAG, "Audio analysis stopped");
            Toast.makeText(this, "Audio analysis stopped", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio analysis", e);
            Toast.makeText(this, "Error stopping audio analysis: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Set the buffer size for audio analysis
     * @param bufferSizeMs Buffer size in milliseconds
     */
    public void setAnalysisBufferSize(int bufferSizeMs) {
        if (audioAnalyzer != null) {
            audioAnalyzer.setBufferSize(bufferSizeMs);
            Log.d(TAG, "Analysis buffer size set to: " + bufferSizeMs + "ms");
            Toast.makeText(this, "Buffer size set to " + bufferSizeMs + "ms", Toast.LENGTH_SHORT).show();
            updateStatusDisplay();
        }
    }
    
    /**
     * Set custom frequency bands
     * @param numBands Number of frequency bands (10-20 as requested)
     */
    public void setCustomFrequencyBands(int numBands) {
        if (audioAnalyzer != null && numBands >= 10 && numBands <= 20) {
            AudioAnalyzer.FrequencyBand[] customBands = AudioAnalyzer.createLogarithmicBands(numBands, 27, 15000);
            audioAnalyzer.setFrequencyBands(customBands);
            viewBinding.frequencyVisualizer.setBarCount(numBands);
            
            Log.d(TAG, "Frequency bands updated to " + numBands + " bands");
            Toast.makeText(this, "Frequency bands updated to " + numBands, Toast.LENGTH_SHORT).show();
        }
    }

    private void releasePlayer() {
        if (exoPlayer != null) {
            Log.d(TAG, "Releasing ExoPlayer.");
            exoPlayer.removeListener(playerListener);
            exoPlayer.release(); // Releases player resources
            exoPlayer = null;
            viewBinding.playerView.setPlayer(null); // Important to clear the reference
        }
    }

    // Android Lifecycle Management for the Player
    // See: https://developer.android.com/guide/topics/media/media3/getting-started/playing-content#managing-the-player
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart called");
        if (Util.SDK_INT > 23) { // Android N (API 24) and higher
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        // Android M (API 23) and lower, or if player was released
        if (Util.SDK_INT <= 23 || exoPlayer == null) {
            initializePlayer();
        }
        
        // Resume visualization if analyzing
        if (isAnalyzing) {
            // updateHandler.post(updateRunnable); // Removed fixed timer
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
        if (Util.SDK_INT <= 23) { // Android M (API 23) and lower
            releasePlayer();
        }
        
        // Pause visualization updates
        // updateHandler.removeCallbacks(updateRunnable); // Removed fixed timer
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop called");
        if (Util.SDK_INT > 23) { // Android N (API 24) and higher
            releasePlayer();
        }
        
        // Stop visualization updates
        // updateHandler.removeCallbacks(updateRunnable); // Removed fixed timer
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        // It's good practice to ensure release here too, though onStop should cover most cases.
        // If onStop was not called (e.g. finish() in onCreate()), this ensures release.
        releasePlayer();
        
        // Stop audio analysis if running
        if (isAnalyzing && audioAnalyzer != null) {
            audioAnalyzer.stopRecording();
        }
        
        // Clean up visualization
        // if (updateHandler != null) { // Removed fixed timer
        //     updateHandler.removeCallbacks(updateRunnable);
        // }
    }
}
