package com.finale.nextgen.teacher;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AudioLogsAdapter extends RecyclerView.Adapter<AudioLogsAdapter.ViewHolder> {

    private final List<AudioLogModel> logs;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());

    public AudioLogsAdapter(List<AudioLogModel> logs) {
        this.logs = logs;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_audio_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AudioLogModel log = logs.get(position);
        
        // Generate interpretation if not already set
        log.generateInterpretation();

        // Format timestamp
        String formattedDate = dateFormat.format(new Date(log.getTimestamp()));
        holder.tvTimestamp.setText(formattedDate);

        // Strike number with badge
        holder.tvStrikeNumber.setText("Strike #" + log.getStrikeNumber());

        // Label and confidence
        holder.tvLabel.setText(log.getLabel() != null && !log.getLabel().isEmpty() ? log.getLabel() : "Unknown");
        holder.tvConfidence.setText(String.format(Locale.US, "%.2f%%", log.getConfidence() * 100));

        // Category and Interpretation (NEW!)
        String category = log.getCategory() != null ? log.getCategory() : "Unknown";
        holder.tvCategory.setText(category);
        
        String interpretation = log.getInterpretation();
        if (interpretation != null && !interpretation.isEmpty()) {
            holder.tvInterpretation.setText(interpretation);
            holder.tvInterpretation.setVisibility(View.VISIBLE);
        } else {
            holder.tvInterpretation.setVisibility(View.GONE);
        }
        
        // Suspicion Level (NEW!)
        holder.tvSuspicionLevel.setText(log.getSuspicionLevelText());
        holder.tvSuspicionLevel.setTextColor(log.getSuspicionColor());
        
        // Transcription (if available)
        String transcription = log.getTranscription();
        if (transcription != null && !transcription.isEmpty()) {
            holder.tvTranscription.setText(transcription);
            holder.tvTranscription.setVisibility(View.VISIBLE);
        } else {
            holder.tvTranscription.setVisibility(View.GONE);
        }
        
        // Audio Context (additional details)
        String audioContext = log.getAudioContext();
        if (audioContext != null && !audioContext.isEmpty()) {
            holder.tvAudioContext.setText(audioContext);
            holder.tvAudioContext.setVisibility(View.VISIBLE);
        } else {
            holder.tvAudioContext.setVisibility(View.GONE);
        }

        // RMS values
        holder.tvFrameRms.setText(String.format(Locale.US, "%.4f", log.getFrameRms()));
        holder.tvAmbientRms.setText(String.format(Locale.US, "%.4f", log.getAmbientRms()));
        holder.tvDelta.setText(String.format(Locale.US, "%.4f", log.getAbsoluteDelta()));
        holder.tvSnr.setText(String.format(Locale.US, "%.2f", log.getSnrRatio()));

        // Device info
        holder.tvDevice.setText(log.getDeviceModel() + " (Android " + log.getAndroidVersion() + ")");

        // Accumulated time
        holder.tvAccumulated.setText(log.getAccumulatedMs() + " ms");

        // Color coding based on suspicion level (more meaningful than strike number)
        int backgroundColor;
        int suspicionLevel = log.getSuspicionLevel();
        if (suspicionLevel >= 4) {
            backgroundColor = Color.parseColor("#FFCDD2"); // Light red - high suspicion
        } else if (suspicionLevel == 3) {
            backgroundColor = Color.parseColor("#FFF9C4"); // Light yellow - moderate
        } else {
            backgroundColor = Color.parseColor("#C8E6C9"); // Light green - low suspicion
        }
        holder.itemView.setBackgroundColor(backgroundColor);
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTimestamp, tvStrikeNumber, tvLabel, tvConfidence;
        TextView tvFrameRms, tvAmbientRms, tvDelta, tvSnr;
        TextView tvDevice, tvAccumulated;
        TextView tvCategory, tvInterpretation, tvSuspicionLevel; // NEW!
        TextView tvTranscription, tvAudioContext; // TRANSCRIPTION!

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvStrikeNumber = itemView.findViewById(R.id.tvStrikeNumber);
            tvLabel = itemView.findViewById(R.id.tvLabel);
            tvConfidence = itemView.findViewById(R.id.tvConfidence);
            tvFrameRms = itemView.findViewById(R.id.tvFrameRms);
            tvAmbientRms = itemView.findViewById(R.id.tvAmbientRms);
            tvDelta = itemView.findViewById(R.id.tvDelta);
            tvSnr = itemView.findViewById(R.id.tvSnr);
            tvDevice = itemView.findViewById(R.id.tvDevice);
            tvAccumulated = itemView.findViewById(R.id.tvAccumulated);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvInterpretation = itemView.findViewById(R.id.tvInterpretation);
            tvSuspicionLevel = itemView.findViewById(R.id.tvSuspicionLevel);
            tvTranscription = itemView.findViewById(R.id.tvTranscription);
            tvAudioContext = itemView.findViewById(R.id.tvAudioContext);
        }
    }
}

