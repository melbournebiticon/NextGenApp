package com.example.nextgen.admin;

import android.content.Intent; // DAGDAG: Import for Intent
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nextgen.MainActivity;
import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class CourseActivity extends AppCompatActivity {

    // DAGDAG: SIDEBAR COMPONENTS
    private DrawerLayout drawerLayout;
    private LinearLayout sidebarLayout;
    private ImageButton btnToggleSidebar;
    private LinearLayout curriculumDropdown, accountsDropdown;

    // DAGDAG: Sidebar state management
    private boolean isCurriculumExpanded = true;
    private boolean isAccountsExpanded = false;

    // ORIGINAL COMPONENTS - WALANG BINAGO
    private EditText etCourseName;
    private Button btnAddCourse;
    private Spinner spinnerSections; // Only use this now
    private RecyclerView recyclerCourses;

    private CourseAdapter adapter;
    private final List<CourseModel> courseList = new ArrayList<>();
    private final List<CourseOption> courseOptionList = new ArrayList<>();
    private DatabaseReference coursesRef, courseOptionsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course);

        // DAGDAG: INITIALIZE SIDEBAR
        initializeSidebar();
        setInitialSidebarState();

        // ORIGINAL CODE - WALANG BINAGO
        etCourseName = findViewById(R.id.etCourseName);
        btnAddCourse = findViewById(R.id.btnAddCourse);
        spinnerSections = findViewById(R.id.spinnerSection);
        recyclerCourses = findViewById(R.id.recyclerCourses);

        recyclerCourses.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CourseAdapter(this, courseList);
        recyclerCourses.setAdapter(adapter);

        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
        courseOptionsRef = FirebaseDatabase.getInstance().getReference("CourseOptions");

        // Load course options into spinner
        loadCourseOptions();

        btnAddCourse.setOnClickListener(v -> addCourse());

        // Load all courses in RecyclerView
        loadCourses();
    }

    // DAGDAG: SIDEBAR INITIALIZATION
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

        // Setup sidebar navigation
        setupSidebarNavigation();
    }

    // DAGDAG: Method to set initial sidebar state
    private void setInitialSidebarState() {
        // Set Manage Course button as active (highlighted)
        Button btnManageCourse = findViewById(R.id.btnManageCourse);
        btnManageCourse.setBackgroundResource(R.drawable.sidebar_button_pressed);

        // Set curriculum dropdown as expanded by default
        curriculumDropdown.setVisibility(View.VISIBLE);
        Button btnCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        btnCurriculumHeader.setText("📘 Manage Curriculum ▴");

        // Set accounts dropdown as collapsed by default
        accountsDropdown.setVisibility(View.GONE);
        Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
        btnAccountsHeader.setText("👤 Manage Accounts ▾");
    }

    // DAGDAG: SIDEBAR NAVIGATION SETUP
    private void setupSidebarNavigation() {
        // Curriculum dropdown
        final Button btnCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        btnCurriculumHeader.setOnClickListener(v -> {
            if (curriculumDropdown.getVisibility() == View.VISIBLE) {
                curriculumDropdown.setVisibility(View.GONE);
                btnCurriculumHeader.setText("📘 Manage Curriculum ▾");
                isCurriculumExpanded = false;
            } else {
                curriculumDropdown.setVisibility(View.VISIBLE);
                btnCurriculumHeader.setText("📘 Manage Curriculum ▴");
                isCurriculumExpanded = true;

                // Collapse accounts if needed for consistency
                if (isAccountsExpanded) {
                    accountsDropdown.setVisibility(View.GONE);
                    Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
                    btnAccountsHeader.setText("👤 Manage Accounts ▾");
                    isAccountsExpanded = false;
                }
            }
        });

        // Accounts dropdown
        final Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
        btnAccountsHeader.setOnClickListener(v -> {
            if (accountsDropdown.getVisibility() == View.VISIBLE) {
                accountsDropdown.setVisibility(View.GONE);
                btnAccountsHeader.setText("👤 Manage Accounts ▾");
                isAccountsExpanded = false;
            } else {
                accountsDropdown.setVisibility(View.VISIBLE);
                btnAccountsHeader.setText("👤 Manage Accounts ▴");
                isAccountsExpanded = true;

                // Collapse curriculum if needed for consistency
                if (isCurriculumExpanded) {
                    curriculumDropdown.setVisibility(View.GONE);
                    btnCurriculumHeader.setText("📘 Manage Curriculum ▾");
                    isCurriculumExpanded = false;
                }
            }
        });

        // Sidebar buttons functionality
        setupSidebarButtons();
    }

    // DAGDAG: Setup sidebar buttons functionality
    private void setupSidebarButtons() {
        // Curriculum buttons
        Button btnManageSpecializations = findViewById(R.id.btnManageSpecializations);
        Button btnManageYears = findViewById(R.id.btnManageYears);
        Button btnManageSections = findViewById(R.id.btnManageSections);
        Button btnManageCourse = findViewById(R.id.btnManageCourse);
        Button btnManageSubjects = findViewById(R.id.btnManageSubjects);

        // Accounts buttons
        Button btnManageTeachers = findViewById(R.id.btnManageTeachers);
        Button btnManageStudents = findViewById(R.id.btnManageStudents);

        // Set click listeners for sidebar buttons
        btnManageSpecializations.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(CourseActivity.this, SpecializationsActivity.class));
        });

        btnManageYears.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(CourseActivity.this, YearsActivity.class));
        });

        btnManageSections.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(CourseActivity.this, SectionsActivity.class));
        });

        btnManageCourse.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            // No navigation needed since we're already in CourseActivity
        });

        btnManageSubjects.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(CourseActivity.this, SubjectActivity.class));
        });

        btnManageTeachers.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(CourseActivity.this, TeacherActivity.class));
        });

        btnManageStudents.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(CourseActivity.this, StudentActivity.class));
        });

        // Logout button
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

    // DAGDAG: Back pressed handling for sidebar
    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(sidebarLayout)) {
            drawerLayout.closeDrawer(sidebarLayout);
        } else {
            super.onBackPressed();
        }
    }

    // ORIGINAL METHODS - WALANG BINAGO
    private void loadCourseOptions() {
        courseOptionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseOptionList.clear();
                List<String> names = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseOption option = ds.getValue(CourseOption.class);
                    if (option != null) {
                        courseOptionList.add(option);
                        names.add(option.getSpecializationName() + " - " + option.getSectionName() + " - " + option.getYearName());

                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(CourseActivity.this,
                        android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerSections.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void addCourse() {
        String name = etCourseName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Enter course name", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedPos = spinnerSections.getSelectedItemPosition();
        if (selectedPos < 0) {
            Toast.makeText(this, "Select a course option", Toast.LENGTH_SHORT).show();
            return;
        }

        CourseOption selectedOption = courseOptionList.get(selectedPos);

        String id = coursesRef.push().getKey();
        if (id == null) {
            Toast.makeText(this, "Error generating ID", Toast.LENGTH_SHORT).show();
            return;
        }

        CourseModel course = new CourseModel(
                id,
                name,
                selectedOption.getSpecializationId(),
                selectedOption.getSpecializationName(),
                selectedOption.getYearId(),
                selectedOption.getYearName(),
                selectedOption.getSectionId(),
                selectedOption.getSectionName()
        );

        coursesRef.child(id).setValue(course)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Course added", Toast.LENGTH_SHORT).show();
                    etCourseName.setText("");
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void loadCourses() {
        coursesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                courseList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) courseList.add(course);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }
}