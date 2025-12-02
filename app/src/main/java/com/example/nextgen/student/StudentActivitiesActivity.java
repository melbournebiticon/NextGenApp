package com.example.nextgen.student;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentActivitiesActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    SessionManager sessionManager;
    DatabaseReference activitiesRef, submissionsRef;
    List<ActivityModel> activityList;
    ActivitiesAdapter adapter;

    TextView tvSubjectCode, tvSubjectName, tvTeacherName;
    Button btnPerformance;

    // Map to preload submissions
    Map<String, SubmissionModel> submissionMap = new HashMap<>();
    String studentId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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
        studentId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        adapter = new ActivitiesAdapter(this, activityList, submissionMap);
        recyclerView.setAdapter(adapter);

        activitiesRef = FirebaseDatabase.getInstance().getReference("Activities");
        submissionsRef = FirebaseDatabase.getInstance().getReference("Submissions").child(studentId);

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
                                Log.d("StudentActivities", "Loaded activity: " + activity.getTitle() +
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

    private void preloadSubmissions() {
        submissionMap.clear();
        submissionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot activitySnap : snapshot.getChildren()) {
                    SubmissionModel submission = activitySnap.getValue(SubmissionModel.class);
                    if (submission != null) {
                        submissionMap.put(activitySnap.getKey(), submission);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
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
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_student, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
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

            // Use preloaded submission map
            SubmissionModel submission = submissionMap.get(activity.getActivityId());
            if (submission == null) {
                setChip(holder, "Pending", R.color.dark_blue_700, R.drawable.ic_clock);
            } else if (Boolean.TRUE.equals(submission.getResubmitRequested())) {
                setChip(holder, "Resubmit Requested", R.color.teal_700, R.drawable.ic_reset);
            } else if (submission.getScore() == null || submission.getScore().trim().isEmpty()) {
                setChip(holder, "Submitted", R.color.teal_700, R.drawable.ic_upload);
            } else {
                try {
                    int score = Integer.parseInt(submission.getScore());
                    setChip(holder, "Done (" + score + "/" + maxScoreInt + ")", R.color.teal_700, R.drawable.ic_check_circle);
                } catch (Exception e) {
                    setChip(holder, "Submitted", R.color.teal_700, R.drawable.ic_upload);
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

        private void setChip(ViewHolder holder, String text, int colorRes, int iconRes) {
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
        private String score;
        private Boolean resubmitRequested;

        public SubmissionModel() {}

        public String getScore() { return score; }
        public Boolean getResubmitRequested() { return resubmitRequested; }
    }
}
