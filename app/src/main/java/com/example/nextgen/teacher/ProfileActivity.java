package com.example.nextgen.teacher;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.nextgen.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.ArrayList;


public class ProfileActivity extends AppCompatActivity {

    private TextView tvTeacherId, tvFullName, tvEmail, tvBirthday, tvCourse, tvSubjects, tvAddress, tvPhone;
    private ImageView profileImage;

    private DatabaseReference teachersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Toolbar (Back button)
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        topAppBar.setNavigationOnClickListener(v -> finish());

        // Bind views
        tvTeacherId = findViewById(R.id.tvTeacherId);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvCourse = findViewById(R.id.tvCourse);
        tvSubjects = findViewById(R.id.tvSubjects);
        tvAddress = findViewById(R.id.tvAddress);
        tvPhone = findViewById(R.id.tvPhone);
        profileImage = findViewById(R.id.profileImage);

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
                if (snapshot.exists()) {
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String birthday = snapshot.child("birthday").getValue(String.class);
                    String profileImageUrl = snapshot.child("profileImage").getValue(String.class);

                    // Handle courseDisplays
                    String courseDisplay = "";
                    DataSnapshot courseSnapshot = snapshot.child("courseDisplays");
                    if (courseSnapshot.exists()) {
                        DataSnapshot displayNameSnap = courseSnapshot.child("displayName");
                        if (displayNameSnap.exists()) {
                            courseDisplay = displayNameSnap.getValue(String.class);
                        }
                    }

                    // Handle assignedSubjects (concatenate all subject names)
                    String subjects = "";
                    DataSnapshot subjectsSnap = snapshot.child("assignedSubjects");
                    if (subjectsSnap.exists()) {
                        List<String> subjectList = new ArrayList<>();
                        for (DataSnapshot s : subjectsSnap.getChildren()) {
                            String sub = s.getValue(String.class);
                            if (sub != null) subjectList.add(sub);
                        }
                        subjects = String.join(", ", subjectList);
                    }

                    // Set data to views
                    if (fullName != null) tvFullName.setText(fullName);
                    if (email != null) tvEmail.setText(email);
                    if (teacherId != null) tvTeacherId.setText("Teacher ID: " + teacherId);
                    if (birthday != null) tvBirthday.setText("Birthday: " + birthday);
                    if (!courseDisplay.isEmpty()) tvCourse.setText("Course: " + courseDisplay);
                    if (!subjects.isEmpty()) tvSubjects.setText("Subjects: " + subjects);

                    if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                        try {
                            // Remove possible prefixes like "data:image/jpeg;base64,"
                            String pureBase64 = profileImageUrl.replaceAll("data:image/.*?;base64,", "");
                            byte[] decodedBytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT);
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                            if (bitmap != null) {
                                profileImage.setImageBitmap(bitmap);
                            } else {
                                // Decoding failed, use default
                                profileImage.setImageResource(R.drawable.tc_profile);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            profileImage.setImageResource(R.drawable.tc_profile);
                        }
                    } else {
                        // Null or empty Base64, use default
                        profileImage.setImageResource(R.drawable.tc_profile);
                    }


                } else {
                    Toast.makeText(ProfileActivity.this, "Teacher not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Failed to load teacher data", Toast.LENGTH_SHORT).show();
            }
        });
    }

}
