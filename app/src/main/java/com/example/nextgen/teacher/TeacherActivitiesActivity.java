package com.example.nextgen.teacher;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class TeacherActivitiesActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    ActivityAdapter adapter;
    List<ActivityModel> activityList = new ArrayList<>();
    DatabaseReference activitiesRef;
    SessionManager sessionManager;

    // ACTION LISTENER (View – Edit – Delete)
    public interface ActivityActionListener {
        void onViewSubmissions(ActivityModel activity);
        void onEditActivity(ActivityModel activity);
        void onDeleteActivity(ActivityModel activity);
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_activities);

        recyclerView = findViewById(R.id.recyclerViewActivities);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        sessionManager = new SessionManager(this);
        activitiesRef = FirebaseDatabase.getInstance().getReference("Activities");

        adapter = new ActivityAdapter(activityList, new ActivityActionListener() {

            @Override
            public void onViewSubmissions(ActivityModel activity) {

                String courseDisplay = activity.getCourseDisplay();

                String courseName = "", specialization = "", year = "", section = "";
                if (courseDisplay != null) {
                    String[] parts = courseDisplay.split(" - ");
                    if (parts.length >= 4) {
                        courseName = parts[0];
                        specialization = parts[1];
                        year = parts[2];
                        section = parts[3];
                    }
                }

                Intent subIntent = new Intent(TeacherActivitiesActivity.this, SubjectSectionActivity.class);
                subIntent.putExtra("courseName", courseName);
                subIntent.putExtra("specializationName", specialization);
                subIntent.putExtra("yearName", year);
                subIntent.putExtra("sectionName", section);
                subIntent.putExtra("activityId", activity.getId());
                startActivity(subIntent);
            }

            @Override
            public void onEditActivity(ActivityModel activity) {

                Log.d("ActivitiesActivity", "EDIT MODE → Activity ID: " + activity.getId());

                Intent editIntent = new Intent(TeacherActivitiesActivity.this, CreateActivityActivity.class);

                editIntent.putExtra("ACTIVITY_ID", activity.getId());
                editIntent.putExtra("TITLE", activity.getTitle());
                editIntent.putExtra("DESCRIPTION", activity.getDescription());
                editIntent.putExtra("DUE_DATE", activity.getDueDate());
                editIntent.putExtra("MAX_SCORE", activity.getMaxScore());
                editIntent.putExtra("COURSE_DISPLAY", activity.getCourseDisplay());
                editIntent.putExtra("SUBJECT_NAME", activity.getSubject());

                editIntent.putExtra("SUBJECT_CODE", activity.getSubjectCode());
                editIntent.putExtra("SUBJECT_ID", activity.getSubjectId());
                editIntent.putExtra("TEACHER_NAME", activity.getTeacherName());

                editIntent.putExtra("MAIN_TERM", activity.getMainTerm());
                editIntent.putExtra("SUB_TERM", activity.getSubTerm());
                editIntent.putExtra("CREATED_AT", activity.getCreatedAt());

                startActivity(editIntent);
            }

            @Override
            public void onDeleteActivity(ActivityModel activity) {

                new AlertDialog.Builder(TeacherActivitiesActivity.this)
                        .setTitle("Delete Activity")
                        .setMessage("Are you sure you want to delete this activity?")
                        .setPositiveButton("Yes", (dialog, which) -> {

                            activitiesRef.child(activity.getId())
                                    .removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(TeacherActivitiesActivity.this, "Activity deleted", Toast.LENGTH_SHORT).show();
                                        activityList.remove(activity);
                                        adapter.notifyDataSetChanged();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(TeacherActivitiesActivity.this,
                                                    "Failed to delete: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show()
                                    );

                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String subjectId = getIntent().getStringExtra("subjectId");
        if (subjectId != null) {
            loadActivitiesForSubject(subjectId);
        }
    }

    private void loadActivitiesForSubject(String subjectId) {

        String teacherId = sessionManager.getUserId();

        activitiesRef.orderByChild("teacherId").equalTo(teacherId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        activityList.clear();

                        for (DataSnapshot snap : snapshot.getChildren()) {
                            ActivityModel activity = snap.getValue(ActivityModel.class);

                            if (activity != null) {
                                activity.setId(snap.getKey());
                            }

                            if (activity != null && subjectId.equals(activity.getSubjectId())) {
                                activityList.add(activity);
                            }
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(TeacherActivitiesActivity.this,
                                "Failed to load activities: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
