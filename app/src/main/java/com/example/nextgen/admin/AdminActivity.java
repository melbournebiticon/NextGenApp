package com.example.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
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

public class AdminActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    // Dashboard counters
    private TextView totalCourses, totalExams, totalExaminees, totalTeachers;

    // Firebase references
    private DatabaseReference coursesRef, examsRef, studentsRef, teachersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Initialize Firebase references
        initializeFirebaseReferences();

        // Initialize Toolbar and Navigation
        initializeToolbarAndNavigation();

        // Initialize Dashboard Counters
        initializeDashboard();

        // Load actual counts from Firebase
        loadDashboardCounts();
    }

    private void initializeFirebaseReferences() {
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        examsRef = FirebaseDatabase.getInstance().getReference("Exams");
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
    }

    private void initializeToolbarAndNavigation() {
        // Setup Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Set toolbar title to include ADMIN
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Admin Dashboard");
        }

        // Setup Drawer Layout and Navigation
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Setup toggle button
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Set navigation item selected listener
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void initializeDashboard() {
        totalCourses = findViewById(R.id.totalCourses);
        totalExams = findViewById(R.id.totalExams);
        totalExaminees = findViewById(R.id.totalExaminees);
        totalTeachers = findViewById(R.id.totalTeachers);

        // Set initial placeholder values
        if (totalCourses != null) totalCourses.setText("0");
        if (totalExams != null) totalExams.setText("0");
        if (totalExaminees != null) totalExaminees.setText("0");
        if (totalTeachers != null) totalTeachers.setText("0");
    }

    private void loadDashboardCounts() {
        // Load Courses Count
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                totalCourses.setText(String.valueOf(count));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AdminActivity.this, "Failed to load courses count", Toast.LENGTH_SHORT).show();
            }
        });

        // Load Exams Count
        examsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                totalExams.setText(String.valueOf(count));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AdminActivity.this, "Failed to load exams count", Toast.LENGTH_SHORT).show();
            }
        });

        // Load Students/Examinees Count
        studentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                totalExaminees.setText(String.valueOf(count));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AdminActivity.this, "Failed to load examinees count", Toast.LENGTH_SHORT).show();
            }
        });

        // Load Teachers Count
        teachersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                totalTeachers.setText(String.valueOf(count));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AdminActivity.this, "Failed to load teachers count", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        // Close drawer first
        drawerLayout.closeDrawer(GravityCompat.START);

        // Handle navigation item clicks
        if (id == R.id.nav_dashboard) {
            // Already on dashboard, do nothing
            return true;
        } else if (id == R.id.nav_specializations) {
            startActivity(new Intent(this, SpecializationsActivity.class));
        } else if (id == R.id.nav_years) {
            startActivity(new Intent(this, YearsActivity.class));
        } else if (id == R.id.nav_sections) {
            startActivity(new Intent(this, SectionsActivity.class));
        } else if (id == R.id.nav_courses) {
            startActivity(new Intent(this, CourseActivity.class));
        } else if (id == R.id.nav_subjects) {
            startActivity(new Intent(this, SubjectActivity.class));
        } else if (id == R.id.nav_teachers) {
            startActivity(new Intent(this, TeacherActivity.class));
        } else if (id == R.id.nav_students) {
            startActivity(new Intent(this, StudentActivity.class));
        } else if (id == R.id.nav_logout) {
            performLogout();
        }

        return true;
    }

    private void performLogout() {
        // Clear session
        SessionManager sessionManager = new SessionManager(this);
        sessionManager.clearSession();

        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut();

        // Redirect to login
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // Optional: Refresh counts when activity resumes
    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardCounts();
    }
}