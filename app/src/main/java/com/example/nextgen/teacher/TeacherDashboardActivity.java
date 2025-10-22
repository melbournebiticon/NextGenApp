package com.example.nextgen.teacher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class TeacherDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    TextView tvTeacherId, tvFullName, tvEmail, tvBirthday, tvCourse, tvSubjects;
    Button logoutBtn;

    SessionManager sessionManager;
    DatabaseReference teachersRef;

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    Toolbar toolbar;

    // Header
    TextView navHeaderUsername, navHeaderEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_dashboard);

        // Toolbar + Drawer setup
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);

        // Access header layout
        View headerView = navigationView.getHeaderView(0);
        navHeaderUsername = headerView.findViewById(R.id.nav_header_username);
        navHeaderEmail = headerView.findViewById(R.id.nav_header_email);

        // Initialize Firebase + Session
        tvTeacherId = findViewById(R.id.tvTeacherId);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvCourse = findViewById(R.id.tvCourse);
        tvSubjects = findViewById(R.id.tvSubjects);
        logoutBtn = findViewById(R.id.logoutBtn);

        sessionManager = new SessionManager(this);
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");

        String teacherId = sessionManager.getUserId();
        if (teacherId != null) {
            loadTeacherInfo(teacherId);
        } else {
            Toast.makeText(this, "Teacher ID not found!", Toast.LENGTH_SHORT).show();
        }

        logoutBtn.setOnClickListener(v -> logout());
    }

    private void loadTeacherInfo(String teacherId) {
        teachersRef.child(teacherId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String id = snapshot.child("id").getValue(String.class);
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String displayName = snapshot.child("displayName").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String birthday = snapshot.child("birthday").getValue(String.class);
                    String courseDisplay = snapshot.child("courseDisplay").getValue(String.class);
                    List<String> subjects = (List<String>) snapshot.child("assignedSubjects").getValue();

                    tvTeacherId.setText(id);
                    tvFullName.setText(fullName + " (" + displayName + ")");
                    tvEmail.setText(email);
                    tvBirthday.setText(birthday);
                    tvCourse.setText(courseDisplay);

                    if (subjects != null && !subjects.isEmpty()) {
                        tvSubjects.setText(String.join(", ", subjects));
                    } else {
                        tvSubjects.setText("No subjects assigned");
                    }

                    navHeaderUsername.setText(displayName != null ? displayName : "Teacher");
                    navHeaderEmail.setText(email != null ? email : "No email");
                } else {
                    Toast.makeText(TeacherDashboardActivity.this, "Teacher info not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherDashboardActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ✅ Handle navigation menu clicks
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_manage_exam) {
            startActivity(new Intent(this, ManageExamActivity.class));

        } else if (id == R.id.nav_view_results) {
            startActivity(new Intent(this, ViewStudentsActivity.class));

        } else if (id == R.id.nav_view_profile) {
            startActivity(new Intent(this, ProfileActivity.class));

        } else if (id == R.id.nav_logout) {
            // Clear shared pref for logged user
            SharedPreferences prefs = getSharedPreferences("user_data", MODE_PRIVATE);
            prefs.edit().remove("logged_user").apply();

            FirebaseAuth.getInstance().signOut();
            sessionManager.clearSession();

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void logout() {
        sessionManager.clearSession();
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void
    onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
