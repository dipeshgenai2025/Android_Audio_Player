package com.example.audiofun;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

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

@OptIn(markerClass = UnstableApi.class) // Annotation for ExoPlayer APIs that might change
public class AudioPlayerActivity extends AppCompatActivity {

    private static final String TAG = "AudioPlayerActivity";

    private ActivityAudioPlayerBinding viewBinding;
    private ExoPlayer exoPlayer;
    private Uri selectedAudioUri;

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
            viewBinding.tvStatus.setText("Player Status: " + stateString);

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
            viewBinding.tvStatus.append(isPlaying ? "\nPlaying" : "\nPaused");
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
            viewBinding.tvStatus.setText(getString(R.string.player_error) + errorCodeNameString);
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

        // Set up button click listener
        try {
            viewBinding.btnLoadAudio.setOnClickListener(v -> openAudioFilePicker());
            Log.d(TAG, "onCreate: Button click listener set successfully");
        } catch (Exception e) {
            Log.e(TAG, "onCreate: Error setting button listener", e);
        }
        
        // Initialize player immediately so controls are always visible
        initializePlayer();
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
        if (Util.SDK_INT <= 23) { // Android M (API 23) and lower
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop called");
        if (Util.SDK_INT > 23) { // Android N (API 24) and higher
            releasePlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        // It's good practice to ensure release here too, though onStop should cover most cases.
        // If onStop was not called (e.g. finish() in onCreate()), this ensures release.
        releasePlayer();
    }
}
