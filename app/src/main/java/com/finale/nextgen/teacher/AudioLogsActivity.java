package com.finale.nextgen.teacher;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AudioLogsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AudioLogsAdapter adapter;
    private TextView tvNoLogs, tvStudentInfo, tvLogCount, tvRecommendation;
    private String examId;
    private String studentId;
    private String studentName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_logs);

        // Get intent data
        examId = getIntent().getStringExtra("examId");
        studentId = getIntent().getStringExtra("studentId");
        studentName = getIntent().getStringExtra("studentName");

        if (examId == null || studentId == null) {
            Toast.makeText(this, "Missing exam or student information", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        recyclerView = findViewById(R.id.recyclerAudioLogs);
        tvNoLogs = findViewById(R.id.tvNoLogs);
        tvStudentInfo = findViewById(R.id.tvStudentInfo);
        tvLogCount = findViewById(R.id.tvLogCount);
        tvRecommendation = findViewById(R.id.tvRecommendation);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set title and student info
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Audio Detection Logs");
        }

        tvStudentInfo.setText("Student: " + (studentName != null ? studentName : studentId));

        // Load logs
        loadAudioLogs();
    }

    private void loadAudioLogs() {
        DatabaseReference logsRef = FirebaseDatabase.getInstance()
                .getReference("ExamAudioLogs")
                .child(examId)
                .child(studentId)
                .child("events");

        logsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<AudioLogModel> logs = new ArrayList<>();

                for (DataSnapshot eventSnapshot : snapshot.getChildren()) {
                    AudioLogModel log = eventSnapshot.getValue(AudioLogModel.class);
                    if (log != null) {
                        logs.add(log);
                    }
                }

                // Sort by timestamp (newest first)
                Collections.sort(logs, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                if (logs.isEmpty()) {
                    recyclerView.setVisibility(RecyclerView.GONE);
                    tvNoLogs.setVisibility(TextView.VISIBLE);
                    tvLogCount.setText("Total Strikes: 0");
                    tvRecommendation.setVisibility(TextView.GONE);
                } else {
                    recyclerView.setVisibility(RecyclerView.VISIBLE);
                    tvNoLogs.setVisibility(TextView.GONE);
                    tvLogCount.setText("Total Strikes: " + logs.size());
                    
                    // Generate interpretation for each log
                    for (AudioLogModel log : logs) {
                        log.generateInterpretation();
                    }
                    
                    // Analyze and generate recommendation
                    String recommendation = generateRecommendation(logs);
                    tvRecommendation.setText(recommendation);
                    tvRecommendation.setVisibility(TextView.VISIBLE);

                    adapter = new AudioLogsAdapter(logs);
                    recyclerView.setAdapter(adapter);
                }

                Log.d("AudioLogsActivity", "Loaded " + logs.size() + " audio logs for student " + studentId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("AudioLogsActivity", "Failed to load audio logs: " + error.getMessage());
                Toast.makeText(AudioLogsActivity.this, "Failed to load logs: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String generateRecommendation(List<AudioLogModel> logs) {
        if (logs.isEmpty()) return "";
        
        // Analyze patterns
        int highSuspicion = 0;
        int moderateSuspicion = 0;
        int lowSuspicion = 0;
        int veryClose = 0;
        int nearby = 0;
        int distant = 0;
        
        for (AudioLogModel log : logs) {
            int suspicion = log.getSuspicionLevel();
            if (suspicion >= 4) highSuspicion++;
            else if (suspicion == 3) moderateSuspicion++;
            else lowSuspicion++;
            
            String category = log.getCategory();
            if (category != null) {
                if (category.contains("Very Close")) veryClose++;
                else if (category.contains("Nearby")) nearby++;
                else distant++;
            }
        }
        
        StringBuilder sb = new StringBuilder("📋 TEACHER RECOMMENDATION:\n\n");
        
        // Calculate percentage of high suspicion
        float highPercent = (float) highSuspicion / logs.size() * 100;
        
        if (highPercent >= 60) {
            // Majority are high suspicion
            sb.append("⚠️ HIGH CONFIDENCE - Likely Cheating\n\n");
            sb.append("• " + highSuspicion + "/" + logs.size() + " strikes are high/very high suspicion\n");
            sb.append("• " + (veryClose + nearby) + " detections were close to device (within 4 feet)\n");
            sb.append("• Pattern suggests sustained nearby conversation\n\n");
            sb.append("SUGGESTED ACTION: Review exam carefully. Consider score reduction or interview student.");
            
        } else if (highPercent >= 30 || (moderateSuspicion > lowSuspicion)) {
            // Mixed results
            sb.append("🤔 MODERATE CONFIDENCE - Investigation Needed\n\n");
            sb.append("• High suspicion: " + highSuspicion + "/" + logs.size() + "\n");
            sb.append("• Moderate: " + moderateSuspicion + "/" + logs.size() + "\n");
            sb.append("• Low: " + lowSuspicion + "/" + logs.size() + "\n\n");
            
            if (distant > (veryClose + nearby)) {
                sb.append("• Many detections were distant (possible classroom noise)\n");
            }
            
            sb.append("SUGGESTED ACTION: Interview student to understand context. Consider minor deduction if pattern is concerning.");
            
        } else {
            // Mostly false positives
            sb.append("✅ LOW CONFIDENCE - Likely False Positives\n\n");
            sb.append("• " + lowSuspicion + "/" + logs.size() + " strikes are low suspicion\n");
            sb.append("• " + distant + " detections were distant (classroom announcements, etc.)\n");
            sb.append("• Pattern suggests environmental noise, not student speech\n\n");
            sb.append("SUGGESTED ACTION: Ignore strikes. Consider adjusting detection sensitivity for future exams.");
        }
        
        return sb.toString();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

