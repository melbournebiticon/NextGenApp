package com.example.nextgen.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_activities);

        recyclerView = findViewById(R.id.recyclerViewActivities);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Get subject info from intent
        Intent intent = getIntent();
        String subjectId = intent.getStringExtra("subjectId");
        String subjectName = intent.getStringExtra("subjectName");
        String subjectCode = intent.getStringExtra("subjectCode");

        adapter = new ActivityAdapter(activityList, activity -> {
            // Get courseDisplay from ActivityModel
            String courseDisplay = activity.getCourseDisplay(); // e.g., "BSIT - SD - 1 - A"

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

            // Open SubjectSectionActivity
            Intent subIntent = new Intent(TeacherActivitiesActivity.this, SubjectSectionActivity.class);
            subIntent.putExtra("courseName", courseName);
            subIntent.putExtra("specializationName", specialization);
            subIntent.putExtra("yearName", year);
            subIntent.putExtra("sectionName", section);
            subIntent.putExtra("activityId", activity.getId());

            startActivity(subIntent);
        });


        recyclerView.setAdapter(adapter);

        sessionManager = new SessionManager(this);
        activitiesRef = FirebaseDatabase.getInstance().getReference("Activities");

        loadActivitiesForSubject(subjectId);
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
                            if (activity != null && subjectId.equals(activity.getSubjectId())) {
                                activityList.add(activity);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

}

