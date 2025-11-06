package com.example.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

public class AdminActivity extends AppCompatActivity {

    // Sidebar components
    private DrawerLayout drawerLayout;
    private LinearLayout sidebarLayout;
    private ImageButton btnToggleSidebar;
    private LinearLayout curriculumDropdown, accountsDropdown;

    // Dashboard counters
    private TextView totalCourses, totalExams, totalExaminees, totalTeachers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Initialize Sidebar
        initializeSidebar();

        // Initialize Dashboard Counters
        initializeDashboard();

        // Setup sidebar navigation - SAME LOGIC, DIFFERENT IMPLEMENTATION
        setupSidebarNavigation();
    }

    // SIDEBAR INITIALIZATION
    private void initializeSidebar() {
        drawerLayout = findViewById(R.id.drawerLayout);
        sidebarLayout = findViewById(R.id.sidebarLayout);
        btnToggleSidebar = findViewById(R.id.btnOpenSidebar);
        curriculumDropdown = findViewById(R.id.curriculumDropdown);
        accountsDropdown = findViewById(R.id.accountsDropdown);

        // Sidebar toggle
        btnToggleSidebar.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(sidebarLayout)) {
                drawerLayout.closeDrawer(sidebarLayout);
            } else {
                drawerLayout.openDrawer(sidebarLayout);
            }
        });
    }

    // DASHBOARD INITIALIZATION
    private void initializeDashboard() {
        totalCourses = findViewById(R.id.totalCourses);
        totalExams = findViewById(R.id.totalExams);
        totalExaminees = findViewById(R.id.totalExaminees);
        totalTeachers = findViewById(R.id.totalTeachers);

        // TODO: Implement actual data loading from Firebase
        // For now, set placeholder values
        totalCourses.setText("0");
        totalExams.setText("0");
        totalExaminees.setText("0");
        totalTeachers.setText("0");
    }

    // SIDEBAR NAVIGATION SETUP - SAME LOGIC AS BEFORE
    private void setupSidebarNavigation() {
        // Curriculum dropdown
        Button btnCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        btnCurriculumHeader.setOnClickListener(v -> {
            if (curriculumDropdown.getVisibility() == View.VISIBLE) {
                curriculumDropdown.setVisibility(View.GONE);
                btnCurriculumHeader.setText("📘 Manage Curriculum ▾");
            } else {
                curriculumDropdown.setVisibility(View.VISIBLE);
                btnCurriculumHeader.setText("📘 Manage Curriculum ▴");
            }
        });

        // Accounts dropdown
        Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
        btnAccountsHeader.setOnClickListener(v -> {
            if (accountsDropdown.getVisibility() == View.VISIBLE) {
                accountsDropdown.setVisibility(View.GONE);
                btnAccountsHeader.setText("👤 Manage Accounts ▾");
            } else {
                accountsDropdown.setVisibility(View.VISIBLE);
                btnAccountsHeader.setText("👤 Manage Accounts ▴");
            }
        });

        // Sidebar buttons functionality - SAME LOGIC AS BEFORE
        setupSidebarButtons();
    }

    private void setupSidebarButtons() {
        // Curriculum buttons - SAME LOGIC AS BEFORE
        Button btnManageSpecializations = findViewById(R.id.btnManageSpecializations);
        Button btnManageYears = findViewById(R.id.btnManageYears);
        Button btnManageSections = findViewById(R.id.btnManageSections);
        Button btnManageCourse = findViewById(R.id.btnManageCourse);
        Button btnManageSubjects = findViewById(R.id.btnManageSubjects);

        // Accounts buttons - SAME LOGIC AS BEFORE
        Button btnManageTeachers = findViewById(R.id.btnManageTeachers);
        Button btnManageStudents = findViewById(R.id.btnManageStudents);

        // Set click listeners for sidebar buttons - SAME LOGIC AS BEFORE
        btnManageSpecializations.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(AdminActivity.this, SpecializationsActivity.class));
        });

        btnManageYears.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(AdminActivity.this, YearsActivity.class));
        });

        btnManageSections.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(AdminActivity.this, SectionsActivity.class));
        });

        btnManageCourse.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(AdminActivity.this, CourseActivity.class));
        });

        btnManageSubjects.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(AdminActivity.this, SubjectActivity.class));
        });

        btnManageTeachers.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(AdminActivity.this, TeacherActivity.class));
        });

        btnManageStudents.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(AdminActivity.this, StudentActivity.class));
        });

        // Logout button - SAME LOGIC AS BEFORE
        Button logoutBtn = findViewById(R.id.logoutBtn);
        logoutBtn.setOnClickListener(v -> {
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
        });
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(sidebarLayout)) {
            drawerLayout.closeDrawer(sidebarLayout);
        } else {
            super.onBackPressed();
        }
    }
}