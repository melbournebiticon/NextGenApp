package com.example.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

public class AdminActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private LinearLayout curriculumDropdown, accountsDropdown;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        auth = FirebaseAuth.getInstance();

        // Drawer (hamburger menu)
        drawerLayout = findViewById(R.id.drawerLayout);
        ImageButton btnOpenSidebar = findViewById(R.id.btnOpenSidebar);
        btnOpenSidebar.setOnClickListener(v -> toggleSidebar());

        // Dropdown headers
        Button btnManageCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        Button btnManageAccountsHeader = findViewById(R.id.btnManageAccountsHeader);

        // Dropdown layouts
        curriculumDropdown = findViewById(R.id.curriculumDropdown);
        accountsDropdown = findViewById(R.id.accountsDropdown);

        // Toggle dropdowns (simple visibility toggle)
        btnManageCurriculumHeader.setOnClickListener(v -> {
            if (curriculumDropdown.getVisibility() == View.VISIBLE) {
                curriculumDropdown.setVisibility(View.GONE);
            } else {
                curriculumDropdown.setVisibility(View.VISIBLE);
                accountsDropdown.setVisibility(View.GONE); // close other dropdown
            }
        });

        btnManageAccountsHeader.setOnClickListener(v -> {
            if (accountsDropdown.getVisibility() == View.VISIBLE) {
                accountsDropdown.setVisibility(View.GONE);
            } else {
                accountsDropdown.setVisibility(View.VISIBLE);
                curriculumDropdown.setVisibility(View.GONE); // close other dropdown
            }
        });

        // Buttons inside Curriculum dropdown
        setupButton(R.id.btnManageSpecializations, SpecializationsActivity.class);
        setupButton(R.id.btnManageYears, YearsActivity.class);
        setupButton(R.id.btnManageSections, SectionsActivity.class);
        setupButton(R.id.btnManageCourse, CourseActivity.class);
        setupButton(R.id.btnManageSubjects, SubjectActivity.class);

        // Buttons inside Accounts dropdown
        setupButton(R.id.btnManageTeachers, TeacherActivity.class);
        setupButton(R.id.btnManageStudents, StudentActivity.class);

        // Logout button
        Button logoutBtn = findViewById(R.id.logoutBtn);
        logoutBtn.setOnClickListener(v -> logout());
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        fixSidebarHeight();
    }

    private void fixSidebarHeight() {
        View sidebarLayout = findViewById(R.id.sidebarLayout);
        if (sidebarLayout != null) {
            sidebarLayout.post(new Runnable() {
                @Override
                public void run() {
                    DisplayMetrics displayMetrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                    int screenHeight = displayMetrics.heightPixels;

                    ViewGroup.LayoutParams params = sidebarLayout.getLayoutParams();
                    params.height = screenHeight;
                    sidebarLayout.setLayoutParams(params);
                    sidebarLayout.requestLayout();
                }
            });
        }
    }

    // Open or close the drawer
    private void toggleSidebar() {
        if (drawerLayout.isDrawerOpen(android.view.Gravity.START)) {
            drawerLayout.closeDrawer(android.view.Gravity.START);
        } else {
            drawerLayout.openDrawer(android.view.Gravity.START);
        }
    }

    // Helper method to open another activity
    private void setupButton(int buttonId, Class<?> activityClass) {
        Button button = findViewById(buttonId);
        button.setOnClickListener(v -> startActivity(new Intent(AdminActivity.this, activityClass)));
    }

    // Logout
    private void logout() {
        // Close sidebar first
        if (drawerLayout.isDrawerOpen(android.view.Gravity.START)) {
            drawerLayout.closeDrawer(android.view.Gravity.START);
        }

        // Clear session AND sign out from Firebase
        SessionManager sessionManager = new SessionManager(this);
        sessionManager.clearSession();
        auth.signOut();

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        // Redirect to login screen
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}