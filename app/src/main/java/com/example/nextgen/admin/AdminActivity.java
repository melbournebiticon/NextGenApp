package com.example.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.view.MenuItem; // <-- ADD THIS


public class AdminActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

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

        // Initialize Toolbar and Navigation Drawer
        initializeToolbarAndNavigation();

        // Initialize Dashboard counters
        initializeDashboard();

        // Load counts from Firebase
        loadDashboardCounts();

        // Initialize Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_academics) {
                startActivity(new Intent(this, AcademicsActivity.class));
                return true;
            } else if (id == R.id.nav_teachers) {
                startActivity(new Intent(this, TeacherActivity.class));
                return true;
            } else if (id == R.id.nav_students) {
                startActivity(new Intent(this, StudentActivity.class));
                return true;
            }
            return false;
        });

    }

    private void initializeFirebaseReferences() {
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        examsRef = FirebaseDatabase.getInstance().getReference("Exams");
        studentsRef = FirebaseDatabase.getInstance().getReference("Students");
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
    }

    private void initializeToolbarAndNavigation() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Admin Dashboard");
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
    }

    private void initializeDashboard() {
        totalCourses = findViewById(R.id.totalCourses);
        totalExams = findViewById(R.id.totalExams);
        totalExaminees = findViewById(R.id.totalExaminees);
        totalTeachers = findViewById(R.id.totalTeachers);

        // Set initial values to 0
        totalCourses.setText("0");
        totalExams.setText("0");
        totalExaminees.setText("0");
        totalTeachers.setText("0");
    }

    private void loadDashboardCounts() {
        loadCountFromFirebase(coursesRef, totalCourses, "courses");
        loadCountFromFirebase(examsRef, totalExams, "exams");
        loadCountFromFirebase(studentsRef, totalExaminees, "examinees");
        loadCountFromFirebase(teachersRef, totalTeachers, "teachers");
    }

    private void loadCountFromFirebase(DatabaseReference ref, TextView textView, String label) {
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                textView.setText(String.valueOf(count));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AdminActivity.this, "Failed to load " + label + " count", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        drawerLayout.closeDrawer(GravityCompat.START);

        if (id == R.id.nav_academics) {
            startActivity(new Intent(this, AcademicsActivity.class));
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
        SessionManager sessionManager = new SessionManager(this);
        sessionManager.clearSession();
        FirebaseAuth.getInstance().signOut();

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

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardCounts();
    }
}
