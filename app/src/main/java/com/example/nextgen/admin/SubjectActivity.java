package com.example.nextgen.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
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

public class SubjectActivity extends AppCompatActivity {

    // SIDEBAR COMPONENTS
    private DrawerLayout drawerLayout;
    private LinearLayout sidebarLayout;
    private ImageButton btnToggleSidebar;
    private LinearLayout curriculumDropdown, accountsDropdown;

    // Sidebar state management
    private boolean isCurriculumExpanded = true;
    private boolean isAccountsExpanded = false;

    // ORIGINAL COMPONENTS - WALANG BINAGO
    private EditText etSubjectCode, etSubjectName;
    private Spinner spinnerCourses;
    private Button btnAddSubject;
    private RecyclerView recyclerSubjects;

    // NEW UI COMPONENTS
    private TextView tvSubjectCount;
    private LinearLayout emptyState;

    private SubjectAdapter adapter;
    private final List<SubjectModel> subjectList = new ArrayList<>();
    private final List<SubjectOption> subjectOptionList = new ArrayList<>();

    private DatabaseReference subjectsRef, coursesRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject);

        // INITIALIZE SIDEBAR
        initializeSidebar();
        setInitialSidebarState();

        // ORIGINAL CODE - WALANG BINAGO
        etSubjectCode = findViewById(R.id.etSubjectCode);
        etSubjectName = findViewById(R.id.etSubjectName);
        spinnerCourses = findViewById(R.id.spinnerCourseOption);
        btnAddSubject = findViewById(R.id.btnAddSubject);
        recyclerSubjects = findViewById(R.id.recyclerSubjects);

        // NEW UI COMPONENTS INITIALIZATION
        tvSubjectCount = findViewById(R.id.tvSubjectCount);
        emptyState = findViewById(R.id.emptyState);

        // RecyclerView setup
        recyclerSubjects.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectAdapter(subjectList);
        recyclerSubjects.setAdapter(adapter);

        // Firebase
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");

        // Load spinner options and existing subjects
        loadSubjectOptions();
        loadSubjects();

        // Add subject
        btnAddSubject.setOnClickListener(v -> addSubject());
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

        // Setup sidebar navigation
        setupSidebarNavigation();
    }

    // Method to set initial sidebar state
    private void setInitialSidebarState() {
        // Set Manage Subjects button as active (highlighted)
        Button btnManageSubjects = findViewById(R.id.btnManageSubjects);
        btnManageSubjects.setBackgroundResource(R.drawable.sidebar_button_pressed);

        // Set curriculum dropdown as expanded by default
        curriculumDropdown.setVisibility(View.VISIBLE);
        Button btnCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        btnCurriculumHeader.setText("📘 Manage Curriculum ▴");

        // Set accounts dropdown as collapsed by default
        accountsDropdown.setVisibility(View.GONE);
        Button btnAccountsHeader = findViewById(R.id.btnManageAccountsHeader);
        btnAccountsHeader.setText("👤 Manage Accounts ▾");
    }

    // SIDEBAR NAVIGATION SETUP
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

    // Setup sidebar buttons functionality
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
            startActivity(new Intent(SubjectActivity.this, SpecializationsActivity.class));
        });

        btnManageYears.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SubjectActivity.this, YearsActivity.class));
        });

        btnManageSections.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SubjectActivity.this, SectionsActivity.class));
        });

        btnManageCourse.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SubjectActivity.this, CourseActivity.class));
        });

        btnManageSubjects.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            // No navigation needed since we're already in SubjectActivity
        });

        btnManageTeachers.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SubjectActivity.this, TeacherActivity.class));
        });

        btnManageStudents.setOnClickListener(v -> {
            drawerLayout.closeDrawer(sidebarLayout);
            startActivity(new Intent(SubjectActivity.this, StudentActivity.class));
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

    // Back pressed handling for sidebar
    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(sidebarLayout)) {
            drawerLayout.closeDrawer(sidebarLayout);
        } else {
            super.onBackPressed();
        }
    }

    // ORIGINAL METHODS - WALANG BINAGO
    private void loadSubjectOptions() {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectOptionList.clear();
                List<String> displayNames = new ArrayList<>();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) {
                        SubjectOption option = new SubjectOption(
                                course.getId(),
                                course.getName(),
                                course.getSpecializationName(),
                                course.getYearName(),
                                course.getSectionName()
                        );
                        subjectOptionList.add(option);
                        displayNames.add(option.toString());
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(SubjectActivity.this,
                        android.R.layout.simple_spinner_item, displayNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCourses.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void addSubject() {
        String code = etSubjectCode.getText().toString().trim();
        String name = etSubjectName.getText().toString().trim();

        if (TextUtils.isEmpty(code)) {
            etSubjectCode.setError("Enter subject code");
            etSubjectCode.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(name)) {
            etSubjectName.setError("Enter subject name");
            etSubjectName.requestFocus();
            return;
        }

        int pos = spinnerCourses.getSelectedItemPosition();
        if (pos < 0) {
            Toast.makeText(this, "Select a course option", Toast.LENGTH_SHORT).show();
            return;
        }

        SubjectOption selectedOption = subjectOptionList.get(pos);

        String id = subjectsRef.push().getKey();
        if (id == null) {
            Toast.makeText(this, "Error generating ID", Toast.LENGTH_SHORT).show();
            return;
        }

        SubjectModel subject = new SubjectModel(
                id,
                code,
                name,
                selectedOption.getCourseId(),
                selectedOption.getCourseName(),
                selectedOption.getSpecializationName(),
                selectedOption.getYearName(),
                selectedOption.getSectionName()
        );

        subjectsRef.child(id).setValue(subject)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Subject added successfully", Toast.LENGTH_SHORT).show();
                    etSubjectCode.setText("");
                    etSubjectName.setText("");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void loadSubjects() {
        subjectsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SubjectModel s = ds.getValue(SubjectModel.class);
                    if (s != null) subjectList.add(s);
                }

                // UPDATE UI WITH COUNT AND EMPTY STATE
                updateUI();
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // NEW METHOD: Update UI based on data
    private void updateUI() {
        int count = subjectList.size();
        tvSubjectCount.setText(count + " subject" + (count != 1 ? "s" : ""));

        if (subjectList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerSubjects.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerSubjects.setVisibility(View.VISIBLE);
        }
    }
}