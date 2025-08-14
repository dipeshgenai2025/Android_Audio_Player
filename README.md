# AudioFun - Android Audio Player with Real-time FFT Analysis

A comprehensive Android application that combines audio playback capabilities with real-time frequency analysis using FFT (Fast Fourier Transform).

## Features

### Audio Playback
- Load and play audio files from device storage
- Standard media player controls (play, pause, seek)
- Support for various audio formats through Media3 ExoPlayer
- Real-time playback status monitoring

### Real-time Audio Analysis
- **Raw PCM Audio Capture**: Captures raw audio data from microphone with configurable buffer sizes
- **FFT Analysis**: Performs Fast Fourier Transform to extract frequency components
- **Frequency Detection**: Identifies dominant frequencies and their amplitudes
- **Configurable Buffer Sizes**: 25ms, 50ms, or 100ms analysis windows
- **Real-time Logging**: Outputs frequency analysis results to logcat

## Technical Implementation

### Audio Analysis Components

#### AudioAnalyzer Class
- **Raw Audio Capture**: Uses `AudioRecord` to capture PCM audio data from microphone
- **FFT Processing**: Implements Fast Fourier Transform using Apache Commons Math library
- **Window Function**: Applies Hanning window for better frequency resolution
- **Frequency Binning**: Converts FFT results to frequency-amplitude pairs
- **Threading**: Runs analysis in background thread to prevent UI blocking

#### Key Features
- **Sample Rate**: 44.1 kHz (CD quality)
- **Audio Format**: 16-bit PCM, Mono channel
- **Buffer Sizes**: Configurable from 25ms to 100ms
- **Frequency Resolution**: Depends on buffer size (e.g., 50ms = ~22Hz resolution)
- **Noise Filtering**: Filters out frequencies below 20Hz and low-amplitude components

### Dependencies

```gradle
// Audio processing and FFT
implementation 'org.apache.commons:commons-math3:3.6.1'

// Media3 for audio playback
implementation 'androidx.media3:media3-exoplayer:1.3.1'
implementation 'androidx.media3:media3-ui:1.3.1'
implementation 'androidx.media3:media3-common:1.3.1'
```

### Permissions Required

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

## Usage

### Starting Audio Analysis

1. **Grant Permissions**: The app will request microphone permission on first launch
2. **Select Buffer Size**: Choose from 25ms, 50ms, or 100ms using the buffer size buttons
3. **Start Analysis**: Tap "Start Analysis" to begin real-time frequency analysis
4. **Monitor Results**: Check logcat for frequency analysis output

### Understanding the Output

The app logs frequency analysis results in this format:
```
=== Frequency Analysis (50ms buffer) ===
Freq: 440.0 Hz, Amp: 0.125, Mag: 275.5
Freq: 880.0 Hz, Amp: 0.089, Mag: 196.2
Freq: 1320.0 Hz, Amp: 0.067, Mag: 147.8
=====================================
```

- **Frequency**: The detected frequency in Hz
- **Amplitude**: Normalized amplitude (0.0 to 1.0)
- **Magnitude**: Raw FFT magnitude value

### Buffer Size Considerations

- **25ms Buffer**: Higher frequency resolution, faster response, more CPU usage
- **50ms Buffer**: Balanced resolution and performance (recommended)
- **100ms Buffer**: Lower frequency resolution, slower response, less CPU usage

## Code Structure

### Main Components

1. **AudioPlayerActivity**: Main activity with UI controls and lifecycle management
2. **AudioAnalyzer**: Core audio analysis engine with FFT processing
3. **FrequencyBin**: Data class representing frequency-amplitude pairs

### Key Methods

#### AudioAnalyzer
- `startRecording()`: Begin audio capture and analysis
- `stopRecording()`: Stop audio capture and analysis
- `setBufferSize(int ms)`: Configure analysis buffer size
- `performFFT()`: Execute FFT on audio buffer
- `logFrequencyAnalysis()`: Output results to logcat

#### AudioPlayerActivity
- `startAudioAnalysis()`: Start real-time analysis
- `stopAudioAnalysis()`: Stop real-time analysis
- `setAnalysisBufferSize(int ms)`: Change buffer size
- `updateStatusDisplay()`: Update UI with current status

## Technical Details

### FFT Implementation
- Uses Apache Commons Math FastFourierTransformer
- Applies Hanning window function for spectral leakage reduction
- Processes only first half of FFT result (Nyquist frequency)
- Normalizes amplitudes for consistent scaling

### Audio Processing Pipeline
1. **Capture**: Raw PCM audio from microphone
2. **Window**: Apply Hanning window function
3. **FFT**: Transform to frequency domain
4. **Analysis**: Extract frequency bins and amplitudes
5. **Filter**: Remove noise and low-amplitude components
6. **Output**: Log significant frequency components

### Performance Optimizations
- Power-of-2 buffer sizes for efficient FFT
- Background thread processing
- Configurable analysis frequency
- Memory-efficient audio buffer management

## Troubleshooting

### Common Issues

1. **Permission Denied**: Ensure microphone permission is granted
2. **No Audio Input**: Check microphone hardware and volume settings
3. **High CPU Usage**: Try larger buffer sizes (100ms)
4. **Poor Frequency Resolution**: Use smaller buffer sizes (25ms)

### Debug Information

Enable debug logging to see detailed information:
```java
Log.d("AudioAnalyzer", "Debug messages enabled");
```

## Future Enhancements

- **Spectrum Visualization**: Real-time frequency spectrum display
- **Audio Recording**: Save analyzed audio data
- **Frequency Detection**: Identify musical notes and chords
- **Multiple Audio Sources**: Support for different input sources
- **Advanced Filtering**: Implement bandpass and notch filters

## License

This project is licensed under the MIT License - see the LICENSE file for details.
