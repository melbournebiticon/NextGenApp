package com.example.nextgen.teacher;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.example.nextgen.R;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // TopAppBar with back navigation
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        topAppBar.setNavigationOnClickListener(v -> finish());

        // Bind Views
        TextView tvUsername = findViewById(R.id.tvUsername);
        TextView tvBirthday = findViewById(R.id.tvBirthday);
        TextView tvAge      = findViewById(R.id.tvAge);
        TextView tvEmail    = findViewById(R.id.tvEmail);
        TextView tvPhone    = findViewById(R.id.tvPhone);
        TextView tvLocation = findViewById(R.id.tvLocation);
        ImageView profileImage = findViewById(R.id.profileImage);

        // Fetch saved user info
        SharedPreferences prefs = getSharedPreferences("user_data", MODE_PRIVATE);
        String username = prefs.getString("username", "N/A");
        String birthday = prefs.getString("birthday", "N/A");
        String age      = prefs.getString("age", "N/A");
        String email    = prefs.getString("email", "N/A");
        String phone    = prefs.getString("phone", "N/A");
        String location = prefs.getString("location", "N/A");

        // Display values
        tvUsername.setText("Username: " + username);
        tvBirthday.setText("Birthday: " + birthday);
        tvAge.setText("Age: " + age);
        tvEmail.setText("Email: " + email);
        tvPhone.setText("Phone: " + phone);
        tvLocation.setText("Location: " + location);
    }
}
