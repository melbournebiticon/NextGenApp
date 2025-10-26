package com.example.nextgen.student;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.example.nextgen.admin.StudentModel;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class StudentDashboardActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "StudentDashboard";

    // --- Navigation Drawer Fields (KEEP) ---
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private TextView navHeaderFullName, navHeaderEmail;
    private ImageView navHeaderProfileImage;
    // ----------------------------------------

    private Button btnLogout;

    private FirebaseAuth auth;
    private DatabaseReference studentsRef;

    private RecyclerView rvExams;
    private ExamAdapter examAdapter;
    private List<ExamModel> examList = new ArrayList<>();
    private DatabaseReference examsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        // --- 1. SETUP TOOLBAR AND NAVIGATION DRAWER ---
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        // ----------------------------------------------

        // --- 2. INITIALIZE NAVIGATION HEADER COMPONENTS ---
        View headerView = navigationView.getHeaderView(0);
        navHeaderFullName = headerView.findViewById(R.id.nav_header_full_name);
        navHeaderEmail = headerView.findViewById(R.id.nav_header_email);
        navHeaderProfileImage = headerView.findViewById(R.id.nav_header_profile_image);
        // --------------------------------------------------


        // RecyclerView for exams
        rvExams = findViewById(R.id.rvExams);
        rvExams.setLayoutManager(new LinearLayoutManager(this));

        examsRef = FirebaseDatabase.getInstance().getReference("Exams");

        // Initialize Main Content UI
        btnLogout = findViewById(R.id.logoutBtn);

        // Firebase Auth
        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No logged-in user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        studentsRef = FirebaseDatabase.getInstance().getReference("Students");

        // Fetch student data
        studentsRef.orderByChild("uid").equalTo(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                StudentModel student = ds.getValue(StudentModel.class);
                                if (student != null) {
                                    populateStudentData(student);
                                    fetchExamsForStudent(student);
                                }
                            }
                        } else {
                            Toast.makeText(StudentDashboardActivity.this, "Student record not found", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(StudentDashboardActivity.this, "Failed to fetch data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        // Logout Button listener
        btnLogout.setOnClickListener(v -> handleLogout());
    }

    // --- 3. Implement Navigation Item Click Handler (UPDATED) ---
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            // Do nothing, we are here
        } else if (id == R.id.nav_view_profile) {
            // <<< START STUDENT PROFILE ACTIVITY HERE >>>
            Intent intent = new Intent(this, StudentProfileActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_view_scores) {
            // TODO: Start View Scores/Exam History Activity
            Toast.makeText(this, "Opening Exam Scores/History", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_logout) {
            handleLogout();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    // --- 4. Handle Back Button Press (No Change) ---
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // --- 5. Centralized Logout Logic (No Change) ---
    private void handleLogout() {
        new SessionManager(this).clearSession();
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // --- 6. populateStudentData (No Change) ---
    private void populateStudentData(StudentModel student) {
        // Navigation Header UI
        if (navHeaderFullName != null) {
            navHeaderFullName.setText(student.getFullName());
        }
        if (navHeaderEmail != null) {
            navHeaderEmail.setText(student.getEmail());
        }

        // Profile Image Logic (Focus only on Nav Header Image)
        if (student.getProfileImage() != null && !student.getProfileImage().isEmpty()) {
            try {
                byte[] decodedBytes = android.util.Base64.decode(student.getProfileImage(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                if (navHeaderProfileImage != null) {
                    navHeaderProfileImage.setImageBitmap(bitmap);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error decoding image: " + e.getMessage());
                if (navHeaderProfileImage != null) {
                    navHeaderProfileImage.setImageResource(R.drawable.examinee_default);
                }
            }
        } else {
            if (navHeaderProfileImage != null) {
                navHeaderProfileImage.setImageResource(R.drawable.examinee_default);
            }
        }
    }

    // ===== Fetch exams assigned to this student's course/section (No Change) =====
    private void fetchExamsForStudent(StudentModel student) {
        // Build the courseDisplay string to match Firebase
        String studentCourseDisplay = student.getCourseName()
                + " - " + student.getSpecializationName()
                + " - " + student.getYearName()
                + " - " + student.getSectionName();

        Log.d("DEBUG_COURSE_DISPLAY", "Querying exams for: " + studentCourseDisplay);

        examsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                examList.clear();
                for (DataSnapshot teacherSnap : snapshot.getChildren()) {
                    for (DataSnapshot examSnap : teacherSnap.getChildren()) {
                        ExamModel exam = examSnap.getValue(ExamModel.class);
                        if (exam != null &&
                                exam.getCourseDisplay().equals(studentCourseDisplay) &&
                                exam.isActive()) {
                            examList.add(exam);
                        }
                    }
                }
                if (examList.isEmpty()) {
                    Toast.makeText(StudentDashboardActivity.this, "No exams found for your course.", Toast.LENGTH_SHORT).show();
                }
                examAdapter = new ExamAdapter(StudentDashboardActivity.this, examList);
                rvExams.setAdapter(examAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }
}