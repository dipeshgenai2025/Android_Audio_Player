package com.example.audiofun;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AudioAnalyzer class for capturing raw PCM audio and performing FFT analysis
 * Supports configurable buffer sizes and real-time frequency analysis
 * Provides complete spectrum from 50Hz to 10kHz with customizable frequency bands
 */
public class AudioAnalyzer {
    private static final String TAG = "AudioAnalyzer";
    
    // Audio configuration
    private static final int SAMPLE_RATE = 44100; // Standard audio sample rate
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    // Frequency analysis configuration
    private static final int MIN_FREQUENCY = 27; // Hz - updated for new frequency bands
    private static final int MAX_FREQUENCY = 15000; // Hz - updated for new frequency bands
    private static final double MIN_AMPLITUDE_THRESHOLD = 0.0001; // Lower threshold for more sensitivity
    private static final double NORMALIZED_SCALE = 10.0; // Scale for 0-10 normalization
    
    // Amplitude scaling factors for better visualization
    private static final double AMPLITUDE_SCALE_FACTOR = 200.0; // Increased from 50.0
    private static final double LOW_FREQ_DAMPING = 0.3; // Dampen low frequencies to reduce dominance
    //private static final int LOW_FREQ_THRESHOLD = 500; // Hz - frequencies below this get damped
    private static final int LOW_FREQ_THRESHOLD = 27; // Hz - frequencies below this get damped
    
    // Default buffer size (100ms at 44.1kHz = 4410 samples, rounded to 4096)
    private int bufferSizeMs = 100;
    private int bufferSizeSamples;
    
    // Audio recording components
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    
    // FFT components
    private FastFourierTransformer fft;
    private double[] windowFunction;
    
    // Analysis results
    private List<FrequencyBin> frequencyBins;
    private Map<String, FrequencyBand> frequencyBands;
    private List<FrequencyBand> frequencyBandsList; // Ordered list for visualization
    
    // Callback for real-time updates
    private OnFrequencyAnalysisListener analysisListener;
    
    /**
     * Interface for frequency analysis updates
     */
    public interface OnFrequencyAnalysisListener {
        void onFrequencyAnalysisUpdated(List<FrequencyBand> frequencyBands);
    }
    
    /**
     * Set the listener for frequency analysis updates
     */
    public void setOnFrequencyAnalysisListener(OnFrequencyAnalysisListener listener) {
        this.analysisListener = listener;
    }
    
    // Frequency band definitions - Custom frequency bands as specified
    private static final FrequencyBand[] DEFAULT_FREQUENCY_BANDS = {
        new FrequencyBand("Band 1", 50, 100),
        new FrequencyBand("Band 2", 120, 250),
        new FrequencyBand("Band 3", 5000, 15000),
        new FrequencyBand("Band 4", 40, 400),
        new FrequencyBand("Band 5", 80, 1200),
        new FrequencyBand("Band 6", 27, 4200),
        new FrequencyBand("Band 7", 200, 3500),
        new FrequencyBand("Band 8", 250, 2500),
        new FrequencyBand("Band 9", 165, 1000),
        new FrequencyBand("Band 10", 85, 180),
        new FrequencyBand("Band 11", 165, 255)
    };
    
    /**
     * Represents a frequency bin with frequency and amplitude
     */
    public static class FrequencyBin {
        public final double frequency;
        public final double amplitude;
        public final double magnitude;
        public final double normalizedAmplitude; // 0-10 scale
        
        public FrequencyBin(double frequency, double amplitude, double magnitude) {
            this.frequency = frequency;
            this.amplitude = amplitude;
            this.magnitude = magnitude;
            
            // Apply low frequency damping to reduce dominance of bass frequencies
            double dampedAmplitude = amplitude;
            if (frequency < LOW_FREQ_THRESHOLD) {
                dampedAmplitude *= LOW_FREQ_DAMPING;
            }
            
            // Improved normalization with higher scaling factor
            this.normalizedAmplitude = Math.min(NORMALIZED_SCALE, dampedAmplitude * NORMALIZED_SCALE * AMPLITUDE_SCALE_FACTOR);
        }
        
        @Override
        public String toString() {
            return String.format("Freq: %.1f Hz, Amp: %.3f, Norm: %.1f", 
                               frequency, amplitude, normalizedAmplitude);
        }
    }
    
    /**
     * Represents a frequency band with aggregated data
     */
    public static class FrequencyBand {
        public final String name;
        public final int minFreq;
        public final int maxFreq;
        public double totalAmplitude;
        public double peakAmplitude;
        public double averageAmplitude;
        public double rawAverageAmplitude;
        public double normalizedAmplitude; // 0-10 scale
        public int binCount;
        
        public FrequencyBand(String name, int minFreq, int maxFreq) {
            this.name = name;
            this.minFreq = minFreq;
            this.maxFreq = maxFreq;
            this.totalAmplitude = 0.0;
            this.peakAmplitude = 0.0;
            this.averageAmplitude = 0.0;
            this.rawAverageAmplitude = 0.0;
            this.normalizedAmplitude = 0.0;
            this.binCount = 0;
        }
        
        public void addBin(FrequencyBin bin) {
            totalAmplitude += bin.amplitude;
            peakAmplitude = Math.max(peakAmplitude, bin.amplitude);
            binCount++;
        }
        
        public void calculateAverage() {
            if (binCount > 0) {
                averageAmplitude = totalAmplitude / binCount;
                rawAverageAmplitude = averageAmplitude;
            }
        }
        
        public void reset() {
            totalAmplitude = 0.0;
            peakAmplitude = 0.0;
            averageAmplitude = 0.0;
            rawAverageAmplitude = 0.0;
            normalizedAmplitude = 0.0;
            binCount = 0;
        }
        
        @Override
        public String toString() {
            return String.format("%s (%d-%d Hz): Avg: %.3f, Norm: %.1f, Bins: %d", 
                               name, minFreq, maxFreq, averageAmplitude, normalizedAmplitude, binCount);
        }
    }
    
    /**
     * Constructor with default 100ms buffer size for better frequency resolution
     */
    public AudioAnalyzer() {
        this(100, DEFAULT_FREQUENCY_BANDS);
    }
    
    /**
     * Constructor with configurable buffer size and frequency bands
     * @param bufferSizeMs Buffer size in milliseconds
     * @param customBands Custom frequency bands (can be null to use defaults)
     */
    public AudioAnalyzer(int bufferSizeMs, FrequencyBand[] customBands) {
        this.bufferSizeMs = bufferSizeMs;
        this.bufferSizeSamples = (SAMPLE_RATE * bufferSizeMs) / 1000;
        
        // Ensure buffer size is a power of 2 for efficient FFT
        this.bufferSizeSamples = nextPowerOf2(this.bufferSizeSamples);
        
        // Initialize FFT transformer
        this.fft = new FastFourierTransformer(DftNormalization.STANDARD);
        
        // Create Hanning window function for better frequency resolution
        this.windowFunction = createHanningWindow(this.bufferSizeSamples);
        
        // Initialize frequency bands
        FrequencyBand[] bandsToUse = (customBands != null) ? customBands : DEFAULT_FREQUENCY_BANDS;
        this.frequencyBands = new HashMap<>();
        this.frequencyBandsList = new ArrayList<>();
        
        for (FrequencyBand band : bandsToUse) {
            FrequencyBand newBand = new FrequencyBand(band.name, band.minFreq, band.maxFreq);
            this.frequencyBands.put(band.name, newBand);
            this.frequencyBandsList.add(newBand);
        }
        
        Log.d(TAG, "AudioAnalyzer initialized with buffer size: " + bufferSizeMs + "ms (" + bufferSizeSamples + " samples)");
        Log.d(TAG, "Frequency resolution: " + String.format("%.2f", (double) SAMPLE_RATE / bufferSizeSamples) + " Hz");
        Log.d(TAG, "Frequency range: " + MIN_FREQUENCY + "Hz - " + MAX_FREQUENCY + "Hz");
        Log.d(TAG, "Number of frequency bands: " + this.frequencyBandsList.size());
    }
    
    /**
     * Set custom frequency bands
     * @param customBands Array of custom frequency bands
     */
    public void setFrequencyBands(FrequencyBand[] customBands) {
        if (customBands == null || customBands.length == 0) {
            Log.w(TAG, "Invalid frequency bands provided, using defaults");
            return;
        }
        
        this.frequencyBands.clear();
        this.frequencyBandsList.clear();
        
        for (FrequencyBand band : customBands) {
            FrequencyBand newBand = new FrequencyBand(band.name, band.minFreq, band.maxFreq);
            this.frequencyBands.put(band.name, newBand);
            this.frequencyBandsList.add(newBand);
        }
        
        Log.d(TAG, "Frequency bands updated. Number of bands: " + this.frequencyBandsList.size());
    }
    
    /**
     * Create custom frequency bands with equal logarithmic spacing
     * @param numBands Number of bands to create
     * @param minFreq Minimum frequency in Hz
     * @param maxFreq Maximum frequency in Hz
     * @return Array of frequency bands
     */
    public static FrequencyBand[] createLogarithmicBands(int numBands, int minFreq, int maxFreq) {
        if (numBands <= 0 || minFreq >= maxFreq) {
            Log.e(TAG, "Invalid parameters for logarithmic bands");
            return DEFAULT_FREQUENCY_BANDS;
        }
        
        FrequencyBand[] bands = new FrequencyBand[numBands];
        double logMin = Math.log(minFreq);
        double logMax = Math.log(maxFreq);
        double logStep = (logMax - logMin) / numBands;
        
        for (int i = 0; i < numBands; i++) {
            int bandMinFreq = (int) Math.exp(logMin + i * logStep);
            int bandMaxFreq = (int) Math.exp(logMin + (i + 1) * logStep);
            
            String bandName = String.format("Band %d", i + 1);
            bands[i] = new FrequencyBand(bandName, bandMinFreq, bandMaxFreq);
        }
        
        return bands;
    }
    
    /**
     * Create custom frequency bands with equal linear spacing
     * @param numBands Number of bands to create
     * @param minFreq Minimum frequency in Hz
     * @param maxFreq Maximum frequency in Hz
     * @return Array of frequency bands
     */
    public static FrequencyBand[] createLinearBands(int numBands, int minFreq, int maxFreq) {
        if (numBands <= 0 || minFreq >= maxFreq) {
            Log.e(TAG, "Invalid parameters for linear bands");
            return DEFAULT_FREQUENCY_BANDS;
        }
        
        FrequencyBand[] bands = new FrequencyBand[numBands];
        int freqStep = (maxFreq - minFreq) / numBands;
        
        for (int i = 0; i < numBands; i++) {
            int bandMinFreq = minFreq + i * freqStep;
            int bandMaxFreq = minFreq + (i + 1) * freqStep;
            
            String bandName = String.format("Band %d", i + 1);
            bands[i] = new FrequencyBand(bandName, bandMinFreq, bandMaxFreq);
        }
        
        return bands;
    }
    
    /**
     * Start audio recording and analysis
     */
    public void startRecording() {
        if (isRecording.get()) {
            Log.w(TAG, "Recording already in progress");
            return;
        }
        
        try {
            // Calculate minimum buffer size
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            int recordBufferSize = Math.max(minBufferSize, bufferSizeSamples * 2); // 16-bit = 2 bytes per sample
            
            Log.d(TAG, "AudioRecord configuration - Sample Rate: " + SAMPLE_RATE + 
                      ", Min Buffer Size: " + minBufferSize + 
                      ", Record Buffer Size: " + recordBufferSize);
            
            // Initialize AudioRecord
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                recordBufferSize
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord. State: " + audioRecord.getState());
                return;
            }
            
            isRecording.set(true);
            audioRecord.startRecording();
            
            // Start recording thread
            recordingThread = new Thread(new RecordingRunnable());
            recordingThread.start();
            
            Log.d(TAG, "Audio recording started successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio recording", e);
        }
    }
    
    /**
     * Stop audio recording and analysis
     */
    public void stopRecording() {
        if (!isRecording.get()) {
            Log.w(TAG, "No recording in progress");
            return;
        }
        
        isRecording.set(false);
        
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        
        if (recordingThread != null) {
            try {
                recordingThread.join(1000); // Wait up to 1 second for thread to finish
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for recording thread to finish");
            }
            recordingThread = null;
        }
        
        Log.d(TAG, "Audio recording stopped");
    }
    
    /**
     * Set the buffer size for analysis
     * @param bufferSizeMs Buffer size in milliseconds
     */
    public void setBufferSize(int bufferSizeMs) {
        this.bufferSizeMs = bufferSizeMs;
        this.bufferSizeSamples = (SAMPLE_RATE * bufferSizeMs) / 1000;
        this.bufferSizeSamples = nextPowerOf2(this.bufferSizeSamples);
        this.windowFunction = createHanningWindow(this.bufferSizeSamples);
        
        Log.d(TAG, "Buffer size updated to: " + bufferSizeMs + "ms (" + bufferSizeSamples + " samples)");
        Log.d(TAG, "New frequency resolution: " + String.format("%.2f", (double) SAMPLE_RATE / bufferSizeSamples) + " Hz");
    }
    
    /**
     * Get the current buffer size in milliseconds
     */
    public int getBufferSizeMs() {
        return bufferSizeMs;
    }
    
    /**
     * Get the latest frequency analysis results
     */
    public List<FrequencyBin> getFrequencyBins() {
        return frequencyBins != null ? new ArrayList<>(frequencyBins) : new ArrayList<>();
    }
    
    /**
     * Get the latest frequency band analysis results as a map
     */
    public Map<String, FrequencyBand> getFrequencyBands() {
        return frequencyBands != null ? new HashMap<>(frequencyBands) : new HashMap<>();
    }
    
    /**
     * Get the latest frequency band analysis results as an ordered list
     */
    public List<FrequencyBand> getFrequencyBandsList() {
        return frequencyBandsList != null ? new ArrayList<>(frequencyBandsList) : new ArrayList<>();
    }
    
    /**
     * Recording thread that continuously captures audio and performs FFT analysis
     */
    private class RecordingRunnable implements Runnable {
        @Override
        public void run() {
            short[] audioBuffer = new short[bufferSizeSamples];
            
            while (isRecording.get()) {
                try {
                    // Read audio data
                    int samplesRead = audioRecord.read(audioBuffer, 0, bufferSizeSamples);
                    
                    if (samplesRead > 0) {
                        // Perform FFT analysis
                        frequencyBins = performFFT(audioBuffer, samplesRead);
                        
                        // Calculate frequency bands
                        calculateFrequencyBands();
                        
                        // Log frequency analysis results (only bands, not individual bins)
                        logFrequencyBands();
                        
                        // Notify listener of new data (this will trigger visualization update)
                        if (analysisListener != null) {
                            analysisListener.onFrequencyAnalysisUpdated(frequencyBandsList);
                        }
                    }
                    
                    // Small delay to prevent excessive CPU usage
                    Thread.sleep(10);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in recording thread", e);
                    break;
                }
            }
        }
    }
    
    /**
     * Perform FFT analysis on the audio buffer
     * @param audioBuffer Raw PCM audio data
     * @param samplesRead Number of samples actually read
     * @return List of frequency bins with frequency and amplitude data
     */
    private List<FrequencyBin> performFFT(short[] audioBuffer, int samplesRead) {
        // Convert short array to double array and apply window function
        double[] windowedData = new double[bufferSizeSamples];
        
        for (int i = 0; i < samplesRead; i++) {
            // Normalize audio data to [-1, 1] range and apply window function
            windowedData[i] = (audioBuffer[i] / 32768.0) * windowFunction[i];
        }
        
        // Pad with zeros if necessary
        for (int i = samplesRead; i < bufferSizeSamples; i++) {
            windowedData[i] = 0.0;
        }
        
        // Perform FFT
        Complex[] fftResult = fft.transform(windowedData, TransformType.FORWARD);
        
        // Calculate frequency bins
        List<FrequencyBin> bins = new ArrayList<>();
        double frequencyResolution = (double) SAMPLE_RATE / bufferSizeSamples;
        
        // Only process first half of FFT result (Nyquist frequency)
        int numBins = bufferSizeSamples / 2;
        
        for (int i = 0; i < numBins; i++) {
            double frequency = i * frequencyResolution;
            
            // Only include frequencies in our range of interest (50Hz - 10kHz)
            if (frequency >= MIN_FREQUENCY && frequency <= MAX_FREQUENCY) {
                Complex complex = fftResult[i];
                
                // Calculate magnitude and amplitude
                double magnitude = Math.sqrt(complex.getReal() * complex.getReal() + complex.getImaginary() * complex.getImaginary());
                double amplitude = magnitude / (bufferSizeSamples / 2.0); // Normalize by buffer size
                
                // Apply additional scaling for window function
                amplitude *= 2.0; // Compensate for window function
                
                // Only include bins above threshold
                if (amplitude >= MIN_AMPLITUDE_THRESHOLD) {
                    bins.add(new FrequencyBin(frequency, amplitude, magnitude));
                }
            }
        }
        
        return bins;
    }
    
    /**
     * Calculate frequency bands from individual frequency bins
     */
    private void calculateFrequencyBands() {
        if (frequencyBins == null) {
            return;
        }
        
        // Reset all bands using the ordered list to preserve display order
        for (FrequencyBand band : frequencyBandsList) {
            band.reset();
        }
        
        // Assign bins to ALL overlapping bands (no break), preserving the provided order
        for (FrequencyBin bin : frequencyBins) {
            for (FrequencyBand band : frequencyBandsList) {
                if (bin.frequency >= band.minFreq && bin.frequency <= band.maxFreq) {
                    band.addBin(bin);
                }
            }
        }
        
        // First pass: compute raw averages per band
        for (FrequencyBand band : frequencyBandsList) {
            band.calculateAverage();
        }
        
        // Second pass: per-frame relative normalization similar to Python implementation
        // Compute damped averages (optional low-frequency damping) and find max across bands
        double maxDampedAverage = 0.0;
        double[] dampedAverages = new double[frequencyBandsList.size()];
        for (int i = 0; i < frequencyBandsList.size(); i++) {
            FrequencyBand band = frequencyBandsList.get(i);
            double avg = band.rawAverageAmplitude;
            double centerFreq = (band.minFreq + band.maxFreq) / 2.0;
            double damped = avg;
            if (centerFreq < LOW_FREQ_THRESHOLD) {
                damped *= LOW_FREQ_DAMPING;
            }
            dampedAverages[i] = damped;
            if (damped > maxDampedAverage) {
                maxDampedAverage = damped;
            }
        }
        
        // Normalize each band to 0-10 relative to the loudest band in this frame
        if (maxDampedAverage <= 0.0) {
            for (FrequencyBand band : frequencyBandsList) {
                band.normalizedAmplitude = 0.0;
            }
        } else {
            for (int i = 0; i < frequencyBandsList.size(); i++) {
                FrequencyBand band = frequencyBandsList.get(i);
                double relative = dampedAverages[i] / maxDampedAverage;
                band.normalizedAmplitude = Math.max(0.0, Math.min(NORMALIZED_SCALE, relative * NORMALIZED_SCALE));
            }
        }
        
        // Notify listener if available
        if (analysisListener != null) {
            analysisListener.onFrequencyAnalysisUpdated(frequencyBandsList);
        }
    }
    
    /**
     * Log frequency bands analysis results to logcat (only bands, not individual bins)
     */
    private void logFrequencyBands() {
        if (frequencyBandsList == null || frequencyBandsList.isEmpty()) {
            return;
        }
        
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("=== Frequency Bands Analysis (").append(bufferSizeMs).append("ms buffer) ===\n");
        
        // Log only frequency bands with significant activity
        int activeBands = 0;
        for (FrequencyBand band : frequencyBandsList) {
            if (band.normalizedAmplitude > 0.1) { // Only show bands with amplitude > 0.1
                logMessage.append(band.toString()).append("\n");
                activeBands++;
            }
        }
        
        if (activeBands == 0) {
            logMessage.append("No significant frequency activity detected\n");
        }
        
        logMessage.append("=====================================");
        
        Log.d(TAG, logMessage.toString());
    }
    
    /**
     * Create a Hanning window function for better frequency resolution
     * @param size Window size
     * @return Window function array
     */
    private double[] createHanningWindow(int size) {
        double[] window = new double[size];
        for (int i = 0; i < size; i++) {
            window[i] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (size - 1)));
        }
        return window;
    }
    
    /**
     * Find the next power of 2 greater than or equal to the given number
     * @param n Input number
     * @return Next power of 2
     */
    private int nextPowerOf2(int n) {
        int power = 1;
        while (power < n) {
            power *= 2;
        }
        return power;
    }
    
    /**
     * Get the current sample rate
     */
    public int getSampleRate() {
        return SAMPLE_RATE;
    }
    
    /**
     * Check if recording is currently active
     */
    public boolean isRecording() {
        return isRecording.get();
    }
    
    /**
     * Get frequency resolution in Hz
     */
    public double getFrequencyResolution() {
        return (double) SAMPLE_RATE / bufferSizeSamples;
    }
    
    /**
     * Get the update interval in milliseconds based on buffer size
     */
    public int getUpdateIntervalMs() {
        return bufferSizeMs;
    }
}
