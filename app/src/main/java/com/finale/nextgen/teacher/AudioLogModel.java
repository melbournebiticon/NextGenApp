package com.finale.nextgen.teacher;

public class AudioLogModel {
    private long timestamp;
    private String label;
    private float confidence;
    private float frameRms;
    private float ambientRms;
    private float absoluteDelta;
    private float snrRatio;
    private int strikeNumber;
    private String deviceModel;
    private int androidVersion;
    private int accumulatedMs;
    
    // New fields for better context
    private String interpretation; // AI-generated interpretation
    private String category; // "Nearby Conversation", "Distant Speech", "Whispering", etc.
    private int suspicionLevel; // 1-5 (1=likely false positive, 5=highly suspicious)
    private String transcription; // What was actually said (if available)
    private String audioContext; // Additional context about the audio

    public AudioLogModel() {
        // Default constructor required for Firebase
    }

    public AudioLogModel(long timestamp, String label, float confidence, float frameRms,
                         float ambientRms, float absoluteDelta, float snrRatio, int strikeNumber,
                         String deviceModel, int androidVersion, int accumulatedMs) {
        this.timestamp = timestamp;
        this.label = label;
        this.confidence = confidence;
        this.frameRms = frameRms;
        this.ambientRms = ambientRms;
        this.absoluteDelta = absoluteDelta;
        this.snrRatio = snrRatio;
        this.strikeNumber = strikeNumber;
        this.deviceModel = deviceModel;
        this.androidVersion = androidVersion;
        this.accumulatedMs = accumulatedMs;
    }

    // Getters and Setters
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public float getFrameRms() { return frameRms; }
    public void setFrameRms(float frameRms) { this.frameRms = frameRms; }

    public float getAmbientRms() { return ambientRms; }
    public void setAmbientRms(float ambientRms) { this.ambientRms = ambientRms; }

    public float getAbsoluteDelta() { return absoluteDelta; }
    public void setAbsoluteDelta(float absoluteDelta) { this.absoluteDelta = absoluteDelta; }

    public float getSnrRatio() { return snrRatio; }
    public void setSnrRatio(float snrRatio) { this.snrRatio = snrRatio; }

    public int getStrikeNumber() { return strikeNumber; }
    public void setStrikeNumber(int strikeNumber) { this.strikeNumber = strikeNumber; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    public int getAndroidVersion() { return androidVersion; }
    public void setAndroidVersion(int androidVersion) { this.androidVersion = androidVersion; }

    public int getAccumulatedMs() { return accumulatedMs; }
    public void setAccumulatedMs(int accumulatedMs) { this.accumulatedMs = accumulatedMs; }

    public String getInterpretation() { return interpretation; }
    public void setInterpretation(String interpretation) { this.interpretation = interpretation; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getSuspicionLevel() { return suspicionLevel; }
    public void setSuspicionLevel(int suspicionLevel) { this.suspicionLevel = suspicionLevel; }

    public String getTranscription() { return transcription; }
    public void setTranscription(String transcription) { this.transcription = transcription; }

    public String getAudioContext() { return audioContext; }
    public void setAudioContext(String audioContext) { this.audioContext = audioContext; }
    
    // Helper method to generate interpretation based on metrics
    public void generateInterpretation() {
        if (interpretation != null && !interpretation.isEmpty()) {
            return; // Already set from Firebase
        }
        
        // Analyze metrics to provide context
        StringBuilder sb = new StringBuilder();
        
        // 1. Proximity detection based on RMS and Delta
        if (absoluteDelta > 0.08f && frameRms > 0.15f) {
            category = "Very Close Speech";
            sb.append("📍 Very close to device (within 1-2 feet). ");
            suspicionLevel = 5; // Highly suspicious
        } else if (absoluteDelta > 0.05f && frameRms > 0.08f) {
            category = "Nearby Conversation";
            sb.append("📍 Nearby speech detected (2-4 feet away). ");
            suspicionLevel = 4; // Suspicious
        } else if (absoluteDelta > 0.03f && frameRms > 0.04f) {
            category = "Moderate Distance Speech";
            sb.append("📍 Moderate distance speech (4-8 feet). ");
            suspicionLevel = 3; // Uncertain
        } else if (frameRms > ambientRms * 3) {
            category = "Distant Speech/Announcement";
            sb.append("📍 Distant speech or classroom announcement. ");
            suspicionLevel = 2; // Likely false positive
        } else {
            category = "Ambient Noise";
            sb.append("📍 Low-level ambient noise. ");
            suspicionLevel = 1; // Very likely false positive
        }
        
        // 2. Analyze SNR for context
        if (snrRatio > 20f) {
            sb.append("Very clear audio signal. ");
        } else if (snrRatio > 10f) {
            sb.append("Clear audio above background noise. ");
        } else if (snrRatio > 4f) {
            sb.append("Audio slightly above background. ");
        } else {
            sb.append("Audio barely above background (possible false positive). ");
            suspicionLevel = Math.max(1, suspicionLevel - 1); // Reduce suspicion
        }
        
        // 3. Sustained detection analysis
        if (accumulatedMs >= 4000) {
            sb.append("Sustained for 4+ seconds (required for strike). ");
        } else if (accumulatedMs >= 2000) {
            sb.append("Sustained for 2-4 seconds. ");
        }
        
        // 4. Confidence analysis
        if (confidence > 0.9f) {
            sb.append("Model very confident it's human speech.");
        } else if (confidence > 0.75f) {
            sb.append("Model confident it's human speech.");
        } else {
            sb.append("Model moderately confident (could be other sounds).");
            suspicionLevel = Math.max(1, suspicionLevel - 1); // Reduce suspicion
        }
        
        interpretation = sb.toString();
    }
    
    // Get suspicion level as text
    public String getSuspicionLevelText() {
        switch (suspicionLevel) {
            case 5: return "Very High - Likely Cheating";
            case 4: return "High - Suspicious";
            case 3: return "Moderate - Uncertain";
            case 2: return "Low - Likely Legitimate";
            case 1: return "Very Low - Likely False Positive";
            default: return "Unknown";
        }
    }
    
    // Get color for suspicion level
    public int getSuspicionColor() {
        switch (suspicionLevel) {
            case 5: return 0xFFFF5252; // Red
            case 4: return 0xFFFF9800; // Orange
            case 3: return 0xFFFFCA28; // Yellow
            case 2: return 0xFF66BB6A; // Light green
            case 1: return 0xFF4CAF50; // Green
            default: return 0xFF9E9E9E; // Gray
        }
    }
}

