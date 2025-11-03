package com.example.nextgen.teacher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TeacherDashboardActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    // Profile info (Hidden Text Views)
    TextView tvTeacherId, tvFullName, tvEmail, tvBirthday, tvCourse, tvSubjects;

    SessionManager sessionManager;
    DatabaseReference teachersRef, examsRef;

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    Toolbar toolbar;

    // Header views for Side Navigation
    TextView navHeaderUsername, navHeaderEmail;

    // Dashboard summary
    TextView tvTeacherNameDisplay, tvTeacherIdDisplay, tvTotalExams, tvRecentExamTitle;

    // Dashboard cards (Quick Actions)
    CardView cardManageExam, cardManageExaminees, cardViewProfile;

    // NEW: Variable para hawakan ang reference ng Profile icon
    private MenuItem profileMenuItem;


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
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);

        // Access header layout
        View headerView = navigationView.getHeaderView(0);
        navHeaderUsername = headerView.findViewById(R.id.nav_header_username);
        navHeaderEmail = headerView.findViewById(R.id.nav_header_email);

        // Firebase + Session
        sessionManager = new SessionManager(this);
        teachersRef = FirebaseDatabase.getInstance().getReference("Teachers");
        examsRef = FirebaseDatabase.getInstance().getReference("Exams");

        // Initialize views
        tvTeacherId = findViewById(R.id.tvTeacherId);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvCourse = findViewById(R.id.tvCourse);
        tvSubjects = findViewById(R.id.tvSubjects);

        // Dashboard summary
        tvTeacherNameDisplay = findViewById(R.id.tvTeacherNameDisplay);
        tvTeacherIdDisplay = findViewById(R.id.tvTeacherIdDisplay);
        tvRecentExamTitle = findViewById(R.id.tvRecentExamTitle);

        // Cards
        cardManageExam = findViewById(R.id.cardManageExam);
        cardManageExaminees = findViewById(R.id.cardManageExaminees);
        cardViewProfile = findViewById(R.id.cardViewProfile);

        // Card actions
        cardManageExam.setOnClickListener(v -> startActivity(new Intent(this, ManageExamActivity.class)));
        cardManageExaminees.setOnClickListener(v -> startActivity(new Intent(this, ViewStudentsActivity.class)));
        cardViewProfile.setOnClickListener(v -> openProfile()); // Quick action card also opens profile

        // Get teacher ID from session
        String teacherId = sessionManager.getUserId();
        if (teacherId != null) {
            loadTeacherInfo(teacherId);
            loadExamData(teacherId);
        } else {
            Toast.makeText(this, "Teacher ID not found in session!", Toast.LENGTH_SHORT).show();
        }
    }


    // =================================================================
    // START: TOOLBAR PROFILE/LOGOUT MENU HANDLING (FINAL FIXED VERSION)
    // =================================================================

    /**
     * Step 4: Handles the Toolbar Profile icon and Popup Menu.
     * This version fixes the wrong ID reference and guarantees that
     * the popup appears when the profile icon is tapped.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the toolbar_action_menu.xml (contains only the profile icon)
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.toolbar_action_menu, menu);
        profileMenuItem = menu.findItem(R.id.action_profile);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        // ✅ Corrected: use R.id.action_profile (NOT R.id.id.action_profile)
        if (itemId == R.id.action_profile) {
            // Try to locate the actual view for the icon
            View anchorView = findViewById(R.id.action_profile);

            // If we can’t find it, use the toolbar itself as fallback
            if (anchorView == null) {
                anchorView = toolbar;
            }

            // Show the popup menu anchored to the icon (or toolbar)
            showProfilePopup(anchorView);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Displays the PopupMenu under the Profile icon with View Profile and Logout.
     */
    private void showProfilePopup(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);

        // Inflate popup menu layout (profile_popup_menu.xml)
        popup.getMenuInflater().inflate(R.menu.profile_popup_menu, popup.getMenu());

        // Handle popup menu item clicks
        popup.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();

            if (itemId == R.id.action_view_profile) {
                openProfile(); // open profile screen
                return true;

            } else if (itemId == R.id.action_logout) {
                logout(); // perform logout
                return true;
            }

            return false;
        });

        popup.show();
    }

    // =================================================================
    // END: TOOLBAR PROFILE/LOGOUT MENU HANDLING (FINAL FIXED VERSION)
    // =================================================================


    private void loadTeacherInfo(String teacherId) {
        teachersRef.child(teacherId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String id = snapshot.child("id").getValue(String.class);
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String birthday = snapshot.child("birthday").getValue(String.class);

                    // Handle course display list
                    String courseDisplay = "No Course";
                    if (snapshot.child("courseDisplays").exists()) {
                        Object val = snapshot.child("courseDisplays").getValue();
                        if (val instanceof List) {
                            List<String> list = (List<String>) val;
                            courseDisplay = String.join(", ", list);
                        }
                    }

                    // Handle subjects list
                    String subjectsText = "No subjects assigned";
                    if (snapshot.child("assignedSubjects").exists()) {
                        Object val = snapshot.child("assignedSubjects").getValue();
                        if (val instanceof List) {
                            List<String> list = (List<String>) val;
                            subjectsText = String.join(", ", list);
                        }
                    }

                    // Update UI
                    tvTeacherId.setText(id != null ? id : teacherId);
                    tvFullName.setText(fullName != null ? fullName : "No Name");
                    tvEmail.setText(email != null ? email : "No Email");
                    tvBirthday.setText(birthday != null ? birthday : "No Birthday");
                    tvCourse.setText(courseDisplay);
                    tvSubjects.setText(subjectsText);

                    // Header info
                    navHeaderUsername.setText(fullName != null ? fullName : "Teacher");
                    navHeaderEmail.setText(email != null ? email : "No email");

                    // Dashboard top info
                    tvTeacherNameDisplay.setText("Welcome, " + (fullName != null ? fullName : "Teacher Name"));
                    tvTeacherIdDisplay.setText("ID: " + (id != null ? id : teacherId));

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

    private void loadExamData(String teacherId) {
        DatabaseReference examsRef = FirebaseDatabase.getInstance()
                .getReference("Exams");

        examsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<DataSnapshot> teacherExams = new ArrayList<>();

                if (snapshot.hasChild(teacherId)) {
                    for (DataSnapshot examSnap : snapshot.child(teacherId).getChildren()) {
                        teacherExams.add(examSnap);
                    }
                } else {
                    for (DataSnapshot examSnap : snapshot.getChildren()) {
                        String tid = examSnap.child("teacherId").getValue(String.class);
                        if (tid != null && tid.equals(teacherId)) {
                            teacherExams.add(examSnap);
                        }
                    }
                }

                if (!teacherExams.isEmpty()) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    teacherExams.sort((a, b) -> {
                        try {
                            Date dateA = sdf.parse(a.child("createdAt").getValue(String.class));
                            Date dateB = sdf.parse(b.child("createdAt").getValue(String.class));
                            return dateB.compareTo(dateA);
                        } catch (ParseException e) {
                            return 0;
                        }
                    });

                    DataSnapshot latestExam = teacherExams.get(0);
                    String examTitle = latestExam.child("examTitle").getValue(String.class);
                    String sectionName = latestExam.child("sectionName").getValue(String.class);
                    String createdAt = latestExam.child("createdAt").getValue(String.class);

                    tvRecentExamTitle.setText(
                            (examTitle != null ? examTitle : "No Title")
                                    + (sectionName != null ? " - Section " + sectionName : "")
                                    + "\nCreated at: " + (createdAt != null ? createdAt : "No date")
                    );
                } else {
                    tvRecentExamTitle.setText("No exams created yet");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TeacherDashboardActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openProfile() {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("teacherId", tvTeacherId.getText().toString());
        intent.putExtra("fullName", tvFullName.getText().toString());
        intent.putExtra("email", tvEmail.getText().toString());
        intent.putExtra("birthday", tvBirthday.getText().toString());
        intent.putExtra("course", tvCourse.getText().toString());
        intent.putExtra("subjects", tvSubjects.getText().toString());
        startActivity(intent);
    }

    // ===== SIDE NAVIGATION LOGIC =====
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_manage_exam) {
            startActivity(new Intent(this, ManageExamActivity.class));
        } else if (id == R.id.nav_view_results) {
            startActivity(new Intent(this, ViewStudentsActivity.class));
        } else if (id == R.id.nav_view_profile) {
            openProfile();
        } else if (id == R.id.nav_logout) {
            logout();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void logout() {
        SharedPreferences prefs = getSharedPreferences("user_data", MODE_PRIVATE);
        prefs.edit().remove("logged_user").apply();

        FirebaseAuth.getInstance().signOut();
        sessionManager.clearSession();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
