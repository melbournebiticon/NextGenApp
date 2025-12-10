package com.finale.nextgen.student;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finale.nextgen.R;
import com.finale.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.content.Intent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StudentActivitiesActivity
 * - Preloads submissions by student and maps them by activityId (prefers latest submission using submittedAt)
 * - Refreshes submissions onResume
 * - Listens for local broadcasts ACTION_SUBMISSION_UPDATED to refresh immediately when a submission is made
 *
 * NOTE: This version safely reads submission nodes as Map<String,Object> and converts fields defensively
 * to avoid DatabaseException when Firebase stores numbers where the model expects strings.
 */
public class StudentActivitiesActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    SessionManager sessionManager;
    DatabaseReference activitiesRef, submissionsRef;
    List<ActivityModel> activityList;
    ActivitiesAdapter adapter;

    TextView tvSubjectCode, tvSubjectName, tvTeacherName;
    android.widget.Button btnPerformance;

    // Map to preload submissions keyed by activityId
    Map<String, SubmissionModel> submissionMap = new HashMap<>();
    String studentId;

    private static final String TAG = "StudentActivities";

    // Broadcast receiver to listen for submission updates from ActivityMyWorkFragment
    private final android.content.BroadcastReceiver submissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ActivityMyWorkFragment.ACTION_SUBMISSION_UPDATED.equals(action)) {
                String changedActivityId = intent.getStringExtra(ActivityMyWorkFragment.EXTRA_ACTIVITY_ID);
                Log.d(TAG, "Received submission update for activityId: " + changedActivityId);
                // Refresh submissions (will update UI)
                preloadSubmissions();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_activities);

        sessionManager = new SessionManager(this);

        // Header Views
        tvSubjectCode = findViewById(R.id.tvSubjectCode);
        tvSubjectName = findViewById(R.id.tvSubjectName);
        tvTeacherName = findViewById(R.id.tvTeacherName);
        btnPerformance = findViewById(R.id.btnPerformance);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // RecyclerView setup
        recyclerView = findViewById(R.id.recyclerStudentActivities);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        activityList = new ArrayList<>();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            studentId = null;
        }
        adapter = new ActivitiesAdapter(this, activityList, submissionMap);
        recyclerView.setAdapter(adapter);

        activitiesRef = FirebaseDatabase.getInstance().getReference("Activities");
        // Use root "Submissions" reference and query by studentId (submissions are stored under /Submissions/{submissionId})
        submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions");

        // Get intent data
        String subjectId = getIntent().getStringExtra("subjectId");
        String courseDisplay = getIntent().getStringExtra("courseDisplay");
        String subjectCode = getIntent().getStringExtra("subjectCode");
        String subjectName = getIntent().getStringExtra("subjectName");
        String teacherName = getIntent().getStringExtra("teacherName");

        if (subjectId == null || courseDisplay == null) {
            Toast.makeText(this, "No subject selected.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvSubjectCode.setText(subjectCode != null ? subjectCode : "N/A");
        tvSubjectName.setText(subjectName != null ? subjectName : "N/A");
        tvTeacherName.setText(teacherName != null ? teacherName : "N/A");

        btnPerformance.setOnClickListener(v ->
                Toast.makeText(this, "Performance screen coming soon!", Toast.LENGTH_SHORT).show()
        );

        loadStudentActivities(subjectId, courseDisplay);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Register local broadcast receiver to update list immediately on submission
        LocalBroadcastManager.getInstance(this).registerReceiver(submissionReceiver,
                new IntentFilter(ActivityMyWorkFragment.ACTION_SUBMISSION_UPDATED));
    }

    @Override
    protected void onStop() {
        // Unregister receiver
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(submissionReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Receiver unregister failed: " + e.getMessage());
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh submissions so UI shows "Submitted" right after returning
        preloadSubmissions();
    }

    private void loadStudentActivities(String subjectId, String courseDisplay) {
        activitiesRef.orderByChild("courseDisplay").equalTo(courseDisplay)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        activityList.clear();
                        for (DataSnapshot snap : snapshot.getChildren()) {
                            ActivityModel activity = snap.getValue(ActivityModel.class);
                            if (activity != null && subjectId.equals(activity.getSubjectId())) {
                                String firebasePushKey = snap.getKey();
                                activity.setActivityId(firebasePushKey);
                                activityList.add(activity);
                                Log.d(TAG, "Loaded activity: " + activity.getTitle() +
                                        ", ID: " + activity.getActivityId() +
                                        ", Max Score: " + activity.getMaxScore());
                            }
                        }

                        if (activityList.isEmpty()) {
                            Toast.makeText(StudentActivitiesActivity.this, "No activities for this subject.", Toast.LENGTH_SHORT).show();
                        }

                        // Preload submissions for the student
                        preloadSubmissions();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(StudentActivitiesActivity.this, "Failed to load activities", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Preloads submissions for the current student and maps them by activityId so the adapter can
     * display correct statuses (Submitted, Pending, Resubmit Requested, Done).
     *
     * This method now prefers the latest submission per activity by comparing submittedAt (epoch millis).
     * It reads submission nodes defensively as Map<String,Object> to avoid Firebase automatic bean conversion errors.
     */
    private void preloadSubmissions() {
        submissionMap.clear();
        if (studentId == null) {
            adapter.notifyDataSetChanged();
            return;
        }

        submissionsRef.orderByChild("studentId").equalTo(studentId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot submissionSnap : snapshot.getChildren()) {
                            Object raw = submissionSnap.getValue();
                            if (!(raw instanceof Map)) {
                                // Unexpected type, log and skip
                                Log.w(TAG, "Skipping submission node with unexpected value type: " + (raw == null ? "null" : raw.getClass()));
                                continue;
                            }

                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = (Map<String, Object>) raw;

                            SubmissionModel submission = new SubmissionModel();
                            submission.setSubmissionId(submissionSnap.getKey());
                            submission.setActivityId(asString(data.get("activityId")));
                            submission.setStudentId(asString(data.get("studentId")));
                            submission.setFileName(asString(data.get("fileName")));
                            submission.setFileData(asString(data.get("fileData")));
                            submission.setScore(asString(data.get("score")));
                            submission.setMaxScore(data.get("maxScore") == null ? null : String.valueOf(data.get("maxScore")));
                            submission.setResubmitRequested(asBoolean(data.get("resubmitRequested")));
                            submission.setSubmittedAt(asString(data.get("submittedAt"))); // store epoch millis string when available

                            // Only consider if activityId exists
                            String activityId = submission.getActivityId();
                            if (activityId == null) {
                                Log.w(TAG, "Submission " + submission.getSubmissionId() + " missing activityId, skipping");
                                continue;
                            }

                            // prefer latest submission by submittedAt (if available)
                            long newTs = parseSubmittedAt(submission.getSubmittedAt());
                            SubmissionModel existing = submissionMap.get(activityId);
                            long existingTs = existing != null ? parseSubmittedAt(existing.getSubmittedAt()) : -1;
                            if (existing == null || newTs >= existingTs) {
                                submissionMap.put(activityId, submission);
                                Log.d(TAG, "Mapped submission for activityId=" + activityId + " submissionId=" + submission.getSubmissionId() + " ts=" + newTs);
                            } else {
                                Log.d(TAG, "Ignored older submission for activityId=" + activityId + " submissionId=" + submission.getSubmissionId());
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG, "preloadSubmissions cancelled: " + error.getMessage());
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private long parseSubmittedAt(String submittedAt) {
        if (submittedAt == null) return -1;
        try {
            return Long.parseLong(submittedAt);
        } catch (NumberFormatException e) {
            // If submittedAt is not epoch millis, return -1 so it won't override an epoch timestamp
            return -1;
        }
    }

    // Helper: convert various types to String safely
    private String asString(Object o) {
        if (o == null) return null;
        try {
            return String.valueOf(o);
        } catch (Exception e) {
            Log.w(TAG, "asString conversion failed for object: " + (o == null ? "null" : o.getClass()), e);
            return null;
        }
    }

    // Helper: convert various possible representations to boolean
    private boolean asBoolean(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() != 0;
        String s = String.valueOf(o).toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    // ===== RecyclerView Adapter =====
    private static class ActivitiesAdapter extends RecyclerView.Adapter<ActivitiesAdapter.ViewHolder> {
        private final List<ActivityModel> list;
        private final Context context;
        private final Map<String, SubmissionModel> submissionMap;

        public ActivitiesAdapter(Context context, List<ActivityModel> list, Map<String, SubmissionModel> submissionMap) {
            this.context = context;
            this.list = list;
            this.submissionMap = submissionMap;
        }

        @NonNull
        @Override
        public ActivitiesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_student, parent, false);
            return new ViewHolder(view);
        }



        @Override
        public void onBindViewHolder(@NonNull ActivitiesAdapter.ViewHolder holder, int position) {
            ActivityModel activity = list.get(position);

            // Display basic data
            holder.tvTitle.setText(activity.getTitle() != null ? activity.getTitle() : "N/A");
            holder.tvDueDate.setText(activity.getDueDate() != null ? activity.getDueDate() : "N/A");
            holder.tvDescription.setText(activity.getDescription() != null ? activity.getDescription() : "N/A");

            String subject = activity.getSubject() != null ? activity.getSubject() : "N/A";
            String teacher = activity.getTeacherName() != null ? activity.getTeacherName() : "N/A";
            holder.tvTeacher.setText(subject + " • " + teacher);

            // Reset chip to loading
            setChip(holder, "Loading...", R.color.white, R.drawable.ic_clock);

            int maxScoreInt = 0;
            try {
                if (activity.getMaxScore() != null) maxScoreInt = Integer.parseInt(activity.getMaxScore());
            } catch (NumberFormatException e) { }

            // Use preloaded submission map keyed by activityId
            SubmissionModel submission = submissionMap.get(activity.getActivityId());
            if (submission == null) {
                // No submission record found for this activity and student
                setChip(holder, "Pending", R.color.dark_blue_700, R.drawable.ic_clock);
            } else if (Boolean.TRUE.equals(submission.getResubmitRequested())) {
                setChip(holder, "Resubmit Requested", R.color.teal_700, R.drawable.ic_reset);
            } else {
                // If there's a submission record, consider it "Submitted" even if score is "Pending" or empty.
                // If score exists and is numeric, show Done (score/max)
                String scoreStr = submission.getScore();
                if (scoreStr == null || scoreStr.trim().isEmpty() || "Pending".equalsIgnoreCase(scoreStr)) {
                    // student has submitted but not graded yet
                    setChip(holder, "Submitted", R.color.teal_700, R.drawable.ic_upload);
                } else {
                    try {
                        int score = Integer.parseInt(scoreStr);
                        setChip(holder, "Done (" + score + "/" + maxScoreInt + ")", R.color.teal_700, R.drawable.ic_check_circle);
                    } catch (Exception e) {
                        setChip(holder, "Submitted", R.color.teal_700, R.drawable.ic_upload);
                    }
                }
            }

            // Open ActivityDetails on click
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ActivityDetailsActivity.class);
                intent.putExtra("activityId", activity.getActivityId());
                intent.putExtra("title", activity.getTitle());
                intent.putExtra("description", activity.getDescription());
                intent.putExtra("subjectCode", activity.getSubjectCode());
                intent.putExtra("subjectName", activity.getSubject());
                intent.putExtra("teacherName", activity.getTeacherName());
                intent.putExtra("dueDate", activity.getDueDate());
                intent.putExtra("mainTerm", activity.getMainTerm());
                intent.putExtra("subTerm", activity.getSubTerm());
                intent.putExtra("maxScore", activity.getMaxScore());
                context.startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private void setChip(ActivitiesAdapter.ViewHolder holder, String text, int colorRes, int iconRes) {
            holder.chipStatus.setText(text);
            holder.chipStatus.setChipBackgroundColorResource(colorRes);
            holder.chipStatus.setChipIconResource(iconRes);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDueDate, tvDescription, tvTeacher;
            com.google.android.material.chip.Chip chipStatus;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvActivityTitle);
                tvDueDate = itemView.findViewById(R.id.tvActivityDueDate);
                tvDescription = itemView.findViewById(R.id.tvActivityDescription);
                tvTeacher = itemView.findViewById(R.id.tvActivityTeacher);
                chipStatus = itemView.findViewById(R.id.chipStatus);
            }
        }
    }

    // ===== Submission Model =====
    public static class SubmissionModel {
        private String submissionId;
        private String activityId;
        private String score;
        private Boolean resubmitRequested;
        private String submittedAt;
        private String studentId;
        private String fileName;
        private String fileData;
        private String maxScore;

        public SubmissionModel() {}

        public String getSubmissionId() { return submissionId; }
        public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

        public String getActivityId() { return activityId; }
        public void setActivityId(String activityId) { this.activityId = activityId; }

        public String getScore() { return score; }
        public void setScore(String score) { this.score = score; }

        public Boolean getResubmitRequested() { return resubmitRequested; }
        public void setResubmitRequested(Boolean resubmitRequested) { this.resubmitRequested = resubmitRequested; }

        public String getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getFileData() { return fileData; }
        public void setFileData(String fileData) { this.fileData = fileData; }

        public String getMaxScore() { return maxScore; }
        public void setMaxScore(String maxScore) { this.maxScore = maxScore; }
    }
}