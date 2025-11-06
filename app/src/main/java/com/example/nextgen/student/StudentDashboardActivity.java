package com.example.nextgen.student;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import android.os.Handler; // NEW Import for auto-refresh
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
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    // 🏆 NEW: Reference sa Scores table
    private DatabaseReference scoresRef;
    // 🏆 NEW: Variable para sa UID ng kasalukuyang estudyante
    private String currentStudentUid;


    private RecyclerView rvExams;
    private ExamAdapter examAdapter;
    private List<ExamModel> examList = new ArrayList<>();
    private DatabaseReference examsRef;

    // 🏆 FIXED: Ito ang 15-minute max login window.
    private static final long MAX_LOGIN_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(15);

    // 🏆 FIXED: 3-second (3000ms) refresh interval.
    private Handler handler = new Handler();
    private Runnable examRefreshRunnable;
    private final int REFRESH_INTERVAL = 3000;

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
        // 🏆 NEW: Initialize Scores Ref
        scoresRef = FirebaseDatabase.getInstance().getReference("Scores");


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

        // 🏆 I-store ang UID ng kasalukuyang user
        currentStudentUid = currentUser.getUid();

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
                                    // Start the periodic fetcher instead of single fetch
                                    startPeriodicExamFetch(student);
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

    // 🏆 FIXED: Logic to start the periodic fetching (Added a Log)
    private void startPeriodicExamFetch(StudentModel student) {
        // Stop any previous callback to avoid duplicates (CRITICAL)
        if (examRefreshRunnable != null) {
            handler.removeCallbacks(examRefreshRunnable);
            Log.d(TAG, "Removed previous exam refresh callbacks.");
        }

        examRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Running periodic exam fetch. Interval: " + REFRESH_INTERVAL + "ms"); // Log to confirm interval
                // Reruns the fetch logic to check current time against schedule
                fetchExamsForStudent(student);
                // Schedules itself to run again after REFRESH_INTERVAL
                handler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        // Start the initial fetch
        handler.post(examRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 🏆 IMPORTANT: Stop the handler when the activity is paused to save battery
        if (handler != null && examRefreshRunnable != null) {
            handler.removeCallbacks(examRefreshRunnable);
            Log.d(TAG, "Exam refresh stopped.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 🏆 IMPORTANT: Resume the handler when the activity comes back to foreground
        // Re-start the process only if a user is logged in
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && studentsRef != null) {
            studentsRef.orderByChild("uid").equalTo(currentUser.getUid())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                for (DataSnapshot ds : snapshot.getChildren()) {
                                    StudentModel student = ds.getValue(StudentModel.class);
                                    if (student != null) {
                                        startPeriodicExamFetch(student);
                                        Log.d(TAG, "Exam refresh resumed.");
                                    }
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Resume failed: " + error.getMessage());
                        }
                    });
        }
    }

    // --- 3. Implement Navigation Item Click Handler (NO CHANGE) ---
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

    // --- 4. Handle Back Button Press (NO CHANGE) ---
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // --- 5. Centralized Logout Logic (NO CHANGE) ---
    private void handleLogout() {
        new SessionManager(this).clearSession();
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // --- 6. populateStudentData (NO CHANGE) ---
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

    // ===== Fetch exams assigned to this student's course/section (FIXED Counter Logic) =====
    private void fetchExamsForStudent(StudentModel student) {
        String studentCourseDisplay = student.getCourseName()
                + " - " + student.getSpecializationName()
                + " - " + student.getYearName()
                + " - " + student.getSectionName();

        long currentTime = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        String studentId = student.getStudentId(); // ✅ Use teacher-side studentId

        examsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                examList.clear();

                List<DataSnapshot> eligibleExams = new ArrayList<>();
                for (DataSnapshot teacherSnap : snapshot.getChildren()) {
                    for (DataSnapshot examSnap : teacherSnap.getChildren()) {
                        ExamModel exam = examSnap.getValue(ExamModel.class);
                        Long scheduledAtLong = examSnap.child("scheduledAt").getValue(Long.class);
                        Integer durationMinutesInt = examSnap.child("durationMinutes").getValue(Integer.class);

                        if (exam != null && scheduledAtLong != null && durationMinutesInt != null &&
                                exam.getCourseDisplay().equals(studentCourseDisplay) &&
                                exam.isActive()) {
                            eligibleExams.add(examSnap);
                        }
                    }
                }

                final int totalEligibleExams = eligibleExams.size();
                final int[] examsProcessed = {0};

                if (totalEligibleExams == 0) {
                    updateExamRecyclerView();
                    return;
                }

                for (DataSnapshot examSnap : eligibleExams) {
                    ExamModel exam = examSnap.getValue(ExamModel.class);
                    String examId = examSnap.getKey();

                    exam.setExamId(examId);
                    exam.setScheduledAt(examSnap.child("scheduledAt").getValue(Long.class));
                    exam.setDurationMinutes(examSnap.child("durationMinutes").getValue(Integer.class));

                    // Check if exam has been taken
                    scoresRef.child(studentId).child(examId) // ✅ use studentId here
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot scoreSnapshot) {
                                    if (scoreSnapshot.exists()) {
                                        exam.setStatus("TAKEN");
                                        exam.setAvailable(false);
                                    } else {
                                        long start = exam.getScheduledAt();
                                        long endLogin = start + MAX_LOGIN_WINDOW_MILLIS;

                                        if (currentTime < start) {
                                            exam.setStatus("Scheduled: Starts at " + sdf.format(new Date(start)));
                                            exam.setAvailable(false);
                                        } else if (currentTime <= endLogin) {
                                            exam.setStatus("AVAILABLE NOW (Login closes at " + sdf.format(new Date(endLogin)) + ")");
                                            exam.setAvailable(true);
                                        } else {
                                            exam.setStatus("EXPIRED: Login window closed at " + sdf.format(new Date(endLogin)));
                                            exam.setAvailable(false);
                                        }
                                    }

                                    // ✅ Fetch "present" using studentId
                                    DatabaseReference examStudentRef = FirebaseDatabase.getInstance()
                                            .getReference("ExamStudents")
                                            .child(examId)
                                            .child(studentId); // ✅ use teacher-side studentId

                                    examStudentRef.child("present").addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot presentSnap) {
                                            Boolean present = presentSnap.getValue(Boolean.class);
                                            Log.d(TAG, "Fetched present value: " + present + " for exam: " + exam.getExamTitle());

                                            exam.setPresent(present != null && present);
                                            exam.setAvailable(exam.isAvailable() && exam.isPresent());

                                            examList.add(exam);
                                            examsProcessed[0]++;
                                            if (examsProcessed[0] == totalEligibleExams) {
                                                updateExamRecyclerView();
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            exam.setPresent(false);
                                            exam.setAvailable(false);
                                            examList.add(exam);
                                            examsProcessed[0]++;
                                            if (examsProcessed[0] == totalEligibleExams) {
                                                updateExamRecyclerView();
                                            }
                                        }
                                    });
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    examsProcessed[0]++;
                                    if (examsProcessed[0] == totalEligibleExams) {
                                        updateExamRecyclerView();
                                    }
                                }
                            });

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(StudentDashboardActivity.this, "Failed to fetch exams: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 🏆 NEW: Helper method to update RecyclerView after all async score checks are done
    private void updateExamRecyclerView() {
        if (examList.isEmpty()) {
            Toast.makeText(StudentDashboardActivity.this, "No active or future exams found for your course.", Toast.LENGTH_SHORT).show();
        }

        examAdapter = new ExamAdapter(StudentDashboardActivity.this, examList);
        rvExams.setAdapter(examAdapter);
        Log.d(TAG, "Exam list updated with " + examList.size() + " exams.");
    }
}