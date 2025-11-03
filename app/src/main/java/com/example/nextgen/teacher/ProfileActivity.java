package com.example.nextgen.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.nextgen.R;
import com.google.android.material.appbar.MaterialToolbar;

public class ProfileActivity extends AppCompatActivity {

    // Declare views
    private TextView tvTeacherId, tvFullName, tvEmail, tvBirthday, tvCourse, tvSubjects, tvAddress, tvPhone;
    private ImageView profileImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Toolbar (Back button)
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        topAppBar.setNavigationOnClickListener(v -> finish());

        // Bind all views
        tvTeacherId = findViewById(R.id.tvTeacherId);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvCourse = findViewById(R.id.tvCourse);
        tvSubjects = findViewById(R.id.tvSubjects);
        tvAddress = findViewById(R.id.tvAddress);
        tvPhone = findViewById(R.id.tvPhone);
        profileImage = findViewById(R.id.profileImage);

        // Get data from Intent
        Intent intent = getIntent();
        String teacherId = intent.getStringExtra("teacherId");
        String fullName = intent.getStringExtra("fullName");
        String email = intent.getStringExtra("email");
        String birthday = intent.getStringExtra("birthday");
        String course = intent.getStringExtra("course");
        String subjects = intent.getStringExtra("subjects");
        String address = intent.getStringExtra("address");
        String phone = intent.getStringExtra("phone");
        String profileImageUrl = intent.getStringExtra("profileImage");

        // Display top info (clean header style)
        if (fullName != null) tvFullName.setText(fullName);
        if (email != null) tvEmail.setText(email);

        // Display detailed info
        if (teacherId != null) tvTeacherId.setText("Teacher ID: " + teacherId);
        if (birthday != null) tvBirthday.setText("Birthday: " + birthday);
        if (course != null) tvCourse.setText("Course: " + course);
        if (subjects != null) tvSubjects.setText("Subjects: " + subjects);
        if (address != null) tvAddress.setText("Address: " + address);
        if (phone != null) tvPhone.setText("Phone: " + phone);

        // Load profile image if provided (requires Glide dependency)
        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(profileImageUrl)
                    .placeholder(R.drawable.kc) // default image
                    .into(profileImage);
        }
    }
}
