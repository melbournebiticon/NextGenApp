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

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

public class AdminActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private LinearLayout curriculumDropdown, accountsDropdown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

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
    }
}
