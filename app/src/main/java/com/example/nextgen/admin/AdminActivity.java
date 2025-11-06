package com.example.nextgen.admin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
<<<<<<< Updated upstream
=======
import android.widget.TextView;
import android.widget.Toast;
>>>>>>> Stashed changes

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

public class AdminActivity extends AppCompatActivity {

<<<<<<< Updated upstream
    private DrawerLayout drawerLayout;
    private LinearLayout curriculumDropdown, accountsDropdown;
=======
    // Sidebar components
    private DrawerLayout drawerLayout;
    private LinearLayout sidebarLayout;
    private ImageButton btnToggleSidebar;
    private LinearLayout curriculumDropdown, accountsDropdown;

    // Dashboard counters
    private TextView totalCourses, totalExams, totalExaminees, totalTeachers;
>>>>>>> Stashed changes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

<<<<<<< Updated upstream
        // Sidebar toggle (hamburger button)
        ImageButton btnOpenSidebar = findViewById(R.id.btnOpenSidebar);
        drawerLayout = findViewById(R.id.drawerLayout);

        btnOpenSidebar.setOnClickListener(v -> toggleSidebar());

        // Dropdown headers
        Button btnManageCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        Button btnManageAccountsHeader = findViewById(R.id.btnManageAccountsHeader);

        // Dropdown layouts
        curriculumDropdown = findViewById(R.id.curriculumDropdown);
        accountsDropdown = findViewById(R.id.accountsDropdown);

        // Toggle dropdowns with smooth animation
        btnManageCurriculumHeader.setOnClickListener(v -> toggleDropdownSmooth(curriculumDropdown));
        btnManageAccountsHeader.setOnClickListener(v -> toggleDropdownSmooth(accountsDropdown));
=======
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
>>>>>>> Stashed changes

        // Buttons inside Curriculum dropdown
        setupButton(R.id.btnManageSpecializations, SpecializationsActivity.class);
        setupButton(R.id.btnManageYears, YearsActivity.class);
        setupButton(R.id.btnManageSections, SectionsActivity.class);
        setupButton(R.id.btnManageCourse, CourseActivity.class);
        setupButton(R.id.btnManageSubjects, SubjectActivity.class);

<<<<<<< Updated upstream
        // Buttons inside Accounts dropdown
        setupButton(R.id.btnManageTeachers, TeacherActivity.class);
        setupButton(R.id.btnManageStudents, StudentActivity.class);

        // Logout button
        Button logoutBtn = findViewById(R.id.logoutBtn);
        logoutBtn.setOnClickListener(v -> logout());
    }

    // Toggle sidebar open/close
    private void toggleSidebar() {
        if (drawerLayout.isDrawerOpen(android.view.Gravity.START)) {
            drawerLayout.closeDrawer(android.view.Gravity.START);
        } else {
            drawerLayout.openDrawer(android.view.Gravity.START);
        }
    }

    // Smooth dropdown animation
    private void toggleDropdownSmooth(LinearLayout dropdown) {
        if (dropdown.getVisibility() == View.VISIBLE) {
            int initialHeight = dropdown.getHeight();
            ValueAnimator animator = ValueAnimator.ofInt(initialHeight, 0);
            animator.addUpdateListener(animation -> {
                int value = (int) animation.getAnimatedValue();
                dropdown.getLayoutParams().height = value;
                dropdown.requestLayout();
            });
            animator.setDuration(300);
            animator.start();
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dropdown.setVisibility(View.GONE);
                }
            });
        } else {
            dropdown.setVisibility(View.VISIBLE);
            dropdown.measure(View.MeasureSpec.makeMeasureSpec(dropdown.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.UNSPECIFIED);
            int targetHeight = dropdown.getMeasuredHeight();
            dropdown.getLayoutParams().height = 0;
            ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
            animator.addUpdateListener(animation -> {
                int value = (int) animation.getAnimatedValue();
                dropdown.getLayoutParams().height = value;
                dropdown.requestLayout();
            });
            animator.setDuration(300);
            animator.start();
        }
    }

    // Helper to setup button click to open activity
    private void setupButton(int buttonId, Class<?> activityClass) {
        Button button = findViewById(buttonId);
        button.setOnClickListener(v -> startActivity(new Intent(AdminActivity.this, activityClass)));
    }

    // Logout logic
    private void logout() {
        SessionManager sessionManager = new SessionManager(this);
        sessionManager.clearSession();
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
=======
            // Redirect to login
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        });
>>>>>>> Stashed changes
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