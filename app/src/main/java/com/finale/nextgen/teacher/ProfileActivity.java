package com.finale.nextgen.teacher;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.finale.nextgen.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvTeacherId, tvFullName, tvEmail, tvBirthday, tvCourse, tvSubjects;
    private ImageView profileImage;

    private DatabaseReference teachersRef;
    private DatabaseReference subjectsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Toolbar (Back button)
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        topAppBar.setNavigationOnClickListener(v -> finish());

        // Bind views (address/phone removed)
        tvTeacherId = findViewById(R.id.tvTeacherId);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvCourse = findViewById(R.id.tvCourse);
        tvSubjects = findViewById(R.id.tvSubjects);
        profileImage = findViewById(R.id.profileImage);

        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");

        // Get teacherId from Intent
        String teacherId = getIntent().getStringExtra("teacherId");

        if (teacherId != null) {
            fetchTeacherData(teacherId);
        } else {
            Toast.makeText(this, "No teacher ID provided", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchTeacherData(String teacherId) {
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers").child(teacherId);

        teachersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(ProfileActivity.this, "Teacher not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                String fullName = snapshot.child("fullName").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);
                String birthday = snapshot.child("birthday").getValue(String.class);
                String profileImageUrl = snapshot.child("profileImage").getValue(String.class);

                // Handle courseDisplays (may be stored as a list)
                String courseDisplay = "";
                DataSnapshot courseSnapshot = snapshot.child("courseDisplays");
                if (courseSnapshot.exists()) {
                    List<String> displays = new ArrayList<>();
                    for (DataSnapshot ds : courseSnapshot.getChildren()) {
                        String v = ds.getValue(String.class);
                        if (v != null && !v.isEmpty()) displays.add(v);
                    }
                    courseDisplay = String.join("\n", displays);
                }

                // Collect assigned subject IDs (we will resolve to names)
                List<String> assignedSubjectIds = new ArrayList<>();
                DataSnapshot subjectsSnap = snapshot.child("assignedSubjects");
                if (subjectsSnap.exists()) {
                    for (DataSnapshot s : subjectsSnap.getChildren()) {
                        String subId = s.getValue(String.class);
                        if (subId != null) assignedSubjectIds.add(subId);
                    }
                }

                // Set immediate fields (IDs / raw data)
                if (fullName != null) tvFullName.setText(fullName);
                if (email != null) tvEmail.setText(email);
                if (teacherId != null) tvTeacherId.setText("Teacher ID: " + teacherId);
                if (birthday != null) tvBirthday.setText("Birthday: " + birthday);
                if (!courseDisplay.isEmpty()) tvCourse.setText("Course: " + courseDisplay);

                // Resolve subject IDs -> names and set tvSubjects
                resolveSubjectNamesAndDisplay(assignedSubjectIds);

                // Load profile image (Base64 stored)
                if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                    try {
                        String pureBase64 = profileImageUrl.replaceAll("data:image/.*?;base64,", "");
                        byte[] decodedBytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                        if (bitmap != null) {
                            profileImage.setImageBitmap(bitmap);
                        } else {
                            profileImage.setImageResource(R.drawable.tc_profile);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        profileImage.setImageResource(R.drawable.tc_profile);
                    }
                } else {
                    profileImage.setImageResource(R.drawable.tc_profile);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Failed to load teacher data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resolveSubjectNamesAndDisplay(List<String> assignedIds) {
        if (assignedIds == null || assignedIds.isEmpty()) {
            tvSubjects.setText("Subjects: None");
            return;
        }

        // Fetch subjects once and build id->name map, then map IDs -> names
        subjectsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, String> idToName = new HashMap<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String id = ds.getKey();
                    String name = ds.child("name").getValue(String.class);
                    if (id != null && name != null) idToName.put(id, name);
                }

                List<String> names = new ArrayList<>();
                for (String id : assignedIds) {
                    String name = idToName.get(id);
                    if (name != null) names.add(name);
                    else names.add(id); // fallback to raw id if name not found
                }

                String subjectsJoined = String.join(", ", names);
                tvSubjects.setText("Subjects: " + subjectsJoined);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // fallback: show raw IDs if we couldn't load subject names
                tvSubjects.setText("Subjects: " + String.join(", ", assignedIds));
            }
        });
    }
}