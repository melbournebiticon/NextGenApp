package com.example.nextgen.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
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

import com.example.nextgen.R;
import com.example.nextgen.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SubjectActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private LinearLayout curriculumDropdown, accountsDropdown;
    private ImageButton btnToggleSidebar;

    private Button btnAddSubject;
    private RecyclerView recyclerSubjects;

    private SubjectAdapter adapter;
    private final List<SubjectModel> subjectList = new ArrayList<>();
    private final List<SubjectOption> subjectOptionList = new ArrayList<>();

    private DatabaseReference subjectsRef, coursesRef;
    private SessionManager sessionManager;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject);

        // CRITICAL FIX FOR SIDEBAR HEIGHT
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        // Initialize SessionManager and Firebase Auth
        sessionManager = new SessionManager(this);
        auth = FirebaseAuth.getInstance();

        initializeViews();
        setupClickListeners();
        setupFirebase();
        loadSubjects();
    }

    private void initializeViews() {
        try {
            // Initialize main views
            drawerLayout = findViewById(R.id.drawerLayout);
            curriculumDropdown = findViewById(R.id.curriculumDropdown);
            accountsDropdown = findViewById(R.id.accountsDropdown);

            // Initialize sidebar button
            btnToggleSidebar = findViewById(R.id.btnToggleSidebar);

            // Initialize subject views
            btnAddSubject = findViewById(R.id.btnAddSubject);
            recyclerSubjects = findViewById(R.id.recyclerSubjects);

            // Additional fix for drawer layout
            if (drawerLayout != null) {
                drawerLayout.setFitsSystemWindows(true);
            }

            // Setup RecyclerView
            recyclerSubjects.setLayoutManager(new LinearLayoutManager(this));
            adapter = new SubjectAdapter(this, subjectList);
            recyclerSubjects.setAdapter(adapter);

            // Check if all views are properly initialized
            if (btnToggleSidebar == null) {
                Log.e("SubjectActivity", "btnToggleSidebar is NULL");
            }

        } catch (Exception e) {
            Log.e("SubjectActivity", "Error in initializeViews: " + e.getMessage());
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_LONG).show();
        }
    }

    private void setupClickListeners() {
        // Sidebar toggle
        if (btnToggleSidebar != null) {
            btnToggleSidebar.setOnClickListener(v -> toggleSidebar());
        } else {
            Log.e("SubjectActivity", "btnToggleSidebar is null - cannot set click listener");
        }

        // Dropdown headers
        Button btnManageCurriculumHeader = findViewById(R.id.btnManageCurriculumHeader);
        Button btnManageAccountsHeader = findViewById(R.id.btnManageAccountsHeader);

        if (btnManageCurriculumHeader != null) {
            btnManageCurriculumHeader.setOnClickListener(v -> toggleDropdown(curriculumDropdown));
        } else {
            Log.e("SubjectActivity", "btnManageCurriculumHeader is null");
        }

        if (btnManageAccountsHeader != null) {
            btnManageAccountsHeader.setOnClickListener(v -> toggleDropdown(accountsDropdown));
        } else {
            Log.e("SubjectActivity", "btnManageAccountsHeader is null");
        }

        // Sidebar navigation buttons
        setupSidebarNavigation();

        // Add Subject button - OPENS DIALOG
        if (btnAddSubject != null) {
            btnAddSubject.setOnClickListener(v -> showAddSubjectDialog());
        } else {
            Log.e("SubjectActivity", "btnAddSubject is null - cannot set click listener");
        }
    }

    private void showAddSubjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_subject, null);
        builder.setView(dialogView);

        EditText etSubjectCode = dialogView.findViewById(R.id.etSubjectCode);
        EditText etSubjectName = dialogView.findViewById(R.id.etSubjectName);
        Spinner spinnerCourses = dialogView.findViewById(R.id.spinnerCourseOption);
        Button btnSave = dialogView.findViewById(R.id.btnSaveSubject);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelSubject);

        // Load course options for spinner in dialog
        loadSubjectOptionsForDialog(spinnerCourses);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSave.setOnClickListener(v -> {
            String code = etSubjectCode.getText().toString().trim();
            String name = etSubjectName.getText().toString().trim();

            if (TextUtils.isEmpty(code)) {
                etSubjectCode.setError("Enter subject code");
                return;
            }
            if (TextUtils.isEmpty(name)) {
                etSubjectName.setError("Enter subject name");
                return;
            }

            int pos = spinnerCourses.getSelectedItemPosition();
            if (pos < 0 || subjectOptionList.isEmpty()) {
                Toast.makeText(this, "Select a course option", Toast.LENGTH_SHORT).show();
                return;
            }

            SubjectOption selectedOption = subjectOptionList.get(pos);
            addSubjectToFirebase(code, name, selectedOption);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
    }

    private void loadSubjectOptionsForDialog(Spinner spinner) {
        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectOptionList.clear();
                List<String> displayNames = new ArrayList<>();

                Log.d("SubjectActivity", "Total courses in database: " + snapshot.getChildrenCount());

                for (DataSnapshot ds : snapshot.getChildren()) {
                    CourseModel course = ds.getValue(CourseModel.class);
                    if (course != null) {
                        // DEBUG: Check if course data is complete
                        Log.d("SubjectActivity", "Course Data - ID: " + course.getId() +
                                ", Name: " + course.getCourseName() +
                                ", Spec: " + course.getSpecializationName() +
                                ", Year: " + course.getYearName() +
                                ", Section: " + course.getSectionName());

                        SubjectOption option = new SubjectOption(
                                course.getId(),
                                course.getCourseName(),
                                course.getSpecializationName(),
                                course.getYearName(),
                                course.getSectionName()
                        );
                        subjectOptionList.add(option);
                        displayNames.add(option.toString());
                    } else {
                        Log.e("SubjectActivity", "Course is null for snapshot: " + ds.getKey());
                    }
                }

                Log.d("SubjectActivity", "Total courses loaded: " + subjectOptionList.size());

                if (displayNames.isEmpty()) {
                    displayNames.add("No courses available");
                    Toast.makeText(SubjectActivity.this, "No courses found. Please add courses first.", Toast.LENGTH_LONG).show();
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(SubjectActivity.this,
                        android.R.layout.simple_spinner_item, displayNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SubjectActivity.this, "Failed to load courses: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("SubjectActivity", "Database error: " + error.getMessage());
            }
        });
    }

    private void addSubjectToFirebase(String code, String name, SubjectOption selectedOption) {
        String id = subjectsRef.push().getKey();
        if (id == null) {
            Toast.makeText(this, "Error generating ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // DEBUG: Check selected option data
        Log.d("SubjectActivity", "Selected Option - CourseID: " + selectedOption.getCourseId() +
                ", CourseName: " + selectedOption.getCourseName() +
                ", Spec: " + selectedOption.getSpecializationName() +
                ", Year: " + selectedOption.getYearName() +
                ", Section: " + selectedOption.getSectionName());

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
                    Log.d("SubjectActivity", "Subject added: " + code + " - " + name);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to add subject: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("SubjectActivity", "Add subject error: " + e.getMessage());
                });
    }

    private void setupSidebarNavigation() {
        // Curriculum management
        View btnSpecializations = findViewById(R.id.btnManageSpecializations);
        View btnYears = findViewById(R.id.btnManageYears);
        View btnSections = findViewById(R.id.btnManageSections);
        View btnCourse = findViewById(R.id.btnManageCourse);
        View btnSubjects = findViewById(R.id.btnManageSubjects);
        View btnTeachers = findViewById(R.id.btnManageTeachers);
        View btnStudents = findViewById(R.id.btnManageStudents);
        View logoutBtn = findViewById(R.id.logoutBtn);

        if (btnSpecializations != null) {
            btnSpecializations.setOnClickListener(v -> {
                startActivity(new Intent(this, SpecializationsActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        }

        if (btnYears != null) {
            btnYears.setOnClickListener(v -> {
                startActivity(new Intent(this, YearsActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        }

        if (btnSections != null) {
            btnSections.setOnClickListener(v -> {
                startActivity(new Intent(this, SectionsActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        }

        if (btnCourse != null) {
            btnCourse.setOnClickListener(v -> {
                startActivity(new Intent(this, CourseActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        }

        if (btnSubjects != null) {
            btnSubjects.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        }

        if (btnTeachers != null) {
            btnTeachers.setOnClickListener(v -> {
                startActivity(new Intent(this, TeacherActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        }

        if (btnStudents != null) {
            btnStudents.setOnClickListener(v -> {
                startActivity(new Intent(this, StudentActivity.class));
                if (drawerLayout != null) drawerLayout.closeDrawer(Gravity.START);
            });
        }

        // Logout
        if (logoutBtn != null) {
            logoutBtn.setOnClickListener(v -> performLogout());
        }
    }

    private void performLogout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Logout");
        builder.setMessage("Are you sure you want to logout?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            sessionManager.clearSession();
            if (auth.getCurrentUser() != null) {
                auth.signOut();
            }
            Intent intent = new Intent(SubjectActivity.this, com.example.nextgen.MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Toast.makeText(SubjectActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void toggleSidebar() {
        if (drawerLayout != null) {
            if (drawerLayout.isDrawerOpen(Gravity.START)) {
                drawerLayout.closeDrawer(Gravity.START);
            } else {
                drawerLayout.openDrawer(Gravity.START);
            }
        }
    }

    private void toggleDropdown(LinearLayout dropdown) {
        if (dropdown != null) {
            if (dropdown.getVisibility() == View.VISIBLE) {
                dropdown.setVisibility(View.GONE);
            } else {
                dropdown.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupFirebase() {
        subjectsRef = FirebaseDatabase.getInstance().getReference("Subjects");
        coursesRef = FirebaseDatabase.getInstance().getReference("Courses");
    }

    private void loadSubjects() {
        subjectsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                subjectList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SubjectModel s = ds.getValue(SubjectModel.class);
                    if (s != null) {
                        subjectList.add(s);
                        Log.d("SubjectActivity", "Loaded subject: " + s.getCode() + " - " + s.getName());
                    }
                }
                adapter.notifyDataSetChanged();
                Log.d("SubjectActivity", "Total subjects loaded: " + subjectList.size());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SubjectActivity.this, "Failed to load subjects", Toast.LENGTH_SHORT).show();
                Log.e("SubjectActivity", "Load subjects error: " + error.getMessage());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Close drawer when activity resumes
        if (drawerLayout != null && drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawer(Gravity.START);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawer(Gravity.START);
        } else {
            super.onBackPressed();
        }
    }
}